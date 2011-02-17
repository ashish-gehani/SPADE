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
    public boolean initialize(String path) {
        try {
            graphDb = new EmbeddedGraphDatabase(path);
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

    private void commit() {
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
        }
        return true;
    }

    private AbstractVertex fixVertex(AbstractVertex vertex) {
        if (vertex instanceof Artifact) {
            vertex.addAnnotation("type", "Artifact");
        } else if (vertex instanceof Process) {
            vertex.addAnnotation("type", "Process");
        } else if (vertex instanceof Agent) {
            vertex.addAnnotation("type", "Agent");
        }
        return vertex;
    }

    private AbstractEdge fixEdge(AbstractEdge edge) {
        if (edge instanceof Used) {
            edge.addAnnotation("type", "Used");
        } else if (edge instanceof WasControlledBy) {
            edge.addAnnotation("type", "WasControlledBy");
        } else if (edge instanceof WasDerivedFrom) {
            edge.addAnnotation("type", "WasDerivedFrom");
        } else if (edge instanceof WasGeneratedBy) {
            edge.addAnnotation("type", "WasGeneratedBy");
        } else if (edge instanceof WasTriggeredBy) {
            edge.addAnnotation("type", "WasTriggeredBy");
        }
        return edge;
    }

    @Override
    public boolean putVertex(AbstractVertex incomingVertex) {
        if (transactionCount == 0) {
            transaction = graphDb.beginTx();
        }
        incomingVertex = fixVertex(incomingVertex);
        if (vertexTable.get(incomingVertex) != null) {
            return false;
        }
        Node newVertex = graphDb.createNode();
        Map<String, String> annotations = incomingVertex.getAnnotations();
        for (Iterator iterator = annotations.keySet().iterator(); iterator.hasNext();) {
            String key = (String) iterator.next();
            String value = (String) annotations.get(key);
            if (key.equals("vertexId")) {
                continue;
            }
            try {
                Long val = Long.parseLong(value);
                newVertex.setProperty(key, val);
                vertexIndex.add(newVertex, key, new ValueContext(val).indexNumeric());
            } catch (Exception parseLongException) {
                try {
                    Double val = Double.parseDouble(value);
                    newVertex.setProperty(key, val);
                    vertexIndex.add(newVertex, key, new ValueContext(val).indexNumeric());
                } catch (Exception parseDoubleException) {
                    newVertex.setProperty(key, value);
                    vertexIndex.add(newVertex, key, value);
                }
            }
        }
        newVertex.setProperty("vertexId", newVertex.getId());
        vertexIndex.add(newVertex, "vertexId", new ValueContext(newVertex.getId()).indexNumeric());
        vertexTable.put(incomingVertex, newVertex.getId());
        commit();
        return true;
    }

    @Override
    public boolean putEdge(AbstractEdge incomingEdge) {
        if (transactionCount == 0) {
            transaction = graphDb.beginTx();
        }
        AbstractVertex vsrc = fixVertex(incomingEdge.getSrcVertex());
        AbstractVertex vdst = fixVertex(incomingEdge.getDstVertex());
        incomingEdge = fixEdge(incomingEdge);
        if (edgeSet.add(incomingEdge.hashCode()) == false) {
            return false;
        }
        Long srcNodeId = (Long) vertexTable.get(vsrc);
        Long dstNodeId = (Long) vertexTable.get(vdst);
        Node srcNode = graphDb.getNodeById(srcNodeId);
        Node dstNode = graphDb.getNodeById(dstNodeId);

        Map<String, String> annotations = incomingEdge.getAnnotations();
        Relationship newEdge = srcNode.createRelationshipTo(dstNode, MyRelationshipTypes.EDGE);
        for (Iterator iterator = annotations.keySet().iterator(); iterator.hasNext();) {
            String key = (String) iterator.next();
            String value = (String) annotations.get(key);
            if (key.equals("edgeId")) {
                continue;
            }
            try {
                Long val = Long.parseLong(value);
                newEdge.setProperty(key, val);
                edgeIndex.add(newEdge, key, new ValueContext(val).indexNumeric());
            } catch (Exception parseLongException) {
                try {
                    Double val = Double.parseDouble(value);
                    newEdge.setProperty(key, val);
                    edgeIndex.add(newEdge, key, new ValueContext(val).indexNumeric());
                } catch (Exception parseDoubleException) {
                    newEdge.setProperty(key, value);
                    edgeIndex.add(newEdge, key, value);
                }
            }
        }
        newEdge.setProperty("edgeId", newEdge.getId());
        edgeIndex.add(newEdge, "edgeId", new ValueContext(newEdge.getId()).indexNumeric());

        commit();
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

    private void convertNodeToVertex(Node node, AbstractVertex vertex) {
        for (String key : node.getPropertyKeys()) {
            try {
                String value = (String) node.getProperty(key);
                vertex.addAnnotation(key, value);
            } catch (Exception fetchStringException) {
                try {
                    String value = Long.toString((Long) node.getProperty(key));
                    vertex.addAnnotation(key, value);
                } catch (Exception fetchLongException) {
                    String value = Double.toString((Double) node.getProperty(key));
                    vertex.addAnnotation(key, value);
                }
            }
        }
    }

    private void convertRelationshipToEdge(Relationship relationship, AbstractEdge edge) {
        for (String key : relationship.getPropertyKeys()) {
            try {
                String value = (String) relationship.getProperty(key);
                edge.addAnnotation(key, value);
            } catch (Exception fetchStringException) {
                try {
                    String value = Long.toString((Long) relationship.getProperty(key));
                    edge.addAnnotation(key, value);
                } catch (Exception fetchLongException) {
                    String value = Double.toString((Double) relationship.getProperty(key));
                    edge.addAnnotation(key, value);
                }
            }
        }
    }

    @Override
    public Set<AbstractVertex> getVertices(String expression) {
        Set<AbstractVertex> vertexSet = new HashSet<AbstractVertex>();                          // create empty result set to store matching vertices
        for (Node foundNode : vertexIndex.query(expression)) {                  // evaluate expression and iterate over nodes
            String type = (String) foundNode.getProperty("type");               // determine type of vertex
            AbstractVertex tempVertex;                                                  // create vertex object according to type and populate annotations
            if (type.equals("Process")) {
                tempVertex = new Process();
            } else if (type.equals("Artifact")) {
                tempVertex = new Artifact();
            } else {
                tempVertex = new Agent();
            }
            convertNodeToVertex(foundNode, tempVertex);
            vertexSet.add(tempVertex);                                          // add final populated vertex to result set
        }
        return vertexSet;                                                       // return result set
    }

    @Override
    public Set<AbstractEdge> getEdges(String expression) {
        Set<AbstractEdge> resultSet = new HashSet<AbstractEdge>();                                    // create empty result set to store matching edges
        for (Relationship foundRelationship : edgeIndex.query(expression)) {        // evaluate expression and iterate over relationships
            String relationshipType = (String) foundRelationship.getProperty("type");           // determine edge type: create and populate edge annotations
            if (relationshipType.equals("Used")) {
                Process vertex1 = new Process();
                Artifact vertex2 = new Artifact();
                convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                AbstractEdge tempEdge = new Used(vertex1, vertex2);
                convertRelationshipToEdge(foundRelationship, tempEdge);
                resultSet.add(tempEdge);
            } else if (relationshipType.equals("WasGeneratedBy")) {
                Artifact vertex1 = new Artifact();
                Process vertex2 = new Process();
                convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                AbstractEdge tempEdge = new WasGeneratedBy(vertex1, vertex2);
                convertRelationshipToEdge(foundRelationship, tempEdge);
                resultSet.add(tempEdge);
            } else if (relationshipType.equals("WasTriggeredBy")) {
                Process vertex1 = new Process();
                Process vertex2 = new Process();
                convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                AbstractEdge tempEdge = new WasTriggeredBy(vertex1, vertex2);
                convertRelationshipToEdge(foundRelationship, tempEdge);
                resultSet.add(tempEdge);
            } else if (relationshipType.equals("WasDerivedFrom")) {
                Artifact vertex1 = new Artifact();
                Artifact vertex2 = new Artifact();
                convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                AbstractEdge tempEdge = new WasDerivedFrom(vertex1, vertex2);
                convertRelationshipToEdge(foundRelationship, tempEdge);
                resultSet.add(tempEdge);
            } else if (relationshipType.equals("WasControlledBy")) {
                Process vertex1 = new Process();
                Agent vertex2 = new Agent();
                convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                AbstractEdge tempEdge = new WasControlledBy(vertex1, vertex2);
                convertRelationshipToEdge(foundRelationship, tempEdge);
                resultSet.add(tempEdge);
            }
        }
        return resultSet;
    }

    @Override
    public Set<AbstractEdge> getEdges(String sourceExpression, String destinationExpression, String edgeExpression) {
        Set<AbstractVertex> sourceSet = getVertices(sourceExpression);                              // get set of source vertices matching the expression
        Set<AbstractVertex> destinationSet = getVertices(destinationExpression);                    // get set of destination vertices matching the expression
        Set<AbstractEdge> resultSet = new HashSet<AbstractEdge>();                                            // create empty result set to store matching edges
        for (Relationship foundRelationship : edgeIndex.query(edgeExpression)) {            // evaluate edge expression and iterate over relationships
            String relationshipType = (String) foundRelationship.getProperty("type");                   // determine edge type
            if (relationshipType.equals("Used")) {
                Process vertex1 = new Process();
                Artifact vertex2 = new Artifact();
                convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                if (sourceSet.contains(vertex1) && destinationSet.contains(vertex2)) {      // check that relationship source and destination nodes
                    AbstractEdge tempEdge = new Used(vertex1, vertex2);             // are in the sets that we populated earlier
                    convertRelationshipToEdge(foundRelationship, tempEdge);
                    resultSet.add(tempEdge);
                }
            } else if (relationshipType.equals("WasGeneratedBy")) {
                Artifact vertex1 = new Artifact();
                Process vertex2 = new Process();
                convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                if (sourceSet.contains(vertex1) && destinationSet.contains(vertex2)) {
                    AbstractEdge tempEdge = new WasGeneratedBy(vertex1, vertex2);
                    convertRelationshipToEdge(foundRelationship, tempEdge);
                    resultSet.add(tempEdge);
                }
            } else if (relationshipType.equals("WasTriggeredBy")) {
                Process vertex1 = new Process();
                Process vertex2 = new Process();
                convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                if (sourceSet.contains(vertex1) && destinationSet.contains(vertex2)) {
                    AbstractEdge tempEdge = new WasTriggeredBy(vertex1, vertex2);
                    convertRelationshipToEdge(foundRelationship, tempEdge);
                    resultSet.add(tempEdge);
                }
            } else if (relationshipType.equals("WasDerivedFrom")) {
                Artifact vertex1 = new Artifact();
                Artifact vertex2 = new Artifact();
                convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                if (sourceSet.contains(vertex1) && destinationSet.contains(vertex2)) {
                    AbstractEdge tempEdge = new WasDerivedFrom(vertex1, vertex2);
                    convertRelationshipToEdge(foundRelationship, tempEdge);
                    resultSet.add(tempEdge);
                }
            } else if (relationshipType.equals("WasControlledBy")) {
                Process vertex1 = new Process();
                Agent vertex2 = new Agent();
                convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                if (sourceSet.contains(vertex1) && destinationSet.contains(vertex2)) {
                    AbstractEdge tempEdge = new WasControlledBy(vertex1, vertex2);
                    convertRelationshipToEdge(foundRelationship, tempEdge);
                    resultSet.add(tempEdge);
                }
            }
        }
        return resultSet;
    }

    @Override
    public Set<AbstractEdge> getEdges(AbstractVertex sourceVertex, AbstractVertex destinationVertex) {
        Map<String, String> sourceAnnotations = sourceVertex.getAnnotations();
        Map<String, String> destinationAnnotations = destinationVertex.getAnnotations();
        Set<Node> sourceSet = new HashSet<Node>();
        Set<Node> destinationSet = new HashSet<Node>();

        boolean firstrun = true;
        for (Map.Entry<String, String> entry : sourceAnnotations.entrySet()) {
            Set<Node> tmpSet = new HashSet<Node>();
            String key = (String) entry.getKey();
            String value = (String) entry.getValue();
            IndexHits<Node> nodesFound = vertexIndex.get(key, value);
            while (nodesFound.hasNext()) {
                Node tmpNode = (Node) nodesFound.next();
                tmpSet.add(tmpNode);
            }
            if (firstrun) {
                firstrun = false;
                sourceSet.addAll(tmpSet);
            } else {
                sourceSet.retainAll(tmpSet);
            }
        }

        firstrun = true;
        for (Map.Entry<String, String> entry : destinationAnnotations.entrySet()) {
            Set<Node> tmpSet = new HashSet<Node>();
            String key = (String) entry.getKey();
            String value = (String) entry.getValue();
            IndexHits<Node> nodesFound = vertexIndex.get(key, value);
            while (nodesFound.hasNext()) {
                Node tmpNode = (Node) nodesFound.next();
                tmpSet.add(tmpNode);
            }
            if (firstrun) {
                firstrun = false;
                destinationSet.addAll(tmpSet);
            } else {
                destinationSet.retainAll(tmpSet);
            }
        }

        Set<AbstractEdge> resultSet = new HashSet<AbstractEdge>();
        Iterator srcIter = sourceSet.iterator();
        while (srcIter.hasNext()) {
            Node srcNode = (Node) srcIter.next();
            Iterator dstIter = destinationSet.iterator();
            while (dstIter.hasNext()) {
                Node dstNode = (Node) dstIter.next();
                IndexHits<Relationship> relationshipHits = edgeIndex.query("type:*", srcNode, dstNode);
                while (relationshipHits.hasNext()) {
                    Relationship foundRelationship = relationshipHits.next();
                    String relationshipType = (String) foundRelationship.getProperty("type");
                    if (relationshipType.equals("Used")) {
                        Process vertex1 = new Process();
                        Artifact vertex2 = new Artifact();
                        convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                        convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                        AbstractEdge tempEdge = new Used(vertex1, vertex2);
                        convertRelationshipToEdge(foundRelationship, tempEdge);
                        resultSet.add(tempEdge);
                    } else if (relationshipType.equals("WasGeneratedBy")) {
                        Artifact vertex1 = new Artifact();
                        Process vertex2 = new Process();
                        convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                        convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                        AbstractEdge tempEdge = new WasGeneratedBy(vertex1, vertex2);
                        convertRelationshipToEdge(foundRelationship, tempEdge);
                        resultSet.add(tempEdge);
                    } else if (relationshipType.equals("WasTriggeredBy")) {
                        Process vertex1 = new Process();
                        Process vertex2 = new Process();
                        convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                        convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                        AbstractEdge tempEdge = new WasTriggeredBy(vertex1, vertex2);
                        convertRelationshipToEdge(foundRelationship, tempEdge);
                        resultSet.add(tempEdge);
                    } else if (relationshipType.equals("WasDerivedFrom")) {
                        Artifact vertex1 = new Artifact();
                        Artifact vertex2 = new Artifact();
                        convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                        convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                        AbstractEdge tempEdge = new WasDerivedFrom(vertex1, vertex2);
                        convertRelationshipToEdge(foundRelationship, tempEdge);
                        resultSet.add(tempEdge);
                    } else if (relationshipType.equals("WasControlledBy")) {
                        Process vertex1 = new Process();
                        Agent vertex2 = new Agent();
                        convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                        convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                        AbstractEdge tempEdge = new WasControlledBy(vertex1, vertex2);
                        convertRelationshipToEdge(foundRelationship, tempEdge);
                        resultSet.add(tempEdge);
                    }
                }
            }
        }
        return resultSet;
    }

    @Override
    public Lineage getLineage(AbstractVertex sourceVertex, String pruneExpression, int direction, boolean includeTerminatingNode) {
        Lineage resultLineage = new Lineage();
        HashMap tempVertexTable = new HashMap<Long, AbstractVertex>();

        Set<Node> pruneSet = new HashSet<Node>();
        for (Node foundNode : vertexIndex.query(pruneExpression)) {
            pruneSet.add(foundNode);
        }

        Map<String, String> sourceAnnotations = sourceVertex.getAnnotations();
        Set<Node> sourceSet = new HashSet<Node>();
        boolean firstrun = true;
        for (Map.Entry<String, String> entry : sourceAnnotations.entrySet()) {
            Set<Node> tmpSet = new HashSet<Node>();
            String key = (String) entry.getKey();
            String value = (String) entry.getValue();
            IndexHits<Node> nodesFound = vertexIndex.get(key, value);
            while (nodesFound.hasNext()) {
                Node tmpNode = (Node) nodesFound.next();
                tmpSet.add(tmpNode);
            }
            if (firstrun) {
                firstrun = false;
                sourceSet.addAll(tmpSet);
            } else {
                sourceSet.retainAll(tmpSet);
            }
        }
        Node sourceNode = (Node) sourceSet.iterator().next();
        String type = (String) sourceNode.getProperty("type");
        AbstractVertex tempVertex;
        if (type.equals("Process")) {
            tempVertex = new Process();
        } else if (type.equals("Artifact")) {
            tempVertex = new Artifact();
        } else {
            tempVertex = new Agent();
        }
        convertNodeToVertex(sourceNode, tempVertex);
        tempVertexTable.put(sourceNode.getId(), tempVertex);
        resultLineage.putVertex(tempVertex);

        Set<Node> tempSet = new HashSet<Node>();
        tempSet.add(sourceNode);
        while (true) {
            if (tempSet.isEmpty()) {
                break;
            }
            Set<Node> tempSet2 = new HashSet<Node>();
            Iterator iterator = tempSet.iterator();
            while (iterator.hasNext()) {
                Node tempNode = (Node) iterator.next();
                Direction dir;
                if (direction == 0) {
                    dir = Direction.OUTGOING;
                } else {
                    dir = Direction.INCOMING;
                }
                for (Relationship nodeRelationship : tempNode.getRelationships(dir)) {
                    Node otherNode;
                    if (direction == 0) {
                        otherNode = nodeRelationship.getEndNode();
                    } else {
                        otherNode = nodeRelationship.getStartNode();
                    }
                    if (pruneSet.contains(otherNode) && (includeTerminatingNode == false)) {
                        continue;
                    }
                    String otherNodeType = (String) otherNode.getProperty("type");
                    AbstractVertex otherVertex;
                    if (otherNodeType.equals("Process")) {
                        otherVertex = new Process();
                    } else if (otherNodeType.equals("Artifact")) {
                        otherVertex = new Artifact();
                    } else {
                        otherVertex = new Agent();
                    }
                    convertNodeToVertex(otherNode, otherVertex);
                    tempVertexTable.put(otherNode.getId(), otherVertex);
                    resultLineage.putVertex(otherVertex);

                    String relationshipType = (String) nodeRelationship.getProperty("type");
                    if (direction == 0) {
                        if (relationshipType.equals("Used")) {
                            AbstractEdge tempEdge = new Used((Process) tempVertexTable.get(tempNode.getId()), (Artifact) tempVertexTable.get(otherNode.getId()));
                            convertRelationshipToEdge(nodeRelationship, tempEdge);
                            resultLineage.putEdge(tempEdge);
                        } else if (relationshipType.equals("WasGeneratedBy")) {
                            AbstractEdge tempEdge = new WasGeneratedBy((Artifact) tempVertexTable.get(tempNode.getId()), (Process) tempVertexTable.get(otherNode.getId()));
                            convertRelationshipToEdge(nodeRelationship, tempEdge);
                            resultLineage.putEdge(tempEdge);
                        } else if (relationshipType.equals("WasTriggeredBy")) {
                            AbstractEdge tempEdge = new WasTriggeredBy((Process) tempVertexTable.get(tempNode.getId()), (Process) tempVertexTable.get(otherNode.getId()));
                            convertRelationshipToEdge(nodeRelationship, tempEdge);
                            resultLineage.putEdge(tempEdge);
                        } else if (relationshipType.equals("WasDerivedFrom")) {
                            AbstractEdge tempEdge = new WasDerivedFrom((Artifact) tempVertexTable.get(tempNode.getId()), (Artifact) tempVertexTable.get(otherNode.getId()));
                            convertRelationshipToEdge(nodeRelationship, tempEdge);
                            resultLineage.putEdge(tempEdge);
                        } else if (relationshipType.equals("WasControlledBy")) {
                            AbstractEdge tempEdge = new WasControlledBy((Process) tempVertexTable.get(tempNode.getId()), (Agent) tempVertexTable.get(otherNode.getId()));
                            convertRelationshipToEdge(nodeRelationship, tempEdge);
                            resultLineage.putEdge(tempEdge);
                        }
                    } else {
                        if (relationshipType.equals("Used")) {
                            AbstractEdge tempEdge = new Used((Process) tempVertexTable.get(otherNode.getId()), (Artifact) tempVertexTable.get(tempNode.getId()));
                            convertRelationshipToEdge(nodeRelationship, tempEdge);
                            resultLineage.putEdge(tempEdge);
                        } else if (relationshipType.equals("WasGeneratedBy")) {
                            AbstractEdge tempEdge = new WasGeneratedBy((Artifact) tempVertexTable.get(otherNode.getId()), (Process) tempVertexTable.get(tempNode.getId()));
                            convertRelationshipToEdge(nodeRelationship, tempEdge);
                            resultLineage.putEdge(tempEdge);
                        } else if (relationshipType.equals("WasTriggeredBy")) {
                            AbstractEdge tempEdge = new WasTriggeredBy((Process) tempVertexTable.get(otherNode.getId()), (Process) tempVertexTable.get(tempNode.getId()));
                            convertRelationshipToEdge(nodeRelationship, tempEdge);
                            resultLineage.putEdge(tempEdge);
                        } else if (relationshipType.equals("WasDerivedFrom")) {
                            AbstractEdge tempEdge = new WasDerivedFrom((Artifact) tempVertexTable.get(otherNode.getId()), (Artifact) tempVertexTable.get(tempNode.getId()));
                            convertRelationshipToEdge(nodeRelationship, tempEdge);
                            resultLineage.putEdge(tempEdge);
                        } else if (relationshipType.equals("WasControlledBy")) {
                            AbstractEdge tempEdge = new WasControlledBy((Process) tempVertexTable.get(otherNode.getId()), (Agent) tempVertexTable.get(tempNode.getId()));
                            convertRelationshipToEdge(nodeRelationship, tempEdge);
                            resultLineage.putEdge(tempEdge);
                        }
                    }

                    if (pruneSet.contains(otherNode) == false) {
                        tempSet2.add(otherNode);
                    }
                }
            }
            tempSet.clear();
            tempSet.addAll(tempSet2);
        }

        return resultLineage;
    }

    @Override
    public Lineage getLineage(AbstractVertex sourceVertex, int depth, int direction) {
        Lineage resultLineage = new Lineage();
        HashMap tempVertexTable = new HashMap<Long, AbstractVertex>();

        Map<String, String> sourceAnnotations = sourceVertex.getAnnotations();
        Set<Node> sourceSet = new HashSet<Node>();
        boolean firstrun = true;
        for (Map.Entry<String, String> entry : sourceAnnotations.entrySet()) {
            Set<Node> tmpSet = new HashSet<Node>();
            String key = (String) entry.getKey();
            String value = (String) entry.getValue();
            IndexHits<Node> nodesFound = vertexIndex.get(key, value);
            while (nodesFound.hasNext()) {
                Node tmpNode = (Node) nodesFound.next();
                tmpSet.add(tmpNode);
            }
            if (firstrun) {
                firstrun = false;
                sourceSet.addAll(tmpSet);
            } else {
                sourceSet.retainAll(tmpSet);
            }
        }
        Node sourceNode = (Node) sourceSet.iterator().next();
        String type = (String) sourceNode.getProperty("type");
        AbstractVertex tempVertex;
        if (type.equals("Process")) {
            tempVertex = new Process();
        } else if (type.equals("Artifact")) {
            tempVertex = new Artifact();
        } else {
            tempVertex = new Agent();
        }
        convertNodeToVertex(sourceNode, tempVertex);
        tempVertexTable.put(sourceNode.getId(), tempVertex);
        resultLineage.putVertex(tempVertex);

        Set<Node> tempSet = new HashSet<Node>();
        tempSet.add(sourceNode);
        while (depth > 0) {
            Set<Node> tempSet2 = new HashSet<Node>();
            Iterator iterator = tempSet.iterator();
            while (iterator.hasNext()) {
                Node tempNode = (Node) iterator.next();
                Direction dir;
                if (direction == 0) {
                    dir = Direction.OUTGOING;
                } else {
                    dir = Direction.INCOMING;
                }
                for (Relationship nodeRelationship : tempNode.getRelationships(dir)) {
                    Node otherNode;
                    if (direction == 0) {
                        otherNode = nodeRelationship.getEndNode();
                    } else {
                        otherNode = nodeRelationship.getStartNode();
                    }
                    String otherNodeType = (String) otherNode.getProperty("type");
                    AbstractVertex otherVertex;
                    if (otherNodeType.equals("Process")) {
                        otherVertex = new Process();
                    } else if (otherNodeType.equals("Artifact")) {
                        otherVertex = new Artifact();
                    } else {
                        otherVertex = new Agent();
                    }
                    convertNodeToVertex(otherNode, otherVertex);
                    tempVertexTable.put(otherNode.getId(), otherVertex);
                    resultLineage.putVertex(otherVertex);

                    String relationshipType = (String) nodeRelationship.getProperty("type");
                    if (direction == 0) {
                        if (relationshipType.equals("Used")) {
                            AbstractEdge tempEdge = new Used((Process) tempVertexTable.get(tempNode.getId()), (Artifact) tempVertexTable.get(otherNode.getId()));
                            convertRelationshipToEdge(nodeRelationship, tempEdge);
                            resultLineage.putEdge(tempEdge);
                        } else if (relationshipType.equals("WasGeneratedBy")) {
                            AbstractEdge tempEdge = new WasGeneratedBy((Artifact) tempVertexTable.get(tempNode.getId()), (Process) tempVertexTable.get(otherNode.getId()));
                            convertRelationshipToEdge(nodeRelationship, tempEdge);
                            resultLineage.putEdge(tempEdge);
                        } else if (relationshipType.equals("WasTriggeredBy")) {
                            AbstractEdge tempEdge = new WasTriggeredBy((Process) tempVertexTable.get(tempNode.getId()), (Process) tempVertexTable.get(otherNode.getId()));
                            convertRelationshipToEdge(nodeRelationship, tempEdge);
                            resultLineage.putEdge(tempEdge);
                        } else if (relationshipType.equals("WasDerivedFrom")) {
                            AbstractEdge tempEdge = new WasDerivedFrom((Artifact) tempVertexTable.get(tempNode.getId()), (Artifact) tempVertexTable.get(otherNode.getId()));
                            convertRelationshipToEdge(nodeRelationship, tempEdge);
                            resultLineage.putEdge(tempEdge);
                        } else if (relationshipType.equals("WasControlledBy")) {
                            AbstractEdge tempEdge = new WasControlledBy((Process) tempVertexTable.get(tempNode.getId()), (Agent) tempVertexTable.get(otherNode.getId()));
                            convertRelationshipToEdge(nodeRelationship, tempEdge);
                            resultLineage.putEdge(tempEdge);
                        }
                    } else {
                        if (relationshipType.equals("Used")) {
                            AbstractEdge tempEdge = new Used((Process) tempVertexTable.get(otherNode.getId()), (Artifact) tempVertexTable.get(tempNode.getId()));
                            convertRelationshipToEdge(nodeRelationship, tempEdge);
                            resultLineage.putEdge(tempEdge);
                        } else if (relationshipType.equals("WasGeneratedBy")) {
                            AbstractEdge tempEdge = new WasGeneratedBy((Artifact) tempVertexTable.get(otherNode.getId()), (Process) tempVertexTable.get(tempNode.getId()));
                            convertRelationshipToEdge(nodeRelationship, tempEdge);
                            resultLineage.putEdge(tempEdge);
                        } else if (relationshipType.equals("WasTriggeredBy")) {
                            AbstractEdge tempEdge = new WasTriggeredBy((Process) tempVertexTable.get(otherNode.getId()), (Process) tempVertexTable.get(tempNode.getId()));
                            convertRelationshipToEdge(nodeRelationship, tempEdge);
                            resultLineage.putEdge(tempEdge);
                        } else if (relationshipType.equals("WasDerivedFrom")) {
                            AbstractEdge tempEdge = new WasDerivedFrom((Artifact) tempVertexTable.get(otherNode.getId()), (Artifact) tempVertexTable.get(tempNode.getId()));
                            convertRelationshipToEdge(nodeRelationship, tempEdge);
                            resultLineage.putEdge(tempEdge);
                        } else if (relationshipType.equals("WasControlledBy")) {
                            AbstractEdge tempEdge = new WasControlledBy((Process) tempVertexTable.get(otherNode.getId()), (Agent) tempVertexTable.get(tempNode.getId()));
                            convertRelationshipToEdge(nodeRelationship, tempEdge);
                            resultLineage.putEdge(tempEdge);
                        }
                    }

                    tempSet2.add(otherNode);
                }
            }
            tempSet.clear();
            tempSet.addAll(tempSet2);
            depth--;
        }

        return resultLineage;
    }

    @Override
    public Lineage getLineage(String vertexId, int depth, String direction) {
        Long sourceNodeId = Long.parseLong(vertexId);
        Node sourceNode = graphDb.getNodeById(sourceNodeId);
        String type = (String) sourceNode.getProperty("type");
        AbstractVertex sourceVertex;
        if (type.equals("Process")) {
            sourceVertex = new Process();
        } else if (type.equals("Artifact")) {
            sourceVertex = new Artifact();
        } else {
            sourceVertex = new Agent();
        }
        convertNodeToVertex(sourceNode, sourceVertex);
        if (direction.equalsIgnoreCase("ancestors")) {
            return getLineage(sourceVertex, depth, 0);
        } else if (direction.equalsIgnoreCase("descendants")) {
            return getLineage(sourceVertex, depth, 1);
        }
        return null;
    }
}
