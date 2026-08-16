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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;

public class ConvertTest {

    @Test
    public void producesExpectedTopLevelKeys() {
        final spade.core.Graph graph = new spade.core.Graph();
        final ObjectNode node = new Convert().toJSON(graph);

        assertTrue(node.has("vertices"));
        assertTrue(node.has("edges"));
    }

    @Test
    public void roundTripsEmptyGraph() {
        final spade.core.Graph graph = new spade.core.Graph();

        final Convert convert = new Convert();
        final ObjectNode node = convert.toJSON(graph);

        assertEquals(0, node.get("vertices").size());
        assertEquals(0, node.get("edges").size());

        final spade.core.Graph roundTripped = convert.fromJSON(node);

        assertEquals(0, roundTripped.vertexSet().size());
        assertEquals(0, roundTripped.edgeSet().size());
    }

    @Test
    public void roundTripsVerticesEdgesAndAnnotations() {
        final AbstractVertex child = new spade.core.Vertex();
        child.addAnnotation("name", "child-name");
        final AbstractVertex parent = new spade.core.Vertex();
        parent.addAnnotation("name", "parent-name");

        final spade.core.Graph graph = new spade.core.Graph();
        graph.putVertex(child);
        graph.putVertex(parent);
        graph.putEdge(new spade.core.Edge(child, parent));

        final Convert convert = new Convert();
        final ObjectNode node = convert.toJSON(graph);
        final spade.core.Graph roundTripped = convert.fromJSON(node);

        assertEquals(2, roundTripped.vertexSet().size());
        assertEquals(1, roundTripped.edgeSet().size());

        final AbstractEdge roundTrippedEdge = roundTripped.edgeSet().iterator().next();
        assertEquals(child.getIdentifierForExport(), roundTrippedEdge.getChildVertex().getIdentifierForExport());
        assertEquals(parent.getIdentifierForExport(), roundTrippedEdge.getParentVertex().getIdentifierForExport());
    }

    @Test
    public void preservesAnnotationsThroughRoundTrip() {
        final AbstractVertex vertex = new spade.core.Vertex();
        vertex.addAnnotation("name", "only-vertex");

        final spade.core.Graph graph = new spade.core.Graph();
        graph.putVertex(vertex);

        final Convert convert = new Convert();
        final spade.core.Graph roundTripped = convert.fromJSON(convert.toJSON(graph));

        final AbstractVertex roundTrippedVertex = roundTripped.vertexSet().iterator().next();
        assertEquals("only-vertex", roundTrippedVertex.getAnnotation("name"));
    }

