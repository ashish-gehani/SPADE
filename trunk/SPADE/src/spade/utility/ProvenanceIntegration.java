/*
 -------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2014 SRI International

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
 -------------------------------------------------------------------------------
 */
package spade.utility;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProvenanceIntegration {

    public static String inputPath1 = "/var/tmp/input1.dot";
    public static String inputPath2 = "/var/tmp/input2.dot";
    public static String resultPath = "/var/tmp/result.dot";
    public static int vertexThreshold = 4;
    public static int edgeThreshold = 2;
    public static int tolerance = 2;

    public static void main(String[] args) {
        Graph graph1 = Graph.importGraph(inputPath1);
        Graph graph2 = Graph.importGraph(inputPath2);
        Graph result = Graph.integrate(graph1, graph2, vertexThreshold, edgeThreshold, tolerance);
        result.exportGraph(resultPath);
    }
}

class Graph {

    private static final String ID_STRING = "STORAGE_ID";

    private static final Pattern nodePattern = Pattern.compile("\"(.*)\" \\[label=\"(.*)\" shape=\"(\\w*)\" fillcolor=\"(\\w*)\"", Pattern.DOTALL);
    private static final Pattern edgePattern = Pattern.compile("\"(.*)\" -> \"(.*)\" \\[label=\"(.*)\" color=\"(\\w*)\"", Pattern.DOTALL);

    private final Set<Vertex> vertexSet = new LinkedHashSet<>();
    private final Map<Integer, Vertex> vertexIdentifiers = new HashMap<>();
    private final Map<Vertex, Integer> reverseVertexIdentifiers = new HashMap<>();
    private final Set<Edge> edgeSet = new LinkedHashSet<>();
    private final Map<Integer, Edge> edgeIdentifiers = new HashMap<>();
    private final Map<Edge, Integer> reverseEdgeIdentifiers = new HashMap<>();
    private int serial_number = 1;

    public int integratedVertices = 0;
    public int integratedEdges = 0;
    public int unintegratedVertices = 0;
    public int unintegratedEdges = 0;
    public int regularVertices = 0;
    public int regularEdges = 0;
    public int vertexCost = 0;

    private static int countCommonAnnotations(Vertex v1, Vertex v2) {
        Set<Map.Entry<String, String>> e1 = v1.getAnnotations().entrySet();
        Set<Map.Entry<String, String>> e2 = v2.getAnnotations().entrySet();
        Set<Map.Entry<String, String>> results = new HashSet<>();
        results.addAll(e1);
        results.retainAll(e2);
        return results.size();
    }

    private static int countCommonAnnotations(Edge edge1, Edge edge2) {
        Set<Map.Entry<String, String>> e1 = edge1.getAnnotations().entrySet();
        Set<Map.Entry<String, String>> e2 = edge2.getAnnotations().entrySet();
        Set<Map.Entry<String, String>> results = new HashSet<>();
        results.addAll(e1);
        results.retainAll(e2);
        return results.size();
    }

    private static Vertex integrateVertices(Graph g, List<Vertex> vertices, int tolerance) {
        Vertex vertex = new Vertex();
        int cost = 0;
        for (Vertex v : vertices) {
            int tempcost = 0;

            String currentUID = vertex.getAnnotation("uid");
            String currentGID = vertex.getAnnotation("gid");
            String currentTGID = vertex.getAnnotation("tgid");

            String newUID = v.getAnnotation("uid");
            String newGID = v.getAnnotation("gid");
            String newTGID = v.getAnnotation("tgid");

            vertex.getAnnotations().putAll(v.getAnnotations());
            if (currentUID == null || currentGID == null || currentTGID == null
                    || newUID == null || newGID == null || newTGID == null) {
                continue;
            }

            if (!currentUID.equals(newUID)) {
                tempcost++;
            }
            if (!currentGID.equals(newGID)) {
                tempcost++;
            }
            if (!currentTGID.equals(newTGID)) {
                tempcost++;
            }

            if (tempcost == 3) {
                tempcost = Integer.MAX_VALUE;
            }
            if (tempcost > tolerance) {
                return null;
            }

            if (tempcost == Integer.MAX_VALUE) {
                cost = Integer.MAX_VALUE;
            } else {
                cost += tempcost;
            }
        }

        if (cost == Integer.MAX_VALUE) {
            g.vertexCost = Integer.MAX_VALUE;
        } else {
            g.vertexCost += cost;
        }
        return vertex;
    }

