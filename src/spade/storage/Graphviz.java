/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2016 SRI International

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

import java.io.FileWriter;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.codec.binary.Hex;
import spade.core.AbstractEdge;
import spade.core.AbstractStorage;
import spade.core.AbstractVertex;

/**
 * A storage implementation that writes data to a DOT file.
 *
 * @author Dawood Tariq
 */
public class Graphviz extends AbstractStorage {

    private FileWriter outputFile;
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
            outputFile = new FileWriter(filePath, false);
            transaction_count = 0;
            outputFile.write("digraph spade2dot {\n"
                    + "graph [rankdir = \"RL\"];\n"
                    + "node [fontname=\"Helvetica\" fontsize=\"8\" style=\"filled\" margin=\"0.0,0.0\"];\n"
                    + "edge [fontname=\"Helvetica\" fontsize=\"8\"];\n");
            return true;
        } catch (Exception exception) {
            Logger.getLogger(Graphviz.class.getName()).log(Level.SEVERE, null, exception);
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
                Logger.getLogger(Graphviz.class.getName()).log(Level.SEVERE, null, exception);
            }
        }
    }

    @Override
    public boolean putVertex(AbstractVertex incomingVertex) {
        try {
            StringBuilder annotationString = new StringBuilder();
            for (Map.Entry<String, String> currentEntry : incomingVertex.getAnnotations().entrySet()) {
                String key = currentEntry.getKey();
                String value = currentEntry.getValue();
                if (key == null || value == null) {
                    continue;
                }
                annotationString.append(key);
                annotationString.append(":");
                annotationString.append(value);
                annotationString.append("\\n");
            }
            String vertexString = annotationString.substring(0, annotationString.length() - 2);
            String shape = "box";
            String color = "white";
            String type = incomingVertex.getAnnotation("type");
            if (type.equalsIgnoreCase("Agent")) {
                shape = "octagon";
                color = "rosybrown1";
            } else if (type.equalsIgnoreCase("Process") || type.equalsIgnoreCase("Activity")) {
                shape = "box";
                color = "lightsteelblue1";
            } else if (type.equalsIgnoreCase("Artifact") || type.equalsIgnoreCase("Entity")) {
                shape = "ellipse";
                color = "khaki1";
                try {
                    String subtype = incomingVertex.getAnnotation("subtype");
                    if (subtype.equalsIgnoreCase("network")) {
                        shape = "diamond";
                        color = "palegreen1";
                    }
                } catch (Exception exception) {
                    // Ignore
                }
            }

            String key = Hex.encodeHexString(incomingVertex.bigHashCode());
            outputFile.write("\"" + key + "\" [label=\"" + vertexString.replace("\"", "'") + "\" shape=\"" + shape + "\" fillcolor=\"" + color + "\"];\n");
            checkTransactions();
            return true;
        } catch (Exception exception) {
            Logger.getLogger(Graphviz.class.getName()).log(Level.SEVERE, null, exception);
            return false;
        }
    }

    @Override
    public boolean putEdge(AbstractEdge incomingEdge) {
        try {
            StringBuilder annotationString = new StringBuilder();
            for (Map.Entry<String, String> currentEntry : incomingEdge.getAnnotations().entrySet()) {
                String key = currentEntry.getKey();
                String value = currentEntry.getValue();
                if (key == null || value == null) {
                    continue;
                }
                annotationString.append(key);
                annotationString.append(":");
                annotationString.append(value);
                annotationString.append("\\n");
            }
            String color = "black";
            String type = incomingEdge.getAnnotation("type");
            if (type.equalsIgnoreCase("Used")) {
                color = "green";
            } else if (type.equalsIgnoreCase("WasGeneratedBy")) {
                color = "red";
            } else if (type.equalsIgnoreCase("WasTriggeredBy") || type.equalsIgnoreCase("WasInformedBy")) {
                color = "blue";
            } else if (type.equalsIgnoreCase("WasControlledBy") || type.equalsIgnoreCase("WasAssociatedWith")) {
                color = "purple";
            } else if (type.equalsIgnoreCase("WasDerivedFrom")) {
                color = "orange";
            }

            String style = "solid";
            if (incomingEdge.getAnnotation("success") != null && incomingEdge.getAnnotation("success").equals("false")) {
                style = "dashed";
            }

            String edgeString = annotationString.toString();
            if (edgeString.length() > 0) {
                edgeString = "(" + edgeString.substring(0, edgeString.length() - 2) + ")";
            }

            String srckey = Hex.encodeHexString(incomingEdge.getSourceVertex().bigHashCode());
            String dstkey = Hex.encodeHexString(incomingEdge.getDestinationVertex().bigHashCode());

            outputFile.write("\"" + srckey + "\" -> \"" + dstkey + "\" [label=\"" + edgeString.replace("\"", "'") + "\" color=\"" + color + "\" style=\"" + style + "\"];\n");
            checkTransactions();
            return true;
        } catch (Exception exception) {
            Logger.getLogger(Graphviz.class.getName()).log(Level.SEVERE, null, exception);
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
            Logger.getLogger(Graphviz.class.getName()).log(Level.SEVERE, null, exception);
            return false;
        }
    }
}
