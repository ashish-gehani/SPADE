package spade.query.common;

import org.apache.commons.collections.CollectionUtils;
import spade.core.AbstractEdge;
import spade.core.AbstractQuery;
import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.query.sql.postgresql.GetChildren;
import spade.query.sql.postgresql.GetEdge;
import spade.query.sql.postgresql.GetParents;
import spade.query.sql.postgresql.GetVertex;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.net.NetworkInterface;

import static spade.core.AbstractAnalyzer.setRemoteResolutionRequired;
import static spade.core.AbstractStorage.CHILD_VERTEX_KEY;
import static spade.core.AbstractStorage.DIRECTION;
import static spade.core.AbstractStorage.MAX_DEPTH;
import static spade.core.AbstractStorage.PARENT_VERTEX_KEY;
import static spade.core.AbstractStorage.PRIMARY_KEY;

/**
 * @author raza
 */
public class GetLineage extends AbstractQuery<Graph, Map<String, List<String>>>
{
    public GetLineage()
    {
        register();
    }

    @Override
    public Graph execute(Map<String, List<String>> parameters, Integer limit)
    {
        //TODO: support both directions too
        try
        {
            Graph result = new Graph();
            String storage = currentStorage.getClass().getSimpleName().toLowerCase();
            if(storage.contains("sql"))
            {
                storage = storage + ".postgresql";
            }
            String class_prefix = "spade.query." + storage;
            String direction = parameters.get("direction").get(0);
            Integer maxDepth = Integer.parseInt(parameters.get("maxDepth").get(0));
            result.setMaxDepth(maxDepth);
            GetVertex getVertex;
            GetEdge getEdge;
            GetChildren getChildren;
            GetParents getParents;
            try
            {
                getVertex = (GetVertex) Class.forName(class_prefix + ".GetVertex").newInstance();
                getEdge = (GetEdge) Class.forName(class_prefix + ".GetEdge").newInstance();
                getChildren = (GetChildren) Class.forName(class_prefix + ".GetChildren").newInstance();
                getParents = (GetParents) Class.forName(class_prefix + ".GetParents").newInstance();
            }
            catch(IllegalAccessException | InstantiationException | ClassNotFoundException ex)
            {
                Logger.getLogger(GetLineage.class.getName()).log(Level.SEVERE, "Unable to create classes for GetLineage!", ex);
                return null;
            }

            Map<String, List<String>> vertexParams = new HashMap<>(parameters);
            vertexParams.remove(DIRECTION);
            vertexParams.remove(MAX_DEPTH);
            int current_depth = 0;
            Set<String> remainingVertices = new HashSet<>();
            Set<String> visitedVertices = new HashSet<>();
            Set<AbstractVertex> startingVertexSet = getVertex.execute(vertexParams, DEFAULT_MIN_LIMIT);
            if(!CollectionUtils.isEmpty(startingVertexSet))
            {
                AbstractVertex startingVertex = startingVertexSet.iterator().next();
                startingVertex.setDepth(0);
                remainingVertices.add(startingVertex.bigHashCode());
//                remainingVertices.add(startingVertex.getAnnotation(PRIMARY_KEY));
                result.setRootVertex(startingVertex);
            }
            else
                return null;

            while(!remainingVertices.isEmpty() && current_depth < maxDepth)
            {
                visitedVertices.addAll(remainingVertices);
                Set<String> currentSet = new HashSet<>();
                for(String vertexHash: remainingVertices)
                {
                    Graph neighbors;
                    Map<String, List<String>> params = new HashMap<>();
                    if(DIRECTION_ANCESTORS.startsWith(direction.toLowerCase()))
                    {
                        params.put(CHILD_VERTEX_KEY, Arrays.asList(OPERATORS.EQUALS, vertexHash, null));
                        neighbors = getParents.execute(params, DEFAULT_MAX_LIMIT);
                    }
                    else
                    {
                        params.put(PARENT_VERTEX_KEY, Arrays.asList(OPERATORS.EQUALS, vertexHash, null));
                        neighbors = getChildren.execute(params, DEFAULT_MAX_LIMIT);
                    }
                    for(AbstractVertex V: neighbors.vertexSet())
                		V.setDepth(current_depth+1);
                    result.vertexSet().addAll(neighbors.vertexSet());
                    // empty right now. TODO: make getParents and getChildren return edges too
                    result.edgeSet().addAll(neighbors.edgeSet());
                    for(AbstractVertex vertex : neighbors.vertexSet())
                    {
                        String neighborHash = vertex.bigHashCode();
//                        String neighborHash = vertex.getAnnotation(PRIMARY_KEY);
                        if(!visitedVertices.contains(neighborHash))
                        {
                            currentSet.add(neighborHash);
                        }
                        if(vertex.isNetworkVertex())
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
                        Set<AbstractEdge> edgeSet = getEdge.execute(edgeParams, DEFAULT_MAX_LIMIT);
                        result.edgeSet().addAll(edgeSet);
                    }
                }
                remainingVertices.clear();
                remainingVertices.addAll(currentSet);
                current_depth++;
            }
            result.setComputeTime(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z").format(new Date()));

            return result;
        }
        catch(Exception ex)
        {
            Logger.getLogger(GetLineage.class.getName()).log(Level.SEVERE, "Error executing GetLineage!", ex);
            return null;
        }
    }
}
