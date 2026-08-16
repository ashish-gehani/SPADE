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

package spade.utility.graph.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public abstract class Graph {

    private static final String keyType = "type";

    protected static final ObjectMapper mapper = new ObjectMapper();

    private final Type type;

    public Graph(final Type type) {
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    public abstract ObjectNode toJSON();

    protected final ObjectNode newNode() {
        final ObjectNode node = mapper.createObjectNode();
        node.put(keyType, type.name());
        return node;
    }

    public static Graph fromJSON(final ObjectNode node) {
        if (node == null) {
            throw new IllegalArgumentException("NULL node");
        }

        final JsonNode typeNode = node.get(keyType);
        if (typeNode == null || !typeNode.isTextual()) {
            throw new IllegalArgumentException("Missing/invalid '" + keyType + "' in: " + node);
        }

        final Type type;
        try {
            type = Type.valueOf(typeNode.asText());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown codec type: " + typeNode.asText(), e);
        }

        switch (type) {
            case TOKENIZE:
                return spade.utility.graph.codec.tokenize.Graph.fromJSON(node);
            case UNCOMPRESSED:
                return spade.utility.graph.codec.uncompressed.Graph.fromJSON(node);
            default:
                throw new IllegalArgumentException("Unknown codec type: " + type);
        }
    }

}
