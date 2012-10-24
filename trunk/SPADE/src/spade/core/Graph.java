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
package spade.core;

import java.io.Serializable;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;

/**
 * This class is used to represent query responses using sets for edges and
 * vertices.
 *
 * @author Dawood Tariq
 */
public class Graph extends AbstractStorage implements Serializable {

    private static final Logger logger = Logger.getLogger(Graph.class.getName());
    private static final int MAX_QUERY_RESULTS = 1000;
    private static final int MAX_INTERNAL_QUERY_RESULTS = 100;
    private static final String INTERNAL_HASH_KEY = "INTERNAL_HASH_KEY";
    private static final String INTERNAL_SRC_VERTEX_HASH_KEY = "INTERNAL_SRC_VERTEX_HASH_KEY";
    private static final String INTERNAL_DST_VERTEX_HASH_KEY = "INTERNAL_DST_VERTEX_HASH_KEY";
    private Set<AbstractVertex> vertexSet;
    private Map<Integer, AbstractVertex> vertexHashes;
    private Set<AbstractEdge> edgeSet;
    private Map<Integer, AbstractEdge> edgeHashes;
    private Map<AbstractVertex, Integer> networkMap;
    public Map<String, String> graphInfo;
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
     * An empty constructor.
     */
    public Graph() {
        vertexSet = new HashSet<AbstractVertex>();
        vertexHashes = new HashMap<Integer, AbstractVertex>();
        edgeSet = new HashSet<AbstractEdge>();
        edgeHashes = new HashMap<Integer, AbstractEdge>();
        networkMap = new HashMap<AbstractVertex, Integer>();
        graphInfo = new HashMap<String, String>();

        // Lucene initialization
        try {
            StandardAnalyzer analyzer = new StandardAnalyzer(Version.LUCENE_35);
            IndexWriterConfig vertexConfig = new IndexWriterConfig(Version.LUCENE_35, analyzer);
            IndexWriterConfig edgeConfig = new IndexWriterConfig(Version.LUCENE_35, analyzer);
            vertexIndex = new RAMDirectory();
            edgeIndex = new RAMDirectory();
            vertexIndexWriter = new IndexWriter(vertexIndex, vertexConfig);
            edgeIndexWriter = new IndexWriter(edgeIndex, edgeConfig);
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
        }
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
     * Add a vertex to the graph object. The vertex is sent to the transformers
     * before it is finally committed.
     *
     * @param inputVertex The vertex to be added
     */
    public boolean putVertex(AbstractVertex inputVertex) {
        inputVertex.resultGraph = this;
        Kernel.sendToTransformers(inputVertex);
        return true;
    }

    /**
     * Add an edge to the graph object. The edge is sent to the transformers
     * before it is finally committed.
     *
     * @param inputEdge The edge to be added
     */
    public boolean putEdge(AbstractEdge inputEdge) {
        inputEdge.resultGraph = this;
        Kernel.sendToTransformers(inputEdge);
        return true;
    }

    /**
     * Commit a vertex to this graph.
     *
     * @param inputVertex The vertex to be committed
     */
    public void commitVertex(AbstractVertex inputVertex) {
        vertexSet.add(inputVertex);
        vertexHashes.put(inputVertex.hashCode(), inputVertex);

        // Add vertex to Lucene index
        try {
            Document doc = new Document();
            for (Map.Entry<String, String> currentEntry : inputVertex.getAnnotations().entrySet()) {
                String key = currentEntry.getKey();
                String value = currentEntry.getValue();
                doc.add(new Field(key, value, Field.Store.YES, Field.Index.ANALYZED));
            }
            doc.add(new Field(INTERNAL_HASH_KEY, Integer.toString(inputVertex.hashCode()), Field.Store.YES, Field.Index.ANALYZED));
            vertexIndexWriter.addDocument(doc);
            vertexIndexWriter.commit();
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
        }
    }

    /**
     * Commit an edge to this graph.
     *
     * @param inputEdge The edge to be committed
     */
    public void commitEdge(AbstractEdge inputEdge) {
        edgeSet.add(inputEdge);
        edgeHashes.put(inputEdge.hashCode(), inputEdge);

        // Add edge to Lucene index
        try {
            Document doc = new Document();
            for (Map.Entry<String, String> currentEntry : inputEdge.getAnnotations().entrySet()) {
                String key = currentEntry.getKey();
                String value = currentEntry.getValue();
                doc.add(new Field(key, value, Field.Store.YES, Field.Index.ANALYZED));
            }
            doc.add(new Field(INTERNAL_HASH_KEY, Integer.toString(inputEdge.hashCode()), Field.Store.YES, Field.Index.ANALYZED));
            doc.add(new Field(INTERNAL_SRC_VERTEX_HASH_KEY, Integer.toString(inputEdge.getSourceVertex().hashCode()), Field.Store.YES, Field.Index.ANALYZED));
            doc.add(new Field(INTERNAL_DST_VERTEX_HASH_KEY, Integer.toString(inputEdge.getDestinationVertex().hashCode()), Field.Store.YES, Field.Index.ANALYZED));
            edgeIndexWriter.addDocument(doc);
            edgeIndexWriter.commit();
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
        }
    }

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
        Set<AbstractVertex> vertices = new HashSet<AbstractVertex>();
        Set<AbstractEdge> edges = new HashSet<AbstractEdge>();

        vertices.addAll(graph1.vertexSet());
        vertices.retainAll(graph2.vertexSet());
        edges.addAll(graph1.edgeSet());
        edges.retainAll(graph2.edgeSet());

        for (AbstractVertex vertex : vertices) {
            resultGraph.commitVertex(vertex);
        }
        for (AbstractEdge edge : edges) {
            resultGraph.commitEdge(edge);
        }

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
    public static Graph union(Graph graph1, Graph graph2) {
        Graph resultGraph = new Graph();
        Set<AbstractVertex> vertices = new HashSet<AbstractVertex>();
        Set<AbstractEdge> edges = new HashSet<AbstractEdge>();

        vertices.addAll(graph1.vertexSet());
        vertices.addAll(graph2.vertexSet());
        edges.addAll(graph1.edgeSet());
        edges.addAll(graph2.edgeSet());

        for (AbstractVertex vertex : vertices) {
            resultGraph.commitVertex(vertex);
        }
        for (AbstractEdge edge : edges) {
            resultGraph.commitEdge(edge);
        }

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
        Set<AbstractVertex> vertices = new HashSet<AbstractVertex>();
        Set<AbstractEdge> edges = new HashSet<AbstractEdge>();

        vertices.addAll(graph1.vertexSet());
        vertices.removeAll(graph2.vertexSet());
        edges.addAll(graph1.edgeSet());
        edges.removeAll(graph2.edgeSet());

        for (AbstractVertex vertex : vertices) {
            resultGraph.commitVertex(vertex);
        }
        for (AbstractEdge edge : edges) {
            resultGraph.commitEdge(edge);
        }

        return resultGraph;
    }

    /**
     * This method is used to export the graph to a DOT file which is useful for
     * visualization.
     *
     * @param path The path to export the file to.
     */
    public void exportDOT(String path) {
        try {
            spade.storage.Graphviz outputStorage = new spade.storage.Graphviz();
            outputStorage.initialize(path);
            for (AbstractVertex vertex : vertexSet) {
                outputStorage.putVertex(vertex);
            }
            for (AbstractEdge edge : edgeSet) {
                outputStorage.putEdge(edge);
            }
            outputStorage.shutdown();
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
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

    @Override
    public Graph getVertices(String expression) {
        try {
            StandardAnalyzer analyzer = new StandardAnalyzer(Version.LUCENE_35);
            QueryParser queryParser = new QueryParser(Version.LUCENE_35, null, analyzer);
            org.apache.lucene.search.Query query = queryParser.parse(expression);
            IndexReader reader = IndexReader.open(vertexIndex);
            IndexSearcher searcher = new IndexSearcher(reader);
            TopScoreDocCollector collector = TopScoreDocCollector.create(MAX_QUERY_RESULTS, true);
            searcher.search(query, collector);
            ScoreDoc[] hits = collector.topDocs().scoreDocs;

            Graph resultGraph = new Graph();
            for (int i = 0; i < hits.length; ++i) {
                int docId = hits[i].doc;
                Document foundDoc = searcher.doc(docId);
                int hash = Integer.parseInt(foundDoc.get(INTERNAL_HASH_KEY));
                resultGraph.commitVertex(vertexHashes.get(hash));
            }

            searcher.close();
            return resultGraph;
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
            return null;
        }
    }

    @Override
    public Graph getPaths(String srcVertexId, String dstVertexId, int maxLength) {
        StandardAnalyzer analyzer = new StandardAnalyzer(Version.LUCENE_35);
        QueryParser queryParser = new QueryParser(Version.LUCENE_35, null, analyzer);
        // The algorithm for this method is as follows:
        //
        // 1) Create two sets of vertices and edges, each for storing the lineage
        //    of source and destination vertices.
        // 2) Use the lucene index to look up the hash value of the source vertex.
        // 3) Find edges that have the source vertex hash determined in (2).
        // 4) Iteratively repeat (2) and (3) maxLength times.
        // 5) (2), (3), and (4) are used to determine the lineage of the source
        //    vertex.
        // 6) Repeat (5) to determine the lineage of the destination vertex.
        // 7) Check that the source lineage contains the destination vertex and
        //    that the destination lineage contains the source vertex.
        // 8) If (7) is true, find the intersecting vertex and edge set of the two
        //    lineages and add them to the final result graph.
        try {
            Set<AbstractVertex> srcVertexSet = new HashSet<AbstractVertex>();
            Set<AbstractVertex> dstVertexSet = new HashSet<AbstractVertex>();
            Set<AbstractEdge> srcEdgeSet = new HashSet<AbstractEdge>();
            Set<AbstractEdge> dstEdgeSet = new HashSet<AbstractEdge>();

            IndexReader reader = IndexReader.open(edgeIndex);
            IndexSearcher searcher = new IndexSearcher(reader);
            TopScoreDocCollector collector = TopScoreDocCollector.create(MAX_INTERNAL_QUERY_RESULTS, true);

            int srcHash = getHashFromId(srcVertexId);
            Set<Integer> tempSet = new HashSet<Integer>();
            tempSet.add(srcHash);
            for (int i = 0; i <= maxLength; i++) {
                Set<Integer> newTempSet = new HashSet<Integer>();
                for (Integer currentVertexHash : tempSet) {
                    srcVertexSet.add(vertexHashes.get(currentVertexHash));

                    org.apache.lucene.search.Query query = queryParser.parse(INTERNAL_SRC_VERTEX_HASH_KEY + ":" + currentVertexHash);
                    searcher.search(query, collector);
                    ScoreDoc[] hits = collector.topDocs().scoreDocs;

                    for (int j = 0; j < hits.length; ++j) {
                        int docId = hits[j].doc;
                        Document foundDoc = searcher.doc(docId);
                        int hash = Integer.parseInt(foundDoc.get(INTERNAL_HASH_KEY));
                        srcEdgeSet.add(edgeHashes.get(hash));
                        newTempSet.add(edgeHashes.get(hash).getDestinationVertex().hashCode());
                    }
                    searcher.close();
                }
                if (newTempSet.isEmpty()) {
                    break;
                }
                tempSet = newTempSet;
            }

            int dstHash = getHashFromId(srcVertexId);
            tempSet = new HashSet<Integer>();
            tempSet.add(dstHash);
            for (int i = 0; i <= maxLength; i++) {
                Set<Integer> newTempSet = new HashSet<Integer>();
                for (Integer currentVertexHash : tempSet) {
                    dstVertexSet.add(vertexHashes.get(currentVertexHash));

                    org.apache.lucene.search.Query query = queryParser.parse(INTERNAL_DST_VERTEX_HASH_KEY + ":" + currentVertexHash);
                    searcher.search(query, collector);
                    ScoreDoc[] hits = collector.topDocs().scoreDocs;

                    for (int j = 0; j < hits.length; ++j) {
                        int docId = hits[j].doc;
                        Document foundDoc = searcher.doc(docId);
                        int hash = Integer.parseInt(foundDoc.get(INTERNAL_HASH_KEY));
                        dstEdgeSet.add(edgeHashes.get(hash));
                        newTempSet.add(edgeHashes.get(hash).getSourceVertex().hashCode());
                    }
                    searcher.close();
                }
                if (newTempSet.isEmpty()) {
                    break;
                }
                tempSet = newTempSet;
            }

            Graph resultGraph = new Graph();
            if (srcVertexSet.contains(vertexHashes.get(dstHash)) && dstVertexSet.contains(vertexHashes.get(srcHash))) {
                Set<AbstractVertex> vertexResultSet = srcVertexSet;
                vertexResultSet.retainAll(dstVertexSet);
                Set<AbstractEdge> edgeResultSet = srcEdgeSet;
                edgeResultSet.retainAll(dstEdgeSet);
                for (AbstractVertex vertex : vertexResultSet) {
                    resultGraph.commitVertex(vertex);
                }
                for (AbstractEdge edge : edgeResultSet) {
                    resultGraph.commitEdge(edge);
                }
            }
            return resultGraph;
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
            return null;
        }
    }

    @Override
    public Graph getLineage(String vertexId, int depth, String direction, String terminatingExpression) {
        try {
            StandardAnalyzer analyzer = new StandardAnalyzer(Version.LUCENE_35);
            QueryParser queryParser = new QueryParser(Version.LUCENE_35, null, analyzer);

            if ((terminatingExpression != null) && (terminatingExpression.trim().equalsIgnoreCase("null"))) {
                terminatingExpression = null;
            }

            Set<Integer> terminatingSet;
            if (terminatingExpression != null) {
                terminatingSet = new HashSet<Integer>();
                org.apache.lucene.search.Query query = queryParser.parse(terminatingExpression);
                IndexReader reader = IndexReader.open(vertexIndex);
                IndexSearcher searcher = new IndexSearcher(reader);
                TopScoreDocCollector collector = TopScoreDocCollector.create(MAX_INTERNAL_QUERY_RESULTS, true);
                searcher.search(query, collector);
                ScoreDoc[] hits = collector.topDocs().scoreDocs;
                for (int i = 0; i < hits.length; ++i) {
                    int docId = hits[i].doc;
                    Document foundDoc = searcher.doc(docId);
                    int hash = Integer.parseInt(foundDoc.get(INTERNAL_HASH_KEY));
                    terminatingSet.add(hash);
                }
                searcher.close();
            }

            IndexReader reader = IndexReader.open(edgeIndex);
            IndexSearcher searcher = new IndexSearcher(reader);
            TopScoreDocCollector collector = TopScoreDocCollector.create(MAX_INTERNAL_QUERY_RESULTS, true);

            Graph resultGraph = new Graph();
            int vertexHash = getHashFromId(vertexId);
            Set<Integer> tempSet = new HashSet<Integer>();
            Set<Integer> tempEdgeSet = new HashSet<Integer>();
            Set<Integer> doneSet = new HashSet<Integer>();
            tempSet.add(vertexHash);
            for (int i = 0; i <= depth; i++) {
                Set<Integer> newTempSet = new HashSet<Integer>();
                Set<Integer> newTempEdgeSet = new HashSet<Integer>();
                for (Integer currentVertexHash : tempSet) {
                    resultGraph.commitVertex(vertexHashes.get(currentVertexHash));
                    doneSet.add(currentVertexHash);

                    String queryString;
                    if (Query.DIRECTION_ANCESTORS.startsWith(direction.toLowerCase())) {
                        queryString = INTERNAL_SRC_VERTEX_HASH_KEY + ":" + currentVertexHash;
                    } else if (Query.DIRECTION_DESCENDANTS.startsWith(direction.toLowerCase())) {
                        queryString = INTERNAL_DST_VERTEX_HASH_KEY + ":" + currentVertexHash;
                    } else if (Query.DIRECTION_BOTH.startsWith(direction.toLowerCase())) {
                        queryString = INTERNAL_SRC_VERTEX_HASH_KEY + ":" + currentVertexHash
                                + " OR " + INTERNAL_DST_VERTEX_HASH_KEY + ":" + currentVertexHash;
                    } else {
                        return null;
                    }
                    org.apache.lucene.search.Query query = queryParser.parse(queryString);
                    searcher.search(query, collector);
                    ScoreDoc[] hits = collector.topDocs().scoreDocs;

                    for (int j = 0; j < hits.length; ++j) {
                        int docId = hits[j].doc;
                        Document foundDoc = searcher.doc(docId);
                        int hash = Integer.parseInt(foundDoc.get(INTERNAL_HASH_KEY));
                        newTempEdgeSet.add(hash);
                        AbstractEdge tempEdge = edgeHashes.get(hash);
                        if (Query.DIRECTION_ANCESTORS.startsWith(direction.toLowerCase())) {
                            int newHash = tempEdge.getDestinationVertex().hashCode();
                            if (!doneSet.contains(newHash)) {
                                newTempSet.add(newHash);
                            }
                        } else if (Query.DIRECTION_DESCENDANTS.startsWith(direction.toLowerCase())) {
                            int newHash = tempEdge.getSourceVertex().hashCode();
                            if (!doneSet.contains(newHash)) {
                                newTempSet.add(newHash);
                            }
                        } else if (Query.DIRECTION_BOTH.startsWith(direction.toLowerCase())) {
                            int newHash1 = tempEdge.getSourceVertex().hashCode();
                            if (!doneSet.contains(newHash1)) {
                                newTempSet.add(newHash1);
                            }
                            int newHash2 = tempEdge.getDestinationVertex().hashCode();
                            if (!doneSet.contains(newHash2)) {
                                newTempSet.add(newHash2);
                            }
                        } else {
                            return null;
                        }
                    }
                    searcher.close();
                }
                for (int edgeHash : tempEdgeSet) {
                    resultGraph.commitEdge(edgeHashes.get(edgeHash));
                }
                tempEdgeSet = newTempEdgeSet;

                if (newTempSet.isEmpty()) {
                    break;
                }
                tempSet = newTempSet;
            }

            return resultGraph;
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
            return null;
        }
    }

    private int getHashFromId(String vertexId) throws Exception {
        try {
            StandardAnalyzer analyzer = new StandardAnalyzer(Version.LUCENE_35);
            QueryParser queryParser = new QueryParser(Version.LUCENE_35, null, analyzer);
            org.apache.lucene.search.Query query = queryParser.parse(Query.STORAGE_ID_STRING + ":" + vertexId);
            IndexReader reader = IndexReader.open(vertexIndex);
            IndexSearcher searcher = new IndexSearcher(reader);
            TopScoreDocCollector collector = TopScoreDocCollector.create(1, true);
            searcher.search(query, collector);
            Document foundDoc = searcher.doc(collector.topDocs().scoreDocs[0].doc);
            int hash = Integer.parseInt(foundDoc.get(INTERNAL_HASH_KEY));
            searcher.close();
            return hash;
        } catch (Exception exception) {
            throw exception;
        }
    }
}
