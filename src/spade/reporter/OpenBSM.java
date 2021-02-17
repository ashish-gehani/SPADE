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
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractReporter;
import spade.core.Settings;
import spade.edge.opm.Used;
import spade.edge.opm.WasDerivedFrom;
import spade.edge.opm.WasGeneratedBy;
import spade.edge.opm.WasTriggeredBy;
import spade.vertex.opm.Artifact;
import spade.vertex.opm.Process;

/**
 * The OpenBSM reporter.
 *
 * @author Dawood Tariq
 */
public class OpenBSM extends AbstractReporter {

    private BufferedReader eventReader;
    private java.lang.Process nativeProcess;
    private Map<String, Process> processVertices;
    private Map<String, Integer> fileVersions;
    private String myPID;
    private String nativePID;
    private volatile boolean shutdown;
    private final String simpleDatePattern = "EEE MMM d H:mm:ss yyyy";
    private final String binaryPath = Settings.getPathRelativeToLibraryDirectory("spadeOpenBSM");
    private final int THREAD_SLEEP_DELAY = 5;
    private Map<String, String> eventData;
    private int pathCount = 0;
    private FileWriter debugFile;
    private String debugFilePath = "openbsm_debug.txt";
    private boolean debug = false;
    private Queue<String> buffer = new ConcurrentLinkedQueue<String>();
    private boolean USE_PS = false;

    @Override
    public boolean launch(String arguments) {
        if (arguments != null && arguments.equalsIgnoreCase("USE_PS")) {
            USE_PS = true;
        }

        // The argument to the launch method is unused.
        processVertices = new HashMap<String, Process>();
        fileVersions = new HashMap<String, Integer>();
        eventData = new HashMap<String, String>();

        shutdown = false;
        buildProcessTree();

        // Get the PID of SPADE (i.e., the current JavaVM) so that events generated
        // by it can be ignored.
        myPID = ManagementFactory.getRuntimeMXBean().getName().split("@")[0].trim();

        try {
            // Launch the utility to start reading from the auditpipe.
            nativeProcess = Runtime.getRuntime().exec(binaryPath);
            Field pidField = nativeProcess.getClass().getDeclaredField("pid");
            pidField.setAccessible(true);
            nativePID = Integer.toString((Integer) pidField.get(nativeProcess));
            eventReader = new BufferedReader(new InputStreamReader(nativeProcess.getInputStream()));

            Runnable bufferRunnable = new Runnable() {
                public void run() {
                    try {
                        if (debug) {
                            debugFile = new FileWriter(debugFilePath, false);
                        }

                        while (!shutdown) {
                            String line = eventReader.readLine();
                            if (line != null) {
                                buffer.add(line);
                                if (debug) {
                                    debugFile.write(line + "\n");
                                }

                            }
                        }

                        if (debug) {
                            debugFile.flush();
                            debugFile.close();
                        }

                        // Get the pid of the process and kill it.
                        nativeProcess.destroy();
                        Runtime.getRuntime().exec("kill " + nativePID);
                    } catch (Exception exception) {
                        Logger.getLogger(OpenBSM.class.getName()).log(Level.SEVERE, null, exception);
                    }
                }
            };
            new Thread(bufferRunnable, "OpenBSM-buffer-Thread").start();

            Runnable eventParserRunnable = new Runnable() {
                public void run() {
                    try {
                        while (!shutdown) {
                            String line = buffer.poll();
                            if (line != null) {
                                // Process the event.
                                parseEventToken(line);
                            } else {
                                Thread.sleep(THREAD_SLEEP_DELAY);
                            }
                        }
                        while (!buffer.isEmpty()) {
                            parseEventToken(buffer.poll());
                        }
                    } catch (Exception exception) {
                        Logger.getLogger(OpenBSM.class.getName()).log(Level.SEVERE, null, exception);
                    }
                }
            };
            new Thread(eventParserRunnable, "OpenBSM-parser-Thread").start();

        } catch (Exception exception) {
            Logger.getLogger(OpenBSM.class.getName()).log(Level.SEVERE, null, exception);
            return false;
        }
        return true;
    }

    @Override
    public boolean shutdown() {
        shutdown = true;
        return true;
    }

