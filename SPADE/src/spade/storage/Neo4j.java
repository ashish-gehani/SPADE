/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2011 SRI International

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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.neo4j.graphalgo.GraphAlgoFactory;
import org.neo4j.graphalgo.PathFinder;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.graphdb.index.IndexManager;
import org.neo4j.graphdb.index.RelationshipIndex;
import org.neo4j.helpers.collection.MapUtil;
import org.neo4j.kernel.AbstractGraphDatabase;
import static org.neo4j.kernel.Config.ALLOW_STORE_UPGRADE;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.kernel.Traversal;
import org.neo4j.server.WrappingNeoServerBootstrapper;
import spade.core.*;

/**
 * Neo4j storage implementation.
 *
 * @author Dawood
 */
public class Neo4j extends AbstractStorage {

    // Number of transactions to buffer before committing to database
    private final int TRANSACTION_LIMIT = 50;
    // Number of transaction flushes before the database is shutdown and
    // restarted
    private final int HARD_FLUSH_LIMIT = 10000;
    // Identifying annotation to add to each edge/vertex
    private final String ID_STRING = "storageId";
    // String to match for when specifying direction for ancestors
    private final String direction_ancestors = "ancestors";
    // String to match for when specifying direction for descendants
    private final String direction_descendants = "descendants";
    // String to match for when specifying direction for both ancestors
    // and descendants
    private final String direction_both = "both";
    private GraphDatabaseService graphDb;
    private WrappingNeoServerBootstrapper webServer;
    private IndexManager index;
    private Index<Node> vertexIndex;
    private RelationshipIndex edgeIndex;
    private Transaction transaction;
    private int transactionCount;
    private int flushCount;
    private Map<Integer, Long> vertexMap;
    private Set<Integer> edgeSet;

    private enum MyRelationshipTypes implements RelationshipType {

        EDGE
    }

    @Override
    public boolean initialize(String arguments) {
        try {
            if (arguments == null) {
                return false;
            }
            // Create new database given the path as argument. Upgrade the database
            // if it already exists and is an older version
            graphDb = new EmbeddedGraphDatabase(arguments, MapUtil.stringMap(ALLOW_STORE_UPGRADE, "true"));
            // Initialize the web server for the embedded database
            webServer = new WrappingNeoServerBootstrapper((AbstractGraphDatabase) graphDb);
            index = graphDb.index();
            transactionCount = 0;
            flushCount = 0;
            // Create vertex index
            vertexIndex = index.forNodes("vertexIndex");
            // Create edge index
            edgeIndex = index.forRelationships("edgeIndex");
            // Create HashMap and HashSet to cache incoming edges/vertices
            vertexMap = new HashMap<Integer, Long>();
            edgeSet = new HashSet<Integer>();
            // Start the web server
            webServer.start();
            return true;
        } catch (Exception exception) {
            Logger.getLogger(Neo4j.class.getName()).log(Level.SEVERE, null, exception);
            return false;
        }
    }

    private void checkTransactionCount() {
        transactionCount++;
        if (transactionCount == TRANSACTION_LIMIT) {
            // If transaction limit is reached, commit the transactions
            try {
                transaction.success();
                transaction.finish();
            } catch (Exception exception) {
                Logger.getLogger(Neo4j.class.getName()).log(Level.SEVERE, null, exception);
            }
            // Reset transaction count and increase flush count
            transactionCount = 0;
            flushCount++;
            // If hard flush limit is reached, restart the database
            if (flushCount == HARD_FLUSH_LIMIT) {
                webServer.stop();
                graphDb.shutdown();
                graphDb = new EmbeddedGraphDatabase(arguments);
                webServer = new WrappingNeoServerBootstrapper((AbstractGraphDatabase) graphDb);
                vertexIndex = index.forNodes("vertexIndex");
                edgeIndex = index.forRelationships("edgeIndex");
                flushCount = 0;
            }
        }
    }

    @Override
    public boolean flushTransactions() {
        // Flush any pending transactions. This method is called by the Kernel
        // whenever a query is executed
        if (transaction != null) {
            transaction.success();
            transaction.finish();
            transactionCount = 0;
        }
        return true;
    }

