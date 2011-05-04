/*
--------------------------------------------------------------------------------
SPADE - Support for Provenance Auditing in Distributed Environments.
Copyright (C) 2011 SRI International

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
import spade.core.AbstractEdge;
import spade.opm.edge.WasTriggeredBy;
import spade.opm.edge.WasGeneratedBy;
import spade.opm.edge.Used;
import spade.opm.edge.WasDerivedFrom;
import spade.opm.vertex.Artifact;
import spade.core.AbstractVertex;
import spade.opm.vertex.Process;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

public class OpenBSM extends AbstractReporter {

    private BufferedReader eventReader;
    private java.lang.Process pipeProcess;
    private Map<String, AbstractVertex> processVertices;
    private Set<Integer> sentObjects;
    private Set<String> processTree;
    private AbstractVertex tempVertex1, tempVertex2;
    private int current_event_id;
    private String currentEventTime;
    private String currentFilePath;
    private String javaPID;
    private String eventPID;
    private volatile boolean shutdown;

    @Override
    public boolean launch(String arguments) {
        // The argument to the launch method is unused.
        processVertices = new HashMap<String, AbstractVertex>();
        sentObjects = new HashSet<Integer>();
        processTree = new HashSet<String>();
        shutdown = false;
        buildProcessTree();

        // Get the PID of SPADE (i.e., the current JavaVM) so that events generated
        // by it can be ignored.
        javaPID = ManagementFactory.getRuntimeMXBean().getName().split("@")[0].trim();

        try {
            // Launch the utility to start reading from the auditpipe.
            String[] cmd = {"/bin/sh", "-c", "sudo ./spade/reporter/spade_reporter_OpenBSM"};
            pipeProcess = Runtime.getRuntime().exec(cmd);
            eventReader = new BufferedReader(new InputStreamReader(pipeProcess.getInputStream()));
            eventReader.readLine();
            Runnable eventThread = new Runnable() {

                public void run() {
                    try {
                        while (!shutdown) {
                            if (eventReader.ready()) {
                                String line = eventReader.readLine();
                                if (line != null) {
                                    // Call the parseEvent method to parse the audit data.
                                    parseEvent(line);
                                }
                            }
                            Thread.sleep(5);
                        }
                        eventReader.close();
                        pipeProcess.destroy();
                    } catch (Exception exception) {
                        exception.printStackTrace(System.err);
                    }
                }
            };
            new Thread(eventThread).start();
        } catch (Exception exception) {
            exception.printStackTrace(System.err);
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
        Process parentVertex, childVertex;
        try {
            String line = "";
            java.lang.Process pidinfo = Runtime.getRuntime().exec("ps -Aco pid,ppid,comm");
            BufferedReader pidreader = new BufferedReader(new InputStreamReader(pidinfo.getInputStream()));
            line = pidreader.readLine();
            line = pidreader.readLine().trim();
            String info[] = line.split("\\s+", 3);
            getProcessVertex(info[0]);
            while (true) {
                line = pidreader.readLine().trim();
                String childinfo[] = line.split("\\s+", 3);
                if (childinfo[2].equals("ps")) {
                    break;
                }
                childVertex = getProcessVertex(childinfo[0]);
                if (childVertex != null) {
                    parentVertex = (Process) processVertices.get(childinfo[1]);
                    AbstractEdge edge = new WasTriggeredBy(childVertex, parentVertex);
                    pushToBuffer(parentVertex);
                    pushToBuffer(childVertex);
                    pushToBuffer(edge);
                    processTree.add(childinfo[0]);
                    processTree.add(childinfo[1]);
                }
            }
        } catch (Exception exception) {
            exception.printStackTrace(System.err);
        }
    }

    private Artifact createFileVertex(String path) {
        // Create a file artifact and use the stat utility to populate annotations.
        // If stat fails then return a file artifact with limited annotations.
        try {
            Artifact fileArtifact = new Artifact();
            String[] filename = path.split("/");
            if (filename.length > 0) {
                fileArtifact.addAnnotation("filename", filename[filename.length - 1]);
            }
            fileArtifact.addAnnotation("path", path);
            java.lang.Process fileinfo = Runtime.getRuntime().exec("stat -f %Op%n%u%n%g%n%m%n%z " + path);
            BufferedReader inforeader = new BufferedReader(new InputStreamReader(fileinfo.getInputStream()));
            String permissions = inforeader.readLine();
            String owneruid = inforeader.readLine();
            String ownergid = inforeader.readLine();
            String lastmodified = inforeader.readLine();
            String size = inforeader.readLine();
            if ((size == null) || (lastmodified == null) || (ownergid == null) || (owneruid == null) || (permissions == null)) {
                throw new Exception();
            }
            fileArtifact.addAnnotation("lastmodified", lastmodified + "000");
            fileArtifact.addAnnotation("size", size);
            fileArtifact.addAnnotation("permissions", permissions);
            fileArtifact.addAnnotation("owneruid", owneruid);
            fileArtifact.addAnnotation("ownergid", ownergid);
            return fileArtifact;
        } catch (Exception exception) {
            Artifact fileArtifact = new Artifact();
            File file = new File(path);
            String[] filename = path.split("/");
            if (filename.length > 0) {
                fileArtifact.addAnnotation("filename", filename[filename.length - 1]);
            }
            fileArtifact.addAnnotation("path", path);
            if ((file.lastModified() != 0) && (file.length() != 0)) {
                String lastmodified = Long.toString(file.lastModified());
                String filesize = Long.toString(file.length());
                fileArtifact.addAnnotation("lastmodified", lastmodified);
                fileArtifact.addAnnotation("size", filesize);
            }
            return fileArtifact;
        }
    }

    private Process getProcessVertex(String pid) {
        // Use the ps utility to create a process vertex and add it to the current
        // process tree. This is done recursively on the parent process to ensure
        // completeness of the process tree.
        Process processVertex = (Process) processVertices.get(pid);
        if (processVertex != null) {
            return processVertex;
        }
        try {
            String line = "";
            java.lang.Process pidinfo = Runtime.getRuntime().exec("ps -p " + pid + " -co pid,ppid,uid,gid,lstart,sess,comm");
            BufferedReader pidreader = new BufferedReader(new InputStreamReader(pidinfo.getInputStream()));
            line = pidreader.readLine();
            line = pidreader.readLine();
            if (line == null) {
                return null;
            }
            processVertex = new Process();
            String info[] = line.trim().split("\\s+", 11);
            processVertex.addAnnotation("pidname", info[10]);
            processVertex.addAnnotation("pid", pid);
            processVertex.addAnnotation("ppid", info[1]);
            String timestring = info[4] + " " + info[5] + " " + info[6] + " " + info[7] + " " + info[8];
            String starttime = Long.toString(new java.text.SimpleDateFormat("EEE MMM d HH:mm:ss yyyy").parse(timestring).getTime());
            processVertex.addAnnotation("starttime", starttime);
            processVertex.addAnnotation("sessionid", info[9]);
            processVertex.addAnnotation("uid", info[2]);
            processVertex.addAnnotation("gid", info[3]);
            pidinfo = Runtime.getRuntime().exec("ps -p " + pid + " -o command");
            pidreader = new BufferedReader(new InputStreamReader(pidinfo.getInputStream()));
            line = pidreader.readLine();
            line = pidreader.readLine();
            if (line != null) {
                processVertex.addAnnotation("commandline", line);
            }
            pidinfo = Runtime.getRuntime().exec("ps -p " + pid + " -Eo command");
            pidreader = new BufferedReader(new InputStreamReader(pidinfo.getInputStream()));
            line = pidreader.readLine();
            line = pidreader.readLine();
            if ((line != null) && (line.length() > processVertex.getAnnotation("commandline").length())) {
                processVertex.addAnnotation("environment", line.substring(processVertex.getAnnotation("commandline").length()));
            }
        } catch (Exception exception) {
            exception.printStackTrace(System.err);
        }
        processVertices.put(pid, processVertex);
        if (processTree.contains(pid) == false) {
            Process parentVertex = getProcessVertex(processVertex.getAnnotation("ppid"));
            if (parentVertex != null) {
                AbstractEdge edge = new WasTriggeredBy(processVertex, parentVertex);
                pushToBuffer(parentVertex);
                pushToBuffer(processVertex);
                pushToBuffer(edge);
                processTree.add(pid);
            }
        }
        return processVertex;
    }

    private void parseEvent(String line) {
        // This is the main loop that reads the audit data and tokenizes it to create
        // provenance semantics.
        StringTokenizer tokenizer = new StringTokenizer(line, ",");
        int token_type = Integer.parseInt(tokenizer.nextToken());

        // token types derive from
        // http://www.opensource.apple.com/source/xnu/xnu-1456.1.26/bsd/bsm/audit_record.h
        switch (token_type) {

            // header defines the event type, audit event IDs derive from
            // http://www.opensource.apple.com/source/xnu/xnu-1456.1.26/bsd/bsm/audit_kevents.h
            case 20:						// AUT_HEADER32
            case 21:						// AUT_HEADER32_EX
            case 116: 						// AUT_HEADER64
            case 121: 						// AUT_HEADER64_EX
                String record_length = tokenizer.nextToken();
                String audit_record_version = tokenizer.nextToken();
                int event_id = Integer.parseInt(tokenizer.nextToken());
                String event_id_modifier = tokenizer.nextToken();
                String date_time = tokenizer.nextToken();
                String offset_msec = tokenizer.nextToken();

                current_event_id = event_id;
                currentEventTime = date_time + offset_msec;
                break;

            case 36:						// AUT_SUBJECT32
            case 122:						// AUT_SUBJECT32_EX
            case 117:						// AUT_SUBJECT64
            case 124:						// AUT_SUBJECT64_EX
                String user_audit_id = tokenizer.nextToken();
                String euid = tokenizer.nextToken();
                String egid = tokenizer.nextToken();
                String uid = tokenizer.nextToken();
                String gid = tokenizer.nextToken();
                String pid = tokenizer.nextToken();
                /*
                String sessionid = tokenizer.nextToken();
                String deviceid = tokenizer.nextToken();
                String machineid = tokenizer.nextToken();
                 * 
                 */
                eventPID = pid;
                if ((current_event_id == 2) || ((current_event_id > 71) && (current_event_id < 84))) {
                    if (pid.equals(javaPID)) {
                        tempVertex1 = null;
                    } else {
                        tempVertex1 = getProcessVertex(pid);
                    }
                }
                break;

            case 38:						// AUT_PROCESS32
            case 123:						// AUT_PROCESS32_EX
            case 119:						// AUT_PROCESS64
            case 125:						// AUT_PROCESS64_EX
                /*
                String process_user_audit_id = tokenizer.nextToken();
                String process_euid = tokenizer.nextToken();
                String process_egid = tokenizer.nextToken();
                String process_uid = tokenizer.nextToken();
                String process_gid = tokenizer.nextToken();
                String process_pid = tokenizer.nextToken();
                String process_session_id = tokenizer.nextToken();
                String process_device_id = tokenizer.nextToken();
                String process_machine_id = tokenizer.nextToken();
                 * 
                 */
                break;

            case 39:						// AUT_RETURN32
            case 114:						// AUT_RETURN64
                String error = tokenizer.nextToken();
                String return_value = tokenizer.nextToken();
                if ((current_event_id == 2) && (tempVertex1 != null)) {                    // fork occurred, determine child PID
                    tempVertex2 = getProcessVertex(return_value);
                    if (tempVertex2 == null) {                  // short-lived process
                        tempVertex2 = new Process();
                        tempVertex2.addAnnotation("pid", return_value);
                        tempVertex2.addAnnotation("ppid", tempVertex1.getAnnotation("pid"));
                    }
                }
                break;

            case 49: 						// AUT_ATTR
            case 62:						// AUT_ATTR32
            case 115:						// AUT_ATTR64
                /*
                String file_access_mode = tokenizer.nextToken();
                String owneruid = tokenizer.nextToken();
                String ownergid = tokenizer.nextToken();
                String filesystemid = tokenizer.nextToken();
                String inodeid = tokenizer.nextToken();
                String filedeviceid = tokenizer.nextToken();
                 * 
                 */
                if (current_event_id == 42) {
                    tempVertex1 = createFileVertex(currentFilePath);
                }
                break;

            case 45:						// AUT_ARG32
            case 113:						// AUT_ARG64
                /*
                String arg_number = tokenizer.nextToken();
                String arg_value = tokenizer.nextToken();
                String arg_text = tokenizer.nextToken();
                 * 
                 */
                break;

            case 35: 						// AUT_PATH
                String path = tokenizer.nextToken();
                currentFilePath = path;
                if ((current_event_id == 42) && (tempVertex1 != null)) {
                    tempVertex2 = createFileVertex(currentFilePath);
                }
                break;

            case 40: 						// AUT_TEXT
                /*
                String text_string = tokenizer.nextToken();
                 * 
                 */
                break;

            case 19:						// AUT_TRAILER
                AbstractEdge edge = null;
                if (current_event_id == 72) { // read only
                    tempVertex2 = createFileVertex(currentFilePath);
                    if ((tempVertex1 != null) && (tempVertex2 != null)) {
                        edge = new Used((Process) tempVertex1, (Artifact) tempVertex2);
                        pushToBuffer(tempVertex1);
                        pushToBuffer(tempVertex2);
                        pushToBuffer(edge);
                    }
                } else if ((current_event_id > 72) && (current_event_id < 84)) { // read,write,create
                    tempVertex2 = createFileVertex(currentFilePath);
                    if ((tempVertex1 != null) && (tempVertex2 != null)) {
                        edge = new WasGeneratedBy((Artifact) tempVertex2, (Process) tempVertex1);
                        pushToBuffer(tempVertex1);
                        pushToBuffer(tempVertex2);
                        pushToBuffer(edge);
                    }
                } else if (current_event_id == 2) { // fork
                    if ((tempVertex1 != null) && (tempVertex2 != null)) {
                        edge = new WasTriggeredBy((Process) tempVertex2, (Process) tempVertex1);
                        pushToBuffer(tempVertex1);
                        pushToBuffer(tempVertex2);
                        pushToBuffer(edge);
                    }
                } else if (current_event_id == 42) { // rename
                    AbstractVertex procVertex = getProcessVertex(eventPID);
                    if ((tempVertex1 != null) && (tempVertex2 != null) && (procVertex != null)) {
                        edge = new Used((Process) procVertex, (Artifact) tempVertex1);
                        pushToBuffer(procVertex);
                        pushToBuffer(tempVertex1);
                        pushToBuffer(edge);
                        edge = new WasGeneratedBy((Artifact) tempVertex2, (Process) procVertex);
                        pushToBuffer(tempVertex2);
                        pushToBuffer(edge);
                        edge = new WasDerivedFrom((Artifact) tempVertex2, (Artifact) tempVertex1);
                        pushToBuffer(edge);
                    }
                } else if ((current_event_id == 1) || (current_event_id == 15)) { // exit, kill
                    processVertices.remove(eventPID);
                }
                current_event_id = 0;
                tempVertex1 = null;
                tempVertex2 = null;
                edge = null;
                currentFilePath = null;
                eventPID = null;
                break;

            case 128:						// AUT_SOCKINET32
            case 129:						// AUT_SOCKINET128
                int socket_family = Integer.parseInt(tokenizer.nextToken());
                int socket_local_port = Integer.parseInt(tokenizer.nextToken());
                String socket_address = tokenizer.nextToken();
                break;

            case 82: 						// AUT_EXIT
                String exit_status = tokenizer.nextToken();
                String exit_value = tokenizer.nextToken();
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


            default:
                break;

        }
    }

    private void pushToBuffer(Object incomingObject) {
        // This method is used to push provenance objects to the reporter's buffer.
        if (sentObjects.add(incomingObject.hashCode())) {
            if (incomingObject instanceof AbstractVertex) {
                putVertex((AbstractVertex) incomingObject);
            } else if (incomingObject instanceof AbstractEdge) {
                putEdge((AbstractEdge) incomingObject);
            }
        }
    }
}
