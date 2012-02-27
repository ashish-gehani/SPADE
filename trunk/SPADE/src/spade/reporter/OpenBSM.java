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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import spade.core.AbstractReporter;
import spade.core.AbstractVertex;
import spade.edge.opm.Used;
import spade.edge.opm.WasDerivedFrom;
import spade.edge.opm.WasGeneratedBy;
import spade.edge.opm.WasTriggeredBy;
import spade.vertex.custom.File;
import spade.vertex.custom.Program;

/**
 * The OpenBSM reporter.
 *
 * @author dawood
 */
public class OpenBSM extends AbstractReporter {

    private BufferedReader eventReader;
    private java.lang.Process nativeProcess;
    private Map<String, Program> processVertices;
    private Map<String, File> fileVertices;
    private AbstractVertex tempVertex1, tempVertex2;
    private int current_event_id;
    private String currentEventTime;
    private String currentFilePath;
    private String myPID;
    private String nativePID;
    private String eventPID;
    private volatile boolean shutdown;
    private final String simpleDatePattern = "EEE MMM d H:mm:ss yyyy";
    private final String binaryPath = "../build/spade/reporter/spadeOpenBSM";
    private final int THREAD_SLEEP_DELAY = 5;

    @Override
    public boolean launch(String arguments) {
        // The argument to the launch method is unused.
        processVertices = new HashMap<String, Program>();
        fileVertices = new HashMap<String, File>();

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

            Runnable eventProcessor = new Runnable() {

                public void run() {
                    try {
                        while (!shutdown) {
                            if (eventReader.ready()) {
                                String line = eventReader.readLine();
                                if (line != null) {
                                    // Process the event.
                                    parseEvent(line);
                                }
                            }
                            Thread.sleep(THREAD_SLEEP_DELAY);
                        }
                        // Get the pid of the process and kill it.
                        nativeProcess.destroy();
                        Runtime.getRuntime().exec("kill " + nativePID);
                    } catch (Exception exception) {
                        Logger.getLogger(OpenBSM.class.getName()).log(Level.SEVERE, null, exception);
                    }
                }
            };
            new Thread(eventProcessor, "OpenBSM-Thread").start();

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
                String childinfo[] = line.split("\\s+", 3);
                if (childinfo[2].equals("ps")) {
                    break;
                }
                getProcessVertex(childinfo[0]);
            }
        } catch (Exception exception) {
            Logger.getLogger(OpenBSM.class.getName()).log(Level.SEVERE, null, exception);
        }
    }

    private File getFileVertex(String path, boolean refresh) {
        // Create a file artifact.
        // The refresh flag is used for caching purposes - a new file vertex is
        // created if the event modified the file (i.e., write, truncate, rename, etc)
        // but the cached one is used in case of read.
        if ((!refresh) && (fileVertices.containsKey(path))) {
            return fileVertices.get(path);
        }
        File fileArtifact = new File();
        fileArtifact.addAnnotation("path", path);
        String[] filename = path.split("/");
        if (filename.length > 0) {
            fileArtifact.addAnnotation("filename", filename[filename.length - 1]);
        }
        try {
            java.io.File file = new java.io.File(path);
            if ((file.lastModified() != 0L) && (file.length() != 0L)) {
                long lastmodified = file.lastModified();
                String filesize = Long.toString(file.length());
                fileArtifact.addAnnotation("lastmodified_unix", Long.toString(lastmodified));
                fileArtifact.addAnnotation("lastmodified_simple", new java.text.SimpleDateFormat(simpleDatePattern).format(new java.util.Date(lastmodified)));
                fileArtifact.addAnnotation("size", filesize);
            }
        } catch (Exception exception) {
            // Ignore
        }
        fileVertices.put(path, fileArtifact);
        return fileArtifact;
    }

    private Program getProcessVertex(String pid) {
        // Use the ps utility to create a process vertex and add it to the current
        // process tree. This is done recursively on the parent process to ensure
        // completeness of the process tree.
        if (processVertices.containsKey(pid)) {
            return processVertices.get(pid);
        }
        try {
            Program processVertex = new Program();
            String line;
            java.lang.Process pidinfo = Runtime.getRuntime().exec("ps -p " + pid + " -co pid=,ppid=,uid=,user=,gid=,lstart=,sess=,comm=");
            BufferedReader pidreader = new BufferedReader(new InputStreamReader(pidinfo.getInputStream()));
            line = pidreader.readLine();
            if (line == null) {
                return null;
            }
            String info[] = line.trim().split("\\s+", 12);
            String ppid = info[1];
            if (ppid.equals(myPID)) {
                // Return null if this was our own child process.
                return null;
            }

            processVertex.addAnnotation("pidname", info[11]);
            processVertex.addAnnotation("pid", pid);
            processVertex.addAnnotation("ppid", info[1]);
            String timestring = info[5] + " " + info[6] + " " + info[7] + " " + info[8] + " " + info[9];
            Long unixtime = new java.text.SimpleDateFormat(simpleDatePattern).parse(timestring).getTime();
            String starttime_unix = Long.toString(unixtime);
            String starttime_simple = new java.text.SimpleDateFormat(simpleDatePattern).format(new java.util.Date(unixtime));
            processVertex.addAnnotation("starttime_unix", starttime_unix);
            processVertex.addAnnotation("starttime_simple", starttime_simple);
            processVertex.addAnnotation("sessionid", info[10]);
            processVertex.addAnnotation("uid", info[2]);
            processVertex.addAnnotation("user", info[3]);
            processVertex.addAnnotation("groupid", info[4]);

            try {
                pidinfo = Runtime.getRuntime().exec("ps -p " + pid + " -o command=");
                pidreader = new BufferedReader(new InputStreamReader(pidinfo.getInputStream()));
                line = pidreader.readLine();
                if (line != null) {
                    processVertex.addAnnotation("commandline", line);
                }

                pidinfo = Runtime.getRuntime().exec("ps -p " + pid + " -Eo command=");
                pidreader = new BufferedReader(new InputStreamReader(pidinfo.getInputStream()));
                line = pidreader.readLine();
                if ((line != null) && (line.length() > processVertex.getAnnotation("commandline").length())) {
                    processVertex.addAnnotation("environment", line.substring(processVertex.getAnnotation("commandline").length()).trim());
                }
            } catch (Exception exception) {
                // Ignore
            }

            putVertex(processVertex);
            processVertices.put(pid, processVertex);
            if (Integer.parseInt(ppid) > 0) {
                Program parentVertex = getProcessVertex(ppid);
                if (parentVertex != null) {
                    putVertex(parentVertex);
                    putEdge(new WasTriggeredBy(processVertex, parentVertex));
                }
            }
            return processVertex;
        } catch (Exception exception) {
            Logger.getLogger(OpenBSM.class.getName()).log(Level.WARNING, null, exception);
            return null;
        }
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
                String sessionid = tokenizer.nextToken();
                String deviceid = tokenizer.nextToken();
                String machineid = tokenizer.nextToken();

                eventPID = pid;
                if ((current_event_id == 2) || ((current_event_id > 71) && (current_event_id < 84))) {
                    tempVertex1 = ((eventPID.equals(myPID)) || (eventPID.equals(nativePID))) ? null : getProcessVertex(pid);
                }
                break;

            case 38:						// AUT_PROCESS32
            case 123:						// AUT_PROCESS32_EX
            case 119:						// AUT_PROCESS64
            case 125:						// AUT_PROCESS64_EX
                /*
                 * String process_user_audit_id = tokenizer.nextToken(); String
                 * process_euid = tokenizer.nextToken(); String process_egid =
                 * tokenizer.nextToken(); String process_uid =
                 * tokenizer.nextToken(); String process_gid =
                 * tokenizer.nextToken(); String process_pid =
                 * tokenizer.nextToken(); String process_session_id =
                 * tokenizer.nextToken(); String process_device_id =
                 * tokenizer.nextToken(); String process_machine_id =
                 * tokenizer.nextToken();
                 *
                 */
                break;

            case 39:						// AUT_RETURN32
            case 114:						// AUT_RETURN64
                String error = tokenizer.nextToken();
                String return_value = tokenizer.nextToken();
                if ((current_event_id == 2) && (tempVertex1 != null)) {
                    // fork occurred, determine child PID
                    tempVertex2 = getProcessVertex(return_value);
                }
                break;

            case 49: 						// AUT_ATTR
            case 62:						// AUT_ATTR32
            case 115:						// AUT_ATTR64
                /*
                 * String file_access_mode = tokenizer.nextToken(); String
                 * owneruid = tokenizer.nextToken(); String ownergid =
                 * tokenizer.nextToken(); String filesystemid =
                 * tokenizer.nextToken(); String inodeid =
                 * tokenizer.nextToken(); String filedeviceid =
                 * tokenizer.nextToken();
                 *
                 */
                if (current_event_id == 42) { // rename
                    tempVertex1 = getFileVertex(currentFilePath, false);
                }
                break;

            case 45:						// AUT_ARG32
            case 113:						// AUT_ARG64
                /*
                 * String arg_number = tokenizer.nextToken(); String arg_value =
                 * tokenizer.nextToken(); String arg_text =
                 * tokenizer.nextToken();
                 *
                 */
                break;

            case 35: 						// AUT_PATH
                currentFilePath = tokenizer.nextToken();
                if ((current_event_id == 42) && (tempVertex1 != null)) { // rename
                    tempVertex2 = getFileVertex(currentFilePath, true);
                }
                break;

            case 40: 						// AUT_TEXT
                /*
                 * String text_string = tokenizer.nextToken();
                 *
                 */
                break;

            case 19:						// AUT_TRAILER
                if ((tempVertex1 != null)) {
                    if (current_event_id == 72) { // read only
                        tempVertex2 = getFileVertex(currentFilePath, false);
                        if (tempVertex2 != null) {
                            putVertex(tempVertex1);
                            putVertex(tempVertex2);
                            Used usedEdge = new Used((Program) tempVertex1, (File) tempVertex2);
                            usedEdge.addAnnotation("time", currentEventTime);
                            putEdge(usedEdge);
                        }
                    } else if ((current_event_id > 72) && (current_event_id < 84)) { // read,write,create
                        tempVertex2 = getFileVertex(currentFilePath, true);
                        if (tempVertex2 != null) {
                            putVertex(tempVertex1);
                            putVertex(tempVertex2);
                            WasGeneratedBy generatedEdge = new WasGeneratedBy((File) tempVertex2, (Program) tempVertex1);
                            generatedEdge.addAnnotation("time", currentEventTime);
                            putEdge(generatedEdge);
                        }
                    } else if (current_event_id == 2) { // fork
                        if (tempVertex2 != null) {
                            putVertex(tempVertex1);
                            putVertex(tempVertex2);
                            WasTriggeredBy triggeredEdge = new WasTriggeredBy((Program) tempVertex2, (Program) tempVertex1);
                            putEdge(triggeredEdge);
                        }
                    } else if (current_event_id == 42) { // rename
                        Program procVertex = getProcessVertex(eventPID);
                        if ((tempVertex2 != null) && (procVertex != null)) {
                            putVertex(procVertex);
                            putVertex(tempVertex1);
                            putVertex(tempVertex2);
                            Used usedEdge = new Used(procVertex, (File) tempVertex1);
                            usedEdge.addAnnotation("time", currentEventTime);
                            putEdge(usedEdge);
                            WasGeneratedBy generatedEdge = new WasGeneratedBy((File) tempVertex2, procVertex);
                            generatedEdge.addAnnotation("time", currentEventTime);
                            putEdge(generatedEdge);
                            WasDerivedFrom renameEdge = new WasDerivedFrom((File) tempVertex2, (File) tempVertex1);
                            renameEdge.addAnnotation("operation", "rename");
                            renameEdge.addAnnotation("time", currentEventTime);
                            putEdge(renameEdge);
                        }
                    } else if ((current_event_id == 1) || (current_event_id == 15)) { // exit, kill
                        processVertices.remove(eventPID);
                    }
                }
                current_event_id = 0;
                tempVertex1 = null;
                tempVertex2 = null;
                currentFilePath = null;
                eventPID = null;
                currentEventTime = null;
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
}
