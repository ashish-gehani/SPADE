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
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.LinkedList;
import java.util.Calendar;
import java.util.Date;

import org.apache.commons.codec.digest.DigestUtils;
import org.neo4j.graphalgo.GraphAlgoFactory;
import org.neo4j.graphalgo.PathFinder;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.QueryExecutionException;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseBuilder;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.graphdb.index.IndexManager;
import org.neo4j.graphdb.index.RelationshipIndex;
import org.neo4j.helpers.collection.Iterators;
import org.neo4j.graphdb.PathExpanders;

import spade.core.AbstractEdge;
import spade.core.AbstractStorage;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Graph;
import spade.core.Settings;
import spade.core.Vertex;
import spade.core.BloomFilter;
import spade.utility.CommonFunctions;

import static spade.core.Kernel.CONFIG_PATH;
import static spade.core.Kernel.FILE_SEPARATOR;

/**
 * Neo4j storage implementation.
 *
 * @author Dawood Tariq and Hasanat Kazmi
 */
public class Neo4j extends AbstractStorage
{

    // Identifying annotation to add to each edge/vertex
    private static final String ID_STRING = "id";
    private static final String DIRECTION_ANCESTORS = Settings.getProperty("direction_ancestors");
    private static final String DIRECTION_DESCENDANTS = Settings.getProperty("direction_descendants");
    private static final String DIRECTION_BOTH = Settings.getProperty("direction_both");
    private static final String VERTEX_INDEX = "vertexIndex";
    private static final String EDGE_INDEX = "edgeIndex";
    private GraphDatabaseService graphDb;
    private IndexManager index;
    private Index<Node> vertexIndex;
    private RelationshipIndex edgeIndex;
    static final Logger logger = Logger.getLogger(Neo4j.class.getName());
    private final String NEO_CONFIG_FILE = "cfg/neo4j.properties";

    public enum RelationshipTypes implements RelationshipType { EDGE }
    public enum NodeTypes implements Label { VERTEX }
    private String databasePath;

    public final String HASHCODE_LABEL = "hashCode";
    private double falsePositiveProbability = 0.0001;

    // Performance tuning note: Set this to higher value (up to Integer.MAX_VALUE) to reduce db hit rate.
    // Downside: This would eat more heap at start time.
  	private int expectedNumberOfElements = 1000000;
    private BloomFilter<String> nodeBloomFilter;
    private BloomFilter<String> edgeBloomFilter;
    private LinkedList<String> localNodeHashQueue = new LinkedList<String>();
    private HashMap<String, Node> localNodeCache = new HashMap<String, Node>();

    // Performance tuning note: Depending on data locality, you can increase this FIFO cache size.
    // Performance tuning note: Set this to higher value (e.g. 1000000) to reduce db hit rate.
    // Downside: This would eat more heap.
  	private final int NODE_VERTEX_LOCAL_CACHE_SIZE = 1000000;
    // Performance tuning note: Set this to higher value (e.g. 100000) to commit less often to db - This increases ingestion rate.
    // Downside: Any external (non atomic) quering to database won't report non-commited data.
    private final int GLOBAL_TX_SIZE = 100000;
    // Performance tuning note: This is time in sec that storage is flushed. Increase this to increase throughput / ingestion rate.
    // Downside: Any external (non atomic) quering to database won't report non-commited data.
    private final int MAX_WAIT_TIME_BEFORE_FLUSH = 15000; // ms
    private final boolean LOG_PERFORMANCE_STATS = true;
    private final String NODE_BLOOMFILTER = "spade-neo4j-node-bloomfilter";
    private final String EDGE_BLOOMFILTER = "spade-neo4j-edge-bloomfilter";

    private Transaction globalTx;
  	private int globalTxCount=0;

    private Date lastFlushTime;

    //
    // variables used to track stats only
    //
    private int reportProgressAverageTime = 60000; // ms

  	private int vertexCount = 0;
  	private int edgeCount = 0;
    private int dbHitCountForVertex = 0;
    private int dbHitCountForEdge = 0;
    private int nodeFoundInLocalCacheCount = 0;
    private int foundInDbCount = 0;
    private int falsePositiveCount = 0;
    private Date reportProgressDate;

  	private int vertexCountTmp = 0;
  	private int edgeCountTmp = 0;
    private int dbHitCountForVertexTmp = 0;
    private int dbHitCountForEdgeTmp = 0;
    private int nodeFoundInLocalCacheCountTmp = 0;
    private int foundInDbCountTmp = 0;
    private int falsePositiveCountTmp = 0;
    //
    private static Properties databaseConfigs = new Properties();

    public Neo4j()
    {
        String configFile =  CONFIG_PATH + FILE_SEPARATOR + "spade.storage.Neo4j.config";
        try
        {
            databaseConfigs.load(new FileInputStream(configFile));
        }
        catch(Exception ex)
        {
            String msg  = "Loading Neo4j configurations for SPADE unsuccessful! Unexpected behavior might follow";
            logger.log(Level.WARNING, msg, ex);
        }
    }

