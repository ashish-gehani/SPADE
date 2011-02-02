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

import java.io.FileWriter;
import java.util.*;

public class DOTOutput implements ConsumerInterface {

    private FileWriter out;
    private HashSet<String> EdgeSet;

    public boolean initialize(String path) {
        try {
            EdgeSet = new HashSet<String>();
            out = new FileWriter(path, false);
            out.write("digraph spade_dot {\ngraph [rankdir = \"RL\"];\nnode [fontname=\"Helvetica\" fontsize=\"10\" shape=\"Mrecord\"];\nedge [fontname=\"Helvetica\" fontsize=\"10\"];\n");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean putVertex(Vertex v) {
        try {
            String vertexstring = "";
            HashMap<String, String> annotations = v.getAnnotations();
            for (Iterator it = annotations.keySet().iterator(); it.hasNext();) {
                String name = (String) it.next();
                String value = (String) annotations.get(name);
                vertexstring = vertexstring + name + ":" + value + "|";
            }
            vertexstring = vertexstring.substring(0, vertexstring.length() - 1);
            out.write("\"" + v.hashCode() + "\" [label=\"" + vertexstring + "\"];\n");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean putEdge(Edge e) {
        try {
            if (EdgeSet.add(e.getSrcVertex().hashCode() + "-" + e.getDstVertex().hashCode())) {
                String annotationstring = "";
                HashMap<String, String> annotations = e.getAnnotations();
                for (Iterator it = annotations.keySet().iterator(); it.hasNext();) {
                    String name = (String) it.next();
                    String value = (String) annotations.get(name);
                    if (name.equals("type")) {
                        continue;
                    }
                    annotationstring = annotationstring + name + ":" + value + ", ";
                }
                if (annotationstring.length() > 3) {
                    annotationstring = e.getEdgeType() + " (" + annotationstring.substring(0, annotationstring.length() - 2) + ")";
                } else {
                    annotationstring = e.getEdgeType();
                }
                String edgestring = "\"" + e.getSrcVertex().hashCode() + "\" -> \"" + e.getDstVertex().hashCode() + "\" [label=\"" + annotationstring + "\"];\n";
                out.write(edgestring);
                return true;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return false;
    }

    public boolean shutdown() {
        try {
            out.write("}");
            out.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
