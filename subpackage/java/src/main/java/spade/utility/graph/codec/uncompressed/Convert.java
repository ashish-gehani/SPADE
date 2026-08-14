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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Vertex;

/*

 toJSON produces, and fromJSON expects, an object of this shape:

 {
   "vertices": [
     {
       "id": "<id>",
       "type": "<type>",
       "annotations": { "<name>": "<value>", ... }
     },
     ...
   ],
   "edges": [
     {
       "from": "<child id>",
       "to": "<parent id>",
       "type": "<type>",
       "annotations": { "<name>": "<value>", ... }
     },
     ...
   ]
 }

 Unlike the tokenize codec, no string here is ever replaced by a reference - every id,
 type, and annotation is stored as-is.

*/
public class Convert {

    private static final String keyVertices = "vertices";
    private static final String keyEdges = "edges";

    private static final ObjectMapper mapper = new ObjectMapper();

    public ObjectNode toJSON(final spade.core.Graph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("NULL graph");
        }

        final ObjectNode root = mapper.createObjectNode();
        root.set(keyVertices, toJSONVertices(graph));
        root.set(keyEdges, toJSONEdges(graph));
        return root;
    }

    private ArrayNode toJSONVertices(final spade.core.Graph graph) {
        final ArrayNode node = mapper.createArrayNode();
        for (final AbstractVertex vertex : graph.vertexSet()) {
            if (vertex == null) {
                throw new IllegalArgumentException("NULL vertex in graph");
            }
            node.add(toJSONVertex(vertex));
        }
        return node;
    }

    private ArrayNode toJSONEdges(final spade.core.Graph graph) {
        final ArrayNode node = mapper.createArrayNode();
        for (final AbstractEdge edge : graph.edgeSet()) {
            if (edge == null) {
                throw new IllegalArgumentException("NULL edge in graph");
            }
            node.add(toJSONEdge(edge));
        }
        return node;
    }

    private ObjectNode toJSONVertex(final AbstractVertex vertex) {
        final ObjectNode node = mapper.createObjectNode();
        node.put(AbstractVertex.idKey, vertex.getIdentifierForExport());
        node.put(AbstractVertex.typeKey, vertex.type());
        node.set(AbstractVertex.annotationsKey, toJSONAnnotations(vertex.getCopyOfAnnotations()));
        return node;
    }

    private ObjectNode toJSONEdge(final AbstractEdge edge) {
        if (edge.getChildVertex() == null) {
            throw new IllegalArgumentException("NULL child vertex in edge: " + edge);
        } else if (edge.getParentVertex() == null) {
            throw new IllegalArgumentException("NULL parent vertex in edge: " + edge);
        }

        final ObjectNode node = mapper.createObjectNode();
        node.put(AbstractEdge.idKey, edge.getIdentifierForExport());
        node.put(AbstractEdge.fromIdKey, edge.getChildVertex().getIdentifierForExport());
        node.put(AbstractEdge.toIdKey, edge.getParentVertex().getIdentifierForExport());
        node.put(AbstractEdge.typeKey, edge.type());
        node.set(AbstractEdge.annotationsKey, toJSONAnnotations(edge.getCopyOfAnnotations()));
        return node;
    }

    private ObjectNode toJSONAnnotations(final java.util.Map<String, String> annotations) {
        final ObjectNode node = mapper.createObjectNode();
        for (final java.util.Map.Entry<String, String> annotation : annotations.entrySet()) {
            node.put(annotation.getKey(), annotation.getValue());
        }
        return node;
    }

    public spade.core.Graph fromJSON(final ObjectNode node) {
        if (node == null) {
            throw new IllegalArgumentException("NULL node");
        }

        final spade.core.Graph graph = new spade.core.Graph();
        final java.util.Map<String, AbstractVertex> vertices = new java.util.HashMap<>();
        fromJSONVertices(arrayNode(node, keyVertices), graph, vertices);
        fromJSONEdges(arrayNode(node, keyEdges), graph, vertices);
        return graph;
    }

    private void fromJSONVertices(
            final ArrayNode node, final spade.core.Graph graph,
            final java.util.Map<String, AbstractVertex> vertices) {
        for (final JsonNode entry : node) {
            final AbstractVertex vertex = fromJSONVertex(objectNode(entry, "vertex"));
            vertices.put(vertex.getIdentifierForExport(), vertex);
            graph.putVertex(vertex);
        }
    }

    private void fromJSONEdges(
            final ArrayNode node, final spade.core.Graph graph,
            final java.util.Map<String, AbstractVertex> vertices) {
        for (final JsonNode entry : node) {
            graph.putEdge(fromJSONEdge(objectNode(entry, "edge"), vertices));
        }
    }

    // Materializing a vertex always rebuilds it as a reference vertex fixed to the
    // persisted identifier, regardless of whether the original was a content or
    // reference vertex - see AbstractVertex.md. Any 'id' annotation that was present
    // is restored below along with the rest of the annotations, same as it would
    // already be part of the original annotation map.
    private AbstractVertex fromJSONVertex(final ObjectNode node) {
        final JsonNode idNode = node.get(AbstractVertex.idKey);
        if (idNode == null || !idNode.isTextual()) {
            throw new IllegalArgumentException("Missing/invalid '" + AbstractVertex.idKey + "' in: " + node);
        }

        final AbstractVertex vertex = new Vertex(idNode.asText());
        for (final java.util.Map.Entry<String, String> annotation :
                fromJSONAnnotations(objectNode(node, AbstractVertex.annotationsKey)).entrySet()) {
            vertex.addAnnotation(annotation.getKey(), annotation.getValue());
        }
        return vertex;
    }

    // Materializing an edge, like a vertex, always rebuilds it as a reference fixed to
    // the persisted identifier rather than one whose hash is (re)derived from content -
    // see AbstractVertex.md and the identical convention in tokenize's Decompress.
    private AbstractEdge fromJSONEdge(final ObjectNode node, final java.util.Map<String, AbstractVertex> vertices) {
        final JsonNode idNode = node.get(AbstractEdge.idKey);
        final JsonNode fromNode = node.get(AbstractEdge.fromIdKey);
        final JsonNode toNode = node.get(AbstractEdge.toIdKey);
        if (idNode == null || !idNode.isTextual()) {
            throw new IllegalArgumentException("Missing/invalid '" + AbstractEdge.idKey + "' in: " + node);
        } else if (fromNode == null || !fromNode.isTextual()) {
            throw new IllegalArgumentException("Missing/invalid '" + AbstractEdge.fromIdKey + "' in: " + node);
        } else if (toNode == null || !toNode.isTextual()) {
            throw new IllegalArgumentException("Missing/invalid '" + AbstractEdge.toIdKey + "' in: " + node);
        }

        final AbstractVertex child = vertices.get(fromNode.asText());
        final AbstractVertex parent = vertices.get(toNode.asText());
        if (child == null) {
            throw new IllegalArgumentException("Unresolved child vertex in edge: " + node);
        } else if (parent == null) {
            throw new IllegalArgumentException("Unresolved parent vertex in edge: " + node);
        }

        final AbstractEdge edge = new Edge(idNode.asText(), child, parent);
        for (final java.util.Map.Entry<String, String> annotation :
                fromJSONAnnotations(objectNode(node, AbstractEdge.annotationsKey)).entrySet()) {
            edge.addAnnotation(annotation.getKey(), annotation.getValue());
        }
        return edge;
    }

    private java.util.Map<String, String> fromJSONAnnotations(final ObjectNode node) {
        final java.util.Map<String, String> annotations = new java.util.HashMap<>();
        final java.util.Iterator<java.util.Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            final java.util.Map.Entry<String, JsonNode> field = fields.next();
            annotations.put(field.getKey(), field.getValue().asText());
        }
        return annotations;
    }

    // Centralizes the type/presence check every raw '(ArrayNode/ObjectNode) ...' cast in
    // this class used to skip, since the JSON being decoded is untrusted input, not just
    // this class's own toJSON output.
    private ObjectNode objectNode(final JsonNode value, final String context) {
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException("Missing/invalid '" + context + "' object");
        }
        return (ObjectNode) value;
    }

    private ObjectNode objectNode(final ObjectNode parent, final String key) {
        return objectNode(parent.get(key), key);
    }

    private ArrayNode arrayNode(final ObjectNode parent, final String key) {
        final JsonNode value = parent.get(key);
        if (value == null || !value.isArray()) {
            throw new IllegalArgumentException("Missing/invalid '" + key + "' array");
        }
        return (ArrayNode) value;
    }

}
