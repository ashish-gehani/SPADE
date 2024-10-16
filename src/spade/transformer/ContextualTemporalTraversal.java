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

package spade.transformer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
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
import spade.reporter.audit.OPMConstants;
import spade.utility.HelperFunctions;

public class TemporalTraversalPrime extends AbstractTransformer{

        private static final Logger logger = Logger.getLogger(TemporalTraversalPrime.class.getName());
        private Boolean outputTime;
        private Double graphMinTime;
        private Double graphMaxTime;
        private String annotationName;


        private BufferedWriter outputWriter;

        // must specify the name of an annotation
        @Override
        public boolean initialize(String arguments){
                Map<String, String> argumentsMap = HelperFunctions.parseKeyValPairs(arguments);
                if("timestamp".equals(argumentsMap.get("order"))){
                        annotationName = "timestampNanos";
                        // annotationName = OPMConstants.EDGE_TIME;
                }else{
                        annotationName = OPMConstants.EDGE_EVENT_ID;
                }


                /*
                * Output Start time and end time to a file for comparisons while merging graphs
                * used specifically for shadewatcher transitive closure merging reimplementation
                *
                */
                outputTime = true;
            if(outputTime == true) {
                try {
                    File file =new File("/tmp/temporal_traversal.json");
                    if(!file.exists()){
                        file.createNewFile();
                    }
                    outputWriter = new BufferedWriter(new FileWriter(file, true));
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Failed to establish writer to /tmp/temporal_traversal.json", e);
                    return false;
                }
            }

                graphMinTime = Double.MAX_VALUE;
                graphMaxTime = Double.MIN_VALUE;
                return true;
        }

        @Override
        public LinkedHashSet<ArgumentName> getArgumentNames(){
                return new LinkedHashSet<ArgumentName>(
                                Arrays.asList(
                                                ArgumentName.SOURCE_GRAPH
                                        )
                                );
        }

        /*
        * Check all edges that have child matching our target Vertex and see their timestamps
        * Find edge with lowest timestamp that is our start time for our traversal
        *
        */
        public Double getMintime(AbstractVertex vertex, HashMap<String, HashMap<String, HashMap<String, AbstractEdge>>> edgeMap){
                Double minTime = Double.MAX_VALUE;

                HashMap<String, AbstractEdge> vertexParentEdges = edgeMap.get(vertex.bigHashCode()).get("parentEdges");

                for (HashMap.Entry<String, AbstractEdge> entry : vertexParentEdges.entrySet()) {
                        AbstractEdge edge = entry.getValue();
                        AbstractEdge newEdge = createNewWithoutAnnotations(edge);
                        try{
                                Double time = Double.parseDouble(getAnnotationSafe(newEdge, annotationName));
                                if(time < minTime) {
                                        minTime = time;
                                }
                        }catch(Exception e){
                                logger.log(Level.SEVERE, "Failed to parse where " + annotationName + "='"
                                                + getAnnotationSafe(newEdge, annotationName) + "'");
                        }
                }

                if (minTime == Double.MAX_VALUE) {
                        minTime = -1.0;
                }
                return minTime;
        }

