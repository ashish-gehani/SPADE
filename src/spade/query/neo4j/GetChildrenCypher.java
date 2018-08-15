package spade.query.neo4j;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Result;
import spade.core.Graph;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static spade.storage.Neo4j.convertNodeToVertex;
/**
 * @author raza
 */
public class GetChildrenCypher extends Neo4j<Graph, Map<String, List<String>>>
{
    @Override
    public Graph execute(Map<String, List<String>> parameters, Integer limit)
    {
        String vertexQuery = prepareGetVertexQuery(parameters, limit);
        Result result = (Result) currentStorage.executeQuery(vertexQuery);
        Iterator<Node> nodeSet = result.columnAs(VERTEX_ALIAS);
        Node node;
        if(nodeSet.hasNext())
        {
            // starting point can only be one vertex
            node = nodeSet.next();
        }
        else
            return null;
        Iterable<Relationship> relationships = node.getRelationships(Direction.INCOMING);
        Graph children = new Graph();
        for(Relationship relationship: relationships)
        {
            children.putVertex(convertNodeToVertex(relationship.getEndNode()));
        }

        return children;
    }
}
