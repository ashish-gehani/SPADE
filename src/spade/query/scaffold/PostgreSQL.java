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
package spade.query.scaffold;


import au.com.bytecode.opencsv.CSVWriter;
import spade.core.AbstractEdge;
import spade.core.Graph;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static spade.core.Kernel.CONFIG_PATH;
import static spade.core.Kernel.FILE_SEPARATOR;

public class PostgreSQL extends Scaffold
{
    private Logger logger = Logger.getLogger(Scaffold.class.getName());
    private Connection dbConnection;
    private static String PARENTS_TABLE = "parents";
    private static String CHILDREN_TABLE = "children";
    private static String HASH = "hash";
    private static String PARENT_HASH = "parenthash";
    private static String CHILD_HASH = "childhash";

    private boolean bulkUpload = true;
    private static Map<String, String> parentsCache = new HashMap<>(GLOBAL_TX_SIZE+1, 1);
    private static Map<String, String> childrenCache = new HashMap<>(GLOBAL_TX_SIZE+1, 1);
    private static String[] parentsCacheColumns = {HASH, PARENT_HASH};
    private static String[] childrenCacheColumns = {HASH, CHILD_HASH};

    /**
     * This method is invoked by the kernel to initialize the storage.
     *
     * @param arguments The directory path of the scaffold storage.
     * @return True if the storage was initialized successfully.
     */
    @Override
    public boolean initialize(String arguments)
    {
        Properties databaseConfigs = new Properties();
        String configFile = CONFIG_PATH + FILE_SEPARATOR + "spade.storage.PostgreSQL.config";
        try
        {
            databaseConfigs.load(new FileInputStream(configFile));
        }
        catch(IOException ex)
        {
            logger.log(Level.SEVERE, "Loading PostgreSQL configurations from file unsuccessful!", ex);
            return false;
        }

        try
        {
            String databaseURL = "scaffold";
            databaseURL = databaseConfigs.getProperty("databaseURLPrefix") + databaseURL;
            String databaseUsername = "raza";
            String databasePassword = "12345";

            Class.forName(databaseConfigs.getProperty("databaseDriver")).newInstance();
            dbConnection = DriverManager.getConnection(databaseURL, databaseUsername, databasePassword);
            dbConnection.setAutoCommit(false);
        }
        catch(Exception ex)
        {
            logger.log(Level.SEVERE, "Unable to create PostgreSQL scaffold instance!", ex);
            return false;
        }

        try
        {
            Statement dbStatement = dbConnection.createStatement();
            // Create parent table if it does not already exist
            String createParentTable = "CREATE TABLE IF NOT EXISTS "
                    + PARENTS_TABLE
                    + "(\"" + HASH + "\" "
                    + "UUID "
                    + ", "
                    + "\"" + PARENT_HASH + "\" "
                    + "UUID "
                    + ")";
            dbStatement.execute(createParentTable);

            // Create parent table if it does not already exist
            String createChildrenTable = "CREATE TABLE IF NOT EXISTS "
                    + CHILDREN_TABLE
                    + "(\"" + HASH + "\" "
                    + "UUID "
                    + ", "
                    + "\"" + CHILD_HASH + "\" "
                    + "UUID "
                    + ")";
            dbStatement.execute(createChildrenTable);

            dbStatement.close();
            globalTxCheckin(true);

            return true;

        }
        catch (Exception ex)
        {
            logger.log(Level.SEVERE, "Unable to initialize scaffold successfully!", ex);
            return false;
        }
    }

    @Override
    protected void globalTxCheckin(boolean forcedFlush)
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
     * This method is invoked by the AbstractStorage to shut down the storage.
     *
     * @return True if scaffold was shut down successfully.
     */
    @Override
    public boolean shutdown()
    {
        try
        {
            dbConnection.commit();
            dbConnection.close();
        }
        catch (Exception ex)
        {
            logger.log(Level.SEVERE, "Unable to shut down scaffold properly!", ex);
            return false;
        }
        return true;
    }

    @Override
    public Set<String> getChildren(String parentHash)
    {
        return null;
    }

    @Override
    public Set<String> getParents(String childHash)
    {
        return null;
    }

    @Override
    public Set<String> getNeighbors(String hash)
    {
        return null;
    }

    @Override
    public Map<String, Set<String>> getLineage(String hash, String direction, int maxDepth)
    {
        return null;
    }

    @Override
    public Map<String, Set<String>> getPaths(String source_hash, String destination_hash, int maxLength)
    {
        return null;
    }