        /*
        * Get children for a vertex that are from edges with increasing time edges with lesser time will not be considered
        *
        *
        * Note: Passing adjacentGraph to collect all children from multiple vertices from one level into one graph
        */
        public Graph getAllChildrenForAVertex(AbstractVertex vertex, Graph adjacentGraph, Graph finalGraph, Graph graph, Integer levelCount, HashMap<String, HashMap<String, HashMap<String, AbstractEdge>>> edgeMap, HashMap<String, HashMap<String, HashMap<String, AbstractEdge>>> finalEdgeMap){
                Double minTime = -2.0;
                Graph childGraph = null;
                minTime = finalGraph.vertexSet().isEmpty() ? getMintime(vertex, edgeMap) : getMintime(vertex, finalEdgeMap);
                                childGraph = finalGraph.vertexSet().isEmpty() || adjacentGraph == null ? new Graph() : adjacentGraph;
                if (levelCount == 1) {
                        if (minTime < graphMinTime) {
                                graphMinTime = minTime;
                        }
                }
                logger.log(Level.INFO, "Got mintime: " + minTime.toString());

                
                HashMap<String, AbstractEdge> vertexChildEdges = edgeMap.get(vertex.bigHashCode()).get("childEdges");
                HashMap<String, AbstractEdge> vertexParentEdges = edgeMap.get(vertex.bigHashCode()).get("parentEdges");

                for (HashMap.Entry<String, AbstractEdge> entry : vertexChildEdges.entrySet()) {
                        AbstractEdge edge = entry.getValue();
                        AbstractEdge newEdge = createNewWithoutAnnotations(edge);
                        try{
                                Double time = Double.parseDouble(getAnnotationSafe(newEdge, annotationName));
                                if (time > minTime) {
                                        if (time > graphMaxTime) {
                                        graphMaxTime = time;
                                        }
                                        childGraph.putVertex(newEdge.getChildVertex());
                                        childGraph.putVertex(newEdge.getParentVertex());
                                        childGraph.putEdge(newEdge);
                                }
                        }catch(Exception e){
                                logger.log(Level.SEVERE, "Failed to parse where " + annotationName + "='"
                                                + getAnnotationSafe(newEdge, annotationName) + "'");
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
                for(AbstractVertex vertex: graph.vertexSet()) {
                        edgeMap.put(vertex.bigHashCode(), new HashMap<String, HashMap<String, AbstractEdge>>(){{put("parentEdges", new HashMap<String, AbstractEdge>()); put("childEdges", new HashMap<String, AbstractEdge>());}});
                }
                
                for(AbstractEdge edge : graph.edgeSet()) {
                        AbstractVertex edgeChild = edge.getChildVertex();
                        AbstractVertex edgeParent = edge.getParentVertex();
                        final String edgeHash = edge.bigHashCode();
                        edgeMap.get(edgeChild.bigHashCode()).get("parentEdges").put(edgeHash, edge);
                        edgeMap.get(edgeParent.bigHashCode()).get("childEdges").put(edgeHash, edge);
                }

                return edgeMap;
        }



        /**
            *
        */
        @Override
        public Graph transform(Graph graph, ExecutionContext context) {
                Set<AbstractVertex> currentLevel = new HashSet<AbstractVertex>();
                /*
                * Pick a start vertex to begin traversal pass it to transform method in SPADE Query client
                * Example: $1 = $2.transform(TemporalTraversalPrime, "order=timestamp", $startVertex, 'descendant')
                *
                */
                List<AbstractVertex> startGraphVertexSet = new ArrayList<AbstractVertex>(context.getSourceGraph().vertexSet());
                AbstractVertex startVertex = startGraphVertexSet.get(0);

                HashMap<String, HashMap<String, HashMap<String, AbstractEdge>>> edgeMap = setEdgeMap(graph);

                Integer levelCount = 0;

                currentLevel.add(startVertex);
                Graph finalGraph = new Graph();
                logger.log(Level.INFO, "Starting");
                while(!currentLevel.isEmpty()) {
                        logger.log(Level.INFO, "current level is " + String.valueOf(levelCount));
                        Graph adjacentGraph = null;
                        HashMap<String, HashMap<String, HashMap<String, AbstractEdge>>> finalEdgeMap = setEdgeMap(finalGraph);
                        for(AbstractVertex node : currentLevel) {
                                // get children of current level nodes
                                // timestamp of subsequent edges > than current
                                adjacentGraph = getAllChildrenForAVertex(node, adjacentGraph, finalGraph, graph, levelCount, edgeMap, finalEdgeMap);
                        }
                        logger.log(Level.INFO, "children recieved");
                        if(! adjacentGraph.vertexSet().isEmpty()){// If children exists
                                logger.log(Level.INFO, "adding");
                                // Add children in graph and run DFS on children
                                Set<AbstractVertex> nextLevelVertices = new HashSet<AbstractVertex>();
                                nextLevelVertices.addAll(adjacentGraph.vertexSet());
                                nextLevelVertices.removeAll(currentLevel);
                                nextLevelVertices.removeAll(finalGraph.vertexSet());
                                currentLevel.clear();
                                currentLevel.addAll(nextLevelVertices);
                                finalGraph.union(adjacentGraph);
                        } else {
                                logger.log(Level.INFO, "breaking");
                                break;
                        }
                        finalEdgeMap = null;
                        levelCount++;
                }

                try {
                        if (outputTime) {
                        final JSONObject graphTimeSpan = new JSONObject();
                        if (graphMaxTime == Double.MIN_VALUE && graphMinTime == Double.MAX_VALUE) {
                                logger.log(Level.INFO, finalGraph.toString());
                                graphMaxTime = -1.0;
                                graphMinTime = -1.0;
                        } else if (graphMaxTime == Double.MIN_VALUE || graphMinTime == Double.MAX_VALUE) {
                                logger.log(Level.SEVERE, "This shouldn't be happening");
                        }
                        graphTimeSpan.put("start_time", graphMinTime);
                        graphTimeSpan.put("end_time", graphMaxTime);

                        outputWriter.write(graphTimeSpan.toString() + "\n");
                        outputWriter.close();
                        }
                }catch (Exception e) {
                        logger.log(Level.SEVERE, "Failed to create JSON Object for TemporalTraversalPrime Transformer", e);
                }

                edgeMap = null;

                logger.log(Level.INFO, "done writing");

                return finalGraph;
        }

}

