/**
 * ContextualTemporalTraversal Transformer
 * ----------------------------------
 * This transformer performs a context-sensitive temporal traversal of a graph. The traversal ensures causally future events are included by considering only edges with timestamps greater than a calculated minimum time for each vertex, rather than a global timestamp. It was specifically developed for use in Watson's adapted DFS implementation in the Shadewatcher system.
 *  
 * This approach helps in summarizing behavior instances while avoiding false dependencies and dependency explosions. Refer to WATSON: Abstracting Behaviors from Audit Logs via Aggregation of Contextual Semantics section III-d for more details.
 * 
 * Notes:
 *  - Outputs traversal time span to `/tmp/temporal_traversal.json`.
 * --------------------------------------------------------------------------------
 * SPADE - Support for Provenance Auditing in Distributed Environments.
 * Copyright (C) 2015 SRI International
 * 
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program. If not, see <http://www.gnu.org/licenses/>.
 * --------------------------------------------------------------------------------
 */


package spade.transformer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONObject;
import spade.core.AbstractEdge;
import spade.core.AbstractTransformer;
import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.utility.ArgumentFunctions;
import spade.utility.HelperFunctions;

public class ContextualTemporalTraversal extends AbstractTransformer {

        private static final Logger logger = Logger.getLogger(ContextualTemporalTraversal.class.getName());
        private Double graphMinTime;
        private Double graphMaxTime;
        private static final String keyOutputFilename = "outputFilename",keyAnnotationName = "annotationName",keyOutputTime = "outputTime";

        private Boolean outputTime;
        private String outputFilename;
        private String annotationName;

        private BufferedWriter outputWriter;

        @Override
        public boolean initialize(String arguments) {
                try {
                        // Parse arguments with defaults from config file
                        Map<String, String> argumentsMap = HelperFunctions.parseKeyValPairs(arguments);
                        annotationName = ArgumentFunctions.mustParseNonEmptyString(keyAnnotationName, argumentsMap);
                        outputFilename = ArgumentFunctions.mustParseNonEmptyString(keyOutputFilename, argumentsMap);
                        outputTime = ArgumentFunctions.mustParseBoolean(keyOutputTime, argumentsMap);
                        if (outputTime) {
                                File file = new File(outputFilename);
                                if (!file.exists()) {
                                        file.createNewFile();
                                }
                                outputWriter = new BufferedWriter(new FileWriter(file, true));
                                logger.log(Level.INFO, "Output file for traversal results will be written to: " + outputFilename);
                        }
                        // Initialize graphMinTime and graphMaxTime to null
                        graphMinTime = null;
                        graphMaxTime = null;
                        
                        return true;
                } catch (Exception e) {
                        logger.log(Level.SEVERE, "Failed to initialize ContextualTemporalTraversal", e);
                        return false;
                }
        }

        @Override
        public LinkedHashSet<ArgumentName> getArgumentNames() {
                return new LinkedHashSet<ArgumentName>(Arrays.asList(ArgumentName.SOURCE_GRAPH));
        }

        /*
         * Check all edges that have child matching our target Vertex and see their timestamps
         * Find edge with lowest timestamp that is our start time for our traversal
         */
        public Double getMintime(AbstractVertex vertex,HashMap<String, HashMap<String, HashMap<String, AbstractEdge>>> edgeMap) {
                Double minTime = null;
                HashMap<String, AbstractEdge> vertexParentEdges = edgeMap.get(vertex.bigHashCode()).get("parentEdges");

                for (HashMap.Entry<String, AbstractEdge> entry : vertexParentEdges.entrySet()) {
                    AbstractEdge edge = entry.getValue();
                    try {
                        Double time = Double.parseDouble(getAnnotationSafe(edge, annotationName));
                        if (minTime == null || time < minTime) {
                            minTime = time;
                        }
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "Failed to parse where " + annotationName + "='" + getAnnotationSafe(edge, annotationName) + "'");
                    }
                }
        
