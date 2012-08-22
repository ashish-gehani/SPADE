/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2012 SRI International

 This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU General Public License as
 published by the Free Software Foundation, either version 3 of the
 License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
 --------------------------------------------------------------------------------
 */
package spade.reporter;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import spade.core.AbstractReporter;
import spade.edge.opm.Used;
import spade.edge.opm.WasDerivedFrom;
import spade.edge.opm.WasGeneratedBy;
import spade.edge.opm.WasTriggeredBy;
import spade.vertex.opm.Artifact;
import spade.vertex.opm.Process;

/**
 *
 * @author Dawood Tariq
 */
public class Audit extends AbstractReporter {

    // Store log for debugging purposes
    private boolean DEBUG_DUMP_LOG = false;
    private boolean ANDROID_PLATFORM = false;
    private String DEBUG_DUMP_FILE;
    ////////////////////////////////////////////////////////////////////////////
    private BufferedReader eventReader;
    private volatile boolean shutdown = false;
    private long boottime = 0;
    private int binderTransaction = 0;
    private long THREAD_SLEEP_DELAY = 100;
    private boolean USE_PROCFS = true;
    private final String simpleDatePattern = "EEE MMM d H:mm:ss yyyy";
    private String AUDIT_EXEC_PATH;
    // Process map based on <pid, vertex> pairs
    private Map<String, Process> processes = new HashMap<String, Process>();
    // File descriptor map based on <pid <fd, path>> pairs
    private Map<String, Map<String, String>> fileDescriptors = new HashMap<String, Map<String, String>>();
    // Event buffer map based on <audit_record_id <key, value>> pairs
    private Map<String, Map<String, String>> eventBuffer = new HashMap<String, Map<String, String>>();
    // File version map based on <path, version> pairs
    private Map<String, Integer> fileVersions = new HashMap<String, Integer>();
    ////////////////////////////////////////////////////////////////////////////
    // Group 1: key
    // Group 2: value
    private static Pattern pattern_key_value = Pattern.compile("(\\w+)=\"*((?<=\")[^\"]+(?=\")|([^\\s]+))\"*");
    // Group 1: node
    // Group 2: type
    // Group 3: time
    // Group 4: recordid
    private static Pattern pattern_message_start = Pattern.compile("type=(\\w*) audit\\(([0-9\\.]+)\\:([0-9]+)\\):\\s*");
    // Group 1: cwd
    private static Pattern pattern_cwd = Pattern.compile("cwd=\"*((?<=\")[^\"]*(?=\"))\"*");
    // Group 1: item number
    // Group 2: name
    private static Pattern pattern_path = Pattern.compile("item=([0-9]*)\\s*name=\"*((?<=\")[^\"]*(?=\"))\"*");
    // Binder transaction log pattern
    private static Pattern binder_transaction = Pattern.compile("([0-9]+): ([a-z]+)\\s*from ([0-9]+):[0-9]+ to ([0-9]+):[0-9]+");
    ////////////////////////////////////////////////////////////////////////////
    // List of processes to ignore
    private String ignoreProcesses;
    static final Logger logger = Logger.getLogger(Audit.class.getName());

    private enum SYSCALL {

        FORK,
        CLONE,
        CHMOD,
        FCHMOD
    }

