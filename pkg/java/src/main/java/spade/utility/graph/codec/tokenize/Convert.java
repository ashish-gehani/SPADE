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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import spade.utility.graph.codec.tokenize.token.Map;
import spade.utility.graph.codec.tokenize.token.Phrase;
import spade.utility.graph.codec.tokenize.token.Token;

/*

 toJSON produces, and fromJSON expects, an object of this shape:

 {
   "tokenMap": {
     "<token>": "<phrase>",       // one entry per token referenced anywhere below
     ...
   },
   "vertices": {
     "<token>": {                 // key is the vertex's own token
       "annotations": {
         "<token>": <token>,      // annotation key token -> annotation value token
         ...
       }
     },
     ...
   },
   "edges": {
     "<token>": {                 // key is the edge's own token
       "child": <token>,
       "parent": <token>,
       "annotations": {
         "<token>": <token>,
         ...
       }
     },
     ...
   }
 }

 A "token" as an object key is always the token's numeric value written as a JSON
 string (JSON object keys must be strings); a "token" as a value is that same numeric
 value written as a JSON number.

*/
public class Convert {

    private static final String keyTokenMap = "tokenMap";
    private static final String keyVertices = "vertices";
    private static final String keyEdges = "edges";
    private static final String keyAnnotations = "annotations";
    private static final String keyChild = "child";
    private static final String keyParent = "parent";

    private static final ObjectMapper mapper = new ObjectMapper();

    public ObjectNode toJSON(final Graph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("NULL graph");
        }

        final ObjectNode root = mapper.createObjectNode();
        root.set(keyTokenMap, toJSONTokenMap(graph.getTokenMap()));
        root.set(keyVertices, toJSONVertices(graph));
        root.set(keyEdges, toJSONEdges(graph));
        return root;
    }

    private ObjectNode toJSONVertices(final Graph graph) {
        final ObjectNode node = mapper.createObjectNode();
        for (final java.util.Map.Entry<Token, Vertex> entry : graph.getVertices().entrySet()) {
            node.set(String.valueOf(entry.getKey().getValue()), toJSONVertex(entry.getValue()));
        }
        return node;
    }

    private ObjectNode toJSONEdges(final Graph graph) {
        final ObjectNode node = mapper.createObjectNode();
        for (final java.util.Map.Entry<Token, Edge> entry : graph.getEdges().entrySet()) {
            node.set(String.valueOf(entry.getKey().getValue()), toJSONEdge(entry.getValue()));
        }
        return node;
    }

    private ObjectNode toJSONVertex(final Vertex vertex) {
        final ObjectNode node = mapper.createObjectNode();
        node.set(keyAnnotations, toJSONAnnotations(vertex.getAnnotations()));
        return node;
    }

    private ObjectNode toJSONEdge(final Edge edge) {
        final ObjectNode node = mapper.createObjectNode();
        node.put(keyChild, edge.getChild().getValue());
        node.put(keyParent, edge.getParent().getValue());
        node.set(keyAnnotations, toJSONAnnotations(edge.getAnnotations()));
        return node;
    }

    private ObjectNode toJSONAnnotations(final AnnotationMap annotations) {
        final ObjectNode node = mapper.createObjectNode();
        for (final java.util.Map.Entry<Token, Token> annotation : annotations.entrySet()) {
            node.put(String.valueOf(annotation.getKey().getValue()), annotation.getValue().getValue());
        }
        return node;
    }

    private ObjectNode toJSONTokenMap(final Map tokenMap) {
        final ObjectNode node = mapper.createObjectNode();
        for (final java.util.Map.Entry<Token, Phrase> entry : tokenMap.entrySet()) {
            node.put(String.valueOf(entry.getKey().getValue()), entry.getValue().getValue());
        }
        return node;
    }

    private void fromJSONTokenMap(final ObjectNode node, final Map tokenMap) {
        final java.util.Iterator<java.util.Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            final java.util.Map.Entry<String, JsonNode> field = fields.next();
            tokenMap.putToken(new Token(Long.parseLong(field.getKey())), new Phrase(field.getValue().asText()));
        }
    }

    public Graph fromJSON(final ObjectNode node) {
        if (node == null) {
            throw new IllegalArgumentException("NULL node");
        }

        final Graph graph = new Graph();
        fromJSONTokenMap(objectNode(node, keyTokenMap), graph.getTokenMap());
        fromJSONVertices(objectNode(node, keyVertices), graph);
        fromJSONEdges(objectNode(node, keyEdges), graph);
        return graph;
    }

    private void fromJSONVertices(final ObjectNode node, final Graph graph) {
        final java.util.Iterator<java.util.Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            final java.util.Map.Entry<String, JsonNode> field = fields.next();
            final Token id = new Token(Long.parseLong(field.getKey()));
            graph.putVertex(id, fromJSONVertex(objectNode(field.getValue(), "vertex " + field.getKey())));
        }
    }

    private void fromJSONEdges(final ObjectNode node, final Graph graph) {
        final java.util.Iterator<java.util.Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            final java.util.Map.Entry<String, JsonNode> field = fields.next();
            final Token id = new Token(Long.parseLong(field.getKey()));
            graph.putEdge(id, fromJSONEdge(objectNode(field.getValue(), "edge " + field.getKey())));
        }
    }

    private Vertex fromJSONVertex(final ObjectNode node) {
        return new Vertex(fromJSONAnnotations(objectNode(node, keyAnnotations)));
    }

    private Edge fromJSONEdge(final ObjectNode node) {
        final JsonNode childNode = node.get(keyChild);
        final JsonNode parentNode = node.get(keyParent);
        if (childNode == null || !childNode.isNumber()) {
            throw new IllegalArgumentException("Missing/invalid '" + keyChild + "' in edge: " + node);
        } else if (parentNode == null || !parentNode.isNumber()) {
            throw new IllegalArgumentException("Missing/invalid '" + keyParent + "' in edge: " + node);
        }

        final Token child = new Token(childNode.asLong());
        final Token parent = new Token(parentNode.asLong());
        return new Edge(child, parent, fromJSONAnnotations(objectNode(node, keyAnnotations)));
    }

    // Centralizes the type/presence check every raw '(ObjectNode) ...' cast in this class
    // used to skip, since the JSON being decoded is untrusted input, not just this
    // class's own toJSON output.
    private ObjectNode objectNode(final JsonNode value, final String context) {
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException("Missing/invalid '" + context + "' object");
        }
        return (ObjectNode) value;
    }

    private ObjectNode objectNode(final ObjectNode parent, final String key) {
        return objectNode(parent.get(key), key);
    }

    private java.util.Map<Token, Token> fromJSONAnnotations(final ObjectNode node) {
        final java.util.Map<Token, Token> annotations = new java.util.HashMap<>();

        final java.util.Iterator<java.util.Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            final java.util.Map.Entry<String, JsonNode> field = fields.next();
            final Token key = new Token(Long.parseLong(field.getKey()));
            final Token value = new Token(field.getValue().asLong());
            annotations.put(key, value);
        }

        return annotations;
    }

}