    @Test
    public void preservesEdgeIdentifierThroughRoundTrip() {
        final AbstractVertex child = new spade.core.Vertex();
        child.addAnnotation("name", "child");
        final AbstractVertex parent = new spade.core.Vertex();
        parent.addAnnotation("name", "parent");
        final AbstractEdge edge = new spade.core.Edge(child, parent);

        final spade.core.Graph graph = new spade.core.Graph();
        graph.putVertex(child);
        graph.putVertex(parent);
        graph.putEdge(edge);

        final Convert convert = new Convert();
        final spade.core.Graph roundTripped = convert.fromJSON(convert.toJSON(graph));

        final AbstractEdge roundTrippedEdge = roundTripped.edgeSet().iterator().next();
        assertEquals(edge.getIdentifierForExport(), roundTrippedEdge.getIdentifierForExport());
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
    public void throwsWhenVerticesIsMissingInFromJSON() {
        final ObjectNode node = new ObjectMapper().createObjectNode();
        node.putArray("edges");

        assertThrows(IllegalArgumentException.class, () -> new Convert().fromJSON(node));
    }

    @Test
    public void throwsWhenEdgesIsMissingInFromJSON() {
        final ObjectNode node = new ObjectMapper().createObjectNode();
        node.putArray("vertices");

        assertThrows(IllegalArgumentException.class, () -> new Convert().fromJSON(node));
    }

    @Test
    public void throwsWhenVerticesIsNotAnArrayInFromJSON() {
        final ObjectNode node = new ObjectMapper().createObjectNode();
        node.put("vertices", "not-an-array");
        node.putArray("edges");

        assertThrows(IllegalArgumentException.class, () -> new Convert().fromJSON(node));
    }

    @Test
    public void throwsWhenVertexIsMissingIdInFromJSON() {
        final ObjectMapper mapper = new ObjectMapper();

        final ObjectNode node = mapper.createObjectNode();
        final ArrayNode verticesNode = node.putArray("vertices");
        final ObjectNode vertexNode = verticesNode.addObject();
        vertexNode.put("type", "vertex");
        vertexNode.putObject("annotations");
        node.putArray("edges");

        assertThrows(IllegalArgumentException.class, () -> new Convert().fromJSON(node));
    }

    @Test
    public void throwsWhenVertexIsMissingAnnotationsInFromJSON() {
        final ObjectMapper mapper = new ObjectMapper();

        final ObjectNode node = mapper.createObjectNode();
        final ArrayNode verticesNode = node.putArray("vertices");
        final ObjectNode vertexNode = verticesNode.addObject();
        vertexNode.put("id", "v1");
        node.putArray("edges");

        assertThrows(IllegalArgumentException.class, () -> new Convert().fromJSON(node));
    }

    @Test
    public void throwsWhenEdgeIsMissingIdInFromJSON() {
        final ObjectMapper mapper = new ObjectMapper();

        final ObjectNode node = mapper.createObjectNode();
        node.putArray("vertices");
        final ArrayNode edgesNode = node.putArray("edges");
        final ObjectNode edgeNode = edgesNode.addObject();
        edgeNode.put("from", "child");
        edgeNode.put("to", "parent");
        edgeNode.putObject("annotations");

        assertThrows(IllegalArgumentException.class, () -> new Convert().fromJSON(node));
    }

    @Test
    public void throwsWhenEdgeIsMissingFromInFromJSON() {
        final ObjectMapper mapper = new ObjectMapper();

        final ObjectNode node = mapper.createObjectNode();
        node.putArray("vertices");
        final ArrayNode edgesNode = node.putArray("edges");
        final ObjectNode edgeNode = edgesNode.addObject();
        edgeNode.put("id", "e1");
        edgeNode.put("to", "parent");
        edgeNode.putObject("annotations");

        assertThrows(IllegalArgumentException.class, () -> new Convert().fromJSON(node));
    }

    @Test
    public void throwsWhenEdgeIsMissingToInFromJSON() {
        final ObjectMapper mapper = new ObjectMapper();

        final ObjectNode node = mapper.createObjectNode();
        node.putArray("vertices");
        final ArrayNode edgesNode = node.putArray("edges");
        final ObjectNode edgeNode = edgesNode.addObject();
        edgeNode.put("id", "e1");
        edgeNode.put("from", "child");
        edgeNode.putObject("annotations");

        assertThrows(IllegalArgumentException.class, () -> new Convert().fromJSON(node));
    }

    @Test
    public void throwsWhenEdgeIsMissingAnnotationsInFromJSON() {
        final ObjectMapper mapper = new ObjectMapper();

        final ObjectNode node = mapper.createObjectNode();
        node.putArray("vertices");
        final ArrayNode edgesNode = node.putArray("edges");
        final ObjectNode edgeNode = edgesNode.addObject();
        edgeNode.put("id", "e1");
        edgeNode.put("from", "child");
        edgeNode.put("to", "parent");

        assertThrows(IllegalArgumentException.class, () -> new Convert().fromJSON(node));
    }

    @Test
    public void throwsWhenChildVertexIsUnresolvedInFromJSON() {
        final ObjectMapper mapper = new ObjectMapper();

        final ObjectNode node = mapper.createObjectNode();
        final ArrayNode verticesNode = node.putArray("vertices");
        final ObjectNode parentNode = verticesNode.addObject();
        parentNode.put("id", "parent");
        parentNode.put("type", "vertex");
        parentNode.putObject("annotations");

        final ArrayNode edgesNode = node.putArray("edges");
        final ObjectNode edgeNode = edgesNode.addObject();
        edgeNode.put("id", "e1");
        edgeNode.put("from", "missing-child");
        edgeNode.put("to", "parent");
        edgeNode.putObject("annotations");

        assertThrows(IllegalArgumentException.class, () -> new Convert().fromJSON(node));
    }

    @Test
    public void throwsWhenParentVertexIsUnresolvedInFromJSON() {
        final ObjectMapper mapper = new ObjectMapper();

        final ObjectNode node = mapper.createObjectNode();
        final ArrayNode verticesNode = node.putArray("vertices");
        final ObjectNode childNode = verticesNode.addObject();
        childNode.put("id", "child");
        childNode.put("type", "vertex");
        childNode.putObject("annotations");

        final ArrayNode edgesNode = node.putArray("edges");
        final ObjectNode edgeNode = edgesNode.addObject();
        edgeNode.put("id", "e1");
        edgeNode.put("from", "child");
        edgeNode.put("to", "missing-parent");
        edgeNode.putObject("annotations");

        assertThrows(IllegalArgumentException.class, () -> new Convert().fromJSON(node));
    }

}
