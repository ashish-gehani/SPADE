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

import com.google.common.annotations.VisibleForTesting;
import com.sleepycat.bind.EntryBinding;
import com.sleepycat.bind.serial.SerialBinding;
import com.sleepycat.bind.serial.StoredClassCatalog;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.EnvironmentFailureException;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import org.apache.commons.lang.StringUtils;
import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Graph;

import java.io.File;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static spade.core.AbstractQuery.COL_VALUE;
import static spade.core.AbstractQuery.getCurrentStorage;
import static spade.core.AbstractStorage.CHILD_VERTEX_KEY;
import static spade.core.AbstractStorage.DIRECTION;
import static spade.core.AbstractStorage.DIRECTION_ANCESTORS;
import static spade.core.AbstractStorage.DIRECTION_DESCENDANTS;
import static spade.core.AbstractStorage.MAX_DEPTH;
import static spade.core.AbstractStorage.PARENT_VERTEX_KEY;
import static spade.core.AbstractStorage.PRIMARY_KEY;


/**
 * @author raza
 */
public class BerkeleyDB extends Scaffold
{
    private static final int INITIAL_CACHE_CAPACITY = 500;
    private static final int DEFAULT_ENTRY_SIZE = 200;
    private Map<String, StringBuilder> childListCache = new HashMap<>(INITIAL_CACHE_CAPACITY);
    private Map<String, StringBuilder> parentListCache = new HashMap<>(INITIAL_CACHE_CAPACITY);
    private static final String LIST_SEPARATOR = "-";
    private static final String HASH_SEPARATOR = ",";

    private static long serial_number = 1;
    private long start_time;

    private Environment scaffoldDbEnvironment = null;
    private Database scaffoldDatabase = null;
    private Database neighborDatabase = null;
    private static Logger logger = Logger.getLogger(Scaffold.class.getName());
    private Transaction transaction = null;

    /* For testing purposes. Reads database and outputs. */
    @VisibleForTesting
    public void readData(int limit)
    {
        Cursor cursor = scaffoldDatabase.openCursor(null, null);
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        int i = 0;
        while(cursor.getNext(key, data, LockMode.DEFAULT) == OperationStatus.SUCCESS)
        {
            if(i >= limit)
                break;
            String keyString = new String(key.getData());
            System.out.println("hash: " + keyString);
            i++;
        }
        cursor.close();
    }

    public BerkeleyDB()
    {
        GLOBAL_TX_SIZE = 1000;
    }

    /**
     * This method is invoked by the kernel to initialize the storage.
     *
     * @param arguments The directory path of the scaffold storage.
     * @return True if the storage was initialized successfully.
     */
    @Override
    public boolean initialize(String arguments)
    {
        directoryPath = arguments ;
        scaffoldDbEnvironment = null;
        scaffoldDatabase = null;
        try
        {
            EnvironmentConfig envConfig = new EnvironmentConfig();
            envConfig.setAllowCreate(true);
            envConfig.setTransactional(true);
//            envConfig.setConfigParam(EnvironmentConfig.CHECKPOINTER_BYTES_INTERVAL, "50000000");
//            envConfig.setDurability(Durability.COMMIT_NO_SYNC);
//            envConfig.setDurability(Durability.COMMIT_WRITE_NO_SYNC);
            scaffoldDbEnvironment = new Environment(new File(directoryPath), envConfig);

            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setAllowCreate(true);
            dbConfig.setTransactional(true);
            scaffoldDatabase = scaffoldDbEnvironment.openDatabase(null, "scaffold_berkeleydb", dbConfig);

            // databases to store class information
            neighborDatabase = scaffoldDbEnvironment.openDatabase(null, "neighbor_berkeleydb", dbConfig);

            globalTxCheckin(true);
            start_time = System.nanoTime();

            return true;
        }
        catch(Exception ex)
        {
            logger.log(Level.SEVERE, null, ex);
        }

        return false;
    }

