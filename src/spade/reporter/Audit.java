/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2015 SRI International

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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import spade.core.AbstractReporter;
import spade.core.Settings;
import spade.edge.opm.Used;
import spade.edge.opm.WasDerivedFrom;
import spade.edge.opm.WasGeneratedBy;
import spade.edge.opm.WasTriggeredBy;
import spade.vertex.opm.Artifact;
import spade.vertex.opm.Process;

/**
 *
 * @author Dawood Tariq, Sharjeel Ahmed Qureshi
 */
public class Audit extends AbstractReporter {

    private boolean DEBUG_DUMP_LOG; // Store log for debugging purposes
    private String DEBUG_DUMP_FILE;
    // //////////////////////////////////////////////////////////////////////////
    private BufferedReader eventReader;
    private volatile boolean shutdown = false;
    private long boottime = 0;
    private int binderTransaction = 0;
    private long THREAD_SLEEP_DELAY = 100;
    private long THREAD_CLEANUP_TIMEOUT = 1000;
    private final boolean USE_PROCFS = false;
    private boolean USE_OPEN_CLOSE = true;
    private boolean USE_SOCK_SEND_RCV = false; //to toggle monitoring of system calls: sendmsg, recvmsg, sendto, and recvfrom
    private boolean ARCH_32BIT = true;
    private final String simpleDatePattern = "EEE MMM d H:mm:ss yyyy";
    private static final String SPADE_ROOT = Settings.getProperty("spade_root");
    private String AUDIT_EXEC_PATH;
    // Process map based on <pid, vertex> pairs
    private Map<String, Process> processes = new HashMap<String, Process>();
    // File descriptor map based on <pid <fd, path>> pairs
    private Map<String, Map<String, String>> fileDescriptors = new HashMap<String, Map<String, String>>();
    // Event buffer map based on <audit_record_id <key, value>> pairs
    private Map<String, Map<String, String>> eventBuffer = new HashMap<String, Map<String, String>>();
    // File version map based on <path, version> pairs
    private Map<String, Integer> fileVersions = new HashMap<String, Integer>();
    // Set to cache paths that have received ioctl calls
    private Set<String> ioctlPaths = new HashSet<String>();
    // List of processes to ignore
    private String ignoreProcesses;
    static final Logger logger = Logger.getLogger(Audit.class.getName());
    // //////////////////////////////////////////////////////////////////////////
    // Group 1: key
    // Group 2: value
    private static Pattern pattern_key_value = Pattern.compile("(\\w+)=\"*((?<=\")[^\"]+(?=\")|([^\\s]+))\"*");
    // Group 1: node
    // Group 2: type
    // Group 3: time
    // Group 4: recordid
    private static Pattern pattern_message_start = Pattern.compile("node=(\\S+) type=(\\w*) msg=audit\\(([0-9\\.]+)\\:([0-9]+)\\):\\s*");
    // Group 1: cwd
    private static Pattern pattern_cwd = Pattern.compile("cwd=\"*((?<=\")[^\"]*(?=\"))\"*");
    // Group 1: item number
    // Group 2: name
    private static Pattern pattern_path = Pattern.compile("item=([0-9]*)\\s*name=\"*((?<=\")[^\"]*(?=\"))\"*");
    // Binder transaction log pattern
    private static Pattern binder_transaction = Pattern.compile("([0-9]+): ([a-z]+)\\s*from ([0-9]+):[0-9]+ to ([0-9]+):[0-9]+");

    /*
     *  Added to indicate in the output from where the process info was read. Either from 
     *  1) procfs or directly from 2) audit log. 
     */
    private static final String PROC_INFO_SRC_KEY = "source",
    							PROC_INFO_PROCFS = "/proc",
    							PROC_INFO_AUDIT = "/dev/audit";
    
    // //////////////////////////////////////////////////////////////////////////
    private enum SYSCALL {

        FORK, CLONE, CHMOD, FCHMOD, SENDTO, SENDMSG, RECVFROM, RECVMSG, TRUNCATE, FTRUNCATE
    }

    private Thread eventProcessorThread = null;
    private String auditRules;

    private static native int initAuditStream();

    private static native String readAuditStream();

    private static native int closeAuditStream();

