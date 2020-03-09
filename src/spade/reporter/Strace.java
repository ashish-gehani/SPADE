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

import spade.core.AbstractReporter;
import spade.edge.opm.Used;
import spade.edge.opm.WasDerivedFrom;
import spade.edge.opm.WasGeneratedBy;
import spade.edge.opm.WasTriggeredBy;
import spade.reporter.pdu.Pdu;
import spade.reporter.pdu.PduParser;
import spade.vertex.opm.Artifact;
import spade.vertex.opm.Process;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
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
 * @author Dawood Tariq, Sharjeel Qureshi and Hasanat Kazmi
 */
public class Strace extends AbstractReporter {

    PrintWriter logWriter;
    final boolean LOG_DEBUG_INFO = true;
    final boolean TRACE_SYSTEM = false;
    final boolean TRACE_APPS = false;
    final boolean ADD_BEHAVIOR_TAGS = false;
    final int THREAD_SLEEP_DELAY = 5;
    volatile boolean shutdown = false;
    static final Logger logger = Logger.getLogger(Strace.class.getName());
    static final Pattern eventPattern = Pattern.compile("([0-9]+)\\s+([\\d]+:[\\d]+:[\\d]+\\.[\\d]+)\\s+(\\w+)\\((.*)\\)\\s+=\\s+(\\-?[0-9]+).*");
    static final Pattern eventIncompletePattern = Pattern.compile("([0-9]+)\\s+(.*) <unfinished \\.\\.\\.>");
    static final Pattern eventCompletorPattern = Pattern.compile("([0-9]+)\\s+.*<... \\w+ resumed> (.*)");
    static final Pattern networkPattern = Pattern.compile("sin_port=htons\\(([0-9]+)\\), sin_addr=inet_addr\\(\"(.*)\"\\)");
    static final Pattern binderTransactionPattern = Pattern.compile("([0-9]+): ([a-z]+)\\s*from ([0-9]+):[0-9]+ to ([0-9]+):[0-9]+");
    String DEBUG_FILE_PATH;
    String TEMP_FILE_PATH;
    Map<String, String> incompleteEvents = new HashMap<String, String>();
    Map<String, String> socketDescriptors = new HashMap<String, String>();
    Map<String, Map<String, String>> fileDescriptors = new HashMap<String, Map<String, String>>();
    Map<String, Integer> fileVersions = new HashMap<String, Integer>();
    Map<String, Process> processes = new HashMap<String, Process>();
    List<Set<String>> sharedDescriptorTables = new ArrayList<Set<String>>();
    final String simpleDatePattern = "EEE MMM d H:mm:ss yyyy";
    ArrayList<String> mainPIDs = new ArrayList<String>();
    String templine = null;

    private void log(String message) {
        logger.log(Level.INFO, message);
        if (LOG_DEBUG_INFO) {
            logWriter.println(message);
        }
    }

