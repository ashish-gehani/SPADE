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

/**
 * This is the base class for storages.
 * 
 * @author Dawood Tariq
 */
public abstract class AbstractStorage {

	/**
	 * The arguments with which this storage was initialized.
	 */
	public String arguments;
	/**
	 * The number of vertices that this storage instance has successfully
	 * received.
	 */
	public long vertexCount;
	/**
	 * The number of edges that this storage instance has successfully received.
	 */
	public long edgeCount;

	/**
	 * This method is invoked by the kernel to initialize the storage.
	 * 
	 * @param arguments
	 *            The arguments with which this storage is to be initialized.
	 * @return True if the storage was initialized successfully.
	 */
	public abstract boolean initialize(String arguments);

	/**
	 * This method is invoked by the kernel to shut down the storage.
	 * 
	 * @return True if the storage was shut down successfully.
	 */
	public abstract boolean shutdown();

	/**
	 * This method is triggered when the storage receives a vertex.
	 * 
	 * @param incomingVertex
	 *            The vertex received by this storage.
	 * @return True if the vertex was processed successfully.
	 */
	public abstract boolean putVertex(AbstractVertex incomingVertex);

	/**
	 * This method is triggered when the storage receives an edge.
	 * 
	 * @param incomingEdge
	 *            The edge received by this storage.
	 * @return True if the edge was processed successfully.
	 */
	public abstract boolean putEdge(AbstractEdge incomingEdge);

	/**
	 * This method is triggered by the Kernel to flush transactions.
	 * 
	 * @return True if the transactions were flushed successfully.
	 */
	public boolean flushTransactions() {
		return true;
	}

	/**
	 * Query method that returns a set of vertices based on a given expression.
	 * 
	 * @param expression
	 *            The query expression.
	 * @return The result of this query in a graph object.
	 */
	public Graph getVertices(String expression) {
		throw new UnsupportedOperationException("Unsupported operation.");
	}

	/**
	 * Query method that returns a set of edges given expressions for the edge,
	 * the source vertex and the destination vertex.
	 * 
	 * @param sourceExpression
	 *            The query expression for source vertices.
	 * @param destinationExpression
	 *            The query expression for destination vertices.
	 * @param edgeExpression
	 *            The query expression for edges.
	 * @return The result of this query in a graph object.
	 */
	public Graph getEdges(String sourceExpression, String destinationExpression, String edgeExpression) {
		throw new UnsupportedOperationException("Unsupported operation.");
	}

	/**
	 * Query method that returns a set of edges given the source and destination
	 * vertex identifiers.
	 * 
	 * @param srcVertexId
	 *            The source vertex id.
	 * @param dstVertexId
	 *            The destination vertex id.
	 * @return The result of this query in a graph object.
	 */
	public Graph getEdges(int srcVertexId, int dstVertexId) {
		throw new UnsupportedOperationException("Unsupported operation.");
	}

	/**
	 * Query method that returns a graph object containing all paths from the
	 * given source vertex to the given destination vertex, constrained by the
	 * maxLength argument as the maximum length of the paths.
	 * 
	 * @param srcVertexId
	 *            The source vertex id.
	 * @param dstVertexId
	 *            The destination vertex id.
	 * @param maxLength
	 *            The maximum path length.
	 * @return The result of this query in a graph object.
	 */
	public Graph getPaths(int srcVertexId, int dstVertexId, int maxLength) {
		throw new UnsupportedOperationException("Unsupported operation.");
	}

	public Graph getPaths(int srcVertexId, int dstVertexId) {
		return getPaths(srcVertexId, dstVertexId, Integer.MAX_VALUE);
	}

	public Graph getPaths(String srcVertexExpression, String dstVertexExpression, int maxLength) {
		throw new UnsupportedOperationException("Unsupported operation.");
	}

	public Graph getPaths(String srcVertexExpression, String dstVertexExpression) {
		return getPaths(srcVertexExpression, dstVertexExpression, Integer.MAX_VALUE);
	}

	public Graph getPaths(Graph srcGraph, Graph dstGraph, int maxLength) {
		throw new UnsupportedOperationException("Unsupported operation.");
	}

	public Graph getPaths(Graph srcGraph, Graph dstGraph) {
		return getPaths(srcGraph, dstGraph, Integer.MAX_VALUE);
	}

	/**
	 * Query method that returns a graph object containing the lineage of a
	 * given vertex. The controlling parameters for the lineage are depth and
	 * direction along with an optional expression for terminating on vertices.
	 * 
	 * @param vertexId
	 *            The source vertex of the lineage.
	 * @param depth
	 *            The maximum depth of the lineage.
	 * @param direction
	 *            The direction in which to get the lineage.
	 * @param terminatingExpression
	 *            The query expression on which to terminate lineage or null.
	 * @return The result of this query in a graph object.
	 */
	public Graph getLineage(int vertexId, int depth, String direction, String terminatingExpression) {
		throw new UnsupportedOperationException("Unsupported operation.");
	}

	public Graph getLineage(int vertexId, String direction) {
		return getLineage(vertexId, Integer.MAX_VALUE, direction, null);
	}

	public Graph getLineage(String vertexExpression, int depth, String direction, String terminatingExpression) {
		throw new UnsupportedOperationException("Unsupported operation.");
	}

	public Graph getLineage(String vertexExpression, String direction) {
		return getLineage(vertexExpression, Integer.MAX_VALUE, direction, null);
	}

	public Graph getLineage(Graph srcGraph, int depth, String direction, String terminatingExpression) {
		throw new UnsupportedOperationException("Unsupported operation.");
	}

	public Graph getLineage(Graph srcGraph, String direction) {
		return getLineage(srcGraph, Integer.MAX_VALUE, direction, null);
	}
}
