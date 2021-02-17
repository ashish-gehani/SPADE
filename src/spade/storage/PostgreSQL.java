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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import au.com.bytecode.opencsv.CSVWriter;
import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Cache;
import spade.core.Settings;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.storage.postgresql.PostgreSQLInstructionExecutor;
import spade.storage.postgresql.PostgreSQLQueryEnvironment;
import spade.utility.HelperFunctions;

/**
 * Basic PostgreSQL storage implementation.
 *
 * @author Dawood Tariq, Hasanat Kazmi and Raza Ahmad
 */
public class PostgreSQL extends SQL
{
	private PostgreSQLInstructionExecutor queryInstructionExecutor = null;
	private PostgreSQLQueryEnvironment queryEnvironment = null;

	private final String baseGraphName = "spade_base_graph";
	private String tableNameBaseVertex = PostgreSQLQueryEnvironment.getVertexTableName(baseGraphName);
    private String tableNameBaseEdge = PostgreSQLQueryEnvironment.getEdgeTableName(baseGraphName);
	
    // Performance tuning note: Set this to higher value (e.g. 100000) to commit less often to db - This increases ingestion rate.
    // Downside: Any external (non atomic) quering to database won't report non-committed data.
    private int GLOBAL_TX_SIZE = 1000;
    // Performance tuning note: This is time in sec that storage is flushed. Increase this to increase throughput / ingestion rate.
    // Downside: Any external (non atomic) quering to database won't report non-committed data.
    private int globalTxCount = 0;
    private final int MAX_WAIT_TIME_BEFORE_FLUSH = 15000; // ms
    private Date lastFlushTime;
    private long edgeBatches = 0;
    private long vertexBatches = 0;
    private boolean bulkUpload = true;
    private boolean setPrimaryKey = false;
    private boolean buildSecondaryIndexes = false;
    
    private boolean reset = false;
    
    private List<Map<String, String>> edgeList = new ArrayList<>();
    private List<Map<String, String>> vertexList = new ArrayList<>();
    private ArrayList<String> edgeColumnNames = new ArrayList<>();
    private ArrayList<String> vertexColumnNames = new ArrayList<>();

    public PostgreSQL()
    {
        DUPLICATE_COLUMN_ERROR_CODE = "42701";
        logger = Logger.getLogger(PostgreSQL.class.getName());
        String configFile = Settings.getDefaultConfigFilePath(this.getClass());
        try
        {
            databaseConfigs.load(new FileInputStream(configFile));
        }
        catch(IOException ex)
        {
            String msg  = "Loading PostgreSQL configurations from file unsuccessful! Unexpected behavior might follow";
            logger.log(Level.SEVERE, msg, ex);
        }
        setPrimaryKey = Boolean.parseBoolean(databaseConfigs.getProperty("setPrimaryKey",
                String.valueOf(setPrimaryKey)));
        buildSecondaryIndexes = Boolean.parseBoolean(databaseConfigs.getProperty("buildSecondaryIndexes",
                String.valueOf(buildSecondaryIndexes)));
        bulkUpload = Boolean.parseBoolean(databaseConfigs.getProperty("bulkUpload", String.valueOf(bulkUpload)));
        reportingEnabled = Boolean.parseBoolean(databaseConfigs.getProperty("reportingEnabled",
                String.valueOf(reportingEnabled)));
        reset = Boolean.parseBoolean(databaseConfigs.getProperty("reset", String.valueOf(reset)));
	GLOBAL_TX_SIZE = Integer.parseInt(databaseConfigs.getProperty("globalTxSize", String.valueOf(GLOBAL_TX_SIZE)));
        if(reportingEnabled)
        {
            reportingInterval = 120;
            reportEveryMs = reportingInterval * 1000; //convert to milliseconds
            startTime = lastReportedTime = System.currentTimeMillis();
            lastReportedVertexCount = lastReportedEdgeCount = 0;
        }
    }

