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
import java.util.logging.Level;
import java.util.logging.Logger;
import spade.core.AbstractEdge;

/** The LinuxFUSE reporter.
 * 
 * @author Dawood
 */
public class LinuxFUSE extends AbstractReporter {

    private long boottime;
    private Map<String, AbstractVertex> localCache;
    private Map<String, String> links;
    private String mountPoint;
    private String mountPath;
    private final String simpleDatePattern = "EEE MMM d H:mm:ss yyyy";

    // The native launchFUSE method to start FUSE. The argument is the
    // mount point.
    private native int launchFUSE(String argument);

    @Override
    public boolean launch(String arguments) {
        if (arguments == null) {
            return false;
        }
        // The argument to this reporter is the mount point for FUSE.
        mountPoint = arguments;
        localCache = Collections.synchronizedMap(new HashMap<String, AbstractVertex>());
        links = Collections.synchronizedMap(new HashMap<String, String>());

        try {
            // Load the native library.
            System.loadLibrary("LinuxFUSE");
        } catch (Exception exception) {
            Logger.getLogger(LinuxFUSE.class.getName()).log(Level.SEVERE, null, exception);
            return false;
        }

        // Create a new directory as the mount point for FUSE.
        if ((new File(mountPoint)).exists()) {
            return false;
        } else {
            try {
                int exitValue = Runtime.getRuntime().exec("mkdir " + mountPoint).waitFor();
                if (exitValue != 0) {
                    throw new Exception();
                }
            } catch (Exception exception) {
                Logger.getLogger(LinuxFUSE.class.getName()).log(Level.SEVERE, null, exception);
                return false;
            }
        }

        mountPath = (new File(mountPoint)).getAbsolutePath();

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
            Logger.getLogger(LinuxFUSE.class.getName()).log(Level.SEVERE, null, exception);
        }

        // Create an initial root vertex which will be used as the root of the
        // process tree.
        Process rootVertex = new Process();
        rootVertex.addAnnotation("pidname", "System");
        rootVertex.addAnnotation("pid", "0");
        rootVertex.addAnnotation("ppid", "0");
        String stime_readable = new java.text.SimpleDateFormat(simpleDatePattern).format(new java.util.Date(boottime));
        String stime = Long.toString(boottime);
        rootVertex.addAnnotation("boottime_unix", stime);
        rootVertex.addAnnotation("boottime_simple", stime_readable);
        localCache.put("0", rootVertex);
        putVertex(rootVertex);

        String path = "/proc";
        String currentProcess;
        File folder = new File(path);
        File[] listOfFiles = folder.listFiles();

        // Build the process tree using the directories under /proc/. Directories
        // which have a numeric name represent processes.
        for (int i = 0; i < listOfFiles.length; i++) {
            if (listOfFiles[i].isDirectory()) {

                currentProcess = listOfFiles[i].getName();
                try {
                    Integer.parseInt(currentProcess);
                    Process processVertex = createProcessVertex(currentProcess);
                    String ppid = (String) processVertex.getAnnotation("ppid");
                    localCache.put(currentProcess, processVertex);
                    putVertex(processVertex);
                    if (Integer.parseInt(ppid) >= 0) {
                        if (((Process) localCache.get(ppid) != null) && (processVertex != null)) {
                            WasTriggeredBy triggerEdge = new WasTriggeredBy(processVertex, (Process) localCache.get(ppid));
                            putEdge(triggerEdge);
                        }
                    }
                } catch (Exception exception) {
                    continue;
                }
            }
        }

        Runnable FUSEThread = new Runnable() {

            public void run() {
                try {
                    // Launch FUSE from the native library.
                    launchFUSE(mountPoint);
                } catch (Exception exception) {
                    Logger.getLogger(LinuxFUSE.class.getName()).log(Level.SEVERE, null, exception);
                }
            }
        };
        new Thread(FUSEThread, "FUSEThread").start();

