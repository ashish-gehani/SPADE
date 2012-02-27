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
import java.net.InetAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import spade.core.AbstractReporter;
import spade.core.AbstractVertex;
import spade.edge.opm.*;
import spade.vertex.custom.File;
import spade.vertex.custom.Program;
import spade.vertex.opm.Agent;

/**
 * The MacFUSE reporter.
 *
 * @author dawood
 */
public class MacFUSE extends AbstractReporter {

    // Cache for storing vertices.
    private Map<String, AbstractVertex> localCache;
    // Map for resolving links.
    private Map<String, String> links;
    // The mount point is the argument given to this reporter on launch(). Can
    // be absolute or relative.
    private String mountPoint;
    // The mount path is the absolute path to the mount point.
    private String mountPath;
    // This is the PID of the JVM running SPADE and is used to ignore self-generated
    // provenance.
    private String myPID;
    // The local host address to be added to process vertices.
    private String localHostAddress;
    // The local host name to be added to process vertices.
    private String localHostName;
    // Set this boolean to true to refresh the hostAddress and hostName values
    // everytime a new process vertex is created.
    private boolean refreshHost = false;
    // Date pattern used to generate human-readable time-value annotations.
    private final String simpleDatePattern = "EEE MMM d H:mm:ss yyyy";

    // The native launchFUSE method to start FUSE. The argument is the
    // mount point.
    private native int launchFUSE(String argument);

    @Override
    public boolean launch(String arguments) {
        if (arguments == null) {
            return false;
        }

        try {
            localHostAddress = InetAddress.getLocalHost().getHostAddress();
            localHostName = InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            localHostAddress = null;
            localHostName = null;
            Logger.getLogger(LinuxFUSE.class.getName()).log(Level.WARNING, null, ex);
        }

        try {
            // The argument to this reporter is the mount point for FUSE.
            mountPoint = arguments;

            // Map for caching vertices
            localCache = Collections.synchronizedMap(new HashMap<String, AbstractVertex>());

            // Map for resolving links to paths
            links = Collections.synchronizedMap(new HashMap<String, String>());

            // Create a new directory as the mount point for FUSE.
            java.io.File mount = new java.io.File(mountPoint);
            if (mount.exists()) {
                return false;
            } else {
                int exitValue = Runtime.getRuntime().exec("mkdir " + mountPoint).waitFor();
                if (exitValue != 0) {
                    return false;
                }
            }

            mountPath = (new java.io.File(mountPoint)).getAbsolutePath();
            myPID = ManagementFactory.getRuntimeMXBean().getName().split("@")[0].trim();

            // Load the native library.
            System.loadLibrary("MacFUSE");

            // Create an initial root vertex which will be used as the root of the
            // process tree.
            Program rootVertex = new Program();
            rootVertex.addAnnotation("pidname", "System");
            rootVertex.addAnnotation("pid", "0");
            rootVertex.addAnnotation("ppid", "0");
            localCache.put("0", rootVertex);
            putVertex(rootVertex);

            // Recursively build the initial process tree using the ps utility.
            try {
                java.lang.Process pidinfo = Runtime.getRuntime().exec("ps -Aco pid,ppid,comm");
                BufferedReader pidreader = new BufferedReader(new InputStreamReader(pidinfo.getInputStream()));
                pidreader.readLine();
                while (true) {
                    String line = pidreader.readLine().trim();
                    String childinfo[] = line.split("\\s+", 3);
                    if (childinfo[1].equals(myPID)) {
                        // Break when we encounter the ps utility itself.
                        break;
                    }
                    checkProgramTree(childinfo[0]);
                }
            } catch (Exception exception) {
                Logger.getLogger(MacFUSE.class.getName()).log(Level.WARNING, null, exception);
            }

            Runnable FUSEThread = new Runnable() {

                public void run() {
                    try {
                        // Launch FUSE from the native library.
                        launchFUSE(mountPoint);
                    } catch (Exception exception) {
                        Logger.getLogger(MacFUSE.class.getName()).log(Level.SEVERE, null, exception);
                    }
                }
            };
            new Thread(FUSEThread, "MacFUSE-Thread").start();
        } catch (Exception exception) {
            Logger.getLogger(MacFUSE.class.getName()).log(Level.SEVERE, null, exception);
            return false;
        }

        return true;
    }

