/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2016 SRI International

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

import spade.core.AbstractReporter;
import spade.edge.opm.Used;
import spade.edge.opm.WasDerivedFrom;
import spade.edge.opm.WasGeneratedBy;
import spade.edge.opm.WasTriggeredBy;
import spade.vertex.opm.Artifact;
import spade.vertex.opm.Process;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author Hasanat Kazmi
 */
public class StraceLinux extends AbstractReporter {

    PrintWriter logWriter;
    final boolean LOG_DEBUG_INFO = true;
    final int THREAD_SLEEP_DELAY = 5;
    volatile boolean shutdown = false;
    static final Logger logger = Logger.getLogger(StraceLinux.class.getName());
    static final Pattern eventPattern = Pattern.compile("([0-9]+)\\s+([\\d]+:[\\d]+:[\\d]+\\.[\\d]+)\\s+(\\w+)\\((.*)\\)\\s+=\\s+(\\-?[0-9]+).*");
    static final Pattern eventIncompletePattern = Pattern.compile("([0-9]+)\\s+(.*) <unfinished \\.\\.\\.>");
    static final Pattern eventCompletorPattern = Pattern.compile("([0-9]+)\\s+.*<... \\w+ resumed> (.*)");
    String DEBUG_FILE_PATH = "/tmp/spade-strace-debug.txt";
    String TEMP_FILE_PATH = "/tmp/spade-strace-output.txt";
    Map<String, String> incompleteEvents = new HashMap<String, String>();
    Map<String, String> fileDescriptors = new HashMap<String, String>();
    Map<String, Integer> fileVersions = new HashMap<String, Integer>();
    Map<String, Process> processes = new HashMap<String, Process>();
    List<Set<String>> sharedDescriptorTables = new ArrayList<Set<String>>();
    final String simpleDatePattern = "EEE MMM d H:mm:ss yyyy";
    String templine = null;

    private void log(String message) {
        logger.log(Level.INFO, message);
        if (LOG_DEBUG_INFO) {
            logWriter.println(message);
        }
    }

    @Override
    public boolean launch(String arguments) {
		// arguments is command to run
        if (arguments == null || arguments.equals("")) {
            return false;
        }

        final String command = arguments;

        fileDescriptors.put("0", "stdin");
        fileDescriptors.put("1", "stdout");
        fileDescriptors.put("2", "stderr");

        try {
            if (LOG_DEBUG_INFO) {
                logWriter = new PrintWriter(new FileWriter(DEBUG_FILE_PATH, false));
            }

            Runnable traceProcessor = new Runnable() {
                public void run() {
                    try {
                        String straceCmdLine = "strace -e fork,read,write,open,close,link,execve,mknod,rename,dup,dup2,symlink,";
                        straceCmdLine += "clone,vfork,setuid32,setgid32,chmod,fchmod,pipe,truncate,ftruncate,";
                        straceCmdLine += "readv,recv,recvfrom,recvmsg,send,sendto,sendmsg,connect,accept";
                        straceCmdLine += " -f -F -tt -v -s 200";

                        straceCmdLine += " -o " + TEMP_FILE_PATH;
                        straceCmdLine += " " + command;
                        logger.log(Level.INFO, straceCmdLine);

                        java.lang.Process straceProcess = Runtime.getRuntime().exec(straceCmdLine);
                        BufferedReader traceReader = new BufferedReader(new FileReader(TEMP_FILE_PATH));

                        while (!shutdown) {
                            String line = traceReader.readLine();
                            if (line != null) {
                                parseEvent(line);
                            } else {
                                try {
                                    straceProcess.exitValue();
                                    logger.log(Level.INFO, "Process has ended. Its safe to remove Strace reporter");
                                    break;
                                } catch (Exception exception) {                        
                                   Thread.sleep(THREAD_SLEEP_DELAY);
                                }
                            }
                        }

                        if (LOG_DEBUG_INFO) {
                            logWriter.flush();
                            logWriter.close();
                        }
                        traceReader.close();
                        straceProcess.destroy();
                    } catch (Exception exception) {
                        logger.log(Level.SEVERE, null, exception);
                    }
                }
            };
            new Thread(traceProcessor, "strace-Thread").start();
            return true;
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
            return false;
        }
    }