    private static Edge integrateEdges(Graph g, List<Edge> edges) {
        Vertex src = edges.iterator().next().getSourceVertex();
        Vertex dst = edges.iterator().next().getDestinationVertex();
        Edge edge = new Edge(src, dst);
        for (Edge e : edges) {
            edge.getAnnotations().putAll(e.getAnnotations());
        }
        return edge;
    }

    public static Graph integrate(Graph graph1, Graph graph2, int vthreshold, int ethreshold, int tolerance) {
        Map<Vertex, Vertex> integratedVertexMap = new HashMap<>();
        Map<Edge, Edge> integratedEdgeMap = new HashMap<>();
        Set<Integer> vertexIndices = new HashSet<>();
        Set<Integer> edgeIndices = new HashSet<>();

        List<Vertex> allVertices = new ArrayList<>();
        allVertices.addAll(graph1.vertexSet());
        allVertices.addAll(graph2.vertexSet());

        List<Edge> allEdges = new ArrayList<>();
        allEdges.addAll(graph1.edgeSet());
        allEdges.addAll(graph2.edgeSet());

        Graph result = new Graph();

        for (int i = 0; i < allVertices.size() - 1; i++) {
            Vertex v1 = allVertices.get(i);
            List<Vertex> commonSet = new ArrayList<>();
            Set<Integer> tempIndices = new HashSet<>();
            commonSet.add(v1);
            tempIndices.add(i);
            for (int j = i + 1; j < allVertices.size(); j++) {
                if (vertexIndices.contains(i) || vertexIndices.contains(j)) {
                    continue;
                }
                Vertex v2 = allVertices.get(j);
                if (countCommonAnnotations(v1, v2) >= vthreshold) {
                    commonSet.add(v2);
                    tempIndices.add(j);
                }
            }
            if (commonSet.size() > 1) {
                vertexIndices.addAll(tempIndices);
                Vertex integrated = integrateVertices(result, commonSet, tolerance);
                if (integrated == null) {
                    continue;
                }
                for (Vertex tempV : commonSet) {
                    integratedVertexMap.put(tempV, integrated);
                }
                result.unintegratedVertices += commonSet.size();
            }
        }
        for (Vertex v : allVertices) {
            if (integratedVertexMap.containsKey(v)) {
                result.putVertex(integratedVertexMap.get(v));
            } else {
                if (result.putVertex(v)) {
                    result.regularVertices++;
                }
            }
        }

        List<Edge> tempEdges = new ArrayList<>();
        for (Edge e : allEdges) {
            Vertex newSrc = e.getSourceVertex();
            Vertex newDst = e.getDestinationVertex();
            Edge newEdge = new Edge(newSrc, newDst);
            newEdge.getAnnotations().putAll(e.getAnnotations());
            if (integratedVertexMap.containsKey(newSrc)) {
                newEdge.setSourceVertex(integratedVertexMap.get(newSrc));
            }
            if (integratedVertexMap.containsKey(newDst)) {
                newEdge.setDestinationVertex(integratedVertexMap.get(newDst));
            }
            tempEdges.add(newEdge);
        }
        for (int i = 0; i < tempEdges.size() - 1; i++) {
            Edge e1 = tempEdges.get(i);
            List<Edge> commonSet = new ArrayList<>();
            Set<Integer> tempIndices = new HashSet<>();
            commonSet.add(e1);
            tempIndices.add(i);
            for (int j = i + 1; j < tempEdges.size() - 1; j++) {
                if (edgeIndices.contains(i) || edgeIndices.contains(j)) {
                    continue;
                }
                Edge e2 = tempEdges.get(j);
                if (e1.getSourceVertex().equals(e2.getSourceVertex())
                        && e1.getDestinationVertex().equals(e2.getDestinationVertex())
                        && countCommonAnnotations(e1, e2) >= ethreshold) {
                    commonSet.add(e2);
                    tempIndices.add(j);
                }
            }
            if (commonSet.size() > 1) {
                edgeIndices.addAll(tempIndices);
                Edge integrated = integrateEdges(result, commonSet);
                if (integrated == null) {
                    continue;
                }
                for (Edge tempE : commonSet) {
                    integratedEdgeMap.put(tempE, integrated);
                }
                result.unintegratedEdges += commonSet.size();
            }
        }
        for (Edge e : tempEdges) {
            if (integratedEdgeMap.containsKey(e)) {
                result.putEdge(integratedEdgeMap.get(e));
            } else {
                if (result.putEdge(e)) {
                    result.regularEdges++;
                }
            }
        }

        Set<Vertex> tempVertexSet = new HashSet<>();
        tempVertexSet.addAll(integratedVertexMap.values());
        Set<Edge> tempEdgeSet = new HashSet<>();
        tempEdgeSet.addAll(integratedEdgeMap.values());

        result.integratedVertices = tempVertexSet.size();
        result.integratedEdges = tempEdgeSet.size();

        return result;
    }

