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
package spade.core;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;

import spade.edge.opm.Used;
import spade.edge.opm.WasControlledBy;
import spade.edge.opm.WasDerivedFrom;
import spade.edge.opm.WasGeneratedBy;
import spade.edge.opm.WasTriggeredBy;
import spade.vertex.opm.Agent;
import spade.vertex.opm.Artifact;
import spade.vertex.opm.Process;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class is used to represent query responses using sets for edges and
 * vertices.
 *
 * @author Dawood Tariq
 */
public class Graph extends AbstractStorage implements Serializable
{
	
    private static final Logger logger = Logger.getLogger(Graph.class.getName());
    private static final int MAX_QUERY_HITS = 1000;
    private static final String SRC_VERTEX_ID = "SRC_VERTEX_ID";
    private static final String DST_VERTEX_ID = "DST_VERTEX_ID";

    private static final Pattern nodePattern = Pattern.compile("\"(.*)\" \\[label=\"(.*)\" shape=\"(\\w*)\" fillcolor=\"(\\w*)\"", Pattern.DOTALL);
    private static final Pattern edgePattern = Pattern.compile("\"(.*)\" -> \"(.*)\" \\[label=\"(.*)\" color=\"(\\w*)\"", Pattern.DOTALL);

    private transient Analyzer analyzer = new KeywordAnalyzer();
    private transient QueryParser queryParser = new QueryParser(null, analyzer);
    private Set<AbstractVertex> vertexSet = new LinkedHashSet<>();
    private Map<String, AbstractVertex> vertexIdentifiers = new HashMap<>();
    private Map<AbstractVertex, String> reverseVertexIdentifiers = new HashMap<>();
    private Set<AbstractEdge> edgeSet = new LinkedHashSet<>();
    private Map<String, AbstractEdge> edgeIdentifiers = new HashMap<>();
    private Map<AbstractEdge, String> reverseEdgeIdentifiers = new HashMap<>();
    private Map<AbstractVertex, Integer> networkMap = new HashMap<>();
    private int serial_number = 1;
    /**
     * For query results spanning multiple hosts, this is used to indicate
     * whether the network boundaries have been properly transformed.
     */
    public boolean transformed = false;
    private Directory vertexIndex;
    private Directory edgeIndex;
    private transient IndexWriter vertexIndexWriter;
    private transient IndexWriter edgeIndexWriter;

    /**
     * Fields for discrepancy check and query params
     */
    private String hostName;
    private String computeTime;
    private int maxDepth;
    private AbstractVertex rootVertex;

    public AbstractVertex getDestinationVertex()
    {
        return destinationVertex;
    }

    private AbstractVertex destinationVertex;
    private String signature;
    
    public void mergeThreads() {

    }

    /**
     * An empty constructor.
     */
    public Graph() {
        // Lucene initialization
        try {
            vertexIndex = new RAMDirectory();
            edgeIndex = new RAMDirectory();
            vertexIndexWriter = new IndexWriter(vertexIndex, new IndexWriterConfig(analyzer));
            edgeIndexWriter = new IndexWriter(edgeIndex, new IndexWriterConfig(analyzer));
            queryParser.setAllowLeadingWildcard(true);
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
        }
    }

    public String getHash(AbstractVertex vertex)
    {
        return (reverseVertexIdentifiers.containsKey(vertex)) ? reverseVertexIdentifiers.get(vertex) : null;
    }

    public String getHash(AbstractEdge edge)
    {
        return (reverseEdgeIdentifiers.containsKey(edge)) ? reverseEdgeIdentifiers.get(edge) : null;
    }

    /**
     * This method is used to put the network vertices in the network vertex
     * map. The network vertex map is used when doing remote querying.
     *
     * @param inputVertex The network vertex
     * @param depth The depth of this vertex from the source vertex
     */
    public void putNetworkVertex(AbstractVertex inputVertex, int depth) {
        networkMap.put(inputVertex, depth);
    }

