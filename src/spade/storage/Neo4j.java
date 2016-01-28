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
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
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
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseBuilder;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.graphdb.index.IndexManager;
import org.neo4j.graphdb.index.RelationshipIndex;
import org.neo4j.helpers.collection.IteratorUtil;
import org.neo4j.kernel.Traversal;
import org.neo4j.tooling.GlobalGraphOperations;

import spade.core.AbstractEdge;
import spade.core.AbstractStorage;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Graph;
import spade.core.Settings;
import spade.core.Vertex;
import spade.core.BloomFilter;

/**
 * Neo4j storage implementation.
 *
 * @author Dawood Tariq and Hasanat Kazmi
 */
public class Neo4j extends AbstractStorage {

    // Identifying annotation to add to each edge/vertex
    private static final String ID_STRING = Settings.getProperty("storage_identifier");
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

    private enum MyRelationshipTypes implements RelationshipType { EDGE }
    private enum MyNodeTypes implements Label { VERTEX }    
    private String neo4jDatabaseDirectoryPath = null;

	public final String NODE_HASHCODE_LABEL = "hashCode";
	private double falsePositiveProbability = 0.0001;
	private int expectedNumberOfElements = Integer.MAX_VALUE;
	private BloomFilter<Integer> nodeBloomFilter; 
	private LinkedList<Integer> localNodeHashQueue = new LinkedList<Integer>();
	private HashMap<Integer, Node> localNodeCache = new HashMap<Integer, Node>();
	private final int NODE_VERTEX_LOCAL_CACHE_SIZE = 100000;
    private final int FLUSH_TX_COUNT = 10000;
	private String NODE_BLOOMFILTER = "spade-neo4j-node-bloomfilter";

    @Override
    public boolean initialize(String arguments) {
        try {
        	neo4jDatabaseDirectoryPath = arguments;
            if (neo4jDatabaseDirectoryPath == null) {
                return false;
            }
            GraphDatabaseBuilder graphDbBuilder = new GraphDatabaseFactory().newEmbeddedDatabaseBuilder(neo4jDatabaseDirectoryPath)
            	// this options caches in memeory before dumping it on disk
	            // .setConfig(GraphDatabaseSettings.pagecache_memory, "" + 1024*1024*1024)
            ;
            try {
                graphDbBuilder.loadPropertiesFromFile(NEO_CONFIG_FILE);
                logger.log(Level.INFO, "Neo4j configurations loaded from config file.");
            } catch (Exception exception) {
                logger.log(Level.INFO, "Default Neo4j configurations loaded.");
            }
            graphDb = graphDbBuilder.newGraphDatabase();

            try ( Transaction tx = graphDb.beginTx() ) {
                index = graphDb.index();
                // Create vertex index
                vertexIndex = index.forNodes(VERTEX_INDEX);
                // Create edge index
                edgeIndex = index.forRelationships(EDGE_INDEX);
                tx.success();
            }

            nodeBloomFilter = loadBloomFilter(NODE_BLOOMFILTER);

            logger.log(Level.INFO, "nodeBloomFilter size: " + nodeBloomFilter.count());

            reportProgressDate = Calendar.getInstance().getTime();
            
            return true;
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
            return false;
        }
    }

    private BloomFilter loadBloomFilter(String fileName) {

    	try {
    		File filePath = new File(neo4jDatabaseDirectoryPath, fileName);
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
    		logger.log(Level.SEVERE, "Failed to load spade neo4j bloomfilter cache", exception);
    	} catch(ClassNotFoundException exception) {
        	exception.printStackTrace();
      	}
      	return new BloomFilter<Integer>(falsePositiveProbability, expectedNumberOfElements);
    }

