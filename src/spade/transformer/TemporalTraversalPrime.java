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
