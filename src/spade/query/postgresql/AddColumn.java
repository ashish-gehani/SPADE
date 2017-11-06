package spade.query.postgresql;

import spade.storage.PostgreSQL;

import java.sql.ResultSet;
import java.util.Map;
/**
 * @author raza
 */
public class AddColumn extends spade.query.postgresql.PostgreSQL<Boolean, Map<String, String>>
{

    @Override
    public Boolean execute(Map<String, String> parameters, Integer limit)
    {
        Map.Entry<String, String> entry = parameters.entrySet().iterator().next();
        String tableName = entry.getKey();
        String colName = entry.getValue();
        PostgreSQL postgresqlStorage = (PostgreSQL) currentStorage;
        // Check if this column already exists for this table
        if ((tableName.equalsIgnoreCase(VERTEX_TABLE)) && postgresqlStorage.vertexAnnotationIsPresent(colName))
            return true;
        else if ((tableName.equalsIgnoreCase(EDGE_TABLE)) && postgresqlStorage.edgeAnnotationIsPresent(colName))
            return true;

        String query = "ALTER TABLE " +
                        tableName +
                        " ADD COLUMN " +
                        colName +
                        " VARCHAR(256);";
        ResultSet success = postgresqlStorage.executeQuery(query);
        if(success == null)
            return false;

        if (tableName.equalsIgnoreCase(VERTEX_TABLE))
        {
            postgresqlStorage.addVertexAnnotation(colName);
        }
        else if (tableName.equalsIgnoreCase(EDGE_TABLE))
        {
            postgresqlStorage.addEdgeAnnotation(colName);
        }

        return true;
    }
}
