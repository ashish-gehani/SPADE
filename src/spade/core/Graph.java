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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Serializable;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
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

import spade.query.quickgrail.instruction.GetLineage.Direction;
import spade.reporter.audit.OPMConstants;

/**
 * This class is used to represent query responses using sets for edges and
 * vertices.
 *
 * @author Dawood Tariq
 */
public class Graph implements Serializable{

	private static final long serialVersionUID = -219720065503901539L;

	private static final Logger logger = Logger.getLogger(Graph.class.getName());
	//////////////////////////////////////////////////////
	public static final Pattern nodePattern = Pattern
			.compile("\"(.*)\" \\[label=\"(.*)\" shape=\"(\\w*)\" fillcolor=\"(\\w*)\"", Pattern.DOTALL);
	public static final Pattern edgePattern = Pattern
			.compile("\"(.*)\" -> \"(.*)\" \\[label=\"(.*)\" color=\"(\\w*)\"", Pattern.DOTALL);

	//////////////////////////////////////////////////////
	private final Set<AbstractVertex> vertexSet = new LinkedHashSet<>();
	private final Set<AbstractEdge> edgeSet = new LinkedHashSet<>();
	//////////////////////////////////////////////////////

	/**
	 * Fields for discrepancy check and query params
	 */
	private String hostName;
	private byte[] signature;
	private String queryString;
	/*
	 * time at which this graph was constructed completely
	 */
	private String computeTime; // unix timestamp TODO
	/*
	 * period of validity of this graph in hours, if it is stored in the cache.
	 * Would be configurable
	 */
	private int TTL = 1;

	/**
	 * This function inserts the given vertex into the underlying storage(s) and
	 * updates the cache(s) accordingly.
	 *
	 * @param incomingVertex vertex to insert into the storage
	 * @return returns true if the insertion is successful. Insertion is considered
	 *         not successful if the vertex is already present in the storage.
	 */
	public boolean putVertex(AbstractVertex incomingVertex){
		return vertexSet.add(incomingVertex);
	}

	/**
	 * This function inserts the given edge into the underlying storage(s) and
	 * updates the cache(s) accordingly.
	 *
	 * @param incomingEdge edge to insert into the storage
	 * @return returns true if the insertion is successful. Insertion is considered
	 *         not successful if the edge is already present in the storage.
	 */
	public boolean putEdge(AbstractEdge incomingEdge){
		return edgeSet.add(incomingEdge);
	}

	/**
	 * Checks if the two given graphs are equal.
	 * 
	 * @return True if both graphs have the same vertices and edges
	 */
	public boolean equals(Graph otherGraph){
		if(otherGraph == null){
			return false;
		}

		if(this.vertexSet().size() != otherGraph.vertexSet().size()){
			return false;
		}
		if(this.edgeSet().size() != otherGraph.edgeSet().size()){
			return false;
		}
		if(!this.vertexSet().equals(otherGraph.vertexSet())){
			return false;
		}
		if(!this.edgeSet().equals(otherGraph.edgeSet())){
			return false;
		}

		return true;
	}

	/**
	 *
	 * Returns the status of graph as empty or non-empty
	 *
	 * @return True if the graph contains no vertex
	 */
	private boolean isEmpty(){
		return (vertexSet().size() == 0);
	}

	/**
	 * Returns the set containing the vertices.
	 *
	 * @return The set containing the vertices.
	 */
	public Set<AbstractVertex> vertexSet(){
		return vertexSet;
	}

	/**
	 * Returns the set containing the edges.
	 *
	 * @return The set containing edges.
	 */
	public Set<AbstractEdge> edgeSet(){
		return edgeSet;
	}

	/**
	 * This method is used to create a new graph as an intersection of the two given
	 * input graphs. This is done simply by using set functions on the vertex and
	 * edge sets.
	 *
	 * @param graph1 Input graph 1
	 * @param graph2 Input graph 2
	 * @return The result graph
	 */
	public static Graph intersection(Graph graph1, Graph graph2){
		Graph resultGraph = new Graph();
		resultGraph.vertexSet().addAll(graph1.vertexSet());
		resultGraph.vertexSet().retainAll(graph2.vertexSet());
		resultGraph.edgeSet().addAll(graph1.edgeSet());
		resultGraph.edgeSet().retainAll(graph2.edgeSet());
		return resultGraph;
	}