    @Override
    protected void globalTxCheckin(boolean forcedFlush)
    {
        if ((globalTxCount % GLOBAL_TX_SIZE == 0) || (forcedFlush))
        {
            try
            {
                if(transaction != null)
                    transaction.commit();
                globalTxCount = 0;
                transaction = scaffoldDbEnvironment.beginTransaction(null, null);
            }
            catch(Exception ex)
            {
                logger.log(Level.SEVERE, "Error committing transactions!", ex);
            }
        }
        else
        {
            globalTxCount++;
        }
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
            if (scaffoldDatabase != null)
                scaffoldDatabase.close();
            if(neighborDatabase != null)
                neighborDatabase.close();
            if (scaffoldDbEnvironment != null)
                scaffoldDbEnvironment.close();

            return true;
        }
        catch(DatabaseException ex)
        {
            logger.log(Level.SEVERE, "Database and environment closure error!", ex);
        }

        return false;
    }

    @Override
    public Set<String> getChildren(String parentHash)
    {
        try
        {
            // Instantiate class catalog
            StoredClassCatalog neighborCatalog = new StoredClassCatalog(neighborDatabase);
            // Create the binding
            EntryBinding<Neighbors> neighborBinding = new SerialBinding<>(neighborCatalog, Neighbors.class);
            // Create DatabaseEntry for the key
            DatabaseEntry key = new DatabaseEntry(parentHash.getBytes("UTF-8"));
            // Create the DatabaseEntry for the data.
            DatabaseEntry data = new DatabaseEntry();
            // query database to get the key-value
            OperationStatus operationStatus = scaffoldDatabase.get(null, key, data, LockMode.DEFAULT);
            if(operationStatus != OperationStatus.NOTFOUND)
            {
                Neighbors neighbors = neighborBinding.entryToObject(data);
                return neighbors.children;
            }
        }
        catch (UnsupportedEncodingException ex)
        {
            logger.log(Level.SEVERE, "Scaffold entry insertion error!", ex);
        }
        return null;
    }

    @Override
    public Set<String> getParents(String childHash)
    {
        try
        {
            // Instantiate class catalog
            StoredClassCatalog neighborCatalog = new StoredClassCatalog(neighborDatabase);
            // Create the binding
            EntryBinding<Neighbors> neighborBinding = new SerialBinding<>(neighborCatalog, Neighbors.class);
            // Create DatabaseEntry for the key
            DatabaseEntry key = new DatabaseEntry(childHash.getBytes("UTF-8"));
            // Create the DatabaseEntry for the data.
            DatabaseEntry data = new DatabaseEntry();
            // query database to get the key-value
            OperationStatus operationStatus = scaffoldDatabase.get(null, key, data, LockMode.DEFAULT);
            if(operationStatus != OperationStatus.NOTFOUND)
            {
                Neighbors neighbors = neighborBinding.entryToObject(data);
                return neighbors.parents;
            }
        }
        catch (UnsupportedEncodingException ex)
        {
            logger.log(Level.SEVERE, "Scaffold entry insertion error!", ex);
        }
        return null;
    }

    @Override
    public Set<String> getNeighbors(String hash)
    {
        Set<String> neighbors = getChildren(hash);
        neighbors.addAll(getParents(hash));

        return neighbors;
    }

    @Override
    public Map<String, Set<String>> getLineage(String hash, String direction, int maxDepth)
    {
        try
        {
            // Instantiate class catalog
            StoredClassCatalog neighborCatalog = new StoredClassCatalog(neighborDatabase);
            // Create the binding
            EntryBinding<Neighbors> neighborBinding = new SerialBinding<>(neighborCatalog, Neighbors.class);
            // Create DatabaseEntry for the key
            DatabaseEntry key = new DatabaseEntry(hash.getBytes("UTF-8"));
            // Create the DatabaseEntry for the data.
            DatabaseEntry data = new DatabaseEntry();
            // query database to get the key-value
            OperationStatus operationStatus = scaffoldDatabase.get(null, key, data, LockMode.DEFAULT);
            if(operationStatus != OperationStatus.NOTFOUND)
            {
                Set<String> remainingVertices = new HashSet<>();
                Set<String> visitedVertices = new HashSet<>();
                Map<String, Set<String>> lineageMap = new HashMap<>();
                remainingVertices.add(hash);
                int current_depth = 0;
                while(!remainingVertices.isEmpty() && current_depth < maxDepth)
                {
                    visitedVertices.addAll(remainingVertices);
                    Set<String> currentSet = new HashSet<>();
                    for(String current_hash: remainingVertices)
                    {
                        Set<String> neighbors = null;
                        if(DIRECTION_ANCESTORS.startsWith(direction.toLowerCase()))
                            neighbors = getParents(current_hash);
                        else if(DIRECTION_DESCENDANTS.startsWith(direction.toLowerCase()))
                            neighbors = getChildren(current_hash);

                        if(neighbors != null)
                        {
                            lineageMap.put(current_hash, neighbors);
                            for(String vertexHash: neighbors)
                            {
                                if(!visitedVertices.contains(vertexHash))
                                {
                                    currentSet.addAll(neighbors);
                                }
                            }
                        }
                    }
                    remainingVertices.clear();
                    remainingVertices.addAll(currentSet);
                    current_depth++;
                }

                return lineageMap;
            }
        }
        catch(UnsupportedEncodingException ex)
        {
            logger.log(Level.SEVERE, "Scaffold Get Lineage error!", ex);
        }

        return null;
    }

    @Override
    public Map<String, Set<String>> getPaths(String source_hash, String destination_hash, int maxLength)
    {

        Map<String, Set<String>> lineageUp = getLineage(source_hash, DIRECTION_ANCESTORS, maxLength);
        Map<String, Set<String>> lineageDown = getLineage(destination_hash, DIRECTION_DESCENDANTS, maxLength);
        Map<String, Set<String>> paths = new HashMap<>();
        Set<String> keys = lineageUp.keySet();
        keys.retainAll(lineageDown.keySet());

        for(String key: keys)
        {
            Set<String> pathEntry = paths.get(key);
            if(pathEntry == null)
            {
                pathEntry = new HashSet<>();
            }
            pathEntry.addAll(lineageUp.get(key));
            pathEntry.addAll(lineageDown.get(key));
        }

        return paths;
    }

    public boolean flushBulkEntries(boolean forcedFlush)
    {
        try
        {
            if((globalTxCount % GLOBAL_TX_SIZE == 0) || forcedFlush)
            {
                Transaction transaction = scaffoldDbEnvironment.beginTransaction(null, null);
                Set<String> hashList = new HashSet<>(childListCache.keySet());
                hashList.addAll(parentListCache.keySet());
                for(String hash: hashList)
                {
                    byte[] hashBytes = hash.getBytes("UTF-8");
                    StringBuilder childCacheBuilder = childListCache.get(hash);
                    String childCache = "";
                    if(childCacheBuilder != null)
                    {
                        childCache = childCacheBuilder.toString();
                    }
                    StringBuilder parentCacheBuilder = parentListCache.get(hash);
                    String parentCache = "";
                    if(parentCacheBuilder != null)
                    {
                        parentCache = parentCacheBuilder.toString();
                    }

                    // DatabaseEntry for the key
                    DatabaseEntry key = new DatabaseEntry(hashBytes);
                    // DatabaseEntry for the data.
                    DatabaseEntry data = new DatabaseEntry();
                    // query database to get the key-value
                    OperationStatus operationStatus = scaffoldDatabase.get(null, key, data, LockMode.DEFAULT);

                    String newScaffoldEntry;
                    if(operationStatus.equals(OperationStatus.NOTFOUND))
                    {
                        Set<String> childHashSet = new HashSet<>(Arrays.asList(childCache.split(HASH_SEPARATOR)));
                        Set<String> parentHashSet = new HashSet<>(Arrays.asList(parentCache.split(HASH_SEPARATOR)));
                        newScaffoldEntry = StringUtils.join(childHashSet, HASH_SEPARATOR) + LIST_SEPARATOR +
                                StringUtils.join(parentHashSet, HASH_SEPARATOR);
                    }
                    else
                    {
                        String currentScaffoldEntry = new String(data.getData(), "UTF-8");
                        String[] neighborHashList = currentScaffoldEntry.split(LIST_SEPARATOR, -1);
                        String childrenHashList = neighborHashList[0];
                        String parentHashList = neighborHashList[1];
                        Set<String> cachedChildrenHashSet = new HashSet<>(Arrays.asList(childCache.split(HASH_SEPARATOR)));
                        Set<String> cachedParentHashSet = new HashSet<>(Arrays.asList(parentCache.split(HASH_SEPARATOR)));

                        Set<String> currentChildrenHashSet = new HashSet<>(Arrays.asList(childrenHashList.split(HASH_SEPARATOR)));
                        Set<String> currentParentHashSet = new HashSet<>(Arrays.asList(parentHashList.split(HASH_SEPARATOR)));
                        currentChildrenHashSet.addAll(cachedChildrenHashSet);
                        currentParentHashSet.addAll(cachedParentHashSet);

                        newScaffoldEntry = StringUtils.join(currentChildrenHashSet, HASH_SEPARATOR) + LIST_SEPARATOR +
                                StringUtils.join(currentParentHashSet, HASH_SEPARATOR);
                    }
                    data = new DatabaseEntry(newScaffoldEntry.getBytes("UTF-8"));
                    scaffoldDatabase.put(null, key, data);
                }
                transaction.commit();
                childListCache.clear();
                parentListCache.clear();
            }
        }
        catch(Exception ex)
        {
            logger.log(Level.SEVERE, "Error bulk flushing cached entries to scaffold!", ex);
            return false;
        }

        return true;
    }

    public boolean insertEntry1(AbstractEdge incomingEdge)
    {
        try
        {
            String childVertexHash = incomingEdge.getChildVertex().bigHashCode();
            String parentVertexHash = incomingEdge.getParentVertex().bigHashCode();
            /*
                processing child vertex
             */
            StringBuilder childList = childListCache.get(childVertexHash);
            if(childList == null)
            {
                childList = new StringBuilder(DEFAULT_ENTRY_SIZE);
                childList.append(parentVertexHash);
                childListCache.put(childVertexHash, childList);
            }
            else
            {
                childList.append(HASH_SEPARATOR).append(parentVertexHash);
            }

            /*
                processing parent vertex
             */
            StringBuilder parentList = parentListCache.get(parentVertexHash);
            if(parentList == null)
            {
                parentList = new StringBuilder(DEFAULT_ENTRY_SIZE);
                parentList.append(childVertexHash);
                parentListCache.put(parentVertexHash, parentList);
            }
            else
            {
                parentList.append(HASH_SEPARATOR).append(childVertexHash);
            }

            // increment count
            globalTxCount++;
            flushBulkEntries(false);
        }
        catch(Exception ex)
        {
            logger.log(Level.SEVERE, "Error committing transactions!", ex);
            return false;
        }

        return true;
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
        AbstractVertex childVertex = incomingEdge.getChildVertex();
        AbstractVertex parentVertex = incomingEdge.getParentVertex();
        String childHash = incomingEdge.getAnnotations().containsKey(CHILD_VERTEX_KEY) ?
                incomingEdge.getAnnotation(CHILD_VERTEX_KEY) : childVertex.bigHashCode();
        String parentHash = incomingEdge.getAnnotations().containsKey(PARENT_VERTEX_KEY) ?
                incomingEdge.getAnnotation(PARENT_VERTEX_KEY) : parentVertex.bigHashCode();
        try
        {
            // Instantiate class catalog
            StoredClassCatalog neighborCatalog = new StoredClassCatalog(neighborDatabase);
            // Create the binding
            EntryBinding<Neighbors> neighborBinding = new SerialBinding<>(neighborCatalog, Neighbors.class);
            // Create DatabaseEntry for the key
            DatabaseEntry key = new DatabaseEntry(childHash.getBytes("UTF-8"));
            // Create the DatabaseEntry for the data.
            DatabaseEntry data = new DatabaseEntry();
            // query database to get the key-value
            OperationStatus operationStatus = scaffoldDatabase.get(null, key, data, LockMode.DEFAULT);
            addItem(operationStatus, PARENTS , key, data, neighborBinding, parentHash);

            // now do the reverse too
            key = new DatabaseEntry(parentHash.getBytes("UTF-8"));
            data = new DatabaseEntry();
            operationStatus = scaffoldDatabase.get(null, key, data, LockMode.DEFAULT);
            addItem(operationStatus, CHILDREN, key, data, neighborBinding, childHash);

            // stats calculation
            serial_number++;
            if(serial_number % GLOBAL_TX_SIZE == 0)
            {
                long elapsed_time = System.nanoTime() - start_time;
                String msg = "Items Inserted: " + serial_number + ". Time Duration: " + elapsed_time / 1000000000.0;
                logger.log(Level.INFO, msg);
                start_time = System.nanoTime();
            }

            return true;
        }
        catch (UnsupportedEncodingException ex)
        {
            logger.log(Level.SEVERE, "Scaffold entry insertion error!", ex);
        }

        return false;
    }

    /**
     * Helper method to add an item to the storage
     * @param status status of last get operation at the caller
     * @param direction wither parent or children
     * @param key key of the element to insert data into
     * @param neighborBinding binding object to bind the data object to
     * @param hash hash of the parent or child to add to the data list
     */
    private void addItem(OperationStatus status, String direction, DatabaseEntry key, DatabaseEntry data,
                         EntryBinding<Neighbors> neighborBinding, String hash)
    {
        Neighbors neighbors;
        if(status == OperationStatus.NOTFOUND)
        {
            // if key is not present, add a new object
            neighbors = new Neighbors();
        }
        else
        {
            // if key is present, retrieve corresponding data object
            neighbors = neighborBinding.entryToObject(data);
        }
        if(direction.equalsIgnoreCase(PARENTS))
            neighbors.parents.add(hash);
        else if(direction.equalsIgnoreCase(CHILDREN))
            neighbors.children.add(hash);
        data = new DatabaseEntry();
        try
        {
            neighborBinding.objectToEntry(neighbors, data);
            scaffoldDatabase.put(null, key, data);
            globalTxCheckin(false);
        }
        catch(EnvironmentFailureException ex)
        {
            logger.log(Level.SEVERE, "Environment Failure Exception. Closing environment and database!", ex);
            scaffoldDbEnvironment.close();
            neighborDatabase.close();
            scaffoldDatabase.close();
            initialize(directoryPath);
            logger.log(Level.INFO, "Environment and databases opened again");
        }
    }

    @Override
    public Graph queryManager(Map<String, List<String>> params)
    {
        try
        {
            String hash = params.get(PRIMARY_KEY).get(COL_VALUE);
            String direction = params.get(DIRECTION).get(0);
            int maxDepth = Integer.parseInt(params.get(MAX_DEPTH).get(0));
            Map<String, Set<String>> lineageMap = getLineage(hash, direction, maxDepth);
            if(lineageMap != null)
            {
                String storageName = getCurrentStorage().getClass().getSimpleName();
                String className = "spade.query." + storageName.toLowerCase() + "." + storageName;
                Class<?> queryClass = Class.forName(className);
                Method method = queryClass.getMethod("constructGraphFromLineageMap");
                Graph result = (Graph) method.invoke(null, lineageMap, direction);

                return result;
            }
        }
        catch(Exception ex)
        {
            logger.log(Level.SEVERE, "Error in query manager!", ex);
        }

        return null;
    }

    private static class Neighbors implements Serializable
    {
        public Set<String> parents = new HashSet<>();
        public Set<String> children = new HashSet<>();

        @Override
        public boolean equals(Object otherObject)
        {
            if (this == otherObject)
            {
                return true;
            }
            if (!(otherObject instanceof Neighbors))
            {
                return false;
            }
            Neighbors otherNeighbor = (Neighbors) otherObject;
            return this.parents.equals(otherNeighbor.parents) &&
                    this.children.equals(otherNeighbor.children);
        }

        @Override
        public int hashCode()
        {
            final int seed1 = 5;
            final int seed2 = 97;
            int hashCode = seed1;
            hashCode = seed2 * hashCode + parents.hashCode();
            hashCode = seed2 * hashCode + children.hashCode();
            return hashCode;
        }

        @Override
        public String toString()
        {
            return "Neighbors{" +
                    "parents=" + parents +
                    ", children=" + children +
                    '}';
        }
    }

}


