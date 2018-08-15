package spade.query.postgresql;

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
public class GetVertex extends PostgreSQL<Set<AbstractVertex>, Map<String, List<String>>>
{
    public GetVertex()
    {
        register();
    }

    @Override
    public Set<AbstractVertex> execute(Map<String, List<String>> parameters, Integer limit)
    {
        Set<AbstractVertex> vertexSet = null;
        StringBuilder query = new StringBuilder(100);
        try
        {
            query.append("SELECT * FROM ");
            query.append(VERTEX_TABLE);
            query.append(" WHERE ");
            for (Map.Entry<String, List<String>> entry : parameters.entrySet())
            {
                String colName = entry.getKey();
                List<String> values = entry.getValue();
                query.append("\"");
                query.append(colName);
                query.append("\"");
                query.append(values.get(COMPARISON_OPERATOR));
                query.append("'");
                query.append(values.get(COL_VALUE));
                query.append("'");
                query.append(" ");
                String boolOperator = values.get(BOOLEAN_OPERATOR);
                if (boolOperator != null)
                    query.append(boolOperator).append(" ");
            }
            if (limit != null)
                query.append(" LIMIT ").append(limit);
            query.append(";");

            Logger.getLogger(GetVertex.class.getName()).log(Level.INFO, "Following query: " + query.toString());
            vertexSet = prepareVertexSetFromSQLResult(query.toString());
            if (!CollectionUtils.isEmpty(vertexSet))
                return vertexSet;
        }
        catch (Exception ex)
        {
            Logger.getLogger(GetVertex.class.getName()).log(Level.SEVERE, "Error creating vertex set from the following query: \n" + query.toString(), ex);
        }

        return vertexSet;
    }
}
