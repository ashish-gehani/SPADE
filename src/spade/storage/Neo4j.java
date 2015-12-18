/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2015 SRI International

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

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
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.graphdb.index.IndexManager;
import org.neo4j.graphdb.index.RelationshipIndex;
import org.neo4j.kernel.Traversal;

import spade.core.AbstractEdge;
import spade.core.AbstractStorage;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Graph;
import spade.core.Settings;
import spade.core.Vertex;

/**
 * Neo4j storage implementation.
 *
 * @author Dawood Tariq
 */
public class Neo4j extends AbstractStorage {

    // Number of transactions to buffer before committing to database
    private static final int TRANSACTION_LIMIT = 10000;
    // Number of transaction flushes before the database is shutdown and
    // restarted
    private static final int HARD_FLUSH_LIMIT = 10;
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
    private Transaction transaction;
    private int transactionCount;
    private int flushCount;
    private Map<String, Long> spadeNeo4jCache;
    private Map<String, Long> uncommittedSpadeNeo4jCache;
    private final Pattern longPattern = Pattern.compile("^[-+]?[0-9]+$");
    private final Pattern doublePattern = Pattern.compile("^[-+]?[0-9]*\\.?[0-9]+([eE][-+]?[0-9]+)?$");
    static final Logger logger = Logger.getLogger(Neo4j.class.getName());
    private final String NEO_CONFIG_FILE = "cfg/neo4j.properties";

    private enum MyRelationshipTypes implements RelationshipType { EDGE }

    private enum MyNodeTypes implements Label { VERTEX }
    
    private String neo4jDatabaseDirectoryPath = null;
    private String spadeNeo4jCacheFilePath = "spade-neo4j-cache";

    @Override
    public boolean initialize(String arguments) {
        try {
        	neo4jDatabaseDirectoryPath = arguments;
            if (neo4jDatabaseDirectoryPath == null) {
                return false;
            }
            GraphDatabaseBuilder graphDbBuilder = new GraphDatabaseFactory().newEmbeddedDatabaseBuilder(neo4jDatabaseDirectoryPath);
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
            // Create HashMap to store IDs of incoming vertices
            transactionCount = 0;
            flushCount = 0;
            spadeNeo4jCache = new HashMap<String, Long>();
            uncommittedSpadeNeo4jCache = new HashMap<String, Long>();
            loadSpadeNeo4jCache();
            
            return true;
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
            return false;
        }
    }
    
    private void loadSpadeNeo4jCache(){
    	File spadeNeo4jCacheFile = new File(neo4jDatabaseDirectoryPath + File.separatorChar + spadeNeo4jCacheFilePath);
    	if(spadeNeo4jCacheFile.exists()){
    		long start = System.currentTimeMillis();
    		ObjectInputStream cacheObjectInputStream = null;
    		try{
    			cacheObjectInputStream = new ObjectInputStream(new FileInputStream(spadeNeo4jCacheFile));
    			spadeNeo4jCache = (Map<String, Long>)cacheObjectInputStream.readObject();
    		}catch(Exception e){
    			logger.log(Level.SEVERE, "Failed to read the spade-neo4j cache", e);
    		}finally{
    			try{
    				if(cacheObjectInputStream != null){
    					cacheObjectInputStream.close();
    				}
    			}catch(Exception e){
    				logger.log(Level.SEVERE, "Failed to close cache file reader", e);
    			}
    		}
    		logger.log(Level.INFO, "Loaded cache size = " + spadeNeo4jCache.size() + " in time " + (System.currentTimeMillis() - start) + " ms");
    	}
    }

    private void checkTransactionCount() {
        transactionCount++;
        if (transactionCount == TRANSACTION_LIMIT) {
            // If transaction limit is reached, commit the transactions
            commitTransaction(transaction);
            //increase flush count
            flushCount++;
            // If hard flush limit is reached, restart the database
            if (flushCount == HARD_FLUSH_LIMIT) {
                logger.log(Level.INFO, "Hard flush limit reached - restarting database");
                graphDb.shutdown();
                graphDb = new GraphDatabaseFactory().newEmbeddedDatabase(arguments);
                index = graphDb.index();
                try ( Transaction tx = graphDb.beginTx() ) {
                    vertexIndex = index.forNodes(VERTEX_INDEX);
                    edgeIndex = index.forRelationships(EDGE_INDEX);
                    tx.success();
                }
                flushCount = 0;
            }
        }
    }
    
    private void commitTransaction(Transaction transaction){
    	boolean success = false;
        if (transaction != null) {
        	try{
        		transaction.success();
        		success = true;
        	}catch(Exception e){
        		logger.log(Level.SEVERE, null, e);
        	} finally {
                transaction.close();
            }
        }
    	commitCache(success);
    	// Reset transaction count
    	transactionCount = 0;
    }

    @Override
    public boolean flushTransactions() {
        // Flush any pending transactions. This method is called by the Kernel
        // whenever a query is executed
    	commitTransaction(transaction);
        return true;
    }

