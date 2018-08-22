/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2017 SRI International
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
package spade.query.common;

import org.apache.commons.collections.CollectionUtils;
import spade.core.AbstractEdge;
import spade.core.AbstractQuery;
import spade.core.AbstractVertex;
import spade.core.Graph;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static spade.core.AbstractAnalyzer.setRemoteResolutionRequired;
import static spade.core.AbstractStorage.CHILD_VERTEX_KEY;
import static spade.core.AbstractStorage.DIRECTION;
import static spade.core.AbstractStorage.DIRECTION_ANCESTORS;
import static spade.core.AbstractStorage.DIRECTION_BOTH;
import static spade.core.AbstractStorage.DIRECTION_DESCENDANTS;
import static spade.core.AbstractStorage.MAX_DEPTH;
import static spade.core.AbstractStorage.PARENT_VERTEX_KEY;

/**
 * @author raza
 */
public class GetLineage extends AbstractQuery<Graph, Map<String, List<String>>>
{
    private AbstractQuery getVertex = null;
    private AbstractQuery getEdge = null;
    private AbstractQuery getChildren = null;
    private AbstractQuery getParents = null;
    private static final Logger logger = Logger.getLogger(GetLineage.class.getName());

    public GetLineage()
    {
        register();
    }

    @Override
    public Graph execute(Map<String, List<String>> parameters, Integer limit)
    {
        try
        {
            Graph result = new Graph();
            String storage = currentStorage.getClass().getSimpleName().toLowerCase();
            String class_prefix = "spade.query." + storage;
            String direction = parameters.get("direction").get(0);
            Integer maxDepth = Integer.parseInt(parameters.get("maxDepth").get(0));
            result.setMaxDepth(maxDepth);

            getVertex = (AbstractQuery) Class.forName(class_prefix + ".GetVertex").newInstance();
            getEdge = (AbstractQuery) Class.forName(class_prefix + ".GetEdge").newInstance();
            getChildren = (AbstractQuery) Class.forName(class_prefix + ".GetChildren").newInstance();
            getParents = (AbstractQuery) Class.forName(class_prefix + ".GetParents").newInstance();

            if(DIRECTION_ANCESTORS.startsWith(direction.toLowerCase()))
            {
                result = execute(parameters, maxDepth, direction, limit);
            }
            else if(DIRECTION_DESCENDANTS.startsWith(direction.toLowerCase()))
            {
                result = execute(parameters, maxDepth, direction, limit);
            }
            else if(DIRECTION_BOTH.startsWith(direction.toLowerCase()))
            {
                direction = DIRECTION_ANCESTORS;
                result = execute(parameters, maxDepth, direction, limit);
                direction = DIRECTION_DESCENDANTS;
                result = Graph.union(result, execute(parameters, maxDepth, direction, limit));
            }
            else
            {
                result = null;
            }

            return result;
        }
        catch(Exception ex)
        {
            logger.log(Level.SEVERE, "Error executing GetLineage setup!", ex);
            return null;
        }
    }

    private Graph execute(Map<String, List<String>> parameters, int maxDepth, String direction, Integer limit)
    {
        Graph result = new Graph();
        try
        {
            Map<String, List<String>> vertexParams = new HashMap<>(parameters);
            vertexParams.remove(DIRECTION);
            vertexParams.remove(MAX_DEPTH);
            int current_depth = 0;
            Set<String> remainingVertices = new HashSet<>();
            Set<String> visitedVertices = new HashSet<>();
            Set<AbstractVertex> startingVertexSet = (Set<AbstractVertex>) getVertex.execute(vertexParams, limit);
            if(!CollectionUtils.isEmpty(startingVertexSet))
            {
                AbstractVertex startingVertex = startingVertexSet.iterator().next();
                startingVertex.setDepth(current_depth);
//                for(AbstractVertex vertex: startingVertexSet)
//                {
//                    remainingVertices.add(vertex.bigHashCode());
//                }
                remainingVertices.add(startingVertex.bigHashCode());
//                remainingVertices.add(startingVertex.getAnnotation(PRIMARY_KEY));
                result.putVertex(startingVertex);
                result.setRootVertex(startingVertex);
            }
            else
            {
                return null;
            }

            while(!remainingVertices.isEmpty() && current_depth < maxDepth)
            {
                current_depth++;
                visitedVertices.addAll(remainingVertices);
                Set<String> currentSet = new HashSet<>();
                for(String vertexHash: remainingVertices)
                {
                    Graph neighbors;
                    Map<String, List<String>> params = new HashMap<>();
                    if(DIRECTION_ANCESTORS.startsWith(direction.toLowerCase()))
                    {
                        params.put(CHILD_VERTEX_KEY, Arrays.asList(OPERATORS.EQUALS, vertexHash, null));
                        neighbors = (Graph) getParents.execute(params, limit);
                    }
                    else
                    {
                        params.put(PARENT_VERTEX_KEY, Arrays.asList(OPERATORS.EQUALS, vertexHash, null));
                        neighbors = (Graph) getChildren.execute(params, limit);
                    }
                    result.vertexSet().addAll(neighbors.vertexSet());
                    // empty right now. TODO: make getParents and getChildren return edges too
                    result.edgeSet().addAll(neighbors.edgeSet());
                    for(AbstractVertex vertex : neighbors.vertexSet())
                    {
                        // for discrepancy check
                        vertex.setDepth(current_depth+1);
                        String neighborHash = vertex.bigHashCode();
//                        String neighborHash = vertex.getAnnotation(PRIMARY_KEY);
                        if(!visitedVertices.contains(neighborHash))
                        {
                            currentSet.add(neighborHash);
                        }
                        if(vertex.isCompleteNetworkVertex())
                        {
                            setRemoteResolutionRequired();
                            result.putNetworkVertex(vertex, current_depth);
                        }
                        Map<String, List<String>> edgeParams = new LinkedHashMap<>();
                        if(DIRECTION_ANCESTORS.startsWith(direction.toLowerCase()))
                        {
                            edgeParams.put(CHILD_VERTEX_KEY, Arrays.asList(OPERATORS.EQUALS, vertexHash, "AND"));
                            edgeParams.put(PARENT_VERTEX_KEY, Arrays.asList(OPERATORS.EQUALS, neighborHash, null));
                        }
                        else
                        {
                            edgeParams.put(PARENT_VERTEX_KEY, Arrays.asList(OPERATORS.EQUALS, vertexHash, "AND"));
                            edgeParams.put(CHILD_VERTEX_KEY, Arrays.asList(OPERATORS.EQUALS, neighborHash, null));
                        }
                        Set<AbstractEdge> edgeSet = (Set<AbstractEdge>) getEdge.execute(edgeParams, limit);
                        result.edgeSet().addAll(edgeSet);
                    }
                }
                remainingVertices.clear();
                remainingVertices.addAll(currentSet);
            }
            result.setComputeTime(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z").format(new Date()));

            return result;
        }
        catch(Exception ex)
        {
            logger.log(Level.SEVERE, "Error executing GetLineage!", ex);
            return null;
        }
    }
}