    @Override
    public boolean shutdown() {
        // Flush all transactions before shutting down the database
        if (transaction != null) {
            transaction.success();
            transaction.finish();
        }
        webServer.stop();
        graphDb.shutdown();
        return true;
    }

    @Override
    public boolean putVertex(AbstractVertex incomingVertex) {
        if (vertexMap.containsKey(incomingVertex.hashCode())) {
            return false;
        }
        if (transactionCount == 0) {
            transaction = graphDb.beginTx();
        }
        Node newVertex = graphDb.createNode();
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
        vertexMap.put(incomingVertex.hashCode(), newVertex.getId());
        checkTransactionCount();
        return true;
    }

    @Override
    public boolean putEdge(AbstractEdge incomingEdge) {
        AbstractVertex srcVertex = incomingEdge.getSourceVertex();
        AbstractVertex dstVertex = incomingEdge.getDestinationVertex();
        if (!vertexMap.containsKey(srcVertex.hashCode())
                || !vertexMap.containsKey(dstVertex.hashCode())
                || !edgeSet.add(incomingEdge.hashCode())) {
            return false;
        }
        if (transactionCount == 0) {
            transaction = graphDb.beginTx();
        }
        Node srcNode = graphDb.getNodeById(vertexMap.get(srcVertex.hashCode()));
        Node dstNode = graphDb.getNodeById(vertexMap.get(dstVertex.hashCode()));

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
        checkTransactionCount();
        return true;
    }

    private AbstractVertex convertNodeToVertex(Node node) {
        AbstractVertex resultVertex = new Vertex();
        for (String key : node.getPropertyKeys()) {
            try {
                String value = (String) node.getProperty(key);
                resultVertex.addAnnotation(key, value);
            } catch (Exception fetchStringException) {
                try {
                    String longValue = Long.toString((Long) node.getProperty(key));
                    resultVertex.addAnnotation(key, longValue);
                } catch (Exception fetchLongException) {
                    String doubleValue = Double.toString((Double) node.getProperty(key));
                    resultVertex.addAnnotation(key, doubleValue);
                }
            }
        }
        return resultVertex;
    }

    private AbstractEdge convertRelationshipToEdge(Relationship relationship) {
        AbstractEdge resultEdge = new Edge((Vertex) convertNodeToVertex(relationship.getStartNode()), (Vertex) convertNodeToVertex(relationship.getEndNode()));
        for (String key : relationship.getPropertyKeys()) {
            try {
                String value = (String) relationship.getProperty(key);
                resultEdge.addAnnotation(key, value);
            } catch (Exception fetchStringException) {
                try {
                    String longValue = Long.toString((Long) relationship.getProperty(key));
                    resultEdge.addAnnotation(key, longValue);
                } catch (Exception fetchLongException) {
                    String doubleValue = Double.toString((Double) relationship.getProperty(key));
                    resultEdge.addAnnotation(key, doubleValue);
                }
            }
        }
        return resultEdge;
    }

    @Override
    public Graph getVertices(String expression) {
        Graph resultGraph = new Graph();
        IndexHits<Node> queryHits = vertexIndex.query(expression);
        for (Node foundNode : queryHits) {
            resultGraph.putVertex(convertNodeToVertex(foundNode));
        }
        queryHits.close();
        return resultGraph;
    }

    @Override
    public Graph getEdges(String sourceExpression, String destinationExpression, String edgeExpression) {
        Graph resultGraph = new Graph();
        Set<AbstractVertex> sourceSet = null;
        Set<AbstractVertex> destinationSet = null;
        if (sourceExpression != null) {
            sourceSet = getVertices(sourceExpression).vertexSet();
        }
        if (destinationExpression != null) {
            destinationSet = getVertices(destinationExpression).vertexSet();
        }
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
        return resultGraph;
    }

