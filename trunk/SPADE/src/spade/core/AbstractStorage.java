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

import java.util.Set;

public abstract class AbstractStorage {

    public String arguments;

    public boolean initialize(String arguments) {
        return false;
    }

    public boolean shutdown() {
        return true;
    }

    public boolean putVertex(AbstractVertex incomingVertex) {
        return false;
    }

    public boolean putEdge(AbstractEdge incomingEdge) {
        return false;
    }

    public boolean flushTransactions() {
        return true;
    }

    public Set<AbstractVertex> getVertices(String expression) {
        return null;
    }

    public Set<AbstractEdge> getEdges(String sourceExpression, String destinationExpression, String edgeExpression) {
        return null;
    }

    public Set<AbstractEdge> getEdges(String srcVertexId, String dstVertexId) {
        return null;
    }

    public Lineage getLineage(String vertexId, int depth, String direction, String terminatingExpression) {
        return null;
    }
}
