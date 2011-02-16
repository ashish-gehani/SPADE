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
import spade.opm.edge.WasControlledBy;
import spade.opm.edge.WasGeneratedBy;
import spade.opm.edge.Used;
import spade.opm.edge.WasDerivedFrom;
import spade.opm.vertex.Artifact;
import spade.core.AbstractVertex;
import java.io.FileWriter;
import java.util.HashSet;
import java.util.Map;
import java.util.Iterator;

public class DOTOutput extends AbstractStorage {

    private FileWriter outputFile;
    private HashSet EdgeSet;

    @Override
    public boolean initialize(String path) {
        try {
            EdgeSet = new HashSet();
            outputFile = new FileWriter(path, false);
            outputFile.write("digraph spade_dot {\ngraph [rankdir = \"RL\"];\nnode [fontname=\"Helvetica\" fontsize=\"10\"];\nedge [fontname=\"Helvetica\" fontsize=\"10\"];\n");
            return true;
        } catch (Exception exception) {
            exception.printStackTrace(System.err);
        }
        return false;
    }

    @Override
    public boolean putVertex(AbstractVertex incomingVertex) {
        try {
            String vertexString = "";
            Map<String, String> annotations = incomingVertex.getAnnotations();
            for (Iterator it = annotations.keySet().iterator(); it.hasNext();) {
                String name = (String) it.next();
                String value = (String) annotations.get(name);
                vertexString = vertexString + name + ":" + value + "|";
            }
            vertexString = vertexString.substring(0, vertexString.length() - 1);
            if (incomingVertex instanceof Artifact) {
                outputFile.write("\"" + incomingVertex.hashCode() + "\" [label=\"" + vertexString.replace("\"", "'") + "\" shape=\"Mrecord\"];\n");
            } else {
                outputFile.write("\"" + incomingVertex.hashCode() + "\" [label=\"" + vertexString.replace("\"", "'") + "\" shape=\"record\"];\n");
            }
            return true;
        } catch (Exception exception) {
            exception.printStackTrace(System.err);
        }
        return false;
    }

    @Override
    public boolean putEdge(AbstractEdge incomingEdge) {
        try {
            if (EdgeSet.add(incomingEdge.hashCode())) {
                String annotationString = "";
                Map<String, String> annotations = incomingEdge.getAnnotations();
                for (Iterator it = annotations.keySet().iterator(); it.hasNext();) {
                    String name = (String) it.next();
                    String value = (String) annotations.get(name);
                    if (name.equals("type")) {
                        continue;
                    }
                    annotationString = annotationString + name + ":" + value + ", ";
                }
                String color = "";
                if (incomingEdge instanceof Used) {
                    color = "green";
                } else if (incomingEdge instanceof WasGeneratedBy) {
                    color = "red";
                } else if (incomingEdge instanceof WasTriggeredBy) {
                    color = "blue";
                } else if (incomingEdge instanceof WasControlledBy) {
                    color = "orange";
                } else if (incomingEdge instanceof WasDerivedFrom) {
                    color = "purple";
                }
                if (annotationString.length() > 3) {
                    annotationString = "(" + annotationString.substring(0, annotationString.length() - 2) + ")";
                }
                String edgeString = "\"" + incomingEdge.getSrcVertex().hashCode() + "\" -> \"" + incomingEdge.getDstVertex().hashCode() + "\" [label=\"" + annotationString.replace("\"", "'") + "\" color=\"" + color + "\"];\n";
                outputFile.write(edgeString);
                return true;
            }
        } catch (Exception exception) {
            exception.printStackTrace(System.err);
        }
        return false;
    }

    @Override
    public boolean shutdown() {
        try {
            outputFile.write("}\n");
            outputFile.close();
            return true;
        } catch (Exception exception) {
            exception.printStackTrace(System.err);
        }
        return false;
    }
}