    /**
     *  initializes the PostgreSQL database and creates the necessary tables
     * if not already present. The necessary tables include VERTEX and EDGE tables
     * to store provenance metadata.
     *
     * @param arguments A string of 3 space-separated tokens for making a successful connection
     *                  to the database, could be provided in the following format:
     *                  'database databaseUser databasePassword'
     *
     *                  Example argument strings are as follows:
     *                  spade_pg root 12345
     *
     *                  Points to note:
     *                  1. For external databases like MySQL or PostgreSQL, a stand-alone database
     *                  version needs to be installed and executed in parallel, and independent of the
     *                  SPADE kernel.
     *
     * @return  returns true if the connection to database has been successful.
     */
    @Override
    public boolean initialize(String arguments)
    {
        try
        {
            Map<String, String> argsMap = HelperFunctions.parseKeyValPairs(arguments);
            String database = (argsMap.get("database") != null) ? argsMap.get("database") :
                    databaseConfigs.getProperty("database");
            String databaseUsername = (argsMap.get("databaseUsername") != null) ? argsMap.get("databaseUsername") :
                    databaseConfigs.getProperty("databaseUsername");
            String databasePassword = (argsMap.get("databasePassword") != null) ? argsMap.get("databasePassword") :
                    databaseConfigs.getProperty("databasePassword");

            String databaseURL = databaseConfigs.getProperty("databaseURLPrefix") + database;

            Class.forName(databaseConfigs.getProperty("databaseDriver")).newInstance();
            dbConnection = DriverManager.getConnection(databaseURL, databaseUsername, databasePassword);
            dbConnection.setAutoCommit(false);
        }
        catch(Exception ex)
        {
            logger.log(Level.SEVERE, "Unable to create PostgreSQL class instance!", ex);
            return false;
        }

        if(reset){
        	try(Statement statement = dbConnection.createStatement()){
        		statement.execute("drop table if exists " + VERTEX_TABLE);
        		statement.execute("drop table if exists " + EDGE_TABLE);
        		statement.execute("drop table if exists " + tableNameBaseVertex);
        		statement.execute("drop table if exists " + tableNameBaseEdge);
        	}catch(Exception e){
        		logger.log(Level.SEVERE, "Failed to reset", e);
        		return false;
        	}
        }
        
        try
        {
            Statement dbStatement = dbConnection.createStatement();
            // Create vertex table if it does not already exist
            String createVertexTable = "CREATE TABLE IF NOT EXISTS "
                    + VERTEX_TABLE
                    + "(\"" + PRIMARY_KEY
                    + "\" "
                    + "UUID "
                    + (setPrimaryKey ? "PRIMARY KEY" : "")
                    + ", "
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
                String colName = metadata.getColumnLabel(i);
                vertexColumnNames.add(colName);
                addVertexAnnotation(colName);
            }

            String createEdgeTable = "CREATE TABLE IF NOT EXISTS "
                    + EDGE_TABLE
                    + " (\"" + PRIMARY_KEY
                    + "\" "
                    + "UUID "
                    + (setPrimaryKey ? "PRIMARY KEY" : "")
                    + ", "
                    + "\"type\" VARCHAR(32) NOT NULL ,"
                    + "\"childVertexHash\" UUID NOT NULL, "
                    + "\"parentVertexHash\" UUID NOT NULL "
                    + ")";
            dbStatement.execute(createEdgeTable);
            query = "SELECT * FROM " + EDGE_TABLE + " WHERE false;";
            dbStatement.execute(query);
            result = dbStatement.executeQuery(query);
            metadata = result.getMetaData();
            columnCount = metadata.getColumnCount();
            for(int i = 1; i <= columnCount; i++)
            {
                String colName = metadata.getColumnLabel(i);
                edgeColumnNames.add(colName);
                addEdgeAnnotation(colName);
            }

            String createBaseGraphVertexTable = "CREATE TABLE IF NOT EXISTS "
                    + tableNameBaseVertex
                    + "(\""
                    + PRIMARY_KEY
                    + "\""
                    + " UUID"
                    + ")";
            dbStatement.execute(createBaseGraphVertexTable);

            String createBaseGraphEdgeTable = "CREATE TABLE IF NOT EXISTS "
                    + tableNameBaseEdge
                    + "(\""
                    + PRIMARY_KEY
                    + "\""
                    + " UUID"
                    + ")";
            dbStatement.execute(createBaseGraphEdgeTable);
            
            if(buildSecondaryIndexes)
            {
                String createVertexHashIndex = "CREATE INDEX IF NOT EXISTS hash_index ON vertex USING hash(\"hash\")";
                String createEdgeParentHashIndex = "CREATE INDEX IF NOT EXISTS parentVertexHash_index ON edge USING hash(\"parentVertexHash\")";
                String createEdgeChildHashIndex = "CREATE INDEX IF NOT EXISTS childVertexHash_index ON edge USING hash(\"childVertexHash\")";
                dbStatement.execute(createVertexHashIndex);
                dbStatement.execute(createEdgeParentHashIndex);
                dbStatement.execute(createEdgeChildHashIndex);
            }

            dbStatement.close();
            globalTxCheckin(true);

            return true;

        }
        catch (Exception ex)
        {
            logger.log(Level.SEVERE, "Unable to initialize storage successfully!", ex);
            return false;
        }
    }

    private synchronized void globalTxCheckin(boolean forcedFlush)
    {
        if ((globalTxCount % GLOBAL_TX_SIZE == 0) || (forcedFlush))
        {
            try
            {
                dbConnection.commit();
                globalTxCount = 0;
            }
            catch(SQLException ex)
            {
                logger.log(Level.SEVERE, null, ex);
            }
        }
        else
        {
            globalTxCount++;
        }
    }

    /**
     *  closes the connection to the open PostgreSQL database
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
            if(bulkUpload)
            {
                flushBulkEdges(true);
                flushBulkVertices(true);
            }
            dbConnection.close();
        }
        catch (Exception ex)
        {
            logger.log(Level.SEVERE, null, ex);
            return false;
        }
        return true;
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
    protected boolean addColumn(String table_name, final String column_name)
    {
        // If this column has already been added before for this table, then return
        if ((table_name.equalsIgnoreCase(VERTEX_TABLE)) && vertexAnnotationIsPresent(column_name))
        {
            return true;
        }
        else if ((table_name.equalsIgnoreCase(EDGE_TABLE)) && edgeAnnotationIsPresent(column_name))
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
                    + "\" VARCHAR; ";
            columnStatement.execute(statement);
            columnStatement.close();
            globalTxCheckin(true);

            return true;
        }
        catch (SQLException ex)
        {
            try
            {
                dbConnection.rollback();
            }
            catch(SQLException e)
            {
                logger.log(Level.WARNING, "Duplicate column found in table. Error in rollback!", e);
                return false;
            }
            if (ex.getSQLState().equals(DUPLICATE_COLUMN_ERROR_CODE))
            {
                return true;
            }
        }
        catch (Exception ex)
        {
            logger.log(Level.SEVERE, null, ex);
            return false;
        }
        finally
        {
            if (table_name.equalsIgnoreCase(VERTEX_TABLE))
            {
            	addVertexAnnotation(column_name);
            }
            else if (table_name.equalsIgnoreCase(EDGE_TABLE))
            {
            	addEdgeAnnotation(column_name);
            }
        }

        return false;
    }

    @Override
	public synchronized boolean flushTransactions(boolean force){
    	if(bulkUpload){
    		flushBulkVertices(force);
    		flushBulkEdges(force);
    	}else{
    		globalTxCheckin(force);
    	}
    	return true;
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
    public synchronized boolean storeEdge(AbstractEdge incomingEdge)
    {
        if(bulkUpload)
        {
            processBulkEdges(incomingEdge);
            return true;
        }
        String edgeHash = incomingEdge.bigHashCode();
        if(Cache.isEdgePresent(edgeHash))
            return true;

        String childVertexHash = incomingEdge.getChildVertex().bigHashCode();
        String parentVertexHash = incomingEdge.getParentVertex().bigHashCode();

        // Use StringBuilder to build the PostgreSQL insert statement
        StringBuilder insertStringBuilder = new StringBuilder(200);
        insertStringBuilder.append("INSERT INTO ");
        insertStringBuilder.append(EDGE_TABLE);
        insertStringBuilder.append(" (");
        insertStringBuilder.append("\"");
        insertStringBuilder.append(PRIMARY_KEY);
        insertStringBuilder.append("\"");
        insertStringBuilder.append(", ");
        if(!incomingEdge.getCopyOfAnnotations().containsKey(CHILD_VERTEX_KEY))
        {
            insertStringBuilder.append("\"");
            insertStringBuilder.append(CHILD_VERTEX_KEY);
            insertStringBuilder.append("\"");
            insertStringBuilder.append(", ");
        }
        if(!incomingEdge.getCopyOfAnnotations().containsKey(PARENT_VERTEX_KEY))
        {
            insertStringBuilder.append("\"");
            insertStringBuilder.append(PARENT_VERTEX_KEY);
            insertStringBuilder.append("\"");
            insertStringBuilder.append(", ");
        }
        for (String annotationKey : incomingEdge.getCopyOfAnnotations().keySet())
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
        if(!incomingEdge.getCopyOfAnnotations().containsKey(CHILD_VERTEX_KEY))
        {
            insertStringBuilder.append("'");
            insertStringBuilder.append(childVertexHash);
            insertStringBuilder.append("', ");
        }
        if(!incomingEdge.getCopyOfAnnotations().containsKey(PARENT_VERTEX_KEY))
        {
            insertStringBuilder.append("'");
            insertStringBuilder.append(parentVertexHash);
            insertStringBuilder.append("', ");
        }

        // Add the annotation values
        for (String annotationValue : incomingEdge.getCopyOfAnnotations().keySet())
        {
            String value = (ENABLE_SANITIZATION) ? incomingEdge.getAnnotation(annotationValue).replace("'", "\"") : incomingEdge.getAnnotation(annotationValue);

            insertStringBuilder.append("'");
            insertStringBuilder.append(value);
            insertStringBuilder.append("', ");
        }
        insertString = insertStringBuilder.substring(0, insertStringBuilder.length() - 2) + ")";
        try
        {
            Statement s = dbConnection.createStatement();
            s.execute(insertString);
            s.close();
            globalTxCheckin(false);

            if(BUILD_SCAFFOLD)
            {
                insertScaffoldEntry(incomingEdge);
            }
        }
        catch (Exception e)
        {
            logger.log(Level.SEVERE, null, e);
        }

        if(reportingEnabled)
        {
            computeStats();
        }

        // cache the vertex successfully inserted in the storage
        Cache.addItem(incomingEdge);


        return true;
    }

    private void processBulkEdges(AbstractEdge incomingEdge)
    {
        Map<String, String> annotations = new HashMap<>(incomingEdge.getCopyOfAnnotations());
        annotations.put(PRIMARY_KEY, incomingEdge.bigHashCode());
        annotations.put(CHILD_VERTEX_KEY, incomingEdge.getChildVertex().bigHashCode());
        annotations.put(PARENT_VERTEX_KEY, incomingEdge.getParentVertex().bigHashCode());
        edgeList.add(annotations);
        for(String annotationKey: annotations.keySet())
        {
            if(!edgeColumnNames.contains(annotationKey))
            {
                edgeColumnNames.add(annotationKey);
                addColumn(EDGE_TABLE, annotationKey);
            }
        }

        if(BUILD_SCAFFOLD)
        {
            insertScaffoldEntry(incomingEdge);
        }
        flushBulkEdges(false);
    }

    private synchronized void flushBulkEdges(boolean forcedFlush)
    {
        if(( (edgeCount > 0) && (edgeCount % GLOBAL_TX_SIZE == 0) ) || forcedFlush)
        {
        	StringBuilder edgeHashes = new StringBuilder((int) (edgeCount * 32));
            String edgeFileName = "/tmp/bulk_edges.csv";
            try
            {
                File file = new File(edgeFileName);
                file.createNewFile();
                if(!(file.setWritable(true, false)
                    && file.setReadable(true, false)))
                {
                    logger.log(Level.SEVERE, "Permission denied to read/write from edge buffer files!");
                    return;
                }
                FileWriter fileWriter = new FileWriter(file);

                CSVWriter writer = new CSVWriter(fileWriter);
                writer.writeNext(edgeColumnNames.toArray(new String[edgeColumnNames.size()]));
                for(Map<String, String> edge: edgeList)
                {
                    ArrayList<String> annotationValues = new ArrayList<>();
                    for(String edgeColumnName : edgeColumnNames)
                    {
                    	String annotationValue = edge.get(edgeColumnName);
                        annotationValues.add(annotationValue);
                        if(edgeColumnName.equals(PRIMARY_KEY))
                        {
                            edgeHashes.append("(");
                            edgeHashes.append("'");
                            edgeHashes.append(annotationValue);
                            edgeHashes.append("'), ");
                        }
                    }
                    writer.writeNext(annotationValues.toArray(new String[annotationValues.size()]));
                }
                writer.close();
            }
            catch(Exception ex)
            {
                logger.log(Level.SEVERE, "Error writing edges to file", ex);
            }

            String copyTableString = "COPY "
                    + EDGE_TABLE
                    + " FROM '"
                    + edgeFileName
                    + "' CSV HEADER";
            try
            {
                Statement s = dbConnection.createStatement();
                s.execute(copyTableString);
                if(edgeHashes.length() > 0)
                {
                    String baseGraphEdgeInsert = "INSERT INTO "
                            + tableNameBaseEdge
                            + "(\""
                            + PRIMARY_KEY
                            + "\") VALUES "
                            + edgeHashes.substring(0, edgeHashes.length() - 2);
                    s.execute(baseGraphEdgeInsert);
                }
                s.close();
                globalTxCheckin(true);
                edgeList.clear();
                logger.log(Level.INFO, "Bulk uploaded " + GLOBAL_TX_SIZE + " edges to databases. Total edges: " + edgeCount);
                edgeBatches++;
                long currentTime = System.currentTimeMillis();
                if((currentTime - lastReportedTime) >= reportEveryMs)
                {
                    lastReportedTime = currentTime;
                    logger.log(Level.INFO, "edge batches flushed per " + reportingInterval + "sec: " + edgeBatches);
                    edgeBatches = 0;
                }
            }
            catch (Exception ex)
            {
                logger.log(Level.SEVERE, null, ex);
            }
        }
    }

    private void processBulkVertices(AbstractVertex incomingVertex)
    {
        Map<String, String> annotations = incomingVertex.getCopyOfAnnotations();
        annotations.put(PRIMARY_KEY, incomingVertex.bigHashCode());
        vertexList.add(annotations);
        for(String annotationKey: annotations.keySet())
        {
            if(!vertexColumnNames.contains(annotationKey))
            {
                vertexColumnNames.add(annotationKey);
                addColumn(VERTEX_TABLE, annotationKey);
            }
        }
        flushBulkVertices(false);
    }

    private synchronized void flushBulkVertices(boolean forcedFlush)
    {
        if(( (vertexCount > 0) && (vertexCount % GLOBAL_TX_SIZE == 0) ) || forcedFlush)
        {
        	StringBuilder vertexHashes = new StringBuilder((int) (vertexCount * 32));
            String vertexFileName = "/tmp/bulk_vertices.csv";
            try
            {
                File file = new File(vertexFileName);
                file.createNewFile();
                if(!(file.setWritable(true, false)
                        && file.setReadable(true, false)))
                {
                    logger.log(Level.SEVERE, "Permission denied to read/write from vertex buffer files!");
                    return;
                }
                FileWriter fileWriter = new FileWriter(file);
                CSVWriter writer = new CSVWriter(fileWriter);
                writer.writeNext(vertexColumnNames.toArray(new String[vertexColumnNames.size()]));
                for(Map<String, String> vertex: vertexList)
                {
                    ArrayList<String> annotationValues = new ArrayList<>();
                    for(String vertexColumnName : vertexColumnNames)
                    {
                    	String annotationValue = vertex.get(vertexColumnName);
                        annotationValues.add(annotationValue);
                        if(vertexColumnName.equals(PRIMARY_KEY))
                        {
                            vertexHashes.append("(");
                            vertexHashes.append("'");
                            vertexHashes.append(annotationValue);
                            vertexHashes.append("'), ");
                        }
                    }
                    writer.writeNext(annotationValues.toArray(new String[annotationValues.size()]));
                }
                writer.close();
            }
            catch(Exception ex)
            {
                logger.log(Level.SEVERE, "Error writing vertices to file", ex);
            }

            String copyTableString = "COPY "
                    + VERTEX_TABLE
                    + " FROM '"
                    + vertexFileName
                    + "' CSV HEADER";
            try
            {
                Statement s = dbConnection.createStatement();
                s.execute(copyTableString);
                if(vertexHashes.length() > 0)
                {
                    String baseGraphVertexInsert = "INSERT INTO "
                            + tableNameBaseVertex
                            + "(\""
                            + PRIMARY_KEY
                            + "\") VALUES "
                            + vertexHashes.substring(0, vertexHashes.length() - 2);
                    s.execute(baseGraphVertexInsert);
                }
                s.close();
                globalTxCheckin(true);
                vertexList.clear();
                logger.log(Level.INFO, "Bulk uploaded " + GLOBAL_TX_SIZE + " vertices to databases. Total vertices: " + vertexCount);
                vertexBatches++;
                long currentTime = System.currentTimeMillis();
                if((currentTime - lastReportedTime) >= reportEveryMs)
                {
                    lastReportedTime = currentTime;
                    logger.log(Level.INFO, "vertex batches flushed per " + reportingInterval + "sec: " + vertexBatches);
                    vertexBatches = 0;
                }
            }
            catch (Exception ex)
            {
                logger.log(Level.SEVERE, null, ex);
            }
        }
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
    public synchronized boolean storeVertex(AbstractVertex incomingVertex)
    {
        if(bulkUpload)
        {
            processBulkVertices(incomingVertex);
            return true;
        }
        String vertexHash = incomingVertex.bigHashCode();
        if(Cache.isVertexPresent(vertexHash))
            return true;

        // Use StringBuilder to build the PostgreSQL insert statement
        StringBuilder insertStringBuilder = new StringBuilder( 100);
        insertStringBuilder.append("INSERT INTO ");
        insertStringBuilder.append(VERTEX_TABLE);
        insertStringBuilder.append(" (");
        insertStringBuilder.append("\"");
        insertStringBuilder.append(PRIMARY_KEY);
        insertStringBuilder.append("\"");
        insertStringBuilder.append(", ");
        for (String annotationKey : incomingVertex.getCopyOfAnnotations().keySet())
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
        for (String annotationValue : incomingVertex.getCopyOfAnnotations().keySet())
        {
            String value = (ENABLE_SANITIZATION) ? incomingVertex.getAnnotation(annotationValue).replace("'", "\"") :
                    incomingVertex.getAnnotation(annotationValue);

            insertStringBuilder.append("'");
            insertStringBuilder.append(value);
            insertStringBuilder.append("', ");
        }
        insertString = insertStringBuilder.substring(0, insertStringBuilder.length() - 2) + ")";

        String baseGraphVertexInsert = "INSERT INTO "
                + tableNameBaseVertex
                + "(\""
                + PRIMARY_KEY
                + "\") VALUES ('"
                + incomingVertex.bigHashCode()
                + "')";
        
        try
        {
            Statement s = dbConnection.createStatement();
            s.execute(insertString);
            s.execute(baseGraphVertexInsert);
            s.close();
            globalTxCheckin(false);
        }
        catch (Exception e)
        {
            logger.log(Level.SEVERE, null, e);
            return false;
        }

        if(reportingEnabled)
        {
            computeStats();
        }

        // cache the vertex successfully inserted in the storage
        Cache.addItem(incomingVertex);


        return true;
    }

    @Override
    public synchronized ResultSet executeQuery(String query)
    {
        ResultSet result = null;
        try
        {
            globalTxCheckin(true);
            Statement queryStatement = dbConnection.createStatement();
            if(CURSOR_FETCH_SIZE > 0)
                queryStatement.setFetchSize(CURSOR_FETCH_SIZE);
            result = queryStatement.executeQuery(query);
        }
        catch (SQLException ex)
        {
            logger.log(Level.SEVERE, "PostgreSQL query execution not successful!", ex);
        }

        return result;
    }
    
    private boolean doesVertexColumnExist(String columnName){
    	return vertexColumnNames.contains(columnName);
    }
    
    private boolean doesEdgeColumnExist(String columnName){
    	return edgeColumnNames.contains(columnName);
    }

	public synchronized List<List<String>> executeQueryForResult(String query, boolean addColumnNames){
		Statement queryStatement = null;
		try{
			globalTxCheckin(true);
			queryStatement = dbConnection.createStatement();
			if(CURSOR_FETCH_SIZE > 0){
				queryStatement.setFetchSize(CURSOR_FETCH_SIZE);
			}
			boolean resultIsResultSet = queryStatement.execute(query);
			if(resultIsResultSet){
				ResultSet resultSet = queryStatement.getResultSet();
				if(resultSet != null){
					List<List<String>> listOfList = new ArrayList<List<String>>();
					if(resultSet != null){
						int columnCount = resultSet.getMetaData().getColumnCount();
						if(addColumnNames){
							List<String> heading = new ArrayList<String>();
							listOfList.add(heading);
							for(int i = 0; i < columnCount; i++){
								heading.add(resultSet.getMetaData().getColumnLabel(i+1));
							}
						}
						while(resultSet.next()){
							List<String> sublist = new ArrayList<String>();
							listOfList.add(sublist);
							for(int i = 0; i < columnCount; i++){
								Object cellObject = resultSet.getObject(i + 1);
								if(resultSet.wasNull()){
									sublist.add(null);
								}else{
									sublist.add(String.valueOf(cellObject));
								}
							}
						}
					}
					return listOfList;
				}
			}
			// Check if update count
			List<List<String>> listOfList = new ArrayList<List<String>>();
			List<String> sublist0 = new ArrayList<String>(); sublist0.add("count"); listOfList.add(sublist0);
			List<String> sublist1 = new ArrayList<String>(); sublist1.add(String.valueOf(queryStatement.getUpdateCount())); listOfList.add(sublist1);
			return listOfList;
		}catch(Exception ex){
			logger.log(Level.SEVERE, "PostgreSQL query execution not successful!", ex);
			throw new RuntimeException("Query failed: " + query, ex);
		}finally{
			try{ if(queryStatement != null){queryStatement.close();} }catch(Exception e){}
		}
	}
    
	@Override
	public QueryInstructionExecutor getQueryInstructionExecutor(){
		synchronized(this){
			if(queryEnvironment == null){
				queryEnvironment = new PostgreSQLQueryEnvironment(baseGraphName, this);
				if(reset){
					queryEnvironment.resetWorkspace();
				}else{
					queryEnvironment.initialize();
				}
			}
			if(queryInstructionExecutor == null){
				queryInstructionExecutor = new PostgreSQLInstructionExecutor(
						this, queryEnvironment, 
						PRIMARY_KEY,
						CHILD_VERTEX_KEY,
						PARENT_VERTEX_KEY,
						VERTEX_TABLE,
						EDGE_TABLE);
			}
		}
		return queryInstructionExecutor;
	}
}
