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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Map.Entry;
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
 * @author Dawood
 */
public class strace extends AbstractReporter {

    BufferedReader reader;
    PrintWriter debugWriter;
    volatile boolean shutdown = false;
    final boolean DEBUG_INFO = true;
    static final Logger logger = Logger.getLogger(strace.class.getName());
    final Pattern eventPattern = Pattern.compile("([0-9]+)\\s+([\\d]+:[\\d]+:[\\d]+\\.[\\d]+)\\s+(\\w+)\\((.*)\\)\\s+=\\s+(\\-?[0-9]+).*");
    final Pattern eventIncompletePattern = Pattern.compile("([0-9]+)\\s+(.*) <unfinished \\.\\.\\.>");
    final Pattern eventCompletorPattern = Pattern.compile("([0-9]+)\\s+.*<... \\w+ resumed> (.*)");
    Map<String, String> incompleteEvents = new HashMap<String, String>();
    Map<String, Map<String, String>> fileDescriptors = new HashMap<String, Map<String, String>>();
    Map<String, Integer> fileVersions = new HashMap<String, Integer>();
    Map<String, Process> processes = new HashMap<String, Process>();
    List<Set<String>> sharedDescriptorTables = new ArrayList<Set<String>>();
    int lineNumber = 1;

    // syscall list:
    //      fork,read,write,open,close,link,unlink,execve,mknod,rename,dup,pipe,dup2,symlink,truncate,ftruncate,socketcall,clone,readv,writev,vfork,setreuid32,setresuid32,setuid32
    //      ioctl,pread,readv,preadv,pwrite,pwritev,recv,recvfrom,recvmsg,send,sendto,sendmsg,socket,connect,accept
    @Override
    public boolean launch(String arguments) {
        if (arguments == null) {
            return false;
        }
        try {
            BufferedReader fdReader = new BufferedReader(new FileReader("fds.txt"));
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
            fileDescriptors.put("33", descriptors);
            fdReader.close();

            if (DEBUG_INFO) {
                debugWriter = new PrintWriter(new FileWriter(arguments + ".dbg"));
            }
            reader = new BufferedReader(new FileReader(arguments));

            Runnable eventProcessor = new Runnable() {

                public void run() {
                    try {
                        while (!shutdown) {
                            String line = reader.readLine();
                            if (line != null) {
                                parseEvent(line);
                            } else {
                                break;
                            }
                            lineNumber++;
                        }
                    } catch (Exception exception) {
                        logger.log(Level.SEVERE, null, exception);
                    }
                }
            };
            new Thread(eventProcessor, "strace-Thread").start();

            return true;
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
            return false;
        }
    }

    private void checkProcess(String pid) {
        if (!processes.containsKey(pid)) {
            Process process = new Process();
            process.addAnnotation("pid", pid);
            putVertex(process);
            processes.put(pid, process);
        }
    }

    private boolean updateSharedDescriptorTables(String pid, String fd, String path, boolean add) {
        // Search for processes with shared file descriptor tables and update them as well
        for (int i = 0; i < sharedDescriptorTables.size(); i++) {
            if (sharedDescriptorTables.get(i).contains(pid)) {
                for (String sharedPid : sharedDescriptorTables.get(i)) {
                    if (add) {
                        if (!fileDescriptors.containsKey(sharedPid)) {
                            Map<String, String> descriptors = new HashMap<String, String>();
                            descriptors.put(fd, path);
                            fileDescriptors.put(sharedPid, descriptors);
                        } else {
                            fileDescriptors.get(sharedPid).put(fd, path);
                        }
                    } else {
                        if (fileDescriptors.containsKey(sharedPid) && fileDescriptors.get(sharedPid).containsKey(fd)) {
                            fileDescriptors.get(sharedPid).remove(fd);
                        }
                    }
                }
                return true;
            }
        }
        return false;
    }

