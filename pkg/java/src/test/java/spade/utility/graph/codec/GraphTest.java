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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class GraphTest {

    @Test
    public void fromJSONThrowsWhenNodeIsNull() {
        assertThrows(IllegalArgumentException.class, () -> Graph.fromJSON(null));
    }

    @Test
    public void fromJSONThrowsWhenTypeIsMissing() {
        final ObjectNode node = new ObjectMapper().createObjectNode();

        assertThrows(IllegalArgumentException.class, () -> Graph.fromJSON(node));
    }

    @Test
    public void fromJSONThrowsWhenTypeIsNotTextual() {
        final ObjectNode node = new ObjectMapper().createObjectNode();
        node.put("type", 1);

        assertThrows(IllegalArgumentException.class, () -> Graph.fromJSON(node));
    }

    @Test
    public void fromJSONThrowsWhenTypeIsUnknown() {
        final ObjectNode node = new ObjectMapper().createObjectNode();
        node.put("type", "NOT_A_REAL_TYPE");

        assertThrows(IllegalArgumentException.class, () -> Graph.fromJSON(node));
    }

    @Test
    public void fromJSONDispatchesToTokenizeCodec() {
        final spade.utility.graph.codec.tokenize.Graph graph = new spade.utility.graph.codec.tokenize.Graph();
        final ObjectNode node = graph.toJSON();

        final Graph decoded = Graph.fromJSON(node);

        assertEquals(Type.TOKENIZE, decoded.getType());
        assertTrue(decoded instanceof spade.utility.graph.codec.tokenize.Graph);
    }

}
