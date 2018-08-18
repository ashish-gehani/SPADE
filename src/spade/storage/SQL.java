/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2016 SRI International

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
package spade.storage;

import spade.core.AbstractEdge;
import spade.core.AbstractStorage;
import spade.core.AbstractVertex;
import spade.core.Cache;
import spade.core.Edge;
import spade.core.Graph;
import spade.core.Vertex;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static spade.core.Kernel.DB_ROOT;


/**
 * Basic SQL storage implementation.
 *
 * @author Dawood Tariq, Hasanat Kazmi and Raza Ahmad
 */
public abstract class SQL extends AbstractStorage
{
    protected Connection dbConnection;
    protected HashSet<String> vertexAnnotations;
    protected HashSet<String> edgeAnnotations;
    protected boolean ENABLE_SANITIZATION = true;
    protected static final String VERTEX_TABLE = "vertex";
    protected static final String EDGE_TABLE = "edge";
    protected String DUPLICATE_COLUMN_ERROR_CODE;
    protected int CURSOR_FETCH_SIZE = 0;
    public int MAX_COLUMN_VALUE_LENGTH = 256;

    public SQL()
    {
        logger = Logger.getLogger(SQL.class.getName());
        vertexAnnotations = new HashSet<>();
        edgeAnnotations = new HashSet<>();
    }

    public int getCursorFetchSize()
    {
        return CURSOR_FETCH_SIZE;
    }

    public void setCursorFetchSize(int cursorFetchSize)
    {
        CURSOR_FETCH_SIZE = cursorFetchSize;
    }
    /**
     *  initializes the database and creates the necessary tables
     * if not already present. The necessary tables include VERTEX and EDGE tables
     * to store provenance metadata.
     *
     * @param arguments A string of 4 space-separated tokens used for making a successful
     *                  connection to the database, of the following format:
     *                  'database_path username password'
     *
     *                  Example argument strings are as follows:
     *                  *H2*
     *                  /tmp/spade.sql sa null
     *
     *                  Points to note:
     *                  1. The database driver jar should be present in lib/ in the project's root.
     *                  2. For external databases like MySQL or PostgreSQL, a stand-alone database
     *                  version needs to be installed and executed in parallel, and independent of the
     *                  SPADE kernel.
     *
     * @return  returns true if the connection to database has been successful.
     */
    @Override
    public abstract boolean initialize(String arguments);
    /**
     *  closes the connection to the open database
     * after committing all pending transactions.
     *
     * @return  returns true if the database connection is successfully closed.
     */
    @Override
    public boolean shutdown()
    {
        return true;
    }

    /**
     *  This function cleans the given column name for all characters
     * other than digits and alphabets.
     *
     * @param column The name of column to sanitize.
     *
     * @return  returns the sanitized column name string.
     */
    public static String sanitizeColumn(String column)
    {
        //excludes everything except digits, alphabets and single spaces
        column = column.replaceAll("[^a-zA-Z0-9 ]+", "");
        return column;
    }

    public static String sanitizeString(String string)
    {
        return string.replace("'", "\"");
    }

    public static String stripDashes(String string)
    {
        return string.replace("-", "");
    }

    public static String clipString(String string, int length)
    {
        if(string.length() > length)
            return string.substring(0, length - 1);

        return string;
    }

    /**
     *  adds a new column in the database table,
     * if it is not already present.
     *
     * @param table_name The name of table in database to add column to.
     * @param column_name The name of column to add in the table.
     *
     * @return  returns true if column creation in the database has been successful.
     */
    protected abstract boolean addColumn(String table_name, String column_name);


    /**
     * This function queries the underlying storage and retrieves the edge
     * matching the given criteria.
     *
     * @param childVertexHash      hash of the source vertex.
     * @param parentVertexHash hash of the destination vertex.
     * @return returns edge object matching the given vertices OR NULL.
     */
    @Deprecated
    @Override
    public AbstractEdge getEdge(String childVertexHash, String parentVertexHash)
    {
        if(!Cache.isPresent(childVertexHash) || !Cache.isPresent(parentVertexHash))
        return null;

    AbstractEdge edge = null;
        try
    {
        dbConnection.commit();
        AbstractVertex childVertex = getVertex(childVertexHash);
        AbstractVertex parentVertex = getVertex(parentVertexHash);

        String query = "SELECT * FROM " +
                EDGE_TABLE +
                " WHERE childVertexHash = " +
                childVertexHash +
                " AND parentVertexHash = " +
                parentVertexHash;
        Statement vertexStatement = dbConnection.createStatement();
        ResultSet result = vertexStatement.executeQuery(query);
        ResultSetMetaData metadata = result.getMetaData();
        int columnCount = metadata.getColumnCount();

        Map<Integer, String> columnLabels = new HashMap<>();
        for (int i = 1; i <= columnCount; i++)
        {
            columnLabels.put(i, metadata.getColumnName(i));
        }

        if(result.next())
        {
            edge = new Edge(childVertex, parentVertex);
            for (int i = 1; i <= columnCount; i++)
            {
                String colName = columnLabels.get(i);
                if (colName != null)
                {
                    edge.addAnnotation(colName, result.getString(i));
                }
            }
        }

    }
        catch (Exception ex)
    {
        logger.log(Level.SEVERE, null, ex);
        return null;
    }


        return edge;
}

