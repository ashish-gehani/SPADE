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
import com.fasterxml.jackson.databind.node.ObjectNode;

import spade.utility.graph.codec.Type;
import spade.utility.graph.codec.tokenize.token.Map;
import spade.utility.graph.codec.tokenize.token.Token;

public class Graph extends spade.utility.graph.codec.Graph {

    private static final String keyGraph = "graph";

    private final Map tokenMap = new Map();

    private final java.util.Map<Token, Vertex> vertices = new java.util.HashMap<>();

    private final java.util.Map<Token, Edge> edges = new java.util.HashMap<>();

    public Graph() {
        super(Type.TOKENIZE);
    }

    public void putVertex(final Token token, final Vertex vertex) {
        vertices.put(token, vertex);
    }

    public void putEdge(final Token token, final Edge edge) {
        edges.put(token, edge);
    }

    public Map getTokenMap() {
        return tokenMap;
    }

    public java.util.Map<Token, Vertex> getVertices() {
        return vertices;
    }

    public java.util.Map<Token, Edge> getEdges() {
        return edges;
    }

    @Override
    public ObjectNode toJSON() {
        final ObjectNode node = newNode();
        node.set(keyGraph, new Convert().toJSON(this));
        return node;
    }

    public static Graph fromJSON(final ObjectNode node) {
        final JsonNode graphNode = node.get(keyGraph);
        if (graphNode == null || !graphNode.isObject()) {
            throw new IllegalArgumentException("Missing/invalid '" + keyGraph + "' in: " + node);
        }
        return new Convert().fromJSON((ObjectNode) graphNode);
    }

}
