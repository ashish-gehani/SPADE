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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Basic SQL storage implementation.
 *
 * @author Dawood Tariq, Hasanat Kazmi and Raza Ahmad
 */
public class SQL extends AbstractStorage
{
    private Connection dbConnection;
    private HashSet<String> vertexAnnotations;
    private HashSet<String> edgeAnnotations;
    private static final boolean ENABLE_SANITIZATION = true;
    private static final String VERTEX_TABLE = "vertex";
    private static final String EDGE_TABLE = "edge";
    private static String DUPLICATE_COLUMN_ERROR_CODE;
    private static final Logger logger = Logger.getLogger(SQL.class.getName());
    /**
     *  initializes the SQL database and creates the necessary tables
     * if not already present. The necessary tables include VERTEX and EDGE tables
     * to store provenance metadata.
     *
     * @param arguments A string of 4 space-separated tokens used for making a successful
     *                  connection to the database, of the following format:
     *                  'driver_name database_URL username password'
     *
     *                  Example argument strings are as follows:
     *                  *H2*
     *                  org.h2.Driver jdbc:h2:/tmp/spade.sql sa null
     *                  *PostgreSQL*
     *                  org.postgresql.Driver jdbc:postgres://localhost/5432/spade_pg.sql root 12345
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
    public boolean initialize(String arguments)
    {
        vertexAnnotations = new HashSet<>();
        edgeAnnotations = new HashSet<>();

        // Arguments consist of 4 space-separated tokens: 'driver URL username password'
        try
        {
            String[] tokens = arguments.split("\\s+");
            String databaseDriver = tokens[0];
            // for postgres, it is jdbc:postgres://localhost/5432/database_name
            // for h2, it is jdbc:h2:/tmp/spade.sql
            String databaseURL = tokens[1];
            String databaseUsername = tokens[2];
            String databasePassword = tokens[3];

            Class.forName(databaseDriver).newInstance();
            dbConnection = DriverManager.getConnection(databaseURL, databaseUsername, databasePassword);
            dbConnection.setAutoCommit(false);

            switch(databaseDriver)
            {
                case("org.postgresql.Driver"):
                    DUPLICATE_COLUMN_ERROR_CODE = "42701";
                    break;
                case "org.mysql.Driver":
                    DUPLICATE_COLUMN_ERROR_CODE = "1060";
                    break;
                default:    // org.h2.Driver
                    DUPLICATE_COLUMN_ERROR_CODE = "42121";
            }


            Statement dbStatement = dbConnection.createStatement();
            // Create vertex table if it does not already exist
            String createVertexTable = "CREATE TABLE IF NOT EXISTS "
                    + VERTEX_TABLE
                    + "(" + PRIMARY_KEY
                    + " "
                    + "UUID PRIMARY KEY " //TODO: add comma and remove comment
//                    + "type VARCHAR(32) NOT NULL "
                    + ")";
            dbStatement.execute(createVertexTable);
            String query = "SELECT * FROM " + VERTEX_TABLE + " WHERE false;";
            dbStatement.execute(query);
            ResultSet result = dbStatement.executeQuery(query);
            ResultSetMetaData metadata = result.getMetaData();
            int columnCount = metadata.getColumnCount();
            for(int i = 1; i <= columnCount; i++)
            {
                vertexAnnotations.add(metadata.getColumnLabel(i));
            }

            String createEdgeTable = "CREATE TABLE IF NOT EXISTS "
                    + EDGE_TABLE
                    + " (" + PRIMARY_KEY
                    + " "
                    + "UUID PRIMARY KEY, "
//                    + "type VARCHAR(32) NOT NULL ," //TODO: remove comment
                    + "childVertexHash UUID NOT NULL, "
                    + "parentHash UUID NOT NULL "
                    + ")";
            dbStatement.execute(createEdgeTable);
            query = "SELECT * FROM " + EDGE_TABLE + " WHERE false;";
            dbStatement.execute(query);
            result = dbStatement.executeQuery(query);
            metadata = result.getMetaData();
            columnCount = metadata.getColumnCount();
            for(int i = 1; i <= columnCount; i++)
            {
                edgeAnnotations.add(metadata.getColumnLabel(i));
            }
            dbStatement.close();

            return true;

        }
        catch (ClassNotFoundException | InstantiationException | IllegalAccessException | SQLException ex)
        {
            logger.log(Level.SEVERE, null, ex);
            return false;
        }
    }

    /**
     *  closes the connection to the open SQL database
     * after committing all pending transactions.
     *
     * @return  returns true if the database connection is successfully closed.
     */
    @Override
    public boolean shutdown()
    {
        try
        {
            dbConnection.commit();
            dbConnection.close();
            return true;
        }
        catch (Exception ex)
        {
            logger.log(Level.SEVERE, null, ex);
            return false;
        }
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
        if (ENABLE_SANITIZATION)
        {
            column = column.replaceAll("[^a-zA-Z0-9]+", "");
        }

        return column;
    }