    @Override
    public boolean initialize(String arguments)
    {
        try
        {
            Map<String, String> argsMap = CommonFunctions.parseKeyValPairs(arguments);
            databasePath = argsMap.get("databasePath");
            if (databasePath == null)
            {
                databasePath = databaseConfigs.getProperty("databasePath");
            }
            File databaseFile = new File(databasePath);
            GraphDatabaseBuilder graphDbBuilder = new GraphDatabaseFactory().newEmbeddedDatabaseBuilder(databaseFile);
            try
            {
                graphDbBuilder.loadPropertiesFromFile(NEO_CONFIG_FILE);
                logger.log(Level.INFO, "Neo4j internal configurations loaded from config file.");
            }
            catch (Exception exception)
            {
                logger.log(Level.INFO, "Default Neo4j internal configurations loaded.");
            }
            graphDb = graphDbBuilder.newGraphDatabase();

            try ( Transaction tx = graphDb.beginTx() )
            {
                index = graphDb.index();
                // Create vertex index
                vertexIndex = index.forNodes(VERTEX_INDEX);
                // Create edge index
                edgeIndex = index.forRelationships(EDGE_INDEX);
                tx.success();
            }

            nodeBloomFilter = loadBloomFilter(NODE_BLOOMFILTER);
            edgeBloomFilter = loadBloomFilter(EDGE_BLOOMFILTER);

            if (LOG_PERFORMANCE_STATS==true)
            {
              logger.log(Level.INFO, "nodeBloomFilter size at startup: " + nodeBloomFilter.count());
            }
            reportProgressDate = Calendar.getInstance().getTime();
            lastFlushTime = Calendar.getInstance().getTime();

            return true;
        }
        catch (Exception exception)
        {
            logger.log(Level.SEVERE, null, exception);
            return false;
        }
    }

    private BloomFilter loadBloomFilter(String fileName) {

    	try {
    		File filePath = new File(databasePath, fileName);
    		if (filePath.exists()) {
	    		FileInputStream fileInputStream = new FileInputStream(
	    			filePath.toString()
				);
	    		ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
	    		BloomFilter bloomFilter = (BloomFilter<Integer>) objectInputStream.readObject();
	    		objectInputStream.close();
	    		fileInputStream.close();
	    		return bloomFilter;
	    	}
    	} catch (IOException exception) {
    		logger.log(Level.SEVERE, "Failed to load Bloom filter cache", exception);
    	} catch(ClassNotFoundException exception) {
        	exception.printStackTrace();
      	}
      	return new BloomFilter<Integer>(falsePositiveProbability, expectedNumberOfElements);
    }

