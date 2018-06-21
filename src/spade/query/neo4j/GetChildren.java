package spade.query.neo4j;

import spade.core.Graph;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static spade.core.AbstractStorage.PARENT_VERTEX_KEY;
import static spade.core.AbstractStorage.PRIMARY_KEY;

/**
 * @author raza
 */
public class GetChildren extends Neo4j<Graph, Map<String, List<String>>>
{
    private static final Logger logger = Logger.getLogger(GetChildren.class.getName());
    @Override
    public Graph execute(Map<String, List<String>> parameters, Integer limit)
    {
        try
        {
            String query = PRIMARY_KEY;
            List<String> values = parameters.get(PARENT_VERTEX_KEY);
            query += ":" ;
            query += values.get(COL_VALUE);

            spade.storage.Neo4j neo4jStorage = (spade.storage.Neo4j) currentStorage;
            Graph children = neo4jStorage.getChildren(query);
            return children;
        }
        catch (Exception ex)
        {
            logger.log(Level.SEVERE, "Error retrieving children!", ex);
            return null;
        }
    }
}
