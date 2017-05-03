package spade.query.sql.postgresql;

import org.apache.commons.collections.CollectionUtils;
import spade.core.AbstractVertex;

import java.util.Map;
import java.util.List;
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
        try
        {
            StringBuilder query = new StringBuilder(100);
            query.append("SELECT * FROM ");
            query.append(VERTEX_TABLE);
            query.append(" WHERE ");
            for (Map.Entry<String, List<String>> entry : parameters.entrySet())
            {
                String colName = entry.getKey();
                List<String> values = entry.getValue();
                query.append(colName);
                query.append(values.get(ARITHMETIC_OPERATOR));
                query.append(values.get(COL_VALUE));
                query.append(" ");
                String boolOperator = values.get(BOOLEAN_OPERATOR);
                if (boolOperator != null)
                    query.append(boolOperator);
            }
            if (limit != null)
                query.append(" LIMIT ").append(limit);

            vertexSet = prepareVertexSetFromSQLResult(query.toString());
            if (!CollectionUtils.isEmpty(vertexSet))
                return vertexSet;
        }
        catch (Exception ex)
        {
            Logger.getLogger(GetVertex.class.getName()).log(Level.SEVERE, "Error creating vertex set!", ex);
        }

        return vertexSet;
    }
}