    @Override
    public boolean launch(String arguments) {
        if (ANDROID_PLATFORM) {
            AUDIT_EXEC_PATH = "spade-audit";
            ignoreProcesses = "spade-audit auditd kauditd /sbin/adbd /system/bin/qemud /system/bin/sh dalvikvm";
            DEBUG_DUMP_FILE = "/sdcard/spade/output/audit.log";
        } else {
            AUDIT_EXEC_PATH = "./spade/reporter/spadeLinuxAudit";
            ignoreProcesses = "./spade/reporter/spadeLinuxAudit spadeLinuxAudit auditd";
            DEBUG_DUMP_FILE = "../../log/LinuxAudit.log";
        }

        // Get system boot time from /proc/stat. This is later used to determine the start
        // time for processes.
        try {
            BufferedReader boottimeReader = new BufferedReader(new FileReader("/proc/stat"));
            String line;
            while ((line = boottimeReader.readLine()) != null) {
                StringTokenizer st = new StringTokenizer(line);
                if (st.nextToken().equals("btime")) {
                    boottime = Long.parseLong(st.nextToken()) * 1000;
                    break;
                } else {
                    continue;
                }
            }
            boottimeReader.close();
        } catch (Exception exception) {
            logger.log(Level.SEVERE, "error reading boot time information from /proc/", exception);
        }

        // Create root vertex
        Process rootVertex = new spade.vertex.opm.Process();
        rootVertex.addAnnotation("pidname", "System");
        rootVertex.addAnnotation("pid", "0");
        rootVertex.addAnnotation("ppid", "0");
        String stime_readable = new java.text.SimpleDateFormat(simpleDatePattern).format(new java.util.Date(boottime));
        String stime = Long.toString(boottime);
        rootVertex.addAnnotation("boottime_unix", stime);
        rootVertex.addAnnotation("boottime_simple", stime_readable);
        processes.put("0", rootVertex);
        putVertex(rootVertex);

        // Build the process tree using the directories under /proc/. Directories
        // which have a numeric name represent processes.
        String path = "/proc";
        java.io.File folder = new java.io.File(path);
        java.io.File[] listOfFiles = folder.listFiles();
        for (int i = 0; i < listOfFiles.length; i++) {
            if (listOfFiles[i].isDirectory()) {

                String currentPID = listOfFiles[i].getName();
                try {
                    // Parse the current directory name to make sure it is numeric. If not,
                    // ignore and continue.
                    Integer.parseInt(currentPID);
                    Process processVertex = createProcessFromProc(currentPID);
                    processes.put(currentPID, processVertex);
                    Process parentVertex = processes.get(processVertex.getAnnotation("ppid"));
                    putVertex(processVertex);
                    WasTriggeredBy wtb = new WasTriggeredBy(processVertex, parentVertex);
                    putEdge(wtb);

                    // Get existing file descriptors for this process
                    Map<String, String> descriptors = getFileDescriptors(currentPID);
                    if (descriptors != null) {
                        fileDescriptors.put(currentPID, descriptors);
                    }
                } catch (Exception exception) {
                    continue;
                }
            }
        }

        try {
            // Start auditd and clear existing rules.
            Runtime.getRuntime().exec("auditd").waitFor();
            Runtime.getRuntime().exec("auditctl -D").waitFor();
            Runtime.getRuntime().exec("auditctl -D").waitFor();

            Runnable eventProcessor = new Runnable() {
                public void run() {
                    try {
                        java.lang.Process straceProcess = Runtime.getRuntime().exec(AUDIT_EXEC_PATH);
                        eventReader = new BufferedReader(new InputStreamReader(straceProcess.getInputStream()));
                        BufferedWriter dumpWriter = null;
                        if (DEBUG_DUMP_LOG) {
                            dumpWriter = new BufferedWriter(new FileWriter(DEBUG_DUMP_FILE));
                        }

                        while (!shutdown) {
                            String line = eventReader.readLine();
                            if ((line != null) && !line.isEmpty()) {
                                if (DEBUG_DUMP_LOG) {
                                    dumpWriter.write(line);
                                    dumpWriter.write(System.getProperty("line.separator"));
                                }
                                parseEventLine(line);
                            }
                        }

                        if (DEBUG_DUMP_LOG) {
                            dumpWriter.close();
                        }
                        eventReader.close();
                        straceProcess.destroy();
                    } catch (Exception exception) {
                        logger.log(Level.SEVERE, null, exception);
                    }
                }
            };
            new Thread(eventProcessor, "Audit-Thread").start();

            if (ANDROID_PLATFORM) {

                Runnable transactionProcessor = new Runnable() {
                    public void run() {
                        try {
                            BufferedReader transactionReader = new BufferedReader(new FileReader("/proc/binder/transaction_log"));
                            String line;
                            while (!shutdown) {
                                while ((line = transactionReader.readLine()) != null) {
                                    Matcher transactionMatcher = binder_transaction.matcher(line);
                                    if (transactionMatcher.find()) {
                                        int id = Integer.parseInt(transactionMatcher.group(1));
                                        String type = transactionMatcher.group(2);
                                        String frompid = transactionMatcher.group(3);
                                        String topid = transactionMatcher.group(4);
                                        if (id > binderTransaction) {
                                            WasTriggeredBy transaction = new WasTriggeredBy(processes.get(topid), processes.get(frompid));
                                            transaction.addAnnotation("operation", "binder-" + type);
                                            putEdge(transaction);
                                            binderTransaction = id;
                                        }
                                    }
                                }
                                Thread.sleep(THREAD_SLEEP_DELAY);
                            }
                            transactionReader.close();
                        } catch (Exception exception) {
                            logger.log(Level.SEVERE, null, exception);
                        }
                    }
                };
                new Thread(transactionProcessor, "androidBinder-Thread").start();
            }

            // Determine pids of processes that are to be ignored. These are appended
            // to the audit rule.
            Set<String> ignoreProcessSet = new HashSet<String>(Arrays.asList(ignoreProcesses.split("\\s+")));
            java.lang.Process pidChecker = Runtime.getRuntime().exec("ps");
            BufferedReader pidReader = new BufferedReader(new InputStreamReader(pidChecker.getInputStream()));
            pidReader.readLine();
            String line;
            StringBuilder ignorePids = new StringBuilder();
            while ((line = pidReader.readLine()) != null) {
                String details[] = line.split("\\s+");
                String user = details[0];
                String pid = details[1];
                String name = details[8].trim();
                if (user.equals("root") && ignoreProcessSet.contains(name)) {
                    ignorePids.append(" -F pid!=").append(pid);
                    ignorePids.append(" -F ppid!=").append(pid);
                    logger.log(Level.INFO, "ignoring process {0} with pid {1}", new Object[]{name, pid});
                }
            }
            pidReader.close();

            String auditRules = "-a exit,always -S fork -S vfork -S execve -S open -S close "
                    + "-S read -S readv -S write -S writev -S ioctl -S link -S symlink "
                    + "-S mknod -S rename -S dup -S dup2 -S setreuid -S setresuid -S setuid "
                    + "-S setreuid32 -S setresuid32 -S setuid32 -S chmod -S fchmod -S pipe "
                    + "-S pipe2 -F success=1" + ignorePids.toString();

            Runtime.getRuntime().exec("auditctl " + auditRules).waitFor();
            logger.log(Level.INFO, "configuring audit rules: {0}", auditRules);
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
            return false;
        }

        return true;
    }