    /**
     * This function inserts the given vertex into the underlying storage(s) and
     * updates the cache(s) accordingly.
     *
     * @param incomingVertex vertex to insert into the storage
     * @return returns true if the insertion is successful. Insertion is considered
     * not successful if the vertex is already present in the storage.
     */
    @Override
    public boolean putVertex(AbstractVertex incomingVertex)
    {
        if (reverseVertexIdentifiers.containsKey(incomingVertex))
        {
            return false;
        }
        // Add vertex to Lucene index
        try
        {
            Document doc = new Document();
            for (Map.Entry<String, String> currentEntry : incomingVertex.getAnnotations().entrySet())
            {
                String key = currentEntry.getKey();
                String value = currentEntry.getValue();
                doc.add(new Field(key, value, Field.Store.YES, Field.Index.ANALYZED));
            }
            doc.add(new Field(PRIMARY_KEY, Integer.toString(serial_number), Field.Store.YES, Field.Index.ANALYZED));
            // vertexIndexWriter.addDocument(doc);
            // vertexIndexWriter.commit();

            String hashCode = incomingVertex.bigHashCode();
            vertexIdentifiers.put(hashCode, incomingVertex);
            reverseVertexIdentifiers.put(incomingVertex, hashCode);
            vertexSet.add(incomingVertex);
            serial_number++;
        }
        catch (Exception exception)
        {
            logger.log(Level.SEVERE, null, exception);
        }
        return true;
    }

    @Override
    public Object executeQuery(String query) {
        return null;
    }

    /**
     * This function inserts the given edge into the underlying storage(s) and
     * updates the cache(s) accordingly.
     *
     * @param incomingEdge edge to insert into the storage
     * @return returns true if the insertion is successful. Insertion is considered
     * not successful if the edge is already present in the storage.
     */
    @Override
    public boolean putEdge(AbstractEdge incomingEdge)
    {
        if (reverseEdgeIdentifiers.containsKey(incomingEdge))
        {
            return false;
        }
        // Add edge to Lucene index
        try
        {
            Document doc = new Document();
            for (Map.Entry<String, String> currentEntry : incomingEdge.getAnnotations().entrySet())
            {
                String key = currentEntry.getKey();
                String value = currentEntry.getValue();
                doc.add(new Field(key, value, Field.Store.YES, Field.Index.ANALYZED));
            }
            doc.add(new Field(PRIMARY_KEY, Integer.toString(serial_number), Field.Store.YES, Field.Index.ANALYZED));
            doc.add(new Field(SRC_VERTEX_ID, reverseVertexIdentifiers.get(incomingEdge.getChildVertex()), Field.Store.YES, Field.Index.ANALYZED));
            doc.add(new Field(DST_VERTEX_ID, reverseVertexIdentifiers.get(incomingEdge.getParentVertex()), Field.Store.YES, Field.Index.ANALYZED));
//            edgeIndexWriter.addDocument(doc);
//            edgeIndexWriter.commit();

            String hashCode = incomingEdge.getChildVertex().bigHashCode() + incomingEdge.getParentVertex().bigHashCode();
            edgeIdentifiers.put(hashCode, incomingEdge);
            reverseEdgeIdentifiers.put(incomingEdge, hashCode);
            edgeSet.add(incomingEdge);
            serial_number++;
        }
        catch (Exception exception)
        {
            logger.log(Level.SEVERE, null, exception);
        }
        return true;
    }

    public void commitIndex() {
        try {
            vertexIndexWriter.commit();
            edgeIndexWriter.commit();
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
        }
    }