    private void parseEvent(String line) {
        try {
            Matcher eventMatcher = eventPattern.matcher(line);
            Matcher incompleteMatcher = eventIncompletePattern.matcher(line);
            Matcher completorMatcher = eventCompletorPattern.matcher(line);

            boolean success = true;

            if (eventMatcher.matches()) {
                String pid = eventMatcher.group(1);
                String time = eventMatcher.group(2);
                String syscall = eventMatcher.group(3);
                String args = eventMatcher.group(4);
                String retVal = eventMatcher.group(5);

                if (!processes.containsKey(pid)) {
                    log(String.format("Process %s not seen before, generating:\t\t%s", pid, line));
                    checkProcessTree(pid);
                }

                if (retVal.equals("-1")) {
                    success = false;
                }

                if (syscall.equals("open")) {
                    if (!success) {
                        templine = null;
                        return;
                    }
                    String path = args.substring(1, args.lastIndexOf('\"'));
                    String fd = retVal;
                    if (!fileDescriptors.containsKey(fd)) {
                        fileDescriptors.put(fd, path);
                    }

                } else if (syscall.equals("close")) {
                    if (!success) {
                        templine = null;
                        return;
                    }
                    String fd = args;
                    if (fileDescriptors.containsKey(fd)) {
                        fileDescriptors.remove(fd);
                    }

                } else if (syscall.equals("dup") || syscall.equals("dup2")) {
                    if (!success) {
                        templine = null;
                        return;
                    }
                    String oldfd = (syscall.equals("dup")) ? args : args.substring(0, args.indexOf(','));
                    String newfd = retVal;

                    fileDescriptors.put(newfd, fileDescriptors.get(oldfd));
                    fileDescriptors.remove(oldfd);

                } else if (syscall.equals("write") || syscall.equals("pwrite") || syscall.equals("writev") || syscall.equals("send") || syscall.equals("sendto") || syscall.equals("sendmsg")) {
                    String fd = args.substring(0, args.indexOf(','));

                    Artifact vertex = new Artifact();
                    vertex.addAnnotation("location", fileDescriptors.get(fd));
                    putVertex(vertex);
                    WasGeneratedBy wgb = new WasGeneratedBy(vertex, processes.get(pid));
                    wgb.addAnnotation("operation", syscall);
                    wgb.addAnnotation("time", time);
                    wgb.addAnnotation("success", success ? "true" : "false");
                    putEdge(wgb);
                    // Extra checks for additional data
                    int firstIndex = args.indexOf("\"") + 1;
                    int secondIndex = args.lastIndexOf("\"");
                    if (firstIndex > 0 && secondIndex > -1) {
                        String data = args.substring(firstIndex, secondIndex);
                    } else {
                        log(String.format("%s() failed - descriptor %s not found:\t\t%s", syscall, fd, line));
                    }
                } else if (syscall.equals("read") || syscall.equals("pread") || syscall.equals("readv") || syscall.equals("recv") || syscall.equals("recvfrom") || syscall.equals("recvmsg")) {
                    String fd = args.substring(0, args.indexOf(','));
                    
                    Artifact vertex = new Artifact();
                    vertex.addAnnotation("location", fileDescriptors.get(fd));

                    putVertex(vertex);
                    Used used = new Used(processes.get(pid), vertex);
                    used.addAnnotation("operation", syscall);
                    used.addAnnotation("time", time);
                    used.addAnnotation("success", success ? "true" : "false");
                    putEdge(used);
                 
                } else if (syscall.equals("fork") || syscall.equals("vfork") || syscall.equals("clone")) {
                    if (!success) {
                        templine = null;
                        return;
                    }
                    String newPid = retVal;
                    Process oldProcess = processes.get(pid);
                    Process newProcess = new Process();
                    String ppid = syscall.equals("clone") ? oldProcess.getAnnotation("ppid") : oldProcess.getAnnotation("pid");
                    String tgid = syscall.equals("clone") ? oldProcess.getAnnotation("tgid") : newPid;
                    newProcess.addAnnotation("uid", oldProcess.getAnnotation("uid"));
                    newProcess.addAnnotation("gid", oldProcess.getAnnotation("gid"));
                    newProcess.addAnnotation("pid", newPid);
                    newProcess.addAnnotation("ppid", ppid);
                    newProcess.addAnnotation("tgid", tgid);
                    String name = getProcessName(newPid);
                    if (name != null) {
                        newProcess.addAnnotation("name", name);
                    }
                    String commandline = getProcessCommandLine(newPid);
                    if (commandline != null) {
                        newProcess.addAnnotation("commandline", commandline);
                    }
                    putVertex(newProcess);
                    processes.put(newPid, newProcess);
                    WasTriggeredBy wtb = new WasTriggeredBy(newProcess, oldProcess);
                    wtb.addAnnotation("operation", syscall);
                    wtb.addAnnotation("time", time);
                    putEdge(wtb);

                    if (syscall.equals("clone") && args.contains("CLONE_FILES")) {
                        for (int i = 0; i < sharedDescriptorTables.size(); i++) {
                            if (sharedDescriptorTables.get(i).contains(pid)) {
                                sharedDescriptorTables.get(i).add(newPid);
                                templine = null;
                                return;
                            }
                        }
                        Set<String> newSet = new HashSet<String>();
                        newSet.add(pid);
                        newSet.add(newPid);
                        sharedDescriptorTables.add(newSet);
                    }
                } else if (syscall.equals("pipe")) {
                    String fd1 = args.substring(1, args.indexOf(','));
                    String fd2 = args.substring(args.indexOf(',') + 1, args.length() - 1);
                    String path = "pipe" + args;

                } else if (syscall.equals("rename")) {
                    if (!success) {
                        templine = null;
                        return;
                    }
                    String from = args.substring(1, args.indexOf(',') - 1);
                    String to = args.substring(args.indexOf(',') + 3, args.length() - 1);
                    int version = 0;
                    if (fileVersions.containsKey(from)) {
                        version = fileVersions.get(from);
                        fileVersions.remove(from);
                    }
                    fileVersions.put(to, 1);
                    Artifact fromVertex = new Artifact();
                    fromVertex.addAnnotation("location", from);
                    fromVertex.addAnnotation("version", Integer.toString(version));
                    putVertex(fromVertex);
                    Used used = new Used(processes.get(pid), fromVertex);
                    used.addAnnotation("time", time);
                    putEdge(used);
                    Artifact toVertex = new Artifact();
                    toVertex.addAnnotation("location", to);
                    toVertex.addAnnotation("version", "1");
                    putVertex(toVertex);
                    WasGeneratedBy wgb = new WasGeneratedBy(toVertex, processes.get(pid));
                    wgb.addAnnotation("time", time);
                    putEdge(wgb);
                    WasDerivedFrom wdf = new WasDerivedFrom(toVertex, fromVertex);
                    wdf.addAnnotation("operation", syscall);
                    wdf.addAnnotation("time", time);
                    putEdge(wdf);
                } else if (syscall.equals("setuid32")) {
                    Process oldProcess = processes.get(pid);
                    Process newProcess = copyProcess(oldProcess);
                    newProcess.addAnnotation("uid", args);
                    putVertex(newProcess);
                    if (success) {
                        processes.put(pid, newProcess);
                    }
                    WasTriggeredBy wtb = new WasTriggeredBy(newProcess, oldProcess);
                    wtb.addAnnotation("operation", syscall);
                    wtb.addAnnotation("time", time);
                    wtb.addAnnotation("success", success ? "true" : "false");
                    putEdge(wtb);
                } else if (syscall.equals("setgid32")) {
                    Process oldProcess = processes.get(pid);
                    Process newProcess = copyProcess(oldProcess);
                    newProcess.addAnnotation("gid", args);
                    putVertex(newProcess);
                    if (success) {
                        processes.put(pid, newProcess);
                    }
                    WasTriggeredBy wtb = new WasTriggeredBy(newProcess, oldProcess);
                    wtb.addAnnotation("operation", syscall);
                    wtb.addAnnotation("time", time);
                    wtb.addAnnotation("success", success ? "true" : "false");
                    putEdge(wtb);
                } else if (syscall.equals("execve")) {
                    String commandline = args.substring(args.indexOf('[') + 1, args.indexOf(']')).replace(",", "").replace("\"", "");
                    Process oldProcess = processes.get(pid);
                    Process newProcess = copyProcess(oldProcess);
                    newProcess.addAnnotation("commandline", commandline);
                    putVertex(newProcess);
                    processes.put(pid, newProcess);
                    WasTriggeredBy wtb = new WasTriggeredBy(newProcess, oldProcess);
                    wtb.addAnnotation("operation", syscall);
                    wtb.addAnnotation("time", time);
                    putEdge(wtb);
                } else if (syscall.equals("connect")) {
                    String fd = args.substring(0, args.indexOf(','));
                    String socket = args.substring(args.indexOf('{'), args.indexOf('}') + 1);
                } else if (syscall.equals("chmod")) {
                    String path = args.substring(1, args.indexOf(',') - 1);
                    String mode = args.split(", ")[1];
                    Artifact vertex = new Artifact();
                    vertex.addAnnotation("location", path);
                    int version = 1;
                    if (fileVersions.containsKey(path)) {
                        // Increment previous version number
                        version = fileVersions.get(path) + 1;
                    }
                    fileVersions.put(path, version);
                    vertex.addAnnotation("version", Integer.toString(version));
                    putVertex(vertex);
                    WasGeneratedBy wgb = new WasGeneratedBy(vertex, processes.get(pid));
                    wgb.addAnnotation("operation", syscall);
                    wgb.addAnnotation("time", time);
                    wgb.addAnnotation("mode", mode);
                    wgb.addAnnotation("success", success ? "true" : "false");
                    putEdge(wgb);
                } else if (syscall.equals("ioctl")) {

                } else {
                    log(String.format("syscall %s() unrecognized:\t\t%s", syscall, line));
                }
            } else if (incompleteMatcher.matches()) {
                incompleteEvents.put(incompleteMatcher.group(1), incompleteMatcher.group(2));
            } else if (completorMatcher.matches()) {
                String completeEvent = completorMatcher.group(1) + " " + incompleteEvents.remove(completorMatcher.group(1)) + completorMatcher.group(2);
                parseEvent(completeEvent);
            } else if (templine == null) {
                templine = line;
                return;
            } else if (templine != null) {
                String finalline = templine + line;
                templine = null;
                parseEvent(finalline);
            }
            templine = null;
        } catch (Exception exception) {
            logger.log(Level.SEVERE, String.format("exception occurred on line: %s", line), exception);
        }
    }

