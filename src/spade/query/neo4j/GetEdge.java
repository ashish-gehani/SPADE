package spade.query.neo4j;

import org.apache.commons.collections.CollectionUtils;
import spade.core.AbstractEdge;
import spade.core.Graph;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static spade.storage.Neo4j.RelationshipTypes;

/**
 * @author raza
 */
public class GetEdge extends Neo4j<Set<AbstractEdge>, Map<String, List<String>>>
{
    private static final Logger logger = Logger.getLogger(GetEdge.class.getName());

    @Override
    public Set<AbstractEdge> execute(Map<String, List<String>> parameters, Integer limit)
    {
        Set<AbstractEdge> edgeSet = null;
        try
        {
            StringBuilder edgeQueryBuilder = new StringBuilder(50);
            for (Map.Entry<String, List<String>> entry : parameters.entrySet())
            {
                String colName = entry.getKey();
                List<String> values = entry.getValue();
                edgeQueryBuilder.append(colName);
                edgeQueryBuilder.append(":");
                edgeQueryBuilder.append(values.get(COL_VALUE));
                String boolOperator = values.get(BOOLEAN_OPERATOR);
                if (boolOperator != null)
                    edgeQueryBuilder.append(boolOperator).append(" ");
            }
            spade.storage.Neo4j neo4jStorage = (spade.storage.Neo4j) currentStorage;
            Graph result = neo4jStorage.getEdges(null, null, edgeQueryBuilder.toString());
            edgeSet = result.edgeSet();
            if (!CollectionUtils.isEmpty(edgeSet))
                return edgeSet;
        }
        catch (Exception ex)
        {
            logger.log(Level.SEVERE, "Error creating edge set!", ex);
        }

        return edgeSet;
    }
}