	public void union(Graph graph){
		this.vertexSet().addAll(graph.vertexSet());
		this.edgeSet().addAll(graph.edgeSet());
	}

	/**
	 * This method is used to create a new graph as a union of the two given input
	 * graphs. This is done simply by using set functions on the vertex and edge
	 * sets.
	 *
	 * @param graph1 Input graph 1
	 * @param graph2 Input graph 2
	 * @return The result graph
	 */
	public static Graph union(Graph graph1, Graph graph2){
		Graph resultGraph = new Graph();
		resultGraph.vertexSet().addAll(graph1.vertexSet());
		resultGraph.vertexSet().addAll(graph2.vertexSet());
		resultGraph.edgeSet().addAll(graph1.edgeSet());
		resultGraph.edgeSet().addAll(graph2.edgeSet());
		return resultGraph;
	}

	/**
	 * This method is used to create a new graph obtained by removing all elements
	 * of the second graph from the first graph given as inputs.
	 *
	 * @param graph1 Input graph 1
	 * @param graph2 Input graph 2
	 * @return The result graph
	 */
	public static Graph remove(Graph graph1, Graph graph2){
		Graph resultGraph = new Graph();
		resultGraph.vertexSet().addAll(graph1.vertexSet());
		resultGraph.vertexSet().removeAll(graph2.vertexSet());
		resultGraph.edgeSet().addAll(graph1.edgeSet());
		resultGraph.edgeSet().removeAll(graph2.edgeSet());
		return resultGraph;
	}

	public void remove(Graph graph){
		this.vertexSet().removeAll(graph.vertexSet());
		this.edgeSet().removeAll(graph.edgeSet());
	}

	public static Graph importGraph(String path){
		if(path == null){
			return null;
		}
		File file = new File(path);
		if(!file.exists()){
			return null;
		}
		Graph result = new Graph();
		Map<String, AbstractVertex> vertexMap = new HashMap<>();
		try{
			BufferedReader eventReader = new BufferedReader(new FileReader(path));
			String line;
			while(true){
				line = eventReader.readLine();
				if(line == null){
					break;
				}
				processImportLine(line, result, vertexMap);
			}
			eventReader.close();
		}catch(Exception exception){
			exception.printStackTrace();
		}
		return result;
	}

	private static void processImportLine(String line, Graph graph, Map<String, AbstractVertex> vertexMap){
		try{
			Matcher nodeMatcher = nodePattern.matcher(line);
			Matcher edgeMatcher = edgePattern.matcher(line);
			if(nodeMatcher.find()){
				String key = nodeMatcher.group(1);
				String label = nodeMatcher.group(2);
				String shape = nodeMatcher.group(3);
				AbstractVertex vertex;
				if(shape.equals("box")){
					vertex = new spade.vertex.opm.Process();
				}else if(shape.equals("ellipse") || shape.equals("diamond")){
					vertex = new spade.vertex.opm.Artifact();
				}else if(shape.equals("octagon")){
					vertex = new spade.vertex.opm.Agent();
				}else{
					vertex = new spade.core.Vertex();
				}
				String[] pairs = label.split("\\\\n");
				for(String pair : pairs){
					String key_value[] = pair.split(":", 2);
					if(key_value.length == 2){
						vertex.addAnnotation(key_value[0], key_value[1]);
					}
				}
				graph.putVertex(vertex);
				vertexMap.put(key, vertex);
			}else if(edgeMatcher.find()){
				String childkey = edgeMatcher.group(1);
				String dstkey = edgeMatcher.group(2);
				String label = edgeMatcher.group(3);
				String color = edgeMatcher.group(4);
				AbstractEdge edge;
				AbstractVertex childVertex = vertexMap.get(childkey);
				AbstractVertex parentVertex = vertexMap.get(dstkey);
				if(color.equals("green")){
					edge = new spade.edge.opm.Used((spade.vertex.opm.Process)childVertex,
							(spade.vertex.opm.Artifact)parentVertex);
				}else if(color.equals("red")){
					edge = new spade.edge.opm.WasGeneratedBy((spade.vertex.opm.Artifact)childVertex,
							(spade.vertex.opm.Process)parentVertex);
				}else if(color.equals("blue")){
					edge = new spade.edge.opm.WasTriggeredBy((spade.vertex.opm.Process)childVertex,
							(spade.vertex.opm.Process)parentVertex);
				}else if(color.equals("purple")){
					edge = new spade.edge.opm.WasControlledBy((spade.vertex.opm.Process)childVertex,
							(spade.vertex.opm.Agent)parentVertex);
				}else if(color.equals("orange")){
					edge = new spade.edge.opm.WasDerivedFrom((spade.vertex.opm.Artifact)childVertex,
							(spade.vertex.opm.Artifact)parentVertex);
				}else{
					edge = new spade.core.Edge(childVertex, parentVertex);
				}
				if((label != null) && (label.length() > 2)){
					// label = label.substring(1, label.length() - 1);
					String[] pairs = label.split("\\\\n");
					for(String pair : pairs){
						String key_value[] = pair.split(":", 2);
						if(key_value.length == 2){
							edge.addAnnotation(key_value[0], key_value[1]);
						}
					}
				}
				graph.putEdge(edge);
			}
		}catch(Exception exception){
			logger.log(Level.SEVERE, "Error while processing line: " + line, exception);
		}
	}

