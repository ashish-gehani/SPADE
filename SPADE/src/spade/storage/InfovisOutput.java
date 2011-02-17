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
import spade.core.AbstractEdge;
import spade.opm.edge.WasTriggeredBy;
import spade.opm.edge.WasGeneratedBy;
import spade.opm.edge.Used;
import spade.opm.edge.WasDerivedFrom;
import spade.core.AbstractVertex;
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
    public boolean putVertex(AbstractVertex incomingVertex) {
        if (!(incomingVertex instanceof Process)) {
            return false;
        }
        try {
            Map annotations = incomingVertex.getAnnotations();
            int vertexHash = incomingVertex.hashCode();
            if (vertexHash < 0) {
                vertexHash *= -1;
            }
            String vname;
            if (incomingVertex instanceof Process) {
                vname = (String) annotations.get("pidname");
            } else {
                vname = (String) annotations.get("filename");
            }
            String json_path;
            String boottime;
            String beginjson;
            String description = "";
            for (Iterator iterator = annotations.keySet().iterator(); iterator.hasNext();) {
                String key = (String) iterator.next();
                String value = (String) annotations.get(key);
                if (key.equals("type")) {
                    continue;
                }
                description = description + "<u><b>" + key + "</b></u>: " + value + "<br />";
            }
            if (vname.equals("System")) {
                json_path = basepath + "json_root.txt";
                boottime = incomingVertex.getAnnotation("boottime");
                beginjson = "{id:\"" + vertexHash + "\", name:\"" + vname + "\", data:{type:\"process\", description:\"<u><b>boottime</b></u>: " + boottime + "\"}, children:[";
            } else {
                if (vname.length() > 15) {
                    vname = vname.substring(0, 15);
                }
                json_path = basepath + "json_" + vertexHash + ".txt";
                beginjson = "{id:\"" + vertexHash + "\", name:\"" + vname + "\", data:{description:\"" + description + "\"}, children:[  ";
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
    public boolean putEdge(AbstractEdge incomingEdge) {
        if (!(incomingEdge instanceof WasTriggeredBy)) {
            return false;
        }
        AbstractVertex vsrc = incomingEdge.getSrcVertex();
        AbstractVertex vdst = incomingEdge.getDstVertex();
        int srcNodeId = vsrc.hashCode();
        int dstNodeId = vdst.hashCode();
        if (srcNodeId < 0) {
            srcNodeId *= -1;
        }
        if (dstNodeId < 0) {
            dstNodeId *= -1;
        }
        if (incomingEdge instanceof Used) {
            srcNodeId = vdst.hashCode();
            dstNodeId = vsrc.hashCode();
            vsrc = vdst;
        }
        if (EdgeSet.add(incomingEdge.hashCode())) {
            try {
                String json_path = basepath + "json_" + dstNodeId + ".txt";
                if (vdst.getAnnotation("pid").equals("0")) {
                    json_path = basepath + "json_root.txt";
                }
                String tmpid = Long.toString(srcNodeId);
                String tmpname = vsrc.getAnnotation("pidname");
                if (tmpname == null) {
                    tmpname = vsrc.getAnnotation("filename");
                }
                if (tmpname.length() > 15) {
                    tmpname = tmpname.substring(0, 15);
                }
                String tmptype = "process";
                if ((incomingEdge instanceof WasGeneratedBy) || (incomingEdge instanceof WasDerivedFrom)) {
                    tmptype = "writefile";
                } else if (incomingEdge instanceof Used) {
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
