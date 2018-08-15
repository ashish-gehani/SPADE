package spade.query.postgresql;

import spade.core.AbstractVertex;
import spade.storage.PostgreSQL;

import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

import static spade.core.AbstractStorage.PRIMARY_KEY;

/**
 * @author raza
 */
public class PutVertex extends spade.query.postgresql.PostgreSQL<Boolean, AbstractVertex>
{
    @Override
    public Boolean execute(AbstractVertex vertex, Integer limit)
    {
        if(vertex == null)
            return false;

        StringBuilder query = new StringBuilder( 100);
        query.append("INSERT INTO ");
        query.append(VERTEX_TABLE);
        query.append(" (");
        query.append(PRIMARY_KEY);  //hash is not part of annotations
        query.append(", ");

        // Add annotation keys
        int i = 0;
        for (Map.Entry<String, String> annotation : vertex.getAnnotations().entrySet())
        {
            String key = annotation.getKey();
            // Sanitize column name to remove special characters
            String colName = PostgreSQL.sanitizeColumn(key);

            // Add column if does not already exist
            AddColumn addColumn = new AddColumn();
            Map<String, String> addColumnParameters = new HashMap<>();
            addColumnParameters.put(VERTEX_TABLE, colName);
            addColumn.execute(addColumnParameters, null);

            query.append(colName);
            i++;
            if(i < vertex.getAnnotations().size())
                query.append(", ");
        }
        Map<String, String> annotations = vertex.getAnnotations();
        annotations.put(PRIMARY_KEY, vertex.bigHashCode());

        query.append(") VALUES (");

        // Add the annotation values
        i = 0;
        for(String colValue: annotations.values())
        {
            query.append("'");
            query.append(PostgreSQL.sanitizeString(colValue));
            query.append("'");
            i++;
            if(i < annotations.size())
                query.append(", ");
        }
        query.append(")");
        ResultSet success = (ResultSet) currentStorage.executeQuery(query.toString());


        if (success != null)
            return true;

        return false;
    }
}
