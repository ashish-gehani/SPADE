/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2015 SRI International

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

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.codec.binary.Hex;

import spade.core.AbstractEdge;
import spade.core.AbstractStorage;
import spade.core.AbstractVertex;
import spade.reporter.audit.OPMConstants;
import spade.utility.CommonFunctions;

import static spade.core.Kernel.CONFIG_PATH;
import static spade.core.Kernel.FILE_SEPARATOR;

/**
 * A storage implementation that writes data to a DOT file.
 *
 * @author Dawood Tariq
 */
public class Graphviz extends AbstractStorage
{

    private FileWriter outputFile;
    private final int TRANSACTION_LIMIT = 1000;
    private int transaction_count;
    private String output;

    public Graphviz()
    {
        String configFile = CONFIG_PATH + FILE_SEPARATOR + "spade.storage.Graphviz.config";
        try
        {
            databaseConfigs.load(new FileInputStream(configFile));
        }
        catch(IOException ex)
        {
            String msg = "Loading Graphviz configurations from file unsuccessful! Unexpected behavior might follow";
            logger.log(Level.SEVERE, msg, ex);
        }
    }

    @Override
    public boolean initialize(String arguments)
    {
        try
        {
            Map<String, String> argsMap = CommonFunctions.parseKeyValPairs(arguments);
            output = (argsMap.get("output") != null) ? argsMap.get("output") :
                    databaseConfigs.getProperty("output");

            outputFile = new FileWriter(output, false);
            transaction_count = 0;
            outputFile.write("digraph spade2dot {\n"
                    + "graph [rankdir = \"RL\"];\n"
                    + "node [fontname=\"Helvetica\" fontsize=\"8\" style=\"filled\" margin=\"0.0,0.0\"];\n"
                    + "edge [fontname=\"Helvetica\" fontsize=\"8\"];\n");
            return true;
        }
        catch(Exception exception)
        {
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
                outputFile = new FileWriter(output, true);
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
            if (type.equalsIgnoreCase("Agent")
                    || type.equalsIgnoreCase("Principal")) {
                shape = "octagon";
                color = "rosybrown1";
            } else if (type.equalsIgnoreCase("Process")
                    || type.equalsIgnoreCase("Activity")
                    || type.equalsIgnoreCase("Subject")) {
                shape = "box";
                color = "lightsteelblue1";
            } else if (type.equalsIgnoreCase("Artifact")
                    || type.equalsIgnoreCase("Entity")
                    || type.equalsIgnoreCase("Object")) {
                shape = "ellipse";
                color = "khaki1";
                try {
                    String subtype = incomingVertex.getAnnotation(OPMConstants.ARTIFACT_SUBTYPE);
                    String cdmType = incomingVertex.getAnnotation("cdm.type");
                    if (OPMConstants.SUBTYPE_NETWORK_SOCKET.equalsIgnoreCase(subtype)
                            || "NetFlowObject".equalsIgnoreCase(cdmType)) {
                        shape = "diamond";
                        color = "palegreen1";
                    }
                } catch (Exception exception) {
                    // Ignore
                }
            }

            String key = Hex.encodeHexString(incomingVertex.bigHashCodeBytes());
            outputFile.write("\"" + key + "\" [label=\"" + vertexString.replace("\"", "'") + "\" shape=\"" + shape + "\" fillcolor=\"" + color + "\"];\n");
            checkTransactions();
            return true;
        } catch (Exception exception) {
            Logger.getLogger(Graphviz.class.getName()).log(Level.SEVERE, null, exception);
            return false;
        }
    }

