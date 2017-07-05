package spade.query.sql.postgresql;

import com.mysql.jdbc.StringUtils;
import org.apache.commons.collections.CollectionUtils;
import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Vertex;
import spade.query.sql.SQL;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static spade.core.AbstractStorage.CHILD_VERTEX_KEY;
import static spade.core.AbstractStorage.PARENT_VERTEX_KEY;
import static spade.core.AbstractStorage.PRIMARY_KEY;

/**
 * @author raza
 */
public abstract class PostgreSQL<R, P> extends SQL<R, P>
{
    protected static final String VERTEX_TABLE = "vertex";
    protected static final String EDGE_TABLE = "edge";

    @Override
    public abstract R execute(P parameters, Integer limit);

    protected Set<AbstractVertex> prepareVertexSetFromSQLResult(String query)
    {
        Set<AbstractVertex> vertexSet = new HashSet<>();
        try
        {
            ResultSet result = (ResultSet) currentStorage.executeQuery(query);
            ResultSetMetaData metadata = result.getMetaData();
            int columnCount = metadata.getColumnCount();

            Map<Integer, String> columnLabels = new HashMap<>();
            for (int i = 1; i <= columnCount; i++)
            {
                columnLabels.put(i, metadata.getColumnName(i));
            }

            while (result.next())
            {
                AbstractVertex vertex = new Vertex();
                for (int i = 1; i <= columnCount; i++)
                {
                    String value = result.getString(i);
                    if (!StringUtils.isNullOrEmpty(value))
                    {
                        vertex.addAnnotation(columnLabels.get(i), result.getString(i));
                    }
                }
                vertexSet.add(vertex);
            }
        }
        catch (SQLException ex)
        {
            Logger.getLogger(PostgreSQL.class.getName()).log(Level.SEVERE, "Vertex set querying unsuccessful!", ex);
        }

        return vertexSet;
    }

    protected Set<AbstractEdge> prepareEdgeSetFromSQLResult(String query)
    {
        Set<AbstractEdge> edgeSet = new HashSet<>();
        try
        {
            ResultSet result = (ResultSet) currentStorage.executeQuery(query);
            ResultSetMetaData metadata = result.getMetaData();
            int columnCount = metadata.getColumnCount();

            Map<Integer, String> columnLabels = new HashMap<>();
            for (int i = 1; i <= columnCount; i++)
            {
                columnLabels.put(i, metadata.getColumnName(i));
            }

            while (result.next())
            {
                Map<String, String> annotations = new HashMap<>();
                for (int i = 1; i <= columnCount; i++)
                {
                    String value = result.getString(i);
                    if (!StringUtils.isNullOrEmpty(value))
                    {
                        String colName = columnLabels.get(i);
                        if (colName != null)
                        {
                            annotations.put(colName, result.getString(i));
                        }
                    }
                }
                GetVertex getVertex = new GetVertex();
                Map<String, List<String>> childMap = new HashMap<>();
                // Note: implicit assumption that CHILD_VERTEX_KEY and PARENT_VERTEX_KEY are present in annotations
                childMap.put(PRIMARY_KEY, new ArrayList<>(
                        Arrays.asList(OPERATORS.EQUALS, annotations.get(CHILD_VERTEX_KEY.toLowerCase()), null)));
                Set<AbstractVertex> childVertexSet = getVertex.execute(childMap, null);
                AbstractVertex childVertex;
                if(!CollectionUtils.isEmpty(childVertexSet))
                    childVertex = childVertexSet.iterator().next();
                else
                    continue;

                Map<String, List<String>> parentMap = new HashMap<>();
                parentMap.put(PRIMARY_KEY, new ArrayList<>(
                        Arrays.asList(OPERATORS.EQUALS, annotations.get(PARENT_VERTEX_KEY.toLowerCase()), null)));
                Set<AbstractVertex> parentVertexSet = getVertex.execute(parentMap, null);
                AbstractVertex parentVertex;
                if(!CollectionUtils.isEmpty(parentVertexSet))
                    parentVertex = parentVertexSet.iterator().next();
                else
                    continue;

                AbstractEdge edge = new Edge(childVertex, parentVertex);
                edge.addAnnotations(annotations);
                edgeSet.add(edge);
            }
        }
        catch (SQLException ex)
        {
            Logger.getLogger(PostgreSQL.class.getName()).log(Level.SEVERE, "Edge set querying unsuccessful!", ex);
        }

        return edgeSet;
    }
}