    private Program createProgramVertex(String pid) {
        // Create the process vertex and populate annotations with information
        // retrieved using the ps utility.
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
            if (info[1].equals(myPID)) {
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

            if (refreshHost) {
                try {
                    localHostAddress = InetAddress.getLocalHost().getHostAddress();
                    localHostName = InetAddress.getLocalHost().getHostName();
                } catch (Exception ex) {
                    localHostAddress = null;
                    localHostName = null;
                    Logger.getLogger(LinuxFUSE.class.getName()).log(Level.WARNING, null, ex);
                }
            }
            processVertex.addAnnotation("hostname", localHostName);
            processVertex.addAnnotation("hostaddress", localHostAddress);

            return processVertex;
        } catch (Exception exception) {
            // Logger.getLogger(MacFUSE.class.getName()).log(Level.SEVERE, null, exception);
            return null;
        }
    }

    private void checkProgramTree(String pid) {
        // Check the process tree to ensure that the given PID exists in it. If,
        // not, add it and recursively check its parents so that this process
        // eventually joins the main process tree.
        try {
            if (localCache.containsKey(pid)) {
                return;
            }
            Program thisProcess = createProgramVertex(pid);
            if (thisProcess == null) {
                return;
            }
            Agent thisAgent = new Agent();
            thisAgent.addAnnotation("user", thisProcess.removeAnnotation("user"));
            thisAgent.addAnnotation("uid", thisProcess.removeAnnotation("uid"));
            putVertex(thisProcess);
            putVertex(thisAgent);
            putEdge(new WasControlledBy(thisProcess, thisAgent));
            localCache.put(pid, thisProcess);
            String ppid = thisProcess.getAnnotation("ppid");
            if (Integer.parseInt(ppid) >= 0) {
                checkProgramTree(ppid);
                WasTriggeredBy tempEdge = new WasTriggeredBy((Program) localCache.get(pid), (Program) localCache.get(ppid));
                putEdge(tempEdge);
            }
        } catch (Exception exception) {
            Logger.getLogger(MacFUSE.class.getName()).log(Level.WARNING, null, exception);
        }
    }

    /**
     * Read event triggered by FUSE.
     *
     * @param pid PID of the triggering process.
     * @param iotime IO time of the operation.
     * @param path Path indicating target file.
     * @param link An integer used to indicate whether the target was a link or
     * not.
     */
    public void read(int pid, int iotime, String path, int link) {
        checkProgramTree(Integer.toString(pid));
        String newPath = sanitizePath(path);
        long now = System.currentTimeMillis();
        // Create file artifact depending on whether this is a link or not.
        // Link artifacts are created differently to avoid recursion that may
        // cause FUSE to crash.
        File fileVertex = (link == 1) ? createLinkVertex(newPath) : createFileVertex(newPath);
        putVertex(fileVertex);
        Used edge = new Used((Program) localCache.get(Integer.toString(pid)), fileVertex);
        edge.addAnnotation("iotime", Integer.toString(iotime));
        edge.addAnnotation("endtime", Long.toString(now));
        putEdge(edge);
        // If the given path represents a link, then perform the same operation on the
        // artifact to which the link points.
        if (link == 1 && links.containsKey(newPath)) {
            read(pid, iotime, links.get(newPath), 0);
        }
    }

    /**
     * Write event triggered by FUSE.
     *
     * @param pid PID of the triggering process.
     * @param iotime IO time of the operation.
     * @param path Path indicating target file.
     * @param link An integer used to indicate whether the target was a link or
     * not.
     */
    public void write(int pid, int iotime, String path, int link) {
        checkProgramTree(Integer.toString(pid));
        path = sanitizePath(path);
        long now = System.currentTimeMillis();
        // Create file artifact depending on whether this is a link or not.
        // Link artifacts are created differently to avoid recursion that may
        // cause FUSE to crash.
        File fileVertex = (link == 1) ? createLinkVertex(path) : createFileVertex(path);
        putVertex(fileVertex);
        WasGeneratedBy edge = new WasGeneratedBy(fileVertex, (Program) localCache.get(Integer.toString(pid)));
        edge.addAnnotation("iotime", Integer.toString(iotime));
        edge.addAnnotation("endtime", Long.toString(now));
        putEdge(edge);
        // If the given path represents a link, then perform the same operation on the
        // artifact to which the link points.
        if (link == 1 && links.containsKey(path)) {
            write(pid, iotime, links.get(path), 0);
        }
    }

    /**
     * ReadLink event triggered by FUSE.
     *
     * @param pid PID of the triggering process.
     * @param iotime IO time of the operation.
     * @param path Path indicating target file.
     */
    public void readlink(int pid, int iotime, String path) {
        checkProgramTree(Integer.toString(pid));
        path = sanitizePath(path);
        // Create the file artifact and populate the annotations with file information.
        long now = System.currentTimeMillis();
        File linkFile = createLinkVertex(path);
        putVertex(linkFile);
        Used edge = new Used((Program) localCache.get(Integer.toString(pid)), linkFile);
        edge.addAnnotation("endtime", Long.toString(now));
        edge.addAnnotation("operation", "readlink");
        putEdge(edge);
        // If the given path represents a link, then perform the same operation on the
        // artifact to which the link points.
        if (links.containsKey(path)) {
            read(pid, iotime, links.get(path), 0);
        }
    }

