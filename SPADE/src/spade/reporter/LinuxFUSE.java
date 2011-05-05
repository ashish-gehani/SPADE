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
import spade.opm.edge.WasTriggeredBy;
import spade.core.AbstractEdge;
import spade.opm.edge.WasGeneratedBy;
import spade.opm.edge.Used;
import spade.opm.edge.WasDerivedFrom;
import spade.opm.vertex.Artifact;
import spade.core.AbstractVertex;
import spade.opm.vertex.Process;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

public class LinuxFUSE extends AbstractReporter {

    private long boottime;
    private Map<String, AbstractVertex> localCache;
    private Map<String, String> links;
    private String mountPoint;

    // The native launchFUSE method to start FUSE. The argument is the
    // mount point.
    public native int launchFUSE(String argument);

    public void createProcessVertex(String pid, Process tempVertex) {
        // The process vertex is created using the proc filesystem.
        try {
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
            String stime_readable = new java.text.SimpleDateFormat("EEE MMM d, h:mm:ss aa").format(new java.util.Date(starttime));
            String stime = Long.toString(starttime);

            StringTokenizer st1 = new StringTokenizer(nameline);
            st1.nextToken();
            String name = st1.nextToken();

            StringTokenizer st3 = new StringTokenizer(ppidline);
            st3.nextToken();
            String ppid = st3.nextToken("").trim();

            StringTokenizer st5 = new StringTokenizer(uidline);
            st5.nextToken();
            String uid = st5.nextToken().trim();

            StringTokenizer st6 = new StringTokenizer(gidline);
            st6.nextToken();
            String gid = st6.nextToken().trim();

            tempVertex.addAnnotation("pidname", name);
            tempVertex.addAnnotation("pid", pid);
            tempVertex.addAnnotation("ppid", ppid);
            tempVertex.addAnnotation("uid", uid);
            tempVertex.addAnnotation("gid", gid);
            tempVertex.addAnnotation("starttime_unix", stime);
            tempVertex.addAnnotation("starttime_simple", stime_readable);
            tempVertex.addAnnotation("group", stats[4]);
            tempVertex.addAnnotation("sessionid", stats[5]);
            tempVertex.addAnnotation("commandline", cmdline);
        } catch (Exception exception) {
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
        } catch (Exception exception) {
        }

    }

    @Override
    public boolean launch(String arguments) {
        // The argument to this reporter is the mount point for FUSE.
        mountPoint = arguments;
        localCache = Collections.synchronizedMap(new HashMap<String, AbstractVertex>());
        links = Collections.synchronizedMap(new HashMap<String, String>());

        // Create a new directory as the mount point for FUSE.
        File mount = new File(mountPoint);
        if (mount.exists()) {
            return false;
        } else {
            try {
                int exitValue = Runtime.getRuntime().exec("mkdir " + mountPoint).waitFor();
                if (exitValue != 0) {
                    System.err.println("Error creating mount point!");
                    throw new Exception();
                }
            } catch (Exception exception) {
                exception.printStackTrace(System.err);
                return false;
            }
        }

        // Load the native library.
        System.loadLibrary("spadeLinuxFUSE");

        // Get the system boot time from the proc filesystem.
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
        } catch (Exception exception) {
            exception.printStackTrace(System.err);
        }

        // Create an initial root vertex which will be used as the root of the
        // process tree.
        Process rootVertex = new Process();
        rootVertex.addAnnotation("pidname", "System");
        rootVertex.addAnnotation("pid", "0");
        rootVertex.addAnnotation("ppid", "0");
        String stime_readable = new java.text.SimpleDateFormat("EEE MMM d, h:mm:ss aa").format(new java.util.Date(boottime));
        String stime = Long.toString(boottime);
        rootVertex.addAnnotation("boottime_unix", stime);
        rootVertex.addAnnotation("boottime_simple", stime_readable);
        putVertex(rootVertex);
        localCache.put("0", rootVertex);

        String path = "/proc";
        String processDir;
        File folder = new File(path);
        File[] listOfFiles = folder.listFiles();

        // Build the process tree using the directories under /proc/. Directories
        // which have a numeric name represent processes.
        for (int i = 0; i < listOfFiles.length; i++) {
            if (listOfFiles[i].isDirectory()) {

                processDir = listOfFiles[i].getName();
                try {
                    Integer.parseInt(processDir);
                    Process tempVertex = new Process();
                    createProcessVertex(processDir, tempVertex);
                    String ppid = (String) tempVertex.getAnnotation("ppid");
                    putVertex(tempVertex);
                    localCache.put(tempVertex.getAnnotation("pid"), tempVertex);
                    if (Integer.parseInt(ppid) >= 0) {
                        WasTriggeredBy tempEdge = new WasTriggeredBy((Process) localCache.get(processDir), (Process) localCache.get(ppid));
                        putEdge(tempEdge);
                    }
                } catch (Exception exception) {
                    continue;
                }

            }
        }