    @Override
    public boolean launch(String arguments) {
		// Parse the arguments
        // Arguments e.g. "user=radio user=u0_a1 name=zygote"
        // all of the conditionals are translate into "OR" clauses
        if (arguments == null || arguments.equals("")) {
            arguments = "name=zygote";
        }

        Map<String, Set<String>> argumentsMap = new HashMap<String, Set<String>>();

        argumentsMap.put("name", new HashSet<String>());
        argumentsMap.put("user", new HashSet<String>());
        argumentsMap.put("pid", new HashSet<String>());
        argumentsMap.put("!name", new HashSet<String>());
        argumentsMap.put("!user", new HashSet<String>());
        argumentsMap.put("!pid", new HashSet<String>());

        String[] pairs = arguments.split("\\s+");
        for (String pair : pairs) {
            String[] keyvalue = pair.split("=");
            String key = keyvalue[0];
            String value = keyvalue[1];

            if (key.equals("name") || key.equals("user") || key.equals("pid")
                    || key.equals("!name") || key.equals("!user") || key.equals("!pid")) {
                argumentsMap.get(key).add(value);
            }
        }

    	if (Files.exists(Paths.get("/sdcard"))) { // Android
    		DEBUG_FILE_PATH = "/sdcard/spade/spade-strace-debug.txt";
    		TEMP_FILE_PATH = "/sdcard/spade/spade-strace-output.txt";
    	} else if (Files.exists(Paths.get("/tmp"))) { // Linux
            DEBUG_FILE_PATH = "/tmp/spade-strace-debug.txt";
            TEMP_FILE_PATH = "/tmp/spade-strace-output.txt";
    	}

        // Attach strace
        try {
            java.lang.Process pidChecker = Runtime.getRuntime().exec("ps -e -o uname,pid,cmd");
            BufferedReader pidReader = new BufferedReader(new InputStreamReader(pidChecker.getInputStream()));
            pidReader.readLine();
            String line;
            while ((line = pidReader.readLine()) != null) {
                String details[] = line.split("\\s+");
                String user = details[0];
                String pid = details[1];
                String name = details[2];
                if (argumentsMap.get("name").contains(name) || argumentsMap.get("user").contains(user) || argumentsMap.get("pid").contains(pid)) {
                    if (!argumentsMap.get("!name").contains(name) && !argumentsMap.get("!user").contains(name) && !argumentsMap.get("!pid").contains(name)) {
                        mainPIDs.add(pid);
                        System.out.println("Attaching to " + name + " with pid " + pid);
                    }
                } else if (TRACE_SYSTEM && name.startsWith("/system/bin/")) {
                    System.out.println("Attaching to " + name + " with pid " + pid);
                    mainPIDs.add(pid);
                } else if (TRACE_APPS && name.startsWith("com.android.")) {
                    System.out.println("Attaching to " + name + " with pid " + pid);
                    mainPIDs.add(pid);
                }
            }
            pidReader.close();
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
            return false;
        }

        // Populate socket descriptors
        try {
            java.lang.Process socketChecker = Runtime.getRuntime().exec("cat /proc/net/unix");
            BufferedReader socketReader = new BufferedReader(new InputStreamReader(socketChecker.getInputStream()));
            socketReader.readLine();
            String line;
            int lastInodeWithPath = -1;
            while ((line = socketReader.readLine()) != null) {
                String details[] = line.split("\\s+");
                if (details.length == 8) {
                    String inode = details[6];
                    String path = details[7];
                    socketDescriptors.put(inode, path);
                    lastInodeWithPath = Integer.parseInt(inode);
                } else if (details.length == 7) {
					// Heuristic to finding socket path against inode
                    // Background here:
                    // http://unix.stackexchange.com/questions/16300/whos-got-the-other-end-of-this-unix-socketpair
                    // Basically the adjacent inode number would probably have
                    // same socket path as this one
                    String inode = details[6];
                    if (Math.abs(Integer.parseInt(inode) - lastInodeWithPath) < 4 && lastInodeWithPath >= 0) {
                        socketDescriptors.put(inode, socketDescriptors.get(Integer.toString(lastInodeWithPath)));
                    }
                }
            }
            socketReader.close();
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
            return false;
        }

        try {
            if (LOG_DEBUG_INFO) {
                logWriter = new PrintWriter(new FileWriter(DEBUG_FILE_PATH, false));
            }

            for (String pid : mainPIDs) {
                checkProcessTree(pid);
            }

            Runnable traceProcessor = new Runnable() {
                public void run() {
                    try {
                        String straceCmdLine = "strace -e fork,read,write,open,close,link,execve,mknod,rename,dup,dup2,symlink,";
                        straceCmdLine += "clone,vfork,setuid32,setgid32,chmod,fchmod,pipe,truncate,ftruncate,";
                        straceCmdLine += "readv,recv,recvfrom,recvmsg,send,sendto,sendmsg,connect,accept";
                        straceCmdLine += " -f -F -tt -v -s 200";
                        for (String pid : mainPIDs) {
                            straceCmdLine += " -p " + pid;
                        }
                        straceCmdLine += " -o " + TEMP_FILE_PATH;
                        logger.log(Level.INFO, straceCmdLine);

                        java.lang.Process straceProcess = Runtime.getRuntime().exec(straceCmdLine);
                        Thread.sleep(2000);
                        BufferedReader traceReader = new BufferedReader(new FileReader(TEMP_FILE_PATH));

                        while (!shutdown) {
                            String line = traceReader.readLine();
                            if (line != null) {
                                parseEvent(line);
                            } else {
                                Thread.sleep(THREAD_SLEEP_DELAY);
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

            Runnable binderProcessor = new Runnable() {
                public void run() {
                    try {
                        try {
                            PrintWriter binderControl = new PrintWriter(new FileWriter("/sdcard/spade/binder_dctl.sh", false));
                            binderControl.println("echo $1 > /sys/module/binder/parameters/debug_mask");
                            binderControl.close();
                            // Purge klog output
                            Runtime.getRuntime().exec("cat /proc/kmsg").destroy();
                            Thread.sleep(500);
                            // Set BINDER's TRANSACTION_ DEBUG log on
                            Runtime.getRuntime().exec("sh /sdcard/spade/binder_dctl.sh 0x200");
                            Thread.sleep(500);
                        } catch (Exception e) {
                            logger.log(Level.SEVERE, "error setting up binder transaction processor", e);
                            return;
                        }

                        java.lang.Process kmsgReaderProcess = Runtime.getRuntime().exec("cat /proc/kmsg");
                        BufferedReader kmsgReader = new BufferedReader(new InputStreamReader(kmsgReaderProcess.getInputStream()));
                        Set<String> transactionAlreadyProcessed = new HashSet<String>();
                        String line;

                        while (!shutdown) {
                            while ((line = kmsgReader.readLine()) != null) {
                                if (line.contains("BC_REPLY")) {
                                    try {
										// Example line: 
                                        // <6>binder: 276:515 BC_REPLY 116519 -> 422:422, data 2a290aa8-(null) size 8-0

										// Example line on 4.2 device:
                                        // <6>[ 9094.578430] binder: 130:400 BC_REPLY 537128 -> 578:793, data 4118dcc8-  (null) size 12-0
                                        
                                        line = line.substring(line.indexOf("binder:") + 8);
                                        String details[] = line.split("\\s+");

                                        // int baseIndex = Arrays.asList(details).indexOf("binder:");
                                        // String type = details[baseIndex + 2];
                                        String frompid = details[0].split(":")[1];
                                        String topid = details[4].split(":")[1].replace(",", "");
                                        String pidpair = frompid + "-" + topid;

                                        if (!transactionAlreadyProcessed.contains(pidpair)) {
                                            checkProcessTree(topid);
                                            checkProcessTree(frompid);

											// Create vertex artifact
                                            // binder-<frompid>-<topid>
                                            Artifact vertex = new Artifact();
                                            vertex.addAnnotation("location", "binder-" + pidpair);
                                            putVertex(vertex);

                                            WasGeneratedBy wgb = new WasGeneratedBy(vertex, processes.get(frompid));
                                            wgb.addAnnotation("operation", "BC_REPLY");
                                            putEdge(wgb);

                                            Used used = new Used(processes.get(topid), vertex);
                                            used.addAnnotation("operation", "BC_REPLY");
                                            putEdge(used);

                                            transactionAlreadyProcessed.add(pidpair);

                                            if (processes.get(frompid).getAnnotation("commandline").equals("android.process.acore")) {
                                                createBehavior(vertex, "GetContact");
                                            }
                                        }
                                    } catch (Exception e) {
                                        logger.log(Level.SEVERE, "error parsing binder transaction log: " + line, e);
                                    }

                                }
                            }
                        }
                        // Set BINDER's TRANSACTION_ DEBUG log off
                        Runtime.getRuntime().exec("sh /sdcard/spade/binder_dctl.sh 0");
                    } catch (Exception exception) {
                        logger.log(Level.SEVERE, null, exception);
                    }
                }
            };
	    if (Files.exists(Paths.get("/sdcard"))) { // Android
            	new Thread(binderProcessor, "androidBinder-Thread").start();
	    }
            return true;
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
            return false;
        }
    }

    private void createBehavior(Artifact artifact, String behavior) {
        if (ADD_BEHAVIOR_TAGS) {
            Artifact behaviorArtifact = new Artifact();
            behaviorArtifact.addAnnotation("behavior", behavior);
            putVertex(behaviorArtifact);
            WasDerivedFrom w1 = new WasDerivedFrom(artifact, behaviorArtifact);
            putEdge(w1);
            WasDerivedFrom w2 = new WasDerivedFrom(behaviorArtifact, artifact);
            putEdge(w2);
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
					// Search for processes with shared file descriptor
                    // tables
                    // and
                    // update them as well
                    if (updateSharedDescriptorTables(pid, fd, path, true)) {
                        templine = null;
                        return;
                    }
					// No shared processes found, just add file to own file
                    // descriptor table
                    if (!fileDescriptors.containsKey(pid)) {
                        Map<String, String> descriptors = new HashMap<String, String>();
                        descriptors.put(fd, path);
                        fileDescriptors.put(pid, descriptors);
                    } else {
                        fileDescriptors.get(pid).put(fd, path);
                    }
                } else if (syscall.equals("close")) {
                    if (!success) {
                        templine = null;
                        return;
                    }
                    String fd = args;
					// Search for processes with shared file descriptor
                    // tables
                    // and
                    // update them as well
                    if (updateSharedDescriptorTables(pid, fd, null, false)) {
                        templine = null;
                        return;
                    }
                    if (fileDescriptors.containsKey(pid) && fileDescriptors.get(pid).containsKey(fd)) {
                        fileDescriptors.get(pid).remove(fd);
                    } else {
                        log(String.format("%s() failed - descriptor %s not found:\t\t%s", syscall, fd, line));
                    }
                } else if (syscall.equals("dup") || syscall.equals("dup2")) {
                    if (!success) {
                        templine = null;
                        return;
                    }
                    String oldfd = (syscall.equals("dup")) ? args : args.substring(0, args.indexOf(','));
                    String newfd = retVal;
                    if (fileDescriptors.containsKey(pid) && fileDescriptors.get(pid).containsKey(oldfd)) {
                        String path = fileDescriptors.get(pid).get(oldfd);
                        if (updateSharedDescriptorTables(pid, newfd, path, true)) {
                            templine = null;
                            return;
                        }
                        fileDescriptors.get(pid).put(newfd, path);
                    } else {
                        log(String.format("%s() failed - descriptor %s not found:\t\t%s", syscall, oldfd, line));
                    }
                } else if (syscall.equals("write") || syscall.equals("pwrite") || syscall.equals("writev") || syscall.equals("send") || syscall.equals("sendto") || syscall.equals("sendmsg")) {
                    String fd = args.substring(0, args.indexOf(','));
                    if (!fileDescriptors.get(pid).containsKey(fd)) {
                        fixDescriptor(pid, fd);
                    }
                    String path = fileDescriptors.get(pid).get(fd);
                    if (path != null) {
                        Artifact vertex = new Artifact();
                        vertex.addAnnotation("location", path);
                        Matcher networkMatcher = networkPattern.matcher(path);
                        if (networkMatcher.find()) {
                            vertex.addAnnotation("subtype", "network");
                            vertex.addAnnotation("address", networkMatcher.group(2));
                            vertex.addAnnotation("port", networkMatcher.group(1));
                        } else if ((path.startsWith("/") && !path.startsWith("/dev/"))) {
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
                        wgb.addAnnotation("operation", syscall);
                        wgb.addAnnotation("time", time);
                        wgb.addAnnotation("success", success ? "true" : "false");
                        putEdge(wgb);
                        // Extra checks for additional data
                        int firstIndex = args.indexOf("\"") + 1;
                        int secondIndex = args.lastIndexOf("\"");
                        if (firstIndex > 0 && secondIndex > -1) {
                            String data = args.substring(firstIndex, secondIndex);
                            if (data.startsWith("AT")) {
                                Artifact at = new Artifact();
                                at.addAnnotation("command", data);
                                at.addAnnotation("time", time);
                                putVertex(at);
                                WasGeneratedBy atwgb = new WasGeneratedBy(at, processes.get(pid));
                                atwgb.addAnnotation("operation", syscall);
                                atwgb.addAnnotation("action", "atcommand");
                                atwgb.addAnnotation("time", time);
                                putEdge(atwgb);
                                if (data.startsWith("ATD")) {
                                    createBehavior(at, "PhoneCall");
                                } else if (data.startsWith("AT+CNMI")) {
                                    createBehavior(at, "ReceiveSMS");
                                } else {
                                    createBehavior(at, "ATCommand");
                                }
                            }
                            if (processes.get(pid).getAnnotation("name") != null && processes.get(pid).getAnnotation("name").equals("rild") && path.equals("/dev/qemu_pipe")
                                    && data.matches("[0-9A-Fa-f]+")) {
                                Artifact pdu = new Artifact();
                                pdu.addAnnotation("pdudata", data);
                                PduParser parser = new PduParser();
                                Pdu pduMessage = parser.parsePdu(data);
                                pdu.addAnnotation("address", pduMessage.getAddress());
                                pdu.addAnnotation("text", pduMessage.getDecodedText().replaceAll("\n", ""));
                                pdu.addAnnotation("time", time);
                                putVertex(pdu);
                                WasGeneratedBy pduwgb = new WasGeneratedBy(pdu, processes.get(pid));
                                pduwgb.addAnnotation("operation", syscall);
                                pduwgb.addAnnotation("action", "pdu");
                                pduwgb.addAnnotation("time", time);
                                putEdge(pduwgb);
                                createBehavior(pdu, "SendSMS");
                            }
                            if (data.startsWith("$GP")) {
                                Artifact gps = new Artifact();
                                gps.addAnnotation("gpsdata", data);
                                gps.addAnnotation("time", time);
                                putVertex(gps);
                                WasGeneratedBy gpswgb = new WasGeneratedBy(gps, processes.get(pid));
                                gpswgb.addAnnotation("operation", syscall);
                                gpswgb.addAnnotation("action", "gps");
                                gpswgb.addAnnotation("time", time);
                                putEdge(gpswgb);
                                createBehavior(gps, "GeoLocation");
                            }
                        }
                        if (path.equals("/data/data/com.android.providers.telephony/databases/mmssms.db")) {
                            createBehavior(vertex, "WriteSMSDB");
                        }
                        if (networkMatcher.find()) {
                            createBehavior(vertex, "Internet");
                        }
                    } else {
                        log(String.format("%s() failed - descriptor %s not found:\t\t%s", syscall, fd, line));
                    }
                } else if (syscall.equals("read") || syscall.equals("pread") || syscall.equals("readv") || syscall.equals("recv") || syscall.equals("recvfrom") || syscall.equals("recvmsg")) {
                    String fd = args.substring(0, args.indexOf(','));
                    if (!fileDescriptors.get(pid).containsKey(fd)) {
                        fixDescriptor(pid, fd);
                    }
                    String path = fileDescriptors.get(pid).get(fd);
                    if (path != null) {
                        Artifact vertex = new Artifact();
                        vertex.addAnnotation("location", path);
                        Matcher networkMatcher = networkPattern.matcher(path);
                        if (networkMatcher.find()) {
                            vertex.addAnnotation("subtype", "network");
                            vertex.addAnnotation("address", networkMatcher.group(2));
                            vertex.addAnnotation("port", networkMatcher.group(1));
                        } else if ((path.startsWith("/") && !path.startsWith("/dev/"))) {
                            int version = 0;
                            if (fileVersions.containsKey(path)) {
                                version = fileVersions.get(path);
                            }
                            fileVersions.put(path, version);
                            vertex.addAnnotation("version", Integer.toString(version));
                        }
                        putVertex(vertex);
                        Used used = new Used(processes.get(pid), vertex);
                        used.addAnnotation("operation", syscall);
                        used.addAnnotation("time", time);
                        used.addAnnotation("success", success ? "true" : "false");
                        putEdge(used);
                        // Extra checks for additional data
                        int firstIndex = args.indexOf("\"") + 1;
                        int secondIndex = args.lastIndexOf("\"");
                        if (firstIndex > 0 && secondIndex > -1) {
                            String data = args.substring(firstIndex, secondIndex);
                            if (data.startsWith("$GP")) {
                                Artifact gps = new Artifact();
                                gps.addAnnotation("gpsdata", data);
                                gps.addAnnotation("time", time);
                                putVertex(gps);
                                Used gpsUsed = new Used(processes.get(pid), gps);
                                gpsUsed.addAnnotation("operation", syscall);
                                gpsUsed.addAnnotation("action", "gps");
                                gpsUsed.addAnnotation("time", time);
                                putEdge(gpsUsed);
                                createBehavior(gps, "GeoLocation");
                            }
                        }
                        if (path.equals("/data/data/com.android.providers.telephony/databases/mmssms.db")) {
                            createBehavior(vertex, "ReadSMSDB");
                        }
                        if (networkMatcher.find()) {
                            createBehavior(vertex, "Internet");
                        }
                    } else {
                        log(String.format("%s() failed - descriptor %s not found:\t\t%s", syscall, fd, line));
                    }
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

                    // Copy file descriptor table to the new process
                    if (fileDescriptors.containsKey(pid)) {
                        Map<String, String> newfds = new HashMap<String, String>();
                        for (Map.Entry<String, String> entry : fileDescriptors.get(pid).entrySet()) {
                            newfds.put(entry.getKey(), entry.getValue());
                        }
                        fileDescriptors.put(newPid, newfds);
                    }

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
                    // Update shared descriptor tables
                    if (updateSharedDescriptorTables(pid, fd1, path, true) && updateSharedDescriptorTables(pid, fd2, path, true)) {
                        templine = null;
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
                    if (updateSharedDescriptorTables(pid, fd, socket, true)) {
                        templine = null;
                        return;
                    }
					// No shared processes found, just add file to own file
                    // descriptor table
                    if (!fileDescriptors.containsKey(pid)) {
                        Map<String, String> descriptors = new HashMap<String, String>();
                        descriptors.put(fd, socket);
                        fileDescriptors.put(pid, descriptors);
                    } else {
                        fileDescriptors.get(pid).put(fd, socket);
                    }
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
					// String fd = args.substring(0, args.indexOf(','));
                    // if (!fileDescriptors.get(pid).containsKey(fd)) {
                    // fixDescriptor(pid, fd);
                    // }
                    // if (fileDescriptors.containsKey(pid) &&
                    // fileDescriptors.get(pid).containsKey(fd)) {
                    // String path = fileDescriptors.get(pid).get(fd);
                    // Artifact vertex = new Artifact();
                    // vertex.addAnnotation("location", path);
                    // putVertex(vertex);
                    // WasGeneratedBy wgb = new WasGeneratedBy(vertex,
                    // processes.get(pid));
                    // wgb.addAnnotation("operation", syscall);
                    // wgb.addAnnotation("time", time);
                    // putEdge(wgb);
                    // } else {
                    // log(String.format("%s() failed - descriptor %s not found:\t\t%s",
                    // syscall, fd, line));
                    // }
                } else if (syscall.equals("syscall_983045") || syscall.equals("syscall_983042")) {
					// Ignore these syscalls
                    // 983045 is ARM_set_tls(void*)
                    // 983042 is ARM_cacheflush(long start, long end, long
                    // flags)
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
            if (Integer.parseInt(ppid) >= 1) {
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
			// System.out.println("First command line: " + cmdline);
            // Thread.sleep(2000);
            // File file = new File("/proc/" + pid + "/cmdline");
            // if (!file.exists())
            // return cmdline;
            // cmdlineReader = new BufferedReader(new FileReader("/proc/" + pid
            // + "/cmdline"));
            // String newCmdline = cmdlineReader.readLine();
            // cmdlineReader.close();
            // newCmdline = (newCmdline == null) ? null :
            // newCmdline.replace("\0", " ").replace("\"", "'").trim();
            // System.out.println("Second command line: " + newCmdline);
            // return (newCmdline == null) ? cmdline : newCmdline;
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
			// System.out.println("First process name: " + nameline);
            // Thread.sleep(2000);
            // File file = new File("/proc/" + pid + "/status");
            // if (!file.exists())
            // return nameline;
            // namelineReader = new BufferedReader(new FileReader("/proc/" + pid
            // + "/status"));
            // String newNameline = namelineReader.readLine();
            // namelineReader.close();
            // newNameline = (newNameline == null) ? null :
            // newNameline.split("\\s+", 2)[1];
            // System.out.println("Second process name: " + newNameline);
            // return (newNameline == null) ? nameline : newNameline;
        } catch (Exception exception) {
            return null;
        }
    }

    private Process createProcess(String pid) {
        // The process vertex is created using the proc filesystem.
        try {
            Process newProcess = new Process();
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

            BufferedReader cmdlineReader = new BufferedReader(new FileReader("/proc/" + pid + "/cmdline"));
            String cmdline = cmdlineReader.readLine();
            cmdlineReader.close();

            String name = nameline.split("\\s+", 2)[1];
            String ppid = ppidline.split("\\s+")[1];
            String uid = uidline.split("\\s+")[2];
            String gid = gidline.split("\\s+")[2];
            String tgid = tgidline.split("\\s+")[1];

            newProcess.addAnnotation("name", name);
            newProcess.addAnnotation("pid", pid);
            newProcess.addAnnotation("ppid", ppid);
            newProcess.addAnnotation("tgid", tgid);
            newProcess.addAnnotation("uid", uid);
            newProcess.addAnnotation("gid", gid);
            if (cmdline != null) {
                cmdline = cmdline.replace("\0", " ").replace("\"", "'").trim();
                newProcess.addAnnotation("commandline", cmdline);
            }

            java.lang.Process pidinfo = Runtime.getRuntime().exec("ls -l /proc/" + pid + "/fd");
            BufferedReader fdReader = new BufferedReader(new InputStreamReader(pidinfo.getInputStream()));
            fdReader.readLine();
            Map<String, String> descriptors = new HashMap<String, String>();
            while (true) {
                String line = fdReader.readLine();
                if (line == null) {
                    break;
                }
                String tokens[] = line.split("\\s+", 8);
                String fd = tokens[5];
                String location = tokens[7];
                if (location.startsWith("socket:")) {
                    String node = location.substring(location.indexOf("[") + 1, location.lastIndexOf("]"));
                    if (socketDescriptors.containsKey(node)) {
                        location = socketDescriptors.get(node);
                    }
                }
                descriptors.put(fd, location);
            }
            fileDescriptors.put(pid, descriptors);
            fdReader.close();

            return newProcess;
        } catch (Exception exception) {
            log("unable to create process vertex for pid " + pid + " from /proc/");
            return null;
        }
    }

    private boolean updateSharedDescriptorTables(String pid, String fd, String path, boolean add) {
		// Search for processes with shared file descriptor tables and update
        // them as well
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

    private boolean fixDescriptor(String pid, String fd) {
        try {
            File file = new File("/proc/" + pid + "/fd/" + fd);
            String resolved = file.getCanonicalPath();
            if ((resolved.indexOf(":") != -1) && (resolved.lastIndexOf("/") != -1)) {
                resolved = resolved.substring(resolved.lastIndexOf("/") + 1);
            }
            if (resolved.startsWith("socket:")) {
                String node = resolved.substring(resolved.indexOf("[") + 1, resolved.lastIndexOf("]"));
                if (socketDescriptors.containsKey(node)) {
                    resolved = socketDescriptors.get(node);
                }
            }
            fileDescriptors.get(pid).put(fd, resolved);
            return true;
        } catch (Exception exception) {
            log(String.format("unable to get file descriptor %s for pid %s from /proc/%s/fd/%s", fd, pid, pid, fd));
            return false;
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