    public static String sanitizeString(String string)
    {
        return (ENABLE_SANITIZATION) ? string.replace("'", "\"") : string;
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
    private boolean addColumn(String table_name, String column_name)
    {
        // If this column has already been added before for this table, then return
        if ((table_name.equalsIgnoreCase(VERTEX_TABLE)) && vertexAnnotations.contains(column_name))
        {
            return true;
        }
        else if ((table_name.equalsIgnoreCase(EDGE_TABLE)) && edgeAnnotations.contains(column_name))
        {
            return true;
        }

        try
        {
            Statement columnStatement = dbConnection.createStatement();
            String statement = "ALTER TABLE "
                    + table_name
                    + " ADD COLUMN "
                    + column_name
                    + " VARCHAR(256);";
            columnStatement.execute(statement);
            dbConnection.commit();
            columnStatement.close();

            if (table_name.equalsIgnoreCase(VERTEX_TABLE))
            {
                vertexAnnotations.add(column_name);
            }
            else if (table_name.equalsIgnoreCase(EDGE_TABLE))
            {
                edgeAnnotations.add(column_name);
            }

            return true;
        }
        catch (SQLException ex)
        {
            logger.log(Level.WARNING, "Duplicate column found in table", ex);
            if (ex.getSQLState().equals(DUPLICATE_COLUMN_ERROR_CODE))
            {
                if (table_name.equalsIgnoreCase(VERTEX_TABLE))
                {
                    vertexAnnotations.add(column_name);
                }
                else if (table_name.equalsIgnoreCase(EDGE_TABLE))
                {
                    edgeAnnotations.add(column_name);
                }
                return true;
            }
        }
        catch (Exception ex)
        {
            logger.log(Level.SEVERE, null, ex);
            return false;
        }
        return false;
    }


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
                        //TODO: check for src and destination vertices
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
    public boolean putEdge(AbstractEdge incomingEdge)
    {
        //TODO: insert vertex if not present before
        String edgeHash = incomingEdge.bigHashCode();
        if(Cache.isPresent(edgeHash))
            return true;

        String childVertexHash = incomingEdge.getChildVertex().bigHashCode();
        String parentVertexHash = incomingEdge.getParentVertex().bigHashCode();

        // Use StringBuilder to build the SQL insert statement
        StringBuilder insertStringBuilder = new StringBuilder(100);
        insertStringBuilder.append("INSERT INTO ");
        insertStringBuilder.append(EDGE_TABLE);
        insertStringBuilder.append(" (");
        insertStringBuilder.append(PRIMARY_KEY);
        insertStringBuilder.append(", childVertexHash, parentVertexHash, ");
        for (String annotationKey : incomingEdge.getAnnotations().keySet())
        {
            // Sanitize column name to remove special characters
            String newAnnotationKey = sanitizeColumn(annotationKey);

            // As the annotation keys are being iterated, add them as new
            // columns to the table_name if they do not already exist
            addColumn(EDGE_TABLE, newAnnotationKey);

            insertStringBuilder.append("");
            insertStringBuilder.append(newAnnotationKey);
            insertStringBuilder.append(", ");
        }

        // Eliminate the last 2 characters from the string (", ") and begin adding values
        String insertString = insertStringBuilder.substring(0, insertStringBuilder.length() - 2);
        insertStringBuilder = new StringBuilder(insertString + ") VALUES ('");

        // Add the hash code, and source and destination vertex Ids
        insertStringBuilder.append(edgeHash);
        insertStringBuilder.append("', '");
        insertStringBuilder.append(childVertexHash);
        insertStringBuilder.append("', '");
        insertStringBuilder.append(parentVertexHash);
        insertStringBuilder.append("', ");

        // Add the annotation values
        for (String annotationKey : incomingEdge.getAnnotations().keySet())
        {
            String value = (ENABLE_SANITIZATION) ? incomingEdge.getAnnotation(annotationKey).replace("'", "\"") : incomingEdge.getAnnotation(annotationKey);

            insertStringBuilder.append("'");
            insertStringBuilder.append(value);
            insertStringBuilder.append("', ");
        }
        insertString = insertStringBuilder.substring(0, insertStringBuilder.length() - 2) + ")";

        try {
            Statement s = dbConnection.createStatement();
            s.execute(insertString);
            s.close();
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }

        return true;
    }

    /**
     * This function inserts the given vertex into the underlying storage(s) and
     * updates the cache(s) accordingly.
     *
     * @param incomingVertex vertex to insert into the storage
     * @return returns true if the insertion is successful. Insertion is considered
     * not successful if the vertex is already present in the storage.
     */
    @Override
    public boolean putVertex(AbstractVertex incomingVertex)
    {
        //TODO: type should come automatically as the first key-value pair
        String vertexHash = incomingVertex.bigHashCode();
        if(Cache.isPresent(vertexHash))
            return true;

        // Use StringBuilder to build the SQL insert statement
        StringBuilder insertStringBuilder = new StringBuilder( 100);
        insertStringBuilder.append("INSERT INTO ");
        insertStringBuilder.append(VERTEX_TABLE);
        insertStringBuilder.append(" (");
        insertStringBuilder.append(PRIMARY_KEY);
        insertStringBuilder.append(", ");
        for (String annotationKey : incomingVertex.getAnnotations().keySet())
        {
            // Sanitize column name to remove special characters
            String newAnnotationKey = sanitizeColumn(annotationKey);

            // As the annotation keys are being iterated, add them as new
            // columns to the table if they do not already exist
            addColumn(VERTEX_TABLE, newAnnotationKey);

            insertStringBuilder.append(newAnnotationKey);
            insertStringBuilder.append(", ");
        }

        // Eliminate the last 2 characters from the string (", ") and begin adding values
        String insertString = insertStringBuilder.substring(0, insertStringBuilder.length() - 2);
        insertStringBuilder = new StringBuilder(insertString + ") VALUES (");

        // Add the hash code primary key
        insertStringBuilder.append("'");
        insertStringBuilder.append(vertexHash);
        insertStringBuilder.append("', ");

        // Add the annotation values
        for (String annotationKey : incomingVertex.getAnnotations().keySet())
        {
            String value = (ENABLE_SANITIZATION) ? incomingVertex.getAnnotation(annotationKey).replace("'", "\"") :
                    incomingVertex.getAnnotation(annotationKey);

            insertStringBuilder.append("'");
            insertStringBuilder.append(value);
            insertStringBuilder.append("', ");
        }
        insertString = insertStringBuilder.substring(0, insertStringBuilder.length() - 2) + ")";

        try
        {
            dbConnection.commit();
            Statement s = dbConnection.createStatement();
            s.execute(insertString);
            s.close();
        }
        catch (Exception e)
        {
            logger.log(Level.SEVERE, null, e);
            return false;
        }

        // cache the vertex successfully inserted in the storage
        Cache.addItem(incomingVertex);
        return true;
    }

    @Override
    public ResultSet executeQuery(String query)
    {
        ResultSet result = null;
        try
        {
            dbConnection.commit();
            Statement queryStatement = dbConnection.createStatement();
            result = queryStatement.executeQuery(query);
        }
        catch (SQLException ex)
        {
            logger.log(Level.SEVERE, "SQL query execution not successful!", ex);
        }

        return result;
    }

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