    @Override
    public boolean shutdown() {
        // Flush all transactions before shutting down the database
    	commitTransaction(transaction);
        graphDb.shutdown();
        saveSpadeNeo4jCache();
        return true;
    }
    
    private void saveSpadeNeo4jCache(){
    	ObjectOutputStream cacheObjectOutputStream = null;
    	try{
	    	File spadeNeo4jCacheFile = new File(neo4jDatabaseDirectoryPath + File.separator + spadeNeo4jCacheFilePath);
	    	if(spadeNeo4jCacheFile.exists()){
	    		spadeNeo4jCacheFile.delete();
	    	}
	    	long start = System.currentTimeMillis();
	    	cacheObjectOutputStream = new ObjectOutputStream(new FileOutputStream(spadeNeo4jCacheFile));
	    	cacheObjectOutputStream.writeObject(spadeNeo4jCache);
	    	logger.log(Level.INFO, "Saved cache size = " + spadeNeo4jCache.size() + " in time " + (System.currentTimeMillis() - start) + " ms");
    	}catch(Exception e){
    		logger.log(Level.SEVERE, "Failed to save spade neo4j cache", e);
    	}finally{
    		try{
    			if(cacheObjectOutputStream != null){
    				cacheObjectOutputStream.close();
    			}
    		}catch(Exception e){
    			logger.log(Level.SEVERE, "Failed to close cache output writer", e);
    		}
    	}
    }

    @Override
    public boolean putVertex(AbstractVertex incomingVertex) {
    	if(existsInCache(getHashOfVertex(incomingVertex))){
    		return false;
    	}
        try {
            if (transactionCount == 0) {
                transaction = graphDb.beginTx();
            }
            try ( Transaction tx = graphDb.beginTx() ) {
                Node newVertex = graphDb.createNode(MyNodeTypes.VERTEX);
                for (Map.Entry<String, String> currentEntry : incomingVertex.getAnnotations().entrySet()) {
                    String key = currentEntry.getKey();
                    String value = currentEntry.getValue();
                    if (key.equalsIgnoreCase(ID_STRING)) {
                        continue;
                    }
                    newVertex.setProperty(key, value);
                    vertexIndex.add(newVertex, key, value);
                }
                newVertex.setProperty(ID_STRING, newVertex.getId());
                vertexIndex.add(newVertex, ID_STRING, Long.toString(newVertex.getId()));
                putInCache(getHashOfVertex(incomingVertex), newVertex.getId());
                checkTransactionCount();
                tx.success();
            } 
            return true;
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
            return false;
        }
    }

    @Override
    public boolean putEdge(AbstractEdge incomingEdge) {
    	if(existsInCache(getHashOfEdge(incomingEdge))){
    		return false;
    	}
        try {
            AbstractVertex srcVertex = incomingEdge.getSourceVertex();
            AbstractVertex dstVertex = incomingEdge.getDestinationVertex();
            if (!existsInCache(getHashOfVertex(srcVertex)) || !existsInCache(getHashOfVertex(dstVertex))) {
                return false;
            }
            if (transactionCount == 0) {
                transaction = graphDb.beginTx();
            }
            try ( Transaction tx = graphDb.beginTx() ) {
                Node srcNode = graphDb.getNodeById(getFromCache(getHashOfVertex(srcVertex)));
                Node dstNode = graphDb.getNodeById(getFromCache(getHashOfVertex(dstVertex)));

                Relationship newEdge = srcNode.createRelationshipTo(dstNode, MyRelationshipTypes.EDGE);
                for (Map.Entry<String, String> currentEntry : incomingEdge.getAnnotations().entrySet()) {
                    String key = currentEntry.getKey();
                    String value = currentEntry.getValue();
                    if (key.equalsIgnoreCase(ID_STRING)) {
                        continue;
                    }
                    newEdge.setProperty(key, value);
                    edgeIndex.add(newEdge, key, value);
                }
                newEdge.setProperty(ID_STRING, newEdge.getId());
                edgeIndex.add(newEdge, ID_STRING, Long.toString(newEdge.getId()));
                putInCache(getHashOfEdge(incomingEdge), newEdge.getId());
                checkTransactionCount();
                tx.success();
            } 
            
            return true;
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
            return false;
        }
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
    
    private boolean existsInCache(String hash){
    	return spadeNeo4jCache.get(hash) != null || uncommittedSpadeNeo4jCache.get(hash) != null;
    }
    
    private void putInCache(String hash, Long id){
    	uncommittedSpadeNeo4jCache.put(hash, id);
    }
    
    private Long getFromCache(String hash){
    	Long value = spadeNeo4jCache.get(hash);
    	if(value == null){
    		value = uncommittedSpadeNeo4jCache.get(hash);
    	}
    	return value;
    }
    
    private void commitCache(boolean success){
    	if(success){
    		spadeNeo4jCache.putAll(uncommittedSpadeNeo4jCache);
    	}
    	uncommittedSpadeNeo4jCache.clear();
    }
}
