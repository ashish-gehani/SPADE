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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import spade.query.transport.json.data.Data;
import spade.query.transport.json.data.Type;

public class Graph extends Data {

    private static final String keyGraph = "graph";

    private final spade.utility.graph.codec.Graph graph;

    public Graph(final spade.utility.graph.codec.Graph graph) {
        super(Type.GRAPH);
        if (graph == null) {
            throw new IllegalArgumentException("NULL graph");
        }
        this.graph = graph;
    }

    public spade.utility.graph.codec.Graph getGraph() {
        return graph;
    }

    @Override
    public ObjectNode toJSON() {
        final ObjectNode node = newNode();
        node.set(keyGraph, graph.toJSON());
        return node;
    }

    public static Data fromJSON(final ObjectNode node) {
        if (node == null) {
            throw new IllegalArgumentException("NULL node");
        }

        final JsonNode graphNode = node.get(keyGraph);
        if (graphNode == null || !graphNode.isObject()) {
            throw new IllegalArgumentException("Missing/invalid '" + keyGraph + "' in: " + node);
        }

        final spade.utility.graph.codec.Graph decodedGraph =
                spade.utility.graph.codec.Graph.fromJSON((ObjectNode) graphNode);
        return new Graph(decodedGraph);
    }

}