    private Map<String, String> getFileDescriptors(String pid) {
        // Check if this pid exists in the /proc/ filesystem
        if (!(new java.io.File("/proc/" + pid).exists())) {
            return null;
        }
        // Get file descriptors for this process from /proc/
        try {
            java.lang.Process fdInfo = Runtime.getRuntime().exec("ls -l /proc/" + pid + "/fd");
            BufferedReader fdReader = new BufferedReader(new InputStreamReader(fdInfo.getInputStream()));
            Map<String, String> descriptors = new HashMap<String, String>();
            while (true) {
                String line = fdReader.readLine();
                if (line == null) {
                    break;
                }
                String tokens[] = line.split("\\s+", 8);
                String fd = tokens[5];
                String location = tokens[7];
                descriptors.put(fd, location);
            }
            fdReader.close();
            return descriptors;
        } catch (Exception exception) {
            logger.log(Level.WARNING, "unable to retrieve file descriptors for " + pid, exception);
            return null;
        }
    }

    @Override
    public boolean shutdown() {
        // Stop the event reader and clear all audit rules.
        shutdown = true;
        try {
            Runtime.getRuntime().exec("auditctl -D").waitFor();
            Runtime.getRuntime().exec("auditctl -D").waitFor();
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
        }
        return true;
    }

    private void parseEventLine(String line) {
        Matcher event_start_matcher = pattern_message_start.matcher(line);
        if (event_start_matcher.find()) {
            String type = event_start_matcher.group(1);
            String time = event_start_matcher.group(2);
            String eventId = event_start_matcher.group(3);
            String messageData = line.substring(event_start_matcher.end());

            if (type.equals("SYSCALL")) {
                Matcher key_value_matcher = pattern_key_value.matcher(messageData);
                Map<String, String> eventData = new HashMap<String, String>();
                while (key_value_matcher.find()) {
                    eventData.put(key_value_matcher.group(1), key_value_matcher.group(2));
                }
                eventData.put("time", time);
                eventBuffer.put(eventId, eventData);
            } else {
                if (!eventBuffer.containsKey(eventId)) {
                    logger.log(Level.WARNING, "eventid {0} not found for message: {1}", new Object[]{eventId, line});
                    return;
                }
                if (type.equals("EOE")) {
                    finishEvent(eventId);
                } else if (type.equals("CWD")) {
                    Matcher cwd_matcher = pattern_cwd.matcher(messageData);
                    if (cwd_matcher.find()) {
                        String cwd = cwd_matcher.group(1);
                        eventBuffer.get(eventId).put("cwd", cwd);
                    }
                } else if (type.equals("PATH")) {
                    Matcher path_matcher = pattern_path.matcher(messageData);
                    if (path_matcher.find()) {
                        String item = path_matcher.group(1);
                        String name = path_matcher.group(2);
                        eventBuffer.get(eventId).put("path" + item, name);
                    }
                } else if (type.equals("EXECVE")) {
                    Matcher key_value_matcher = pattern_key_value.matcher(messageData);
                    while (key_value_matcher.find()) {
                        eventBuffer.get(eventId).put("execve_" + key_value_matcher.group(1), key_value_matcher.group(2));
                    }
                } else if (type.equals("FD_PAIR")) {
                    Matcher key_value_matcher = pattern_key_value.matcher(messageData);
                    while (key_value_matcher.find()) {
                        eventBuffer.get(eventId).put(key_value_matcher.group(1), key_value_matcher.group(2));
                    }
                } else if (type.equals("SOCKETCALL")) {
                    Matcher key_value_matcher = pattern_key_value.matcher(messageData);
                    while (key_value_matcher.find()) {
                        eventBuffer.get(eventId).put("socketcall_" + key_value_matcher.group(1), key_value_matcher.group(2));
                    }
                } else if (type.equals("SOCKADDR")) {
                    Matcher key_value_matcher = pattern_key_value.matcher(messageData);
                    while (key_value_matcher.find()) {
                        eventBuffer.get(eventId).put(key_value_matcher.group(1), key_value_matcher.group(2));
                    }
                } else {
                    logger.log(Level.WARNING, "unknown type {0} for message: {1}", new Object[]{type, line});
                }
            }
        } else {
            logger.log(Level.WARNING, "unable to match line: {0}", line);
        }
    }

