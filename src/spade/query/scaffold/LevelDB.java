package spade.query.scaffold;

import org.apache.commons.lang.StringUtils;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.WriteBatch;
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
    private DB scaffoldDatabase = null;
    WriteBatch batch = null;
    private static final String LIST_SEPARATOR = "-";
    private static final String HASH_SEPARATOR = ",";

    public LevelDB()
    {
        GLOBAL_TX_SIZE = 1;
    }

    public LevelDB(int batchSize)
    {
        GLOBAL_TX_SIZE = batchSize;
    }

    public boolean initialize(String arguments)
    {
        try
        {
            directoryPath = arguments;
            Options options = new Options();
            options.createIfMissing(true);
            scaffoldDatabase = factory.open(new File(directoryPath), options);
//            globalTxCheckin(true);
        }
        catch(IOException ex)
        {
            logger.log(Level.SEVERE, null, ex);
            return false;
        }

        return true;
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
        levelDB.initialize("leveldb_test");

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


        System.out.println(levelDB.insertEntry(e1));
        System.out.println(levelDB.insertEntry(e2));

    }
}
