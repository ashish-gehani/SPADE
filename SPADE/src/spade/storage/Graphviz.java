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
import spade.core.AbstractVertex;
import java.io.FileWriter;
import java.util.HashSet;
import java.util.Map;
import java.util.Iterator;
import java.util.Set;

public class Graphviz extends AbstractStorage {

    private FileWriter outputFile;
    private Set<Integer> edgeSet;
    private Set<Integer> vertexSet;
    private final int TRANSACTION_LIMIT = 1000;
    private int transaction_count;
    private String filePath;

    @Override
    public boolean initialize(String arguments) {
        try {
            if (arguments == null) {
                return false;
            }
            filePath = arguments;
            edgeSet = new HashSet<Integer>();
            vertexSet = new HashSet<Integer>();
            outputFile = new FileWriter(filePath, false);
            transaction_count = 0;
            outputFile.write("digraph SPADE2Graphviz {\ngraph [rankdir = \"RL\"];\nnode [fontname=\"Helvetica\" fontsize=\"8\" style=\"filled\" margin=\"0.0,0.0\"];\nedge [fontname=\"Helvetica\" fontsize=\"8\"];\n");
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private void checkTransactions() {
        transaction_count++;
        if (transaction_count == TRANSACTION_LIMIT) {
            try {
                outputFile.flush();
                outputFile.close();
                outputFile = new FileWriter(filePath, true);
                transaction_count = 0;
            } catch (Exception exception) {
            }
        }
    }

    @Override
    public boolean putVertex(AbstractVertex incomingVertex) {
        try {
            if (vertexSet.add(incomingVertex.hashCode())) {
                String annotationString = "";
                Map<String, String> annotations = incomingVertex.getAnnotations();
                for (Iterator iterator = annotations.keySet().iterator(); iterator.hasNext();) {
                    String key = (String) iterator.next();
                    String value = (String) annotations.get(key);
                    if ((key.equalsIgnoreCase("type")) || (key.equalsIgnoreCase("storageId"))
                            || (key.equalsIgnoreCase("environment")) || (key.equalsIgnoreCase("commandline"))
                            || (key.equalsIgnoreCase("source_reporter"))) {
                        continue;
                    }
                    if (key.equalsIgnoreCase("path") && value.length() > 12) {
                        value = value.substring(0, 11) + "...";
                    }
                    annotationString = annotationString + key.replace("\\", "\\\\") + ":" + value.replace("\\", "\\\\") + "\\n";
                }
                annotationString = annotationString.substring(0, annotationString.length() - 2);
                String shape = "box";
                String color = "white";
                String type = incomingVertex.getAnnotation("type");
                if (type.equalsIgnoreCase("Agent")) {
                    shape = "octagon";
                    color = "rosybrown1";
                } else if (type.equalsIgnoreCase("Process")) {
                    shape = "box";
                    color = "lightsteelblue1";
                } else if (type.equalsIgnoreCase("Artifact")) {
                    shape = "ellipse";
                    color = "khaki1";
                }
                outputFile.write("\"" + incomingVertex.hashCode() + "\" [label=\"" + annotationString.replace("\"", "'") + "\" shape=\"" + shape + "\" fillcolor=\"" + color + "\"];\n");
                checkTransactions();
                return true;
            }
            return false;
        } catch (Exception exception) {
            return false;
        }
    }

    @Override
    public boolean putEdge(AbstractEdge incomingEdge) {
        try {
            if (edgeSet.add(incomingEdge.hashCode())) {
                String annotationString = "";
                Map<String, String> annotations = incomingEdge.getAnnotations();
                for (Iterator iterator = annotations.keySet().iterator(); iterator.hasNext();) {
                    String key = (String) iterator.next();
                    String value = (String) annotations.get(key);
                    if ((key.equalsIgnoreCase("storageId")) || (key.equalsIgnoreCase("type"))
                            || (key.equalsIgnoreCase("source_reporter"))) {
                        continue;
                    }
                    annotationString = annotationString + key.replace("\\", "\\\\") + ":" + value.replace("\\", "\\\\") + ", ";
                }
                String color = "black";
                String type = incomingEdge.getAnnotation("type");
                if (type.equalsIgnoreCase("Used")) {
                    color = "green";
                } else if (type.equalsIgnoreCase("WasGeneratedBy")) {
                    color = "red";
                } else if (type.equalsIgnoreCase("WasTriggeredBy")) {
                    color = "blue";
                } else if (type.equalsIgnoreCase("WasControlledBy")) {
                    color = "purple";
                } else if (type.equalsIgnoreCase("WasDerivedFrom")) {
                    color = "orange";
                }
                if (annotationString.length() > 3) {
                    annotationString = "(" + annotationString.substring(0, annotationString.length() - 2) + ")";
                }
                String edgeString = "\"" + incomingEdge.getSourceVertex().hashCode() + "\" -> \"" + incomingEdge.getDestinationVertex().hashCode() + "\" [label=\"" + annotationString.replace("\"", "'") + "\" color=\"" + color + "\"];\n";
                outputFile.write(edgeString);
                checkTransactions();
                return true;
            }
            return false;
        } catch (Exception exception) {
            return false;
        }
    }

    @Override
    public boolean shutdown() {
        try {
            outputFile.write("}\n");
            outputFile.close();
            return true;
        } catch (Exception exception) {
            return false;
        }
    }
}
