/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2026 SRI International

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
package spade.utility.graph.codec.tokenize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import spade.core.AbstractVertex;
import spade.utility.graph.codec.Properties;
import spade.utility.graph.codec.Type;

public class CompressTest {

    @Test
    public void throwsWhenGraphIsNull() {
        final Compress compress = new Compress(new Properties(Type.TOKENIZE));
        assertThrows(IllegalArgumentException.class, () -> compress.graph(null));
    }

    @Test
    public void throwsWhenVertexInGraphIsNull() {
        final spade.core.Graph graph = new spade.core.Graph();
        graph.putVertex(null);

        final Compress compress = new Compress(new Properties(Type.TOKENIZE));
        assertThrows(IllegalArgumentException.class, () -> compress.graph(graph));
    }

    @Test
    public void throwsWhenEdgeInGraphIsNull() {
        final spade.core.Graph graph = new spade.core.Graph();
        graph.putEdge(null);

        final Compress compress = new Compress(new Properties(Type.TOKENIZE));
        assertThrows(IllegalArgumentException.class, () -> compress.graph(graph));
    }

    @Test
    public void throwsWhenChildVertexInEdgeIsNull() {
        final AbstractVertex parent = new spade.core.Vertex();

        final spade.core.Graph graph = new spade.core.Graph();
        graph.putVertex(parent);
        graph.putEdge(new spade.core.Edge(null, parent));

        final Compress compress = new Compress(new Properties(Type.TOKENIZE));
        assertThrows(IllegalArgumentException.class, () -> compress.graph(graph));
    }

    @Test
    public void throwsWhenParentVertexInEdgeIsNull() {
        final AbstractVertex child = new spade.core.Vertex();

        final spade.core.Graph graph = new spade.core.Graph();
        graph.putVertex(child);
        graph.putEdge(new spade.core.Edge(child, null));

        final Compress compress = new Compress(new Properties(Type.TOKENIZE));
        assertThrows(IllegalArgumentException.class, () -> compress.graph(graph));
    }

    @Test
    public void compressesGraphWithOnlyVertices() {
        final AbstractVertex vertex1 = new spade.core.Vertex();
        vertex1.addAnnotation("name", "v1");
        final AbstractVertex vertex2 = new spade.core.Vertex();
        vertex2.addAnnotation("name", "v2");

        final spade.core.Graph graph = new spade.core.Graph();
        graph.putVertex(vertex1);
        graph.putVertex(vertex2);

        final Compress compress = new Compress(new Properties(Type.TOKENIZE));
        final Graph compressedGraph = compress.graph(graph);

        assertEquals(2, compressedGraph.getVertices().size());
        assertEquals(0, compressedGraph.getEdges().size());
    }

    @Test
    public void compressesGraphWithOnlyEdges() {
        final AbstractVertex child = new spade.core.Vertex();
        child.addAnnotation("name", "child");
        final AbstractVertex parent = new spade.core.Vertex();
        parent.addAnnotation("name", "parent");

        final spade.core.Graph graph = new spade.core.Graph();
        graph.putEdge(new spade.core.Edge(child, parent));

        final Compress compress = new Compress(new Properties(Type.TOKENIZE));
        final Graph compressedGraph = compress.graph(graph);

        assertEquals(2, compressedGraph.getVertices().size());
        assertEquals(1, compressedGraph.getEdges().size());
    }

    @Test
    public void compressesGraphWithVerticesAndEdges() {
        final AbstractVertex child = new spade.core.Vertex();
        child.addAnnotation("name", "child");
        final AbstractVertex parent = new spade.core.Vertex();
        parent.addAnnotation("name", "parent");

        final spade.core.Graph graph = new spade.core.Graph();
        graph.putVertex(child);
        graph.putVertex(parent);
        graph.putEdge(new spade.core.Edge(child, parent));

        final Compress compress = new Compress(new Properties(Type.TOKENIZE));
        final Graph compressedGraph = compress.graph(graph);

        assertEquals(2, compressedGraph.getVertices().size());
        assertEquals(1, compressedGraph.getEdges().size());
    }

}
