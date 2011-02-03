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

public class FUSEProducer implements ProducerInterface {

    private long boottime;
    private Buffer buffer;
    private HashMap<String, Vertex> LocalCache;

    public native int launchFUSE();

    public void createProcessVertex(String pid, Process tempVertex) {
        try {
            tempVertex.addAnnotation("pid", pid);
            BufferedReader procReader = new BufferedReader(new FileReader("/proc/" + pid + "/status"));
            String nameline = procReader.readLine();
            procReader.readLine();
            String tgidline = procReader.readLine();
            procReader.readLine();
            String ppidline = procReader.readLine();
            String tracerpidline = procReader.readLine();
            String uidline = procReader.readLine();
            String gidline = procReader.readLine();
            procReader.close();

            BufferedReader statReader = new BufferedReader(new FileReader("/proc/" + pid + "/stat"));
            String statline = statReader.readLine();
            statReader.close();

            BufferedReader cmdlineReader = new BufferedReader(new FileReader("/proc/" + pid + "/cmdline"));
            String cmdline = cmdlineReader.readLine();
            cmdlineReader.close();
            if (cmdline == null) {
                cmdline = "";
            } else {
                cmdline = cmdline.replace("\0", " ");
                cmdline = cmdline.replace("\"", "'");
            }

            String stats[] = statline.split("\\s+");
            long elapsedtime = Long.parseLong(stats[21]) * 10;
            long starttime = boottime + elapsedtime;
//            String stime = new java.text.SimpleDateFormat("MM/dd/yyyy HH:mm:ss.SSS").format(new java.util.Date(starttime));
            String stime = Long.toString(starttime);

            StringTokenizer st1 = new StringTokenizer(nameline);
            st1.nextToken();
            String name = st1.nextToken();

            StringTokenizer st2 = new StringTokenizer(tgidline);
            st2.nextToken();
            String tgid = st2.nextToken("").trim();

            StringTokenizer st3 = new StringTokenizer(ppidline);
            st3.nextToken();
            String ppid = st3.nextToken("").trim();

            StringTokenizer st4 = new StringTokenizer(tracerpidline);
            st4.nextToken();
            String tracerpid = st4.nextToken("").trim();

            StringTokenizer st5 = new StringTokenizer(uidline);
            st5.nextToken();
            String uid = st5.nextToken("").trim();

            StringTokenizer st6 = new StringTokenizer(gidline);
            st6.nextToken();
            String gid = st6.nextToken("").trim();

            tempVertex.addAnnotation("ppid", ppid);
            tempVertex.addAnnotation("pidname", name);
            tempVertex.addAnnotation("tgid", tgid);
            tempVertex.addAnnotation("tracerpid", tracerpid);
            tempVertex.addAnnotation("uid", uid.replace("\t", " "));
            tempVertex.addAnnotation("gid", gid.replace("\t", " "));
            tempVertex.addAnnotation("starttime", stime);
            tempVertex.addAnnotation("group", stats[4]);
            tempVertex.addAnnotation("session", stats[5]);
            tempVertex.addAnnotation("commandline", cmdline);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            BufferedReader environReader = new BufferedReader(new FileReader("/proc/" + pid + "/environ"));
            String environ = environReader.readLine();
            environReader.close();
            if (environ != null) {
                environ = environ.replace("\0", ", ");
                environ = environ.replace("\"", "'");
                tempVertex.addAnnotation("environment", environ);
            }
        } catch (Exception e) {
        }

    }

    public boolean initialize(Buffer buff) {
        LocalCache = new HashMap<String, Vertex>();
        buffer = buff;

        System.loadLibrary("jfuse");

        boottime = 0;
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
        } catch (Exception e) {
            e.printStackTrace();
        }

        Process rootVertex = new Process();
        rootVertex.addAnnotation("ppid", "0");
        rootVertex.addAnnotation("pid", "0");
        rootVertex.addAnnotation("pidname", "System");
//        String stime = new java.text.SimpleDateFormat("MM/dd/yyyy HH:mm:ss.SSS").format(new java.util.Date(boottime));
        String stime = Long.toString(boottime);
        rootVertex.addAnnotation("boottime", stime);
        buffer.putVertex(rootVertex);
        LocalCache.put("0", rootVertex);

        String path = "/proc";
        String processDir;
        File folder = new File(path);
        File[] listOfFiles = folder.listFiles();

