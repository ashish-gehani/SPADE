package spade.query.postgresql;

import com.mysql.jdbc.StringUtils;
import org.apache.commons.collections.CollectionUtils;
import spade.core.AbstractEdge;
import spade.core.AbstractQuery;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Vertex;

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
import static spade.storage.SQL.stripDashes;

/**
 * @author raza
 */
public abstract class PostgreSQL<R, P> extends AbstractQuery<R, P>
{
    public static final String VERTEX_TABLE = "vertex";
    public static final String EDGE_TABLE = "edge";

    @Override
    public abstract R execute(P parameters, Integer limit);

    public static Set<AbstractVertex> prepareVertexSetFromSQLResult(String query)
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
                    String colName = columnLabels.get(i);
                    String value = result.getString(i);
                    if (!StringUtils.isNullOrEmpty(value))
                    {
                        if(colName != null && !colName.equals(PRIMARY_KEY))
                        {
                            vertex.addAnnotation(colName, value);
                        }
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

    public static Set<AbstractEdge> prepareEdgeSetFromSQLResult(String query)
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
                    String colName = columnLabels.get(i);
                    String value = result.getString(i);
                    if (!StringUtils.isNullOrEmpty(value))
                    {
                        if (colName != null && !colName.equals(PRIMARY_KEY))
                        {
                            if(colName.equals(CHILD_VERTEX_KEY) || colName.equals(PARENT_VERTEX_KEY))
                            {
                                value = stripDashes(value);
                            }
                            annotations.put(colName, value);
                        }
                    }
                }
                GetVertex getVertex = new GetVertex();
                Map<String, List<String>> childMap = new HashMap<>();
                // Note: implicit assumption that CHILD_VERTEX_KEY and PARENT_VERTEX_KEY are present in annotations
                childMap.put(PRIMARY_KEY, new ArrayList<>(
                        Arrays.asList(OPERATORS.EQUALS, annotations.get(CHILD_VERTEX_KEY), null)));
                Set<AbstractVertex> childVertexSet = getVertex.execute(childMap, null);
                AbstractVertex childVertex;
                if(!CollectionUtils.isEmpty(childVertexSet))
                    childVertex = childVertexSet.iterator().next();
                else
                    continue;

                Map<String, List<String>> parentMap = new HashMap<>();
                parentMap.put(PRIMARY_KEY, new ArrayList<>(
                        Arrays.asList(OPERATORS.EQUALS, annotations.get(PARENT_VERTEX_KEY), null)));
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
