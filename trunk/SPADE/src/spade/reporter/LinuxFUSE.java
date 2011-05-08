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
    private String mountPath;

    // The native launchFUSE method to start FUSE. The argument is the
    // mount point.
    public native int launchFUSE(String argument);

    @Override
    public boolean launch(String arguments) {
        // The argument to this reporter is the mount point for FUSE.
        mountPoint = arguments;
        localCache = Collections.synchronizedMap(new HashMap<String, AbstractVertex>());
        links = Collections.synchronizedMap(new HashMap<String, String>());

        // Create a new directory as the mount point for FUSE.
        File mount1 = new File(mountPoint);
        if (mount1.exists()) {
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

        File mount2 = new File(mountPoint);
        mountPath = mount2.getAbsolutePath();

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
        localCache.put("0", rootVertex);
        putVertex(getProcess(0));

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
                    Process tempVertex = createProcessVertex(processDir);
                    String ppid = (String) tempVertex.getAnnotation("ppid");
                    localCache.put(tempVertex.getAnnotation("pid"), tempVertex);
                    putVertex(getProcess(Integer.parseInt(tempVertex.getAnnotation("pid"))));
                    if (Integer.parseInt(ppid) >= 0) {
                        WasTriggeredBy tempEdge = new WasTriggeredBy(getProcess(Integer.parseInt(processDir)), getProcess(Integer.parseInt(ppid)));
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

    public Process createProcessVertex(String pid) {
        // The process vertex is created using the proc filesystem.
        Process resultVertex = new Process();
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

            resultVertex.addAnnotation("pidname", name);
            resultVertex.addAnnotation("pid", pid);
            resultVertex.addAnnotation("ppid", ppid);
            resultVertex.addAnnotation("uid", uid);
            resultVertex.addAnnotation("gid", gid);
            resultVertex.addAnnotation("starttime_unix", stime);
            resultVertex.addAnnotation("starttime_simple", stime_readable);
            // resultVertex.addAnnotation("group", stats[4]);
            // resultVertex.addAnnotation("sessionid", stats[5]);
            resultVertex.addAnnotation("commandline", cmdline);
        } catch (Exception exception) {
            exception.printStackTrace(System.err);
            return null;
        }

        try {
            BufferedReader environReader = new BufferedReader(new FileReader("/proc/" + pid + "/environ"));
            String environ = environReader.readLine();
            environReader.close();
            if (environ != null) {
                environ = environ.replace("\0", ", ");
                environ = environ.replace("\"", "'");
                // resultVertex.addAnnotation("environment", environ);
            }
        } catch (Exception exception) {
        }
        return resultVertex;
    }

    public void checkProcessTree(String pid) {
        // Check the process tree to ensure that the given PID exists in it. If not,
        // then add it and recursively check its parents so that this process
        // eventually joins the main process tree.
        try {
            if (localCache.get(pid) != null) {
                return;
            }
            Process tempVertex = createProcessVertex(pid);
            String ppid = (String) tempVertex.getAnnotation("ppid");
            localCache.put(tempVertex.getAnnotation("pid"), tempVertex);
            putVertex(getProcess(Integer.parseInt(tempVertex.getAnnotation("pid"))));
            if (Integer.parseInt(ppid) >= 0) {
                checkProcessTree(ppid);
                WasTriggeredBy tempEdge = new WasTriggeredBy(getProcess(Integer.parseInt(pid)), getProcess(Integer.parseInt(ppid)));
                putEdge(tempEdge);
            } else {
                return;
            }
        } catch (Exception exception) {
            exception.printStackTrace(System.err);
        }
    }

    public void read(int pid, int iotime, String path, int link) {
        if (path.startsWith(mountPath)) path = path.substring(mountPath.length());
        try {
            // Create the file artifact and populate the annotations with file information.
            long now = System.currentTimeMillis();
            checkProcessTree(Integer.toString(pid));
            Artifact fileArtifact;
            if (link == 1) {
                fileArtifact = createLinkArtifact(path);
            } else {
                fileArtifact = createFileArtifact(path);
            }
            putVertex(fileArtifact);
            Used edge = new Used(getProcess(pid), fileArtifact);
            if (iotime > 0) {
                edge.addAnnotation("iotime", Integer.toString(iotime));
            }
            edge.addAnnotation("endtime", Long.toString(now));
            putEdge(edge);
            if (link == 1 && links.get(path) != null) {
                read(pid, iotime, links.get(path), 0);
            }
        } catch (Exception exception) {
            exception.printStackTrace(System.err);
        }
    }

    public void write(int pid, int iotime, String path, int link) {
        if (path.startsWith(mountPath)) path = path.substring(mountPath.length());
        try {
            // Create the file artifact and populate the annotations with file information.
            long now = System.currentTimeMillis();
            checkProcessTree(Integer.toString(pid));
            Artifact fileArtifact;
            if (link == 1) {
                fileArtifact = createLinkArtifact(path);
            } else {
                fileArtifact = createFileArtifact(path);
            }
            putVertex(fileArtifact);
            WasGeneratedBy edge = new WasGeneratedBy(fileArtifact, getProcess(pid));
            if (iotime > 0) {
                edge.addAnnotation("iotime", Integer.toString(iotime));
            }
            edge.addAnnotation("endtime", Long.toString(now));
            putEdge(edge);
            if (link == 1 && links.get(path) != null) {
                write(pid, iotime, links.get(path), 0);
            }
        } catch (Exception exception) {
            exception.printStackTrace(System.err);
        }
    }

    public void readlink(int pid, int iotime, String path) {
        if (path.startsWith(mountPath)) path = path.substring(mountPath.length());
        try {
            // Create the file artifact and populate the annotations with file information.
            long now = System.currentTimeMillis();
            checkProcessTree(Integer.toString(pid));
            Artifact tempVertex = createLinkArtifact(path);
            Used edge = new Used(getProcess(pid), tempVertex);
            edge.addAnnotation("endtime", Long.toString(now));
            edge.addAnnotation("operation", "readlink");
            putEdge(edge);
            // If the given path represents a link, then perform the same operation on the
            // artifact to which the link points.
            if (links.containsKey(path)) {
                read(pid, iotime, links.get(path), 0);
            }
        } catch (Exception exception) {
            exception.printStackTrace(System.err);
        }
    }

    public void rename(int pid, int iotime, String pathfrom, String pathto, int link, int done) {
        if (pathfrom.startsWith(mountPath)) pathfrom = pathfrom.substring(mountPath.length());
        if (pathto.startsWith(mountPath)) pathto = pathto.substring(mountPath.length());
        try {
            long now = System.currentTimeMillis();
            checkProcessTree(Integer.toString(pid));
            Artifact fileArtifact;
            if (done == 0) {
                if (link == 1) {
                    fileArtifact = createLinkArtifact(pathfrom);
                } else {
                    fileArtifact = createFileArtifact(pathfrom);
                }
                putVertex(fileArtifact);
                localCache.put(pathfrom, fileArtifact);
                Used edge = new Used(getProcess(pid), fileArtifact);
                edge.addAnnotation("endtime", Long.toString(now));
                putEdge(edge);
            } else {
                if (link == 1) {
                    fileArtifact = createLinkArtifact(pathto);
                } else {
                    fileArtifact = createFileArtifact(pathto);
                }
                putVertex(fileArtifact);
                WasGeneratedBy writeEdge = new WasGeneratedBy(fileArtifact, getProcess(pid));
                writeEdge.addAnnotation("endtime", Long.toString(now));
                putEdge(writeEdge);
                WasDerivedFrom renameEdge = new WasDerivedFrom(fileArtifact, (Artifact) localCache.get(pathfrom));
                renameEdge.addAnnotation("iotime", Integer.toString(iotime));
                renameEdge.addAnnotation("endtime", Long.toString(now));
                renameEdge.addAnnotation("operation", "rename");
                putEdge(renameEdge);
                localCache.remove(pathfrom);
                if (links.containsKey(pathfrom)) {
                    // If the rename is on a link then update the link name.
                    String linkedLocation = links.get(pathfrom);
                    links.remove(pathfrom);
                    links.put(pathto, linkedLocation);
                }
            }
        } catch (Exception exception) {
            exception.printStackTrace(System.err);
        }
    }

    public void link(int pid, String originalFile, String pathtoLink) {
        if (originalFile.startsWith(mountPath)) originalFile = originalFile.substring(mountPath.length());
        if (pathtoLink.startsWith(mountPath)) pathtoLink = pathtoLink.substring(mountPath.length());
        try {
            checkProcessTree(Integer.toString(pid));
            Artifact link = createLinkArtifact(pathtoLink);
            Artifact original = createFileArtifact(originalFile);
            putVertex(link);
            putVertex(original);
            WasDerivedFrom tempEdge = new WasDerivedFrom(original, link);
            tempEdge.addAnnotation("operation", "link");
            putEdge(tempEdge);
            // The links map is used to maintain links for artifacts or files that may have
            // been linked before provenance started in order to ensure completeness of the
            // provenance graph.
            links.put(pathtoLink, originalFile);
        } catch (Exception exception) {
            exception.printStackTrace(System.err);
        }
    }

    public void unlink(int pid, String path) {
        if (path.startsWith(mountPath)) path = path.substring(mountPath.length());
        try {
            checkProcessTree(Integer.toString(pid));
            links.remove(path);
        } catch (Exception exception) {
            exception.printStackTrace(System.err);
        }
    }

    private Artifact createFileArtifact(String path) {
        Artifact fileArtifact = new Artifact();
        File file = new File(path);
        fileArtifact.addAnnotation("filename", file.getName());
        fileArtifact.addAnnotation("path", path);
        long filesize = file.length();
        long lastmodified = file.lastModified();
        fileArtifact.addAnnotation("size", Long.toString(filesize));
        String lastmodified_readable = new java.text.SimpleDateFormat("EEE MMM d, h:mm:ss aa").format(new java.util.Date(lastmodified));
        fileArtifact.addAnnotation("lastmodified_unix", Long.toString(lastmodified));
        fileArtifact.addAnnotation("lastmodified_simple", lastmodified_readable);
        return fileArtifact;
    }

    private Artifact createLinkArtifact(String path) {
        Artifact fileArtifact = new Artifact();
        fileArtifact.addAnnotation("path", path);
        fileArtifact.addAnnotation("filetype", "link");
        return fileArtifact;
    }

    private Process getProcess(int pid) {
        Process process = new Process();
        Process tempProcess = (Process) localCache.get(Integer.toString(pid));
        process.getAnnotations().putAll(tempProcess.getAnnotations());
        return process;
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
