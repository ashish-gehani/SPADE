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

import java.io.*;
import java.util.*;

public class OpenBSMProducer implements ProducerInterface {

    private Buffer buffer;
    private BufferedReader eventreader;
    private java.lang.Process pipeprocess;
    private boolean shutdown;
    private HashMap processVertices;
    private HashMap processParents;
    private HashMap unfinishedVertices;
    private HashSet sentObjects;
    private Vertex tempVertex1, tempVertex2;
    private Edge tempEdge;
    private int current_event_id;

    public boolean initialize(Buffer buff) {
        buffer = buff;
        processVertices = new HashMap();
        processParents = new HashMap();
        sentObjects = new HashSet();
        unfinishedVertices = new HashMap();
        shutdown = false;

        try {
            String[] cmd = {"/bin/sh", "-c", "sudo praudit -r /dev/auditpipe"};
            pipeprocess = Runtime.getRuntime().exec(cmd);
            eventreader = new BufferedReader(new InputStreamReader(pipeprocess.getInputStream()));
            eventreader.readLine();
            Runnable eventThread = new Runnable() {

                public void run() {
                    try {
                        String line = eventreader.readLine();
                        while (true) {
                            if (shutdown) {
                                break;
                            }
                            if (line != null) {
                                parseEvent(line);
                            }
                            line = eventreader.readLine();
                        }
                        pipeprocess.destroy();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };
            new Thread(eventThread).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    public void shutdown() {
        shutdown = true;
    }

    public void parseEvent(String line) {
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
                int record_length = Integer.parseInt(tokenizer.nextToken());
                int audit_record_version = Integer.parseInt(tokenizer.nextToken());
                int event_id = Integer.parseInt(tokenizer.nextToken());
                int event_id_modifier = Integer.parseInt(tokenizer.nextToken());
                String date_time = tokenizer.nextToken();
                String offset_msec = tokenizer.nextToken();

                current_event_id = event_id;
                if ((event_id > 71) && (event_id < 84)) {       // read/write events
                    tempVertex1 = new Process();
                    tempVertex2 = new Artifact();
                    if (event_id == 72) {
                        tempEdge = new Used((Process) tempVertex1, (Artifact) tempVertex2);
                    } else {
                        tempEdge = new WasGeneratedBy((Artifact) tempVertex2, (Process) tempVertex1);
                    }
                } else if (event_id == 2) {                     // fork
                    tempVertex1 = new Process();
                    tempVertex2 = new Process();
                    tempEdge = new WasTriggeredBy((Process) tempVertex2, (Process) tempVertex1);
                }
                if (tempEdge != null) {
                    tempEdge.addAnnotation("endtime", date_time + offset_msec);
                }
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
                if (tempVertex1 != null) {
                    tempVertex1.addAnnotation("euid", euid);
                    tempVertex1.addAnnotation("egid", egid);
                    tempVertex1.addAnnotation("uid", uid);
                    tempVertex1.addAnnotation("gid", gid);
                    tempVertex1.addAnnotation("pid", pid);
                    tempVertex1.addAnnotation("sessionid", sessionid);
                    tempVertex1.addAnnotation("deviceid", deviceid);
                    tempVertex1.addAnnotation("machineid", machineid);
                    processVertices.put(pid, tempVertex1);
                    pushToBuffer(tempVertex1);
                }
                if (unfinishedVertices.get(pid) != null) {
                    Vertex childVertex = (Vertex) unfinishedVertices.get(pid);
                    childVertex.addAnnotation("euid", euid);
                    childVertex.addAnnotation("egid", egid);
                    childVertex.addAnnotation("uid", uid);
                    childVertex.addAnnotation("gid", gid);
                    childVertex.addAnnotation("sessionid", sessionid);
                    childVertex.addAnnotation("deviceid", deviceid);
                    childVertex.addAnnotation("machineid", machineid);
                    processVertices.put(pid, tempVertex1);
                    unfinishedVertices.remove(pid);
                    Edge triggered = new WasTriggeredBy((Process)childVertex, (Process)processVertices.get(processParents.get(pid)));
                    pushToBuffer(childVertex);
                    pushToBuffer(triggered);
                }
                break;

            case 38:						// AUT_PROCESS32
            case 123:						// AUT_PROCESS32_EX
            case 119:						// AUT_PROCESS64
            case 125:						// AUT_PROCESS64_EX
                int process_user_audit_id = Integer.parseInt(tokenizer.nextToken());
                int process_euid = Integer.parseInt(tokenizer.nextToken());
                int process_egid = Integer.parseInt(tokenizer.nextToken());
                int process_uid = Integer.parseInt(tokenizer.nextToken());
                int process_gid = Integer.parseInt(tokenizer.nextToken());
                int process_pid = Integer.parseInt(tokenizer.nextToken());
                int process_session_id = Integer.parseInt(tokenizer.nextToken());
                int process_device_id = Integer.parseInt(tokenizer.nextToken());
                String process_machine_id = tokenizer.nextToken();
                break;

            case 39:						// AUT_RETURN32
            case 114:						// AUT_RETURN64
                int error = Integer.parseInt(tokenizer.nextToken());
                String return_value = tokenizer.nextToken();
                if (current_event_id == 2) {                    // fork occurred, determine child PID
                    tempVertex2.addAnnotation("pid", return_value);
                    processParents.put(return_value, tempVertex1.getAnnotationValue("pid"));
                    unfinishedVertices.put(return_value, tempVertex2);
                }
                break;

            case 49: 						// AUT_ATTR
            case 62:						// AUT_ATTR32
            case 115:						// AUT_ATTR64
                String file_access_mode = tokenizer.nextToken();
                String owneruid = tokenizer.nextToken();
                String ownergid = tokenizer.nextToken();
                String filesystemid = tokenizer.nextToken();
                String inodeid = tokenizer.nextToken();
                String filedeviceid = tokenizer.nextToken();
                /*
                if ((current_event_id > 71) && (current_event_id < 84) && (tempVertex2 != null)) {
                    tempVertex2.addAnnotation("owneruid", owneruid);
                    tempVertex2.addAnnotation("ownergid", ownergid);
                    tempVertex2.addAnnotation("filesystemid", filesystemid);
                    tempVertex2.addAnnotation("inodeid", inodeid);
                    tempVertex2.addAnnotation("filedeviceid", filedeviceid);
                }
                 * 
                 */
                break;

            case 45:						// AUT_ARG32
            case 113:						// AUT_ARG64
                int arg_number = Integer.parseInt(tokenizer.nextToken());
                String arg_value = tokenizer.nextToken();
                String arg_text = tokenizer.nextToken();
                break;

            case 35: 						// AUT_PATH
                String path = tokenizer.nextToken();
                if ((current_event_id > 71) && (current_event_id < 84) && (tempVertex2 != null)) {
                    String[] filename = path.split("/");
                    if (filename.length > 0) {
                        tempVertex2.addAnnotation("filename", filename[filename.length-1]);
                    }
                    tempVertex2.addAnnotation("path", path);
                }
                break;

            case 40: 						// AUT_TEXT
                String text_string = tokenizer.nextToken();
                break;

            case 19:						// AUT_TRAILER
                if ((tempVertex1 != null) && (tempVertex2 != null) && (tempEdge != null)) {
                    if ((current_event_id > 71) && (current_event_id < 84)) {
                        pushToBuffer(tempVertex1);
                        pushToBuffer(tempVertex2);
                        pushToBuffer(tempEdge);
                    }
                }
                current_event_id = 0;
                tempVertex1 = null;
                tempVertex2 = null;
                tempEdge = null;
                break;

            case 128:						// AUT_SOCKINET32
            case 129:						// AUT_SOCKINET128
                int socket_family = Integer.parseInt(tokenizer.nextToken());
                int socket_local_port = Integer.parseInt(tokenizer.nextToken());
                String socket_address = tokenizer.nextToken();
                break;

            case 82: 						// AUT_EXIT
                String exit_status = tokenizer.nextToken();
                int exit_value = Integer.parseInt(tokenizer.nextToken());
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

    private void pushToBuffer(Object o) {
        if (sentObjects.add(o)) {
            if (o instanceof Vertex) {
                buffer.putVertex((Vertex) o);
            } else if (o instanceof Edge) {
                buffer.putEdge((Edge) o);
            }
        }
    }
}