    private void finishEvent(String eventId) {
        try {
            // System call numbers are derived from:
            // https://android.googlesource.com/platform/bionic/+/android-4.1.1_r1/libc/SYSCALLS.TXT
            if (!eventBuffer.containsKey(eventId)) {
                return;
            }
            Map<String, String> eventData = eventBuffer.get(eventId);
            int syscall = Integer.parseInt(eventData.get("syscall"));

            switch (syscall) {
                case 2: // fork()
                case 190: // vfork()
                    processForkClone(eventData, SYSCALL.FORK);
                    break;

                case 120: // clone()
                    processForkClone(eventData, SYSCALL.CLONE);
                    break;

                case 11: // execve()
                    processExecve(eventData);
                    break;

                case 5: // open()
                    processOpen(eventData);
                    break;

                case 6: // close()
                    processClose(eventData);
                    break;

                case 3: // read()
                case 145: // readv()
                    processRead(eventData);
                    break;

                case 4: // write()
                case 146: // writev()
                    processWrite(eventData);
                    break;

                case 54: // ioctl()
                    processIoctl(eventData);
                    break;

                case 9: // link()
                case 83: // symlink()
                    processLink(eventData);
                    break;

                case 10: // unlink()
                    break;

                case 14: // mknod()
                    break;

                case 38: // rename()
                    processRename(eventData);
                    break;

                case 42: // pipe()
                case 331: // pipe2()
                case 359: // pipe2()
                    processPipe(eventData);
                    break;

                case 41: // dup()
                case 63: // dup2()
                    processDup(eventData);
                    break;

                case 203: // setreuid()
                case 208: // setresuid()
                case 213: // setuid()
                    processSetuid(eventData);
                    break;

                case 15: // chmod()
                    processChmod(eventData, SYSCALL.CHMOD);
                    break;

                case 94: // fchmod()
                    processChmod(eventData, SYSCALL.FCHMOD);
                    break;

                case 102: // socketcall()
                    processSocketCall(eventData);
                    break;

                //////////////////////////////////////////////////////////////////

                case 281: // socket()
                    break;

                case 290: // sendto()
                case 296: // sendmsg()
                    break;

                case 292: // recvfrom()
                case 297: // recvmsg()
                    break;

                default:
                    break;
            }
            eventBuffer.remove(eventId);
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
        }
    }

    private void processForkClone(Map<String, String> eventData, SYSCALL syscall) {
        // fork() and clone() receive the following message(s):
        // - SYSCALL
        // - EOE
        String time = eventData.get("time");
        String oldPID = eventData.get("pid");
        String newPID = eventData.get("exit");
        checkProcessVertex(eventData, true, false);

        Process newProcess = new Process();
        String uid = String.format("%s\t%s\t%s\t%s", eventData.get("uid"), eventData.get("euid"), eventData.get("suid"), eventData.get("fsuid"));
        String gid = String.format("%s\t%s\t%s\t%s", eventData.get("gid"), eventData.get("egid"), eventData.get("sgid"), eventData.get("fsgid"));
        newProcess.addAnnotation("pid", newPID);
        newProcess.addAnnotation("ppid", oldPID);
        newProcess.addAnnotation("uid", uid);
        newProcess.addAnnotation("gid", gid);

        processes.put(newPID, newProcess);
        putVertex(newProcess);
        WasTriggeredBy wtb = new WasTriggeredBy(newProcess, processes.get(oldPID));
        wtb.addAnnotation("operation", syscall.name().toLowerCase());
        wtb.addAnnotation("time", time);
        putEdge(wtb);
        // Copy file descriptors from old process to new one
        if (fileDescriptors.containsKey(oldPID)) {
            Map<String, String> newfds = new HashMap<String, String>();
            for (Map.Entry<String, String> entry : fileDescriptors.get(oldPID).entrySet()) {
                newfds.put(entry.getKey(), entry.getValue());
            }
            fileDescriptors.put(newPID, newfds);
        }
    }

