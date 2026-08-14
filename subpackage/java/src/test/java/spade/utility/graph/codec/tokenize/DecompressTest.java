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

import java.util.HashMap;

import org.junit.jupiter.api.Test;

import spade.core.AbstractVertex;
import spade.utility.graph.codec.Properties;
import spade.utility.graph.codec.Type;
import spade.utility.graph.codec.tokenize.token.Phrase;
import spade.utility.graph.codec.tokenize.token.Token;

public class DecompressTest {

    @Test
    public void throwsWhenGraphIsNull() {
        final Decompress decompress = new Decompress(new Properties(Type.TOKENIZE));
        assertThrows(IllegalArgumentException.class, () -> decompress.graph(null));
    }

    @Test
    public void throwsWhenVertexTokenIsNull() {
        final Graph graph = new Graph();
        graph.putVertex(null, new Vertex(new HashMap<>()));

        final Decompress decompress = new Decompress(new Properties(Type.TOKENIZE));
        assertThrows(IllegalArgumentException.class, () -> decompress.graph(graph));
    }

    @Test
    public void throwsWhenVertexIsNull() {
        final Graph graph = new Graph();
        final Token id = graph.getTokenMap().getToken(new Phrase("v1"));
        graph.putVertex(id, null);

        final Decompress decompress = new Decompress(new Properties(Type.TOKENIZE));
        assertThrows(IllegalArgumentException.class, () -> decompress.graph(graph));
    }

    @Test
    public void throwsWhenEdgeTokenIsNull() {
        final Graph graph = new Graph();
        final Token childId = graph.getTokenMap().getToken(new Phrase("child"));
        final Token parentId = graph.getTokenMap().getToken(new Phrase("parent"));
        graph.putVertex(childId, new Vertex(new HashMap<>()));
        graph.putVertex(parentId, new Vertex(new HashMap<>()));
        graph.putEdge(null, new Edge(childId, parentId, new HashMap<>()));

        final Decompress decompress = new Decompress(new Properties(Type.TOKENIZE));
        assertThrows(IllegalArgumentException.class, () -> decompress.graph(graph));
    }

    @Test
    public void throwsWhenEdgeIsNull() {
        final Graph graph = new Graph();
        final Token id = graph.getTokenMap().getToken(new Phrase("e1"));
        graph.putEdge(id, null);

        final Decompress decompress = new Decompress(new Properties(Type.TOKENIZE));
        assertThrows(IllegalArgumentException.class, () -> decompress.graph(graph));
    }

    @Test
    public void throwsWhenChildTokenInEdgeIsNull() {
        final Graph graph = new Graph();
        final Token parentId = graph.getTokenMap().getToken(new Phrase("parent"));
        graph.putVertex(parentId, new Vertex(new HashMap<>()));

        final Token edgeId = graph.getTokenMap().getToken(new Phrase("e1"));
        graph.putEdge(edgeId, new Edge(null, parentId, new HashMap<>()));

        final Decompress decompress = new Decompress(new Properties(Type.TOKENIZE));
        assertThrows(IllegalArgumentException.class, () -> decompress.graph(graph));
    }

    @Test
    public void throwsWhenParentTokenInEdgeIsNull() {
        final Graph graph = new Graph();
        final Token childId = graph.getTokenMap().getToken(new Phrase("child"));
        graph.putVertex(childId, new Vertex(new HashMap<>()));

        final Token edgeId = graph.getTokenMap().getToken(new Phrase("e1"));
        graph.putEdge(edgeId, new Edge(childId, null, new HashMap<>()));

        final Decompress decompress = new Decompress(new Properties(Type.TOKENIZE));
        assertThrows(IllegalArgumentException.class, () -> decompress.graph(graph));
    }

    @Test
    public void throwsWhenChildVertexIsUnresolved() {
        final Graph graph = new Graph();
        final Token parentId = graph.getTokenMap().getToken(new Phrase("parent"));
        graph.putVertex(parentId, new Vertex(new HashMap<>()));

        final Token missingChildId = graph.getTokenMap().getToken(new Phrase("missing-child"));
        final Token edgeId = graph.getTokenMap().getToken(new Phrase("e1"));
        graph.putEdge(edgeId, new Edge(missingChildId, parentId, new HashMap<>()));

        final Decompress decompress = new Decompress(new Properties(Type.TOKENIZE));
        assertThrows(IllegalArgumentException.class, () -> decompress.graph(graph));
    }

    @Test
    public void throwsWhenParentVertexIsUnresolved() {
        final Graph graph = new Graph();
        final Token childId = graph.getTokenMap().getToken(new Phrase("child"));
        graph.putVertex(childId, new Vertex(new HashMap<>()));

        final Token missingParentId = graph.getTokenMap().getToken(new Phrase("missing-parent"));
        final Token edgeId = graph.getTokenMap().getToken(new Phrase("e1"));
        graph.putEdge(edgeId, new Edge(childId, missingParentId, new HashMap<>()));

        final Decompress decompress = new Decompress(new Properties(Type.TOKENIZE));
        assertThrows(IllegalArgumentException.class, () -> decompress.graph(graph));
    }

    @Test
    public void decompressesGraphWithOnlyVertices() {
        final AbstractVertex vertex1 = new spade.core.Vertex();
        vertex1.addAnnotation("name", "v1");
        final AbstractVertex vertex2 = new spade.core.Vertex();
        vertex2.addAnnotation("name", "v2");

        final spade.core.Graph originalGraph = new spade.core.Graph();
        originalGraph.putVertex(vertex1);
        originalGraph.putVertex(vertex2);

        final Graph compressedGraph = new Compress(new Properties(Type.TOKENIZE)).graph(originalGraph);
        final spade.core.Graph decompressedGraph = new Decompress(new Properties(Type.TOKENIZE)).graph(compressedGraph);

        assertEquals(2, decompressedGraph.vertexSet().size());
        assertEquals(0, decompressedGraph.edgeSet().size());
    }

    @Test
    public void decompressesGraphWithVerticesAndEdges() {
        final AbstractVertex child = new spade.core.Vertex();
        child.addAnnotation("name", "child");
        final AbstractVertex parent = new spade.core.Vertex();
        parent.addAnnotation("name", "parent");

        final spade.core.Graph originalGraph = new spade.core.Graph();
        originalGraph.putVertex(child);
        originalGraph.putVertex(parent);
        originalGraph.putEdge(new spade.core.Edge(child, parent));

        final Graph compressedGraph = new Compress(new Properties(Type.TOKENIZE)).graph(originalGraph);
        final spade.core.Graph decompressedGraph = new Decompress(new Properties(Type.TOKENIZE)).graph(compressedGraph);

        assertEquals(2, decompressedGraph.vertexSet().size());
        assertEquals(1, decompressedGraph.edgeSet().size());
    }

    @Test
    public void preservesAnnotationsThroughRoundTrip() {
        final AbstractVertex vertex = new spade.core.Vertex();
        vertex.addAnnotation("name", "only-vertex");

        final spade.core.Graph originalGraph = new spade.core.Graph();
        originalGraph.putVertex(vertex);

        final Graph compressedGraph = new Compress(new Properties(Type.TOKENIZE)).graph(originalGraph);
        final spade.core.Graph decompressedGraph = new Decompress(new Properties(Type.TOKENIZE)).graph(compressedGraph);

        final AbstractVertex decompressedVertex = decompressedGraph.vertexSet().iterator().next();
        assertEquals("only-vertex", decompressedVertex.getAnnotation("name"));
    }

}