    /**
     * Checks if the two given graphs are equal.
     * @return True if both graphs have the same vertices and edges
     */
    public boolean equals(Graph otherGraph)
    {
        if(this.vertexSet().size() != otherGraph.vertexSet().size())
            return false;
        if(this.edgeSet().size() != otherGraph.edgeSet().size())
            return false;

        /*
        * Compare the sets of vertices and edges by their IDs.
        * This should ordinarily work with hashes and overriding equals() and hashCode() methods.
        * hashCode() is buggy. Meanwhile adding this method to compare two graphs.
        */

        // Compares the sets of vertices
        Iterator<AbstractVertex> thisVertex = this.vertexSet().iterator();
        Set<String> thisVertexIds = new HashSet<>();
        while(thisVertex.hasNext())
        {
            thisVertexIds.add(thisVertex.next().getAnnotation("vertexId"));
        }
        Iterator<AbstractVertex> otherVertex = otherGraph.vertexSet().iterator();
        Set<String> otherVertexIds = new HashSet<>();
        while(otherVertex.hasNext())
        {
            otherVertexIds.add(otherVertex.next().getAnnotation("vertexId"));
        }
        if(!thisVertexIds.equals(otherVertexIds))
            return false;

        // Compare the sets of edges
        Iterator<AbstractEdge> thisEdge = this.edgeSet().iterator();
        Set<String> thisEdgeIds = new HashSet<>();
        while(thisEdge.hasNext())
        {
            thisEdgeIds.add(thisEdge.next().getAnnotation("edgeId"));
        }
        Iterator<AbstractEdge> otherEdge = otherGraph.edgeSet().iterator();
        Set<String> otherEdgeIds = new HashSet<>();
        while(otherEdge.hasNext())
        {
            otherEdgeIds.add(otherEdge.next().getAnnotation("edgeId"));
        }
        if(!thisEdgeIds.equals(otherEdgeIds))
            return false;

        return true;

    }


    /**
     *
     * Returns the status of graph as empty or non-empty
     *
     * @return True if the graph contains no vertex
     */
    public boolean isEmpty() { return (vertexSet().size() > 0); }

    /**
     * Returns the set containing the vertices.
     *
     * @return The set containing the vertices.
     */
    public Set<AbstractVertex> vertexSet() {
        return vertexSet;
    }

    /**
     * Returns the set containing the edges.
     *
     * @return The set containing edges.
     */
    public Set<AbstractEdge> edgeSet() {
        return edgeSet;
    }

    /**
     * Returns the map of network vertices for this graph.
     *
     * @return The map containing the network vertices and their depth relative
     * to the source vertex.
     */
    public Map<AbstractVertex, Integer> networkMap() {
        return networkMap;
    }

    /**
     * This method is used to create a new graph as an intersection of the two
     * given input graphs. This is done simply by using set functions on the
     * vertex and edge sets.
     *
     * @param graph1 Input graph 1
     * @param graph2 Input graph 2
     * @return The result graph
     */
    public static Graph intersection(Graph graph1, Graph graph2) {
        Graph resultGraph = new Graph();
        Set<AbstractVertex> vertices = new HashSet<>();
        Set<AbstractEdge> edges = new HashSet<>();

        vertices.addAll(graph1.vertexSet());
        vertices.retainAll(graph2.vertexSet());
        edges.addAll(graph1.edgeSet());
        edges.retainAll(graph2.edgeSet());

        for (AbstractVertex vertex : vertices) {
            resultGraph.putVertex(vertex);
        }
        for (AbstractEdge edge : edges) {
            resultGraph.putEdge(edge);
        }

        resultGraph.commitIndex();
        return resultGraph;
    }

    /**
     * This method is used to create a new graph as a union of the two given
     * input graphs. This is done simply by using set functions on the vertex
     * and edge sets.
     *
     * @param graph1 Input graph 1
     * @param graph2 Input graph 2
     * @return The result graph
     */
    public static Graph union(Graph graph1, Graph graph2)
    {
        Graph resultGraph = new Graph();
        Set<AbstractVertex> vertices = new HashSet<>();
        Set<AbstractEdge> edges = new HashSet<>();

        vertices.addAll(graph1.vertexSet());
        vertices.addAll(graph2.vertexSet());
        edges.addAll(graph1.edgeSet());
        edges.addAll(graph2.edgeSet());

        for (AbstractVertex vertex : vertices)
        {
            resultGraph.putVertex(vertex);
        }
        for (AbstractEdge edge : edges)
        {
            resultGraph.putEdge(edge);
        }

        // adding network maps
        resultGraph.networkMap.putAll(graph1.networkMap());
        resultGraph.networkMap.putAll(graph2.networkMap());

        resultGraph.commitIndex();

        return resultGraph;
    }

