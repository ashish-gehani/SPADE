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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.KeywordAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;

import spade.edge.opm.Used;
import spade.edge.opm.WasControlledBy;
import spade.edge.opm.WasDerivedFrom;
import spade.edge.opm.WasGeneratedBy;
import spade.edge.opm.WasTriggeredBy;
import spade.vertex.opm.Agent;
import spade.vertex.opm.Artifact;
import spade.vertex.opm.Process;

import spade.vertex.custom.EOS;

/**
 * This class is used to represent query responses using sets for edges and
 * vertices.
 * 
 * @author Dawood Tariq
 */
public class Graph extends AbstractStorage implements Serializable {

	private static final Logger logger = Logger.getLogger(Graph.class.getName());
	private static final int MAX_QUERY_HITS = 1000;
	private static final String SRC_VERTEX_ID = "SRC_VERTEX_ID";
	private static final String DST_VERTEX_ID = "DST_VERTEX_ID";
	private static final String ID_STRING = Settings.getProperty("storage_identifier");
	private static final String DIRECTION_ANCESTORS = Settings.getProperty("direction_ancestors");
	private static final String DIRECTION_DESCENDANTS = Settings.getProperty("direction_descendants");
	private static final String DIRECTION_BOTH = Settings.getProperty("direction_both");

	private static final Pattern nodePattern = Pattern.compile("\"(.*)\" \\[label=\"(.*)\" shape=\"(\\w*)\" fillcolor=\"(\\w*)\"", Pattern.DOTALL);
	private static final Pattern edgePattern = Pattern.compile("\"(.*)\" -> \"(.*)\" \\[label=\"(.*)\" color=\"(\\w*)\"", Pattern.DOTALL);
	private static final Pattern longPattern = Pattern.compile("^[-+]?[0-9]+$");
	private static final Pattern doublePattern = Pattern.compile("^[-+]?[0-9]*\\.?[0-9]+([eE][-+]?[0-9]+)?$");