    private void processExecve(Map<String, String> eventData) {
        // execve() receives the following message(s):
        // - SYSCALL
        // - EXECVE
        // - BPRM_FCAPS (ignored)
        // - CWD
        // - PATH
        // - PATH
        // - EOE
        String pid = eventData.get("pid");
        String time = eventData.get("time");

        Process newProcess = checkProcessVertex(eventData, false, true);
        if (!newProcess.getAnnotations().containsKey("commandline")) {
            // Unable to retrieve complete process information; generate vertex
            // based on audit information
            int argc = Integer.parseInt(eventData.get("execve_argc"));
            String commandline = "";
            for (int i = 0; i < argc; i++) {
                commandline += eventData.get("execve_a" + i) + " ";
            }
            commandline = commandline.trim();
            newProcess.addAnnotation("cwd", eventData.get("cwd"));
            newProcess.addAnnotation("commandline", commandline);
        }
        putVertex(newProcess);
        WasTriggeredBy wtb = new WasTriggeredBy(newProcess, processes.get(pid));
        wtb.addAnnotation("operation", "execve");
        wtb.addAnnotation("time", time);
        putEdge(wtb);
        processes.put(pid, newProcess);
    }

    private void processOpen(Map<String, String> eventData) {
        // open() receives the following message(s):
        // - SYSCALL
        // - CWD
        // - PATH
        // - EOE
        String pid = eventData.get("pid");
        String cwd = eventData.get("cwd");
        String path = eventData.get("path0");
        String fd = eventData.get("exit");
        checkProcessVertex(eventData, true, false);

        if (!path.startsWith("/")) {
            path = joinPaths(cwd, path);
        }
        addDescriptor(pid, fd, path);
    }

    private void processClose(Map<String, String> eventData) {
        // close() receives the following message(s):
        // - SYSCALL
        // - EOE
        String pid = eventData.get("pid");
        String hexFD = eventData.get("a0");
        checkProcessVertex(eventData, true, false);

        String fd = Integer.toString(Integer.parseInt(hexFD, 16));
        removeDescriptor(pid, fd);
    }

    private void processRead(Map<String, String> eventData) {
        // read() receives the following message(s):
        // - SYSCALL
        // - EOE
        String pid = eventData.get("pid");
        String hexFD = eventData.get("a0");
        checkProcessVertex(eventData, true, false);

        String fd = Integer.toString(Integer.parseInt(hexFD, 16));
        String time = eventData.get("time");

        if (fileDescriptors.containsKey(pid) && fileDescriptors.get(pid).containsKey(fd)) {
            String path = fileDescriptors.get(pid).get(fd);
            Artifact vertex = new Artifact();
            vertex.addAnnotation("location", path);
            // Look up version number in the version table if this is a normal file
            if ((path.startsWith("/") && !path.startsWith("/dev/"))) {
                int version = fileVersions.containsKey(path) ? fileVersions.get(path) : 0;
                fileVersions.put(path, version);
                vertex.addAnnotation("version", Integer.toString(version));
            }
            putVertex(vertex);
            Used used = new Used(processes.get(pid), vertex);
            used.addAnnotation("operation", "read");
            used.addAnnotation("time", time);
            putEdge(used);
        } else {
//            logger.log(Level.WARNING, "read(): fd {0} not found for pid {1}", new Object[]{fd, pid});
        }
    }

    private void processWrite(Map<String, String> eventData) {
        // write() receives the following message(s):
        // - SYSCALL
        // - EOE
        String pid = eventData.get("pid");
        checkProcessVertex(eventData, true, false);

        String hexFD = eventData.get("a0");
        String fd = Integer.toString(Integer.parseInt(hexFD, 16));
        String time = eventData.get("time");

        if (fileDescriptors.containsKey(pid) && fileDescriptors.get(pid).containsKey(fd)) {
            String path = fileDescriptors.get(pid).get(fd);
            Artifact vertex = new Artifact();
            vertex.addAnnotation("location", path);
            // Look up version number in the version table if this is a normal file
            if ((path.startsWith("/") && !path.startsWith("/dev/"))) {
                // Increment previous version number if it exists
                int version = fileVersions.containsKey(path) ? fileVersions.get(path) + 1 : 1;
                fileVersions.put(path, version);
                vertex.addAnnotation("version", Integer.toString(version));
            }
            putVertex(vertex);
            WasGeneratedBy wgb = new WasGeneratedBy(vertex, processes.get(pid));
            wgb.addAnnotation("operation", "write");
            wgb.addAnnotation("time", time);
            putEdge(wgb);
        } else {
//            logger.log(Level.WARNING, "write(): fd {0} not found for pid {1}", new Object[]{fd, pid});
        }
    }

