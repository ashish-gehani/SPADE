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
import spade.opm.edge.Edge;
import spade.opm.vertex.Vertex;

public abstract class AbstractStorage {

    public boolean initialize(String path) {
        return true;
    }

    public boolean shutdown() {
        return true;
    }

    public boolean putVertex(Vertex v) {
        return true;
    }

    public boolean putEdge(Edge e) {
        return true;
    }

    // vertices/expression
    public Set<Vertex> getVertices(String expression) {
        return null;
    }

    // edges/expression
    public Set<Edge> getEdges(String expression) {
        return null;
    }

    public Set<Edge> getEdges(String sourceExpression, String destinationExpression, String edgeExpression) {
        return null;
    }

    public Set<Edge> getEdges(Vertex source, Vertex destination) {
        return null;
    }

    public Lineage getLineage(Vertex source, String pruneExpression, int direction, boolean includeTerminatingNode) {
        return null;
    }

    public Lineage getLineage(Vertex source, int depth, int direction) {
        return null;
    }
}
