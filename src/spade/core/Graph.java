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

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.query.quickgrail.instruction.ExportGraph;
import spade.query.quickgrail.instruction.GetLineage.Direction;
import spade.storage.Graphviz;
import spade.storage.JSON;
import spade.utility.DotConfiguration;
import spade.utility.HelperFunctions;

/**
 * This class is used to represent query responses using sets for edges and
 * vertices.
 *
 * @author Dawood Tariq
 */
public class Graph implements Serializable{

	private static final long serialVersionUID = 697695332496188058L;

	private static final Logger logger = Logger.getLogger(Graph.class.getName());

	private final Set<AbstractVertex> vertexSet = new LinkedHashSet<>();
	private final Set<AbstractEdge> edgeSet = new LinkedHashSet<>();

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
	 * Returns the set containing the vertices.
	 *
	 * @return The set containing the vertices.
	 */
	public final Set<AbstractVertex> vertexSet(){
		return vertexSet;
	}

	/**
	 * Returns the set containing the edges.
	 *
	 * @return The set containing edges.
	 */
	public final Set<AbstractEdge> edgeSet(){
		return edgeSet;
	}

	public String getHostName(){
		return hostName;
	}

	public void setHostName(String hostName){
		this.hostName = hostName;
	}

	private byte[] getSignature(){
		return signature;
	}

	private void setSignature(byte[] signature){
		this.signature = signature;
	}

	public String getQueryString(){
		return queryString;
	}

	private void setQueryString(String queryString){
		this.queryString = queryString;
	}

	@Override
	public String toString(){
		return "Graph{" + "vertexSet=" + vertexSet + ", edgeSet=" + edgeSet + "}";
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

					if(adjacentGraph.vertexSet().isEmpty()){
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
	
	public static final Graph importGraphFromDOTFile(final String filePath) throws Exception{
		final boolean blocking = true, closeReaderOnShutdown = true;
		final int reportingIntervalSeconds = -1;
		final boolean logAll = false;
		
		final spade.reporter.Graphviz reporter = new spade.reporter.Graphviz();
		final spade.core.Buffer buffer = new spade.core.Buffer();
		reporter.setBuffer(buffer);
		reporter.launchUnsafe(filePath, reportingIntervalSeconds, blocking, closeReaderOnShutdown, logAll);
		reporter.shutdown();
		
		return createGraphFromBuffer(buffer);
	}
	
	public static final Graph importGraphFromJSONFile(final String filePath) throws Exception{
		final boolean blocking = true, closeReaderOnShutdown = true;
		final int reportingIntervalSeconds = -1;
		final boolean logAll = false;
		
		final spade.reporter.JSON reporter = new spade.reporter.JSON();
		final spade.core.Buffer buffer = new spade.core.Buffer();
		reporter.setBuffer(buffer);
		reporter.launchUnsafe(filePath, reportingIntervalSeconds, blocking, closeReaderOnShutdown, logAll);
		reporter.shutdown();
		
		return createGraphFromBuffer(buffer);
	}
	
	private static final Graph createGraphFromBuffer(final Buffer buffer){
		final Graph graph = new Graph();
		
		while(!buffer.isEmpty()){
			final Object bufferElement = buffer.getBufferElement();
			if(bufferElement != null){
				if(bufferElement instanceof AbstractVertex){
					graph.putVertex((AbstractVertex)bufferElement);
				}else if(bufferElement instanceof AbstractEdge){
					graph.putEdge((AbstractEdge)bufferElement);
				}
			}
		}
		
		return graph;
	}
	
	public static final String exportGraphToString(final ExportGraph.Format format, final Graph graph) throws Exception{
		final StringWriter stringBuffer = new StringWriter();
		exportGraphUsingWriter(format, new BufferedWriter(stringBuffer), graph, true);
		return stringBuffer.getBuffer().toString();
	}
	
	public static final void exportGraphToFile(final ExportGraph.Format format, final String filePath, final Graph graph) throws Exception{
		if(HelperFunctions.isNullOrEmpty(filePath)){
			throw new RuntimeException("Cannot export graph to NULL/Empty file path: '"+filePath+"'");
		}else{
			try(BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))){
				exportGraphUsingWriter(format, writer, graph, false);
			}
		}
	}

	public static final void exportGraphUsingWriter(
			final ExportGraph.Format format,
			final BufferedWriter writer, final Graph graph,
			final boolean closeWriter) throws Exception{
		if(graph == null){
			throw new RuntimeException("Cannot export NULL graph");
		}else if(format == null){
			throw new RuntimeException("Cannot export to NULL format");
		}else{
			final AbstractStorage storage;
			final boolean printHeader = true, printFooter = true;
			
			switch(format){
				case kJson:
					final boolean printRecordSeparator = true;
					final JSON jsonStorage = new JSON();
					jsonStorage.initializeUnsafe(
							writer,  
							printHeader, printFooter, printRecordSeparator,
							System.lineSeparator(), closeWriter);
					storage = jsonStorage;
					break;
				case kDot:
					final Graphviz dotStorage = new Graphviz();
					dotStorage.initializeUnsafe(
							writer, 
							DotConfiguration.getDefaultConfigFilePath(), 
							printHeader, printFooter, System.lineSeparator(),
							closeWriter);
					storage = dotStorage;
					break;
				default: throw new RuntimeException("Unhandled graph export format: " + format);
			}

			try{
				for(AbstractVertex vertex : graph.vertexSet()){
					storage.putVertex(vertex);
				}
				
				for(AbstractEdge edge : graph.edgeSet()){
					storage.putEdge(edge);
				}
			}catch(Exception e){
				throw e;
			}finally{
				try{
					storage.shutdown();
				}catch(Exception e){
					// ignore
				}
			}
		}
	}

}

/*
if(decryptionLevel.equals(HIGH)){
	long tl = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").parse(timestamp).getTime();
	double td = ((double)tl) / (1000.00);
	timestamp = String.format("%.3f", td);
}
*/
