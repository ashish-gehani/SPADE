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

    public boolean initialize(String path) {
        return false;
    }

    public boolean shutdown() {
        return false;
    }

    public boolean putVertex(AbstractVertex incomingVertex) {
        return false;
    }

    public boolean putEdge(AbstractEdge incomingEdge) {
        return false;
    }

    public boolean flushTransactions() {
        return false;
    }

    public Set<AbstractVertex> getVertices(String expression) {
        return null;
    }

    public Set<AbstractEdge> getEdges(String expression) {
        return null;
    }

    public Set<AbstractEdge> getEdges(String sourceExpression, String destinationExpression, String edgeExpression) {
        return null;
    }

    public Set<AbstractEdge> getEdges(AbstractVertex sourceVertex, AbstractVertex destinationVertex) {
        return null;
    }

    public Lineage getLineage(AbstractVertex sourceVertex, String pruneExpression, int direction, boolean includeTerminatingNode) {
        return null;
    }

    public Lineage getLineage(AbstractVertex sourceVertex, int depth, int direction) {
        return null;
    }

    public Lineage getLineage(String vertexId, int depth, String direction) {
        return null;
    }
}