	public String exportGraph(){
		StringBuilder outputString = new StringBuilder(500);
		try{
			outputString.append("digraph spade2dot {\n" + "graph [rankdir = \"RL\"];\n"
					+ "node [fontname=\"Helvetica\" fontsize=\"8\" style=\"filled\" margin=\"0.0,0.0\"];\n"
					+ "edge [fontname=\"Helvetica\" fontsize=\"8\"];\n");

			for(AbstractVertex vertex : vertexSet){
				outputString.append(exportVertex(vertex));
			}
			for(AbstractEdge edge : edgeSet){
				outputString.append(exportEdge(edge));
			}

			outputString.append("}\n");
		}catch(Exception exception){
			logger.log(Level.SEVERE, null, exception);
		}

		return outputString.toString();
	}

	public String exportGraphUnsafe() throws Exception{
		if(vertexSet.isEmpty()){
			return null;
		}
		StringBuilder outputString = new StringBuilder(500);
		try{
			outputString.append("digraph spade2dot {\n" + "graph [rankdir = \"RL\"];\n"
					+ "node [fontname=\"Helvetica\" fontsize=\"8\" style=\"filled\" margin=\"0.0,0.0\"];\n"
					+ "edge [fontname=\"Helvetica\" fontsize=\"8\"];\n");

			for(AbstractVertex vertex : vertexSet){
				String vertexString = exportVertex(vertex);
				if(vertexString == null){
					throw new Exception("Failed to export vertex: " + vertex);
				}
				outputString.append(vertexString);
			}
			for(AbstractEdge edge : edgeSet){
				String edgeString = exportEdge(edge);
				if(edgeString == null){
					throw new Exception("Failed to export edge: " + edge);
				}
				outputString.append(edgeString);
			}

			outputString.append("}\n");
		}catch(Exception exception){
			logger.log(Level.SEVERE, null, exception);
			throw exception;
		}
		return outputString.toString();
	}

	/**
	 * This method is used to export the graph to a DOT file which is useful for
	 * visualization.
	 *
	 * @param path The path to export the file to.
	 */
	public void exportGraph(String path){
		if((path == null)){
			return;
		}
		try{
			String outputString = this.exportGraph();
			if(outputString != null){
				FileWriter writer = new FileWriter(path, false);
				writer.write(outputString);
				writer.flush();
				writer.close();
			}
		}catch(Exception exception){
			logger.log(Level.SEVERE, null, exception);
		}
	}