    @Override
    public Object executeQuery(String query)
    {
        return null;
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
            } else if (type.equalsIgnoreCase("WasTriggeredBy")
                    || type.equalsIgnoreCase("WasInformedBy")) {
                color = "blue";
            } else if (type.equalsIgnoreCase("WasControlledBy")
                    || type.equalsIgnoreCase("WasAssociatedWith")) {
                color = "purple";
            } else if (type.equalsIgnoreCase("WasDerivedFrom")) {
                color = "orange";
            } else if(type.equalsIgnoreCase("SimpleEdge")){
                String cdmTypeString = incomingEdge.getAnnotation("cdm.type");
                if(cdmTypeString != null){//exception handling
                    switch (cdmTypeString) {
                        case "UnitDependency":
                        case "EVENT_EXIT":
                        case "EVENT_FORK":
                        case "EVENT_CLONE":
                        case "EVENT_EXECUTE":
                        case "EVENT_CHANGE_PRINCIPAL":
                        case "EVENT_UNIT":
                        case "EVENT_MODIFY_PROCESS":
                        case "EVENT_SIGNAL":
                            color = "blue";
                            break;
                        case "EVENT_TEE":
                        case "EVENT_SPLICE":
                        case "EVENT_CLOSE":
                        case "EVENT_OPEN":
                        case "EVENT_CREATE_OBJECT":
                        case "EVENT_MMAP":
                        case "EVENT_RENAME":
                        case "EVENT_LINK":
                        case "EVENT_UPDATE":
                            color = "violet";
                            break;
                        case "EVENT_VMSPLICE":
                        case "EVENT_UNLINK":
                        case "EVENT_WRITE":
                        case "EVENT_SENDMSG":
                        case "EVENT_MPROTECT":
                        case "EVENT_CONNECT":
                        case "EVENT_TRUNCATE":
                        case "EVENT_MODIFY_FILE_ATTRIBUTES":
                            color = "red";
                            break;
                        case "EVENT_INIT_MODULE":
                        case "EVENT_FINIT_MODULE":
                        case "EVENT_LOADLIBRARY":
                        case "EVENT_READ":
                        case "EVENT_RECVMSG":
                        case "EVENT_ACCEPT":
                            color = "green";
                            break;
                        default:
                            color = "black";
                            break;
                    }
                }else{
                    color = "black";
                }
            }

            String style = "solid";
            if (incomingEdge.getAnnotation("success") != null && incomingEdge.getAnnotation("success").equals("false")) {
                style = "dashed";
            }

            String edgeString = annotationString.toString();
            if (edgeString.length() > 0) {
                edgeString = "(" + edgeString.substring(0, edgeString.length() - 2) + ")";
            }

            String srckey = Hex.encodeHexString(incomingEdge.getChildVertex().bigHashCodeBytes());
            String dstkey = Hex.encodeHexString(incomingEdge.getParentVertex().bigHashCodeBytes());

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

    /**
     * This function queries the underlying storage and retrieves the edge
     * matching the given criteria.
     *
     * @param childVertexHash  hash of the source vertex.
     * @param parentVertexHash hash of the destination vertex.
     * @return returns edge object matching the given vertices OR NULL.
     */
    @Override
    public spade.core.AbstractEdge getEdge(String childVertexHash, String parentVertexHash)
    {
        return null;
    }

    /**
     * This function queries the underlying storage and retrieves the vertex
     * matching the given criteria.
     *
     * @param vertexHash hash of the vertex to find.
     * @return returns vertex object matching the given hash OR NULL.
     */
    @Override
    public spade.core.AbstractVertex getVertex(String vertexHash)
    {
        return null;
    }

    /**
     * This function finds the children of a given vertex.
     * A child is defined as a vertex which is the source of a
     * direct edge between itself and the given vertex.
     *
     * @param parentHash hash of the given vertex
     * @return returns graph object containing children of the given vertex OR NULL.
     */
    @Override
    public spade.core.Graph getChildren(String parentHash)
    {
        return null;
    }

    /**
     * This function finds the parents of a given vertex.
     * A parent is defined as a vertex which is the destination of a
     * direct edge between itself and the given vertex.
     *
     * @param childVertexHash hash of the given vertex
     * @return returns graph object containing parents of the given vertex OR NULL.
     */
    @Override
    public spade.core.Graph getParents(String childVertexHash)
    {
        return null;
    }
}