    /**
     * Rename event triggered by FUSE.
     *
     * @param pid PID of the triggering process.
     * @param iotime IO time of the operation.
     * @param pathfrom The source path.
     * @param pathto The destination path.
     * @param link An integer used to indicate whether the target was a link or
     * not.
     * @param done An integer used to indicate whether this event was triggered
     * before or after the rename operation.
     */
    public void rename(int pid, int iotime, String pathfrom, String pathto, int link, int done) {
        checkProgramTree(Integer.toString(pid));
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
            File fileVertex = (link == 1) ? createLinkVertex(pathfrom) : createFileVertex(pathfrom);
            putVertex(fileVertex);
            // Put the file artifact in the localCache to be removed on post-rename.
            localCache.put(pathfrom, fileVertex);
            Used edge = new Used((Program) localCache.get(Integer.toString(pid)), fileVertex);
            edge.addAnnotation("endtime", Long.toString(now));
            putEdge(edge);
        } else {
            // Create file artifact depending on whether this is a link or not.
            // Link artifacts are created differently to avoid recursion that may
            // cause FUSE to crash.
            File fileVertex = (link == 1 ? createLinkVertex(pathto) : createFileVertex(pathto));
            putVertex(fileVertex);
            WasGeneratedBy writeEdge = new WasGeneratedBy(fileVertex, (Program) localCache.get(Integer.toString(pid)));
            writeEdge.addAnnotation("endtime", Long.toString(now));
            putEdge(writeEdge);
            WasDerivedFrom renameEdge = new WasDerivedFrom(fileVertex, (File) localCache.remove(pathfrom));
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

    /**
     * Link event triggered by FUSE.
     *
     * @param pid PID of the triggering process.
     * @param originalFilePath The original file path.
     * @param linkPath Path to link to.
     */
    public void link(int pid, String originalFilePath, String linkPath) {
        checkProgramTree(Integer.toString(pid));
        originalFilePath = sanitizePath(originalFilePath);
        linkPath = sanitizePath(linkPath);
        long now = System.currentTimeMillis();
        File link = createLinkVertex(linkPath);
        putVertex(link);
        File original = createFileVertex(originalFilePath);
        putVertex(original);
        WasDerivedFrom linkEdge = new WasDerivedFrom(original, link);
        linkEdge.addAnnotation("operation", "link");
        linkEdge.addAnnotation("endtime", Long.toString(now));
        putEdge(linkEdge);
        // Add the link to the links map.
        links.put(linkPath, originalFilePath);
    }

    /**
     * Unlink event triggered by FUSE.
     *
     * @param pid PID of the triggering process.
     * @param path The path to unlink.
     */
    public void unlink(int pid, String path) {
        checkProgramTree(Integer.toString(pid));
        path = sanitizePath(path);
        links.remove(path);
    }

    private File createFileVertex(String path) {
        File fileVertex = new File();
        java.io.File file = new java.io.File(path);
        fileVertex.addAnnotation("filename", file.getName());
        fileVertex.addAnnotation("path", path);
        long filesize = file.length();
        long lastmodified = file.lastModified();
        fileVertex.addAnnotation("size", Long.toString(filesize));
        String lastmodified_readable = new java.text.SimpleDateFormat(simpleDatePattern).format(new java.util.Date(lastmodified));
        fileVertex.addAnnotation("lastmodified_unix", Long.toString(lastmodified));
        fileVertex.addAnnotation("lastmodified_simple", lastmodified_readable);
        return fileVertex;
    }

    private File createLinkVertex(String path) {
        File linkVertex = new File();
        linkVertex.addAnnotation("path", path);
        linkVertex.addAnnotation("filetype", "link");
        return linkVertex;
    }

    private String sanitizePath(String path) {
        // Sanitize path to by removing the FUSE mount path from the beginning so
        // as to avoid entering infinite recursion.
        if (path.startsWith(mountPath)) {
            return path.substring(mountPath.length());
        }
        return path;
    }

    @Override
    public boolean shutdown() {
        try {
            // Force dismount of FUSE and then remove the mount point directory.
            Runtime.getRuntime().exec("umount -f " + mountPoint).waitFor();
            Runtime.getRuntime().exec("rm -r " + mountPoint).waitFor();
            return true;
        } catch (Exception exception) {
            Logger.getLogger(MacFUSE.class.getName()).log(Level.SEVERE, null, exception);
            return false;
        }
    }
}
