package spade.query.common;

import spade.core.*;
import spade.query.sql.postgresql.GetChildren;
import spade.query.sql.postgresql.GetEdge;
import spade.query.sql.postgresql.GetParents;
import spade.query.sql.postgresql.GetVertex;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static spade.core.AbstractAnalyzer.setRemoteFlag;
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
        String storage = currentStorage.getClass().getName();
        if(storage.contains("sql"))
        {
            storage = "sql." + storage;
        }
        String class_prefix = "spade.query." + storage;
        String direction = parameters.get("direction").get(0);
        Integer maxDepth = Integer.parseInt(parameters.get("maxDepth").get(0));
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

        Graph result = new Graph();
        AbstractVertex previousVertex = null;
        AbstractVertex currentVertex = null;
        int current_depth = 0;
        Queue<AbstractVertex> queue = new LinkedList<>();
        Map<String, List<String>> vertexParams = new HashMap<>();
        for(Map.Entry<String, List<String>> entry: parameters.entrySet())
        {
            String key = entry.getKey();
            if(key.equals("direction") || key.equals(("maxDepth")))
                vertexParams.put(key, entry.getValue());
        }
        Set<AbstractVertex> startingVertexSet = (Set<AbstractVertex>) getVertex.execute(vertexParams, 100);
        queue.addAll(startingVertexSet);

        //TODO: keep a visited array
        while(!queue.isEmpty() && current_depth < maxDepth)
        {
            currentVertex = queue.remove();
            String currentHash = currentVertex.getAnnotation(PRIMARY_KEY);
            if(DIRECTION_ANCESTORS.startsWith(direction.toLowerCase()))
            {
                Map<String, List<String>> parentParams = new HashMap<>();
                parentParams.put(CHILD_VERTEX_KEY, Collections.singletonList(currentHash));
                Graph parentGraph = (Graph) getParents.execute(parentParams, 100);
                queue.addAll(parentGraph.vertexSet());
            }
            else if(DIRECTION_DESCENDANTS.startsWith(direction.toLowerCase()))
            {
                Map<String, List<String>> childrenParams = new HashMap<>();
                childrenParams.put(PARENT_VERTEX_KEY, Collections.singletonList(currentHash));
                Graph childrenGraph = (Graph) getChildren.execute(childrenParams, 100);
                queue.addAll(childrenGraph.vertexSet());
            }

            result.putVertex(currentVertex);
            String subtype = currentVertex.getAnnotation("subtype");
            if(subtype != null && subtype.equalsIgnoreCase("network"))
            {
                setRemoteFlag();
                result.putNetworkVertex(currentVertex, current_depth);
            }

            if(previousVertex != null)
            {
                Map<String, List<String>> edgeParams = new HashMap<>();
                edgeParams.put(CHILD_VERTEX_KEY, Collections.singletonList(previousVertex.getAnnotation(PRIMARY_KEY)));
                edgeParams.put(PARENT_VERTEX_KEY, Collections.singletonList(currentHash));
                Set<AbstractEdge> edgeSet = (Set<AbstractEdge>) getEdge.execute(edgeParams, 100);
                result.edgeSet().addAll(edgeSet);
            }

            previousVertex = currentVertex;
            current_depth++;
        }

        return result;
    }
}
