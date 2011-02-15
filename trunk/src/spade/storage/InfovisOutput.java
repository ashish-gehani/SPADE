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

package spade.storage;

import spade.core.AbstractStorage;
import spade.opm.edge.Edge;
import spade.opm.edge.WasTriggeredBy;
import spade.opm.edge.WasGeneratedBy;
import spade.opm.edge.Used;
import spade.opm.edge.WasDerivedFrom;
import spade.opm.vertex.Vertex;
import spade.opm.vertex.Process;
import java.io.FileWriter;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

public class InfovisOutput extends AbstractStorage {

    private String basepath;
    private HashSet EdgeSet;

    @Override
    public boolean initialize(String path) {
//        basepath = path;
        basepath = "/home/dawood/Desktop/SharedFolder/spadetree/json/";
        EdgeSet = new HashSet();
        return true;
    }

    @Override
    public boolean putVertex(Vertex v) {
        if (!(v instanceof Process)) {
            return false;
        }
        try {
            Map annotations = v.getAnnotations();
            int vid = v.hashCode();
            if (vid < 0) {
                vid *= -1;
            }
            String vname;
            if (v instanceof Process) {
                vname = (String) annotations.get("pidname");
            } else {
                vname = (String) annotations.get("filename");
            }
            String json_path;
            String boottime;
            String beginjson;
            String description = "";
            for (Iterator it = annotations.keySet().iterator(); it.hasNext();) {
                String name = (String) it.next();
                String value = (String) annotations.get(name);
                if (name.equals("type")) {
                    continue;
                }
                description = description + "<u><b>" + name + "</b></u>: " + value + "<br />";
            }
            if (vname.equals("System")) {
                json_path = basepath + "json_root.txt";
                boottime = v.getAnnotationValue("boottime");
                beginjson = "{id:\"" + vid + "\", name:\"" + vname + "\", data:{type:\"process\", description:\"<u><b>boottime</b></u>: " + boottime + "\"}, children:[";
            } else {
                if (vname.length() > 15) {
                    vname = vname.substring(0, 15);
                }
                json_path = basepath + "json_" + vid + ".txt";
                beginjson = "{id:\"" + vid + "\", name:\"" + vname + "\", data:{description:\"" + description + "\"}, children:[  ";
            }
            FileWriter out = new FileWriter(json_path, false);
            out.write(beginjson);
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    @Override
    public boolean putEdge(Edge e) {
        if (!(e instanceof WasTriggeredBy)) {
            return false;
        }
        Vertex vsrc = e.getSrcVertex();
        Vertex vdst = e.getDstVertex();
        int srcNodeId = vsrc.hashCode();
        int dstNodeId = vdst.hashCode();
        if (srcNodeId < 0) {
            srcNodeId *= -1;
        }
        if (dstNodeId < 0) {
            dstNodeId *= -1;
        }
        if (e instanceof Used) {
            srcNodeId = vdst.hashCode();
            dstNodeId = vsrc.hashCode();
            vsrc = vdst;
        }
        if (EdgeSet.add(dstNodeId + "-" + srcNodeId)) {
            try {
                String json_path = basepath + "json_" + dstNodeId + ".txt";
                if (vdst.getAnnotationValue("pid").equals("0")) {
                    json_path = basepath + "json_root.txt";
                }
                String tmpid = Long.toString(srcNodeId);
                String tmpname = vsrc.getAnnotationValue("pidname");
                if (tmpname == null) {
                    tmpname = vsrc.getAnnotationValue("filename");
                }
                if (tmpname.length() > 15) {
                    tmpname = tmpname.substring(0, 15);
                }
                String tmptype = "process";
                if ((e instanceof WasGeneratedBy) || (e instanceof WasDerivedFrom)) {
                    tmptype = "writefile";
                } else if (e instanceof Used) {
                    tmptype = "readfile";
                }
                String childrendata = "{id:\"" + tmpid + "\", name:\"" + tmpname + "\", data:{type:\"" + tmptype + "\"}}, ";
                FileWriter out = new FileWriter(json_path, true);
                out.write(childrendata);
                out.close();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return true;
    }

    @Override
    public boolean shutdown() {
        return true;
    }
}