    private void processIoctl(Map<String, String> eventData) {
        // ioctl() receives the following message(s):
        // - SYSCALL
        // - EOE
        String pid = eventData.get("pid");
        checkProcessVertex(eventData, true, false);

        String hexFD = eventData.get("a0");
        String fd = Integer.toString(Integer.parseInt(hexFD, 16));
        String time = eventData.get("time");

        if (fileDescriptors.containsKey(pid) && fileDescriptors.get(pid).containsKey(fd)) {
            String path = fileDescriptors.get(pid).get(fd);
            Artifact vertex = new Artifact();
            vertex.addAnnotation("location", path);
            putVertex(vertex);
            WasGeneratedBy wgb = new WasGeneratedBy(vertex, processes.get(pid));
            wgb.addAnnotation("operation", "ioctl");
            wgb.addAnnotation("time", time);
            putEdge(wgb);
        } else {
//            logger.log(Level.WARNING, "ioctl(): fd {0} not found for pid {1}", new Object[]{fd, pid});
        }
    }

    private void processDup(Map<String, String> eventData) {
        // dup() and dup2() receive the following message(s):
        // - SYSCALL
        // - EOE
        String pid = eventData.get("pid");
        checkProcessVertex(eventData, true, false);

        String hexFD = eventData.get("a0");
        String fd = Integer.toString(Integer.parseInt(hexFD, 16));
        String newFD = eventData.get("exit");
        if (fileDescriptors.containsKey(pid)
                && fileDescriptors.get(pid).containsKey(fd)) {
            String path = fileDescriptors.get(pid).get(fd);
            fileDescriptors.get(pid).put(newFD, path);
        }
    }

    private void processSetuid(Map<String, String> eventData) {
        // setuid() receives the following message(s):
        // - SYSCALL
        // - EOE
        String time = eventData.get("time");
        String pid = eventData.get("pid");
        checkProcessVertex(eventData, true, false);
        Process newProcess = checkProcessVertex(eventData, false, false);
        putVertex(newProcess);
        WasTriggeredBy wtb = new WasTriggeredBy(newProcess, processes.get(pid));
        wtb.addAnnotation("operation", "setuid");
        wtb.addAnnotation("time", time);
        putEdge(wtb);
        processes.put(pid, newProcess);
    }

    private void processRename(Map<String, String> eventData) {
        // rename() receives the following message(s):
        // - SYSCALL
        // - CWD
        // - PATH 0 is directory of <src>
        // - PATH 1 is directory of <dst>
        // - PATH 2 is path of <src> relative to <cwd>
        // - PATH 3 is path of <dst> relative to <cwd>
        // - EOE
        // we use cwd and paths 2 and 3
        String time = eventData.get("time");
        String pid = eventData.get("pid");
        String cwd = eventData.get("cwd");
        checkProcessVertex(eventData, true, false);

        String srcpath = joinPaths(cwd, eventData.get("path2"));
        String dstpath = joinPaths(cwd, eventData.get("path3"));

        Artifact srcVertex = new Artifact();
        srcVertex.addAnnotation("location", srcpath);
        // Look up version number in the version table if it exists and remove it
        int version = fileVersions.containsKey(srcpath) ? fileVersions.remove(srcpath) : 0;
        srcVertex.addAnnotation("version", Integer.toString(version));
        putVertex(srcVertex);
        Used used = new Used(processes.get(pid), srcVertex);
        used.addAnnotation("operation", "read");
        used.addAnnotation("time", time);
        putEdge(used);

        Artifact dstVertex = new Artifact();
        dstVertex.addAnnotation("location", dstpath);
        dstVertex.addAnnotation("version", "1");
        fileVersions.put(dstpath, 1);
        putVertex(dstVertex);
        WasGeneratedBy wgb = new WasGeneratedBy(dstVertex, processes.get(pid));
        wgb.addAnnotation("operation", "write");
        wgb.addAnnotation("time", time);
        putEdge(wgb);

        WasDerivedFrom wdf = new WasDerivedFrom(dstVertex, srcVertex);
        wdf.addAnnotation("operation", "rename");
        wdf.addAnnotation("time", time);
        putEdge(wdf);
    }

