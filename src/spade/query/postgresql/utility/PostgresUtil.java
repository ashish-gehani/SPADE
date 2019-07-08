package spade.query.postgresql.utility;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static spade.core.AbstractQuery.currentStorage;
import static spade.query.graph.utility.CommonVariables.EDGE_TABLE;
import static spade.query.graph.utility.CommonVariables.VERTEX_TABLE;

public class PostgresUtil
{
    private static Logger logger = Logger.getLogger(PostgresUtil.class.getName());

    public static long getNumVertices()
    {
        String numVerticesQuery = "SELECT COUNT(*) FROM " + VERTEX_TABLE + ";";
        logger.log(Level.INFO, "Executing query: " + numVerticesQuery);
        long numVertices = 0;
        try
        {
            ResultSet result = (ResultSet) currentStorage.executeQuery(numVerticesQuery);
            result.next();
            numVertices = result.getLong(1);
            return numVertices;
        }
        catch(SQLException ex)
        {
            logger.log(Level.SEVERE, "Error executing getNumVertices query", ex);
        }
        return numVertices;
    }

    public static long getNumEdges()
    {
        String numEdgesQuery = "SELECT COUNT(*) FROM " + EDGE_TABLE + ";";
        logger.log(Level.INFO, "Executing query: " + numEdgesQuery);
        long numEdges = 0;
        try
        {
            ResultSet result = (ResultSet) currentStorage.executeQuery(numEdgesQuery);
            result.next();
            numEdges = result.getLong(1);
            return numEdges;
        }
        catch(SQLException ex)
        {
            logger.log(Level.SEVERE, "Error executing getNumEdges query", ex);
        }
        return numEdges;
    }
}
