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
package spade.storage;


import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Cache;
import spade.utility.CommonFunctions;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static spade.core.Kernel.CONFIG_PATH;
import static spade.core.Kernel.DB_ROOT;
import static spade.core.Kernel.FILE_SEPARATOR;

public class H2 extends SQL
{
    public H2()
    {
        DUPLICATE_COLUMN_ERROR_CODE = "42121";
        logger = Logger.getLogger(H2.class.getName());
        String configFile = CONFIG_PATH + FILE_SEPARATOR + "spade.storage.H2.config";
        try
        {
            databaseConfigs.load(new FileInputStream(configFile));
        }
        catch(IOException ex)
        {
            String msg  = "Loading H2 configurations from file unsuccessful! Unexpected behavior might follow";
            logger.log(Level.SEVERE, msg, ex);
        }
    }

    /**
     * initializes the H2 database and creates the necessary tables
     * if not already present. The necessary tables include VERTEX and EDGE tables
     * to store provenance metadata.
     *
     * @param arguments A string of 3 space-separated tokens for making a successful connection
     *                  to the database, could be provided in the following format:
     *                  'databasePath databaseUser databasePassword'
     *
     *                  Example argument strings are as follows:
     *                  /tmp/spade.sql sa null
     *
     *                  Points to note:
     *                  1. The database driver jar should be present in lib/ in the project's root.
     * @return returns true if the connection to database has been successful.
     */
    @Override
    public boolean initialize(String arguments)
    {
        try
        {
            Map<String, String> argsMap = CommonFunctions.parseKeyValPairs(arguments);
            // These arguments could be provided: databasePath databaseUsername databasePassword
            String databasePath = (argsMap.get("databasePath") != null) ? argsMap.get("databasePath") :
                    databaseConfigs.getProperty("databasePath");
            String databaseUsername = (argsMap.get("databaseUsername") != null) ? argsMap.get("databaseUsername") :
                    databaseConfigs.getProperty("databaseUsername");
            String databasePassword = (argsMap.get("databasePassword") != null) ? argsMap.get("databasePassword") :
                    databaseConfigs.getProperty("databasePassword");

            String databaseURL = databaseConfigs.getProperty("databaseURLPrefix") + databasePath;

            Class.forName(databaseConfigs.getProperty("databaseDriver")).newInstance();
            dbConnection = DriverManager.getConnection(databaseURL, databaseUsername, databasePassword);
            dbConnection.setAutoCommit(false);
        }
        catch(Exception ex)
        {
            logger.log(Level.SEVERE, "Unable to create H2 class instance!", ex);
            return false;
        }

        try
        {
            Statement dbStatement = dbConnection.createStatement();
            // Create vertex table if it does not already exist
            String createVertexTable = "CREATE TABLE IF NOT EXISTS "
                    + VERTEX_TABLE + "(\""
                    + PRIMARY_KEY + "\" VARCHAR(32) PRIMARY KEY, "
                    + "\"type\" VARCHAR(32) NOT NULL "
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
                    + EDGE_TABLE + " (\""
                    + PRIMARY_KEY + "\" VARCHAR(32) PRIMARY KEY, "
                    + "\"type\" VARCHAR(32) NOT NULL ,"
                    + "\"childVertexHash\" VARCHAR(32) NOT NULL, "
                    + "\"parentVertexHash\" VARCHAR(32) NOT NULL "
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
        catch (Exception ex)
        {
            logger.log(Level.SEVERE, "Unable to initialize storage successfully!", ex);
            return false;
        }
    }

    /**
     * closes the connection to the open H2 database
     * after committing all pending transactions.
     *
     * @return returns true if the database connection is successfully closed.
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
     * adds a new column in the database table,
     * if it is not already present.
     *
     * @param table_name  The name of table in database to add column to.
     * @param column_name The name of column to add in the table.
     * @return returns true if column creation in the database has been successful.
     */
    @Override
    protected boolean addColumn(String table_name, String column_name)
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
                    + " ADD COLUMN \""
                    + column_name
                    + "\" VARCHAR(256);";
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
        String edgeHash = incomingEdge.bigHashCode();
        if(Cache.isPresent(edgeHash))
            return true;

        String childVertexHash = incomingEdge.getChildVertex().bigHashCode();
        String parentVertexHash = incomingEdge.getParentVertex().bigHashCode();

        // Use StringBuilder to build the H2 insert statement
        StringBuilder insertStringBuilder = new StringBuilder(200);
        insertStringBuilder.append("INSERT INTO ");
        insertStringBuilder.append(EDGE_TABLE);
        insertStringBuilder.append(" (");
        insertStringBuilder.append("\"");
        insertStringBuilder.append(PRIMARY_KEY);
        insertStringBuilder.append("\"");
        insertStringBuilder.append(", ");
        if(!incomingEdge.getAnnotations().containsKey(CHILD_VERTEX_KEY))
        {
            insertStringBuilder.append("\"");
            insertStringBuilder.append(CHILD_VERTEX_KEY);
            insertStringBuilder.append("\"");
            insertStringBuilder.append(", ");
        }
        if(!incomingEdge.getAnnotations().containsKey(PARENT_VERTEX_KEY))
        {
            insertStringBuilder.append("\"");
            insertStringBuilder.append(PARENT_VERTEX_KEY);
            insertStringBuilder.append("\"");
            insertStringBuilder.append(", ");
        }
        for (String annotationKey : incomingEdge.getAnnotations().keySet())
        {
            // Sanitize column name to remove special characters
            String newAnnotationKey;
            if(ENABLE_SANITIZATION)
            {
                newAnnotationKey = sanitizeColumn(annotationKey);
            }
            else
                newAnnotationKey = annotationKey;

            // As the annotation keys are being iterated, add them as new
            // columns to the table_name if they do not already exist
            addColumn(EDGE_TABLE, newAnnotationKey);

            insertStringBuilder.append("\"");
            insertStringBuilder.append(newAnnotationKey);
            insertStringBuilder.append("\"");
            insertStringBuilder.append(", ");
        }

        // Eliminate the last 2 characters from the string (", ") and begin adding values
        String insertString = insertStringBuilder.substring(0, insertStringBuilder.length() - 2);
        insertStringBuilder = new StringBuilder(insertString + ") VALUES ('");
        // Add the hash code, and source and destination vertex Ids
        insertStringBuilder.append(edgeHash);
        insertStringBuilder.append("', ");
        if(!incomingEdge.getAnnotations().containsKey(CHILD_VERTEX_KEY))
        {
            insertStringBuilder.append("'");
            insertStringBuilder.append(childVertexHash);
            insertStringBuilder.append("', ");
        }
        if(!incomingEdge.getAnnotations().containsKey(PARENT_VERTEX_KEY))
        {
            insertStringBuilder.append("'");
            insertStringBuilder.append(parentVertexHash);
            insertStringBuilder.append("', ");
        }

        // Add the annotation values
        for (String annotationKey : incomingEdge.getAnnotations().keySet())
        {
            String value = (ENABLE_SANITIZATION) ? incomingEdge.getAnnotation(annotationKey).replace("'", "\"") : incomingEdge.getAnnotation(annotationKey);

            insertStringBuilder.append("'");
            insertStringBuilder.append(value);
            insertStringBuilder.append("', ");
        }
        insertString = insertStringBuilder.substring(0, insertStringBuilder.length() - 2) + ")";

        try
        {
            Statement s = dbConnection.createStatement();
            s.execute(insertString);
            if(BUILD_SCAFFOLD)
            {
                insertScaffoldEntry(incomingEdge);
            }
            s.close();
        }
        catch (Exception e)
        {
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
        String vertexHash = incomingVertex.bigHashCode();
        if(Cache.isPresent(vertexHash))
            return true;

        // Use StringBuilder to build the H2 insert statement
        StringBuilder insertStringBuilder = new StringBuilder( 100);
        insertStringBuilder.append("INSERT INTO ");
        insertStringBuilder.append(VERTEX_TABLE);
        insertStringBuilder.append(" (");
        insertStringBuilder.append("\"");
        insertStringBuilder.append(PRIMARY_KEY);
        insertStringBuilder.append("\"");
        insertStringBuilder.append(", ");
        for (String annotationKey : incomingVertex.getAnnotations().keySet())
        {
            // Sanitize column name to remove special characters
            String newAnnotationKey;
            if(ENABLE_SANITIZATION)
            {
                newAnnotationKey = sanitizeColumn(annotationKey);
            }
            else
                newAnnotationKey = annotationKey;

            // As the annotation keys are being iterated, add them as new
            // columns to the table if they do not already exist
            addColumn(VERTEX_TABLE, newAnnotationKey);

            insertStringBuilder.append("\"");
            insertStringBuilder.append(newAnnotationKey);
            insertStringBuilder.append("\"");
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
            if(CURSOR_FETCH_SIZE > 0)
                queryStatement.setFetchSize(CURSOR_FETCH_SIZE);
            result = queryStatement.executeQuery(query);
        }
        catch (SQLException ex)
        {
            logger.log(Level.SEVERE, "H2 query execution not successful!", ex);
        }

        return result;
    }
}
