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
package spade.query.transport.json.data.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import spade.query.transport.json.data.Type;

public class TextTest {

    @Test
    public void constructorThrowsWhenValueIsNull() {
        assertThrows(IllegalArgumentException.class, () -> new Text(null));
    }

    @Test
    public void toJSONWrapsTypeAndValueArray() {
        final Text text = new Text(Arrays.asList("line1", "line2"));

        final ObjectNode node = text.toJSON();

        assertEquals(Type.TEXT.name(), node.get("type").asText());
        assertTrue(node.get("value").isArray());
        assertEquals(2, node.get("value").size());
        assertEquals("line1", node.get("value").get(0).asText());
        assertEquals("line2", node.get("value").get(1).asText());
    }

    @Test
    public void fromJSONRoundTripsToJSONOutput() {
        final Text text = new Text(Arrays.asList("line1", "line2"));

        final Text roundTripped = (Text) Text.fromJSON(text.toJSON());

        assertEquals(text.getValue(), roundTripped.getValue());
    }

    @Test
    public void fromJSONRoundTripsEmptyValue() {
        final Text text = new Text(Collections.emptyList());

        final Text roundTripped = (Text) Text.fromJSON(text.toJSON());

        assertEquals(0, roundTripped.getValue().size());
    }

    @Test
    public void fromJSONThrowsWhenNodeIsNull() {
        assertThrows(IllegalArgumentException.class, () -> Text.fromJSON(null));
    }

    @Test
    public void fromJSONThrowsWhenValueIsMissing() {
        final ObjectNode node = new ObjectMapper().createObjectNode();
        node.put("type", Type.TEXT.name());

        assertThrows(IllegalArgumentException.class, () -> Text.fromJSON(node));
    }

    @Test
    public void fromJSONThrowsWhenValueIsNotAnArray() {
        final ObjectNode node = new ObjectMapper().createObjectNode();
        node.put("type", Type.TEXT.name());
        node.put("value", "not-an-array");

        assertThrows(IllegalArgumentException.class, () -> Text.fromJSON(node));
    }

    @Test
    public void fromJSONThrowsWhenValueEntryIsNotTextual() {
        final ObjectMapper mapper = new ObjectMapper();

        final ObjectNode node = mapper.createObjectNode();
        node.put("type", Type.TEXT.name());
        final ArrayNode valueNode = node.putArray("value");
        valueNode.add(1);

        assertThrows(IllegalArgumentException.class, () -> Text.fromJSON(node));
    }

}