    private void saveBloomFilter(String fileName, BloomFilter bloomFilter) {

    	try {
    		FileOutputStream fileOutputStream = new FileOutputStream(
    			new File(neo4jDatabaseDirectoryPath, fileName).toString() 
			);
    		ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);
    		objectOutputStream.writeObject(bloomFilter);
    		objectOutputStream.close();
    		fileOutputStream.close();
    	} catch (IOException exception) {
    		logger.log(Level.SEVERE, "Failed to save spade neo4j bloomfilter cache", exception);
    	}

    }

    @Override
    public boolean flushTransactions() {
        // Flush any pending transactions. This method is called by the Kernel
        // whenever a query is executed
        return true;
    }

    @Override
    public boolean shutdown() {
    	logger.log(Level.INFO, "shutdown initiated for Neo4j");
        // Flush all transactions before shutting down the database
        // make sure buffers are done, and stop and join all threads
        graphDb.shutdown(); // look at register shutdownhook in http://neo4j.com/docs/stable/tutorials-java-embedded-setup.html 
        logger.log(Level.INFO, "database shutdown");
        saveBloomFilter(NODE_BLOOMFILTER, nodeBloomFilter);
        logger.log(Level.INFO, "all chores completed!");
        return true;
    }

    //
    // variables used to track starts
    //
	int totalVertices = 0;
	int totalEdges = 0;
    int dbHitCountForVertex = 0;
    int dbHitCountForEdge = 0;
    int nodeFoundInLocalCacheCount = 0;
    int foundInDbCount = 0;
    int falsePositiveCount = 0;
    Date reportProgressDate;

	int totalVerticesTmp = 0;
	int totalEdgesTmp = 0;
    int dbHitCountForVertexTmp = 0;
    int dbHitCountForEdgeTmp = 0;
    int nodeFoundInLocalCacheCountTmp = 0;
    int foundInDbCountTmp = 0;
    int falsePositiveCountTmp = 0;

    int reportProgressAverageTime = 60000; //ms

    void reportProgress() {
        long diff = Calendar.getInstance().getTime().getTime() - reportProgressDate.getTime();
        if (diff > reportProgressAverageTime) {
            logger.log(Level.INFO, "Node L1: Rate: " + (int) (falsePositiveCount - falsePositiveCountTmp)/(diff/reportProgressAverageTime) + " confirmed false +tive/min. Bloom filter probability: " + nodeBloomFilter.getFalsePositiveProbability());
            logger.log(Level.INFO, "Node L2: Rate: " + (int) (nodeFoundInLocalCacheCount - nodeFoundInLocalCacheCountTmp)/(diff/reportProgressAverageTime) +" node detection from local cache/min. Total: " + nodeFoundInLocalCacheCount);
            logger.log(Level.INFO, "Node L2: Rate: " + (int) (100.0*localNodeHashQueue.size()/NODE_VERTEX_LOCAL_CACHE_SIZE) +" % local node cache filled. Total: " + NODE_VERTEX_LOCAL_CACHE_SIZE);
            logger.log(Level.INFO, "Node L3: Rate: " + (int) (dbHitCountForVertex - dbHitCountForVertexTmp)/(diff/reportProgressAverageTime) +" db hit for vertexes from getVertices /min. Total: " + dbHitCountForVertex);
            logger.log(Level.INFO, "Node L3: Rate: " + (int) (foundInDbCount - foundInDbCountTmp)/(diff/reportProgressAverageTime) +" detection from db/min. Total: " + foundInDbCount);

            logger.log(Level.INFO, "Edges Rate: " + (int) (dbHitCountForEdge - dbHitCountForEdgeTmp)/(diff/reportProgressAverageTime) +" db hit for vertices from getEdges /min. Total: " + dbHitCountForEdge);
            logger.log(Level.INFO, "Count Vertices: " + (int) (totalVertices - totalVerticesTmp)/(diff/reportProgressAverageTime) +" nodes/min. Total: " + totalVertices);
            logger.log(Level.INFO, "Count Edges: " + (int) (totalEdges - totalEdgesTmp)/(diff/reportProgressAverageTime) +" edges/min. Total: " + totalEdges);

            reportProgressDate = Calendar.getInstance().getTime();

            totalVerticesTmp = totalVertices;
            totalEdgesTmp = totalEdges;
            dbHitCountForVertexTmp = dbHitCountForVertex;
            nodeFoundInLocalCacheCountTmp = nodeFoundInLocalCacheCount;
            foundInDbCountTmp = foundInDbCount;
            dbHitCountForEdgeTmp = dbHitCountForEdge;
            falsePositiveCountTmp = falsePositiveCount;
        }
    }

    public void putInLocalCache(Node vertex, int hashCode) {
		if (localNodeHashQueue.size() > NODE_VERTEX_LOCAL_CACHE_SIZE) {
		    localNodeCache.remove(localNodeHashQueue.removeFirst());
		}
		localNodeHashQueue.add(hashCode);
		localNodeCache.put(hashCode, vertex);
	}

	int txcount = 0;
	Transaction globalTx;

	private void txCommit() {
		try {
			if (txcount == 0) {
				globalTx = graphDb.beginTx();
			}
			txcount++;
			if (txcount >= FLUSH_TX_COUNT) {
				globalTx.success();
				// globalTx = graphDb.beginTx();
				txcount = 0;
				vertexIndexToCommit = new HashMap<String, HashMap<String, Node>>();
			}
		} catch (Exception exception) {
			logger.log(Level.SEVERE, "globalTx failure", exception);
			// try again?
		}
	}

	private HashMap<String, HashMap<String, Node>> vertexIndexToCommit = new HashMap<String, HashMap<String, Node>>();
    private void addToVertexIndex(Node node, String key, String value) {
		if (vertexIndexToCommit.containsKey(key)==false) {
			vertexIndexToCommit.put(key, new HashMap<String, Node>());
		}
		vertexIndexToCommit.get(key).put(value, node);
		try {
			vertexIndex.add(node, key, value);
		} catch (Exception exception) {
			logger.log(Level.SEVERE, "couldn't add to node index", exception);
		}
		txCommit();
    }

    private Node getFromVertexIndex(String key, String value) {
    	Node toReturn = null;
    	if (vertexIndexToCommit.containsKey(key)) {
	    	toReturn = (Node)((HashMap)vertexIndexToCommit.get(key)).get(value);
	    }
    	if (toReturn == null) {
    		txCommit();
    		try {
	    		return vertexIndex.get(key, value).getSingle();
	    	} catch (Exception exception) {
	    		logger.log(Level.SEVERE, "couldnt get from node index", exception);
	    	}
    	}
    	return toReturn;
    }

    private HashMap<String, HashMap<String, Relationship>> edgeIndexToCommit = new HashMap<String, HashMap<String, Relationship>>();
    private void addToEdgeIndex(Relationship edge, String key, String value) {
        if (edgeIndexToCommit.containsKey(key)==false) {
            edgeIndexToCommit.put(key, new HashMap<String, Relationship>());
        }
        edgeIndexToCommit.get(key).put(value, edge);
        try {
            edgeIndex.add(edge, key, value);
        } catch (Exception exception) {
            logger.log(Level.SEVERE, "couldn't add to edge index", exception);
        }
        txCommit();
    }

    private Relationship getFromEdgeIndex(String key, String value) {
        Relationship toReturn = null;
        if (edgeIndexToCommit.containsKey(key)) {
            toReturn = (Relationship)((HashMap)edgeIndexToCommit.get(key)).get(value);
        }
        if (toReturn == null) {
            txCommit();
            try {
                return edgeIndex.get(key, value).getSingle();
            } catch (Exception exception) {
                logger.log(Level.SEVERE, "couldnt get from edge index", exception);
            }
        }
        return toReturn;
    }



    @Override
    public boolean putVertex(AbstractVertex incomingVertex) {
    	totalVertices++;
    	reportProgress();

    	int hashCode = incomingVertex.hashCode();
        if (nodeBloomFilter.contains(hashCode)) { // L1
        	if (localNodeCache.containsKey(hashCode)) { // L2
            	nodeFoundInLocalCacheCount++;
                return false;
            } 
            dbHitCountForVertex++;

			// try ( Transaction tx = graphDb.beginTx() ) {
				Node newVertex;
				// newVertex = vertexIndex.get(NODE_HASHCODE_LABEL, hashCode).getSingle();
				newVertex = getFromVertexIndex(NODE_HASHCODE_LABEL, Long.toString(hashCode));
            	if (newVertex != null) { // L3, false positive check
					putInLocalCache(newVertex, hashCode);
                	foundInDbCount++;
                	return false;
            	} else {
                	falsePositiveCount++;
            	}
			// tx.success();
			// }
        }

        // try ( Transaction tx = graphDb.beginTx() ) {
        try {
        	txCommit();
            Node newVertex = graphDb.createNode(MyNodeTypes.VERTEX);
            for (Map.Entry<String, String> currentEntry : incomingVertex.getAnnotations().entrySet()) {
                String key = currentEntry.getKey();
                String value = currentEntry.getValue();
                if (key.equalsIgnoreCase(ID_STRING)) {
                    continue;
                }
                newVertex.setProperty(key, value);
                // vertexIndex.add(newVertex, key, value);
                addToVertexIndex(newVertex, key, value);
            }
            // does findNode check in index or in db directly? if in db only, then we need to uncomment this line
            newVertex.setProperty(NODE_HASHCODE_LABEL, hashCode);
            // vertexIndex.add(newVertex, NODE_HASHCODE_LABEL, Long.toString(hashCode));
            addToVertexIndex(newVertex, NODE_HASHCODE_LABEL, Long.toString(hashCode));
            newVertex.setProperty(ID_STRING, newVertex.getId());
            // vertexIndex.add(newVertex, ID_STRING, Long.toString(newVertex.getId()));
            addToVertexIndex(newVertex, ID_STRING, Long.toString(newVertex.getId()));
            nodeBloomFilter.add(hashCode);
            putInLocalCache(newVertex, hashCode);
                
            // tx.success();
        // } 
        } catch (Exception exception) {
        	logger.log(Level.SEVERE, "problem!", exception);
        }
        return true;
    }


    @Override
    public boolean putEdge(AbstractEdge incomingEdge) {
    	totalEdges++;
    	reportProgress();

        AbstractVertex srcVertex = incomingEdge.getSourceVertex();
        AbstractVertex dstVertex = incomingEdge.getDestinationVertex();


        // try ( Transaction tx = graphDb.beginTx() ) {

            int srcVertexHashCode = srcVertex.hashCode();
            int dstVertexHashCode = dstVertex.hashCode();

            if (!nodeBloomFilter.contains(srcVertexHashCode)) {
                putVertex(srcVertex);
            }

            if (!nodeBloomFilter.contains(dstVertexHashCode)) {
                putVertex(dstVertex);
            }

        	Node srcNode = localNodeCache.get(srcVertexHashCode);
        	Node dstNode = localNodeCache.get(dstVertexHashCode);

            if (srcNode == null) {
            	dbHitCountForEdge++;

                // srcNode = vertexIndex.get(NODE_HASHCODE_LABEL, srcVertexHashCode).getSingle();
                srcNode = getFromVertexIndex(NODE_HASHCODE_LABEL, Long.toString(srcVertexHashCode));
                putInLocalCache(srcNode, srcVertexHashCode);

            }
            if (dstNode == null) {
            	dbHitCountForEdge++;

                // dstNode = vertexIndex.get(NODE_HASHCODE_LABEL, dstVertexHashCode).getSingle();
                dstNode = getFromVertexIndex(NODE_HASHCODE_LABEL, Long.toString(dstVertexHashCode));
                putInLocalCache(dstNode, dstVertexHashCode);
            }

            txCommit();
            try{
                // TODO: edge duplication checks! right now another edge will be added
                Relationship newEdge = srcNode.createRelationshipTo(dstNode, MyRelationshipTypes.EDGE);
                for (Map.Entry<String, String> currentEntry : incomingEdge.getAnnotations().entrySet()) {
                    String key = currentEntry.getKey();
                    String value = currentEntry.getValue();
                    if (key.equalsIgnoreCase(ID_STRING)) {
                        continue;
                    }
                    newEdge.setProperty(key, value);
                    // edgeIndex.add(newEdge, key, value);
                    addToEdgeIndex(newEdge, key, value);
                }
                newEdge.setProperty(ID_STRING, newEdge.getId());
                // edgeIndex.add(newEdge, ID_STRING, Long.toString(newEdge.getId()));
                addToEdgeIndex(newEdge, ID_STRING, Long.toString(newEdge.getId()));
            } catch (Exception exception) {
                logger.log(Level.SEVERE, "problem!", exception);
            }
            // tx.success();
        // } 
        
        return true;
    }

    private AbstractVertex convertNodeToVertex(Node node) {
        AbstractVertex resultVertex = new Vertex();
        for (String key : node.getPropertyKeys()) {
            Object value = node.getProperty(key);
            if (value instanceof String) {
                resultVertex.addAnnotation(key, (String) value);
            } else if (value instanceof Long) {
                resultVertex.addAnnotation(key, Long.toString((Long) value));
            } else if (value instanceof Double) {
                resultVertex.addAnnotation(key, Double.toString((Double) value));
            }
        }
        return resultVertex;
    }

    private AbstractEdge convertRelationshipToEdge(Relationship relationship) {
        AbstractEdge resultEdge = new Edge((Vertex) convertNodeToVertex(relationship.getStartNode()), (Vertex) convertNodeToVertex(relationship.getEndNode()));
        for (String key : relationship.getPropertyKeys()) {
            Object value = relationship.getProperty(key);
            if (value instanceof String) {
                resultEdge.addAnnotation(key, (String) value);
            } else if (value instanceof Long) {
                resultEdge.addAnnotation(key, Long.toString((Long) value));
            } else if (value instanceof Double) {
                resultEdge.addAnnotation(key, Double.toString((Double) value));
            }
        }
        return resultEdge;
    }

    @Override
    public Graph getVertices(String expression) {

        try ( Transaction tx = graphDb.beginTx() ) {
            Graph resultGraph = new Graph();
            IndexHits<Node> queryHits = vertexIndex.query(expression);
            for (Node foundNode : queryHits) {
                resultGraph.putVertex(convertNodeToVertex(foundNode));
            }
            queryHits.close();
            tx.success();
            resultGraph.commitIndex();
            return resultGraph;
        }
    }

    @Override
    public Graph getEdges(String sourceExpression, String destinationExpression, String edgeExpression) {
        Graph resultGraph = new Graph();
        Set<AbstractVertex> sourceSet = null;
        Set<AbstractVertex> destinationSet = null;
        if (sourceExpression != null) {
            if (sourceExpression.trim().equalsIgnoreCase("null")) {
                sourceExpression = null;
            } else {
                sourceSet = getVertices(sourceExpression).vertexSet();
            }
        }
        if (destinationExpression != null) {
            if (destinationExpression.trim().equalsIgnoreCase("null")) {
                destinationExpression = null;
            } else {
                destinationSet = getVertices(destinationExpression).vertexSet();
            }
        }
        try( Transaction tx = graphDb.beginTx() ){
            IndexHits<Relationship> queryHits = edgeIndex.query(edgeExpression);
            for (Relationship foundRelationship : queryHits) {
                AbstractVertex sourceVertex = convertNodeToVertex(foundRelationship.getStartNode());
                AbstractVertex destinationVertex = convertNodeToVertex(foundRelationship.getEndNode());
                AbstractEdge tempEdge = convertRelationshipToEdge(foundRelationship);
                if ((sourceExpression != null) && (destinationExpression != null)) {
                    if (sourceSet.contains(tempEdge.getSourceVertex()) && destinationSet.contains(tempEdge.getDestinationVertex())) {
                        resultGraph.putVertex(sourceVertex);
                        resultGraph.putVertex(destinationVertex);
                        resultGraph.putEdge(tempEdge);
                    }
                } else if ((sourceExpression != null) && (destinationExpression == null)) {
                    if (sourceSet.contains(tempEdge.getSourceVertex())) {
                        resultGraph.putVertex(sourceVertex);
                        resultGraph.putVertex(destinationVertex);
                        resultGraph.putEdge(tempEdge);
                    }
                } else if ((sourceExpression == null) && (destinationExpression != null)) {
                    if (destinationSet.contains(tempEdge.getDestinationVertex())) {
                        resultGraph.putVertex(sourceVertex);
                        resultGraph.putVertex(destinationVertex);
                        resultGraph.putEdge(tempEdge);
                    }
                } else if ((sourceExpression == null) && (destinationExpression == null)) {
                    resultGraph.putVertex(sourceVertex);
                    resultGraph.putVertex(destinationVertex);
                    resultGraph.putEdge(tempEdge);
                }
            }
            queryHits.close();
            tx.success();
        }
        resultGraph.commitIndex();
        return resultGraph;
    }

    @Override
    public Graph getEdges(int srcVertexId, int dstVertexId) {
        Graph resultGraph = new Graph();
        try( Transaction tx = graphDb.beginTx() ){
            IndexHits<Relationship> queryHits = edgeIndex.query("type:*", graphDb.getNodeById(srcVertexId), graphDb.getNodeById(dstVertexId));
            for (Relationship currentRelationship : queryHits) {
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

        PathFinder<Path> pathFinder = GraphAlgoFactory.allSimplePaths(Traversal.expanderForAllTypes(Direction.OUTGOING), maxLength);
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
    public Graph getPaths(int srcVertexId, int dstVertexId, int maxLength) {
        return getPaths(ID_STRING + ":" + srcVertexId, ID_STRING + ":" + dstVertexId, maxLength);
    }

    @Override
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

    @Override
    public Graph getLineage(int vertexId, int depth, String direction, String terminatingExpression) {
    	return getLineage(ID_STRING + ":" + vertexId, depth, direction, terminatingExpression);
    }
    
    public String getHashOfEdge(AbstractEdge edge){
    	String completeEdgeString = edge.getSourceVertex().toString() + edge.toString() + edge.getDestinationVertex().toString();
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
        final GraphDatabaseService graphDb = new GraphDatabaseFactory().newEmbeddedDatabaseBuilder( dbpath )
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
                            node.setProperty(spade.storage.Neo4j.ID_STRING, node.getId());
                            vertexIndex.add(node, spade.storage.Neo4j.ID_STRING, Long.toString(node.getId())); 
                            
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
                            relationship.setProperty(spade.storage.Neo4j.ID_STRING, relationship.getId());
                            edgeIndex.add(relationship, spade.storage.Neo4j.ID_STRING, Long.toString(relationship.getId()));                        

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
        final int total;
        
        try ( Transaction tx = graphDb.beginTx() ) {
            total = IteratorUtil.count(GlobalGraphOperations.at(graphDb).getAllNodes()) + IteratorUtil.count(GlobalGraphOperations.at(graphDb).getAllRelationships());
            tx.success();
        }
        System.out.println("done.\n");

        int percentageCompleted = 0;
        int count = 0;

        try ( Transaction tx = graphDb.beginTx() ) {

            // index nodes
            Iterator<Node> nodeIterator = GlobalGraphOperations.at(graphDb).getAllNodes().iterator();
            Iterator<Relationship> edgeIterator = GlobalGraphOperations.at(graphDb).getAllRelationships().iterator();

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
