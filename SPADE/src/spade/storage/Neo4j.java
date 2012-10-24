/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2012 SRI International

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
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.kernel.Traversal;
import spade.core.*;

/**
 * Neo4j storage implementation.
 *
 * @author Dawood Tariq
 */
public class Neo4j extends AbstractStorage {

    // Number of transactions to buffer before committing to database
    private final int TRANSACTION_LIMIT = 10000;
    // Number of transaction flushes before the database is shutdown and
    // restarted
    private final int HARD_FLUSH_LIMIT = 10;
    // Identifying annotation to add to each edge/vertex
    private final String ID_STRING = Query.STORAGE_ID_STRING;
    private final String VERTEX_INDEX = "vertexIndex";
    private final String EDGE_INDEX = "edgeIndex";
    private GraphDatabaseService graphDb;
    private IndexManager index;
    private Index<Node> vertexIndex;
    private RelationshipIndex edgeIndex;
    private Transaction transaction;
    private int transactionCount;
    private int flushCount;
    private Map<AbstractVertex, Long> vertexMap;

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
            graphDb = new EmbeddedGraphDatabase(arguments);
            index = graphDb.index();
            transactionCount = 0;
            flushCount = 0;
            // Create vertex index
            vertexIndex = index.forNodes(VERTEX_INDEX);
            // Create edge index
            edgeIndex = index.forRelationships(EDGE_INDEX);
            // Create HashMap to store IDs of incoming vertices
            vertexMap = new HashMap<AbstractVertex, Long>();

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
                graphDb.shutdown();
                graphDb = new EmbeddedGraphDatabase(arguments);
                vertexIndex = index.forNodes(VERTEX_INDEX);
                edgeIndex = index.forRelationships(EDGE_INDEX);
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
        graphDb.shutdown();
        return true;
    }

    @Override
    public boolean putVertex(AbstractVertex incomingVertex) {
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
        vertexMap.put(incomingVertex, newVertex.getId());
        checkTransactionCount();

        return true;
    }

    @Override
    public boolean putEdge(AbstractEdge incomingEdge) {
        AbstractVertex srcVertex = incomingEdge.getSourceVertex();
        AbstractVertex dstVertex = incomingEdge.getDestinationVertex();
        if (!vertexMap.containsKey(srcVertex) || !vertexMap.containsKey(dstVertex)) {
            return false;
        }
        if (transactionCount == 0) {
            transaction = graphDb.beginTx();
        }
        Node srcNode = graphDb.getNodeById(vertexMap.get(srcVertex));
        Node dstNode = graphDb.getNodeById(vertexMap.get(dstVertex));

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
        if (Query.DIRECTION_ANCESTORS.startsWith(direction.toLowerCase())) {
            dir = Direction.OUTGOING;
        } else if (Query.DIRECTION_DESCENDANTS.startsWith(direction.toLowerCase())) {
            dir = Direction.INCOMING;
        } else if (Query.DIRECTION_BOTH.startsWith(direction.toLowerCase())) {
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
            Set<Node> newTempSet = new HashSet<Node>();
            for (Node tempNode : tempSet) {
                for (Relationship nodeRelationship : tempNode.getRelationships(dir)) {
                    Node otherNode = nodeRelationship.getOtherNode(tempNode);
                    if ((terminatingExpression != null) && (terminatingSet.contains(otherNode))) {
                        continue;
                    }
                    if (!doneSet.contains(otherNode)) {
                        newTempSet.add(otherNode);
                    }
                    resultGraph.putVertex(convertNodeToVertex(otherNode));
                    resultGraph.putEdge(convertRelationshipToEdge(nodeRelationship));
                    // Add network artifacts to the network map of the graph. This is needed
                    // to resolve remote queries
                    try {
                        if (((String) otherNode.getProperty("subtype")).equalsIgnoreCase("network")) {
                            resultGraph.putNetworkVertex(convertNodeToVertex(otherNode), currentDepth);
                        }
                    } catch (Exception exception) {
                        // Ignore
                    }
                }
            }
            tempSet.clear();
            tempSet.addAll(newTempSet);
            depth--;
            currentDepth++;
        }

        return resultGraph;
    }
}