    private void checkProcessTree(String pid) {
		// Check the process tree to ensure that the given PID exists in it. If
        // not, then add it and recursively check its parents so that this
        // process eventually joins the main process tree.
        try {
            if (processes.containsKey(pid)) {
                return;
            }
            Process processVertex = createProcess(pid);
            if (processVertex == null) {
                return;
            }
            putVertex(processVertex);
            processes.put(pid, processVertex);
            String ppid = processVertex.getAnnotation("ppid");
            if (ppid!=null && Integer.parseInt(ppid) >= 1) {
                checkProcessTree(ppid);
                WasTriggeredBy triggerEdge = new WasTriggeredBy((Process) processes.get(pid), (Process) processes.get(ppid));
                putEdge(triggerEdge);
            }
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
        }
    }

    private Process copyProcess(Process input) {
        Process output = new Process();
        for (Map.Entry<String, String> entry : input.getCopyOfAnnotations().entrySet()) {
            output.addAnnotation(entry.getKey(), entry.getValue());
        }
        return output;
    }

    private String getProcessCommandLine(String pid) {
        try {
            BufferedReader cmdlineReader = new BufferedReader(new FileReader("/proc/" + pid + "/cmdline"));
            String cmdline = cmdlineReader.readLine();
            cmdlineReader.close();
            cmdline = (cmdline == null) ? null : cmdline.replace("\0", " ").replace("\"", "'").trim();
            return cmdline;

        } catch (Exception exception) {
            return null;
        }
    }

