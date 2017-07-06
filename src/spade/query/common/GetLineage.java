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
            String storage = currentStorage.getClass().getName();
            if(storage.contains("sql"))
            {
                storage = "sql." + storage;
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
            } catch(IllegalAccessException | InstantiationException | ClassNotFoundException ex)
            {
                Logger.getLogger(GetLineage.class.getName()).log(Level.SEVERE, "Unable to create classes for GetLineage!", ex);
                return null;
            }

            Map<String, List<String>> vertexParams = new HashMap<>();
            for(Map.Entry<String, List<String>> entry : parameters.entrySet())
            {
                String key = entry.getKey();
                if(!(key.equals("direction") || key.equals(("maxDepth"))))
                    vertexParams.put(key, entry.getValue());
            }
            int current_depth = 0;
            Set<String> remainingVertices = new HashSet<>();
            Set<String> visitedVertices = new HashSet<>();
            Set<AbstractVertex> startingVertexSet = getVertex.execute(vertexParams, DEFAULT_LIMIT);
            if(!CollectionUtils.isEmpty(startingVertexSet))
            {
                AbstractVertex startingVertex = startingVertexSet.iterator().next();
                remainingVertices.add(startingVertex.bigHashCode());
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
                    if(DIRECTION_ANCESTORS.startsWith(direction.toLowerCase()))
                    {
                        Map<String, List<String>> parentParams = new HashMap<>();
                        parentParams.put(CHILD_VERTEX_KEY, Arrays.asList(OPERATORS.EQUALS, vertexHash, null));
                        neighbors = getParents.execute(parentParams, DEFAULT_LIMIT);
                    }
                    else
                    {
                        Map<String, List<String>> childrenParams = new HashMap<>();
                        childrenParams.put(PARENT_VERTEX_KEY, Arrays.asList(OPERATORS.EQUALS, vertexHash, null));
                        neighbors = getChildren.execute(childrenParams, DEFAULT_LIMIT);
                    }
                    result.vertexSet().addAll(neighbors.vertexSet());
                    result.edgeSet().addAll(neighbors.edgeSet());
                    for(AbstractVertex vertex : neighbors.vertexSet())
                    {
                        String neighborHash = vertex.bigHashCode();
                        if(!visitedVertices.contains(neighborHash))
                        {
                            currentSet.add(neighborHash);
                        }
                        String subtype = vertex.getAnnotation("subtype");
                        if(subtype != null && subtype.equalsIgnoreCase("network"))
                        {
                            setRemoteResolutionRequired();
                            result.putNetworkVertex(vertex, current_depth);
                        }
                        Map<String, List<String>> edgeParams = new HashMap<>();
                        if(DIRECTION_ANCESTORS.startsWith(direction.toLowerCase()))
                        {
                            edgeParams.put(CHILD_VERTEX_KEY, Arrays.asList(OPERATORS.EQUALS, vertexHash));
                            edgeParams.put(PARENT_VERTEX_KEY, Arrays.asList(OPERATORS.EQUALS, neighborHash));
                        }
                        else
                        {
                            edgeParams.put(PARENT_VERTEX_KEY, Arrays.asList(OPERATORS.EQUALS, vertexHash));
                            edgeParams.put(CHILD_VERTEX_KEY, Arrays.asList(OPERATORS.EQUALS, neighborHash));
                        }
                        Set<AbstractEdge> edgeSet = getEdge.execute(edgeParams, DEFAULT_LIMIT);
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