    /**
     * This method is used to create a new graph obtained by removing all
     * elements of the second graph from the first graph given as inputs.
     *
     * @param graph1 Input graph 1
     * @param graph2 Input graph 2
     * @return The result graph
     */
    public static Graph remove(Graph graph1, Graph graph2) {
        Graph resultGraph = new Graph();
        Set<AbstractVertex> vertices = new HashSet<>();
        Set<AbstractEdge> edges = new HashSet<>();

        vertices.addAll(graph1.vertexSet());
        vertices.removeAll(graph2.vertexSet());
        edges.addAll(graph1.edgeSet());
        edges.removeAll(graph2.edgeSet());

        for (AbstractVertex vertex : vertices) {
            resultGraph.putVertex(vertex);
        }
        for (AbstractEdge edge : edges) {
            resultGraph.putEdge(edge);
        }

        resultGraph.commitIndex();
        return resultGraph;
    }

    public void remove(Graph graph)
    {
        vertexSet.removeAll(graph.vertexSet());
        edgeSet.removeAll(graph.edgeSet());
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
        Map<String, AbstractVertex> vertexMap = new HashMap<>();
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
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        result.commitIndex();
        return result;
    }

    private static void processImportLine(String line, Graph graph, Map<String, AbstractVertex> vertexMap) {
        try {
            Matcher nodeMatcher = nodePattern.matcher(line);
            Matcher edgeMatcher = edgePattern.matcher(line);
            if (nodeMatcher.find()) {
                String key = nodeMatcher.group(1);
                String label = nodeMatcher.group(2);
                String shape = nodeMatcher.group(3);
                AbstractVertex vertex;
                if (shape.equals("box")) {
                    vertex = new Process();
                } else if (shape.equals("ellipse") || shape.equals("diamond")) {
                    vertex = new Artifact();
                } else if (shape.equals("octagon")) {
                    vertex = new Agent();
                } else {
                    vertex = new Vertex();
                }
                String[] pairs = label.split("\\\\n");
                for (String pair : pairs) {
                    String key_value[] = pair.split(":", 2);
                    if (key_value.length == 2) {
                        vertex.addAnnotation(key_value[0], key_value[1]);
                    }
                }
                graph.putVertex(vertex);
                vertexMap.put(key, vertex);
            } else if (edgeMatcher.find()) {
                String childkey = edgeMatcher.group(1);
                String dstkey = edgeMatcher.group(2);
                String label = edgeMatcher.group(3);
                String color = edgeMatcher.group(4);
                AbstractEdge edge;
                AbstractVertex childVertex = vertexMap.get(childkey);
                AbstractVertex parentVertex = vertexMap.get(dstkey);
                if (color.equals("green")) {
                    edge = new Used((Process) childVertex, (Artifact) parentVertex);
                } else if (color.equals("red")) {
                    edge = new WasGeneratedBy((Artifact) childVertex, (Process) parentVertex);
                } else if (color.equals("blue")) {
                    edge = new WasTriggeredBy((Process) childVertex, (Process) parentVertex);
                } else if (color.equals("purple")) {
                    edge = new WasControlledBy((Process) childVertex, (Agent) parentVertex);
                } else if (color.equals("orange")) {
                    edge = new WasDerivedFrom((Artifact) childVertex, (Artifact) parentVertex);
                } else {
                    edge = new Edge(childVertex, parentVertex);
                }
                if ((label != null) && (label.length() > 2)) {
                    label = label.substring(1, label.length() - 1);
                    String[] pairs = label.split("\\\\n");
                    for (String pair : pairs) {
                        String key_value[] = pair.split(":", 2);
                        if (key_value.length == 2) {
                            edge.addAnnotation(key_value[0], key_value[1]);
                        }
                    }
                }
                graph.putEdge(edge);
            }
        } catch (Exception exception) {
            logger.log(Level.SEVERE, "Error while processing line: " + line, exception);
        }
    }

