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

public class DecompressTest {

    @Test
    public void throwsWhenGraphIsNull() {
        final Decompress decompress = new Decompress(new Properties(Type.UNCOMPRESSED));
        assertThrows(IllegalArgumentException.class, () -> decompress.graph(null));
    }

    @Test
    public void unwrapsGraphReferenceDirectly() {
        final spade.core.Graph graph = new spade.core.Graph();
        final Graph compressedGraph = new Graph(graph);

        final Decompress decompress = new Decompress(new Properties(Type.UNCOMPRESSED));
        final spade.core.Graph decompressedGraph = decompress.graph(compressedGraph);

        assertSame(graph, decompressedGraph);
    }

    @Test
    public void roundTripReturnsSameGraphReference() {
        final AbstractVertex child = new spade.core.Vertex();
        child.addAnnotation("name", "child");
        final AbstractVertex parent = new spade.core.Vertex();
        parent.addAnnotation("name", "parent");

        final spade.core.Graph graph = new spade.core.Graph();
        graph.putVertex(child);
        graph.putVertex(parent);
        graph.putEdge(new spade.core.Edge(child, parent));

        final Graph compressedGraph = new Compress(new Properties(Type.UNCOMPRESSED)).graph(graph);
        final spade.core.Graph decompressedGraph = new Decompress(new Properties(Type.UNCOMPRESSED)).graph(compressedGraph);

        assertSame(graph, decompressedGraph);
        assertEquals(2, decompressedGraph.vertexSet().size());
        assertEquals(1, decompressedGraph.edgeSet().size());
    }

}