    public Vertex getVertex(int id) {
        return vertexIdentifiers.get(id);
    }

    public Edge getEdge(int id) {
        return edgeIdentifiers.get(id);
    }

    public int getId(Vertex vertex) {
        return (reverseVertexIdentifiers.containsKey(vertex)) ? reverseVertexIdentifiers.get(vertex) : -1;
    }

    public int getId(Edge edge) {
        return (reverseEdgeIdentifiers.containsKey(edge)) ? reverseEdgeIdentifiers.get(edge) : -1;
    }

    public boolean putVertex(Vertex inputVertex) {
        if (reverseVertexIdentifiers.containsKey(inputVertex)) {
            return false;
        }

        vertexIdentifiers.put(serial_number, inputVertex);
        reverseVertexIdentifiers.put(inputVertex, serial_number);
        vertexSet.add(inputVertex);
        serial_number++;
        return true;
    }

    public boolean putEdge(Edge inputEdge) {
        if (reverseEdgeIdentifiers.containsKey(inputEdge)) {
            return false;
        }

        edgeIdentifiers.put(serial_number, inputEdge);
        reverseEdgeIdentifiers.put(inputEdge, serial_number);
        edgeSet.add(inputEdge);
        serial_number++;
        return true;
    }

    public Set<Vertex> vertexSet() {
        return vertexSet;
    }

    public Set<Edge> edgeSet() {
        return edgeSet;
    }

    public static Graph importGraph(String path) {
        if (path == null) {
            return null;
        }
        File file = new File(path);
        if (!file.exists()) {
            return null;
        }
        Graph result = new Graph();
        Map<String, Vertex> vertexMap = new HashMap<>();
        try {
            BufferedReader eventReader = new BufferedReader(new FileReader(path));
            String line;
            while (true) {
                line = eventReader.readLine();
                if (line == null) {
                    break;
                }
                processImportLine(line, result, vertexMap);
            }
            eventReader.close();
        } catch (IOException exception) {
            exception.printStackTrace();
        }
        return result;
    }