    private void processLink(Map<String, String> eventData) {
        // link() and symlink() receive the following message(s):
        // - SYSCALL
        // - CWD
        // - PATH 0 is path of <src> relative to <cwd>
        // - PATH 1 is directory of <dst>
        // - PATH 2 is path of <dst> relative to <cwd>
        // - EOE
        // we use cwd and paths 0 and 2
        String time = eventData.get("time");
        String pid = eventData.get("pid");
        String cwd = eventData.get("cwd");
        checkProcessVertex(eventData, true, false);

        String srcpath = joinPaths(cwd, eventData.get("path0"));
        String dstpath = joinPaths(cwd, eventData.get("path2"));

        Artifact srcVertex = new Artifact();
        srcVertex.addAnnotation("location", srcpath);
        // Look up version number in the version table if it exists and remove it
        int version = fileVersions.containsKey(srcpath) ? fileVersions.get(srcpath) : 0;
        srcVertex.addAnnotation("version", Integer.toString(version));
        putVertex(srcVertex);
        Used used = new Used(processes.get(pid), srcVertex);
        used.addAnnotation("operation", "read");
        used.addAnnotation("time", time);
        putEdge(used);

        Artifact dstVertex = new Artifact();
        dstVertex.addAnnotation("location", dstpath);
        dstVertex.addAnnotation("version", "1");
        fileVersions.put(dstpath, 1);
        putVertex(dstVertex);
        WasGeneratedBy wgb = new WasGeneratedBy(dstVertex, processes.get(pid));
        wgb.addAnnotation("operation", "write");
        wgb.addAnnotation("time", time);
        putEdge(wgb);

        WasDerivedFrom wdf = new WasDerivedFrom(dstVertex, srcVertex);
        wdf.addAnnotation("operation", "link");
        wdf.addAnnotation("time", time);
        putEdge(wdf);
    }

    private void processChmod(Map<String, String> eventData, SYSCALL syscall) {
        // chmod() receives the following message(s):
        // - SYSCALL
        // - CWD
        // - PATH
        // - EOE
        String time = eventData.get("time");
        String pid = eventData.get("pid");
        checkProcessVertex(eventData, true, false);

        // mode is in hex format in <a1>
        String mode = Integer.toOctalString(Integer.parseInt(eventData.get("a1"), 16));
        // if syscall is chmod, then path is <path0> relative to <cwd>
        // if syscall is fchmod, look up file descriptor which is <a0>
        Artifact vertex = new Artifact();
        String path = null;
        if (syscall == SYSCALL.CHMOD) {
            path = joinPaths(eventData.get("cwd"), eventData.get("path0"));
        } else if (syscall == SYSCALL.FCHMOD) {
            String fd = eventData.get("a0");
            if (fileDescriptors.containsKey(pid) && fileDescriptors.get(pid).containsKey(fd)) {
                path = fileDescriptors.get(pid).get(fd);
            }
        }
        int version = fileVersions.containsKey(path) ? fileVersions.get(path) + 1 : 1;
        vertex.addAnnotation("location", path);
        vertex.addAnnotation("version", Integer.toString(version));
        putVertex(vertex);

        WasGeneratedBy wgb = new WasGeneratedBy(vertex, processes.get(pid));
        wgb.addAnnotation("operation", syscall.name().toLowerCase());
        wgb.addAnnotation("mode", mode);
        wgb.addAnnotation("time", time);
        putEdge(wgb);
    }

    private void processPipe(Map<String, String> eventData) {
        // pipe() receives the following message(s):
        // - SYSCALL
        // - FD_PAIR
        // - EOE
        String pid = eventData.get("pid");
        checkProcessVertex(eventData, true, false);

        String fd0 = eventData.get("fd0");
        String fd1 = eventData.get("fd1");
        String location = "pipe:[" + fd0 + "-" + fd1 + "]";
        addDescriptor(pid, fd0, location);
        addDescriptor(pid, fd1, location);
    }

    private void processSocketCall(Map<String, String> eventData) {
        String time = eventData.get("time");
        String pid = eventData.get("pid");
        String saddr = eventData.get("saddr");
        // continue if this is an AF_INET socket address
        if ((saddr.length() == 16) && (saddr.charAt(1) == '2')) {
            String port = Integer.toString(Integer.parseInt(saddr.substring(4, 8), 16));
            int oct1 = Integer.parseInt(saddr.substring(8, 10), 16);
            int oct2 = Integer.parseInt(saddr.substring(10, 12), 16);
            int oct3 = Integer.parseInt(saddr.substring(12, 14), 16);
            int oct4 = Integer.parseInt(saddr.substring(14, 16), 16);
            String address = String.format("%d.%d.%d.%d", oct1, oct2, oct3, oct4);
            Artifact network = new Artifact();
            network.addAnnotation("subtype", "network");
            network.addAnnotation("address", address);
            network.addAnnotation("port", port);
            putVertex(network);
            int callType = Integer.parseInt(eventData.get("socketcall_a0"));
            // socketcall number is derived from /usr/include/linux/net.h
            if (callType == 3) {
                // connect()
                WasGeneratedBy wgb = new WasGeneratedBy(network, processes.get(pid));
                wgb.addAnnotation("time", time);
                wgb.addAnnotation("operation", "connect");
                putEdge(wgb);
            } else if (callType == 5) {
                // accept()
                Used used = new Used(processes.get(pid), network);
                used.addAnnotation("time", time);
                used.addAnnotation("operation", "accept");
                putEdge(used);
            }
        }
    }