        Runnable r1 = new Runnable() {

            public void run() {
                try {
                    // Launch FUSE from the native library.
                    launchFUSE(mountPoint);
                } catch (Exception exception) {
                    exception.printStackTrace(System.err);
                }
            }
        };
        new Thread(r1).start();

        return true;
    }

    public void checkProcessTree(String pid) {
        // Check the process tree to ensure that the given PID exists in it. If not,
        // then add it and recursively check its parents so that this process
        // eventually joins the main process tree.
        try {
            while (true) {
                if (localCache.get(pid) != null) {
                    return;
                }
                Process tempVertex = new Process();
                createProcessVertex(pid, tempVertex);
                String ppid = (String) tempVertex.getAnnotation("ppid");
                putVertex(tempVertex);
                localCache.put(tempVertex.getAnnotation("pid"), tempVertex);
                if (Integer.parseInt(ppid) >= 0) {
                    checkProcessTree(ppid);
                    WasTriggeredBy tempEdge = new WasTriggeredBy((Process) localCache.get(pid), (Process) localCache.get(ppid));
                    putEdge(tempEdge);
                } else {
                    return;
                }
            }
        } catch (Exception exception) {
        }
        return;
    }

    public void readwrite(int write, int pid, int iotime, String path) {
        // Create the file artifact and populate the annotations with file information.
        long now = System.currentTimeMillis();
        checkProcessTree(Integer.toString(pid));
        File file = new File(path);
        Artifact tempVertex = new Artifact();
        tempVertex.addAnnotation("filename", file.getName());
        tempVertex.addAnnotation("path", path);
        if (file.length() > 0) {
            tempVertex.addAnnotation("size", Long.toString(file.length()));
        }
        if (file.lastModified() > 0) {
            String lastmodified_readable = new java.text.SimpleDateFormat("EEE MMM d, h:mm:ss aa").format(new java.util.Date(file.lastModified()));
            tempVertex.addAnnotation("lastmodified_unix", Long.toString(file.lastModified()));
            tempVertex.addAnnotation("lastmodified_simple", lastmodified_readable);
        }
        putVertex(tempVertex);
        // Put the file artifact in a local cache to be used later when creating edges.
        localCache.put(path, tempVertex);
        AbstractEdge tempEdge = null;
        if (write == 0) {
            tempEdge = new Used((Process) localCache.get(Integer.toString(pid)), (Artifact) localCache.get(path));
        } else {
            tempEdge = new WasGeneratedBy((Artifact) localCache.get(path), (Process) localCache.get(Integer.toString(pid)));
        }
        if (iotime > 0) {
            tempEdge.addAnnotation("iotime", Integer.toString(iotime));
        }
        tempEdge.addAnnotation("endtime", Long.toString(now));
        putEdge(tempEdge);
        // If the given path represents a link, then perform the same operation on the
        // artifact to which the link points.
        if (links.containsKey(path)) {
            readwrite(write, pid, iotime, links.get(path));
        }
    }

    public void rename(int pid, int iotime, String pathfrom, String pathto) {
        long now = System.currentTimeMillis();
        checkProcessTree(Integer.toString(pid));
        WasDerivedFrom tempEdge = new WasDerivedFrom((Artifact) localCache.get(pathto), (Artifact) localCache.get(pathfrom));
        tempEdge.addAnnotation("iotime", Integer.toString(iotime));
        tempEdge.addAnnotation("endtime", Long.toString(now));
        putEdge(tempEdge);
        if (links.containsKey(pathfrom)) {
            // If the rename is on a link then update the link name.
            String linkedLocation = links.get(pathfrom);
            links.remove(pathfrom);
            links.put(pathto, linkedLocation);
        }
    }

    public void link(int pid, String pathfrom, String pathto) {
        checkProcessTree(Integer.toString(pid));
        WasDerivedFrom tempEdge = new WasDerivedFrom((Artifact) localCache.get(pathto), (Artifact) localCache.get(pathfrom));
        tempEdge.addAnnotation("operation", "link");
        putEdge(tempEdge);
        links.put(pathfrom, pathto);
    }

    public void unlink(int pid, String path) {
        checkProcessTree(Integer.toString(pid));
        links.remove(path);
    }

    @Override
    public boolean shutdown() {
        try {
            // Force dismount of FUSE and then remove the mount point directory.
            Runtime.getRuntime().exec("fusermount -u -z " + mountPoint).waitFor();
            Runtime.getRuntime().exec("rm -r " + mountPoint).waitFor();
            return true;
        } catch (Exception exception) {
            exception.printStackTrace(System.err);
            return false;
        }
    }
}