    private static void processImportLine(String line, Graph graph, Map<String, Vertex> vertexMap) {
        try {
            Matcher nodeMatcher = nodePattern.matcher(line);
            Matcher edgeMatcher = edgePattern.matcher(line);
            if (nodeMatcher.find()) {
                String key = nodeMatcher.group(1);
                String label = nodeMatcher.group(2);
                String shape = nodeMatcher.group(3);
                Vertex vertex = new Vertex();
                switch (shape) {
                    case "box":
                        vertex.addAnnotation("type", "Process");
                        break;
                    case "ellipse":
                    case "diamond":
                        vertex.addAnnotation("type", "Artifact");
                        break;
                    case "octagon":
                        vertex.addAnnotation("type", "Agent");
                        break;
                    default:
                        break;
                }
                String[] pairs = label.split("\\\\n");
                for (String pair : pairs) {
                    String key_value[] = pair.split(":", 2);
                    if (key_value.length == 2) {
                        if (key_value[0].equals(ID_STRING)) {
                            continue;
                        }
                        vertex.addAnnotation(key_value[0], key_value[1]);
                    }
                }
                graph.putVertex(vertex);
                vertexMap.put(key, vertex);
            } else if (edgeMatcher.find()) {
                String srckey = edgeMatcher.group(1);
                String dstkey = edgeMatcher.group(2);
                String label = edgeMatcher.group(3);
                String color = edgeMatcher.group(4);
                Edge edge;
                Vertex srcVertex = vertexMap.get(srckey);
                Vertex dstVertex = vertexMap.get(dstkey);
                edge = new Edge(srcVertex, dstVertex);
                switch (color) {
                    case "green":
                        edge.addAnnotation("type", "Used");
                        break;
                    case "red":
                        edge.addAnnotation("type", "WasGeneratedBy");
                        break;
                    case "blue":
                        edge.addAnnotation("type", "WasTriggeredBy");
                        break;
                    case "purple":
                        edge.addAnnotation("type", "WasControlledBy");
                        break;
                    case "orange":
                        edge.addAnnotation("type", "WasDerivedFrom");
                        break;
                    default:
                        break;
                }
                if ((label != null) && (label.length() > 2)) {
                    label = label.substring(1, label.length() - 1);
                    String[] pairs = label.split("\\\\n");
                    for (String pair : pairs) {
                        String key_value[] = pair.split(":", 2);
                        if (key_value.length == 2) {
                            if (key_value[0].equals(ID_STRING)) {
                                continue;
                            }
                            edge.addAnnotation(key_value[0], key_value[1]);
                        }
                    }
                }
                graph.putEdge(edge);
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    public void exportGraph(String path) {
        if ((path == null) || vertexSet.isEmpty()) {
            return;
        }
        try {
            FileWriter writer = new FileWriter(path, false);
            writer.write("digraph spade2dot {\n" + "graph [rankdir = \"RL\"];\n" + "node [fontname=\"Helvetica\" fontsize=\"8\" style=\"filled\" margin=\"0.0,0.0\"];\n"
                    + "edge [fontname=\"Helvetica\" fontsize=\"8\"];\n");
            writer.flush();

            for (Vertex vertex : vertexSet) {
                exportVertex(vertex, writer);
            }
            for (Edge edge : edgeSet) {
                exportEdge(edge, writer);
            }

            writer.write("}\n");
            writer.flush();
            writer.close();
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    private void exportVertex(Vertex vertex, FileWriter writer) {
        try {
            StringBuilder annotationString = new StringBuilder();
            for (Map.Entry<String, String> currentEntry : vertex.getAnnotations().entrySet()) {
                String key = currentEntry.getKey();
                String value = currentEntry.getValue();
                if (key.equals(ID_STRING)) {
                    continue;
                }
                annotationString.append(key.replace("\\", "\\\\")).append(":").append(value.replace("\\", "\\\\")).append("\\n");
            }
            String vertexString = annotationString.substring(0, annotationString.length() - 2);
            String shape = "box";
            String color = "white";
            String type = vertex.getAnnotation("type");
            if (type.equalsIgnoreCase("Agent")) {
                shape = "octagon";
                color = "rosybrown1";
            } else if (type.equalsIgnoreCase("Process") || type.equalsIgnoreCase("Activity")) {
                shape = "box";
                color = "lightsteelblue1";
            } else if (type.equalsIgnoreCase("Artifact") || type.equalsIgnoreCase("Entity")) {
                shape = "ellipse";
                color = "khaki1";
            }

            String key = Integer.toString(reverseVertexIdentifiers.get(vertex));
            writer.write("\"" + key + "\" [label=\"" + vertexString.replace("\"", "'") + "\" shape=\"" + shape + "\" fillcolor=\"" + color + "\"];\n");
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    private void exportEdge(Edge edge, FileWriter writer) {
        try {
            StringBuilder annotationString = new StringBuilder();
            for (Map.Entry<String, String> currentEntry : edge.getAnnotations().entrySet()) {
                String key = currentEntry.getKey();
                String value = currentEntry.getValue();
                if (key.equals(ID_STRING)) {
                    continue;
                }
                annotationString.append(key.replace("\\", "\\\\")).append(":").append(value.replace("\\", "\\\\")).append("\\n");
            }
            String color = "black";
            String type = edge.getAnnotation("type");
            if (type.equalsIgnoreCase("Used")) {
                color = "green";
            } else if (type.equalsIgnoreCase("WasGeneratedBy")) {
                color = "red";
            } else if (type.equalsIgnoreCase("WasTriggeredBy")) {
                color = "blue";
            } else if (type.equalsIgnoreCase("WasControlledBy")) {
                color = "purple";
            } else if (type.equalsIgnoreCase("WasDerivedFrom")) {
                color = "orange";
            }
            String style = "solid";
            if (edge.getAnnotation("success") != null && edge.getAnnotation("success").equals("false")) {
                style = "dashed";
            }

            String edgeString = "(" + annotationString.substring(0, annotationString.length() - 2) + ")";
            String srckey = Integer.toString(reverseVertexIdentifiers.get(edge.getSourceVertex()));
            String dstkey = Integer.toString(reverseVertexIdentifiers.get(edge.getDestinationVertex()));
            writer.write("\"" + srckey + "\" -> \"" + dstkey + "\" [label=\"" + edgeString.replace("\"", "'") + "\" color=\"" + color + "\" style=\"" + style + "\"];\n");
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

}

class Vertex {

    private final Map<String, String> annotations = new HashMap<>();

    public final Map<String, String> getAnnotations() {
        return annotations;
    }

    public final void addAnnotation(String key, String value) {
        if (key == null || value == null) {
            return;
        }
        annotations.put(key, value);
    }

    public final void addAnnotations(Map<String, String> newAnnotations) {
        for (Map.Entry<String, String> currentEntry : newAnnotations.entrySet()) {
            String key = currentEntry.getKey();
            String value = currentEntry.getValue();
            addAnnotation(key, value);
        }
    }

    public final String removeAnnotation(String key) {
        return annotations.remove(key);
    }

    public final String getAnnotation(String key) {
        return annotations.get(key);
    }

    public final String type() {
        return annotations.get("type");
    }

    @Override
    public boolean equals(Object thatObject) {
        if (this == thatObject) {
            return true;
        }
        if (!(thatObject instanceof Vertex)) {
            return false;
        }
        Vertex thatVertex = (Vertex) thatObject;
        return (this.annotations.equals(thatVertex.annotations));
    }

    @Override
    public int hashCode() {
        final int seed1 = 67;
        final int seed2 = 3;
        int hashCode = seed2;
        hashCode = seed1 * hashCode + (this.annotations != null ? this.annotations.hashCode() : 0);
        return hashCode;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> currentEntry : annotations.entrySet()) {
            result.append(currentEntry.getKey());
            result.append(":");
            result.append(currentEntry.getValue());
            result.append("|");
        }
        return result.substring(0, result.length() - 1);
    }
}

class Edge {

    protected Map<String, String> annotations = new HashMap<>();
    private Vertex sourceVertex;
    private Vertex destinationVertex;

    public Edge(Vertex sourceVertex, Vertex destinationVertex) {
        setSourceVertex(sourceVertex);
        setDestinationVertex(destinationVertex);
    }

    public final Map<String, String> getAnnotations() {
        return annotations;
    }

    public final void addAnnotation(String key, String value) {
        if (key == null || value == null) {
            return;
        }
        annotations.put(key, value);
    }

    public final void addAnnotations(Map<String, String> newAnnotations) {
        for (Map.Entry<String, String> currentEntry : newAnnotations.entrySet()) {
            String key = currentEntry.getKey();
            String value = currentEntry.getValue();
            addAnnotation(key, value);
        }
    }

    public final String removeAnnotation(String key) {
        return annotations.remove(key);
    }

    public final String getAnnotation(String key) {
        return annotations.get(key);
    }

    public final String type() {
        return annotations.get("type");
    }

    public final Vertex getSourceVertex() {
        return sourceVertex;
    }

    public final Vertex getDestinationVertex() {
        return destinationVertex;
    }

    public final void setSourceVertex(Vertex sourceVertex) {
        this.sourceVertex = sourceVertex;
    }

    public final void setDestinationVertex(Vertex destinationVertex) {
        this.destinationVertex = destinationVertex;
    }

    @Override
    public boolean equals(Object thatObject) {
        if (this == thatObject) {
            return true;
        }
        if (!(thatObject instanceof Edge)) {
            return false;
        }
        Edge thatEdge = (Edge) thatObject;
        return (this.annotations.equals(thatEdge.annotations)
                && this.getSourceVertex().equals(thatEdge.getSourceVertex())
                && this.getDestinationVertex().equals(thatEdge.getDestinationVertex()));
    }

    @Override
    public int hashCode() {
        final int seed1 = 5;
        final int seed2 = 97;
        int hashCode = seed1;
        hashCode = seed2 * hashCode + (this.annotations != null ? this.annotations.hashCode() : 0);
        hashCode = seed2 * hashCode + (this.sourceVertex != null ? this.sourceVertex.hashCode() : 0);
        hashCode = seed2 * hashCode + (this.destinationVertex != null ? this.destinationVertex.hashCode() : 0);
        return hashCode;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> currentEntry : annotations.entrySet()) {
            result.append(currentEntry.getKey());
            result.append(":");
            result.append(currentEntry.getValue());
            result.append("|");
        }
        return result.substring(0, result.length() - 1);
    }
}
