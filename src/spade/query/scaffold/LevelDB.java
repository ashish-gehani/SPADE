package spade.query.scaffold;

import kafka.security.auth.Write;
import org.apache.commons.lang.StringUtils;
import org.iq80.leveldb.CompressionType;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.WriteBatch;
import org.iq80.leveldb.WriteOptions;
import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Graph;
import spade.core.Vertex;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.fusesource.leveldbjni.JniDBFactory.asString;
import static org.fusesource.leveldbjni.JniDBFactory.bytes;
import static org.fusesource.leveldbjni.JniDBFactory.factory;

import static spade.core.AbstractStorage.DIRECTION_ANCESTORS;
import static spade.core.AbstractStorage.DIRECTION_DESCENDANTS;

public class LevelDB extends Scaffold
{
    private static Logger logger = Logger.getLogger(LevelDB.class.getName());
    private static final int INITIAL_CACHE_CAPACITY = 500;
    private static final int DEFAULT_ENTRY_SIZE = 200;
    private Map<String, StringBuilder> childListCache = new HashMap<>(INITIAL_CACHE_CAPACITY);
    private Map<String, StringBuilder> parentListCache = new HashMap<>(INITIAL_CACHE_CAPACITY);
    private DB childDatabase = null;
    private DB parentDatabase = null;
    private DB scaffoldDatabase = null;
    private WriteBatch childBatch = null;
    private WriteBatch parentBatch = null;
    private WriteBatch batch = null;
    private static final String LIST_SEPARATOR = "-";
    private static final String HASH_SEPARATOR = ",";

    public LevelDB()
    {
        GLOBAL_TX_SIZE = 1000;
    }

    public LevelDB(int batchSize)
    {
        GLOBAL_TX_SIZE = batchSize;
    }

    public boolean initialize(String arguments)
    {
        try
        {
            WriteOptions writeOptions = new WriteOptions();
            writeOptions.sync(false);

            directoryPath = arguments;
            Options options = new Options();
            options.createIfMissing(true);
            options.compressionType(CompressionType.NONE);
//            scaffoldDatabase = factory.open(new File(directoryPath), options);
            childDatabase = factory.open(new File(directoryPath + "child"), options);
            parentDatabase = factory.open(new File(directoryPath + "parent"), options);
            logger.log(Level.INFO, "Scaffold initialized");
//            globalTxCheckin(true);
        }
        catch(IOException ex)
        {
            logger.log(Level.SEVERE, null, ex);
            return false;
        }

        return true;
    }

