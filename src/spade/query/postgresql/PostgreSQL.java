/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2017 SRI International
 This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU General Public License as
 published by the Free Software Foundation, either version 3 of the
 License, or (at your option) any later version.
 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 General Public License for more details.
 You should have received a copy of the GNU General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
 --------------------------------------------------------------------------------
 */
package spade.query.postgresql;

import com.mysql.jdbc.StringUtils;
import org.apache.commons.collections.CollectionUtils;
import spade.core.AbstractEdge;
import spade.core.AbstractQuery;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Graph;
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
import static spade.core.AbstractStorage.DIRECTION_ANCESTORS;
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
                    if (value != null)
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
                    if (value != null)
                    {
                        if (!(colName == null || colName.equals(PRIMARY_KEY) ||
                                colName.equals(CHILD_VERTEX_KEY) ||
                                colName.equals(PARENT_VERTEX_KEY)))
                        {
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

    public static Graph constructGraphFromLineageMap(Map<String, Set<String>> lineageMap, String direction)
    {
        Graph result = new Graph();
        StringBuilder vertexQueryBuilder = new StringBuilder(500);
        StringBuilder edgeQueryBuilder = new StringBuilder(1000);
        try
        {
            boolean edgeFound = false;
            vertexQueryBuilder.append("SELECT * FROM ");
            vertexQueryBuilder.append(VERTEX_TABLE);
            vertexQueryBuilder.append(" WHERE ");
            vertexQueryBuilder.append(PRIMARY_KEY + " IN (");
            edgeQueryBuilder.append("SELECT * FROM ");
            edgeQueryBuilder.append(EDGE_TABLE);
            edgeQueryBuilder.append(" WHERE (");
            String vertexKey;
            String neighborKey;
            if(DIRECTION_ANCESTORS.startsWith(direction.toLowerCase()))
            {
                vertexKey = CHILD_VERTEX_KEY;
                neighborKey = PARENT_VERTEX_KEY;
            }
            else
            {
                vertexKey = PARENT_VERTEX_KEY;
                neighborKey = CHILD_VERTEX_KEY;
            }
            for (Map.Entry<String, Set<String>> entry : lineageMap.entrySet())
            {
                String vertexHash = entry.getKey();
                Set<String> neighbors = entry.getValue();
                vertexQueryBuilder.append("'");
                vertexQueryBuilder.append(vertexHash);
                vertexQueryBuilder.append("'");
                vertexQueryBuilder.append(", ");

                if(neighbors.size() > 0)
                {
                    edgeFound = true;
                    edgeQueryBuilder.append(vertexKey);
                    edgeQueryBuilder.append(AbstractQuery.OPERATORS.EQUALS);
                    edgeQueryBuilder.append("'");
                    edgeQueryBuilder.append(vertexHash);
                    edgeQueryBuilder.append("'");
                    edgeQueryBuilder.append(" AND ");
                    edgeQueryBuilder.append(neighborKey);
                    edgeQueryBuilder.append(" IN (");
                    for(String neighborHash : neighbors)
                    {
                        edgeQueryBuilder.append("'");
                        edgeQueryBuilder.append(neighborHash);
                        edgeQueryBuilder.append("'");
                        edgeQueryBuilder.append(", ");
                    }
                    String edge_query = edgeQueryBuilder.substring(0, edgeQueryBuilder.length() - 2);
                    edgeQueryBuilder = new StringBuilder(edge_query + ")) OR (");
                }
            }
            String vertex_query = vertexQueryBuilder.substring(0, vertexQueryBuilder.length() - 2);
            vertexQueryBuilder = new StringBuilder(vertex_query + ");");
            Set<AbstractVertex> vertexSet = prepareVertexSetFromSQLResult(vertexQueryBuilder.toString());
            result.vertexSet().addAll(vertexSet);

            if(edgeFound)
            {
                String edge_query = edgeQueryBuilder.substring(0, edgeQueryBuilder.length() - 4);
                edgeQueryBuilder = new StringBuilder(edge_query + ";");
                Set<AbstractEdge> edgeSet = prepareEdgeSetFromSQLResult(edgeQueryBuilder.toString());
                result.edgeSet().addAll(edgeSet);
            }
        }
        catch(Exception ex)
        {
            Logger.getLogger(PostgreSQL.class.getName()).log(Level.SEVERE, "Error constructing graph from lineage map!", ex);
            return null;
        }

        return result;
    }

}