    private String getProcessName(String pid) {
        try {
            BufferedReader namelineReader = new BufferedReader(new FileReader("/proc/" + pid + "/status"));
            String nameline = namelineReader.readLine();
            namelineReader.close();
            nameline = (nameline == null) ? null : nameline.split("\\s+", 2)[1];
            return nameline;

        } catch (Exception exception) {
            return null;
        }
    }

    private Process createProcess(String pid) {
        // The process vertex is created using the proc filesystem.
        try {
            Process newProcess = new Process();
            newProcess.addAnnotation("pid", pid);

            if (new File("/proc/" + pid + "/status").exists()) {
                BufferedReader procReader = new BufferedReader(new FileReader("/proc/" + pid + "/status"));
                String nameline = procReader.readLine();
                procReader.readLine();
                String tgidline = procReader.readLine();
                procReader.readLine();
                String ppidline = procReader.readLine();
                procReader.readLine();
                String uidline = procReader.readLine();
                String gidline = procReader.readLine();
                procReader.close();

                String name = nameline.split("\\s+", 2)[1];
                String ppid = ppidline.split("\\s+")[1];
                String uid = uidline.split("\\s+")[2];
                String gid = gidline.split("\\s+")[2];
                String tgid = tgidline.split("\\s+")[1];

                newProcess.addAnnotation("name", name);
                newProcess.addAnnotation("ppid", ppid);
                newProcess.addAnnotation("tgid", tgid);
                newProcess.addAnnotation("uid", uid);
                newProcess.addAnnotation("gid", gid);
            }

            if (new File("/proc/" + pid + "/cmdline").exists()) {
                BufferedReader cmdlineReader = new BufferedReader(new FileReader("/proc/" + pid + "/cmdline"));
                String cmdline = cmdlineReader.readLine();
                cmdlineReader.close();

                if (cmdline != null) {
                    cmdline = cmdline.replace("\0", " ").replace("\"", "'").trim();
                    newProcess.addAnnotation("commandline", cmdline);
                }
            }

            return newProcess;
        } catch (Exception exception) {
            log("unable to create process vertex for pid " + pid + " from /proc/");
            return null;
        }
    }

    @Override
    public boolean shutdown() {
        try {
            shutdown = true;
            return true;
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
            return false;
        }
    }
}