    protected void globalTxCheckin1(boolean forcedFlush)
    {
        if ((globalTxCount % GLOBAL_TX_SIZE == 0) || (forcedFlush))
        {
            try
            {
                if(childBatch != null)
                {
                    childDatabase.write(childBatch);
                    childBatch.close();
                }
                if(parentBatch != null)
                {
                    parentDatabase.write(parentBatch);
                    parentBatch.close();
                }
                globalTxCount = 0;
                childBatch = childDatabase.createWriteBatch();
                parentBatch = parentDatabase.createWriteBatch();
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


    @Override
    protected void globalTxCheckin(boolean forcedFlush)
    {
        if ((globalTxCount % GLOBAL_TX_SIZE == 0) || (forcedFlush))
        {
            try
            {
                if(batch != null)
                {
                    scaffoldDatabase.write(batch);
                    batch.close();
                }
                globalTxCount = 0;
                batch = scaffoldDatabase.createWriteBatch();
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

    public boolean shutdown()
    {
        if(scaffoldDatabase != null)
        {
            try
            {
                scaffoldDatabase.close();
            }
            catch(IOException ex)
            {
                logger.log(Level.SEVERE, null, ex);
                return false;
            }
        }
        return true;
    }

    @Override
    public Set<String> getChildren(String parentHash)
    {
        byte[] scaffoldEntryBytes = scaffoldDatabase.get(bytes(parentHash));
        Set<String> childrenHashesSet = null;
        if(scaffoldEntryBytes != null)
        {
            String scaffoldEntry = asString(scaffoldEntryBytes);
            String[] neighborHashList = scaffoldEntry.split(LIST_SEPARATOR, -1);
            String childrenHashList = neighborHashList[0];
            String[] childrenHashes = childrenHashList.split(HASH_SEPARATOR);
            if(childrenHashes.length > 0)
            {
                childrenHashesSet = new HashSet<>(Arrays.asList(childrenHashes));
            }
        }

        return childrenHashesSet;
    }

    @Override
    public Set<String> getParents(String childHash)
    {
        byte[] scaffoldEntryBytes = scaffoldDatabase.get(bytes(childHash));
        Set<String> parentHashesSet = null;
        if(scaffoldEntryBytes != null)
        {
            String scaffoldEntry = asString(scaffoldEntryBytes);
            String[] neighborHashList = scaffoldEntry.split(LIST_SEPARATOR, -1);
            String parentHashList = neighborHashList[1];
            String[] parentHashes = parentHashList.split(HASH_SEPARATOR);
            if(parentHashes.length > 0)
            {
                parentHashesSet = new HashSet<>(Arrays.asList(parentHashes));
            }
        }

        return parentHashesSet;
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
            byte[] scaffoldEntry = scaffoldDatabase.get(bytes(hash));
            if(scaffoldEntry != null)
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
                                    currentSet.add(vertexHash);
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
        catch(Exception ex)
        {
            logger.log(Level.SEVERE, "Scaffold Get Lineage error!", ex);
        }

        return null;
    }

    @Override
    public Map<String, Set<String>> getPaths(String source_hash, String destination_hash, int maxLength)
    {
        return null;
    }

    private boolean flushBulkEntries(boolean forcedFlush)
    {
        try
        {
            if((globalTxCount % GLOBAL_TX_SIZE == 0) || forcedFlush)
            {
                /*
                    processing child vertex
                 */
                WriteBatch childBatch = childDatabase.createWriteBatch();
                for(Map.Entry<String, StringBuilder> childCacheEntry : childListCache.entrySet())
                {
                    String childVertexHash = childCacheEntry.getKey();
                    byte[] childVertexHashBytes = bytes(childVertexHash);
                    byte[] currentChildEntryBytes = childDatabase.get(childVertexHashBytes);
                    String newChildEntry;
                    if(currentChildEntryBytes == null)
                    {
                        newChildEntry = childCacheEntry.getValue().toString();
                    } else
                    {
                        String currentChildEntry = asString(currentChildEntryBytes);
                        newChildEntry = currentChildEntry + HASH_SEPARATOR + childCacheEntry.getValue();
                    }
                    childBatch.put(childVertexHashBytes, bytes(newChildEntry));
                }
                childDatabase.write(childBatch);
                childBatch.close();
                childListCache.clear();

                /*
                    processing parent vertex
                 */
                WriteBatch parentBatch = parentDatabase.createWriteBatch();
                for(Map.Entry<String, StringBuilder> parentCacheEntry : parentListCache.entrySet())
                {
                    String parentVertexHash = parentCacheEntry.getKey();
                    byte[] parentVertexHashBytes = bytes(parentVertexHash);
                    byte[] currentParentEntryBytes = parentDatabase.get(parentVertexHashBytes);
                    String newParentEntry;
                    if(currentParentEntryBytes == null)
                    {
                        newParentEntry = parentCacheEntry.getValue().toString();
                    } else
                    {
                        String currentParentEntry = asString(currentParentEntryBytes);
                        newParentEntry = currentParentEntry + HASH_SEPARATOR + parentCacheEntry.getValue().toString();
                    }
                    parentBatch.put(parentVertexHashBytes, bytes(newParentEntry));
                }
                parentDatabase.write(parentBatch);
                parentBatch.close();
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

    public boolean flushBulkEntries1(boolean forcedFlush)
    {
        try
        {
            if((globalTxCount % GLOBAL_TX_SIZE == 0) || forcedFlush)
            {
                WriteBatch batch = scaffoldDatabase.createWriteBatch();
                Set<String> hashList = new HashSet<>(childListCache.keySet());
                hashList.addAll(parentListCache.keySet());
                for(String hash: hashList)
                {
                    byte[] hashBytes = bytes(hash);
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
                    byte[] currentScaffoldEntryBytes = scaffoldDatabase.get(hashBytes);
                    String newScaffoldEntry;
                    if(currentScaffoldEntryBytes == null)
                    {
                        Set<String> childHashSet = new HashSet<>(Arrays.asList(childCache.split(HASH_SEPARATOR)));
                        Set<String> parentHashSet = new HashSet<>(Arrays.asList(parentCache.split(HASH_SEPARATOR)));
                        newScaffoldEntry = StringUtils.join(childHashSet, HASH_SEPARATOR) + LIST_SEPARATOR +
                                StringUtils.join(parentHashSet, HASH_SEPARATOR);
                    }
                    else
                    {
                        String currentScaffoldEntry = asString(currentScaffoldEntryBytes);
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
                    batch.put(hashBytes, bytes(newScaffoldEntry));
                }
                childListCache.clear();
                parentListCache.clear();
                scaffoldDatabase.write(batch);
                batch.close();
            }
        }
        catch(Exception ex)
        {
            logger.log(Level.SEVERE, "Error bulk flushing cached entries to scaffold!", ex);
            return false;
        }

        return true;
    }

    public boolean insertEntry2(AbstractEdge incomingEdge)
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

    public boolean insertEntry1(AbstractEdge incomingEdge)
    {
        try
        {
            String childVertexHash = incomingEdge.getChildVertex().bigHashCode();
            byte[] childVertexHashBytes = bytes(childVertexHash);
            String parentVertexHash = incomingEdge.getParentVertex().bigHashCode();
            byte[] parentVertexHashBytes = bytes(parentVertexHash);
            /*
                processing child vertex
             */
            byte[] childEntryBytes = childDatabase.get(childVertexHashBytes);
            String childEntry = asString(childEntryBytes);
            String newChildEntry;
            if(childEntryBytes == null)
            {
                newChildEntry = parentVertexHash;
            }
            else
            {
                newChildEntry = childEntry + HASH_SEPARATOR + parentVertexHash;
            }
            childDatabase.put(childVertexHashBytes, bytes(newChildEntry));

            /*
                processing parent vertex
             */
            byte[] parentEntryBytes = parentDatabase.get(parentVertexHashBytes);
            String parentEntry = asString(parentEntryBytes);
            String newParentEntry;
            if(parentEntryBytes == null)
            {
                newParentEntry = childVertexHash;
            }
            else
            {
                newParentEntry = parentEntry + HASH_SEPARATOR + childVertexHash;
            }
            parentDatabase.put(parentVertexHashBytes, bytes(newParentEntry));

        }
        catch(Exception ex)
        {
            logger.log(Level.SEVERE, null, ex);
            return false;
        }

        return true;
    }

    /**
     * This function inserts hashes of the end vertices of given edge
     * into the scaffold storage.
     *
     * @param incomingEdge edge whose end points to insert into the storage
     * @return returns true if the insertion is successful.
     * format of one (<key>,<value>) entry in scaffold is as follows:
     * <key> = <vertex hash>
     * <value> = <children hashes separated by HASH_SEPARATOR> <LIST_SEPARATOR> <parent hashes by HASH_SEPARATOR>
     * for example:
     * hash1,hash2,hash3,hash4-hash10,hash11,hash12,hash13,hash14
     */
    @Override
    public boolean insertEntry(AbstractEdge incomingEdge)
    {
        try
        {
            String childVertexHash = incomingEdge.getChildVertex().bigHashCode();
            byte[] childVertexHashBytes = bytes(childVertexHash);
            String parentVertexHash = incomingEdge.getParentVertex().bigHashCode();
            byte[] parentVertexHashBytes = bytes(parentVertexHash);

            /*
                processing child vertex
             */
            byte[] currentChildEntryBytes = scaffoldDatabase.get(childVertexHashBytes);
            String newChildEntry;
            // if key is not present
            if(currentChildEntryBytes == null)
            {
                newChildEntry = LIST_SEPARATOR + parentVertexHash;
            }
            // append to existing list
            else
            {
                String currentChildEntry = asString(currentChildEntryBytes);
                String[] neighborHashList = currentChildEntry.split(LIST_SEPARATOR, -1);
                String childrenHashList = neighborHashList[0];
                String parentHashList = neighborHashList[1];
                if(parentHashList.isEmpty())
                {
                    newChildEntry = currentChildEntry + parentVertexHash;
                }
                else
                {
                    Set<String> parentHashSet = new HashSet<>(Arrays.asList(parentHashList.split(HASH_SEPARATOR)));
                    parentHashSet.add(parentVertexHash);
                    newChildEntry = childrenHashList + LIST_SEPARATOR + StringUtils.join(parentHashSet, HASH_SEPARATOR);
                }
            }
            scaffoldDatabase.put(childVertexHashBytes, bytes(newChildEntry));
//            globalTxCheckin(false);

            /*
                processing parent vertex
             */
            byte[] currentParentEntryBytes = scaffoldDatabase.get(parentVertexHashBytes);
            String newParentEntry;
            // if key is not present
            if(currentParentEntryBytes == null)
            {
                newParentEntry = childVertexHash + LIST_SEPARATOR;
            }
            // append to existing list
            else
            {
                String currentParentEntry = asString(currentParentEntryBytes);
                String[] neighborHashList  = currentParentEntry.split(LIST_SEPARATOR, -1);
                String childrenHashList = neighborHashList[0];
                String parentHashList = neighborHashList[1];
                if(childrenHashList.isEmpty())
                {
                    newParentEntry = childVertexHash + currentParentEntry;
                }
                else
                {
                    Set<String> childrenHashSet = new HashSet<>(Arrays.asList(childrenHashList.split(HASH_SEPARATOR)));
                    childrenHashSet.add(childVertexHash);
                    newParentEntry = StringUtils.join(childrenHashSet, HASH_SEPARATOR) + LIST_SEPARATOR + parentHashList;
                }
            }
            scaffoldDatabase.put(parentVertexHashBytes, bytes(newParentEntry));
//            globalTxCheckin(false);
        }
        catch(Exception ex)
        {
            logger.log(Level.SEVERE, null, ex);
            return false;
        }

        return true;
    }

    @Override
    public Graph queryManager(Map<String, List<String>> params)
    {
        return null;
    }

    public static void main(String[] args)
    {
        LevelDB levelDB = new LevelDB();
        levelDB.initialize("leveldb_test/");

        AbstractVertex v1 = new Vertex();
        v1.addAnnotation("type", "Process");
        v1.addAnnotation("name", "root process");
        v1.addAnnotation("pid", "10");
        v1.addAnnotation("vertexid", "1");

        AbstractVertex v2 = new Vertex();
        v2.addAnnotation("type", "Process");
        v2.addAnnotation("name", "child process");
        v2.addAnnotation("pid", "32");
        v2.addAnnotation("vertexid", "2");

        AbstractEdge e1 = new Edge(v2, v1);
        e1.addAnnotation("type", "WasTriggeredBy");
        e1.addAnnotation("time", "5:56 PM");
        e1.addAnnotation("edgeid", "11");

        AbstractVertex v3 = new Vertex();
        v3.addAnnotation("type", "Artifact");
        v3.addAnnotation("filename", "output.tmp");
        v3.addAnnotation("vertexid", "3");

        AbstractVertex v4 = new Vertex();
        v4.addAnnotation("type", "Artifact");
        v4.addAnnotation("filename", "output.o");
        v4.addAnnotation("vertexid", "4");

        AbstractEdge e2 = new Edge(v2, v3);
        e2.addAnnotation("type", "Used");
        e2.addAnnotation("iotime", "12 ms");
        e2.addAnnotation("edgeid", "2");

        AbstractEdge e3 = new Edge(v4, v2);
        e3.addAnnotation("type", "WasGeneratedBy");
        e3.addAnnotation("iotime", "11 ms");
        e3.addAnnotation("edgeid", "3");

        AbstractEdge e4 = new Edge(v4, v3);
        e4.addAnnotation("type", "WasDerivedFrom");
        e4.addAnnotation("edgeid", "4");

        AbstractVertex v5 = new Vertex();
        v5.addAnnotation("type", "Agent");
        v5.addAnnotation("uid", "10");
        v5.addAnnotation("gid", "10");
        v5.addAnnotation("name", "john");
        v5.addAnnotation("vertexid", "5");

        AbstractEdge e5 = new Edge(v1, v5);
        e5.addAnnotation("type", "WasControlledBy");
        e5.addAnnotation("edgeid", "5");

        AbstractEdge e6 = new Edge(v2, v5);
        e6.addAnnotation("type", "WasControlledBy");
        e6.addAnnotation("edgeid", "6");

        System.out.println(levelDB.insertEntry2(e1));
        System.out.println(levelDB.insertEntry2(e2));
        System.out.println(levelDB.insertEntry2(e3));
        System.out.println(levelDB.insertEntry2(e4));
        System.out.println(levelDB.insertEntry2(e5));
        System.out.println(levelDB.insertEntry2(e6));

    }
}