    private String joinPaths(String path1, String path2) {
        if (path1.endsWith("/") && path2.startsWith("/")) {
            return path1 + path2.substring(1);
        } else if (!path1.endsWith("/") && !path2.startsWith("/")) {
            return path1 + "/" + path2;
        } else {
            return path1 + path2;
        }
    }

    private void addDescriptor(String pid, String fd, String path) {
        if (fileDescriptors.containsKey(pid)) {
            fileDescriptors.get(pid).put(fd, path);
        } else {
            Map<String, String> fdMap = new HashMap<String, String>();
            fdMap.put(fd, path);
            fileDescriptors.put(pid, fdMap);
        }
    }

    private void removeDescriptor(String pid, String fd) {
        if (fileDescriptors.containsKey(pid)) {
            fileDescriptors.get(pid).remove(fd);
        } else {
            logger.log(Level.WARNING, "remove descriptor: fd {0} not found for pid {1}", new Object[]{fd, pid});
        }
    }

    private Process checkProcessVertex(Map<String, String> eventData, boolean link, boolean refresh) {
        String pid = eventData.get("pid");
        if (processes.containsKey(pid) && !refresh) {
            return processes.get(pid);
        }
        Process resultProcess = USE_PROCFS ? createProcessFromProc(pid) : null;
        if (resultProcess == null) {
            resultProcess = new Process();
            String ppid = eventData.get("ppid");
            String uid = String.format("%s\t%s\t%s\t%s", eventData.get("uid"), eventData.get("euid"), eventData.get("suid"), eventData.get("fsuid"));
            String gid = String.format("%s\t%s\t%s\t%s", eventData.get("gid"), eventData.get("egid"), eventData.get("sgid"), eventData.get("fsgid"));
            resultProcess.addAnnotation("name", eventData.get("comm"));
            resultProcess.addAnnotation("pid", pid);
            resultProcess.addAnnotation("ppid", ppid);
            resultProcess.addAnnotation("uid", uid);
            resultProcess.addAnnotation("gid", gid);
        }
        if (link == true) {
            Map<String, String> fds = getFileDescriptors(pid);
            if (fds != null) {
                fileDescriptors.put(pid, fds);
            }
            putVertex(resultProcess);
            processes.put(pid, resultProcess);
            String ppid = resultProcess.getAnnotation("ppid");
            if (processes.containsKey(ppid)) {
                WasTriggeredBy wtb = new WasTriggeredBy(resultProcess, processes.get(ppid));
                putEdge(wtb);
            }
        }
        return resultProcess;
    }

    private Process createProcessFromProc(String pid) {
        // Check if this pid exists in the /proc/ filesystem
        if ((new java.io.File("/proc/" + pid).exists())) {
            // The process vertex is created using the proc filesystem.
            try {
                Process newProcess = new Process();
                BufferedReader procReader = new BufferedReader(new FileReader("/proc/" + pid + "/status"));
                String nameline = procReader.readLine();
                procReader.readLine();
                procReader.readLine();
                procReader.readLine();
                String ppidline = procReader.readLine();
                procReader.readLine();
                String uidline = procReader.readLine();
                String gidline = procReader.readLine();
                procReader.close();

                BufferedReader statReader = new BufferedReader(new FileReader("/proc/" + pid + "/stat"));
                String statline = statReader.readLine();
                statReader.close();

                BufferedReader cmdlineReader = new BufferedReader(new FileReader("/proc/" + pid + "/cmdline"));
                String cmdline = cmdlineReader.readLine();
                cmdlineReader.close();

                String stats[] = statline.split("\\s+");
                long elapsedtime = Long.parseLong(stats[21]) * 10;
                long starttime = boottime + elapsedtime;
                String stime_readable = new java.text.SimpleDateFormat(simpleDatePattern).format(new java.util.Date(starttime));
                String stime = Long.toString(starttime);

                String name = nameline.split("\\s+")[1];
                String ppid = ppidline.split("\\s+")[1];
                String uid = uidline.split("\\s+", 2)[1];
                String gid = gidline.split("\\s+", 2)[1];
                cmdline = (cmdline == null) ? "" : cmdline.replace("\0", " ").replace("\"", "'").trim();

                newProcess.addAnnotation("name", name);
                newProcess.addAnnotation("pid", pid);
                newProcess.addAnnotation("ppid", ppid);
                newProcess.addAnnotation("uid", uid);
                newProcess.addAnnotation("gid", gid);
                newProcess.addAnnotation("starttime_unix", stime);
                newProcess.addAnnotation("starttime_simple", stime_readable);
                newProcess.addAnnotation("commandline", cmdline);
                return newProcess;
            } catch (Exception exception) {
                logger.log(Level.WARNING, "unable to create process vertex for pid " + pid + " from /proc/", exception);
                return null;
            }
        } else {
            return null;
        }
    }
}
