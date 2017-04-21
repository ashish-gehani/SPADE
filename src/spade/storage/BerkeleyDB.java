package spade.storage;

import com.sleepycat.je.*;
import spade.core.*;

import com.sleepycat.bind.EntryBinding;
import com.sleepycat.bind.serial.StoredClassCatalog;
import com.sleepycat.bind.serial.SerialBinding;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.sql.ResultSet;
import java.util.logging.Level;
import java.util.logging.Logger;


public class BerkeleyDB extends AbstractStorage
{
    private static Environment myDbEnvironment = null;
    private static Database myDatabase = null;
    private static Database vertexDatabase = null;
    private static Database edgeDatabase = null;

    /**
     * This method is invoked by the kernel to initialize the storage.
     *
     * @param arguments The arguments with which this storage is to be
     *                  initialized.
     * @return True if the storage was initialized successfully.
     */
    @Override
    public boolean initialize(String arguments)
    {
        myDbEnvironment = null;
        myDatabase = null;
        try
        {
            EnvironmentConfig envConfig = new EnvironmentConfig();
            envConfig.setAllowCreate(true);
            envConfig.setTransactional(true);
            myDbEnvironment = new Environment(new File("/tmp/"), envConfig);

            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setAllowCreate(true);
            myDatabase = myDbEnvironment.openDatabase(null, "spade_berkeleyDb", dbConfig);

            // databases to store class information
            vertexDatabase = myDbEnvironment.openDatabase(null, "spade_vertexDb", dbConfig);
            edgeDatabase = myDbEnvironment.openDatabase(null, "edge_vertexDb", dbConfig);

            return true;

        }
        catch(DatabaseException ex)
        {
            Logger.getLogger(BerkeleyDB.class.getName()).log(Level.WARNING, null, ex);
        }

        return false;
    }

    /**
     * This method is invoked by the kernel to shut down the storage.
     *
     * @return True if the storage was shut down successfully.
     */
    @Override
    public boolean shutdown()
    {
        try
        {
            if (myDatabase != null)
                myDatabase.close();
            if (myDbEnvironment != null)
                myDbEnvironment.close();
            return true;
        }
        catch(DatabaseException ex)
        {
            Logger.getLogger(BerkeleyDB.class.getName()).log(Level.WARNING, null, ex);
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
    @Override
    public AbstractEdge getEdge(String childVertexHash, String parentVertexHash)
    {
        AbstractEdge edge = null;
        try
        {
            // Instantiate class catalog
            StoredClassCatalog vertexCatalog = new StoredClassCatalog(vertexDatabase);
            // Create the binding
            EntryBinding vertexBinding = new SerialBinding(vertexCatalog, AbstractVertex.class);
            // Create DatabaseEntry for the key
            DatabaseEntry key = new DatabaseEntry(childVertexHash.getBytes("UTF-8"));
            // Create the DatabaseEntry for the data.
            DatabaseEntry data = new DatabaseEntry();
            myDatabase.get(null, key, data, LockMode.DEFAULT);
            // Recreate the MyData object from the retrieved DatabaseEntry using
            // the EntryBinding created above
            edge = (AbstractEdge) vertexBinding.entryToObject(data);
        }
        catch (UnsupportedEncodingException ex)
        {
            Logger.getLogger(BerkeleyDB.class.getName()).log(Level.WARNING, null, ex);
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
    @Override
    public AbstractVertex getVertex(String vertexHash)
    {
        AbstractVertex vertex = null;
        try
        {
            // Instantiate class catalog
            StoredClassCatalog vertexCatalog = new StoredClassCatalog(vertexDatabase);
            // Create the binding
            EntryBinding vertexBinding = new SerialBinding(vertexCatalog, AbstractVertex.class);
            // Create DatabaseEntry for the key
            DatabaseEntry key = new DatabaseEntry(vertexHash.getBytes("UTF-8"));
            // Create the DatabaseEntry for the data.
            DatabaseEntry data = new DatabaseEntry();
            myDatabase.get(null, key, data, LockMode.DEFAULT);
            // Recreate the MyData object from the retrieved DatabaseEntry using
            // the EntryBinding created above
            vertex = (AbstractVertex) vertexBinding.entryToObject(data);
        }
        catch (UnsupportedEncodingException ex)
        {
            Logger.getLogger(BerkeleyDB.class.getName()).log(Level.WARNING, null, ex);
        }

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
    @Override
    public Graph getChildren(String parentHash) {
        return null;
    }

    /**
     * This function finds the parents of a given vertex.
     * A parent is defined as a vertex which is the destination of a
     * direct edge between itself and the given vertex.
     *
     * @param childVertexHash hash of the given vertex
     * @return returns graph object containing parents of the given vertex OR NULL.
     */
    @Override
    public Graph getParents(String childVertexHash) {
        return null;
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
        try
        {
            // Instantiate class catalog
            StoredClassCatalog edgeCatalog = new StoredClassCatalog(edgeDatabase);
            // Create the binding
            EntryBinding edgeBinding = new SerialBinding(edgeCatalog, AbstractEdge.class);
            // Create DatabaseEntry for the key
            DatabaseEntry key = new DatabaseEntry(incomingEdge.bigHashCode().getBytes("UTF-8"));
            // Create the DatabaseEntry for the data.
            DatabaseEntry data = new DatabaseEntry();
            edgeBinding.objectToEntry(incomingEdge, data);
            // Insert it in database
            myDatabase.put(null, key, data);

            return true;
        }
        catch (UnsupportedEncodingException ex)
        {
            Logger.getLogger(BerkeleyDB.class.getName()).log(Level.WARNING, null, ex);
        }

        return false;
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
        try
        {
            // Instantiate class catalog
            StoredClassCatalog vertexCatalog = new StoredClassCatalog(vertexDatabase);
            // Create the binding
            EntryBinding vertexBinding = new SerialBinding(vertexCatalog, AbstractVertex.class);
            // Create DatabaseEntry for the key
            DatabaseEntry key = new DatabaseEntry(incomingVertex.bigHashCode().getBytes("UTF-8"));
            // Create the DatabaseEntry for the data.
            DatabaseEntry data = new DatabaseEntry();
            vertexBinding.objectToEntry(incomingVertex, data);
            // Insert it in database
            myDatabase.put(null, key, data);

            return true;
        }
        catch (UnsupportedEncodingException ex)
        {
            Logger.getLogger(BerkeleyDB.class.getName()).log(Level.WARNING, null, ex);
        }

        return false;
    }

    @Override
    public ResultSet executeQuery(String query)
    {
        return null;
    }
}