    /**
     * This function inserts hashes of the end vertices of given edge
     * into the scaffold storage.
     *
     * @param incomingEdge edge whose end points to insert into the storage
     * @return returns true if the insertion is successful. Insertion is considered
     * not successful if the vertex is already present in the storage.
     */
    @Override
    public boolean insertEntry(AbstractEdge incomingEdge)
    {
        if(bulkUpload)
        {
            return processBulkScaffold(incomingEdge);
        }
        try
        {
            StringBuilder parentStringBuilder = new StringBuilder(100);
            parentStringBuilder.append("INSERT INTO ");
            parentStringBuilder.append(PARENTS_TABLE);
            parentStringBuilder.append("(");
            parentStringBuilder.append("\"");
            parentStringBuilder.append(HASH);
            parentStringBuilder.append("\", ");
            parentStringBuilder.append(PARENT_HASH);
            parentStringBuilder.append(") ");
            parentStringBuilder.append("VALUES(");
            parentStringBuilder.append("'");
            parentStringBuilder.append(incomingEdge.getChildVertex().bigHashCode());
            parentStringBuilder.append("', ");
            parentStringBuilder.append("'");
            parentStringBuilder.append(incomingEdge.getParentVertex().bigHashCode());
            parentStringBuilder.append("');");

            Statement statement = dbConnection.createStatement();
            statement.execute(parentStringBuilder.toString());

            StringBuilder childrenStringBuilder = new StringBuilder(100);
            childrenStringBuilder.append("INSERT INTO ");
            childrenStringBuilder.append(CHILDREN_TABLE);
            childrenStringBuilder.append("(");
            childrenStringBuilder.append("\"");
            childrenStringBuilder.append(HASH);
            childrenStringBuilder.append("\", ");
            childrenStringBuilder.append(CHILD_HASH);
            childrenStringBuilder.append(") ");
            childrenStringBuilder.append("VALUES(");
            childrenStringBuilder.append("'");
            childrenStringBuilder.append(incomingEdge.getParentVertex().bigHashCode());
            childrenStringBuilder.append("', ");
            childrenStringBuilder.append("'");
            childrenStringBuilder.append(incomingEdge.getChildVertex().bigHashCode());
            childrenStringBuilder.append("');");

            statement.execute(childrenStringBuilder.toString());
            statement.close();
            globalTxCheckin(false);
        }
        catch(Exception ex)
        {
            logger.log(Level.SEVERE, "Error inserting data into scaffold");
            return false;
        }

        return true;
    }

    private boolean processBulkScaffold(AbstractEdge incomingEdge)
    {
        try
        {
            parentsCache.put(incomingEdge.getChildVertex().bigHashCode(), incomingEdge.getParentVertex().bigHashCode());
            childrenCache.put(incomingEdge.getParentVertex().bigHashCode(), incomingEdge.getChildVertex().bigHashCode());
            globalTxCount++;

            return flushBulkScaffold(false);
        }
        catch(Exception ex)
        {
            logger.log(Level.SEVERE, "Error inserting in scaffold cache!", ex);
        }

        return false;
    }

    private boolean flushBulkScaffold(boolean forcedFlush)
    {
        // bulk inserts into Postgres via COPY command
        try
        {
            // creating parents
            if(( (globalTxCount > 0) && (globalTxCount % GLOBAL_TX_SIZE == 0) ) || forcedFlush)
            {
                String parentsFileName = "/tmp/bulk_parents.csv";
                File parentsFile = new File(parentsFileName);
                parentsFile.getParentFile().mkdirs();
                parentsFile.createNewFile();
                if(!(parentsFile.setWritable(true, false)
                        && parentsFile.setReadable(true, false)))
                {
                    logger.log(Level.SEVERE, "Permission denied to read/write from scaffold parent cache files!");
                    return false;
                }
                FileWriter parentsFileWriter = new FileWriter(parentsFile);
                CSVWriter parentsCSVWriter = new CSVWriter(parentsFileWriter);
                parentsCSVWriter.writeNext(parentsCacheColumns);
                for(Map.Entry<String, String> parentEntry: parentsCache.entrySet())
                {
                    String hash = parentEntry.getKey();
                    String parenthash = parentEntry.getValue();
                    String[] parentEntryValues = {hash, parenthash};
                    parentsCSVWriter.writeNext(parentEntryValues);
                }
                parentsCSVWriter.close();
                parentsCache.clear();
                String copyParentsString = "COPY "
                        + PARENTS_TABLE
                        + " FROM '"
                        + parentsFileName
                        + "' CSV HEADER";

                // creating children
                String childrenFileName = "/tmp/bulk_children.csv";
                File childrenFile = new File(childrenFileName);
                childrenFile.getParentFile().mkdirs();
                childrenFile.createNewFile();
                if(!(childrenFile.setWritable(true, false)
                        && childrenFile.setReadable(true, false)))
                {
                    logger.log(Level.SEVERE, "Permission denied to read/write from scaffold children cache files!");
                    return false;
                }
                FileWriter childrenFileWriter = new FileWriter(childrenFile);
                CSVWriter childrenCSVWriter = new CSVWriter(childrenFileWriter);
                childrenCSVWriter.writeNext(childrenCacheColumns);
                for(Map.Entry<String, String> childEntry: childrenCache.entrySet())
                {
                    String hash = childEntry.getKey();
                    String childhash = childEntry.getValue();
                    String[] childEntryValues = {hash, childhash};
                    childrenCSVWriter.writeNext(childEntryValues);
                }
                childrenCSVWriter.close();
                childrenCache.clear();
                String copyChildrenString = "COPY "
                        + CHILDREN_TABLE
                        + " FROM '"
                        + childrenFileName
                        + "' CSV HEADER";

                Statement statement = dbConnection.createStatement();
                statement.execute(copyParentsString);
                statement.execute(copyChildrenString);
                statement.close();
                globalTxCheckin(true);
            }
        }
        catch(Exception ex)
        {
            logger.log(Level.SEVERE, "Error flushing scaffold cache to storage!", ex);
            return false;
        }

        return true;
    }

    @Override
    public Graph queryManager(Map<String, List<String>> params)
    {
        return null;
    }
}
