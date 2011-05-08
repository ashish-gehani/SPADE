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
package spade.core;

public abstract class AbstractStorage {

    // All the methods in this class must be overridden by custom-implemented
    // storages.

    public String arguments;

    // This method must return true to indicate that the storage was
    // succsessfully initialized.
    public boolean initialize(String arguments) {
        return false;
    }

    // This method must return true to indicate that the storage was shut
    // down successfully.
    public boolean shutdown() {
        return true;
    }

    // This method is triggered when the storage receives a vertex.
    public boolean putVertex(AbstractVertex incomingVertex) {
        return false;
    }

    // This method is triggered when the storage receives an edge.
    public boolean putEdge(AbstractEdge incomingEdge) {
        return false;
    }

    // This method is triggered by the Kernel to flush transactions.
    public boolean flushTransactions() {
        return true;
    }

    // Query method that returns a set of vertices based on a given expression.
    public Graph getVertices(String expression) {
        return null;
    }

    // Query method that returns a set of edges given expressions for the edge,
    // the source vertex and the destination vertex.
    public Graph getEdges(String sourceExpression, String destinationExpression, String edgeExpression) {
        return null;
    }

    // Query method that returns a set of edges given the source and destination
    // vertex identifiers.
    public Graph getEdges(String srcVertexId, String dstVertexId) {
        return null;
    }

    // Query method that returns a graph object containing all paths from the given
    // source vertex to the given destination vertex, contrained by the maxLength
    // argument as the maximum length of the paths.
    public Graph getPaths(String srcVertexId, String dstVertexId, int maxLength) {
        return null;
    }

    // Query method that returns a graph object containing the lineage of a given
    // vertex. The controlling parameters for the lineage are depth and direction
    // along with an optional expression for terminating on vertices.
    public Graph getLineage(String vertexId, int depth, String direction, String terminatingExpression) {
        return null;
    }
}
