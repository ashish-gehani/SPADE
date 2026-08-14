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

import java.util.HashMap;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import spade.utility.graph.codec.tokenize.token.Phrase;
import spade.utility.graph.codec.tokenize.token.Token;

public class ConvertTest {

    @Test
    public void producesExpectedTopLevelKeys() {
        final Graph graph = new Graph();
        final ObjectNode node = new Convert().toJSON(graph);

        assertTrue(node.has("tokenMap"));
        assertTrue(node.has("vertices"));
        assertTrue(node.has("edges"));
    }

    @Test
    public void roundTripsEmptyGraph() {
        final Graph graph = new Graph();

        final Convert convert = new Convert();
        final ObjectNode node = convert.toJSON(graph);

        assertEquals(0, node.get("tokenMap").size());
        assertEquals(0, node.get("vertices").size());
        assertEquals(0, node.get("edges").size());

        final Graph roundTripped = convert.fromJSON(node);

        assertEquals(0, roundTripped.getVertices().size());
        assertEquals(0, roundTripped.getEdges().size());
    }

    @Test
    public void roundTripsVerticesEdgesAndAnnotations() {
        final Graph graph = new Graph();

        final Token childId = graph.getTokenMap().getToken(new Phrase("child"));
        final Token parentId = graph.getTokenMap().getToken(new Phrase("parent"));
        final Token nameKey = graph.getTokenMap().getToken(new Phrase("name"));
        final Token childNameValue = graph.getTokenMap().getToken(new Phrase("child-name"));
        final Token parentNameValue = graph.getTokenMap().getToken(new Phrase("parent-name"));

        final java.util.Map<Token, Token> childAnnotations = new HashMap<>();
        childAnnotations.put(nameKey, childNameValue);
        graph.putVertex(childId, new Vertex(childAnnotations));

        final java.util.Map<Token, Token> parentAnnotations = new HashMap<>();
        parentAnnotations.put(nameKey, parentNameValue);
        graph.putVertex(parentId, new Vertex(parentAnnotations));

        final Token edgeId = graph.getTokenMap().getToken(new Phrase("e1"));
        graph.putEdge(edgeId, new Edge(childId, parentId, new HashMap<>()));

        final Convert convert = new Convert();
        final ObjectNode node = convert.toJSON(graph);
        final Graph roundTripped = convert.fromJSON(node);

        assertEquals(2, roundTripped.getVertices().size());
        assertEquals(1, roundTripped.getEdges().size());

        final Vertex roundTrippedChild = roundTripped.getVertices().get(childId);
        final Token roundTrippedChildNameValue =
            roundTrippedChild.getAnnotations().entrySet().iterator().next().getValue();
        assertEquals("child-name", roundTripped.getTokenMap().getPhrase(roundTrippedChildNameValue).getValue());

        final Edge roundTrippedEdge = roundTripped.getEdges().get(edgeId);
        assertEquals(childId, roundTrippedEdge.getChild());
        assertEquals(parentId, roundTrippedEdge.getParent());
    }

    @Test
    public void throwsWhenGraphIsNullInToJSON() {
        assertThrows(IllegalArgumentException.class, () -> new Convert().toJSON(null));
    }

    @Test
    public void throwsWhenNodeIsNullInFromJSON() {
        assertThrows(IllegalArgumentException.class, () -> new Convert().fromJSON(null));
    }

    @Test
    public void throwsWhenTokenMapIsMissingInFromJSON() {
        final ObjectNode node = new ObjectMapper().createObjectNode();
        node.putObject("vertices");
        node.putObject("edges");

        assertThrows(IllegalArgumentException.class, () -> new Convert().fromJSON(node));
    }

    @Test
    public void throwsWhenVerticesIsMissingInFromJSON() {
        final ObjectNode node = new ObjectMapper().createObjectNode();
        node.putObject("tokenMap");
        node.putObject("edges");

        assertThrows(IllegalArgumentException.class, () -> new Convert().fromJSON(node));
    }

    @Test
    public void throwsWhenEdgesIsMissingInFromJSON() {
        final ObjectNode node = new ObjectMapper().createObjectNode();
        node.putObject("tokenMap");
        node.putObject("vertices");

        assertThrows(IllegalArgumentException.class, () -> new Convert().fromJSON(node));
    }

    @Test
    public void throwsWhenTokenMapIsNotAnObjectInFromJSON() {
        final ObjectNode node = new ObjectMapper().createObjectNode();
        node.put("tokenMap", "not-an-object");
        node.putObject("vertices");
        node.putObject("edges");

        assertThrows(IllegalArgumentException.class, () -> new Convert().fromJSON(node));
    }

    @Test
    public void throwsWhenEdgeIsMissingChildInFromJSON() {
        final ObjectMapper mapper = new ObjectMapper();

        final ObjectNode node = mapper.createObjectNode();
        node.putObject("tokenMap");
        node.putObject("vertices");
        final ObjectNode edgesNode = node.putObject("edges");
        final ObjectNode edgeNode = edgesNode.putObject("0");
        edgeNode.put("parent", 1);
        edgeNode.putObject("annotations");

        assertThrows(IllegalArgumentException.class, () -> new Convert().fromJSON(node));
    }

    @Test
    public void throwsWhenEdgeIsMissingParentInFromJSON() {
        final ObjectMapper mapper = new ObjectMapper();

        final ObjectNode node = mapper.createObjectNode();
        node.putObject("tokenMap");
        node.putObject("vertices");
        final ObjectNode edgesNode = node.putObject("edges");
        final ObjectNode edgeNode = edgesNode.putObject("0");
        edgeNode.put("child", 1);
        edgeNode.putObject("annotations");

        assertThrows(IllegalArgumentException.class, () -> new Convert().fromJSON(node));
    }

    @Test
    public void throwsWhenVertexIsMissingAnnotationsInFromJSON() {
        final ObjectMapper mapper = new ObjectMapper();

        final ObjectNode node = mapper.createObjectNode();
        node.putObject("tokenMap");
        final ObjectNode verticesNode = node.putObject("vertices");
        verticesNode.putObject("0");
        node.putObject("edges");

        assertThrows(IllegalArgumentException.class, () -> new Convert().fromJSON(node));
    }

}