    public String exportGraph()
    {
        if (vertexSet.isEmpty())
        {
            return null;
        }
        StringBuilder outputString = new StringBuilder(500);
        try
        {
            outputString.append("digraph spade2dot {\n" + "graph [rankdir = \"RL\"];\n" + "node [fontname=\"Helvetica\" fontsize=\"8\" style=\"filled\" margin=\"0.0,0.0\"];\n"
                    + "edge [fontname=\"Helvetica\" fontsize=\"8\"];\n");

            for (AbstractVertex vertex : vertexSet)
            {
                outputString.append(exportVertex(vertex));
            }
            for (AbstractEdge edge : edgeSet)
            {
                outputString.append(exportEdge(edge));
            }

            outputString.append("}\n");
        }
        catch (Exception exception)
        {
            logger.log(Level.SEVERE, null, exception);
        }

        return outputString.toString();
    }

    /**
     * This method is used to export the graph to a DOT file which is useful for
     * visualization.
     *
     * @param path The path to export the file to.
     */
    public void exportGraph(String path)
    {
        if ((path == null) || vertexSet.isEmpty())
        {
            return;
        }
        try
        {
            String outputString = this.exportGraph();
            if(outputString != null)
            {
                FileWriter writer = new FileWriter(path, false);
                writer.write(outputString);
                writer.flush();
                writer.close();
            }
        }
        catch (Exception exception)
        {
            logger.log(Level.SEVERE, null, exception);
        }
    }

    private String exportVertex(AbstractVertex vertex)
    {
        try
        {
            StringBuilder annotationString = new StringBuilder();
            for (Map.Entry<String, String> currentEntry : vertex.getAnnotations().entrySet())
            {
                String key = currentEntry.getKey();
                String value = currentEntry.getValue();
                annotationString.append(key.replace("\\", "\\\\")).append(":").append(value.replace("\\", "\\\\")).append("\\n");
            }
            String vertexString = annotationString.substring(0, annotationString.length() - 2);
            String shape = "box";
            String color = "white";
            String type = vertex.getAnnotation("type");
            if (type.equalsIgnoreCase("Agent"))
            {
                shape = "octagon";
                color = "rosybrown1";
            }
            else if (type.equalsIgnoreCase("Process") || type.equalsIgnoreCase("Activity"))
            {
                shape = "box";
                color = "lightsteelblue1";
            }
            else if (type.equalsIgnoreCase("Artifact") || type.equalsIgnoreCase("Entity"))
            {
                shape = "ellipse";
                color = "khaki1";
                String subtype = vertex.getAnnotation("subtype");
                if ("network".equalsIgnoreCase(subtype))
                {
                    shape = "diamond";
                    color = "palegreen1";
                }
            }

            String key = vertex.bigHashCode();
            String outputString = "\"" + key + "\" [label=\"" + vertexString.replace("\"", "'") + "\" shape=\"" + shape + "\" fillcolor=\"" + color + "\"];\n";
            return outputString;
        }
        catch (Exception exception)
        {
            logger.log(Level.SEVERE, null, exception);
            return null;
        }
    }