    /**
     * This function queries the underlying storage and retrieves the vertex
     * matching the given criteria.
     *
     * @param vertexHash hash of the vertex to find.
     * @return returns vertex object matching the given hash OR NULL.
     */
    @Deprecated
    @Override
    public AbstractVertex getVertex(String vertexHash)
    {
        if(!Cache.isPresent(vertexHash))
            return null;

        String query = "SELECT * FROM " +
                VERTEX_TABLE +
                " WHERE " +
                PRIMARY_KEY +
                " = " +
                vertexHash;

        AbstractVertex vertex = null;
        Set<AbstractVertex> vertexSet = prepareVertexSetFromSQLResult(query);
        if(!vertexSet.isEmpty())
            vertex = vertexSet.iterator().next();


        return vertex;
    }

    /**
     * This function finds the children of a given vertex.
     * A child is defined as a vertex which is the source of a
     * direct edge between itself and the given vertex.
     *
     * @param parentHash hash of the given vertex
     * @return returns graph object containing children of the given vertex OR NULL.
     */
    @Deprecated
    @Override
    public Graph getChildren(String parentHash)
    {
        if(!Cache.isPresent(parentHash))
            return null;

        Graph children = null;
        String query = "SELECT * FROM " +
                VERTEX_TABLE +
                " WHERE " +
                PRIMARY_KEY +
                " IN " +
                "(SELECT childVertexHash FROM " +
                EDGE_TABLE +
                " WHERE parentVertexHash = " +
                parentHash +
                ")";

        children.vertexSet().addAll(prepareVertexSetFromSQLResult(query));


        return children;
    }


    /**
     * This helper method queries for a set of vertices from database and
     * returns their set after preparing them in the desired format.
     * @param query
     * @return
     */
    @Deprecated
    private Set<AbstractVertex> prepareVertexSetFromSQLResult(String query)
    {
        Set<AbstractVertex> vertexSet = new HashSet<>();
        try
        {
            dbConnection.commit();
            Statement vertexStatement = dbConnection.createStatement();
            ResultSet result = vertexStatement.executeQuery(query);
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
                    if ((value != null) && !value.isEmpty())
                    {
                        vertex.addAnnotation(columnLabels.get(i), result.getString(i));
                    }
                }
                vertexSet.add(vertex);
            }
        }
        catch (SQLException e)
        {
            e.printStackTrace();
        }

        return vertexSet;
    }

    /**
     * This function finds the parents of a given vertex.
     * A parent is defined as a vertex which is the destination of a
     * direct edge between itself and the given vertex.
     *
     * @param childVertexHash hash of the given vertex
     * @return returns graph object containing parents of the given vertex OR NULL.
     */
    @Deprecated
    @Override
    public Graph getParents(String childVertexHash)
    {
        if(!Cache.isPresent(childVertexHash))
            return null;

        Graph parents = null;
        String query = "SELECT * FROM " +
                VERTEX_TABLE +
                " WHERE " +
                PRIMARY_KEY +
                " IN(" +
                "SELECT parentVertexHash FROM " +
                EDGE_TABLE +
                " WHERE childVertexHash = " +
                childVertexHash +
                ")";

        parents.vertexSet().addAll(prepareVertexSetFromSQLResult(query));


        return parents;
    }

    /**
     * This function inserts the given edge into the underlying storage(s) and
     * updates the cache(s) accordingly.
     *
     * @param incomingEdge edge to insert into the storage
     * @return returns true if the insertion is successful. Insertion is considered
     * not successful if the edge is already present in the storage.
     */
    @Override
    public abstract boolean putEdge(AbstractEdge incomingEdge);

    /**
     * This function inserts the given vertex into the underlying storage(s) and
     * updates the cache(s) accordingly.
     *
     * @param incomingVertex vertex to insert into the storage
     * @return returns true if the insertion is successful. Insertion is considered
     * not successful if the vertex is already present in the storage.
     */
    @Override
    public abstract boolean putVertex(AbstractVertex incomingVertex);

    @Override
    public abstract ResultSet executeQuery(String query);

    public boolean vertexAnnotationIsPresent(String annotation)
    {
        if(vertexAnnotations.contains(annotation))
            return true;
        return false;
    }

    public boolean edgeAnnotationIsPresent(String annotation)
    {
        if(edgeAnnotations.contains(annotation))
            return true;
        return false;
    }

    public boolean addVertexAnnotation(String annotation)
    {
        if(!vertexAnnotationIsPresent(annotation))
        {
            vertexAnnotations.add(annotation);
            return true;
        }
        return false;
    }

    public boolean addEdgeAnnotation(String annotation)
    {
        if(!edgeAnnotationIsPresent(annotation))
        {
            edgeAnnotations.add(annotation);
            return true;
        }
        return false;
    }
}
