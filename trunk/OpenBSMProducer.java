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
    private HashMap processVertices;
    private HashSet sentObjects;
    private HashSet processTree;
    private Vertex tempVertex1, tempVertex2;
    private int current_event_id;
    private String current_event_time;
    private String current_file_path;
    private boolean shutdown;

    public boolean initialize(Buffer buff) {
        buffer = buff;
        processVertices = new HashMap();
        sentObjects = new HashSet();
        processTree = new HashSet();
        shutdown = false;
        buildProcessTree();

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
                                eventreader.close();
                                pipeprocess.destroy();
                                break;
                            }
                            if (line != null) {
                                parseEvent(line);
                            }
                            line = eventreader.readLine();
                        }
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

    private void buildProcessTree() {
        Vertex parentVertex, childVertex;
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
                if (childinfo[2].equals("ps")) break;
                childVertex = getProcessVertex(childinfo[0]);
                if (childVertex != null) {
                    parentVertex = (Vertex)processVertices.get(childVertex.getAnnotationValue("ppid"));
                    Edge edge = new WasTriggeredBy((Process)childVertex, (Process)parentVertex);
                    pushToBuffer(parentVertex);
                    pushToBuffer(childVertex);
                    pushToBuffer(edge);
                    processTree.add(childinfo[0]);
                    processTree.add(childinfo[1]);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Vertex createFileVertex(String path) {
        try {
            Vertex f = new Artifact();
            java.lang.Process fileinfo = Runtime.getRuntime().exec("stat -f \"%Op%n%Su(%u)%n%Sg(%g)%n%m%n%z\" " + path);
            BufferedReader inforeader = new BufferedReader(new InputStreamReader(fileinfo.getInputStream()));
            f.addAnnotation("permissions", inforeader.readLine().replace("\"", ""));
            f.addAnnotation("owneruid", inforeader.readLine());
            f.addAnnotation("ownergid", inforeader.readLine());
            f.addAnnotation("lastmodified", inforeader.readLine());
            f.addAnnotation("size", inforeader.readLine().replace("\"", ""));
            String[] filename = path.split("/");
            f.addAnnotation("filename", filename[filename.length-1]);
            f.addAnnotation("path", path);
            fileinfo.destroy();
            return f;
        } catch (Exception e) {
            Vertex f = new Artifact();
            String[] filename = path.split("/");
            if (filename.length > 0) {
                f.addAnnotation("filename", filename[filename.length-1]);
            }
            f.addAnnotation("path", path);
            return f;
        }
    }

    private Vertex getProcessVertex(String pid) {
        Vertex v = (Vertex)processVertices.get(pid);
        if (v != null) return v;
        try {
            String line = "";
            java.lang.Process pidinfo = Runtime.getRuntime().exec("ps -p " + pid + " -co pid,ppid,uid,gid,comm");
            BufferedReader pidreader = new BufferedReader(new InputStreamReader(pidinfo.getInputStream()));
            line = pidreader.readLine();
            line = pidreader.readLine();
            pidinfo.destroy();
            if (line == null) return null;
            v = new Process();
            String info[] = line.trim().split("\\s+", 5);
            v.addAnnotation("pid", pid);
            v.addAnnotation("ppid", info[1]);
            v.addAnnotation("uid", info[2]);
            v.addAnnotation("gid", info[3]);
            v.addAnnotation("pidname", info[4]);
            pidinfo = Runtime.getRuntime().exec("ps -p " + pid + " -o lstart");
            pidreader = new BufferedReader(new InputStreamReader(pidinfo.getInputStream()));
            line = pidreader.readLine();
            line = pidreader.readLine();
            if (line != null) v.addAnnotation("starttime", line);
            pidinfo.destroy();
            pidinfo = Runtime.getRuntime().exec("ps -p " + pid + " -o command");
            pidreader = new BufferedReader(new InputStreamReader(pidinfo.getInputStream()));
            line = pidreader.readLine();
            line = pidreader.readLine();
            if (line != null) v.addAnnotation("commandline", line);
            pidinfo.destroy();
            pidinfo = Runtime.getRuntime().exec("ps -p " + pid + " -Eo command");
            pidreader = new BufferedReader(new InputStreamReader(pidinfo.getInputStream()));
            line = pidreader.readLine();
            line = pidreader.readLine();
            if ((line != null) && (line.length() > v.getAnnotationValue("commandline").length())) {
                v.addAnnotation("environment", line.substring(v.getAnnotationValue("commandline").length()));
            }
            pidinfo.destroy();
        } catch (Exception e) {
            e.printStackTrace();
        }
        processVertices.put(pid, v);
        if (processTree.contains(pid) == false) {
            Vertex p = getProcessVertex(v.getAnnotationValue("ppid"));
            if (p != null) {
                Edge e = new WasTriggeredBy((Process)v, (Process)p);
                pushToBuffer(p);
                pushToBuffer(v);
                pushToBuffer(e);
                processTree.add(pid);
            }
        }
        return v;
    }

    private void parseEvent(String line) {
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
                current_event_time = date_time + offset_msec;
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
                if ((current_event_id == 2) || ((current_event_id > 71) && (current_event_id < 84))) {
                    tempVertex1 = getProcessVertex(pid);
                }
                break;

            case 38:						// AUT_PROCESS32
            case 123:						// AUT_PROCESS32_EX
            case 119:						// AUT_PROCESS64
            case 125:						// AUT_PROCESS64_EX
                String process_user_audit_id = tokenizer.nextToken();
                String process_euid = tokenizer.nextToken();
                String process_egid = tokenizer.nextToken();
                String process_uid = tokenizer.nextToken();
                String process_gid = tokenizer.nextToken();
                String process_pid = tokenizer.nextToken();
                String process_session_id = tokenizer.nextToken();
                String process_device_id = tokenizer.nextToken();
                String process_machine_id = tokenizer.nextToken();
                break;

            case 39:						// AUT_RETURN32
            case 114:						// AUT_RETURN64
                String error = tokenizer.nextToken();
                String return_value = tokenizer.nextToken();
                if (current_event_id == 2) {                    // fork occurred, determine child PID
                    tempVertex2 = getProcessVertex(return_value);
                    if (tempVertex2 == null) {                  // short-lived process
                        tempVertex2 = new Process();
                        tempVertex2.addAnnotation("pid", return_value);
                        tempVertex2.addAnnotation("ppid", tempVertex1.getAnnotationValue("pid"));
                    }
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
                break;

            case 45:						// AUT_ARG32
            case 113:						// AUT_ARG64
                String arg_number = tokenizer.nextToken();
                String arg_value = tokenizer.nextToken();
                String arg_text = tokenizer.nextToken();
                break;

            case 35: 						// AUT_PATH
                String path = tokenizer.nextToken();
                if ((current_event_id > 71) && (current_event_id < 84)) {
                    current_file_path = path;
                }
                break;

            case 40: 						// AUT_TEXT
                String text_string = tokenizer.nextToken();
                break;

            case 19:						// AUT_TRAILER
                Edge edge = null;
                if (current_event_id == 72) {
                    tempVertex2 = createFileVertex(current_file_path);
                    edge = new Used((Process)tempVertex1, (Artifact)tempVertex2);
                    edge.addAnnotation("endtime", current_event_time);
                } else if ((current_event_id > 72) && (current_event_id < 84)) {
                    tempVertex2 = createFileVertex(current_file_path);
                    edge = new WasGeneratedBy((Artifact)tempVertex2, (Process)tempVertex1);
                    edge.addAnnotation("endtime", current_event_time);
                } else if (current_event_id == 2) {
                    edge = new WasTriggeredBy((Process)tempVertex2, (Process)tempVertex1);
                }
                if ((tempVertex1 != null) && (tempVertex2 != null) && (edge != null)) {
                    pushToBuffer(tempVertex1);
                    pushToBuffer(tempVertex2);
                    pushToBuffer(edge);
                }
                current_event_id = 0;
                tempVertex1 = null;
                tempVertex2 = null;
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