	private String exportVertex(AbstractVertex vertex){
		try{
			StringBuilder annotationString = new StringBuilder();
			for(Map.Entry<String, String> currentEntry : vertex.getCopyOfAnnotations().entrySet()){
				String key = currentEntry.getKey();
				String value = currentEntry.getValue();
				annotationString.append(key.replace("\\", "\\\\")).append(":").append(value.replace("\\", "\\\\"))
						.append("\\n");
			}
			String vertexString = annotationString.substring(0, annotationString.length() - 2);
			String shape = "box";
			String color = "white";
			String type = vertex.getAnnotation("type");
			if(type.equalsIgnoreCase("Agent")){
				shape = "octagon";
				color = "rosybrown1";
			}else if(type.equalsIgnoreCase("Process") || type.equalsIgnoreCase("Activity")){
				shape = "box";
				color = "lightsteelblue1";
			}else if(type.equalsIgnoreCase("Artifact") || type.equalsIgnoreCase("Entity")){
				shape = "ellipse";
				color = "khaki1";
				String subtype = vertex.getAnnotation("subtype");
				if(OPMConstants.SUBTYPE_NETWORK_SOCKET.equalsIgnoreCase(subtype)){
					shape = "diamond";
					color = "palegreen1";
				}
			}

			String key = vertex.bigHashCode();
			String outputString = "\"" + key + "\" [label=\"" + vertexString.replace("\"", "'") + "\" shape=\"" + shape
					+ "\" fillcolor=\"" + color + "\"];\n";
			return outputString;
		}catch(Exception exception){
			logger.log(Level.SEVERE, null, exception);
			return null;
		}
	}

	private String exportEdge(AbstractEdge edge){
		try{
			StringBuilder annotationString = new StringBuilder();
			for(Map.Entry<String, String> currentEntry : edge.getCopyOfAnnotations().entrySet()){
				String key = currentEntry.getKey();
				String value = currentEntry.getValue();
				annotationString.append(key.replace("\\", "\\\\")).append(":").append(value.replace("\\", "\\\\"))
						.append("\\n");
			}
			String color = "black";
			String type = edge.getAnnotation("type");
			if(type.equalsIgnoreCase("Used")){
				color = "green";
			}else if(type.equalsIgnoreCase("WasGeneratedBy")){
				color = "red";
			}else if(type.equalsIgnoreCase("WasTriggeredBy")){
				color = "blue";
			}else if(type.equalsIgnoreCase("WasControlledBy")){
				color = "purple";
			}else if(type.equalsIgnoreCase("WasDerivedFrom")){
				color = "orange";
			}
			String style = "solid";
			if(edge.getAnnotation("success") != null && edge.getAnnotation("success").equals("false")){
				style = "dashed";
			}

			String edgeString = "(" + annotationString.substring(0, annotationString.length() - 2) + ")";
			String childKey = edge.getChildVertex().bigHashCode();
			String parentKey = edge.getParentVertex().bigHashCode();
			String outputString = "\"" + childKey + "\" -> \"" + parentKey + "\" [label=\""
					+ edgeString.replace("\"", "'") + "\" color=\"" + color + "\" style=\"" + style + "\"];\n";
			return outputString;
		}catch(Exception exception){
			logger.log(Level.SEVERE, null, exception);
			return null;
		}
	}

	public Graph getParents(Set<AbstractVertex> childVertices){
		Graph result = new Graph();
		for(AbstractEdge edge : edgeSet){
			AbstractVertex edgeChildVertex = edge.getChildVertex();
			for(AbstractVertex childVertex : childVertices){
				if(childVertex.bigHashCode().equals(edgeChildVertex.bigHashCode())){
					result.putVertex(edgeChildVertex);
					result.putVertex(edge.getParentVertex());
					result.putEdge(edge);
				}
			}
		}
		return result;
	}

	public Graph getChildren(Set<AbstractVertex> parentVertices){
		Graph result = new Graph();
		for(AbstractEdge edge : edgeSet){
			AbstractVertex edgeParentVertex = edge.getParentVertex();
			for(AbstractVertex parentVertex : parentVertices){
				if(parentVertex.bigHashCode().equals(edgeParentVertex.bigHashCode())){
					result.putVertex(edgeParentVertex);
					result.putVertex(edge.getChildVertex());
					result.putEdge(edge);
				}
			}
		}
		return result;
	}

//	if(decryptionLevel.equals(HIGH))
//
//	{
//		long tl = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").parse(timestamp).getTime();
//		double td = ((double)tl) / (1000.00);
//		timestamp = String.format("%.3f", td);
//                                             }

