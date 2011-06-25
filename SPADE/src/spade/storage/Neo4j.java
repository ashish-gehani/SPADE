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

import spade.core.AbstractStorage;
import spade.core.Graph;
import spade.core.AbstractEdge;
import spade.core.Edge;
import spade.core.Vertex;
import spade.core.AbstractVertex;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.neo4j.graphalgo.GraphAlgoFactory;
import org.neo4j.graphalgo.PathFinder;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.RelationshipIndex;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.helpers.collection.MapUtil;
import org.neo4j.index.lucene.ValueContext;
import org.neo4j.kernel.Traversal;

public class Neo4j extends AbstractStorage {

    private final int TRANSACTION_LIMIT = 1000;
    private final String ID_STRING = "storageId";
    private GraphDatabaseService graphDb;
    private Index<Node> vertexIndex;
    private RelationshipIndex edgeIndex;
    private Transaction transaction;
    private int transactionCount;
    private Map<Integer, Long> vertexMap;
    private Set<Integer> edgeSet;

    public enum MyRelationshipTypes implements RelationshipType {

        EDGE
    }

    @Override
    public boolean initialize(String arguments) {
        try {
            if (arguments == null) {
                return false;
            }
            graphDb = new EmbeddedGraphDatabase(arguments);
            transactionCount = 0;
            vertexIndex = graphDb.index().forNodes("vertexIndex", MapUtil.stringMap("provider", "lucene", "type", "fulltext"));
            edgeIndex = graphDb.index().forRelationships("edgeIndex", MapUtil.stringMap("provider", "lucene", "type", "fulltext"));
            vertexMap = new HashMap<Integer, Long>();
            edgeSet = new HashSet<Integer>();
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private void checkTransactionCount() {
        transactionCount++;
        if (transactionCount == TRANSACTION_LIMIT) {
            transactionCount = 0;
            try {
                transaction.success();
                transaction.finish();
            } catch (Exception exception) {
            }
        }
    }

    @Override
    public boolean flushTransactions() {
        if (transaction != null) {
            transaction.success();
            transaction.finish();
            transaction = graphDb.beginTx();
            transactionCount = 0;
        }
        return true;
    }

    @Override
    public boolean shutdown() {
        if (transaction != null) {
            transaction.success();
            transaction.finish();
        }
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
        for (Map.Entry currentEntry : incomingVertex.getAnnotations().entrySet()) {
            String key = (String) currentEntry.getKey();
            String value = (String) currentEntry.getValue();
            if (key.equalsIgnoreCase(ID_STRING)) {
                continue;
            }
            try {
                Long longValue = Long.parseLong(value);
                newVertex.setProperty(key, longValue);
                vertexIndex.add(newVertex, key, new ValueContext(longValue).indexNumeric());
            } catch (Exception parseLongException) {
                try {
                    Double doubleValue = Double.parseDouble(value);
                    newVertex.setProperty(key, doubleValue);
                    vertexIndex.add(newVertex, key, new ValueContext(doubleValue).indexNumeric());
                } catch (Exception parseDoubleException) {
                    newVertex.setProperty(key, value);
                    vertexIndex.add(newVertex, key, value);
                }
            }
        }
        newVertex.setProperty(ID_STRING, newVertex.getId());
        vertexIndex.add(newVertex, ID_STRING, new ValueContext(newVertex.getId()).indexNumeric());
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
        for (Map.Entry currentEntry : incomingEdge.getAnnotations().entrySet()) {
            String key = (String) currentEntry.getKey();
            String value = (String) currentEntry.getValue();
            if (key.equalsIgnoreCase(ID_STRING)) {
                continue;
            }
            try {
                Long longValue = Long.parseLong(value);
                newEdge.setProperty(key, longValue);
                edgeIndex.add(newEdge, key, new ValueContext(longValue).indexNumeric());
            } catch (Exception parseLongException) {
                try {
                    Double doubleValue = Double.parseDouble(value);
                    newEdge.setProperty(key, doubleValue);
                    edgeIndex.add(newEdge, key, new ValueContext(doubleValue).indexNumeric());
                } catch (Exception parseDoubleException) {
                    newEdge.setProperty(key, value);
                    edgeIndex.add(newEdge, key, value);
                }
            }
        }
        newEdge.setProperty(ID_STRING, newEdge.getId());
        edgeIndex.add(newEdge, ID_STRING, new ValueContext(newEdge.getId()).indexNumeric());

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
        for (Node foundNode : vertexIndex.query(expression)) {
            resultGraph.putVertex(convertNodeToVertex(foundNode));
        }
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
        for (Relationship foundRelationship : edgeIndex.query(edgeExpression)) {
            AbstractEdge tempEdge = convertRelationshipToEdge(foundRelationship);
            if ((sourceExpression != null) && (destinationExpression != null)) {
                if (sourceSet.contains(tempEdge.getSourceVertex()) && destinationSet.contains(tempEdge.getDestinationVertex())) {
                    resultGraph.putEdge(tempEdge);
                }
            } else if ((sourceExpression != null) && (destinationExpression == null)) {
                if (sourceSet.contains(tempEdge.getSourceVertex())) {
                    resultGraph.putEdge(tempEdge);
                }
            } else if ((sourceExpression == null) && (destinationExpression != null)) {
                if (destinationSet.contains(tempEdge.getDestinationVertex())) {
                    resultGraph.putEdge(tempEdge);
                }
            } else if ((sourceExpression == null) && (destinationExpression == null)) {
                resultGraph.putEdge(tempEdge);
            }
        }
        return resultGraph;
    }

    @Override
    public Graph getEdges(String srcVertexId, String dstVertexId) {
        Graph resultGraph = new Graph();
        Long srcNodeId = Long.parseLong(srcVertexId);
        Long dstNodeId = Long.parseLong(dstVertexId);
        IndexHits<Relationship> foundRelationships = edgeIndex.query("type:*", graphDb.getNodeById(srcNodeId), graphDb.getNodeById(dstNodeId));
        while (foundRelationships.hasNext()) {
            resultGraph.putEdge(convertRelationshipToEdge(foundRelationships.next()));
        }
        return resultGraph;
    }

    @Override
    public Graph getPaths(String srcVertexId, String dstVertexId, int maxLength) {
        Graph resultGraph = new Graph();

        Node sourceNode = graphDb.getNodeById(Long.parseLong(srcVertexId));
        Node destinationNode = graphDb.getNodeById(Long.parseLong(dstVertexId));

        PathFinder<Path> pathFinder = GraphAlgoFactory.allSimplePaths(Traversal.expanderForAllTypes(Direction.INCOMING), maxLength);

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
            for (Node foundNode : vertexIndex.query(terminatingExpression)) {
                terminatingSet.add(foundNode);
            }
        }

        Direction dir = null;
        if (direction.trim().equalsIgnoreCase("a")) {
            dir = Direction.OUTGOING;
        } else if (direction.trim().equalsIgnoreCase("d")) {
            dir = Direction.INCOMING;
        } else if (direction.trim().equalsIgnoreCase("b")) {
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
                    if (otherNode.hasProperty("source port")) {
                        resultGraph.putNetworkVertex(convertNodeToVertex(otherNode), currentDepth);
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
