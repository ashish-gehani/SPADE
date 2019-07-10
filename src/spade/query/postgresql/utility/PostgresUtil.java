package spade.query.postgresql.utility;

import spade.core.Graph;
import spade.query.graph.kernel.Environment;

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

    public static long getNumVertices(Graph graph)
    {
        long numVertices = 0;
        if(Environment.IsBaseGraph(graph))
        {
            String numVerticesQuery = "SELECT COUNT(*) FROM " + VERTEX_TABLE + ";";
            logger.log(Level.INFO, "Executing query: " + numVerticesQuery);
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
        }
        else
        {
            numVertices = graph.vertexSet().size();
        }
        return numVertices;
    }

    public static long getNumEdges(Graph graph)
    {
        long numEdges = 0;
        if(Environment.IsBaseGraph(graph))
        {
            String numEdgesQuery = "SELECT COUNT(*) FROM " + EDGE_TABLE + ";";
            logger.log(Level.INFO, "Executing query: " + numEdgesQuery);
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
        }
        else
        {
            numEdges = graph.edgeSet().size();
        }
        return numEdges;
    }
}
