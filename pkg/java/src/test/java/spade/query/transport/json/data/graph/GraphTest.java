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
package spade.query.transport.json.data.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import spade.core.AbstractVertex;
import spade.query.transport.json.data.Type;
import spade.utility.graph.codec.uncompressed.Compress;

public class GraphTest {

    @Test
    public void constructorThrowsWhenGraphIsNull() {
        assertThrows(IllegalArgumentException.class, () -> new Graph(null));
    }

    @Test
    public void toJSONWrapsTypeAndGraph() {
        final Graph graph = new Graph(new spade.utility.graph.codec.uncompressed.Graph(new spade.core.Graph()));

        final ObjectNode node = graph.toJSON();

        assertEquals(Type.GRAPH.name(), node.get("type").asText());
        assertTrue(node.get("graph").isObject());
        assertTrue(node.get("graph").has("type"));
    }

    @Test
    public void fromJSONRoundTripsToJSONOutput() {
        final AbstractVertex vertex = new spade.core.Vertex();
        final spade.core.Graph coreGraph = new spade.core.Graph();
        coreGraph.putVertex(vertex);

        final spade.utility.graph.codec.Graph compressedGraph =
                new Compress(new spade.utility.graph.codec.Properties(spade.utility.graph.codec.Type.UNCOMPRESSED))
                        .graph(coreGraph);

        final Graph graph = new Graph(compressedGraph);
        final Graph roundTripped = (Graph) Graph.fromJSON(graph.toJSON());

        assertEquals(spade.utility.graph.codec.Type.UNCOMPRESSED, roundTripped.getGraph().getType());
    }

    @Test
    public void fromJSONThrowsWhenNodeIsNull() {
        assertThrows(IllegalArgumentException.class, () -> Graph.fromJSON(null));
    }

    @Test
    public void fromJSONThrowsWhenGraphKeyIsMissing() {
        final ObjectNode node = new ObjectMapper().createObjectNode();
        node.put("type", Type.GRAPH.name());

        assertThrows(IllegalArgumentException.class, () -> Graph.fromJSON(node));
    }

    @Test
    public void fromJSONThrowsWhenGraphKeyIsNotAnObject() {
        final ObjectNode node = new ObjectMapper().createObjectNode();
        node.put("type", Type.GRAPH.name());
        node.put("graph", "not-an-object");

        assertThrows(IllegalArgumentException.class, () -> Graph.fromJSON(node));
    }

}