	public Graph getLineage(Set<AbstractVertex> startingVertices, Direction userDirection, int maxDepth){
		if(startingVertices == null || startingVertices.isEmpty() || maxDepth < 1){
			return new Graph();
		}

		final List<Direction> directions = new ArrayList<Direction>();
		if(Direction.kAncestor.equals(userDirection) || Direction.kDescendant.equals(userDirection)){
			directions.add(userDirection);
		}else if(Direction.kBoth.equals(userDirection)){
			directions.add(Direction.kAncestor);
			directions.add(Direction.kDescendant);
		}else{
			throw new RuntimeException(
					"Unexpected direction: '" + userDirection + "'. Expected: Ancestor, Descendant or Both");
		}

		Graph resultGraph = new Graph();

		for(final Direction direction : directions){
			Graph directionGraph = new Graph();

			Set<AbstractVertex> currentLevelVertices = new HashSet<AbstractVertex>();
			currentLevelVertices.addAll(startingVertices);

			int currentDepth = 1;
			while(currentLevelVertices.size() > 0){
				if(currentDepth > maxDepth){
					break;
				}else{
					Graph adjacentGraph = null;
					if(Direction.kAncestor.equals(direction)){
						adjacentGraph = getParents(currentLevelVertices);
					}else if(Direction.kDescendant.equals(direction)){
						adjacentGraph = getChildren(currentLevelVertices);
					}else{
						throw new RuntimeException(
								"Unexpected direction: '" + direction + "'. Expected: Ancestor or Descendant");
					}

					if(adjacentGraph.isEmpty()){
						break;
					}else{
						// Get only new vertices
						// Get all the vertices in the adjacent graph
						// Remove all the current level vertices from it and that means we have the only
						// new vertices (i.e. next level)
						// Remove all the vertices which are already in the the result graph to avoid
						// doing duplicate work
						Set<AbstractVertex> nextLevelVertices = new HashSet<AbstractVertex>();
						nextLevelVertices.addAll(adjacentGraph.vertexSet());
						nextLevelVertices.removeAll(currentLevelVertices);
						nextLevelVertices.removeAll(directionGraph.vertexSet());

						currentLevelVertices.clear();
						currentLevelVertices.addAll(nextLevelVertices);

						// Update the result graph after so that we don't remove all the relevant
						// vertices
						directionGraph.union(adjacentGraph);

						currentDepth++;
					}
				}
			}
			resultGraph.union(directionGraph);
		}
		return resultGraph;
	}

	/*
	private static Set<AbstractVertex> getVerticesWithNameA0(Graph graph){
		Set<AbstractVertex> r = new HashSet<AbstractVertex>();
		for(AbstractVertex vertex : graph.vertexSet()){
			if("a0".equals(vertex.getAnnotation("name"))){
				r.add(vertex);
			}
		}
		return r;
	}

	private static Graph getTestGraph(){
		Graph g = new Graph();
		Process a0_0 = new Process();
		a0_0.addAnnotation("name", "a0");
		a0_0.addAnnotation("id", "0");
		Process a0_1 = new Process();
		a0_1.addAnnotation("name", "a0");
		a0_1.addAnnotation("id", "1");
		Process a1 = new Process();
		a1.addAnnotation("name", "a1");
		WasTriggeredBy wtb0 = new WasTriggeredBy(a0_0, a1);
		WasTriggeredBy wtb1 = new WasTriggeredBy(a0_1, a1);
		WasTriggeredBy wtb1_reverse = new WasTriggeredBy(a1, a0_1);

		g.putVertex(a0_0);
		g.putVertex(a0_1);
		g.putVertex(a1);
		g.putEdge(wtb0);
		g.putEdge(wtb1);
		g.putEdge(wtb1_reverse);

		return g;
	}

	public static void main(String[] args) throws Exception{
		String outputDot = "/tmp/delout.dot";
		String outputSvg = "/tmp/delout.svg";
		Graph inputGraph = getTestGraph();
		System.out.println(inputGraph.exportGraph());
		/////
		Set<AbstractVertex> startVertices = getVerticesWithNameA0(inputGraph);
		Graph outputGraph = inputGraph.getLineage(startVertices, GetLineage.Direction.kAncestor, 10);
		/////
		outputGraph.exportGraph(outputDot);
		Execute.Output output = Execute.getOutput(
				new String[]{"/bin/bash", "-c", "/usr/local/bin/dot -o " + outputSvg + " -Tsvg " + outputDot});
//		Execute.Output output = Execute.getOutput(new String[]{"/bin/bash", "-c", "which dot"});
		System.out.println(output.command + " = " + output.exitValue);
		System.out.println(output.getStdOut());
		System.out.println(output.getStdErr());
//		Runtime.getRuntime().exec("dot -o " + outputSvg + " -Tsvg " + outputDot);
	}
	*/

