package spade.query.neo4j;

import org.apache.commons.collections.CollectionUtils;
import spade.core.AbstractVertex;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author raza
 */
public class GetVertexCypher extends Neo4j<Set<AbstractVertex>, Map<String, List<String>>>
{
    @Override
    public Set<AbstractVertex> execute(Map<String, List<String>> parameters, Integer limit)
    {
        Set<AbstractVertex> vertexSet = null;
        try
        {
            String queryString = prepareGetVertexQuery(parameters, limit);
            vertexSet = prepareVertexSetFromNeo4jResult(queryString);
            if (!CollectionUtils.isEmpty(vertexSet))
                return vertexSet;
        }
        catch (Exception ex)
        {
            Logger.getLogger(GetVertexCypher.class.getName()).log(Level.SEVERE, "Error creating vertex set!", ex);
        }

        return vertexSet;
    }
}
