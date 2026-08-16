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
package spade.utility.graph.codec.uncompressed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import spade.core.AbstractVertex;
import spade.utility.graph.codec.Properties;
import spade.utility.graph.codec.Type;

public class CompressTest {

    @Test
    public void throwsWhenGraphIsNull() {
        final Compress compress = new Compress(new Properties(Type.UNCOMPRESSED));
        assertThrows(IllegalArgumentException.class, () -> compress.graph(null));
    }

    @Test
    public void wrapsGraphReferenceDirectly() {
        final spade.core.Graph graph = new spade.core.Graph();

        final Compress compress = new Compress(new Properties(Type.UNCOMPRESSED));
        final Graph compressedGraph = compress.graph(graph);

        assertSame(graph, compressedGraph.getGraph());
    }

    @Test
    public void wrapsGraphWithDistinctChildAndParentWithoutModifyingIt() {
        final AbstractVertex child = new spade.core.Vertex();
        child.addAnnotation("name", "child");
        final AbstractVertex parent = new spade.core.Vertex();
        parent.addAnnotation("name", "parent");

        final spade.core.Graph graph = new spade.core.Graph();
        graph.putVertex(child);
        graph.putVertex(parent);
        graph.putEdge(new spade.core.Edge(child, parent));

        final Compress compress = new Compress(new Properties(Type.UNCOMPRESSED));
        final Graph compressedGraph = compress.graph(graph);

        assertEquals(2, compressedGraph.getGraph().vertexSet().size());
        assertEquals(1, compressedGraph.getGraph().edgeSet().size());
    }

    @Test
    public void wrapsGraphWithSameChildAndParentWithoutModifyingIt() {
        final AbstractVertex vertex = new spade.core.Vertex();
        vertex.addAnnotation("name", "self");

        final spade.core.Graph graph = new spade.core.Graph();
        graph.putVertex(vertex);
        graph.putEdge(new spade.core.Edge(vertex, vertex));

        final Compress compress = new Compress(new Properties(Type.UNCOMPRESSED));
        final Graph compressedGraph = compress.graph(graph);

        assertEquals(1, compressedGraph.getGraph().vertexSet().size());
        assertEquals(1, compressedGraph.getGraph().edgeSet().size());
    }

}