    private String exportEdge(AbstractEdge edge)
    {
        try
        {
            StringBuilder annotationString = new StringBuilder();
            for (Map.Entry<String, String> currentEntry : edge.getAnnotations().entrySet())
            {
                String key = currentEntry.getKey();
                String value = currentEntry.getValue();
                annotationString.append(key.replace("\\", "\\\\")).append(":").append(value.replace("\\", "\\\\")).append("\\n");
            }
            String color = "black";
            String type = edge.getAnnotation("type");
            if (type.equalsIgnoreCase("Used"))
            {
                color = "green";
            }
            else if (type.equalsIgnoreCase("WasGeneratedBy"))
            {
                color = "red";
            }
            else if (type.equalsIgnoreCase("WasTriggeredBy"))
            {
                color = "blue";
            }
            else if (type.equalsIgnoreCase("WasControlledBy"))
            {
                color = "purple";
            }
            else if (type.equalsIgnoreCase("WasDerivedFrom"))
            {
                color = "orange";
            }
            String style = "solid";
            if (edge.getAnnotation("success") != null && edge.getAnnotation("success").equals("false"))
            {
                style = "dashed";
            }

            String edgeString = "(" + annotationString.substring(0, annotationString.length() - 2) + ")";
            String childKey = edge.getChildVertex().bigHashCode();
            String parentKey = edge.getParentVertex().bigHashCode();
            String outputString = "\"" + childKey + "\" -> \"" + parentKey + "\" [label=\"" + edgeString.replace("\"", "'") + "\" color=\"" + color + "\" style=\"" + style + "\"];\n";
            return outputString;
        }
        catch (Exception exception)
        {
            logger.log(Level.SEVERE, null, exception);
            return null;
        }
    }

    @Override
    public boolean initialize(String arguments) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean shutdown() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public List<Integer> listVertices(String expression) {
        try {
            List<Integer> results = new ArrayList<>();
            IndexReader reader = DirectoryReader.open(vertexIndex);
            IndexSearcher searcher = new IndexSearcher(reader);
            ScoreDoc[] hits = searcher.search(queryParser.parse(expression), MAX_QUERY_HITS).scoreDocs;

            for (int i = 0; i < hits.length; ++i) {
                int docId = hits[i].doc;
                Document foundDoc = searcher.doc(docId);
                results.add(Integer.parseInt(foundDoc.get(PRIMARY_KEY)));
            }

            reader.close();
            return results;
        } catch (IOException | ParseException | NumberFormatException exception) {
            logger.log(Level.WARNING, "Error while listing vertices. Returning empty array.", exception);
            return new ArrayList<>();
        }
    }

    /**
     * This function queries the underlying storage and retrieves the edge
     * matching the given criteria.
     *
     * @param childVertexHash      hash of the source vertex.
     * @param parentVertexHash hash of the destination vertex.
     * @return returns edge object matching the given vertices OR NULL.
     */
    @Override
    public AbstractEdge getEdge(String childVertexHash, String parentVertexHash)
    {
        String jointHash = childVertexHash + parentVertexHash;
        return (edgeIdentifiers.containsKey(jointHash)) ? edgeIdentifiers.get(jointHash) : null;
    }

    public AbstractEdge getEdge(String edgeHash)
    {
        return (edgeIdentifiers.containsKey(edgeHash)) ? edgeIdentifiers.get(edgeHash) : null;
    }

    /**
     * This function queries the underlying storage and retrieves the vertex
     * matching the given criteria.
     *
     * @param vertexHash hash of the vertex to find.
     * @return returns vertex object matching the given hash OR NULL.
     */
    @Override
    public AbstractVertex getVertex(String vertexHash)
    {
        return (vertexIdentifiers.containsKey(vertexHash)) ? vertexIdentifiers.get(vertexHash) : null;
    }


    /**
     * This function finds the children of a given vertex.
     * A child is defined as a vertex which is the source of a
     * direct edge between itself and the given vertex.
     *
     * @param parentVertexHash hash of the given vertex
     * @return returns graph object containing children of the given vertex OR NULL.
     */
    @Override
    public Graph getChildren(String parentVertexHash)
    {
        Graph result = new Graph();
        for(Map.Entry<String, AbstractEdge> entry: edgeIdentifiers.entrySet())
        {
            AbstractEdge edge = entry.getValue();
            AbstractVertex parentVertex = edge.getParentVertex();
            if(parentVertex.bigHashCode().equals(parentVertexHash))
            {
                result.putVertex(parentVertex);
                result.putVertex(edge.getChildVertex());
                result.putEdge(edge);
            }
        }

        return result;
    }

