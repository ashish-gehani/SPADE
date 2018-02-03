package spade.query.scaffold;


import spade.core.AbstractEdge;
import spade.core.Graph;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
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
    private static String PARENT_TABLE = "parents";
    private static String CHILDREN_TABLE = "children";
    private static String HASH = "hash";
    private static String PARENT_HASH = "parenthash";
    private static String CHILD_HASH = "childhash";

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
                    + PARENT_TABLE
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
        try
        {
            StringBuilder parentStringBuilder = new StringBuilder(100);
            parentStringBuilder.append("INSERT INTO ");
            parentStringBuilder.append(PARENT_TABLE);
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
        }


        return false;
    }

    @Override
    public Graph queryManager(Map<String, List<String>> params)
    {
        return null;
    }
}