	public String getHostName(){
		return hostName;
	}

	public void setHostName(String hostName){
		this.hostName = hostName;
	}

	public byte[] getSignature(){
		return signature;
	}

	public void setSignature(byte[] signature){
		this.signature = signature;
	}

	public String getQueryString(){
		return queryString;
	}

	public void setQueryString(String queryString){
		this.queryString = queryString;
	}

	@Override
	public String toString(){
		return "Graph{" + "vertexSet=" + vertexSet + ", edgeSet=" + edgeSet + '}';
	}

	/*
	 * @Author Raza
	 */
	private String prettyPrintVertices(){
		StringBuilder printStr = new StringBuilder(200);
		for(AbstractVertex vertex : vertexSet){
			printStr.append(vertex.prettyPrint());
			printStr.append(",\n");
		}
		if(printStr.length() > 0)
			return printStr.substring(0, printStr.length() - 2);
		return "\t\tNo Vertices";
	}

	/*
	 * @Author Raza
	 */
	private String prettyPrintEdges(){
		StringBuilder printStr = new StringBuilder(200);
		for(AbstractEdge edge : edgeSet){
			printStr.append(edge.prettyPrint());
			printStr.append(",\n");
		}
		if(printStr.length() > 0)
			return printStr.substring(0, printStr.length() - 2);
		return "\t\tNo edges";
	}

	/*
	 * @Author Raza
	 */
	// prints in a JSON like format
	public String prettyPrint(){
		return "Graph:{\n" + "\tvertexSet:{\n" + prettyPrintVertices() + "\n\t},\n" + "\tedgeSet:{\n"
				+ prettyPrintEdges() + "\n\t}\n}";
	}

	public boolean addSignature(String nonce){
		try{
			SecureRandom secureRandom = new SecureRandom();
			secureRandom.nextInt();
			Signature signature = Signature.getInstance("SHA256withRSA");
			PrivateKey privateKey = Kernel.getServerPrivateKey("serverprivate");
			if(privateKey == null){
				return false;
			}
			signature.initSign(privateKey, secureRandom);

			for(AbstractVertex vertex : vertexSet()){
				signature.update(vertex.bigHashCodeBytes());
			}
			for(AbstractEdge edge : edgeSet()){
				signature.update(edge.bigHashCodeBytes());
			}
			if(getQueryString() != null){
				signature.update(getQueryString().getBytes("UTF-8"));
			}
			if(nonce != null){
				signature.update(nonce.getBytes("UTF-8"));
			}

			byte[] digitalSignature = signature.sign();
			setSignature(digitalSignature);

			return true;
		}catch(Exception ex){
			logger.log(Level.SEVERE, "Error signing the result graph!", ex);
		}
		return false;
	}

	public boolean verifySignature(String nonce){
		try{
			Signature signature = Signature.getInstance("SHA256withRSA");
			String serverName = getHostName();
			if(serverName != null){
				String key_alias = serverName + ".server.public";
				PublicKey publicKey = Kernel.getServerPublicKey(key_alias);
				if(publicKey == null){
					return false;
				}
				signature.initVerify(publicKey);

				for(AbstractVertex vertex : vertexSet()){
					signature.update(vertex.bigHashCodeBytes());
				}
				for(AbstractEdge edge : edgeSet()){
					signature.update(edge.bigHashCodeBytes());
				}
				if(getQueryString() != null){
					signature.update(getQueryString().getBytes("UTF-8"));
				}
				if(nonce != null){
					signature.update(nonce.getBytes("UTF-8"));
				}

				return signature.verify(getSignature());
			}else{
				throw new Exception("NULL host name in graph");
			}
		}catch(Exception ex){
			logger.log(Level.SEVERE, "Error verifying the result graph!", ex);
		}
		return false;
	}

	public Graph copy(){
		Graph newGraph = new Graph();
		for(AbstractVertex vertex : this.vertexSet){
			newGraph.putVertex(vertex);
		}
		for(AbstractEdge edge : this.edgeSet){
			newGraph.putEdge(edge);
		}
		return newGraph;
	}

}