    @Override
    public boolean launch(String arguments) {

        try {
            InputStream archStream = Runtime.getRuntime().exec("uname -i").getInputStream();
            BufferedReader archReader = new BufferedReader(new InputStreamReader(archStream));
            String archLine = archReader.readLine().trim();
            ARCH_32BIT = archLine.equals("i686");
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        if (arguments == null) {
            arguments = "";
        }

        AUDIT_EXEC_PATH = SPADE_ROOT + "lib/spadeLinuxAudit";
        // ignoreProcesses = "spadeLinuxAudit auditd kauditd java spade-server spade-controller";
        /* 
           	Removed java because that would exclude all other java processes too and removed
           	'spade-server' and 'spade-controller' because spade invocation method has been changed. 
           	Also added audispd to the ignore list.
        */
        ignoreProcesses = "spadeLinuxAudit auditd kauditd audispd";
        DEBUG_DUMP_FILE = SPADE_ROOT + "log/LinuxAudit.log";

        Map<String, String> args = parseKeyValPairs(arguments);
        if (args.containsKey("dump")) {
            DEBUG_DUMP_LOG = true;
            if (!args.get("dump").isEmpty()) {
                DEBUG_DUMP_FILE = args.get("dump");
            }
        } else {
            DEBUG_DUMP_LOG = false;
        }
        
        //check if file IO and net IO is also asked by the user to be turned on
        if(args.containsKey("fileio")){
        	if("true".equals(args.get("fileio"))){
        		USE_OPEN_CLOSE = false;
        	}
        }
        if(args.containsKey("netio")){
        	if("true".equals(args.get("netio"))){
        		USE_SOCK_SEND_RCV = true;
        	}
        }

        // Get system boot time from /proc/stat. This is later used to determine
        // the start
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
        rootVertex.addAnnotation("pidname", "BOOT");
        rootVertex.addAnnotation("pid", "0");
        rootVertex.addAnnotation("ppid", "0");
        String stime_readable = new java.text.SimpleDateFormat(simpleDatePattern).format(new java.util.Date(boottime));
        String stime = Long.toString(boottime);
        rootVertex.addAnnotation("boottime_unix", stime);
        rootVertex.addAnnotation("boottime_simple", stime_readable);
        rootVertex.addAnnotation(PROC_INFO_SRC_KEY, PROC_INFO_PROCFS);
        processes.put("0", rootVertex);
        putVertex(rootVertex);

        // Build the process tree using the directories under /proc/.
        // Directories
        // which have a numeric name represent processes.
        String path = "/proc";
        java.io.File folder = new java.io.File(path);
        java.io.File[] listOfFiles = folder.listFiles();
        for (int i = 0; i < listOfFiles.length; i++) {
            if (listOfFiles[i].isDirectory()) {

                String currentPID = listOfFiles[i].getName();
                try {
                    // Parse the current directory name to make sure it is
                    // numeric. If not,
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
            Runtime.getRuntime().exec("auditctl -D").waitFor();
            Runnable eventProcessor = new Runnable() {
                public void run() {
                    try {
                        FileWriter dumpFileWriter = null;
                        BufferedWriter dumpWriter = null;
                        if (DEBUG_DUMP_LOG) {
                            dumpFileWriter = new FileWriter(DEBUG_DUMP_FILE);
                            dumpWriter = new BufferedWriter(dumpFileWriter);
                        }

			java.lang.Process auditProcess = Runtime.getRuntime().exec(AUDIT_EXEC_PATH);
			eventReader = new BufferedReader(new InputStreamReader(auditProcess.getInputStream()));
			while (!shutdown) {
			    String line = eventReader.readLine();
			    if ((line != null) && !line.isEmpty()) {
				if (DEBUG_DUMP_LOG) {
				    dumpWriter.write(line);
				    dumpWriter.write(System.getProperty("line.separator"));
				    dumpWriter.flush();
				}
				parseEventLine(line);
			    }
			}
			//Added this command here because once the spadeLinuxAudit process has exited any rules involving it cannot be cleared.
			//So, deleting the rules before destroying the spadeLinuxAudit process.
			Runtime.getRuntime().exec("auditctl -D").waitFor();
			eventReader.close();
			auditProcess.destroy();

                        if (DEBUG_DUMP_LOG) {
                            dumpWriter.close();
                            dumpFileWriter.close();
                        }
                    } catch (Exception exception) {
                        logger.log(Level.SEVERE, null, exception);
                    }
                }
            };
            eventProcessorThread = new Thread(eventProcessor, "Audit-Thread");
            eventProcessorThread.start();

            // Determine pids of processes that are to be ignored. These are
            // appended to the audit rule.
            StringBuilder ignorePids = ignorePidsString(ignoreProcesses);
	    auditRules = "-a exit,always ";
	    if (!USE_OPEN_CLOSE) {
	    	auditRules += "-S read -S readv -S write -S writev ";
	    }
	    if(USE_SOCK_SEND_RCV){
	    	auditRules += "-S sendto -S recvfrom -S sendmsg -S recvmsg ";
	    }
	    auditRules += "-S link -S symlink -S clone -S fork -S vfork -S execve -S open -S close "
		+ "-S mknod -S rename -S dup -S dup2 -S setreuid -S setresuid -S setuid "
	    + "-S connect -S accept "
		+ "-S chmod -S fchmod -S pipe -S truncate -S ftruncate -S pipe2 -F success=1 "
		+ ignorePids.toString();
            Runtime.getRuntime().exec("auditctl " + auditRules).waitFor();
            logger.log(Level.INFO, "configured audit rules: {0}", auditRules);
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
            return false;
        }

        return true;
    }

    static private StringBuilder ignorePidsString(String ignoreProcesses) {
        try {
            Set<String> ignoreProcessSet = new HashSet<String>(Arrays.asList(ignoreProcesses.split("\\s+")));
            java.lang.Process pidChecker = Runtime.getRuntime().exec("ps axuc");
            BufferedReader pidReader = new BufferedReader(new InputStreamReader(pidChecker.getInputStream()));
            pidReader.readLine();
            String line;
            StringBuilder ignorePids = new StringBuilder();
            while ((line = pidReader.readLine()) != null) {
                String details[] = line.split("\\s+");
                String user = details[0];
                String pid = details[1];
                String name = details[details.length - 1].trim();
                if (user.equals("root") && ignoreProcessSet.contains(name)) {
                    ignorePids.append(" -F pid!=").append(pid);
                    ignorePids.append(" -F ppid!=").append(pid);
                }
            }
            pidReader.close();
            
            //get pid of the running jvm using /proc/self
            int selfpid = getSelfPid();
            if(selfpid != -1){
            	ignorePids.append(" -F pid!=").append(selfpid);
            	ignorePids.append(" -F ppid!=").append(selfpid);
            }
            
            return ignorePids;
        } catch (IOException e) {
            return new StringBuilder();
        }
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
                String tokens[] = line.split("\\s+");
                int toks = tokens.length;
                if (toks > 3) {
                    String fd = tokens[toks - 3];
                    String location = tokens[toks - 1];
                    descriptors.put(fd, location);
                }
            }
            fdReader.close();
            return descriptors;
        } catch (Exception exception) {
            logger.log(Level.WARNING, "Unable to retrieve file descriptors for " + pid, exception);
            return null;
        }
    }

    @Override
    public boolean shutdown() {
        // Stop the event reader and clear all audit rules.
        shutdown = true;
        try {
        	//The following command is being run from inside the event processor thread that listens to spadeLinuxAudit. Check there for reason.
            //Runtime.getRuntime().exec("auditctl -D").waitFor();
            eventProcessorThread.join(THREAD_CLEANUP_TIMEOUT);
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
        }
        return true;
    }

    private void parseEventLine(String line) {
        Matcher event_start_matcher = pattern_message_start.matcher(line);
        if (event_start_matcher.find()) {
            String node = event_start_matcher.group(1);
            String type = event_start_matcher.group(2);
            String time = event_start_matcher.group(3);
            String eventId = event_start_matcher.group(4);
            String messageData = line.substring(event_start_matcher.end());

            if (type.equals("SYSCALL")) {
                Map<String, String> eventData = parseKeyValPairs(messageData);
                eventData.put("time", time);
                eventBuffer.put(eventId, eventData);
            } else {
                if (!eventBuffer.containsKey(eventId)) {
                    // logger.log(Level.WARNING,
                    // "eventid {0} not found for message: {1}", new
                    // Object[]{eventId, line});
                    return;
                }
                if (type.equals("EOE")) {
                    if (ARCH_32BIT) {
                        finishEvent32(eventId);
                    } else {
                        finishEvent64(eventId);
                    }
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

    /*
     * Takes a string with keyvalue pairs and returns a Map Input e.g.
     * "key1=val1 key2=val2" etc. Input string validation is callee's
     * responsiblity
     */
    private static Map<String, String> parseKeyValPairs(String messageData) {
        Matcher key_value_matcher = pattern_key_value.matcher(messageData);
        Map<String, String> keyValPairs = new HashMap<String, String>();
        while (key_value_matcher.find()) {
            keyValPairs.put(key_value_matcher.group(1), key_value_matcher.group(2));
        }
        return keyValPairs;
    }

    private void finishEvent32(String eventId) {
        try {
            // System call numbers are derived from:
            // https://android.googlesource.com/platform/bionic/+/android-4.1.1_r1/libc/SYSCALLS.TXT
	    // TODO: Update the calls to make them linux specific.

            if (!eventBuffer.containsKey(eventId)) {
                logger.log(
		    Level.WARNING,
		    "EOE for eventID {0} received with no prior Event Info",
		    new Object[]{eventId});
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
                case 180: // pread64()
                    processRead(eventData);
                    break;

                case 4: // write()
                case 146: // writev()
                case 181: // pwrite64()
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

                case 92: // truncate()
                    processTruncate(eventData, SYSCALL.TRUNCATE);
                    break;

                case 93: // ftruncate()
                    processTruncate(eventData, SYSCALL.FTRUNCATE);
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

                case 290: // sendto()
                    processSend(eventData, SYSCALL.SENDTO);

                case 296: // sendmsg()
                    processSend(eventData, SYSCALL.SENDMSG);

                case 292: // recvfrom()
                    processRecv(eventData, SYSCALL.RECVFROM);

                case 297: // recvmsg()
                    processRecv(eventData, SYSCALL.RECVMSG);

                case 283: // connect()
                    processConnect(eventData);

                case 285: // accept()
                    processAccept(eventData);

                // ////////////////////////////////////////////////////////////////
                case 281: // socket()
                    break;

                default:
                    break;
            }
            eventBuffer.remove(eventId);
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
        }
    }

    private void finishEvent64(String eventId) {
        try {
            // System call numbers are derived from:
            // http://blog.rchapman.org/post/36801038863/linux-system-call-table-for-x86-64

            if (!eventBuffer.containsKey(eventId)) {
                logger.log(
		    Level.WARNING,
		    "EOE for eventID {0} received with no prior Event Info",
		    new Object[]{eventId});
                return;
            }
            Map<String, String> eventData = eventBuffer.get(eventId);
            int syscall = Integer.parseInt(eventData.get("syscall"));

            switch (syscall) {
                case 57: // fork()
                case 58: // vfork()
                    processForkClone(eventData, SYSCALL.FORK);
                    break;

                case 56: // clone()
                    processForkClone(eventData, SYSCALL.CLONE);
                    break;

                case 59: // execve()
                    processExecve(eventData);
                    break;

                case 2: // open()
                    processOpen(eventData);
                    break;

                case 3: // close()
                    processClose(eventData);
                    break;

                case 0: // read()
                case 19: // readv()
                case 17: // pread64()
                    processRead(eventData);
                    break;

                case 1: // write()
                case 28: // writev()
                case 18: // pwrite64()
                    processWrite(eventData);
                    break;

                case 16: // ioctl()
                    processIoctl(eventData);
                    break;

                case 86: // link()
                case 88: // symlink()
                    processLink(eventData);
                    break;

                case 87: // unlink()
                    break;

                case 133: // mknod()
                    break;

                case 82: // rename()
                    processRename(eventData);
                    break;

                case 22: // pipe()
                case 293: // pipe2()
                    processPipe(eventData);
                    break;

                case 32: // dup()
                case 33: // dup2()
                    processDup(eventData);
                    break;

                case 113: // setreuid()
                case 117: // setresuid()
                case 105: // setuid()
                    processSetuid(eventData);
                    break;

                case 76: // truncate()
                    processTruncate(eventData, SYSCALL.TRUNCATE);
                    break;

                case 77: // ftruncate()
                    processTruncate(eventData, SYSCALL.FTRUNCATE);
                    break;

                case 90: // chmod()
                    processChmod(eventData, SYSCALL.CHMOD);
                    break;

                case 91: // fchmod()
                    processChmod(eventData, SYSCALL.FCHMOD);
                    break;

//                case 102: // socketcall()
//                    processSocketCall(eventData);
//                    break;
                case 44: // sendto()
                    processSend(eventData, SYSCALL.SENDTO);

                case 46: // sendmsg()
                    processSend(eventData, SYSCALL.SENDMSG);

                case 45: // recvfrom()
                    processRecv(eventData, SYSCALL.RECVFROM);

                case 47: // recvmsg()
                    processRecv(eventData, SYSCALL.RECVMSG);

                case 42: // connect()
                    processConnect(eventData);

                case 43: // accept()
                    processAccept(eventData);

                // ////////////////////////////////////////////////////////////////
                case 41: // socket()
                    break;

                default:
                    break;
            }
            eventBuffer.remove(eventId);
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
        }
    }

    private Artifact createFileVertex(String path, boolean update) {
        Artifact fileArtifact = new Artifact();
        path = path.replace("//", "/");
        fileArtifact.addAnnotation("path", path);
        String[] filename = path.split("/");
        if (filename.length > 0) {
            fileArtifact.addAnnotation("filename", filename[filename.length - 1]);
        }
        int version = fileVersions.containsKey(path) ? fileVersions.get(path) : 0;
        if (update && path.startsWith("/") && !path.startsWith("/dev/")) {
            version++;
        }
        fileArtifact.addAnnotation("version", Integer.toString(version));
        fileVersions.put(path, version);
        return fileArtifact;
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
        newProcess.addAnnotation(PROC_INFO_SRC_KEY, PROC_INFO_AUDIT);

        processes.put(newPID, newProcess);
        putVertex(newProcess);
        WasTriggeredBy wtb = new WasTriggeredBy(newProcess, processes.get(oldPID));
        wtb.addAnnotation("operation", syscall.name().toLowerCase());
        wtb.addAnnotation("time", time);
        putEdge(wtb); // Copy file descriptors from old process to new one
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

        if (USE_OPEN_CLOSE) {
            String flags = eventData.get("a1");
            Map<String, String> newData = new HashMap<String, String>();
            newData.put("pid", pid);
            newData.put("a0", Integer.toHexString(Integer.parseInt(fd)));
            newData.put("time", eventData.get("time"));
            if (flags.charAt(flags.length() - 1) == '0') {
                // read
                processRead(newData);
            } else {
                // write
                processWrite(newData);
            }
        }
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
            boolean put = !fileVersions.containsKey(path);
            Artifact vertex = createFileVertex(path, false);
            if (put) {
                putVertex(vertex);
            }
            Used used = new Used(processes.get(pid), vertex);
            used.addAnnotation("operation", "read");
            used.addAnnotation("time", time);
            putEdge(used);
        } else {
            // logger.log(Level.WARNING, "read(): fd {0} not found for pid {1}",
            // new Object[]{fd, pid});
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
            Artifact vertex = createFileVertex(path, true);
            putVertex(vertex);
            WasGeneratedBy wgb = new WasGeneratedBy(vertex, processes.get(pid));
            wgb.addAnnotation("operation", "write");
            wgb.addAnnotation("time", time);
            putEdge(wgb);
        } else {
            // logger.log(Level.WARNING,
            // "write(): fd {0} not found for pid {1}", new Object[]{fd, pid});
        }
    }

    private void processTruncate(Map<String, String> eventData, SYSCALL syscall) {
        // write() receives the following message(s):
        // - SYSCALL
        // - EOE
        String pid = eventData.get("pid");
        checkProcessVertex(eventData, true, false);

        String time = eventData.get("time");
        String path = null;

        if (syscall == SYSCALL.TRUNCATE) {
            path = joinPaths(eventData.get("cwd"), eventData.get("path0"));
        } else if (syscall == SYSCALL.FTRUNCATE) {
            String hexFD = eventData.get("a0");
            String fd = Integer.toString(Integer.parseInt(hexFD, 16));
            if (fileDescriptors.containsKey(pid) && fileDescriptors.get(pid).containsKey(fd)) {
                path = fileDescriptors.get(pid).get(fd);
            } else {
                // logger.log(Level.WARNING,
                // "truncate(): fd {0} not found for pid {1}", new Object[]{fd,
                // pid});
                return;
            }
        }

        Artifact vertex = createFileVertex(path, true);
        putVertex(vertex);
        WasGeneratedBy wgb = new WasGeneratedBy(vertex, processes.get(pid));
        wgb.addAnnotation("operation", syscall.name().toLowerCase());
        wgb.addAnnotation("time", time);
        putEdge(wgb);
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
            vertex.addAnnotation("path", path);
            if (ioctlPaths.add(path)) {
                putVertex(vertex);
            }
            WasGeneratedBy wgb = new WasGeneratedBy(vertex, processes.get(pid));
            wgb.addAnnotation("operation", "ioctl");
            wgb.addAnnotation("time", time);
            putEdge(wgb);
        } else {
            // logger.log(Level.WARNING,
            // "ioctl(): fd {0} not found for pid {1}", new Object[]{fd, pid});
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
        if (fileDescriptors.containsKey(pid) && fileDescriptors.get(pid).containsKey(fd)) {
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

        boolean put = !fileVersions.containsKey(srcpath);
        Artifact srcVertex = createFileVertex(srcpath, false);
        if (put) {
            putVertex(srcVertex);
        }
        Used used = new Used(processes.get(pid), srcVertex);
        used.addAnnotation("operation", "read");
        used.addAnnotation("time", time);
        putEdge(used);

        Artifact dstVertex = createFileVertex(dstpath, true);
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

    private void processUnlink(Map<String, String> eventData) {
        // link() and symlink() receive the following message(s):
        // - SYSCALL
        // - CWD
        // - PATH 0 is directory containing the link
        // - PATH 1 is file that is linked
        // - EOE
        // we use path 1
        // -----------------------------------------------------------
        // NOTHING TO DO: NO PROVENANCE SEMANTICS
        // -----------------------------------------------------------
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

        boolean put = !fileVersions.containsKey(srcpath);
        Artifact srcVertex = createFileVertex(srcpath, false);
        if (put) {
            putVertex(srcVertex);
        }
        Used used = new Used(processes.get(pid), srcVertex);
        used.addAnnotation("operation", "read");
        used.addAnnotation("time", time);
        putEdge(used);

        Artifact dstVertex = createFileVertex(dstpath, true);
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
        String path = null;
        if (syscall == SYSCALL.CHMOD) {
            path = joinPaths(eventData.get("cwd"), eventData.get("path0"));
        } else if (syscall == SYSCALL.FCHMOD) {
            String fd = eventData.get("a0");
            if (fileDescriptors.containsKey(pid) && fileDescriptors.get(pid).containsKey(fd)) {
                path = fileDescriptors.get(pid).get(fd);
            }
        }
        Artifact vertex = createFileVertex(path, true);
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
        String location = null;
        String saddr = eventData.get("saddr");
        // continue if this is an AF_INET socket address
        if(saddr.charAt(1) == '2'){
            String port = Integer.toString(Integer.parseInt(saddr.substring(4, 8), 16));
            int oct1 = Integer.parseInt(saddr.substring(8, 10), 16);
            int oct2 = Integer.parseInt(saddr.substring(10, 12), 16);
            int oct3 = Integer.parseInt(saddr.substring(12, 14), 16);
            int oct4 = Integer.parseInt(saddr.substring(14, 16), 16);
            String address = String.format("%d.%d.%d.%d", oct1, oct2, oct3, oct4);
            location = String.format("address:%s, port:%s", address, port);
        }
        if (location != null) {
            Artifact network = createFileVertex(location, true);
            network.addAnnotation("subtype", "network");
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
            // update file descriptor table
            String hexFD = eventData.get("a0");
            String fd = Integer.toString(Integer.parseInt(hexFD, 16));
            addDescriptor(pid, fd, location);
        }
    }

    private void processConnect(Map<String, String> eventData) {
        String time = eventData.get("time");
        String pid = eventData.get("pid");
        String location = null;
        String saddr = eventData.get("saddr");
        // continue if this is an AF_INET socket address
        if(saddr.charAt(1) == '2'){
            String port = Integer.toString(Integer.parseInt(saddr.substring(4, 8), 16));
            int oct1 = Integer.parseInt(saddr.substring(8, 10), 16);
            int oct2 = Integer.parseInt(saddr.substring(10, 12), 16);
            int oct3 = Integer.parseInt(saddr.substring(12, 14), 16);
            int oct4 = Integer.parseInt(saddr.substring(14, 16), 16);
            String address = String.format("%d.%d.%d.%d", oct1, oct2, oct3, oct4);
            location = String.format("address:%s, port:%s", address, port);
        }
        if (location != null) {
            Artifact network = createFileVertex(location, true);
            network.addAnnotation("subtype", "network");
            putVertex(network);
            WasGeneratedBy wgb = new WasGeneratedBy(network, processes.get(pid));
            wgb.addAnnotation("time", time);
            wgb.addAnnotation("operation", "connect");
            putEdge(wgb);
            // update file descriptor table
            String hexFD = eventData.get("a0");
            String fd = Integer.toString(Integer.parseInt(hexFD, 16));
            addDescriptor(pid, fd, location);
        }
    }

    private void processAccept(Map<String, String> eventData) {
        String time = eventData.get("time");
        String pid = eventData.get("pid");
        String location = null;
        String saddr = eventData.get("saddr");
        // continue if this is an AF_INET socket address
        if(saddr.charAt(1) == '2'){
            String port = Integer.toString(Integer.parseInt(saddr.substring(4, 8), 16));
            int oct1 = Integer.parseInt(saddr.substring(8, 10), 16);
            int oct2 = Integer.parseInt(saddr.substring(10, 12), 16);
            int oct3 = Integer.parseInt(saddr.substring(12, 14), 16);
            int oct4 = Integer.parseInt(saddr.substring(14, 16), 16);
            String address = String.format("%d.%d.%d.%d", oct1, oct2, oct3, oct4);
            location = String.format("address:%s, port:%s", address, port);
        }
        if (location != null) {
            Artifact network = createFileVertex(location, false);
            network.addAnnotation("subtype", "network");
            putVertex(network);
            Used used = new Used(processes.get(pid), network);
            used.addAnnotation("time", time);
            used.addAnnotation("operation", "accept");
            putEdge(used);
            // update file descriptor table
            String hexFD = eventData.get("a0");
            String fd = Integer.toString(Integer.parseInt(hexFD, 16));
            addDescriptor(pid, fd, location);
        }
    }

    private void processSend(Map<String, String> eventData, SYSCALL syscall) {
        // sendto()/sendmsg() receive the following message(s):
        // - SYSCALL
        // - EOE
        String pid = eventData.get("pid");
        checkProcessVertex(eventData, true, false);

        String hexFD = eventData.get("a0");
        String fd = Integer.toString(Integer.parseInt(hexFD, 16));
        String time = eventData.get("time");

        if (fileDescriptors.containsKey(pid) && fileDescriptors.get(pid).containsKey(fd)) {
            String path = fileDescriptors.get(pid).get(fd);
            Artifact vertex = createFileVertex(path, true);
            vertex.addAnnotation("subtype", "network");
            putVertex(vertex);
            WasGeneratedBy wgb = new WasGeneratedBy(vertex, processes.get(pid));
            wgb.addAnnotation("operation", syscall.name().toLowerCase());
            wgb.addAnnotation("time", time);
            putEdge(wgb);
        } else {
            // logger.log(Level.WARNING, "send(): fd {0} not found for pid {1}",
            // new Object[]{fd, pid});
        }
    }

    private void processRecv(Map<String, String> eventData, SYSCALL syscall) {
        // sendto()/sendmsg() receive the following message(s):
        // - SYSCALL
        // - EOE
        String pid = eventData.get("pid");
        checkProcessVertex(eventData, true, false);

        String hexFD = eventData.get("a0");
        String fd = Integer.toString(Integer.parseInt(hexFD, 16));
        String time = eventData.get("time");

        if (fileDescriptors.containsKey(pid) && fileDescriptors.get(pid).containsKey(fd)) {
            String path = fileDescriptors.get(pid).get(fd);
            Artifact vertex = createFileVertex(path, false);
            vertex.addAnnotation("subtype", "network");
            putVertex(vertex);
            Used used = new Used(processes.get(pid), vertex);
            used.addAnnotation("operation", syscall.name().toLowerCase());
            used.addAnnotation("time", time);
            putEdge(used);
        } else {
            // logger.log(Level.WARNING, "recv(): fd {0} not found for pid {1}",
            // new Object[]{fd, pid});
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
            // logger.log(Level.WARNING,
            // "remove descriptor: fd {0} not found for pid {1}", new
            // Object[]{fd, pid});
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
            resultProcess.addAnnotation(PROC_INFO_SRC_KEY, PROC_INFO_AUDIT);
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
                // order of keys in the status file changed. So, now looping through the file to get the necessary ones
                int keysGottenCount = 0; //used to stop reading the file once all the required keys have been gotten
                String line = null, nameline = null, ppidline = null, uidline = null, gidline = null;
                BufferedReader procReader = new BufferedReader(new FileReader("/proc/" + pid + "/status"));
                while((line = procReader.readLine()) != null && keysGottenCount < 4){
                	String tokens[] = line.split(":");
                	String key = tokens[0].trim().toLowerCase();
                	switch(key){
                		case "name": nameline = line; keysGottenCount++; break;
                		case "ppid": ppidline = line; keysGottenCount++; break;
                		case "uid" : uidline  = line; keysGottenCount++; break;
                		case "gid" : gidline  = line; keysGottenCount++; break;
                		default: break;
                	}
                }
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
                
                newProcess.addAnnotation(PROC_INFO_SRC_KEY, PROC_INFO_PROCFS);
                return newProcess;
            } catch (Exception exception) {
                logger.log(Level.WARNING, "unable to create process vertex for pid " + pid + " from /proc/", exception);
                return null;
            }
        } else {
            return null;
        }
    }

    private static Integer getSelfPid() {
        try {
            return Integer.parseInt((new File("/proc/self")).getCanonicalFile().getName());
        } catch (Exception e) {
            return -1;
        }
    }

    /*
     * Simply reads audit stream and dumps it on stdout Mainly for testing and
     * analysis
     */
    public static void dump_stream(String args[]) {

        System.out.println("Dumping Stream! :-");

        try {
            String auditRules = "-a exit,always " + "-S clone -S fork -S vfork -S execve -S open -S close " + "-S read -S readv -S write -S writev -S link -S symlink "
                    + "-S mknod -S rename -S dup -S dup2 -S setreuid -S setresuid -S setuid " + "-S setreuid32 -S setresuid32 -S setuid32 -S chmod -S fchmod -S pipe "
                    + "-S connect -S accept -S sendto -S sendmsg -S recvfrom -S recvmsg " + "-S pread64 -S pwrite64 -S truncate -S ftruncate " + "-S pipe2 -F success=1 "
                    + ignorePidsString("spade-audit auditd kauditd /sbin/adbd /system/bin/qemud /system/bin/sh dalvikvm");
            System.out.println("Settings rules: " + auditRules);
            Runtime.getRuntime().exec("auditctl -D").waitFor();
            Runtime.getRuntime().exec("auditctl " + auditRules).waitFor();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        String outBuf = "";
        int status = initAuditStream();
        if (status != 0) {
            System.out.println("Unable to to open audit stream: " + String.valueOf(status));
        } else {
            System.out.println("Stream initalization");
        }

        try {
            while (true) {

                String audstream = readAuditStream();
                if (audstream != null) {
                    if (!audstream.isEmpty()) {
                        outBuf = outBuf + "\n" + audstream;
                    }
                } else {
                    break;
                }

                if (outBuf.length() > 5000) {
                    // Flush
                    System.out.println(outBuf);
                    outBuf = "";
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.out.println("End of stream!");
            closeAuditStream();
        }
    }

    public static void main(String args[]) {

        String action = args.length >= 1 ? args[0] : "";

        System.out.println("Provided args:");
        for (int i = 0; i < args.length; ++i) {
            System.out.println(args[i]);
        }
        System.out.println("---");

        if (action.equals("dump")) {
            dump_stream(args);
        }
    }
}