        for (int i = 0; i < listOfFiles.length; i++) {
            if (listOfFiles[i].isDirectory()) {

                processDir = listOfFiles[i].getName();
                try {
                    Integer.parseInt(processDir);
                    Process tempVertex = new Process();
                    createProcessVertex(processDir, tempVertex);
                    String ppid = (String) tempVertex.getAnnotationValue("ppid");
                    buffer.putVertex(tempVertex);
                    LocalCache.put(tempVertex.getAnnotationValue("pid"), tempVertex);
                    if (Integer.parseInt(ppid) >= 0) {
                        WasTriggeredBy tempEdge = new WasTriggeredBy((Process) LocalCache.get(processDir), (Process) LocalCache.get(ppid), "WasTriggeredBy", "WasTriggeredBy");
                        buffer.putEdge(tempEdge);
                    }
                } catch (Exception e) {
                    continue;
                }

            }
        }

        Runnable r1 = new Runnable() {

            public void run() {
                try {
                    launchFUSE();
                } catch (Exception iex) {
                    iex.printStackTrace();
                }
            }
        };
        new Thread(r1).start();

        return true;
    }

    public void checkProcessTree(String pid) {
        try {
            while (true) {
                if (LocalCache.get(pid) != null) {
                    return;
                }
                Process tempVertex = new Process();
                createProcessVertex(pid, tempVertex);
                String ppid = (String) tempVertex.getAnnotationValue("ppid");
                buffer.putVertex(tempVertex);
                LocalCache.put(tempVertex.getAnnotationValue("pid"), tempVertex);
                if (Integer.parseInt(ppid) >= 0) {
                    checkProcessTree(ppid);
                    WasTriggeredBy tempEdge = new WasTriggeredBy((Process) LocalCache.get(pid), (Process) LocalCache.get(ppid), "WasTriggeredBy", "WasTriggeredBy");
                    buffer.putEdge(tempEdge);
                } else {
                    return;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return;
    }

    public void readwrite(int write, int pid, int iotime, int var1, String path) {
        long now = System.currentTimeMillis();
        checkProcessTree(Integer.toString(pid));
        File file = new File(path);
//        String lastmodified = new java.text.SimpleDateFormat("MM/dd/yyyy HH:mm:ss.SSS").format(new java.util.Date(file.lastModified()));
        String lastmodified = Long.toString(file.lastModified());
        String filesize = Long.toString(file.length());
        Artifact tempVertex = new Artifact();
        tempVertex.addAnnotation("filename", file.getName());
        tempVertex.addAnnotation("path", path);
        tempVertex.addAnnotation("lastmodified", lastmodified);
        tempVertex.addAnnotation("size", filesize);
        buffer.putVertex(tempVertex);
        LocalCache.put(path, tempVertex);
        if (write == 0) {
            Used tempEdge = new Used((Process) LocalCache.get(Integer.toString(pid)), (Artifact) LocalCache.get(path), "Used", "Used");
            tempEdge.addAnnotation("iotime", Integer.toString(iotime));
            tempEdge.addAnnotation("endtime", Long.toString(now));
            buffer.putEdge(tempEdge);
        } else {
            WasGeneratedBy tempEdge = new WasGeneratedBy((Artifact) LocalCache.get(path), (Process) LocalCache.get(Integer.toString(pid)), "WasGeneratedBy", "WasGeneratedBy");
            tempEdge.addAnnotation("iotime", Integer.toString(iotime));
            tempEdge.addAnnotation("endtime", Long.toString(now));
            buffer.putEdge(tempEdge);
        }
    }

    public void rename(int pid, int iotime, int var1, String pathfrom, String pathto) {
        long now = System.currentTimeMillis();
        checkProcessTree(Integer.toString(pid));
        WasDerivedFrom tempEdge = new WasDerivedFrom((Artifact) LocalCache.get(pathto), (Artifact) LocalCache.get(pathfrom), "WasDerivedFrom", "WasDerivedFrom");
        tempEdge.addAnnotation("iotime", Integer.toString(iotime));
        tempEdge.addAnnotation("endtime", Long.toString(now));
        buffer.putEdge(tempEdge);
    }

    public void link(int pid, int var1, int var2, String pathfrom, String pathto) {
        checkProcessTree(Integer.toString(pid));
    }

    public void shutdown() {
        try {
            Runtime.getRuntime().exec("fusermount -u -z test");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