        return true;
    }

    private Process createProcessVertex(String pid) {
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
            String stime_readable = new java.text.SimpleDateFormat(simpleDatePattern).format(new java.util.Date(starttime));
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
            resultVertex.addAnnotation("group", stats[4]);
            resultVertex.addAnnotation("sessionid", stats[5]);
            resultVertex.addAnnotation("commandline", cmdline);
        } catch (Exception exception) {
            Logger.getLogger(LinuxFUSE.class.getName()).log(Level.SEVERE, null, exception);
            return null;
        }

        try {
            BufferedReader environReader = new BufferedReader(new FileReader("/proc/" + pid + "/environ"));
            String environ = environReader.readLine();
            environReader.close();
            if (environ != null) {
                environ = environ.replace("\0", ", ");
                environ = environ.replace("\"", "'");
                resultVertex.addAnnotation("environment", environ);
            }
        } catch (Exception exception) {
        }
        return resultVertex;
    }

    private void checkProcessTree(String pid) {
        // Check the process tree to ensure that the given PID exists in it. If not,
        // then add it and recursively check its parents so that this process
        // eventually joins the main process tree.
        try {
            if (localCache.containsKey(pid)) {
                return;
            }
            Process processVertex = createProcessVertex(pid);
            String ppid = (String) processVertex.getAnnotation("ppid");
            localCache.put(pid, processVertex);
            putVertex((Process) localCache.get(pid));
            if (Integer.parseInt(ppid) >= 0) {
                checkProcessTree(ppid);
                WasTriggeredBy triggerEdge = new WasTriggeredBy((Process) localCache.get(pid), (Process) localCache.get(ppid));
                putEdge(triggerEdge);
            } else {
                return;
            }
        } catch (Exception exception) {
            Logger.getLogger(LinuxFUSE.class.getName()).log(Level.SEVERE, null, exception);
        }
    }

    /** Read event triggered by FUSE.
     * 
     * @param pid PID of the triggering process.
     * @param iotime IO time of the operation.
     * @param path Path indicating target file.
     * @param link An integer used to indicate whether the target was a link or not.
     */
    public void read(int pid, int iotime, String path, int link) {
        checkProcessTree(Integer.toString(pid));
        path = sanitizePath(path);
        long now = System.currentTimeMillis();
        // Create file artifact depending on whether this is a link or not.
        // Link artifacts are created differently to avoid recursion that may
        // cause FUSE to crash.
        Artifact fileArtifact = (link == 1) ? createLinkArtifact(path) : createFileArtifact(path);
        putVertex(fileArtifact);
        AbstractEdge edge = new Used((Process) localCache.get(Integer.toString(pid)), fileArtifact);
        edge.addAnnotation("iotime", Integer.toString(iotime));
        edge.addAnnotation("endtime", Long.toString(now));
        putEdge(edge);
        // If the given path represents a link, then perform the same operation on the
        // artifact to which the link points.
        if (link == 1 && links.containsKey(path)) {
            read(pid, iotime, links.get(path), 0);
        }
    }

    /** Write event triggered by FUSE.
     * 
     * @param pid PID of the triggering process.
     * @param iotime IO time of the operation.
     * @param path Path indicating target file.
     * @param link An integer used to indicate whether the target was a link or not.
     */
    public void write(int pid, int iotime, String path, int link) {
        checkProcessTree(Integer.toString(pid));
        path = sanitizePath(path);
        long now = System.currentTimeMillis();
        // Create file artifact depending on whether this is a link or not.
        // Link artifacts are created differently to avoid recursion that may
        // cause FUSE to crash.
        Artifact fileArtifact = (link == 1) ? createLinkArtifact(path) : createFileArtifact(path);
        putVertex(fileArtifact);
        AbstractEdge edge = new WasGeneratedBy(fileArtifact, (Process) localCache.get(Integer.toString(pid)));
        edge.addAnnotation("iotime", Integer.toString(iotime));
        edge.addAnnotation("endtime", Long.toString(now));
        putEdge(edge);
        // If the given path represents a link, then perform the same operation on the
        // artifact to which the link points.
        if (link == 1 && links.containsKey(path)) {
            write(pid, iotime, links.get(path), 0);
        }
    }

    /** ReadLink event triggered by FUSE.
     * 
     * @param pid PID of the triggering process.
     * @param iotime IO time of the operation.
     * @param path Path indicating target file.
     */
    public void readlink(int pid, int iotime, String path) {
        checkProcessTree(Integer.toString(pid));
        path = sanitizePath(path);
        // Create the file artifact and populate the annotations with file information.
        long now = System.currentTimeMillis();
        Artifact linkArtifact = createLinkArtifact(path);
        putVertex(linkArtifact);
        Used edge = new Used((Process) localCache.get(Integer.toString(pid)), linkArtifact);
        edge.addAnnotation("endtime", Long.toString(now));
        edge.addAnnotation("operation", "readlink");
        putEdge(edge);
        // If the given path represents a link, then perform the same operation on the
        // artifact to which the link points.
        if (links.containsKey(path)) {
            read(pid, iotime, links.get(path), 0);
        }
    }

    /** Rename event triggered by FUSE.
     * 
     * @param pid PID of the triggering process.
     * @param iotime IO time of the operation.
     * @param pathfrom The source path.
     * @param pathto The destination path.
     * @param link An integer used to indicate whether the target was a link or not.
     * @param done An intiger used to indicate whether this event was triggered before
     * or after the rename operation.
     */
    public void rename(int pid, int iotime, String pathfrom, String pathto, int link, int done) {
        checkProcessTree(Integer.toString(pid));
        pathfrom = sanitizePath(pathfrom);
        pathto = sanitizePath(pathto);
        long now = System.currentTimeMillis();
        // 'done' is used to indicate whether this is a pre-rename or a post-rename
        // call. In pre-rename, a Used edge is created from the process to the old
        // file. In post-rename, a WasGeneratedBy edge is created from the process
        // to the new file and a WasDerivedEdge created between the two file
        // artifacts.
        if (done == 0) {
            // Create file artifact depending on whether this is a link or not.
            // Link artifacts are created differently to avoid recursion that may
            // cause FUSE to crash.
            Artifact fileArtifact = (link == 1) ? createLinkArtifact(pathfrom) : createFileArtifact(pathfrom);
            putVertex(fileArtifact);
            // Put the file artifact in the localCache to be removed on post-rename.
            localCache.put(pathfrom, fileArtifact);
            Used edge = new Used((Process) localCache.get(Integer.toString(pid)), fileArtifact);
            edge.addAnnotation("endtime", Long.toString(now));
            putEdge(edge);
        } else {
            // Create file artifact depending on whether this is a link or not.
            // Link artifacts are created differently to avoid recursion that may
            // cause FUSE to crash.
            Artifact fileArtifact = (link == 1 ? createLinkArtifact(pathto) : createFileArtifact(pathto));
            putVertex(fileArtifact);
            WasGeneratedBy writeEdge = new WasGeneratedBy(fileArtifact, (Process) localCache.get(Integer.toString(pid)));
            writeEdge.addAnnotation("endtime", Long.toString(now));
            putEdge(writeEdge);
            WasDerivedFrom renameEdge = new WasDerivedFrom(fileArtifact, (Artifact) localCache.remove(pathfrom));
            renameEdge.addAnnotation("iotime", Integer.toString(iotime));
            renameEdge.addAnnotation("endtime", Long.toString(now));
            renameEdge.addAnnotation("operation", "rename");
            putEdge(renameEdge);
            if (links.containsKey(pathfrom)) {
                // If the rename is on a link then update the link name.
                String linkedLocation = links.get(pathfrom);
                links.remove(pathfrom);
                links.put(pathto, linkedLocation);
            }
        }
    }

    /** Link event triggered by FUSE.
     * 
     * @param pid PID of the triggering process.
     * @param originalFilePath The original file path.
     * @param linkPath Path to link to.
     */
    public void link(int pid, String originalFilePath, String linkPath) {
        checkProcessTree(Integer.toString(pid));
        originalFilePath = sanitizePath(originalFilePath);
        linkPath = sanitizePath(linkPath);
        long now = System.currentTimeMillis();
        Artifact link = createLinkArtifact(linkPath);
        putVertex(link);
        Artifact original = createFileArtifact(originalFilePath);
        putVertex(original);
        WasDerivedFrom linkEdge = new WasDerivedFrom(original, link);
        linkEdge.addAnnotation("operation", "link");
        linkEdge.addAnnotation("endtime", Long.toString(now));
        putEdge(linkEdge);
        // Add the link to the links map.
        links.put(linkPath, originalFilePath);
    }

    /** Unlink event triggered by FUSE.
     * 
     * @param pid PID of the triggering process.
     * @param path The path to unlink.
     */
    public void unlink(int pid, String path) {
        checkProcessTree(Integer.toString(pid));
        path = sanitizePath(path);
        links.remove(path);
    }

    private Artifact createFileArtifact(String path) {
        Artifact fileArtifact = new Artifact();
        File file = new File(path);
        fileArtifact.addAnnotation("filename", file.getName());
        fileArtifact.addAnnotation("path", path);
        long filesize = file.length();
        long lastmodified = file.lastModified();
        fileArtifact.addAnnotation("size", Long.toString(filesize));
        String lastmodified_readable = new java.text.SimpleDateFormat(simpleDatePattern).format(new java.util.Date(lastmodified));
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

    private String sanitizePath(String path) {
        // Sanitize path to avoid recursion inside FUSE which can cause the
        // reporter to crash.
        if (path.startsWith(mountPath)) {
            path = path.substring(mountPath.length());
        }
        return path;
    }

    @Override
    public boolean shutdown() {
        try {
            // Force dismount of FUSE and then remove the mount point directory.
            Runtime.getRuntime().exec("fusermount -u -z " + mountPoint).waitFor();
            Runtime.getRuntime().exec("rm -r " + mountPoint).waitFor();
            return true;
        } catch (Exception exception) {
            Logger.getLogger(LinuxFUSE.class.getName()).log(Level.SEVERE, null, exception);
            return false;
        }
    }
}