    /**
     * This function finds the parents of a given vertex.
     * A parent is defined as a vertex which is the destination of a
     * direct edge between itself and the given vertex.
     *
     * @param childVertexHash hash of the given vertex
     * @return returns graph object containing parents of the given vertex OR NULL.
     */
    @Override
    public Graph getParents(String childVertexHash)
    {
        Graph result = new Graph();
        for(Map.Entry<String, AbstractEdge> entry: edgeIdentifiers.entrySet())
        {
            AbstractEdge edge = entry.getValue();
            AbstractVertex childVertex = edge.getChildVertex();
            if(childVertex.bigHashCode().equals(childVertexHash))
            {
                result.putVertex(childVertex);
                result.putVertex(edge.getParentVertex());
                result.putEdge(edge);
            }
        }

        return result;
    }

    @Override
    public Graph getLineage(String hash, String direction, int maxDepth)
    {
        Graph result = new Graph();
        int current_depth = 0;
        Set<String> remainingVertices = new HashSet<>();
        AbstractVertex startingVertex = getVertex(hash);
        remainingVertices.add(startingVertex.bigHashCode());
        startingVertex.setDepth(0);
        result.setRootVertex(startingVertex);
        result.setMaxDepth(maxDepth);
        Set<String> visitedVertices = new HashSet<>();
        while(!remainingVertices.isEmpty() && current_depth < maxDepth)
        {
            visitedVertices.addAll(remainingVertices);
            Set<String> currentSet = new HashSet<>();
            for(String vertexHash: remainingVertices)
            {
                Graph neighbors = null;
                if(DIRECTION_ANCESTORS.startsWith(direction.toLowerCase()))
                {
                    neighbors = getParents(vertexHash);
                }
                else if(DIRECTION_DESCENDANTS.startsWith(direction.toLowerCase()))
                {
                    neighbors = getChildren(vertexHash);
                }
                if(neighbors != null)
                {
                	for(AbstractVertex V: neighbors.vertexSet())
                		V.setDepth(current_depth+1);
                    result.vertexSet().addAll(neighbors.vertexSet());
                    result.edgeSet().addAll(neighbors.edgeSet());
                    for(AbstractVertex vertex: neighbors.vertexSet())
                    {
                        String hashCode = vertex.bigHashCode();
                        if(!visitedVertices.contains(hashCode))
                        {
                            currentSet.add(hashCode);
                        }
                    }
                }
            }
            remainingVertices.clear();
            remainingVertices.addAll(currentSet);
            current_depth++;
        }
        result.setComputeTime(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z").format(new Date()));

        return result;
    }

    /*
    * Dummy methods created to make things compile.
    * TODO: remove them and fix issues at usage points
    * */
    public Graph getPaths(int child, int dst) {return null; }

    public Graph getPaths(String child, String dst) {return null; }

    public Graph getPaths(Graph child, Graph dst) {return null; }

    public Graph getLineage(int child, String dst) {return null; }

    public Graph getLineage(String child, String dst) {return null; }

    public Graph getLineage(Graph child, String dst) {return null; }

    public Graph getLineage(String vertexExpression, Integer depth, String direction, String terminatingExpression) {return null; }

    public AbstractVertex getVertex(int id) {return null; }

    public String getHostName()
    {
        return hostName;
    }

    public void setHostName(String hostName)
    {
        this.hostName = hostName;
    }

    public String getComputeTime()
    {
        return computeTime;
    }

    public void setComputeTime(String computeTime)
    {
        this.computeTime = computeTime;
    }

    public int getMaxDepth()
    {
        return maxDepth;
    }

    public void setMaxDepth(int maxDepth)
    {
        this.maxDepth = maxDepth;
    }

    public AbstractVertex getRootVertex()
    {
        return rootVertex;
    }

    public void setRootVertex(AbstractVertex rootVertex)
    {
        this.rootVertex = rootVertex;
    }

    public String getSignature()
    {
        return signature;
    }

    public void setSignature(String signature)
    {
        this.signature = signature;
    }

    @Override
    public String toString()
    {
        return "Graph{" +
                "vertexSet=" + vertexSet +
                ", edgeSet=" + edgeSet +
                '}';
    }
}
