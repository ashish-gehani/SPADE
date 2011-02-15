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
import spade.opm.edge.Edge;
import spade.opm.edge.WasTriggeredBy;
import spade.opm.edge.WasControlledBy;
import spade.opm.edge.WasGeneratedBy;
import spade.opm.edge.Used;
import spade.opm.edge.WasDerivedFrom;
import spade.opm.vertex.Artifact;
import spade.opm.vertex.Agent;
import spade.opm.vertex.Vertex;
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

public class Neo4jStorage extends AbstractStorage {

    private GraphDatabaseService graphDb;
    private Index<Node> vertexIndex;
    private RelationshipIndex edgeIndex;
    private Transaction tx;
    private int txcount;
    private HashMap<Vertex, Long> vertexTable;
    private HashSet edgeSet;

    public enum MyRelationshipTypes implements RelationshipType {

        EDGE
    }

    @Override
    public boolean initialize(String path) {

        graphDb = new EmbeddedGraphDatabase(path);
        txcount = 0;
        vertexIndex = graphDb.index().forNodes("vertexIndex", MapUtil.stringMap("provider", "lucene", "type", "fulltext"));
        edgeIndex = graphDb.index().forRelationships("edgeIndex", MapUtil.stringMap("provider", "lucene", "type", "fulltext"));
        vertexTable = new HashMap<Vertex, Long>();
        edgeSet = new HashSet();

        return true;
    }

