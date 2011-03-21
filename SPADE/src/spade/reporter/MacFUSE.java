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
import java.io.InputStreamReader;
import java.util.HashMap;

public class MacFUSE extends AbstractReporter {

    private HashMap<String, AbstractVertex> localCache;
    private HashMap<String, String> links;
    private String mountPoint;

    public native int launchFUSE(String argument);

    public Process createProcessVertex(String pid) {
        Process processVertex = new Process();
        try {
            String line = "";
            java.lang.Process pidinfo = Runtime.getRuntime().exec("ps -p " + pid + " -co pid,ppid,uid,gid,lstart,sess,comm");
            BufferedReader pidreader = new BufferedReader(new InputStreamReader(pidinfo.getInputStream()));
            line = pidreader.readLine();
            line = pidreader.readLine();
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
        return processVertex;
    }

    private void buildProcessTree() {
        try {
            String line = "";
            java.lang.Process pidinfo = Runtime.getRuntime().exec("ps -Aco pid,ppid,comm");
            BufferedReader pidreader = new BufferedReader(new InputStreamReader(pidinfo.getInputStream()));
            line = pidreader.readLine();
            line = pidreader.readLine().trim();
            String info[] = line.split("\\s+", 3);
            Process rootProcess = createProcessVertex(info[0]);
            putVertex(rootProcess);
            localCache.put("0", rootProcess);
            while (true) {
                line = pidreader.readLine().trim();
                String childinfo[] = line.split("\\s+", 3);
                if (childinfo[2].equals("ps")) {
                    break;
                }
                Process tempVertex = createProcessVertex(childinfo[0]);
                String ppid = (String) tempVertex.getAnnotation("ppid");
                putVertex(tempVertex);
                localCache.put(tempVertex.getAnnotation("pid"), tempVertex);
                if (Integer.parseInt(ppid) >= 0) {
                    WasTriggeredBy tempEdge = new WasTriggeredBy((Process) localCache.get(childinfo[0]), (Process) localCache.get(ppid));
                    putEdge(tempEdge);
                }
            }
        } catch (Exception exception) {
            exception.printStackTrace(System.err);
        }
    }

    @Override
    public boolean launch(String arguments) {
        mountPoint = arguments;
        localCache = new HashMap<String, AbstractVertex>();
        links = new HashMap<String, String>();

        File mount = new File(mountPoint);
        if (mount.exists()) {
            return false;
        } else {
            try {
                Runtime.getRuntime().exec("mkdir " + mountPoint);
            } catch (Exception exception) {
                exception.printStackTrace(System.err);
                return false;
            }
        }

        System.loadLibrary("spadeMacFUSE");
        buildProcessTree();

        Runnable r1 = new Runnable() {

            public void run() {
                try {
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
        try {
            while (true) {
                if (localCache.get(pid) != null) {
                    return;
                }
                Process tempVertex = createProcessVertex(pid);
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
//            exception.printStackTrace(System.err);
        }
        return;
    }

    public void readwrite(int write, int pid, int iotime, String path) {
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
        if (links.containsKey(path)) {
            readwrite(write, pid, iotime, links.get(path));
        }
    }

    public void rename(int pid, int iotime, String pathfrom, String pathto) {
        long now = System.currentTimeMillis();
        checkProcessTree(Integer.toString(pid));
        WasDerivedFrom tempEdge = new WasDerivedFrom((Artifact) localCache.get(pathto), (Artifact) localCache.get(pathfrom));
        tempEdge.addAnnotation("iotime", Integer.toString(iotime));
        tempEdge.addAnnotation("operation", "rename");
        tempEdge.addAnnotation("endtime", Long.toString(now));
        putEdge(tempEdge);
        if (links.containsKey(pathfrom)) {
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
            Runtime.getRuntime().exec("fusermount -u -z " + mountPoint);
            Thread.sleep(500);
            Runtime.getRuntime().exec("rm -r " + mountPoint);
            return true;
        } catch (Exception exception) {
            exception.printStackTrace(System.err);
            return false;
        }
    }
}
