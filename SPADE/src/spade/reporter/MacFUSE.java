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
import spade.core.AbstractVertex;
import spade.core.AbstractEdge;
import spade.edge.opm.WasTriggeredBy;
import spade.edge.opm.WasGeneratedBy;
import spade.edge.opm.Used;
import spade.edge.opm.WasDerivedFrom;
import spade.edge.opm.WasControlledBy;
import spade.vertex.opm.Agent;
import spade.vertex.custom.File;
import spade.vertex.custom.Program;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The MacFUSE reporter.
 * 
 * @author dawood
 */
public class MacFUSE extends AbstractReporter {

    private HashMap<String, AbstractVertex> localCache;
    private HashMap<String, String> links;
    private String mountPoint;
    private String mountPath;
    private final String simpleDatePattern = "EEE MMM d H:mm:ss yyyy";

    private native int launchFUSE(String argument);

    @Override
    public boolean launch(String arguments) {
        // The argument to this reporter is the mount point for FUSE.
        mountPoint = arguments;
        localCache = new HashMap<String, AbstractVertex>();
        links = new HashMap<String, String>();

        // Create a new directory as the mount point for FUSE.
        java.io.File mount = new java.io.File(mountPoint);
        if (mount.exists()) {
            return false;
        } else {
            try {
                int exitValue = Runtime.getRuntime().exec("mkdir " + mountPoint).waitFor();
                if (exitValue != 0) {
                    throw new Exception();
                }
            } catch (Exception exception) {
                Logger.getLogger(MacFUSE.class.getName()).log(Level.SEVERE, null, exception);
                return false;
            }
        }

        mountPath = (new java.io.File(mountPoint)).getAbsolutePath();

        // Load the native library.
        System.loadLibrary("MacFUSE");
        buildProgramTree();

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
        new Thread(FUSEThread).start();

        return true;
    }

    private Program createProgramVertex(String pid) {
        // Create the process vertex and populate annotations with information
        // retrieved using the ps utility.
        Program processVertex = new Program();
        try {
            String line = "";
            java.lang.Process pidinfo = Runtime.getRuntime().exec("ps -p " + pid + " -co pid,ppid,uid,user,gid,lstart,sess,comm");
            BufferedReader pidreader = new BufferedReader(new InputStreamReader(pidinfo.getInputStream()));
            line = pidreader.readLine();
            line = pidreader.readLine();
            processVertex = new Program();
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
            processVertex.addAnnotation("sessionid", info[10]);
            processVertex.addAnnotation("uid", info[2]);
            processVertex.addAnnotation("user", info[3]);
            processVertex.addAnnotation("gid", info[4]);
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
            // Logger.getLogger(MacFUSE.class.getName()).log(Level.SEVERE, null, exception);
            return null;
        }
        return processVertex;
    }

    private void buildProgramTree() {
        // Recursively build the process tree using the ps utility.
        try {
            String line = "";
            java.lang.Process pidinfo = Runtime.getRuntime().exec("ps -Aco pid,ppid,comm");
            BufferedReader pidreader = new BufferedReader(new InputStreamReader(pidinfo.getInputStream()));
            line = pidreader.readLine();
            while (true) {
                line = pidreader.readLine().trim();
                String childinfo[] = line.split("\\s+", 3);
                if (childinfo[2].equals("ps")) {
                    // Break when we encounter the ps utility itself.
                    break;
                }
                checkProgramTree(childinfo[0]);
            }
        } catch (Exception exception) {
            // Logger.getLogger(MacFUSE.class.getName()).log(Level.SEVERE, null, exception);
        }
    }

    private void checkProgramTree(String pid) {
        // Check the process tree to ensure that the given PID exists in it. If not,
        // then add it and recursively check its parents so that this process
        // eventually joins the main process tree.
        while (true) {
            if (localCache.containsKey(pid)) {
                return;
            }
            Program tempProgram = createProgramVertex(pid);
            Agent tempAgent = new Agent();
            tempAgent.addAnnotation("user", tempProgram.removeAnnotation("user"));
            tempAgent.addAnnotation("uid", tempProgram.removeAnnotation("uid"));
            tempAgent.addAnnotation("gid", tempProgram.removeAnnotation("gid"));
            putVertex(tempProgram);
            putVertex(tempAgent);
            putEdge(new WasControlledBy(tempProgram, tempAgent));
            localCache.put(pid, tempProgram);
            String ppid = tempProgram.getAnnotation("ppid");
            if (Integer.parseInt(ppid) > 0) {
                checkProgramTree(ppid);
                WasTriggeredBy tempEdge = new WasTriggeredBy((Program) localCache.get(pid), (Program) localCache.get(ppid));
                putEdge(tempEdge);
            } else {
                return;
            }
        }
    }

    /**
     * Read event triggered by FUSE.
     * 
     * @param pid PID of the triggering process.
     * @param iotime IO time of the operation.
     * @param path Path indicating target file.
     * @param link An integer used to indicate whether the target was a link or not.
     */
    public void read(int pid, int iotime, String path, int link) {
        checkProgramTree(Integer.toString(pid));
        path = sanitizePath(path);
        long now = System.currentTimeMillis();
        // Create file artifact depending on whether this is a link or not.
        // Link artifacts are created differently to avoid recursion that may
        // cause FUSE to crash.
        File fileVertex = (link == 1) ? createLinkVertex(path) : createFileVertex(path);
        putVertex(fileVertex);
        AbstractEdge edge = new Used((Program) localCache.get(Integer.toString(pid)), fileVertex);
        edge.addAnnotation("iotime", Integer.toString(iotime));
        edge.addAnnotation("endtime", Long.toString(now));
        putEdge(edge);
        // If the given path represents a link, then perform the same operation on the
        // artifact to which the link points.
        if (link == 1 && links.containsKey(path)) {
            read(pid, iotime, links.get(path), 0);
        }
    }

    /**
     * Write event triggered by FUSE.
     * 
     * @param pid PID of the triggering process.
     * @param iotime IO time of the operation.
     * @param path Path indicating target file.
     * @param link An integer used to indicate whether the target was a link or not.
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
        AbstractEdge edge = new WasGeneratedBy(fileVertex, (Program) localCache.get(Integer.toString(pid)));
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
     * @param link An integer used to indicate whether the target was a link or not.
     * @param done An intiger used to indicate whether this event was triggered before
     * or after the rename operation.
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
            Runtime.getRuntime().exec("umount -f " + mountPoint).waitFor();
            Runtime.getRuntime().exec("rm -r " + mountPoint).waitFor();
            return true;
        } catch (Exception exception) {
            Logger.getLogger(MacFUSE.class.getName()).log(Level.SEVERE, null, exception);
            return false;
        }
    }
}
