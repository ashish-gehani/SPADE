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
import spade.core.Lineage;
import spade.core.AbstractEdge;
import spade.core.Edge;
import spade.core.Vertex;
import spade.opm.edge.WasTriggeredBy;
import spade.opm.edge.WasControlledBy;
import spade.opm.edge.WasGeneratedBy;
import spade.opm.edge.Used;
import spade.opm.edge.WasDerivedFrom;
import spade.opm.vertex.Artifact;
import spade.opm.vertex.Agent;
import spade.core.AbstractVertex;
import spade.opm.vertex.Process;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.RelationshipIndex;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.helpers.collection.MapUtil;
import org.neo4j.index.impl.lucene.ValueContext;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.index.IndexHits;

public class Neo4j extends AbstractStorage {

    private final int TRANSACTION_LIMIT = 10000;
    private GraphDatabaseService graphDb;
    private Index<Node> vertexIndex;
    private RelationshipIndex edgeIndex;
    private Transaction transaction;
    private int transactionCount;
    private HashMap<AbstractVertex, Long> vertexTable;
    private HashSet edgeSet;

    public enum MyRelationshipTypes implements RelationshipType {

        EDGE
    }

    @Override
    public boolean initialize(String arguments) {
        try {
            graphDb = new EmbeddedGraphDatabase(arguments);
            transactionCount = 0;
            vertexIndex = graphDb.index().forNodes("vertexIndex", MapUtil.stringMap("provider", "lucene", "type", "fulltext"));
            edgeIndex = graphDb.index().forRelationships("edgeIndex", MapUtil.stringMap("provider", "lucene", "type", "fulltext"));
            vertexTable = new HashMap<AbstractVertex, Long>();
            edgeSet = new HashSet();
            return true;
        } catch (Exception exception) {
            exception.printStackTrace(System.err);
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
                exception.printStackTrace(System.err);
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
        if (vertexTable.containsKey(incomingVertex)) {
            return false;
        }
        if (transactionCount == 0) {
            transaction = graphDb.beginTx();
        }
        Node newVertex = graphDb.createNode();
        Map<String, String> annotations = incomingVertex.getAnnotations();
        for (Iterator iterator = annotations.keySet().iterator(); iterator.hasNext();) {
            String key = (String) iterator.next();
            String value = (String) annotations.get(key);
            if (key.equalsIgnoreCase("storageId")) {
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
        newVertex.setProperty("storageId", newVertex.getId());
        vertexIndex.add(newVertex, "storageId", new ValueContext(newVertex.getId()).indexNumeric());
        vertexTable.put(incomingVertex, newVertex.getId());
        checkTransactionCount();
        return true;
    }

    @Override
    public boolean putEdge(AbstractEdge incomingEdge) {
        AbstractVertex srcVertex = incomingEdge.getSrcVertex();
        AbstractVertex dstVertex = incomingEdge.getDstVertex();
        if (edgeSet.add(incomingEdge.hashCode()) == false) {
            return false;
        }
        if (transactionCount == 0) {
            transaction = graphDb.beginTx();
        }
        Long srcNodeId = (Long) vertexTable.get(srcVertex);
        Long dstNodeId = (Long) vertexTable.get(dstVertex);
        Node srcNode = graphDb.getNodeById(srcNodeId);
        Node dstNode = graphDb.getNodeById(dstNodeId);

        Map<String, String> annotations = incomingEdge.getAnnotations();
        Relationship newEdge = srcNode.createRelationshipTo(dstNode, MyRelationshipTypes.EDGE);
        for (Iterator iterator = annotations.keySet().iterator(); iterator.hasNext();) {
            String key = (String) iterator.next();
            String value = (String) annotations.get(key);
            if (key.equalsIgnoreCase("storageId")) {
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
        newEdge.setProperty("storageId", newEdge.getId());
        edgeIndex.add(newEdge, "storageId", new ValueContext(newEdge.getId()).indexNumeric());

        checkTransactionCount();
        return true;
    }

    private AbstractVertex convertNodeToVertex(Node node) {
        AbstractVertex resultVertex = null;
        String type = (String) node.getProperty("type");
        if (type.equals("Process")) {
            resultVertex = new Process();
        } else if (type.equals("Artifact")) {
            resultVertex = new Artifact();
        } else if (type.equals("Agent")) {
            resultVertex = new Agent();
        } else {
            resultVertex = new Vertex();
        }
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
        AbstractEdge resultEdge = null;
        String relationshipType = (String) relationship.getProperty("type");
        if (relationshipType.equals("Used")) {
            resultEdge = new Used((Process) convertNodeToVertex(relationship.getStartNode()), (Artifact) convertNodeToVertex(relationship.getEndNode()));
        } else if (relationshipType.equals("WasGeneratedBy")) {
            resultEdge = new WasGeneratedBy((Artifact) convertNodeToVertex(relationship.getStartNode()), (Process) convertNodeToVertex(relationship.getEndNode()));
        } else if (relationshipType.equals("WasTriggeredBy")) {
            resultEdge = new WasTriggeredBy((Process) convertNodeToVertex(relationship.getStartNode()), (Process) convertNodeToVertex(relationship.getEndNode()));
        } else if (relationshipType.equals("WasControlledBy")) {
            resultEdge = new WasControlledBy((Process) convertNodeToVertex(relationship.getStartNode()), (Agent) convertNodeToVertex(relationship.getEndNode()));
        } else if (relationshipType.equals("WasDerivedFrom")) {
            resultEdge = new WasDerivedFrom((Artifact) convertNodeToVertex(relationship.getStartNode()), (Artifact) convertNodeToVertex(relationship.getEndNode()));
        } else {
            resultEdge = new Edge((Vertex) convertNodeToVertex(relationship.getStartNode()), (Vertex) convertNodeToVertex(relationship.getEndNode()));
        }
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

    private Node convertVertexToNode(AbstractVertex sourceVertex) {
        String expression = "";
        Map<String, String> sourceAnnotations = sourceVertex.getAnnotations();
        for (Map.Entry<String, String> entry : sourceAnnotations.entrySet()) {
            String key = (String) entry.getKey();
            String value = (String) entry.getValue();
            expression = expression + key + ":" + "\"" + value + "\" AND ";
        }
        if (expression.length() > 4) {
            expression = expression.substring(0, expression.length() - 4);
        }
        Node resultNode = vertexIndex.query(expression).getSingle();
        return resultNode;
    }

    @Override
    public Set<AbstractVertex> getVertices(String expression) {
        Set<AbstractVertex> vertexSet = new HashSet<AbstractVertex>();
        for (Node foundNode : vertexIndex.query(expression)) {
            vertexSet.add(convertNodeToVertex(foundNode));
        }
        return vertexSet;
    }

    @Override
    public Set<AbstractEdge> getEdges(String sourceExpression, String destinationExpression, String edgeExpression) {
        Set<AbstractVertex> sourceSet = null;
        Set<AbstractVertex> destinationSet = null;
        if (sourceExpression != null) {
            sourceSet = getVertices(sourceExpression);
        }
        if (destinationExpression != null) {
            destinationSet = getVertices(destinationExpression);
        }
        Set<AbstractEdge> resultSet = new HashSet<AbstractEdge>();
        for (Relationship foundRelationship : edgeIndex.query(edgeExpression)) {
            AbstractEdge tempEdge = convertRelationshipToEdge(foundRelationship);
            if ((sourceExpression != null) && (destinationExpression != null)) {
                if (sourceSet.contains(tempEdge.getSrcVertex()) && destinationSet.contains(tempEdge.getDstVertex())) {
                    resultSet.add(tempEdge);
                }
            } else if ((sourceExpression != null) && (destinationExpression == null)) {
                if (sourceSet.contains(tempEdge.getSrcVertex())) {
                    resultSet.add(tempEdge);
                }
            } else if ((sourceExpression == null) && (destinationExpression != null)) {
                if (destinationSet.contains(tempEdge.getDstVertex())) {
                    resultSet.add(tempEdge);
                }
            } else if ((sourceExpression == null) && (destinationExpression == null)) {
                resultSet.add(tempEdge);
            }
        }
        return resultSet;
    }

    @Override
    public Set<AbstractEdge> getEdges(String srcVertexId, String dstVertexId) {
        Long srcNodeId = Long.parseLong(srcVertexId);
        Long dstNodeId = Long.parseLong(dstVertexId);
        Set<AbstractEdge> resultSet = new HashSet<AbstractEdge>();
        IndexHits<Relationship> foundRelationships = edgeIndex.query("type:*", graphDb.getNodeById(srcNodeId), graphDb.getNodeById(dstNodeId));
        while (foundRelationships.hasNext()) {
            resultSet.add(convertRelationshipToEdge(foundRelationships.next()));
        }
        return resultSet;
    }

    @Override
    public Lineage getLineage(String vertexId, int depth, String direction, String terminatingExpression) {
        Lineage resultLineage = new Lineage();

        Long sourceNodeId = Long.parseLong(vertexId);
        Node sourceNode = graphDb.getNodeById(sourceNodeId);
        resultLineage.putVertex(convertNodeToVertex(sourceNode));

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
        if (direction.equalsIgnoreCase("ancestors")) {
            dir = Direction.OUTGOING;
        } else if (direction.equalsIgnoreCase("descendants")) {
            dir = Direction.INCOMING;
        } else {
            return null;
        }

        Set<Node> tempSet = new HashSet<Node>();
        tempSet.add(sourceNode);
        while (true) {
            if ((tempSet.isEmpty()) || (depth == 0)) {
                break;
            }
            Set<Node> tempSet2 = new HashSet<Node>();
            Iterator iterator = tempSet.iterator();
            while (iterator.hasNext()) {
                Node tempNode = (Node) iterator.next();
                for (Relationship nodeRelationship : tempNode.getRelationships(dir)) {
                    Node otherNode = null;
                    if (dir == Direction.OUTGOING) {
                        otherNode = nodeRelationship.getEndNode();
                    } else {
                        otherNode = nodeRelationship.getStartNode();
                    }
                    if (terminatingExpression != null) {
                        if (terminatingSet.contains(otherNode)) {
                            continue;
                        } else {
                            tempSet2.add(otherNode);
                        }
                    } else {
                        tempSet2.add(otherNode);
                    }
                    resultLineage.putVertex(convertNodeToVertex(otherNode));
                    resultLineage.putEdge(convertRelationshipToEdge(nodeRelationship));
                }
            }
            tempSet.clear();
            tempSet.addAll(tempSet2);
            depth--;
        }

        return resultLineage;
    }
}
