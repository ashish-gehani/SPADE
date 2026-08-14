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
import com.fasterxml.jackson.databind.node.ObjectNode;

import spade.utility.graph.codec.Type;

public class Graph extends spade.utility.graph.codec.Graph {

    private static final String keyGraph = "graph";

    private final spade.core.Graph graph;

    public Graph(final spade.core.Graph graph) {
        super(Type.UNCOMPRESSED);
        if (graph == null) {
            throw new IllegalArgumentException("NULL graph");
        }
        this.graph = graph;
    }

    public spade.core.Graph getGraph() {
        return graph;
    }

    @Override
    public ObjectNode toJSON() {
        final ObjectNode node = newNode();
        node.set(keyGraph, new Convert().toJSON(graph));
        return node;
    }

    public static Graph fromJSON(final ObjectNode node) {
        final JsonNode graphNode = node.get(keyGraph);
        if (graphNode == null || !graphNode.isObject()) {
            throw new IllegalArgumentException("Missing/invalid '" + keyGraph + "' in: " + node);
        }
        return new Graph(new Convert().fromJSON((ObjectNode) graphNode));
    }

}
