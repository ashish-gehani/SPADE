/*
--------------------------------------------------------------------------------
SPADE - Support for Provenance Auditing in Distributed Environments.
Copyright (C) 2010 SRI International

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

import java.util.*;
import java.io.*;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.RelationshipIndex;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.remote.RemoteGraphDatabase;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.helpers.collection.MapUtil;
import org.jgrapht.ext.*;

public class Neo4JQuery implements QueryInterface {

    private GraphDatabaseService graphDb;                                       /* Neo4j graph database instance */

    private Index<Node> vertexIndex;                                            /* index for vertices */

    private RelationshipIndex edgeIndex;                                        /* index for edges */


    public void initialize(String path) {
        try {
            graphDb = new EmbeddedGraphDatabase(path);
            /* initialize vertex and edge indexes and specify configuration */
            vertexIndex = graphDb.index().forNodes("vertexIndex", MapUtil.stringMap("provider", "lucene", "type", "fulltext"));
            edgeIndex = graphDb.index().forRelationships("edgeIndex", MapUtil.stringMap("provider", "lucene", "type", "fulltext"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void shutdown() {
        graphDb.shutdown();
    }

    private void convertNodeToVertex(Node n, Vertex v) {
        for (String key : n.getPropertyKeys()) {
            String value = (String) n.getProperty(key);
            v.addAnnotation(key, value);
        }
    }

    private void convertRelationshipToEdge(Relationship r, Edge e) {
        for (String key : r.getPropertyKeys()) {
            String value = (String) r.getProperty(key);
            e.addAnnotation(key, value);
        }
    }

    public Set<String> getKeySet(String OPMObjectName) {
        Set<String> keySet = new HashSet<String>();
        if (OPMObjectName.equals("process")) {
            keySet.add("PID");
            keySet.add("PPID");
            keySet.add("PIDNAME");
            keySet.add("TGID");
            keySet.add("TRACERPID");
            keySet.add("UID");
            keySet.add("GID");
            keySet.add("STARTTIME");
            keySet.add("GROUP");
            keySet.add("SESSION");
            keySet.add("TYPE");
        } else if (OPMObjectName.equals("artifact")) {
            keySet.add("FILENAME");
            keySet.add("PATH");
            keySet.add("MODIFIED");
            keySet.add("SIZE");
            keySet.add("TYPE");
        }
        return keySet;
    }

    public Set<Vertex> getVertices(String expression) {
        Set<Vertex> vertexSet = new HashSet<Vertex>();                          /* create empty result set to store matching vertices */
        for (Node foundNode : vertexIndex.query(expression)) {                  /* evaluate expression and iterate over nodes */
            String type = (String) foundNode.getProperty("TYPE");               /* determine type of vertex */
            Vertex tempVertex;                                                  /* create vertex object according to type and populate annotations */
            if (type.equals("Process")) {
                tempVertex = new Process();
            } else if (type.equals("Artifact")) {
                tempVertex = new Artifact();
            } else {
                tempVertex = new Agent();
            }
            convertNodeToVertex(foundNode, tempVertex);
            vertexSet.add(tempVertex);                                          /* add final populated vertex to result set */
        }
        return vertexSet;                                                       /* return result set */
    }

    public Set<Edge> getEdges(String expression) {
        Set<Edge> edgeSet = new HashSet<Edge>();                                    /* create empty result set to store matching edges */
        for (Relationship foundRelationship : edgeIndex.query(expression)) {        /* evaluate expression and iterate over relationships */
            String type = (String) foundRelationship.getProperty("TYPE");           /* determine edge type: create and populate edge annotations */
            if (type.equals("Used")) {
                Process vertex1 = new Process();
                Artifact vertex2 = new Artifact();
                convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                Edge tempEdge = new Used(vertex1, vertex2, "Used", "Used");
                convertRelationshipToEdge(foundRelationship, tempEdge);
                edgeSet.add(tempEdge);
            } else if (type.equals("WasGeneratedBy")) {
                Artifact vertex1 = new Artifact();
                Process vertex2 = new Process();
                convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                Edge tempEdge = new WasGeneratedBy(vertex1, vertex2, "WasGeneratedBy", "WasGeneratedBy");
                convertRelationshipToEdge(foundRelationship, tempEdge);
                edgeSet.add(tempEdge);
            } else if (type.equals("WasTriggeredBy")) {
                Process vertex1 = new Process();
                Process vertex2 = new Process();
                convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                Edge tempEdge = new WasTriggeredBy(vertex1, vertex2, "WasTriggeredBy", "WasTriggeredBy");
                convertRelationshipToEdge(foundRelationship, tempEdge);
                edgeSet.add(tempEdge);
            } else if (type.equals("WasDerivedFrom")) {
                Artifact vertex1 = new Artifact();
                Artifact vertex2 = new Artifact();
                convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                Edge tempEdge = new WasDerivedFrom(vertex1, vertex2, "WasDerivedFrom", "WasDerivedFrom");
                convertRelationshipToEdge(foundRelationship, tempEdge);
                edgeSet.add(tempEdge);
            } else if (type.equals("WasControlledBy")) {
                Process vertex1 = new Process();
                Agent vertex2 = new Agent();
                convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                Edge tempEdge = new WasControlledBy(vertex1, vertex2, "WasControlledBy", "WasControlledBy");
                convertRelationshipToEdge(foundRelationship, tempEdge);
                edgeSet.add(tempEdge);
            }
        }
        return edgeSet;
    }

    public Set<Edge> getEdges(String sourceExpression, String destinationExpression, String edgeExpression) {
        Set<Vertex> sourceSet = getVertices(sourceExpression);                              /* get set of source vertices matching the expression */
        Set<Vertex> destinationSet = getVertices(destinationExpression);                    /* get set of destination vertices matching the expression */
        Set<Edge> edgeSet = new HashSet<Edge>();                                            /* create empty result set to store matching edges */
        for (Relationship foundRelationship : edgeIndex.query(edgeExpression)) {            /* evaluate edge expression and iterate over relationships */
            String type = (String) foundRelationship.getProperty("TYPE");                   /* determine edge type */
            if (type.equals("Used")) {
                Process vertex1 = new Process();
                Artifact vertex2 = new Artifact();
                convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                if (sourceSet.contains(vertex1) && destinationSet.contains(vertex2)) {      /* check that relationship source and destination nodes */
                    Edge tempEdge = new Used(vertex1, vertex2, "Used", "Used");             /* are in the sets that we populated earlier */
                    convertRelationshipToEdge(foundRelationship, tempEdge);
                    edgeSet.add(tempEdge);
                }
            } else if (type.equals("WasGeneratedBy")) {
                Artifact vertex1 = new Artifact();
                Process vertex2 = new Process();
                convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                if (sourceSet.contains(vertex1) && destinationSet.contains(vertex2)) {
                    Edge tempEdge = new WasGeneratedBy(vertex1, vertex2, "WasGeneratedBy", "WasGeneratedBy");
                    convertRelationshipToEdge(foundRelationship, tempEdge);
                    edgeSet.add(tempEdge);
                }
            } else if (type.equals("WasTriggeredBy")) {
                Process vertex1 = new Process();
                Process vertex2 = new Process();
                convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                if (sourceSet.contains(vertex1) && destinationSet.contains(vertex2)) {
                    Edge tempEdge = new WasTriggeredBy(vertex1, vertex2, "WasTriggeredBy", "WasTriggeredBy");
                    convertRelationshipToEdge(foundRelationship, tempEdge);
                    edgeSet.add(tempEdge);
                }
            } else if (type.equals("WasDerivedFrom")) {
                Artifact vertex1 = new Artifact();
                Artifact vertex2 = new Artifact();
                convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                if (sourceSet.contains(vertex1) && destinationSet.contains(vertex2)) {
                    Edge tempEdge = new WasDerivedFrom(vertex1, vertex2, "WasDerivedFrom", "WasDerivedFrom");
                    convertRelationshipToEdge(foundRelationship, tempEdge);
                    edgeSet.add(tempEdge);
                }
            } else if (type.equals("WasControlledBy")) {
                Process vertex1 = new Process();
                Agent vertex2 = new Agent();
                convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                if (sourceSet.contains(vertex1) && destinationSet.contains(vertex2)) {
                    Edge tempEdge = new WasControlledBy(vertex1, vertex2, "WasControlledBy", "WasControlledBy");
                    convertRelationshipToEdge(foundRelationship, tempEdge);
                    edgeSet.add(tempEdge);
                }
            }
        }
        return edgeSet;
    }

    public Set<Edge> getEdges(Vertex source, Vertex destination) {
        HashMap<String, String> sourceAnnotations = source.getAnnotations();
        HashMap<String, String> destinationAnnotations = destination.getAnnotations();
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
                IndexHits<Relationship> relationshipHits = edgeIndex.query("TYPE:*", srcNode, dstNode);
                while (relationshipHits.hasNext()) {
                    Relationship foundRelationship = relationshipHits.next();
                    String type = (String) foundRelationship.getProperty("TYPE");
                    if (type.equals("Used")) {
                        Process vertex1 = new Process();
                        Artifact vertex2 = new Artifact();
                        convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                        convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                        Edge tempEdge = new Used(vertex1, vertex2, "Used", "Used");
                        convertRelationshipToEdge(foundRelationship, tempEdge);
                        edgeSet.add(tempEdge);
                    } else if (type.equals("WasGeneratedBy")) {
                        Artifact vertex1 = new Artifact();
                        Process vertex2 = new Process();
                        convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                        convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                        Edge tempEdge = new WasGeneratedBy(vertex1, vertex2, "WasGeneratedBy", "WasGeneratedBy");
                        convertRelationshipToEdge(foundRelationship, tempEdge);
                        edgeSet.add(tempEdge);
                    } else if (type.equals("WasTriggeredBy")) {
                        Process vertex1 = new Process();
                        Process vertex2 = new Process();
                        convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                        convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                        Edge tempEdge = new WasTriggeredBy(vertex1, vertex2, "WasTriggeredBy", "WasTriggeredBy");
                        convertRelationshipToEdge(foundRelationship, tempEdge);
                        edgeSet.add(tempEdge);
                    } else if (type.equals("WasDerivedFrom")) {
                        Artifact vertex1 = new Artifact();
                        Artifact vertex2 = new Artifact();
                        convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                        convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                        Edge tempEdge = new WasDerivedFrom(vertex1, vertex2, "WasDerivedFrom", "WasDerivedFrom");
                        convertRelationshipToEdge(foundRelationship, tempEdge);
                        edgeSet.add(tempEdge);
                    } else if (type.equals("WasControlledBy")) {
                        Process vertex1 = new Process();
                        Agent vertex2 = new Agent();
                        convertNodeToVertex(foundRelationship.getStartNode(), vertex1);
                        convertNodeToVertex(foundRelationship.getEndNode(), vertex2);
                        Edge tempEdge = new WasControlledBy(vertex1, vertex2, "WasControlledBy", "WasControlledBy");
                        convertRelationshipToEdge(foundRelationship, tempEdge);
                        edgeSet.add(tempEdge);
                    }
                }
            }
        }
        return edgeSet;
    }

    public Lineage getLineage(Vertex source, String pruneExpression, int direction, boolean includeTerminatingNode) {
        Lineage lineage = new Lineage(source, pruneExpression);
        HashMap VertexTable = new HashMap<Long, Vertex>();

        Set<Node> pruneSet = new HashSet<Node>();
        for (Node foundNode : vertexIndex.query(pruneExpression)) {
            pruneSet.add(foundNode);
        }

        HashMap<String, String> sourceAnnotations = source.getAnnotations();
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
        String type = (String) sourceNode.getProperty("TYPE");
        Vertex tempVertex;
        if (type.equals("Process")) {
            tempVertex = new Process();
        } else if (type.equals("Artifact")) {
            tempVertex = new Artifact();
        } else {
            tempVertex = new Agent();
        }
        convertNodeToVertex(sourceNode, tempVertex);
        VertexTable.put(sourceNode.getId(), tempVertex);
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
                    String otherNodeType = (String) otherNode.getProperty("TYPE");
                    Vertex otherVertex;
                    if (otherNodeType.equals("Process")) {
                        otherVertex = new Process();
                    } else if (otherNodeType.equals("Artifact")) {
                        otherVertex = new Artifact();
                    } else {
                        otherVertex = new Agent();
                    }
                    convertNodeToVertex(otherNode, otherVertex);
                    VertexTable.put(otherNode.getId(), otherVertex);
                    lineage.putVertex(otherVertex);

                    String rtype = (String) r.getProperty("TYPE");
                    if (rtype.equals("Used")) {
                        Edge tempEdge = new Used((Process) VertexTable.get(tempNode.getId()), (Artifact) VertexTable.get(otherNode.getId()), "Used", "Used");
                        convertRelationshipToEdge(r, tempEdge);
                        lineage.putEdge(tempEdge);
                    } else if (rtype.equals("WasGeneratedBy")) {
                        Edge tempEdge = new WasGeneratedBy((Artifact) VertexTable.get(tempNode.getId()), (Process) VertexTable.get(otherNode.getId()), "WasGeneratedBy", "WasGeneratedBy");
                        convertRelationshipToEdge(r, tempEdge);
                        lineage.putEdge(tempEdge);
                    } else if (rtype.equals("WasTriggeredBy")) {
                        Edge tempEdge = new WasTriggeredBy((Process) VertexTable.get(tempNode.getId()), (Process) VertexTable.get(otherNode.getId()), "WasTriggeredBy", "WasTriggeredBy");
                        convertRelationshipToEdge(r, tempEdge);
                        lineage.putEdge(tempEdge);
                    } else if (rtype.equals("WasDerivedFrom")) {
                        Edge tempEdge = new WasDerivedFrom((Artifact) VertexTable.get(tempNode.getId()), (Artifact) VertexTable.get(otherNode.getId()), "WasDerivedFrom", "WasDerivedFrom");
                        convertRelationshipToEdge(r, tempEdge);
                        lineage.putEdge(tempEdge);
                    } else if (rtype.equals("WasControlledBy")) {
                        Edge tempEdge = new WasControlledBy((Process) VertexTable.get(tempNode.getId()), (Agent) VertexTable.get(otherNode.getId()), "WasControlledBy", "WasControlledBy");
                        convertRelationshipToEdge(r, tempEdge);
                        lineage.putEdge(tempEdge);
                    }

                    if (pruneSet.contains(otherNode) == false) {
                        tempSet2.add(otherNode);
                    }
                }
            }
            tempSet.clear();
            tempSet.addAll(tempSet2);
        }

        exportDOT(lineage);
        return lineage;
    }

    public Lineage getLineage(Vertex source, int depth, int direction) {
        Lineage lineage = new Lineage(source, depth);
        HashMap VertexTable = new HashMap<Long, Vertex>();

        HashMap<String, String> sourceAnnotations = source.getAnnotations();
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
        String type = (String) sourceNode.getProperty("TYPE");
        Vertex tempVertex;
        if (type.equals("Process")) {
            tempVertex = new Process();
        } else if (type.equals("Artifact")) {
            tempVertex = new Artifact();
        } else {
            tempVertex = new Agent();
        }
        convertNodeToVertex(sourceNode, tempVertex);
        VertexTable.put(sourceNode.getId(), tempVertex);
        lineage.putVertex(tempVertex);

        Set<Node> tempSet = new HashSet<Node>();
        tempSet.add(sourceNode);
        while (true) {
            if (depth == 0) {
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
                    String otherNodeType = (String) otherNode.getProperty("TYPE");
                    Vertex otherVertex;
                    if (otherNodeType.equals("Process")) {
                        otherVertex = new Process();
                    } else if (otherNodeType.equals("Artifact")) {
                        otherVertex = new Artifact();
                    } else {
                        otherVertex = new Agent();
                    }
                    convertNodeToVertex(otherNode, otherVertex);
                    VertexTable.put(otherNode.getId(), otherVertex);
                    lineage.putVertex(otherVertex);

                    String rtype = (String) r.getProperty("TYPE");
                    if (rtype.equals("Used")) {
                        Edge tempEdge = new Used((Process) VertexTable.get(tempNode.getId()), (Artifact) VertexTable.get(otherNode.getId()), "Used", "Used");
                        convertRelationshipToEdge(r, tempEdge);
                        lineage.putEdge(tempEdge);
                    } else if (rtype.equals("WasGeneratedBy")) {
                        Edge tempEdge = new WasGeneratedBy((Artifact) VertexTable.get(tempNode.getId()), (Process) VertexTable.get(otherNode.getId()), "WasGeneratedBy", "WasGeneratedBy");
                        convertRelationshipToEdge(r, tempEdge);
                        lineage.putEdge(tempEdge);
                    } else if (rtype.equals("WasTriggeredBy")) {
                        Edge tempEdge = new WasTriggeredBy((Process) VertexTable.get(tempNode.getId()), (Process) VertexTable.get(otherNode.getId()), "WasTriggeredBy", "WasTriggeredBy");
                        convertRelationshipToEdge(r, tempEdge);
                        lineage.putEdge(tempEdge);
                    } else if (rtype.equals("WasDerivedFrom")) {
                        Edge tempEdge = new WasDerivedFrom((Artifact) VertexTable.get(tempNode.getId()), (Artifact) VertexTable.get(otherNode.getId()), "WasDerivedFrom", "WasDerivedFrom");
                        convertRelationshipToEdge(r, tempEdge);
                        lineage.putEdge(tempEdge);
                    } else if (rtype.equals("WasControlledBy")) {
                        Edge tempEdge = new WasControlledBy((Process) VertexTable.get(tempNode.getId()), (Agent) VertexTable.get(otherNode.getId()), "WasControlledBy", "WasControlledBy");
                        convertRelationshipToEdge(r, tempEdge);
                        lineage.putEdge(tempEdge);
                    }
                    tempSet2.add(otherNode);
                }
            }
            tempSet.clear();
            tempSet.addAll(tempSet2);
            depth--;
        }

        exportDOT(lineage);
        return lineage;
    }

    private void exportDOT(Lineage l) {
        try {
            FileWriter out = new FileWriter("export.dot", false);
            DOTExporter d = new DOTExporter(new IDProvider(), new LabelProvider(), new EdgeProvider());
            d.export(out, l.getGraph());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class IDProvider implements VertexNameProvider<Vertex> {

    @Override
    public String getVertexName(Vertex v) {
        if (v.getVertexType().equals("Process")) {
            return v.getAnnotationValue("PID");
        } else {
            return v.getAnnotationValue("PATH");
        }
    }
}

class LabelProvider implements VertexNameProvider<Vertex> {

    @Override
    public String getVertexName(Vertex v) {
        return v.toString();
    }
}

class EdgeProvider implements EdgeNameProvider<Edge> {

    @Override
    public String getEdgeName(Edge e) {
        return e.getEdgeType();
    }
}
