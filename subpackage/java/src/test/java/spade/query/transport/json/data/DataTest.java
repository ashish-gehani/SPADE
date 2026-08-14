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
package spade.query.transport.json.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class DataTest {

    @Test
    public void fromJSONThrowsWhenNodeIsNull() {
        assertThrows(IllegalArgumentException.class, () -> Data.fromJSON(null));
    }

    @Test
    public void fromJSONThrowsWhenTypeIsMissing() {
        final ObjectNode node = new ObjectMapper().createObjectNode();

        assertThrows(IllegalArgumentException.class, () -> Data.fromJSON(node));
    }

    @Test
    public void fromJSONThrowsWhenTypeIsNotTextual() {
        final ObjectNode node = new ObjectMapper().createObjectNode();
        node.put("type", 1);

        assertThrows(IllegalArgumentException.class, () -> Data.fromJSON(node));
    }

    @Test
    public void fromJSONThrowsWhenTypeIsUnknown() {
        final ObjectNode node = new ObjectMapper().createObjectNode();
        node.put("type", "NOT_A_REAL_TYPE");

        assertThrows(IllegalArgumentException.class, () -> Data.fromJSON(node));
    }

    @Test
    public void fromJSONDispatchesToText() {
        final spade.query.transport.json.data.text.Text text =
                new spade.query.transport.json.data.text.Text(Collections.singletonList("hello"));
        final ObjectNode node = text.toJSON();

        final Data decoded = Data.fromJSON(node);

        assertEquals(Type.TEXT, decoded.getType());
        assertTrue(decoded instanceof spade.query.transport.json.data.text.Text);
        assertEquals(Collections.singletonList("hello"), ((spade.query.transport.json.data.text.Text) decoded).getValue());
    }

    @Test
    public void fromJSONDispatchesToGraph() {
        final spade.utility.graph.codec.uncompressed.Graph codecGraph =
                new spade.utility.graph.codec.uncompressed.Graph(new spade.core.Graph());
        final spade.query.transport.json.data.graph.Graph graph =
                new spade.query.transport.json.data.graph.Graph(codecGraph);
        final ObjectNode node = graph.toJSON();

        final Data decoded = Data.fromJSON(node);

        assertEquals(Type.GRAPH, decoded.getType());
        assertTrue(decoded instanceof spade.query.transport.json.data.graph.Graph);
    }

}
