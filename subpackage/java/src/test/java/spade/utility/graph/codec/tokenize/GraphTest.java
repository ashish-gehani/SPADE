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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import spade.utility.graph.codec.Type;
import spade.utility.graph.codec.tokenize.token.Phrase;
import spade.utility.graph.codec.tokenize.token.Token;

public class GraphTest {

    @Test
    public void toJSONWrapsTypeAndGraph() {
        final Graph graph = new Graph();
        final ObjectNode node = graph.toJSON();

        assertEquals(Type.TOKENIZE.name(), node.get("type").asText());
        assertTrue(node.get("graph").isObject());
        assertTrue(node.get("graph").has("tokenMap"));
        assertTrue(node.get("graph").has("vertices"));
        assertTrue(node.get("graph").has("edges"));
    }

    @Test
    public void fromJSONRoundTripsToJSONOutput() {
        final Graph graph = new Graph();
        final Token id = graph.getTokenMap().getToken(new Phrase("v1"));
        graph.putVertex(id, new Vertex(new java.util.HashMap<>()));

        final ObjectNode node = graph.toJSON();
        final Graph roundTripped = Graph.fromJSON(node);

        assertEquals(1, roundTripped.getVertices().size());
    }

    @Test
    public void fromJSONThrowsWhenGraphKeyIsMissing() {
        final ObjectNode node = new ObjectMapper().createObjectNode();
        node.put("type", Type.TOKENIZE.name());

        assertThrows(IllegalArgumentException.class, () -> Graph.fromJSON(node));
    }

    @Test
    public void fromJSONThrowsWhenGraphKeyIsNotAnObject() {
        final ObjectNode node = new ObjectMapper().createObjectNode();
        node.put("type", Type.TOKENIZE.name());
        node.put("graph", "not-an-object");

        assertThrows(IllegalArgumentException.class, () -> Graph.fromJSON(node));
    }

}