    private void buildProcessTree() {
        // Build the process tree using the ps utility.
        try {
            String line;
            java.lang.Process pidinfo = Runtime.getRuntime().exec("ps -Aco pid=,ppid=,comm=");
            BufferedReader pidreader = new BufferedReader(new InputStreamReader(pidinfo.getInputStream()));
            while (true) {
                line = pidreader.readLine().trim();
                if (line == null) {
                    break;
                }
                String childinfo[] = line.split("\\s+", 3);
                if (childinfo[2].equals("ps")) {
                    break;
                }
                String pid = childinfo[0];
                Process processVertex = createProcessVertex(pid);
                if (processVertex != null) {
                    putVertex(processVertex);
                    processVertices.put(pid, processVertex);
                    String ppid = processVertex.getAnnotation("ppid");
                    if ((Integer.parseInt(ppid) >= 1) && processVertices.containsKey(ppid)) {
                        WasTriggeredBy triggerEdge = new WasTriggeredBy(processVertex, (Process) processVertices.get(ppid));
                        putEdge(triggerEdge);
                    }
                }
            }
        } catch (Exception exception) {
            Logger.getLogger(OpenBSM.class.getName()).log(Level.SEVERE, null, exception);
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

    private Process createProcessVertex(String pid) {
        // Use the ps utility to create a process vertex and add it to the current
        // process tree. This is done recursively on the parent process to ensure
        // completeness of the process tree.
        try {
            Process processVertex = new Process();
            String line;
            java.lang.Process pidinfo = Runtime.getRuntime().exec("ps -p " + pid + " -co pid=,ppid=,uid=,user=,gid=,lstart=,sess=,comm=");
            BufferedReader pidreader = new BufferedReader(new InputStreamReader(pidinfo.getInputStream()));
            line = pidreader.readLine();
            if (line == null) {
                return null;
            }
            String info[] = line.trim().split("\\s+", 12);
            processVertex.addAnnotation("pidname", info[11]);
            processVertex.addAnnotation("pid", pid);
            processVertex.addAnnotation("ppid", info[1]);
            String timestring = info[5] + " " + info[6] + " " + info[7] + " " + info[8] + " " + info[9];
            Long unixtime = new java.text.SimpleDateFormat(simpleDatePattern).parse(timestring).getTime();
            String starttime_unix = Long.toString(unixtime);
            String starttime_simple = new java.text.SimpleDateFormat(simpleDatePattern).format(new java.util.Date(unixtime));
            processVertex.addAnnotation("starttime_unix", starttime_unix);
            processVertex.addAnnotation("starttime_simple", starttime_simple);
            processVertex.addAnnotation("user", info[3]);
            processVertex.addAnnotation("uid", info[2]);
            processVertex.addAnnotation("gid", info[4]);

//            try {
//                pidinfo = Runtime.getRuntime().exec("ps -p " + pid + " -o command=");
//                pidreader = new BufferedReader(new InputStreamReader(pidinfo.getInputStream()));
//                line = pidreader.readLine();
//                if (line != null) {
//                    processVertex.addAnnotation("commandline", line);
//                }
//
//                pidinfo = Runtime.getRuntime().exec("ps -p " + pid + " -Eo command=");
//                pidreader = new BufferedReader(new InputStreamReader(pidinfo.getInputStream()));
//                line = pidreader.readLine();
//                if ((line != null) && (line.length() > processVertex.getAnnotation("commandline").length())) {
//                    processVertex.addAnnotation("environment", line.substring(processVertex.getAnnotation("commandline").length()).trim());
//                }
//            } catch (Exception exception) {
//                // Ignore
//            }

            return processVertex;
        } catch (Exception exception) {
            Logger.getLogger(OpenBSM.class.getName()).log(Level.WARNING, null, exception);
            return null;
        }
    }

    private void parseEventToken(String line) {
        // This is the main loop that reads the audit data and tokenizes it to create
        // provenance semantics.
        String[] tokens = line.split(",");
        int token_type = Integer.parseInt(tokens[0]);

        // token types derive from
        // http://www.opensource.apple.com/source/xnu/xnu-1456.1.26/bsd/bsm/audit_record.h
        switch (token_type) {

            case 20:						// AUT_HEADER32
            case 21:						// AUT_HEADER32_EX
            case 116: 						// AUT_HEADER64
            case 121: 						// AUT_HEADER64_EX
                // Begin a new event
                eventData.clear();
                pathCount = 0;
                String record_length = tokens[1];
                String audit_record_version = tokens[2];
                String event_id = tokens[3];
                String event_id_modifier = tokens[4];
                String date_time = tokens[5];
                String offset_msec = tokens[6];
                eventData.put("event_id", event_id);
                eventData.put("event_time", date_time + offset_msec);
                break;

            case 36:						// AUT_SUBJECT32
            case 122:						// AUT_SUBJECT32_EX
            case 117:						// AUT_SUBJECT64
            case 124:						// AUT_SUBJECT64_EX
                String user_audit_id = tokens[1];
                String euid = tokens[2];
                String egid = tokens[3];
                String uid = tokens[4];
                String gid = tokens[5];
                String pid = tokens[6];
                String sessionid = tokens[7];
                String deviceid = tokens[8];
                String machineid = tokens[9];
                eventData.put("pid", pid);
                eventData.put("uid", uid);
                eventData.put("gid", gid);
                eventData.put("euid", euid);
                eventData.put("egid", egid);
                eventData.put("machine_id", machineid);
                break;

            case 38:						// AUT_PROCESS32
            case 123:						// AUT_PROCESS32_EX
            case 119:						// AUT_PROCESS64
            case 125:						// AUT_PROCESS64_EX
                String process_user_audit_id = tokens[1];
                String process_euid = tokens[2];
                String process_egid = tokens[3];
                String process_uid = tokens[4];
                String process_gid = tokens[5];
                String process_pid = tokens[6];
                String process_session_id = tokens[7];
                String process_device_id = tokens[8];
                String process_machine_id = tokens[9];
                break;

            case 39:						// AUT_RETURN32
            case 114:						// AUT_RETURN64
                String error = tokens[1];
                String return_value = tokens[2];
                eventData.put("return_value", return_value);
                break;

            case 49: 						// AUT_ATTR
            case 62:						// AUT_ATTR32
            case 115:						// AUT_ATTR64
                String file_access_mode = tokens[1];
                String owneruid = tokens[2];
                String ownergid = tokens[3];
                String filesystemid = tokens[4];
                String inodeid = tokens[5];
                String filedeviceid = tokens[6];
                break;

            case 45:						// AUT_ARG32
            case 113:						// AUT_ARG64

                String arg_number = tokens[1];
                String arg_value = tokens[2];
                String arg_text = tokens[3];
                break;

            case 35: 						// AUT_PATH
                String path = tokens[1];
                eventData.put("path" + pathCount, path);
                pathCount++;
                break;

            case 40: 						// AUT_TEXT
                String text_string = tokens[1];
                break;

            case 128:						// AUT_SOCKINET32
            case 129:						// AUT_SOCKINET128
                String socket_family = tokens[1];
                String socket_local_port = tokens[2];
                String socket_address = tokens[3];
                break;

            case 82: 						// AUT_EXIT
                String exit_status = tokens[1];
                String exit_value = tokens[2];
                break;

            case 60: 						// AUT_EXEC_ARGS
            case 61: 						// AUT_EXEC_ENV
            case 81: 						// AUT_CMD
            case 42: 						// AUT_IN_ADDR
            case 43: 						// AUT_IP
            case 44: 						// AUT_IPORT
            case 46: 						// AUT_SOCKET
            case 127:						// AUT_SOCKET_EX
            case 112: 						// AUT_HOST
            case 130:						// AUT_SOCKUNIX
                break;

            case 19:						// AUT_TRAILER
                processEvent();
                break;

            default:
                break;

        }
    }

    private void processEvent() {
        String pid = eventData.get("pid");
        if (pid.equals(myPID) || pid.equals(nativePID)) {
            return;
        }

        // Audit event IDs derive from
        // http://www.opensource.apple.com/source/xnu/xnu-1456.1.26/bsd/bsm/audit_kevents.h
        // or from /etc/security/audit_event
        int event_id = Integer.parseInt(eventData.get("event_id"));
        String time = eventData.get("event_time");
        Process thisProcess = processVertices.get(pid);
        boolean put;

        switch (event_id) {

            case 1:         // exit
                checkCurrentProcess();
                processVertices.remove(pid);
                break;

            case 2:         // fork
            case 25:        // vfork
            case 241:       // fork1
                checkCurrentProcess();
                String childPID = eventData.get("return_value");
                Process childProcess = USE_PS ? createProcessVertex(childPID) : null;
                if (childProcess == null) {
                    childProcess = new Process();
                    childProcess.addAnnotation("pid", childPID);
                    childProcess.addAnnotation("ppid", pid);
                    childProcess.addAnnotation("uid", eventData.get("uid"));
                    childProcess.addAnnotation("gid", eventData.get("gid"));
                }
                putVertex(childProcess);
                processVertices.put(childPID, childProcess);
                WasTriggeredBy triggeredEdge = new WasTriggeredBy(childProcess, thisProcess);
                triggeredEdge.addAnnotation("operation", "fork");
                triggeredEdge.addAnnotation("time", time);
                putEdge(triggeredEdge);
                break;

            case 72:        // open - read
                checkCurrentProcess();
                String readPath = eventData.containsKey("path1") ? eventData.get("path1") : eventData.get("path0");
                put = !fileVersions.containsKey(readPath.replace("//", "/"));
                Artifact readFileArtifact = createFileVertex(readPath, false);
                if (put) {
                    putVertex(readFileArtifact);
                }
                Used readEdge = new Used(thisProcess, readFileArtifact);
                readEdge.addAnnotation("time", time);
                putEdge(readEdge);
                break;

            case 73:        // open - read,creat
            case 74:        // open - read,trunc
            case 75:        // open - read,creat,trunc
            case 76:        // open - write
            case 77:        // open - write,creat
            case 78:        // open - write,trunc
            case 79:        // open - write,creat,trunc
            case 80:        // open - read,write
            case 81:        // open - read,write,creat
            case 82:        // open - read,write,trunc
            case 83:        // open - read,write,creat,trunc
                checkCurrentProcess();
                String writePath = eventData.containsKey("path1") ? eventData.get("path1") : eventData.get("path0");
                Artifact writeFileArtifact = createFileVertex(writePath, true);
                putVertex(writeFileArtifact);
                WasGeneratedBy generatedEdge = new WasGeneratedBy(writeFileArtifact, thisProcess);
                generatedEdge.addAnnotation("time", time);
                putEdge(generatedEdge);
                break;

            case 42:        // rename
                checkCurrentProcess();
                String fromPath = eventData.get("path1");
                String toPath = eventData.get("path2");
                if (!toPath.startsWith("/")) {
                    toPath = fromPath.substring(0, fromPath.lastIndexOf("/")) + toPath;
                }
                put = !fileVersions.containsKey(fromPath.replace("//", "/"));
                Artifact fromFileArtifact = createFileVertex(fromPath, false);
                if (put) {
                    putVertex(fromFileArtifact);
                }
                Used renameReadEdge = new Used(thisProcess, fromFileArtifact);
                renameReadEdge.addAnnotation("time", time);
                putEdge(renameReadEdge);
                Artifact toFileArtifact = createFileVertex(toPath, true);
                putVertex(toFileArtifact);
                WasGeneratedBy renameWriteEdge = new WasGeneratedBy(toFileArtifact, thisProcess);
                renameWriteEdge.addAnnotation("time", time);
                putEdge(renameWriteEdge);
                WasDerivedFrom renameEdge = new WasDerivedFrom(toFileArtifact, fromFileArtifact);
                renameEdge.addAnnotation("operation", "rename");
                renameEdge.addAnnotation("time", time);
                break;

            default:
                break;
        }
    }

    private void checkCurrentProcess() {
        String pid = eventData.get("pid");
        // Make sure the process that triggered this event has already been added.
        if (!processVertices.containsKey(pid)) {
            Process process = USE_PS ? createProcessVertex(pid) : null;
            if (process == null) {
                process = new Process();
                process.addAnnotation("pid", pid);
                process.addAnnotation("uid", eventData.get("uid"));
                process.addAnnotation("gid", eventData.get("gid"));
            }
            putVertex(process);
            processVertices.put(pid, process);
            String ppid = process.getAnnotation("ppid");
            if ((ppid != null) && processVertices.containsKey(ppid)) {
                WasTriggeredBy triggeredEdge = new WasTriggeredBy(process, processVertices.get(ppid));
                putEdge(triggeredEdge);
            }
        }
    }
}