    private void parseEvent(String line) {
        Matcher eventMatcher = eventPattern.matcher(line);
        Matcher incompleteMatcher = eventIncompletePattern.matcher(line);
        Matcher completorMatcher = eventCompletorPattern.matcher(line);

        if (eventMatcher.matches()) {
            String pid = eventMatcher.group(1);
            String time = eventMatcher.group(2);
            String syscall = eventMatcher.group(3);
            String args = eventMatcher.group(4);
            String retVal = eventMatcher.group(5);

            if (retVal.equals("-1")) {
                if (DEBUG_INFO) {
                    debugWriter.println(String.format("%d:\t%s() failed with code %s:\t\t%s", lineNumber, syscall, retVal, line));
                }
                return;
            }

            if (syscall.equals("open")) {
                String path = args.substring(1, args.lastIndexOf('\"'));
                String fd = retVal;
                // Search for processes with shared file descriptor tables and update them as well
                if (updateSharedDescriptorTables(pid, fd, path, true)) {
                    return;
                }
                // No shared processes found, just add file to own file descriptor table
                if (!fileDescriptors.containsKey(pid)) {
                    Map<String, String> descriptors = new HashMap<String, String>();
                    descriptors.put(fd, path);
                    fileDescriptors.put(pid, descriptors);
                } else {
                    fileDescriptors.get(pid).put(fd, path);
                }
            } else if (syscall.equals("close")) {
                String fd = args;
                // Search for processes with shared file descriptor tables and update them as well
                if (updateSharedDescriptorTables(pid, fd, null, false)) {
                    return;
                }
                if (fileDescriptors.containsKey(pid) && fileDescriptors.get(pid).containsKey(fd)) {
                    fileDescriptors.get(pid).remove(fd);
                } else {
                    if (DEBUG_INFO) {
                        debugWriter.println(String.format("%d:\t%s() failed - descriptor %s not found:\t\t%s", lineNumber, syscall, fd, line));
                    }
                }
            } else if (syscall.equals("dup") || syscall.equals("dup2")) {
                String oldfd = (syscall.equals("dup")) ? args : args.substring(0, args.indexOf(','));
                String newfd = retVal;
                if (fileDescriptors.containsKey(pid) && fileDescriptors.get(pid).containsKey(oldfd)) {
                    String path = fileDescriptors.get(pid).get(oldfd);
                    if (updateSharedDescriptorTables(pid, newfd, path, true)) {
                        return;
                    }
                    fileDescriptors.get(pid).put(newfd, path);
                } else {
                    if (DEBUG_INFO) {
                        debugWriter.println(String.format("%d:\t%s() failed - descriptor %s not found:\t\t%s", lineNumber, syscall, oldfd, line));
                    }
                }
            } else if (syscall.equals("write")
                    || syscall.equals("pwrite")
                    || syscall.equals("writev")
                    || syscall.equals("send")
                    || syscall.equals("sendmsg")) {
                String fd = args.substring(0, args.indexOf(','));
                if (fileDescriptors.containsKey(pid) && fileDescriptors.get(pid).containsKey(fd)) {
                    checkProcess(pid);
                    String path = fileDescriptors.get(pid).get(fd);
                    Artifact vertex = new Artifact();
                    vertex.addAnnotation("location", path);
                    // Look up version number in the version table if this is a normal file
                    if ((path.startsWith("/") && !path.startsWith("/dev/"))) {
                        int version = 1;
                        if (fileVersions.containsKey(path)) {
                            // Increment previous version number
                            version = fileVersions.get(path) + 1;
                        }
                        fileVersions.put(path, version);
                        vertex.addAnnotation("version", Integer.toString(version));
                    }
                    putVertex(vertex);
                    WasGeneratedBy wgb = new WasGeneratedBy(vertex, processes.get(pid));
                    wgb.addAnnotation("time", time);
                    putEdge(wgb);
                } else {
                    if (DEBUG_INFO) {
                        debugWriter.println(String.format("%d:\t%s() failed - descriptor %s not found:\t\t%s", lineNumber, syscall, fd, line));
                    }
                }
            } else if (syscall.equals("read")
                    || syscall.equals("pread")
                    || syscall.equals("readv")
                    || syscall.equals("recv")
                    || syscall.equals("recvmsg")) {
                String fd = args.substring(0, args.indexOf(','));
                if (fileDescriptors.containsKey(pid) && fileDescriptors.get(pid).containsKey(fd)) {
                    checkProcess(pid);
                    String path = fileDescriptors.get(pid).get(fd);
                    Artifact vertex = new Artifact();
                    vertex.addAnnotation("location", path);
                    // Look up version number in the version table if this is a normal file
                    if ((path.startsWith("/") && !path.startsWith("/dev/"))) {
                        int version = 0;
                        if (fileVersions.containsKey(path)) {
                            version = fileVersions.get(path);
                        }
                        fileVersions.put(path, version);
                        vertex.addAnnotation("version", Integer.toString(version));
                    }
                    putVertex(vertex);
                    Used used = new Used(processes.get(pid), vertex);
                    used.addAnnotation("time", time);
                    used.addAnnotation("operation", syscall);
                    putEdge(used);
                } else {
                    if (DEBUG_INFO) {
                        debugWriter.println(String.format("%d:\t%s() failed - descriptor %s not found:\t\t%s", lineNumber, syscall, fd, line));
                    }
                }
            } else if (syscall.equals("recvfrom")) {
                String fd = args.substring(0, args.indexOf(','));
                String location = null;
                int startIndex = args.lastIndexOf('{');
                if (startIndex != -1) {
                    location = args.substring(startIndex, args.lastIndexOf('}') + 1);
                } else if (fileDescriptors.containsKey(pid) && fileDescriptors.get(pid).containsKey(fd)) {
                    location = fileDescriptors.get(pid).get(fd);
                }
                if (location != null) {
                    checkProcess(pid);
                    Artifact vertex = new Artifact();
                    vertex.addAnnotation("location", location);
                    putVertex(vertex);
                    Used used = new Used(processes.get(pid), vertex);
                    used.addAnnotation("time", time);
                    used.addAnnotation("operation", syscall);
                    putEdge(used);
                }
            } else if (syscall.equals("fork") || syscall.equals("clone")) {
                checkProcess(pid);
                String newPid = retVal;
                Process childProcess = new Process();
                for (Entry<String, String> entry : processes.get(pid).getAnnotations().entrySet()) {
                    childProcess.addAnnotation(entry.getKey(), entry.getValue());
                }
                childProcess.addAnnotation("pid", newPid);
                if (syscall.equals("clone") && args.contains("CLONE_PARENT") && (processes.get(pid).getAnnotation("ppid") != null)) {
                    childProcess.addAnnotation("ppid", processes.get(pid).getAnnotation("ppid"));
                } else {
                    childProcess.addAnnotation("ppid", pid);
                }
                putVertex(childProcess);
                processes.put(newPid, childProcess);
                WasTriggeredBy wtb = new WasTriggeredBy(childProcess, processes.get(pid));
                wtb.addAnnotation("operation", syscall);
                wtb.addAnnotation("time", time);
                putEdge(wtb);

                // Copy file descriptor table to the new process
                if (fileDescriptors.containsKey(pid)) {
                    Map<String, String> newfds = new HashMap<String, String>();
                    for (Map.Entry<String, String> entry : fileDescriptors.get(pid).entrySet()) {
                        newfds.put(entry.getKey(), entry.getValue());
                    }
                    fileDescriptors.put(newPid, newfds);
                }

                // If the clone class has CLONE_FILES flag set, then the file descriptor table
                // needs to be shared
                if (syscall.equals("clone") && args.contains("CLONE_FILES")) {
                    for (int i = 0; i < sharedDescriptorTables.size(); i++) {
                        if (sharedDescriptorTables.get(i).contains(pid)) {
                            sharedDescriptorTables.get(i).add(newPid);
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
                // Update shared descriptor tables
                if (updateSharedDescriptorTables(pid, fd1, path, true) && updateSharedDescriptorTables(pid, fd2, path, true)) {
                    return;
                }
                // No shared file descriptor tables found
                if (!fileDescriptors.containsKey(pid)) {
                    Map<String, String> descriptors = new HashMap<String, String>();
                    descriptors.put(fd1, path);
                    descriptors.put(fd2, path);
                    fileDescriptors.put(pid, descriptors);
                } else {
                    fileDescriptors.get(pid).put(fd1, path);
                    fileDescriptors.get(pid).put(fd2, path);
                }
            } else if (syscall.equals("rename")) {
                checkProcess(pid);
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
                checkProcess(pid);
                Process oldProcess = processes.get(pid);
                Process newProcess = new Process();
                newProcess.addAnnotation("pid", pid);
                newProcess.addAnnotation("uid", args);
                processes.put(pid, newProcess);
                putVertex(newProcess);
                WasTriggeredBy wtb = new WasTriggeredBy(newProcess, oldProcess);
                wtb.addAnnotation("operation", syscall);
                wtb.addAnnotation("time", time);
                putEdge(wtb);
            } else if (syscall.equals("execve")) {
                String filename = args.substring(1, args.indexOf(',') - 1);
                String cmdline = args.substring(args.indexOf('['), args.indexOf(']') + 1);
                Process oldProcess = processes.get(pid);
                Process newProcess = new Process();
                for (Entry<String, String> entry : oldProcess.getAnnotations().entrySet()) {
                    newProcess.addAnnotation(entry.getKey(), entry.getValue());
                }
                newProcess.addAnnotation("filename", filename);
                newProcess.addAnnotation("cmdline", cmdline);
                processes.put(pid, newProcess);
                putVertex(newProcess);
                WasTriggeredBy wtb = new WasTriggeredBy(newProcess, oldProcess);
                wtb.addAnnotation("operation", syscall);
                wtb.addAnnotation("time", time);
                putEdge(wtb);
            } else if (syscall.equals("connect")) {
                String fd = args.substring(0, args.indexOf(','));
                String socket = args.substring(args.indexOf('{'), args.indexOf('}') + 1);
                if (updateSharedDescriptorTables(pid, fd, socket, true)) {
                    return;
                }
                // No shared processes found, just add file to own file descriptor table
                if (!fileDescriptors.containsKey(pid)) {
                    Map<String, String> descriptors = new HashMap<String, String>();
                    descriptors.put(fd, socket);
                    fileDescriptors.put(pid, descriptors);
                } else {
                    fileDescriptors.get(pid).put(fd, socket);
                }
            } else if (syscall.equals("ioctl")) {
                String fd = args.substring(0, args.indexOf(','));
                if (fileDescriptors.containsKey(pid) && fileDescriptors.get(pid).containsKey(fd)) {
                    checkProcess(pid);
                    String path = fileDescriptors.get(pid).get(fd);
                    Artifact vertex = new Artifact();
                    vertex.addAnnotation("location", path);
                    putVertex(vertex);
                    WasGeneratedBy wgb = new WasGeneratedBy(vertex, processes.get(pid));
                    wgb.addAnnotation("operation", syscall);
                    wgb.addAnnotation("time", time);
                    putEdge(wgb);
                } else {
                    if (DEBUG_INFO) {
                        debugWriter.println(String.format("%d:\t%s() failed - descriptor %s not found:\t\t%s", lineNumber, syscall, fd, line));
                    }
                }
            } else if (syscall.equals("syscall_983045")
                    || syscall.equals("syscall_983042")
                    || syscall.equals("socket")
                    || syscall.equals("unlink")) {
                // Ignore these syscalls
                // 983045 is ARM_set_tls(void*)
                // 983042 is ARM_cacheflush(long start, long end, long flags)
            } else {
                if (DEBUG_INFO) {
                    debugWriter.println(String.format("%d:\tsyscall %s() unrecognized:\t\t%s", lineNumber, syscall, line));
                }
            }
        } else if (incompleteMatcher.matches()) {
            incompleteEvents.put(incompleteMatcher.group(1), incompleteMatcher.group(2));
        } else if (completorMatcher.matches()) {
            String completeEvent = completorMatcher.group(1) + " "
                    + incompleteEvents.remove(completorMatcher.group(1)) + completorMatcher.group(2);
            parseEvent(completeEvent);
        }
    }

    @Override
    public boolean shutdown() {
        try {
            if (DEBUG_INFO) {
                debugWriter.flush();
                debugWriter.close();
            }
            shutdown = true;
            reader.close();
            return true;
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
            return false;
        }
    }
}