    private void saveBloomFilter(String fileName, BloomFilter bloomFilter) {

    	try {
    		FileOutputStream fileOutputStream = new FileOutputStream(
    			new File(databasePath, fileName).toString()
			);
    		ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);
    		objectOutputStream.writeObject(bloomFilter);
    		objectOutputStream.close();
    		fileOutputStream.close();
    	} catch (IOException exception) {
    		logger.log(Level.SEVERE, "Failed to save Bloom filter cache", exception);
    	}

    }

    @Override
    public boolean flushTransactions() {
        if (Calendar.getInstance().getTime().getTime() - lastFlushTime.getTime() > MAX_WAIT_TIME_BEFORE_FLUSH) {
            globalTxCheckin(true);
            lastFlushTime = Calendar.getInstance().getTime();
        }
        return true;
    }

    /**
     * This function queries the underlying storage and retrieves the edge
     * matching the given criteria.
     *
     * @param childVertexHash  hash of the child vertex.
     * @param parentVertexHash hash of the parent vertex.
     * @return returns edge object matching the given vertices OR NULL.
     */
    @Override
    public AbstractEdge getEdge(String childVertexHash, String parentVertexHash)
    {
        return null;
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
        return null;
    }

    /**
     * This function finds the children of a given vertex.
     * A child is defined as a vertex which is the source of a
     * direct edge between itself and the given vertex.
     *
     * @param expression expression for the given vertex
     * @return returns graph object containing children of the given vertex OR NULL.
     */
    @Override
    public Graph getChildren(String expression)
    {
        try ( Transaction tx = graphDb.beginTx() )
        {
            IndexHits<Node> queryHits = vertexIndex.query(expression);
            Node startingNode;
            if(queryHits != null && queryHits.size() > 0)
            {
                startingNode = queryHits.iterator().next();
            }
            else
            {
                return null;
            }
            Iterable<Relationship> relationships = startingNode.getRelationships(Direction.INCOMING);
            Graph children = new Graph();
            for(Relationship relationship: relationships)
            {
                children.putVertex(convertNodeToVertex(relationship.getStartNode()));
            }

            queryHits.close();
            tx.success();
            children.commitIndex();

            return children;
        }
    }

    /**
     * This function finds the parents of a given vertex.
     * A parent is defined as a vertex which is the destination of a
     * direct edge between itself and the given vertex.
     *
     * @param expression expression for the given vertex
     * @return returns graph object containing parents of the given vertex OR NULL.
     */
    @Override
    public Graph getParents(String expression)
    {
        try ( Transaction tx = graphDb.beginTx() )
        {
            IndexHits<Node> queryHits = vertexIndex.query(expression);
            Node startingNode;
            if(queryHits != null && queryHits.size() > 0)
            {
                startingNode = queryHits.iterator().next();
            }
            else
            {
                return null;
            }
            Iterable<Relationship> relationships = startingNode.getRelationships(Direction.OUTGOING);
            Graph parents = new Graph();
            for(Relationship relationship: relationships)
            {
                parents.putVertex(convertNodeToVertex(relationship.getEndNode()));
            }

            queryHits.close();
            tx.success();
            parents.commitIndex();

            return parents;
        }
    }

    @Override
    public boolean shutdown() {
        if (LOG_PERFORMANCE_STATS==true) {
          logger.log(Level.INFO, "Shutdown initiated for Neo4j");
        }
        // Flush all transactions before shutting down the database
        // make sure buffers are done, and stop and join all threads
        globalTxFinalize();
        graphDb.shutdown(); // look at register shutdownhook in http://neo4j.com/docs/stable/tutorials-java-embedded-setup.html
        if (LOG_PERFORMANCE_STATS==true) {
          logger.log(Level.INFO, "Database shutdown completed");
        }
        saveBloomFilter(NODE_BLOOMFILTER, nodeBloomFilter);
        saveBloomFilter(EDGE_BLOOMFILTER, edgeBloomFilter);
        
        if (LOG_PERFORMANCE_STATS==true) {
          logger.log(Level.INFO, "All tasks completed!");
        }
        return true;
    }

    void reportProgress() {
        // This reported progess is only for write calls. Querying is independant from this.
        if (LOG_PERFORMANCE_STATS==false) {
          return;
        }

        long diff = Calendar.getInstance().getTime().getTime() - reportProgressDate.getTime();
        if (diff > reportProgressAverageTime) {
            logger.log(Level.INFO, "Node L1: Rate: " + (int) (falsePositiveCount - falsePositiveCountTmp)/(diff/reportProgressAverageTime) + " confirmed false positive/min. Bloom filter false positive probability: " + nodeBloomFilter.getFalsePositiveProbability() + " Bloom filter elements count: " + nodeBloomFilter.count());
            logger.log(Level.INFO, "Node L2: Rate: " + (int) (nodeFoundInLocalCacheCount - nodeFoundInLocalCacheCountTmp)/(diff/reportProgressAverageTime) + " node detection from local cache/min. Total: " + nodeFoundInLocalCacheCount);
            logger.log(Level.INFO, "Node L2: Rate: " + (int) (100.0*localNodeHashQueue.size()/NODE_VERTEX_LOCAL_CACHE_SIZE) + " % local node cache filled. Total: " + NODE_VERTEX_LOCAL_CACHE_SIZE);
            logger.log(Level.INFO, "Node L3: Rate: " + (int) (dbHitCountForVertex - dbHitCountForVertexTmp)/(diff/reportProgressAverageTime) + " db hit for vertexes from putVertices /min. Total: " + dbHitCountForVertex);
            logger.log(Level.INFO, "Node L3: Rate: " + (int) (foundInDbCount - foundInDbCountTmp)/(diff/reportProgressAverageTime) + " detection from db/min. Total: " + foundInDbCount);

            logger.log(Level.INFO, "Edges Rate: " + (int) (dbHitCountForEdge - dbHitCountForEdgeTmp)/(diff/reportProgressAverageTime) + " db hit for vertices from putEdges /min. Total: " + dbHitCountForEdge);
            logger.log(Level.INFO, "Count Vertices: " + (int) (vertexCount - vertexCountTmp)/(diff/reportProgressAverageTime) + " nodes/min. Total: " + vertexCount);
            logger.log(Level.INFO, "Count Edges: " + (int) (edgeCount - edgeCountTmp)/(diff/reportProgressAverageTime) + " edges/min. Total: " + edgeCount);
            logger.log(Level.INFO, "Heap Size: " + Runtime.getRuntime().totalMemory() + " bytes");

            reportProgressDate = Calendar.getInstance().getTime();

            vertexCountTmp = vertexCount;
            edgeCountTmp = edgeCount;
            dbHitCountForVertexTmp = dbHitCountForVertex;
            nodeFoundInLocalCacheCountTmp = nodeFoundInLocalCacheCount;
            foundInDbCountTmp = foundInDbCount;
            dbHitCountForEdgeTmp = dbHitCountForEdge;
            falsePositiveCountTmp = falsePositiveCount;
        }
    }

    public void putInLocalCache(Node vertex, String bigHashCode) {
  		if (localNodeHashQueue.size() > NODE_VERTEX_LOCAL_CACHE_SIZE) {
  		    localNodeCache.remove(localNodeHashQueue.removeFirst());
  		}

      localNodeHashQueue.add(bigHashCode);
      localNodeCache.put(bigHashCode, vertex);
    }

    void globalTxCheckin() {
        globalTxCheckin(false);
    }
    
    void globalTxCheckin(boolean forcedFlush) {
  		if ((globalTxCount % GLOBAL_TX_SIZE == 0) || (forcedFlush == true)) {
            globalTxFinalize();
            globalTx = graphDb.beginTx();
  		}
  		globalTxCount++;
  	}

  	void globalTxFinalize() {
  		if (globalTx != null) {
  			try {
  				globalTx.success();
  			} finally {
  				globalTx.close();
  			}
  		}
  		globalTxCount = 0;
  	}

    @Override
    public boolean putVertex(AbstractVertex incomingVertex)
    {
        incomingVertex.removeAnnotation(ID_STRING);
        String bigHashCode = incomingVertex.bigHashCode();
    	globalTxCheckin();

    	try {
        if (nodeBloomFilter.contains(bigHashCode)) { // L1, confirming if its false positive
          if (localNodeCache.containsKey(bigHashCode)) { // L2
            	nodeFoundInLocalCacheCount++;
              return false;
          }
          dbHitCountForVertex++;

          // L3: confirming from db if we have bloom filter false positive after FIFO cache miss
          Node newVertex;
          newVertex = vertexIndex.get(HASHCODE_LABEL, bigHashCode).getSingle();
        	if (newVertex != null) {
             putInLocalCache(newVertex, bigHashCode);
             foundInDbCount++;
             return false;
          } else {
            falsePositiveCount++;
          }
        }
        vertexCount++;
        reportProgress();

        Node newVertex = graphDb.createNode(NodeTypes.VERTEX);
        newVertex.setProperty(PRIMARY_KEY, bigHashCode);
        vertexIndex.add(newVertex, PRIMARY_KEY, bigHashCode);
        for (Map.Entry<String, String> currentEntry : incomingVertex.getAnnotations().entrySet())
        {
          String key = currentEntry.getKey();
          String value = currentEntry.getValue();
          newVertex.setProperty(key, value);
          vertexIndex.add(newVertex, key, value);
        }

//        newVertex.setProperty(HASHCODE_LABEL, bigHashCode);
//        vertexIndex.add(newVertex, HASHCODE_LABEL, bigHashCode);
//        newVertex.setProperty(ID_STRING, newVertex.getId());
//        vertexIndex.add(newVertex, ID_STRING, Long.toString(newVertex.getId()));
        nodeBloomFilter.add(bigHashCode);
        putInLocalCache(newVertex, bigHashCode);

      } finally {

      }
      return true;
    }


    @Override
    public boolean putEdge(AbstractEdge incomingEdge)
    {
        incomingEdge.removeAnnotation(ID_STRING);
        String bigHashCode = incomingEdge.bigHashCode();
        if (edgeBloomFilter.contains(bigHashCode))
        {
            Relationship edge;
            edge = edgeIndex.get(HASHCODE_LABEL, bigHashCode).getSingle();
            if (edge != null)
            {
                // if (LOG_PERFORMANCE_STATS == true) {
                //     // if there is heavy repetition of edges then comment out this logging or it will slow down ingestion
                //     logger.log(Level.INFO, "Edge (bigHashCode: " + bigHashCode + ") is already in db, skiping");
                // }
                return true;
            }
        }
        
        AbstractVertex childVertex = incomingEdge.getChildVertex();
        AbstractVertex parentVertex = incomingEdge.getParentVertex();
        globalTxCheckin();

        try
        {
            String childVertexHash = childVertex.bigHashCode();
            String parentVertexHash = parentVertex.bigHashCode();
            Node childNode = localNodeCache.get(childVertexHash);
            Node parentNode = localNodeCache.get(parentVertexHash);

            if (childNode == null)
            {
                dbHitCountForEdge++;
                childNode = vertexIndex.get(HASHCODE_LABEL, childVertexHash).getSingle();
                if (childNode == null)
                {
                    // insert vertex if not in db
                    putVertex(childVertex);
                    childNode = localNodeCache.get(childVertexHash);
                }
                putInLocalCache(childNode, childVertexHash);
            }

            if (parentNode == null)
            {
                dbHitCountForEdge++;
                parentNode = vertexIndex.get(HASHCODE_LABEL, parentVertexHash).getSingle();
                if (parentNode == null)
                {
                    // insert vertex if not in db
                    putVertex(parentVertex);
                    parentNode = localNodeCache.get(parentVertexHash);
                }
                putInLocalCache(parentNode, parentVertexHash);
            }

            edgeCount++;
            reportProgress();

            Relationship newEdge = childNode.createRelationshipTo(parentNode, RelationshipTypes.EDGE);
            newEdge.setProperty(PRIMARY_KEY, bigHashCode);
            edgeIndex.add(newEdge, PRIMARY_KEY, bigHashCode);
            newEdge.setProperty(CHILD_VERTEX_KEY, childVertexHash);
            edgeIndex.add(newEdge, CHILD_VERTEX_KEY, childVertexHash);
            newEdge.setProperty(PARENT_VERTEX_KEY, parentVertexHash);
            edgeIndex.add(newEdge, PARENT_VERTEX_KEY, parentVertexHash);
            for (Map.Entry<String, String> currentEntry : incomingEdge.getAnnotations().entrySet())
            {
                String key = currentEntry.getKey();
                String value = currentEntry.getValue();
                newEdge.setProperty(key, value);
                edgeIndex.add(newEdge, key, value);
            }
//            newEdge.setProperty(HASHCODE_LABEL, bigHashCode);
//            edgeIndex.add(newEdge, HASHCODE_LABEL, bigHashCode);
            newEdge.setProperty(ID_STRING, newEdge.getId());
            edgeIndex.add(newEdge, ID_STRING, Long.toString(newEdge.getId()));
            edgeBloomFilter.add(bigHashCode);

        } finally {
        }
        return true;
    }

    public static AbstractVertex convertNodeToVertex(Node node)
    {
        AbstractVertex resultVertex = new Vertex();
        for (String key : node.getPropertyKeys())
        {
            if(key.equalsIgnoreCase(PRIMARY_KEY))
            {
                continue;
            }
            Object value = node.getProperty(key);
            if (value instanceof String)
            {
                resultVertex.addAnnotation(key, (String) value);
            }
            else if (value instanceof Long)
            {
                resultVertex.addAnnotation(key, Long.toString((Long) value));
            }
            else if (value instanceof Double)
            {
                resultVertex.addAnnotation(key, Double.toString((Double) value));
            }
        }
        return resultVertex;
    }

    public static AbstractEdge convertRelationshipToEdge(Relationship relationship)
    {
        AbstractEdge resultEdge = new Edge(convertNodeToVertex(relationship.getStartNode()), convertNodeToVertex(relationship.getEndNode()));
        for (String key : relationship.getPropertyKeys())
        {
            if(key.equals(PRIMARY_KEY) ||
                    key.equals(CHILD_VERTEX_KEY) ||
                    key.equals(PARENT_VERTEX_KEY))
            {
                continue;
            }
            Object value = relationship.getProperty(key);
            if (value instanceof String)
            {
                resultEdge.addAnnotation(key, (String) value);
            }
            else if (value instanceof Long)
            {
                resultEdge.addAnnotation(key, Long.toString((Long) value));
            }
            else if (value instanceof Double)
            {
                resultEdge.addAnnotation(key, Double.toString((Double) value));
            }
        }
        return resultEdge;
    }

    public Graph getVertices(String expression)
    {
        try ( Transaction tx = graphDb.beginTx() )
        {
            Graph resultGraph = new Graph();
            IndexHits<Node> queryHits = vertexIndex.query(expression);
            for (Node foundNode : queryHits)
            {
                resultGraph.putVertex(convertNodeToVertex(foundNode));
            }
            queryHits.close();
            tx.success();
            resultGraph.commitIndex();
            return resultGraph;
        }
    }

    public Graph getEdges(String childExpression, String parentExpression, String edgeExpression)
    {
        Graph resultGraph = new Graph();
        Set<AbstractVertex> childSet = null;
        Set<AbstractVertex> parentSet = null;
        if (childExpression != null)
        {
            if (childExpression.trim().equalsIgnoreCase("null"))
            {
                childExpression = null;
            }
            else
            {
                childSet = getVertices(childExpression).vertexSet();
            }
        }
        if (parentExpression != null)
        {
            if (parentExpression.trim().equalsIgnoreCase("null"))
            {
                parentExpression = null;
            }
            else
            {
                parentSet = getVertices(parentExpression).vertexSet();
            }
        }
        try( Transaction tx = graphDb.beginTx() )
        {
            IndexHits<Relationship> queryHits = edgeIndex.query(edgeExpression);
            for (Relationship foundRelationship : queryHits)
            {
                AbstractVertex childVertex = convertNodeToVertex(foundRelationship.getStartNode());
                AbstractVertex parentVertex = convertNodeToVertex(foundRelationship.getEndNode());
                AbstractEdge tempEdge = convertRelationshipToEdge(foundRelationship);
                if ((childExpression != null) && (parentExpression != null))
                {
                    if (childSet.contains(tempEdge.getChildVertex()) && parentSet.contains(tempEdge.getParentVertex()))
                    {
                        resultGraph.putVertex(childVertex);
                        resultGraph.putVertex(parentVertex);
                        resultGraph.putEdge(tempEdge);
                    }
                }
                else if ((childExpression != null) && (parentExpression == null))
                {
                    if (childSet.contains(tempEdge.getChildVertex()))
                    {
                        resultGraph.putVertex(childVertex);
                        resultGraph.putVertex(parentVertex);
                        resultGraph.putEdge(tempEdge);
                    }
                }
                else if ((childExpression == null) && (parentExpression != null))
                {
                    if (parentSet.contains(tempEdge.getParentVertex()))
                    {
                        resultGraph.putVertex(childVertex);
                        resultGraph.putVertex(parentVertex);
                        resultGraph.putEdge(tempEdge);
                    }
                }
                else if ((childExpression == null) && (parentExpression == null))
                {
                    resultGraph.putVertex(childVertex);
                    resultGraph.putVertex(parentVertex);
                    resultGraph.putEdge(tempEdge);
                }
            }
            queryHits.close();
            tx.success();
        }
        resultGraph.commitIndex();
        return resultGraph;
    }

    public Graph getEdges(int childVertexId, int parentVertexId)
    {
        Graph resultGraph = new Graph();
        try( Transaction tx = graphDb.beginTx() )
        {
            IndexHits<Relationship> queryHits = edgeIndex.query("type:*", graphDb.getNodeById(childVertexId), graphDb.getNodeById(parentVertexId));
            for (Relationship currentRelationship : queryHits)
            {
                resultGraph.putVertex(convertNodeToVertex(currentRelationship.getStartNode()));
                resultGraph.putVertex(convertNodeToVertex(currentRelationship.getEndNode()));
                resultGraph.putEdge(convertRelationshipToEdge(currentRelationship));
            }
            queryHits.close();
            tx.success();
        }
        resultGraph.commitIndex();
        return resultGraph;
    }

    // TODO: remove all neo4j transaction creation in loops. Join that to globalTx
    @Override
    public Graph getPaths(String srcVertexExpression, String dstVertexExpression, int maxLength) {
        Graph resultGraph = new Graph();
        Set<Node> sourceNodes = new HashSet<>();
        Set<Node> destinationNodes = new HashSet<>();

        try ( Transaction tx = graphDb.beginTx() ) {
            IndexHits<Node> queryHits = vertexIndex.query(srcVertexExpression);
            for (Node foundNode : queryHits) {
                sourceNodes.add(foundNode);
            }
            queryHits.close();
            tx.success();
        }
        try ( Transaction tx = graphDb.beginTx() ) {
            IndexHits<Node> queryHits = vertexIndex.query(dstVertexExpression);
            for (Node foundNode : queryHits) {
                destinationNodes.add(foundNode);
            }
            queryHits.close();
            tx.success();
        }

        Set<Long> addedNodeIds = new HashSet<>();
        Set<Long> addedEdgeIds = new HashSet<>();

        PathFinder<Path> pathFinder = GraphAlgoFactory.allSimplePaths(PathExpanders.forDirection(Direction.OUTGOING), maxLength);
        for (Node sourceNode : sourceNodes) {
            for (Node destinationNode : destinationNodes) {
                try ( Transaction tx = graphDb.beginTx() ) {
                    for (Path currentPath : pathFinder.findAllPaths(sourceNode, destinationNode)) {
                        for (Node currentNode : currentPath.nodes()) {
                            if (!addedNodeIds.contains(currentNode.getId())) {
                                resultGraph.putVertex(convertNodeToVertex(currentNode));
                                addedNodeIds.add(currentNode.getId());
                            }
                        }
                        for (Relationship currentRelationship : currentPath.relationships()) {
                            if (!addedEdgeIds.contains(currentRelationship.getId())) {
                                resultGraph.putEdge(convertRelationshipToEdge(currentRelationship));
                                addedEdgeIds.add(currentRelationship.getId());
                            }
                        }
                    }
                    tx.success();
                }
            }
        }

        resultGraph.commitIndex();
        return resultGraph;
    }

    @Override
    public Object executeQuery(String query)
    {
        try ( Transaction tx = graphDb.beginTx() )
        {
            Result result = null;
            globalTxCheckin();
            try
            {
                result = graphDb.execute(query);
            }
            catch(QueryExecutionException ex)
            {
                logger.log(Level.SEVERE, "Neo4j Cypher query execution not successful!", ex);
            }
            finally
            {
                tx.success();
            }
            return result;
        }
    }

    public Graph getPaths(int srcVertexId, int dstVertexId, int maxLength) {
        return getPaths(ID_STRING + ":" + srcVertexId, ID_STRING + ":" + dstVertexId, maxLength);
    }

    public Graph getLineage(String vertexExpression, int depth, String direction, String terminatingExpression) {
        Direction dir;
        if (DIRECTION_ANCESTORS.startsWith(direction.toLowerCase())) {
            dir = Direction.OUTGOING;
        } else if (DIRECTION_DESCENDANTS.startsWith(direction.toLowerCase())) {
            dir = Direction.INCOMING;
        } else if (DIRECTION_BOTH.startsWith(direction.toLowerCase())) {
            Graph ancestor = getLineage(vertexExpression, depth, DIRECTION_ANCESTORS, terminatingExpression);
            Graph descendant = getLineage(vertexExpression, depth, DIRECTION_DESCENDANTS, terminatingExpression);
            Graph result = Graph.union(ancestor, descendant);
            return result;
        } else {
            return null;
        }

        Graph resultGraph = new Graph();
        Set<Node> doneSet = new HashSet<>();
        Set<Node> tempSet = new HashSet<>();

        try ( Transaction tx = graphDb.beginTx() ) {
            IndexHits<Node> queryHits = vertexIndex.query(vertexExpression);
            for (Node foundNode : queryHits) {
                resultGraph.putVertex(convertNodeToVertex(foundNode));
                tempSet.add(foundNode);
            }
            queryHits.close();
            tx.success();
        }

        if ((terminatingExpression != null) && (terminatingExpression.trim().equalsIgnoreCase("null"))) {
            terminatingExpression = null;
        }
        Set<Node> terminatingSet = new HashSet<>();
        if (terminatingExpression != null) {
            try ( Transaction tx = graphDb.beginTx() ) {
                IndexHits<Node> queryHits = vertexIndex.query(terminatingExpression);
                for (Node foundNode : queryHits) {
                    terminatingSet.add(foundNode);
                }
                queryHits.close();
                tx.success();
            }
        }

        int currentDepth = 0;
        while (true) {
            if ((tempSet.isEmpty()) || (depth == 0)) {
                break;
            }
            doneSet.addAll(tempSet);
            Set<Node> newTempSet = new HashSet<>();
            for (Node tempNode : tempSet) {
                try ( Transaction tx = graphDb.beginTx() ) {
                    for (Relationship nodeRelationship : tempNode.getRelationships(dir)) {
                        Node otherNode = nodeRelationship.getOtherNode(tempNode);
                        if (terminatingSet.contains(otherNode)) {
                            continue;
                        }
                        if (!doneSet.contains(otherNode)) {
                            newTempSet.add(otherNode);
                        }
                        resultGraph.putVertex(convertNodeToVertex(otherNode));
                        resultGraph.putEdge(convertRelationshipToEdge(nodeRelationship));
                        // Add network artifacts to the network map of the graph.
                        // This is needed to resolve remote queries
                        try {
                            if (((String) otherNode.getProperty("subtype")).equalsIgnoreCase("network")) {
                                resultGraph.putNetworkVertex(convertNodeToVertex(otherNode), currentDepth);
                            }
                        } catch (Exception exception) {
                            // Ignore
                        }
                    }
                    tx.success();
                }
            }
            tempSet.clear();
            tempSet.addAll(newTempSet);
            depth--;
            currentDepth++;
        }

        resultGraph.commitIndex();
        return resultGraph;
    }

    public Graph getLineage(int vertexId, int depth, String direction, String terminatingExpression) {
    	return getLineage(ID_STRING + ":" + vertexId, depth, direction, terminatingExpression);
    }

    public String getHashOfEdge(AbstractEdge edge){
    	String completeEdgeString = edge.getChildVertex().toString() + edge.toString() + edge.getParentVertex().toString();
    	return DigestUtils.sha256Hex(completeEdgeString);
    }

    public String getHashOfVertex(AbstractVertex vertex){
    	return DigestUtils.sha256Hex(vertex.toString());
    }

    public static void index(String dbpath, boolean printProgress) {

        int totalThreads = Runtime.getRuntime().availableProcessors();
        final ConcurrentLinkedQueue<Node> nodeTaskQueue = new ConcurrentLinkedQueue<Node>();
        final ConcurrentLinkedQueue<Relationship> edgeTaskQueue = new ConcurrentLinkedQueue<Relationship>();
        final ReentrantReadWriteLock nodeRwlock = new ReentrantReadWriteLock();
        final ReentrantReadWriteLock edgeRwlock = new ReentrantReadWriteLock();
        final Index<Node> vertexIndex;
        final RelationshipIndex edgeIndex;
        System.out.println("Loading database...");
        File databaseFile = new File(dbpath);
        final GraphDatabaseService graphDb = new GraphDatabaseFactory().newEmbeddedDatabaseBuilder(databaseFile)
            .setConfig(GraphDatabaseSettings.pagecache_memory, "" + (Runtime.getRuntime().totalMemory()*9)/10)
            // .setConfig(GraphDatabaseSettings.keep_logical_logs, "false")
            .newGraphDatabase();

        System.out.println("Loaded");
        // clear already present indexes
        try ( Transaction tx = graphDb.beginTx() ) {
            graphDb.index().forNodes(spade.storage.Neo4j.VERTEX_INDEX).delete();
            tx.success();
        }

        try ( Transaction tx = graphDb.beginTx() ) {
            graphDb.index().forRelationships(spade.storage.Neo4j.EDGE_INDEX).delete();
            tx.success();
        }
        //

        System.out.println("Creating Indexing discriptors...");

        try ( Transaction tx = graphDb.beginTx() ) {
            vertexIndex = graphDb.index().forNodes(spade.storage.Neo4j.VERTEX_INDEX);
            tx.success();
        }

        try ( Transaction tx = graphDb.beginTx() ) {
            edgeIndex = graphDb.index().forRelationships(spade.storage.Neo4j.EDGE_INDEX);
            tx.success();
        }

        System.out.println("Created");

        class NodeIndexer implements Runnable {

            public void run() {

                Transaction tx = graphDb.beginTx();
                int counter = 0;
                try {
                    while (!Thread.currentThread().isInterrupted()) {

                        if (counter < 10000) {
                            Node node = nodeTaskQueue.poll();
                            if (node==null) {
                                continue;
                            }

                            for ( String key : node.getPropertyKeys() ) {
                                vertexIndex.add(node, key, (String) node.getProperty( key ));
                            }
                            node.setProperty(ID_STRING, node.getId());
                            vertexIndex.add(node, ID_STRING, Long.toString(node.getId()));

                            counter++;
                        }

                        if (counter > 1000 && nodeRwlock.writeLock().tryLock()){
                            tx.success();
                            tx.close();
                            tx = graphDb.beginTx();
                            nodeRwlock.writeLock().unlock();
                            counter =0;
                        }

                    }

                } finally {
                    // tx.success();
                    tx.close();
                    if (nodeRwlock.writeLock().isHeldByCurrentThread()) {
                        nodeRwlock.writeLock().unlock();
                    }
                }
            }
        }

        class RelationshipIndexer implements Runnable {

            public void run() {

                Transaction tx = graphDb.beginTx();
                int counter = 0;
                try {
                    while (!Thread.currentThread().isInterrupted()) {

                        if (counter < 10000) {
                            Relationship relationship = edgeTaskQueue.poll();
                            if (relationship==null) {
                                continue;
                            }

                            for ( String key : relationship.getPropertyKeys() ) {
                                edgeIndex.add(relationship, key, (String) relationship.getProperty( key ));
                            }
                            relationship.setProperty(ID_STRING, relationship.getId());
                            edgeIndex.add(relationship, ID_STRING, Long.toString(relationship.getId()));

                            counter++;
                        }

                        if (counter > 1000 && edgeRwlock.writeLock().tryLock()){
                            // tx.success();
                            tx.close();
                            tx = graphDb.beginTx();
                            edgeRwlock.writeLock().unlock();
                            counter =0;
                        }

                    }

                } finally {
                    // tx.success();
                    tx.close();
                    if (edgeRwlock.writeLock().isHeldByCurrentThread()) {
                        edgeRwlock.writeLock().unlock();
                    }
                }

            }
        }

        ArrayList<Thread> nodeWorkers = new ArrayList<Thread>();
        for (int i=0; i<totalThreads/2; i++) {
            Thread th = new Thread(new NodeIndexer());
            nodeWorkers.add(th);
            th.start();
        }

        ArrayList<Thread> edgeWorkers = new ArrayList<Thread>();
        for (int i=0; i<totalThreads/2; i++) {
            Thread th = new Thread(new RelationshipIndexer());
            edgeWorkers.add(th);
            th.start();
        }


        System.out.println("Counted Nodes and Relationships to index...");
        final long total;

        try ( Transaction tx = graphDb.beginTx() )
        {
            total = Iterators.count(graphDb.getAllNodes().iterator()) + Iterators.count(graphDb.getAllRelationships().iterator());
            tx.success();
        }
        System.out.println("done.\n");

        long percentageCompleted = 0;
        int count = 0;

        try ( Transaction tx = graphDb.beginTx() ) {

            // index nodes
            Iterator<Node> nodeIterator = graphDb.getAllNodes().iterator();
            Iterator<Relationship> edgeIterator = graphDb.getAllRelationships().iterator();

            while (edgeIterator.hasNext() || nodeIterator.hasNext()) {

                if (nodeIterator.hasNext() && nodeTaskQueue.size()<10000) {
                    nodeTaskQueue.add(nodeIterator.next());
                    count = count+1;
                }

                if (edgeIterator.hasNext() && edgeTaskQueue.size()<10000) {
                    edgeTaskQueue.add(edgeIterator.next());
                    count = count+1;
                }

                if (printProgress) {

                    if (((count*100)/total) > percentageCompleted) {
                        Runtime rt = Runtime.getRuntime();
                        long totalMemory = rt.totalMemory()/ 1024 / 1024;
                        long freeMemory = rt.freeMemory()/ 1024 / 1024;
                        long usedMemory = totalMemory - freeMemory;
                        System.out.print("| Cores: " + rt.availableProcessors()
                                + " | Threads: " + totalThreads
                                + " | Heap (MB) - total: " + totalMemory + " , " + (freeMemory*100)/totalMemory +  "% free"
                                // + " | Total Objects (nodes + relationships) to Index: " + total
                                + " | Indexing Object (nodes + relationships): " + count  + " / " + total
                                + " | Completed: " + percentageCompleted + " %"
                                + " |\r");
                    }

                    percentageCompleted = (count*100)/total;
                }

            }

            tx.success();
        }

        System.out.println("\n\nIndexing completed. Waiting for queues to clear...");

        try {
            while (nodeTaskQueue.size()!=0 || edgeTaskQueue.size()!=0) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException exception) {

        }

        System.out.println("Queues cleared. Threads teardown started...");

        for (int i=0; i<totalThreads/2; i++) {
            nodeWorkers.get(i).interrupt();
            try {
                nodeWorkers.get(i).join();
            } catch (InterruptedException exception) {

            }
        }

        for (int i=0; i<totalThreads/2; i++) {
            edgeWorkers.get(i).interrupt();
            try {
                edgeWorkers.get(i).join();
            } catch (InterruptedException exception) {

            }
        }

        System.out.println("Database shutdown started...");
        graphDb.shutdown();
	}
}
