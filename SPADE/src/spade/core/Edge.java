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

import java.util.LinkedHashMap;
import java.util.Map;

public class Edge extends AbstractEdge {

    // A general-purpose, semantic-agnostic implementation of the Edge class.

    private Vertex sourceVertex;
    private Vertex destinationVertex;

    public Edge(Vertex sourceVertex, Vertex destinationVertex) {
        this.sourceVertex = sourceVertex;
        this.destinationVertex = destinationVertex;
        annotations = new LinkedHashMap<String, String>();
        this.edgeType = "Edge";
    }

    public Edge(Vertex sourceVertex, Vertex destinationVertex, Map<String, String> inputAnnotations) {
        this.sourceVertex = sourceVertex;
        this.destinationVertex = destinationVertex;
        this.setAnnotations(inputAnnotations);
        this.edgeType = "Edge";
    }

    public void setSrcVertex(Vertex sourceVertex) {
        this.sourceVertex = sourceVertex;
    }

    public void setDstVertex(Vertex destinationVertex) {
        this.destinationVertex = destinationVertex;
    }

    @Override
    public AbstractVertex getSrcVertex() {
        return sourceVertex;
    }

    @Override
    public AbstractVertex getDstVertex() {
        return destinationVertex;
    }
}