	private Analyzer analyzer = new KeywordAnalyzer();
	private QueryParser queryParser = new QueryParser(Version.LUCENE_35, null, analyzer);
	private Set<AbstractVertex> vertexSet = new LinkedHashSet<AbstractVertex>();
	private Map<Integer, AbstractVertex> vertexIdentifiers = new HashMap<Integer, AbstractVertex>();
	private Map<AbstractVertex, Integer> reverseVertexIdentifiers = new HashMap<AbstractVertex, Integer>();
	private Set<AbstractEdge> edgeSet = new LinkedHashSet<AbstractEdge>();
	private Map<Integer, AbstractEdge> edgeIdentifiers = new HashMap<Integer, AbstractEdge>();
	private Map<AbstractEdge, Integer> reverseEdgeIdentifiers = new HashMap<AbstractEdge, Integer>();
	private Map<AbstractVertex, Integer> networkMap = new HashMap<AbstractVertex, Integer>();
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
			vertexIndexWriter = new IndexWriter(vertexIndex, new IndexWriterConfig(Version.LUCENE_35, analyzer));
			edgeIndexWriter = new IndexWriter(edgeIndex, new IndexWriterConfig(Version.LUCENE_35, analyzer));
			queryParser.setAllowLeadingWildcard(true);
		} catch (Exception exception) {
			logger.log(Level.SEVERE, null, exception);
		}
	}

	public AbstractVertex getVertex(int id) {
		return vertexIdentifiers.get(id);
	}

	public AbstractEdge getEdge(int id) {
		return edgeIdentifiers.get(id);
	}

	public int getId(AbstractVertex vertex) {
		return (reverseVertexIdentifiers.containsKey(vertex)) ? reverseVertexIdentifiers.get(vertex) : -1;
	}

	public int getId(AbstractEdge edge) {
		return (reverseEdgeIdentifiers.containsKey(edge)) ? reverseEdgeIdentifiers.get(edge) : -1;
	}

	/**
	 * This method is used to put the network vertices in the network vertex
	 * map. The network vertex map is used when doing remote querying.
	 * 
	 * @param inputVertex
	 *            The network vertex
	 * @param depth
	 *            The depth of this vertex from the source vertex
	 */
	public void putNetworkVertex(AbstractVertex inputVertex, int depth) {
		networkMap.put(inputVertex, depth);
	}

	/**
	 * Add a vertex to the graph object. The vertex is sent to the transformers
	 * before it is finally committed.
	 * 
	 * @param inputVertex
	 *            The vertex to be added
	 */
	public boolean putVertex(AbstractVertex inputVertex) {
		if (reverseVertexIdentifiers.containsKey(inputVertex)) {
			return false;
		}
		// Add vertex to Lucene index
		try {
			Document doc = new Document();
			for (Map.Entry<String, String> currentEntry : inputVertex.getAnnotations().entrySet()) {
				String key = currentEntry.getKey();
				String value = currentEntry.getValue();
				if (key.equals(ID_STRING))
					continue;
				doc.add(new Field(key, value, Field.Store.YES, Field.Index.ANALYZED));
			}
			doc.add(new Field(ID_STRING, Integer.toString(serial_number), Field.Store.YES, Field.Index.ANALYZED));
			vertexIndexWriter.addDocument(doc);
			// vertexIndexWriter.commit();

			vertexIdentifiers.put(serial_number, inputVertex);
			reverseVertexIdentifiers.put(inputVertex, serial_number);
			vertexSet.add(inputVertex);
			serial_number++;
		} catch (Exception exception) {
			logger.log(Level.SEVERE, null, exception);
		}
		return true;
	}

	/**
	 * Add an edge to the graph object. The edge is sent to the transformers
	 * before it is finally committed.
	 * 
	 * @param inputEdge
	 *            The edge to be added
	 */
	public boolean putEdge(AbstractEdge inputEdge) {
		if (reverseEdgeIdentifiers.containsKey(inputEdge)) {
			return false;
		}
		// Add edge to Lucene index
		try {
			Document doc = new Document();
			for (Map.Entry<String, String> currentEntry : inputEdge.getAnnotations().entrySet()) {
				String key = currentEntry.getKey();
				String value = currentEntry.getValue();
				if (key.equals(ID_STRING))
					continue;
				doc.add(new Field(key, value, Field.Store.YES, Field.Index.ANALYZED));
			}
			doc.add(new Field(ID_STRING, Integer.toString(serial_number), Field.Store.YES, Field.Index.ANALYZED));
			doc.add(new Field(SRC_VERTEX_ID, Integer.toString(reverseVertexIdentifiers.get(inputEdge.getSourceVertex())), Field.Store.YES, Field.Index.ANALYZED));
			doc.add(new Field(DST_VERTEX_ID, Integer.toString(reverseVertexIdentifiers.get(inputEdge.getDestinationVertex())), Field.Store.YES, Field.Index.ANALYZED));
			edgeIndexWriter.addDocument(doc);
			// edgeIndexWriter.commit();

			edgeIdentifiers.put(serial_number, inputEdge);
			reverseEdgeIdentifiers.put(inputEdge, serial_number);
			edgeSet.add(inputEdge);
			serial_number++;
		} catch (Exception exception) {
			// logger.log(Level.SEVERE, null, exception);
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
	 *         to the source vertex.
	 */
	public Map<AbstractVertex, Integer> networkMap() {
		return networkMap;
	}

	/**
	 * This method is used to create a new graph as an intersection of the two
	 * given input graphs. This is done simply by using set functions on the
	 * vertex and edge sets.
	 * 
	 * @param graph1
	 *            Input graph 1
	 * @param graph2
	 *            Input graph 2
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
	 * @param graph1
	 *            Input graph 1
	 * @param graph2
	 *            Input graph 2
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
			resultGraph.putVertex(vertex);
		}
		for (AbstractEdge edge : edges) {
			resultGraph.putEdge(edge);
		}

		resultGraph.commitIndex();
		return resultGraph;
	}

	/**
	 * This method is used to create a new graph obtained by removing all
	 * elements of the second graph from the first graph given as inputs.
	 * 
	 * @param graph1
	 *            Input graph 1
	 * @param graph2
	 *            Input graph 2
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
			resultGraph.putVertex(vertex);
		}
		for (AbstractEdge edge : edges) {
			resultGraph.putEdge(edge);
		}

		resultGraph.commitIndex();
		return resultGraph;
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
		Map<String, AbstractVertex> vertexMap = new HashMap<String, AbstractVertex>();
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
				String srckey = edgeMatcher.group(1);
				String dstkey = edgeMatcher.group(2);
				String label = edgeMatcher.group(3);
				String color = edgeMatcher.group(4);
				AbstractEdge edge;
				AbstractVertex srcVertex = vertexMap.get(srckey);
				AbstractVertex dstVertex = vertexMap.get(dstkey);
				if (color.equals("green")) {
					edge = new Used((Process) srcVertex, (Artifact) dstVertex);
				} else if (color.equals("red")) {
					edge = new WasGeneratedBy((Artifact) srcVertex, (Process) dstVertex);
				} else if (color.equals("blue")) {
					edge = new WasTriggeredBy((Process) srcVertex, (Process) dstVertex);
				} else if (color.equals("purple")) {
					edge = new WasControlledBy((Process) srcVertex, (Agent) dstVertex);
				} else if (color.equals("orange")) {
					edge = new WasDerivedFrom((Artifact) srcVertex, (Artifact) dstVertex);
				} else {
					edge = new Edge(srcVertex, dstVertex);
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

	/**
	 * This method is used to export the graph to a DOT file which is useful for
	 * visualization.
	 * 
	 * @param path
	 *            The path to export the file to.
	 */
	public void exportGraph(String path) {
		if ((path == null) || vertexSet.isEmpty())
			return;
		try {
			FileWriter writer = new FileWriter(path, false);
			writer.write("digraph spade2dot {\n" + "graph [rankdir = \"RL\"];\n" + "node [fontname=\"Helvetica\" fontsize=\"8\" style=\"filled\" margin=\"0.0,0.0\"];\n"
					+ "edge [fontname=\"Helvetica\" fontsize=\"8\"];\n");
			writer.flush();

			for (AbstractVertex vertex : vertexSet) {
				exportVertex(vertex, writer);
			}
			for (AbstractEdge edge : edgeSet) {
				exportEdge(edge, writer);
			}

			writer.write("}\n");
			writer.flush();
			writer.close();
		} catch (Exception exception) {
			logger.log(Level.SEVERE, null, exception);
		}
	}

	private void exportVertex(AbstractVertex vertex, FileWriter writer) {
		try {
			StringBuilder annotationString = new StringBuilder();
			for (Map.Entry<String, String> currentEntry : vertex.getAnnotations().entrySet()) {
				String key = currentEntry.getKey();
				String value = currentEntry.getValue();
				if (key.equals(ID_STRING)) {
					continue;
				}
				annotationString.append(key.replace("\\", "\\\\") + ":" + value.replace("\\", "\\\\") + "\\n");
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
		} catch (Exception exception) {
			logger.log(Level.SEVERE, null, exception);
		}
	}

	private void exportEdge(AbstractEdge edge, FileWriter writer) {
		try {
			StringBuilder annotationString = new StringBuilder();
			for (Map.Entry<String, String> currentEntry : edge.getAnnotations().entrySet()) {
				String key = currentEntry.getKey();
				String value = currentEntry.getValue();
				if (key.equals(ID_STRING)) {
					continue;
				}
				annotationString.append(key.replace("\\", "\\\\") + ":" + value.replace("\\", "\\\\") + "\\n");
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

	public List<Integer> listVertices(String expression) {
		try {
			List<Integer> results = new ArrayList<Integer>();
			IndexReader reader = IndexReader.open(vertexIndex);
			IndexSearcher searcher = new IndexSearcher(reader);
			ScoreDoc[] hits = searcher.search(queryParser.parse(expression), MAX_QUERY_HITS).scoreDocs;

			for (int i = 0; i < hits.length; ++i) {
				int docId = hits[i].doc;
				Document foundDoc = searcher.doc(docId);
				results.add(Integer.parseInt(foundDoc.get(ID_STRING)));
			}

			searcher.close();
			reader.close();
			return results;
		} catch (Exception exception) {
			return new ArrayList<Integer>();
		}
	}

	@Override
	public Graph getVertices(String expression) {
		try {
			IndexReader reader = IndexReader.open(vertexIndex);
			IndexSearcher searcher = new IndexSearcher(reader);
			ScoreDoc[] hits = searcher.search(queryParser.parse(expression), MAX_QUERY_HITS).scoreDocs;

			Graph resultGraph = new Graph();
			for (int i = 0; i < hits.length; ++i) {
				int docId = hits[i].doc;
				Document foundDoc = searcher.doc(docId);
				int vertex_identifier = Integer.parseInt(foundDoc.get(ID_STRING));
				resultGraph.putVertex(vertexIdentifiers.get(vertex_identifier));
			}

			searcher.close();
			reader.close();
			resultGraph.commitIndex();
			return resultGraph;
		} catch (Exception exception) {
			logger.log(Level.SEVERE, null, exception);
			return null;
		}
	}

	@Override
	public Graph getPaths(String srcVertexExpression, String dstVertexExpression, int maxLength) {
		Graph a = getLineage(srcVertexExpression, maxLength, DIRECTION_ANCESTORS, null);
		Graph d = getLineage(dstVertexExpression, maxLength, DIRECTION_DESCENDANTS, null);
		return Graph.intersection(a, d);
		// try {
		// Set<AbstractVertex> srcVertexSet = new HashSet<AbstractVertex>();
		// Set<AbstractVertex> dstVertexSet = new HashSet<AbstractVertex>();
		// Set<AbstractEdge> srcEdgeSet = new HashSet<AbstractEdge>();
		// Set<AbstractEdge> dstEdgeSet = new HashSet<AbstractEdge>();
		//
		// Set<Integer> tempSrcSet = new HashSet<Integer>();
		// Set<Integer> tempDstSet = new HashSet<Integer>();
		// IndexReader vertexReader = IndexReader.open(vertexIndex);
		// IndexSearcher vertexSearcher = new IndexSearcher(vertexReader);
		// ScoreDoc[] srcHits =
		// vertexSearcher.search(queryParser.parse(srcVertexExpression),
		// MAX_QUERY_HITS).scoreDocs;
		// for (int i = 0; i < srcHits.length; ++i) {
		// int docId = srcHits[i].doc;
		// Document foundDoc = vertexSearcher.doc(docId);
		// int vertex_identifier = Integer.parseInt(foundDoc.get(ID_STRING));
		// tempSrcSet.add(vertex_identifier);
		// }
		// ScoreDoc[] dstHits =
		// vertexSearcher.search(queryParser.parse(dstVertexExpression),
		// MAX_QUERY_HITS).scoreDocs;
		// for (int i = 0; i < dstHits.length; ++i) {
		// int docId = dstHits[i].doc;
		// Document foundDoc = vertexSearcher.doc(docId);
		// int vertex_identifier = Integer.parseInt(foundDoc.get(ID_STRING));
		// tempDstSet.add(vertex_identifier);
		// }
		// vertexSearcher.close();
		// vertexReader.close();
		//
		// IndexReader edgeReader = IndexReader.open(edgeIndex);
		// IndexSearcher edgeSearcher = new IndexSearcher(edgeReader);
		//
		// Set<Integer> processedVertices = new HashSet<Integer>();
		// processedVertices.addAll(tempSrcSet);
		// for (int i = 0; i <= maxLength; i++) {
		// Set<Integer> newTempSet = new HashSet<Integer>();
		// for (Integer currentVertexId : tempSrcSet) {
		// srcVertexSet.add(vertexIdentifiers.get(currentVertexId));
		// ScoreDoc[] hits = edgeSearcher.search(queryParser.parse(SRC_VERTEX_ID
		// + ":\"" + currentVertexId + "\""), MAX_QUERY_HITS).scoreDocs;
		// for (int j = 0; j < hits.length; ++j) {
		// int docId = hits[j].doc;
		// Document foundDoc = edgeSearcher.doc(docId);
		// int edge_identifier = Integer.parseInt(foundDoc.get(ID_STRING));
		// AbstractEdge edge = edgeIdentifiers.get(edge_identifier);
		// int vertexId =
		// reverseVertexIdentifiers.get(edge.getDestinationVertex());
		// if (processedVertices.add(vertexId)) {
		// srcEdgeSet.add(edge);
		// newTempSet.add(vertexId);
		// }
		// }
		// }
		// if (newTempSet.isEmpty()) {
		// break;
		// }
		// tempSrcSet = newTempSet;
		// }
		//
		// processedVertices = new HashSet<Integer>();
		// processedVertices.addAll(tempDstSet);
		// for (int i = 0; i <= maxLength; i++) {
		// Set<Integer> newTempSet = new HashSet<Integer>();
		// for (Integer currentVertexId : tempDstSet) {
		// dstVertexSet.add(vertexIdentifiers.get(currentVertexId));
		// ScoreDoc[] hits = edgeSearcher.search(queryParser.parse(DST_VERTEX_ID
		// + ":\"" + currentVertexId + "\""), MAX_QUERY_HITS).scoreDocs;
		// for (int j = 0; j < hits.length; ++j) {
		// int docId = hits[j].doc;
		// Document foundDoc = edgeSearcher.doc(docId);
		// int edge_identifier = Integer.parseInt(foundDoc.get(ID_STRING));
		// AbstractEdge edge = edgeIdentifiers.get(edge_identifier);
		// int vertexId = reverseVertexIdentifiers.get(edge.getSourceVertex());
		// if (processedVertices.add(vertexId)) {
		// dstEdgeSet.add(edge);
		// newTempSet.add(vertexId);
		// }
		// }
		// }
		// if (newTempSet.isEmpty()) {
		// break;
		// }
		// tempDstSet = newTempSet;
		// }
		// edgeSearcher.close();
		// edgeReader.close();
		//
		// Graph resultGraph = new Graph();
		// Set<AbstractVertex> vertexResultSet = srcVertexSet;
		// vertexResultSet.retainAll(dstVertexSet);
		// Set<AbstractEdge> edgeResultSet = srcEdgeSet;
		// edgeResultSet.retainAll(dstEdgeSet);
		// for (AbstractVertex vertex : vertexResultSet) {
		// resultGraph.putVertex(vertex);
		// }
		// for (AbstractEdge edge : edgeResultSet) {
		// resultGraph.putEdge(edge);
		// }
		//
		// resultGraph.commitIndex();
		// return resultGraph;
		// } catch (Exception exception) {
		// logger.log(Level.SEVERE, null, exception);
		// return null;
		// }
	}

	@Override
	public Graph getPaths(int srcVertexId, int dstVertexId, int maxLength) {
		return getPaths(ID_STRING + ":" + srcVertexId, ID_STRING + ":" + dstVertexId, maxLength);
	}

	@Override
	public Graph getLineage(String vertexExpression, int depth, String direction, String terminatingExpression) {
		try {
			if (DIRECTION_BOTH.startsWith(direction.toLowerCase())) {
				Graph ancestor = getLineage(vertexExpression, depth, DIRECTION_ANCESTORS, terminatingExpression);
				Graph descendant = getLineage(vertexExpression, depth, DIRECTION_DESCENDANTS, terminatingExpression);
				Graph result = Graph.union(ancestor, descendant);
				return result;
			} else if (!DIRECTION_ANCESTORS.startsWith(direction.toLowerCase()) && !DIRECTION_DESCENDANTS.startsWith(direction.toLowerCase())) {
				return null;
			}

			Graph resultGraph = new Graph();

			IndexReader vertexReader = IndexReader.open(vertexIndex);
			IndexSearcher vertexSearcher = new IndexSearcher(vertexReader);
			Set<Integer> terminatingSet = new HashSet<Integer>();
			if ((terminatingExpression != null) && (!terminatingExpression.trim().equalsIgnoreCase("null"))) {
				ScoreDoc[] hits = vertexSearcher.search(queryParser.parse(terminatingExpression), MAX_QUERY_HITS).scoreDocs;
				for (int i = 0; i < hits.length; ++i) {
					int docId = hits[i].doc;
					Document foundDoc = vertexSearcher.doc(docId);
					int id = Integer.parseInt(foundDoc.get(ID_STRING));
					terminatingSet.add(id);
				}
			}
			Set<Integer> processedVertices = new HashSet<Integer>();
			ScoreDoc[] hits = vertexSearcher.search(queryParser.parse(vertexExpression), MAX_QUERY_HITS).scoreDocs;
			for (int i = 0; i < hits.length; ++i) {
				int docId = hits[i].doc;
				Document foundDoc = vertexSearcher.doc(docId);
				int vertex_identifier = Integer.parseInt(foundDoc.get(ID_STRING));
				resultGraph.putVertex(vertexIdentifiers.get(vertex_identifier));
				processedVertices.add(vertex_identifier);
			}
			vertexSearcher.close();
			vertexReader.close();

			Set<Integer> doneVertices = new HashSet<Integer>();
			doneVertices.addAll(processedVertices);

			IndexReader edgeReader = IndexReader.open(edgeIndex);
			IndexSearcher edgeSearcher = new IndexSearcher(edgeReader);
			for (int i = 0; i <= depth; i++) {
				Set<Integer> tempProcessedVertices = new HashSet<Integer>();
				for (int currentVertexId : processedVertices) {
					String queryString = null;
					if (DIRECTION_ANCESTORS.startsWith(direction.toLowerCase())) {
						queryString = SRC_VERTEX_ID + ":\"" + currentVertexId + "\"";
					} else if (DIRECTION_DESCENDANTS.startsWith(direction.toLowerCase())) {
						queryString = DST_VERTEX_ID + ":\"" + currentVertexId + "\"";
					}

					hits = edgeSearcher.search(queryParser.parse(queryString), MAX_QUERY_HITS).scoreDocs;
					for (int j = 0; j < hits.length; ++j) {
						int docId = hits[j].doc;
						Document foundDoc = edgeSearcher.doc(docId);
						int edgeId = Integer.parseInt(foundDoc.get(ID_STRING));
						AbstractEdge tempEdge = edgeIdentifiers.get(edgeId);
						int otherVertexId = 0;
						if (DIRECTION_ANCESTORS.startsWith(direction.toLowerCase())) {
							otherVertexId = reverseVertexIdentifiers.get(tempEdge.getDestinationVertex());
						} else if (DIRECTION_DESCENDANTS.startsWith(direction.toLowerCase())) {
							otherVertexId = reverseVertexIdentifiers.get(tempEdge.getSourceVertex());
						}
						if (!terminatingSet.contains(otherVertexId)) {
							resultGraph.putVertex(vertexIdentifiers.get(otherVertexId));
							resultGraph.putEdge(tempEdge);
							if (doneVertices.add(otherVertexId)) {
								tempProcessedVertices.add(otherVertexId);
							}
						}
					}
				}
				if (tempProcessedVertices.isEmpty()) {
					break;
				}
				processedVertices = tempProcessedVertices;
			}

			edgeSearcher.close();
			edgeReader.close();
			resultGraph.commitIndex();
			return resultGraph;
		} catch (Exception exception) {
			logger.log(Level.SEVERE, null, exception);
			return null;
		}
	}

	@Override
	public Graph getLineage(int vertexId, int depth, String direction, String terminatingExpression) {
		return getLineage(ID_STRING + ":" + vertexId, depth, direction, terminatingExpression);
	}
}