    @Override
    public Graph getEdges(String srcVertexId, String dstVertexId) {
        Graph resultGraph = new Graph();
        Long srcNodeId = Long.parseLong(srcVertexId);
        Long dstNodeId = Long.parseLong(dstVertexId);
        IndexHits<Relationship> queryHits = edgeIndex.query("type:*", graphDb.getNodeById(srcNodeId), graphDb.getNodeById(dstNodeId));
        for (Relationship currentRelationship : queryHits) {
            resultGraph.putVertex(convertNodeToVertex(currentRelationship.getStartNode()));
            resultGraph.putVertex(convertNodeToVertex(currentRelationship.getEndNode()));
            resultGraph.putEdge(convertRelationshipToEdge(currentRelationship));
        }
        queryHits.close();
        return resultGraph;
    }

    @Override
    public Graph getPaths(String srcVertexId, String dstVertexId, int maxLength) {
        Graph resultGraph = new Graph();

        Node sourceNode = graphDb.getNodeById(Long.parseLong(srcVertexId));
        Node destinationNode = graphDb.getNodeById(Long.parseLong(dstVertexId));

        PathFinder<Path> pathFinder = GraphAlgoFactory.allSimplePaths(Traversal.expanderForAllTypes(Direction.OUTGOING), maxLength);

        for (Path currentPath : pathFinder.findAllPaths(sourceNode, destinationNode)) {
            for (Node currentNode : currentPath.nodes()) {
                resultGraph.putVertex(convertNodeToVertex(currentNode));
            }
            for (Relationship currentRelationship : currentPath.relationships()) {
                resultGraph.putEdge(convertRelationshipToEdge(currentRelationship));
            }
        }

        return resultGraph;
    }

    @Override
    public Graph getLineage(String vertexId, int depth, String direction, String terminatingExpression) {
        Graph resultGraph = new Graph();

        Node sourceNode = graphDb.getNodeById(Long.parseLong(vertexId));
        resultGraph.putVertex(convertNodeToVertex(sourceNode));

        if ((terminatingExpression != null) && (terminatingExpression.trim().equalsIgnoreCase("null"))) {
            terminatingExpression = null;
        }

        Set<Node> terminatingSet = null;
        if (terminatingExpression != null) {
            terminatingSet = new HashSet<Node>();
            IndexHits<Node> queryHits = vertexIndex.query(terminatingExpression);
            for (Node foundNode : queryHits) {
                terminatingSet.add(foundNode);
            }
            queryHits.close();
        }

        Direction dir;
        if (direction_ancestors.startsWith(direction.toLowerCase())) {
            dir = Direction.OUTGOING;
        } else if (direction_descendants.startsWith(direction.toLowerCase())) {
            dir = Direction.INCOMING;
        } else if (direction_both.startsWith(direction.toLowerCase())) {
            dir = Direction.BOTH;
        } else {
            return null;
        }

        Set<Node> doneSet = new HashSet<Node>();
        Set<Node> tempSet = new HashSet<Node>();
        tempSet.add(sourceNode);
        int currentDepth = 0;
        while (true) {
            if ((tempSet.isEmpty()) || (depth == 0)) {
                break;
            }
            doneSet.addAll(tempSet);
            Set<Node> tempSet2 = new HashSet<Node>();
            for (Node tempNode : tempSet) {
                for (Relationship nodeRelationship : tempNode.getRelationships(dir)) {
                    Node otherNode = nodeRelationship.getOtherNode(tempNode);
                    if ((terminatingExpression != null) && (terminatingSet.contains(otherNode))) {
                        continue;
                    }
                    if (!doneSet.contains(otherNode)) {
                        tempSet2.add(otherNode);
                    }
                    resultGraph.putVertex(convertNodeToVertex(otherNode));
                    resultGraph.putEdge(convertRelationshipToEdge(nodeRelationship));
                    // Add network artifacts to the network map of the graph. This is needed
                    // to resolve remote queries
                    try {
                        if (((String) otherNode.getProperty("subtype")).equalsIgnoreCase("Network")) {
                            resultGraph.putNetworkVertex(convertNodeToVertex(otherNode), currentDepth);
                        }
                    } catch (Exception exception) {
                        // Ignore
                    }
                }
            }
            tempSet.clear();
            tempSet.addAll(tempSet2);
            depth--;
            currentDepth++;
        }

        return resultGraph;
    }
}