                if (minTime == null) {
                    // Log that no valid timestamp was found
                    logger.log(Level.INFO, "No valid timestamp found for vertex: " + vertex);
                }
                return minTime;
            }

        /*
         * Get children for a vertex that are from edges with increasing time, edges with lesser time will not be considered
         * Note: Passing adjacentGraph to collect all children from multiple vertices from one level into one graph
         */
        public Graph getAllChildrenForAVertex(AbstractVertex vertex, Graph adjacentGraph, Graph finalGraph, Graph graph, Integer levelCount, HashMap<String, HashMap<String, HashMap<String, AbstractEdge>>> edgeMap, HashMap<String, HashMap<String, HashMap<String, AbstractEdge>>> finalEdgeMap) {
                Double minTime = null;
                Graph childGraph = finalGraph.vertexSet().isEmpty() || adjacentGraph == null ? new Graph() : adjacentGraph;
                minTime = finalGraph.vertexSet().isEmpty() ? getMintime(vertex, edgeMap) : getMintime(vertex, finalEdgeMap);
                if (levelCount == 1) {
                    if (minTime != null && (graphMinTime == null || minTime < graphMinTime)) {
                        graphMinTime = minTime;
                    }
                }
                logger.log(Level.INFO, "Got mintime: " + minTime);
                HashMap<String, AbstractEdge> vertexChildEdges = edgeMap.get(vertex.bigHashCode()).get("childEdges");
                HashMap<String, AbstractEdge> vertexParentEdges = edgeMap.get(vertex.bigHashCode()).get("parentEdges");
        
                for (HashMap.Entry<String, AbstractEdge> entry : vertexChildEdges.entrySet()) {
                    AbstractEdge edge = entry.getValue();
                    AbstractEdge newEdge = createNewWithoutAnnotations(edge);
                    try {
                        Double time = Double.parseDouble(getAnnotationSafe(newEdge, annotationName));
                        if (minTime == null || time > minTime) { 
                                // If minTime is null, all child edges are considered adjacent
                            if (graphMaxTime == null || time > graphMaxTime) {
                                graphMaxTime = time;
                            }
                            childGraph.putVertex(newEdge.getChildVertex());
                            childGraph.putVertex(newEdge.getParentVertex());
                            childGraph.putEdge(newEdge);
                        }
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "Failed to parse where " + annotationName + "='" + getAnnotationSafe(newEdge, annotationName) + "'");
                        logger.log(Level.SEVERE, e.getMessage());
                    }
                }
        
                for (HashMap.Entry<String, AbstractEdge> entry : vertexParentEdges.entrySet()) {
                    AbstractEdge edge = entry.getValue();
                    AbstractEdge newEdge = createNewWithoutAnnotations(edge);
                    childGraph.putVertex(newEdge.getChildVertex());
                    childGraph.putVertex(newEdge.getParentVertex());
                    childGraph.putEdge(newEdge);
                }
        
                return childGraph;
            }

        public HashMap<String, HashMap<String, HashMap<String, AbstractEdge>>> setEdgeMap(Graph graph) {
                HashMap<String, HashMap<String, HashMap<String, AbstractEdge>>> edgeMap = new HashMap<String, HashMap<String, HashMap<String, AbstractEdge>>>();
                for (AbstractVertex vertex : graph.vertexSet()) {
                    edgeMap.put(vertex.bigHashCode(), new HashMap<String, HashMap<String, AbstractEdge>>() {
                        {
                            put("parentEdges", new HashMap<String, AbstractEdge>());
                            put("childEdges", new HashMap<String, AbstractEdge>());
                        }
                    });
                }
                for (AbstractEdge edge : graph.edgeSet()) {
                    AbstractVertex edgeChild = edge.getChildVertex();
                    AbstractVertex edgeParent = edge.getParentVertex();
                    final String edgeHash = edge.bigHashCode();
                    edgeMap.get(edgeChild.bigHashCode()).get("parentEdges").put(edgeHash, edge);
                    edgeMap.get(edgeParent.bigHashCode()).get("childEdges").put(edgeHash, edge);
                }
                return edgeMap;
            }

        @Override
        public Graph transform(Graph graph, ExecutionContext context) {
                Set<AbstractVertex> currentLevel = new HashSet<>();
                /*
                * Pick a start vertex to begin traversal. Pass it to transform method in SPADE Query client.
                * Example: $1 = $2.transform(ContextualTemporalTraversal, "order=timestamp", $startVertex, 'descendant')
                */
                List<AbstractVertex> startGraphVertexSet = new ArrayList<AbstractVertex>(context.getSourceGraph().vertexSet());
                if (startGraphVertexSet.isEmpty()) {
                        logger.log(Level.SEVERE, "Source graph is empty. Cannot initiate traversal.");
                        return null;
                }
                AbstractVertex startVertex = startGraphVertexSet.get(0);// Select the first vertex from the list as the starting point for traversal
                HashMap<String, HashMap<String, HashMap<String, AbstractEdge>>> edgeMap = setEdgeMap(graph);
                Integer levelCount = 0;

                currentLevel.add(startVertex);
                Graph finalGraph = new Graph();
                logger.log(Level.INFO, "Traversal started. Initiating the process with the selected start vertex.");
                while (!currentLevel.isEmpty()) {
                        logger.log(Level.INFO, "Processing vertices at level: " + levelCount + ". Checking child vertices for traversal.");
                        Graph adjacentGraph = null;
                        HashMap<String, HashMap<String, HashMap<String, AbstractEdge>>> finalEdgeMap = setEdgeMap(finalGraph);
                        for (AbstractVertex node : currentLevel) {
                                // get children of current level nodes
                                // timestamp of subsequent edges > than current
                                adjacentGraph = getAllChildrenForAVertex(node, adjacentGraph, finalGraph, graph, levelCount, edgeMap, finalEdgeMap);
                        }
                        logger.log(Level.INFO, "Children vertices received for current level " + levelCount + ". Moving to process them.");
                        if (!adjacentGraph.vertexSet().isEmpty()) {// If children exists
                                logger.log(Level.INFO, "Adding child vertices to graph and preparing for BFS on the next level.");
                                // Add children in graph and run BFS on children
                                Set<AbstractVertex> nextLevelVertices = new HashSet<AbstractVertex>();
                                nextLevelVertices.addAll(adjacentGraph.vertexSet());
                                nextLevelVertices.removeAll(currentLevel);
                                nextLevelVertices.removeAll(finalGraph.vertexSet());
                                currentLevel.clear();
                                currentLevel.addAll(nextLevelVertices);
                                finalGraph.union(adjacentGraph);
                        } else {
                                logger.log(Level.INFO, "No more children vertices found. Breaking the traversal loop.");
                                break;
                        }
                        finalEdgeMap = null;
                        levelCount++;
                }

                try {
                if (outputTime) {
                        final JSONObject graphTimeSpan = new JSONObject();
                        if (graphMaxTime == null && graphMinTime == null) {
                                logger.log(Level.INFO, "Traversal complete. Final graph structure: " + finalGraph.toString());
                                graphTimeSpan.put("start_time", JSONObject.NULL);
                                graphTimeSpan.put("end_time", JSONObject.NULL);
                        } else if (graphMaxTime == null || graphMinTime == null) {
                                logger.log(Level.SEVERE, "This shouldn't be happening");
                        } else {
                                graphTimeSpan.put("start_time", graphMinTime);
                                graphTimeSpan.put("end_time", graphMaxTime);
                        }
                        try {
                                if (outputWriter != null) {
                                        outputWriter.write(graphTimeSpan.toString() + "\n");
                                }
                        } catch (Exception e) {
                                logger.log(Level.SEVERE,"Failed to write JSON Object for ContextualTemporalTraversal Transformer", e);
                        } finally {
                                if (outputWriter != null) {
                                        try {
                                                outputWriter.close();
                                        } catch (IOException e) {
                                                logger.log(Level.SEVERE, "Failed to close output writer", e);
                                        }
                                }
                        }
                }
                } catch (Exception e) {
                        logger.log(Level.SEVERE, "Failed to create JSON Object for ContextualTemporalTraversal Transformer", e);
                }
                edgeMap = null;
                logger.log(Level.INFO, "Traversal timespan written to output file. Start time: " + graphMinTime + ", End time: " + graphMaxTime);
                return finalGraph;
        }

}