    private void commit() {
        txcount++;
        if (txcount == 10000) {
            txcount = 0;
            try {
                tx.success();
                tx.finish();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private Vertex fixVertex(Vertex v) {
        if (v instanceof Artifact) {
            v.addAnnotation("type", "Artifact");
        } else if (v instanceof Process) {
            v.addAnnotation("type", "Process");
        } else if (v instanceof Agent) {
            v.addAnnotation("type", "Agent");
        }
        return v;
    }

    private Edge fixEdge(Edge e) {
        if (e instanceof Used) {
            e.addAnnotation("type", "Used");
        } else if (e instanceof WasControlledBy) {
            e.addAnnotation("type", "WasControlledBy");
        } else if (e instanceof WasDerivedFrom) {
            e.addAnnotation("type", "WasDerivedFrom");
        } else if (e instanceof WasGeneratedBy) {
            e.addAnnotation("type", "WasGeneratedBy");
        } else if (e instanceof WasTriggeredBy) {
            e.addAnnotation("type", "WasTriggeredBy");
        }
        return e;
    }

    @Override
    public boolean putVertex(Vertex v) {
        if (txcount == 0) {
            tx = graphDb.beginTx();
        }
        v = fixVertex(v);
        if (vertexTable.get(v) != null) {
            return false;
        }
        Node newVertex = graphDb.createNode();
        Map<String, String> annotations = v.getAnnotations();
        for (Iterator it = annotations.keySet().iterator(); it.hasNext();) {
            String name = (String) it.next();
            String value = (String) annotations.get(name);
            try {
                Long val = Long.parseLong(value);
                newVertex.setProperty(name, val);
                vertexIndex.add(newVertex, name, new ValueContext(val).indexNumeric());
            } catch (Exception e1) {
                try {
                    Double val = Double.parseDouble(value);
                    newVertex.setProperty(name, val);
                    vertexIndex.add(newVertex, name, new ValueContext(val).indexNumeric());
                } catch (Exception e2) {
                    newVertex.setProperty(name, value);
                    vertexIndex.add(newVertex, name, value);
                }
            }
        }
        newVertex.setProperty("id", newVertex.getId());
        vertexIndex.add(newVertex, "id", new ValueContext(newVertex.getId()).indexNumeric());
        vertexTable.put(v, newVertex.getId());
        commit();
        return true;
    }

    @Override
    public boolean putEdge(Edge e) {
        if (txcount == 0) {
            tx = graphDb.beginTx();
        }
        Vertex vsrc = fixVertex(e.getSrcVertex());
        Vertex vdst = fixVertex(e.getDstVertex());
        e = fixEdge(e);
        if (edgeSet.add(e.hashCode()) == false) {
            return false;
        }
        Long srcNodeId = (Long) vertexTable.get(vsrc);
        Long dstNodeId = (Long) vertexTable.get(vdst);
        Node srcNode = graphDb.getNodeById(srcNodeId);
        Node dstNode = graphDb.getNodeById(dstNodeId);

        Map<String, String> annotations = e.getAnnotations();
        Relationship newEdge = srcNode.createRelationshipTo(dstNode, MyRelationshipTypes.EDGE);
        for (Iterator it = annotations.keySet().iterator(); it.hasNext();) {
            String name = (String) it.next();
            String value = (String) annotations.get(name);
            newEdge.setProperty(name, value);
            edgeIndex.add(newEdge, name, value);
        }
        commit();
        return true;
    }

    @Override
    public boolean shutdown() {
        if (tx != null) {
            tx.success();
            tx.finish();
        }
        graphDb.shutdown();
        return true;
    }

    private void convertNodeToVertex(Node n, Vertex v) {
        for (String key : n.getPropertyKeys()) {
            try {
                String value = (String) n.getProperty(key);
                v.addAnnotation(key, value);
            } catch (Exception e1) {
                try {
                    String value = Long.toString((Long) n.getProperty(key));
                    v.addAnnotation(key, value);
                } catch (Exception e2) {
                    String value = Double.toString((Double) n.getProperty(key));
                    v.addAnnotation(key, value);
                }
            }
        }
    }

    private void convertRelationshipToEdge(Relationship r, Edge e) {
        for (String key : r.getPropertyKeys()) {
            String value = (String) r.getProperty(key);
            e.addAnnotation(key, value);
        }
    }

    @Override
    public Set<Vertex> getVertices(String expression) {
        Set<Vertex> vertexSet = new HashSet<Vertex>();                          // create empty result set to store matching vertices
        for (Node foundNode : vertexIndex.query(expression)) {                  // evaluate expression and iterate over nodes
            String type = (String) foundNode.getProperty("type");               // determine type of vertex
            Vertex tempVertex;                                                  // create vertex object according to type and populate annotations
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
    public Set<Edge> getEdges(String expression) {
        Set<Edge> edgeSet = new HashSet<Edge>();                                    // create empty result set to store matching edges
        for (Relationship foundRelationship : edgeIndex.query(expression)) {        // evaluate expression and iterate over relationships
            String type = (String) foundRelationship.getProperty("type");           // determine edge type: create and populate edge annotations
            if (type.equals("Used")) {
                Process vertex1 = new Process();
                Artifact vertex2 = new Artifact();
                convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                Edge tempEdge = new Used(vertex1, vertex2);
                convertRelationshipToEdge(foundRelationship, tempEdge);
                edgeSet.add(tempEdge);
            } else if (type.equals("WasGeneratedBy")) {
                Artifact vertex1 = new Artifact();
                Process vertex2 = new Process();
                convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                Edge tempEdge = new WasGeneratedBy(vertex1, vertex2);
                convertRelationshipToEdge(foundRelationship, tempEdge);
                edgeSet.add(tempEdge);
            } else if (type.equals("WasTriggeredBy")) {
                Process vertex1 = new Process();
                Process vertex2 = new Process();
                convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                Edge tempEdge = new WasTriggeredBy(vertex1, vertex2);
                convertRelationshipToEdge(foundRelationship, tempEdge);
                edgeSet.add(tempEdge);
            } else if (type.equals("WasDerivedFrom")) {
                Artifact vertex1 = new Artifact();
                Artifact vertex2 = new Artifact();
                convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                Edge tempEdge = new WasDerivedFrom(vertex1, vertex2);
                convertRelationshipToEdge(foundRelationship, tempEdge);
                edgeSet.add(tempEdge);
            } else if (type.equals("WasControlledBy")) {
                Process vertex1 = new Process();
                Agent vertex2 = new Agent();
                convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                Edge tempEdge = new WasControlledBy(vertex1, vertex2);
                convertRelationshipToEdge(foundRelationship, tempEdge);
                edgeSet.add(tempEdge);
            }
        }
        return edgeSet;
    }

    @Override
    public Set<Edge> getEdges(String sourceExpression, String destinationExpression, String edgeExpression) {
        Set<Vertex> sourceSet = getVertices(sourceExpression);                              // get set of source vertices matching the expression
        Set<Vertex> destinationSet = getVertices(destinationExpression);                    // get set of destination vertices matching the expression
        Set<Edge> edgeSet = new HashSet<Edge>();                                            // create empty result set to store matching edges
        for (Relationship foundRelationship : edgeIndex.query(edgeExpression)) {            // evaluate edge expression and iterate over relationships
            String type = (String) foundRelationship.getProperty("type");                   // determine edge type
            if (type.equals("Used")) {
                Process vertex1 = new Process();
                Artifact vertex2 = new Artifact();
                convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                if (sourceSet.contains(vertex1) && destinationSet.contains(vertex2)) {      // check that relationship source and destination nodes
                    Edge tempEdge = new Used(vertex1, vertex2);             // are in the sets that we populated earlier
                    convertRelationshipToEdge(foundRelationship, tempEdge);
                    edgeSet.add(tempEdge);
                }
            } else if (type.equals("WasGeneratedBy")) {
                Artifact vertex1 = new Artifact();
                Process vertex2 = new Process();
                convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                if (sourceSet.contains(vertex1) && destinationSet.contains(vertex2)) {
                    Edge tempEdge = new WasGeneratedBy(vertex1, vertex2);
                    convertRelationshipToEdge(foundRelationship, tempEdge);
                    edgeSet.add(tempEdge);
                }
            } else if (type.equals("WasTriggeredBy")) {
                Process vertex1 = new Process();
                Process vertex2 = new Process();
                convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                if (sourceSet.contains(vertex1) && destinationSet.contains(vertex2)) {
                    Edge tempEdge = new WasTriggeredBy(vertex1, vertex2);
                    convertRelationshipToEdge(foundRelationship, tempEdge);
                    edgeSet.add(tempEdge);
                }
            } else if (type.equals("WasDerivedFrom")) {
                Artifact vertex1 = new Artifact();
                Artifact vertex2 = new Artifact();
                convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                if (sourceSet.contains(vertex1) && destinationSet.contains(vertex2)) {
                    Edge tempEdge = new WasDerivedFrom(vertex1, vertex2);
                    convertRelationshipToEdge(foundRelationship, tempEdge);
                    edgeSet.add(tempEdge);
                }
            } else if (type.equals("WasControlledBy")) {
                Process vertex1 = new Process();
                Agent vertex2 = new Agent();
                convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                if (sourceSet.contains(vertex1) && destinationSet.contains(vertex2)) {
                    Edge tempEdge = new WasControlledBy(vertex1, vertex2);
                    convertRelationshipToEdge(foundRelationship, tempEdge);
                    edgeSet.add(tempEdge);
                }
            }
        }
        return edgeSet;
    }

    @Override
    public Set<Edge> getEdges(Vertex source, Vertex destination) {
        Map<String, String> sourceAnnotations = source.getAnnotations();
        Map<String, String> destinationAnnotations = destination.getAnnotations();
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

        Set<Edge> edgeSet = new HashSet<Edge>();
        Iterator srcIter = sourceSet.iterator();
        while (srcIter.hasNext()) {
            Node srcNode = (Node) srcIter.next();
            Iterator dstIter = destinationSet.iterator();
            while (dstIter.hasNext()) {
                Node dstNode = (Node) dstIter.next();
                IndexHits<Relationship> relationshipHits = edgeIndex.query("type:*", srcNode, dstNode);
                while (relationshipHits.hasNext()) {
                    Relationship foundRelationship = relationshipHits.next();
                    String type = (String) foundRelationship.getProperty("type");
                    if (type.equals("Used")) {
                        Process vertex1 = new Process();
                        Artifact vertex2 = new Artifact();
                        convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                        convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                        Edge tempEdge = new Used(vertex1, vertex2);
                        convertRelationshipToEdge(foundRelationship, tempEdge);
                        edgeSet.add(tempEdge);
                    } else if (type.equals("WasGeneratedBy")) {
                        Artifact vertex1 = new Artifact();
                        Process vertex2 = new Process();
                        convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                        convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                        Edge tempEdge = new WasGeneratedBy(vertex1, vertex2);
                        convertRelationshipToEdge(foundRelationship, tempEdge);
                        edgeSet.add(tempEdge);
                    } else if (type.equals("WasTriggeredBy")) {
                        Process vertex1 = new Process();
                        Process vertex2 = new Process();
                        convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                        convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                        Edge tempEdge = new WasTriggeredBy(vertex1, vertex2);
                        convertRelationshipToEdge(foundRelationship, tempEdge);
                        edgeSet.add(tempEdge);
                    } else if (type.equals("WasDerivedFrom")) {
                        Artifact vertex1 = new Artifact();
                        Artifact vertex2 = new Artifact();
                        convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                        convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                        Edge tempEdge = new WasDerivedFrom(vertex1, vertex2);
                        convertRelationshipToEdge(foundRelationship, tempEdge);
                        edgeSet.add(tempEdge);
                    } else if (type.equals("WasControlledBy")) {
                        Process vertex1 = new Process();
                        Agent vertex2 = new Agent();
                        convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                        convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                        Edge tempEdge = new WasControlledBy(vertex1, vertex2);
                        convertRelationshipToEdge(foundRelationship, tempEdge);
                        edgeSet.add(tempEdge);
                    }
                }
            }
        }
        return edgeSet;
    }

    @Override
    public Lineage getLineage(Vertex source, String pruneExpression, int direction, boolean includeTerminatingNode) {
        Lineage lineage = new Lineage();
        HashMap tempVertexTable = new HashMap<Long, Vertex>();

        Set<Node> pruneSet = new HashSet<Node>();
        for (Node foundNode : vertexIndex.query(pruneExpression)) {
            pruneSet.add(foundNode);
        }

        Map<String, String> sourceAnnotations = source.getAnnotations();
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
        Vertex tempVertex;
        if (type.equals("Process")) {
            tempVertex = new Process();
        } else if (type.equals("Artifact")) {
            tempVertex = new Artifact();
        } else {
            tempVertex = new Agent();
        }
        convertNodeToVertex(sourceNode, tempVertex);
        tempVertexTable.put(sourceNode.getId(), tempVertex);
        lineage.putVertex(tempVertex);

        Set<Node> tempSet = new HashSet<Node>();
        tempSet.add(sourceNode);
        while (true) {
            if (tempSet.isEmpty()) {
                break;
            }
            Set<Node> tempSet2 = new HashSet<Node>();
            Iterator iter = tempSet.iterator();
            while (iter.hasNext()) {
                Node tempNode = (Node) iter.next();
                Direction dir;
                if (direction == 0) {
                    dir = Direction.OUTGOING;
                } else {
                    dir = Direction.INCOMING;
                }
                for (Relationship r : tempNode.getRelationships(dir)) {
                    Node otherNode;
                    if (direction == 0) {
                        otherNode = r.getEndNode();
                    } else {
                        otherNode = r.getStartNode();
                    }
                    if (pruneSet.contains(otherNode) && (includeTerminatingNode == false)) {
                        continue;
                    }
                    String otherNodeType = (String) otherNode.getProperty("type");
                    Vertex otherVertex;
                    if (otherNodeType.equals("Process")) {
                        otherVertex = new Process();
                    } else if (otherNodeType.equals("Artifact")) {
                        otherVertex = new Artifact();
                    } else {
                        otherVertex = new Agent();
                    }
                    convertNodeToVertex(otherNode, otherVertex);
                    tempVertexTable.put(otherNode.getId(), otherVertex);
                    lineage.putVertex(otherVertex);

                    String rtype = (String) r.getProperty("type");
                    if (direction == 0) {
                        if (rtype.equals("Used")) {
                            Edge tempEdge = new Used((Process) tempVertexTable.get(tempNode.getId()), (Artifact) tempVertexTable.get(otherNode.getId()));
                            convertRelationshipToEdge(r, tempEdge);
                            lineage.putEdge(tempEdge);
                        } else if (rtype.equals("WasGeneratedBy")) {
                            Edge tempEdge = new WasGeneratedBy((Artifact) tempVertexTable.get(tempNode.getId()), (Process) tempVertexTable.get(otherNode.getId()));
                            convertRelationshipToEdge(r, tempEdge);
                            lineage.putEdge(tempEdge);
                        } else if (rtype.equals("WasTriggeredBy")) {
                            Edge tempEdge = new WasTriggeredBy((Process) tempVertexTable.get(tempNode.getId()), (Process) tempVertexTable.get(otherNode.getId()));
                            convertRelationshipToEdge(r, tempEdge);
                            lineage.putEdge(tempEdge);
                        } else if (rtype.equals("WasDerivedFrom")) {
                            Edge tempEdge = new WasDerivedFrom((Artifact) tempVertexTable.get(tempNode.getId()), (Artifact) tempVertexTable.get(otherNode.getId()));
                            convertRelationshipToEdge(r, tempEdge);
                            lineage.putEdge(tempEdge);
                        } else if (rtype.equals("WasControlledBy")) {
                            Edge tempEdge = new WasControlledBy((Process) tempVertexTable.get(tempNode.getId()), (Agent) tempVertexTable.get(otherNode.getId()));
                            convertRelationshipToEdge(r, tempEdge);
                            lineage.putEdge(tempEdge);
                        }
                    } else {
                        if (rtype.equals("Used")) {
                            Edge tempEdge = new Used((Process) tempVertexTable.get(otherNode.getId()), (Artifact) tempVertexTable.get(tempNode.getId()));
                            convertRelationshipToEdge(r, tempEdge);
                            lineage.putEdge(tempEdge);
                        } else if (rtype.equals("WasGeneratedBy")) {
                            Edge tempEdge = new WasGeneratedBy((Artifact) tempVertexTable.get(otherNode.getId()), (Process) tempVertexTable.get(tempNode.getId()));
                            convertRelationshipToEdge(r, tempEdge);
                            lineage.putEdge(tempEdge);
                        } else if (rtype.equals("WasTriggeredBy")) {
                            Edge tempEdge = new WasTriggeredBy((Process) tempVertexTable.get(otherNode.getId()), (Process) tempVertexTable.get(tempNode.getId()));
                            convertRelationshipToEdge(r, tempEdge);
                            lineage.putEdge(tempEdge);
                        } else if (rtype.equals("WasDerivedFrom")) {
                            Edge tempEdge = new WasDerivedFrom((Artifact) tempVertexTable.get(otherNode.getId()), (Artifact) tempVertexTable.get(tempNode.getId()));
                            convertRelationshipToEdge(r, tempEdge);
                            lineage.putEdge(tempEdge);
                        } else if (rtype.equals("WasControlledBy")) {
                            Edge tempEdge = new WasControlledBy((Process) tempVertexTable.get(otherNode.getId()), (Agent) tempVertexTable.get(tempNode.getId()));
                            convertRelationshipToEdge(r, tempEdge);
                            lineage.putEdge(tempEdge);
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

        return lineage;
    }

    @Override
    public Lineage getLineage(Vertex source, int depth, int direction) {
        Lineage lineage = new Lineage();
        HashMap tempVertexTable = new HashMap<Long, Vertex>();

        Map<String, String> sourceAnnotations = source.getAnnotations();
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
        Vertex tempVertex;
        if (type.equals("Process")) {
            tempVertex = new Process();
        } else if (type.equals("Artifact")) {
            tempVertex = new Artifact();
        } else {
            tempVertex = new Agent();
        }
        convertNodeToVertex(sourceNode, tempVertex);
        tempVertexTable.put(sourceNode.getId(), tempVertex);
        lineage.putVertex(tempVertex);

        Set<Node> tempSet = new HashSet<Node>();
        tempSet.add(sourceNode);
        while (depth > 0) {
            Set<Node> tempSet2 = new HashSet<Node>();
            Iterator iter = tempSet.iterator();
            while (iter.hasNext()) {
                Node tempNode = (Node) iter.next();
                Direction dir;
                if (direction == 0) {
                    dir = Direction.OUTGOING;
                } else {
                    dir = Direction.INCOMING;
                }
                for (Relationship r : tempNode.getRelationships(dir)) {
                    Node otherNode;
                    if (direction == 0) {
                        otherNode = r.getEndNode();
                    } else {
                        otherNode = r.getStartNode();
                    }
                    String otherNodeType = (String) otherNode.getProperty("type");
                    Vertex otherVertex;
                    if (otherNodeType.equals("Process")) {
                        otherVertex = new Process();
                    } else if (otherNodeType.equals("Artifact")) {
                        otherVertex = new Artifact();
                    } else {
                        otherVertex = new Agent();
                    }
                    convertNodeToVertex(otherNode, otherVertex);
                    tempVertexTable.put(otherNode.getId(), otherVertex);
                    lineage.putVertex(otherVertex);

                    String rtype = (String) r.getProperty("type");
                    if (direction == 0) {
                        if (rtype.equals("Used")) {
                            Edge tempEdge = new Used((Process) tempVertexTable.get(tempNode.getId()), (Artifact) tempVertexTable.get(otherNode.getId()));
                            convertRelationshipToEdge(r, tempEdge);
                            lineage.putEdge(tempEdge);
                        } else if (rtype.equals("WasGeneratedBy")) {
                            Edge tempEdge = new WasGeneratedBy((Artifact) tempVertexTable.get(tempNode.getId()), (Process) tempVertexTable.get(otherNode.getId()));
                            convertRelationshipToEdge(r, tempEdge);
                            lineage.putEdge(tempEdge);
                        } else if (rtype.equals("WasTriggeredBy")) {
                            Edge tempEdge = new WasTriggeredBy((Process) tempVertexTable.get(tempNode.getId()), (Process) tempVertexTable.get(otherNode.getId()));
                            convertRelationshipToEdge(r, tempEdge);
                            lineage.putEdge(tempEdge);
                        } else if (rtype.equals("WasDerivedFrom")) {
                            Edge tempEdge = new WasDerivedFrom((Artifact) tempVertexTable.get(tempNode.getId()), (Artifact) tempVertexTable.get(otherNode.getId()));
                            convertRelationshipToEdge(r, tempEdge);
                            lineage.putEdge(tempEdge);
                        } else if (rtype.equals("WasControlledBy")) {
                            Edge tempEdge = new WasControlledBy((Process) tempVertexTable.get(tempNode.getId()), (Agent) tempVertexTable.get(otherNode.getId()));
                            convertRelationshipToEdge(r, tempEdge);
                            lineage.putEdge(tempEdge);
                        }
                    } else {
                        if (rtype.equals("Used")) {
                            Edge tempEdge = new Used((Process) tempVertexTable.get(otherNode.getId()), (Artifact) tempVertexTable.get(tempNode.getId()));
                            convertRelationshipToEdge(r, tempEdge);
                            lineage.putEdge(tempEdge);
                        } else if (rtype.equals("WasGeneratedBy")) {
                            Edge tempEdge = new WasGeneratedBy((Artifact) tempVertexTable.get(otherNode.getId()), (Process) tempVertexTable.get(tempNode.getId()));
                            convertRelationshipToEdge(r, tempEdge);
                            lineage.putEdge(tempEdge);
                        } else if (rtype.equals("WasTriggeredBy")) {
                            Edge tempEdge = new WasTriggeredBy((Process) tempVertexTable.get(otherNode.getId()), (Process) tempVertexTable.get(tempNode.getId()));
                            convertRelationshipToEdge(r, tempEdge);
                            lineage.putEdge(tempEdge);
                        } else if (rtype.equals("WasDerivedFrom")) {
                            Edge tempEdge = new WasDerivedFrom((Artifact) tempVertexTable.get(otherNode.getId()), (Artifact) tempVertexTable.get(tempNode.getId()));
                            convertRelationshipToEdge(r, tempEdge);
                            lineage.putEdge(tempEdge);
                        } else if (rtype.equals("WasControlledBy")) {
                            Edge tempEdge = new WasControlledBy((Process) tempVertexTable.get(otherNode.getId()), (Agent) tempVertexTable.get(tempNode.getId()));
                            convertRelationshipToEdge(r, tempEdge);
                            lineage.putEdge(tempEdge);
                        }
                    }

                    tempSet2.add(otherNode);
                }
            }
            tempSet.clear();
            tempSet.addAll(tempSet2);
            depth--;
        }

        return lineage;
    }
}
