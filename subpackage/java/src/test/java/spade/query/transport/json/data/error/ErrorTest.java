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
package spade.query.transport.json.data.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import spade.query.transport.json.data.Type;

public class ErrorTest {

    @Test
    public void throwableConstructorThrowsWhenThrowableIsNull() {
        assertThrows(IllegalArgumentException.class, () -> new Error((Throwable) null));
    }

    @Test
    public void listConstructorThrowsWhenValueIsNull() {
        assertThrows(IllegalArgumentException.class, () -> new Error((List<String>) null));
    }

    @Test
    public void throwableConstructorCollectsMessageAndTopStackLine() {
        final RuntimeException thrown = new RuntimeException("boom");

        final Error error = new Error(thrown);

        assertEquals(1, error.getValue().size());
        assertEquals(thrown.toString() + " at " + thrown.getStackTrace()[0].toString(), error.getValue().get(0));
    }

    @Test
    public void throwableConstructorWalksCauseChain() {
        final RuntimeException cause = new RuntimeException("root cause");
        final RuntimeException outer = new RuntimeException("outer failure", cause);

        final Error error = new Error(outer);

        assertEquals(2, error.getValue().size());
        assertEquals(outer.toString() + " at " + outer.getStackTrace()[0].toString(), error.getValue().get(0));
        assertEquals(cause.toString() + " at " + cause.getStackTrace()[0].toString(), error.getValue().get(1));
    }

    @Test
    public void toJSONWrapsTypeAndValueArray() {
        final Error error = new Error(Arrays.asList("line1", "line2"));

        final ObjectNode node = error.toJSON();

        assertEquals(Type.ERROR.name(), node.get("type").asText());
        assertTrue(node.get("value").isArray());
        assertEquals(2, node.get("value").size());
        assertEquals("line1", node.get("value").get(0).asText());
        assertEquals("line2", node.get("value").get(1).asText());
    }

    @Test
    public void fromJSONRoundTripsToJSONOutput() {
        final Error error = new Error(Arrays.asList("line1", "line2"));

        final ObjectNode node = error.toJSON();
        final Error roundTripped = (Error) Error.fromJSON(node);

        assertEquals(error.getValue(), roundTripped.getValue());
    }

    @Test
    public void fromJSONRoundTripsEmptyValue() {
        final Error error = new Error(Collections.emptyList());

        final Error roundTripped = (Error) Error.fromJSON(error.toJSON());

        assertEquals(0, roundTripped.getValue().size());
    }

    @Test
    public void fromJSONThrowsWhenNodeIsNull() {
        assertThrows(IllegalArgumentException.class, () -> Error.fromJSON(null));
    }

    @Test
    public void fromJSONThrowsWhenValueIsMissing() {
        final ObjectNode node = new ObjectMapper().createObjectNode();
        node.put("type", Type.ERROR.name());

        assertThrows(IllegalArgumentException.class, () -> Error.fromJSON(node));
    }

    @Test
    public void fromJSONThrowsWhenValueIsNotAnArray() {
        final ObjectNode node = new ObjectMapper().createObjectNode();
        node.put("type", Type.ERROR.name());
        node.put("value", "not-an-array");

        assertThrows(IllegalArgumentException.class, () -> Error.fromJSON(node));
    }

    @Test
    public void fromJSONThrowsWhenValueEntryIsNotTextual() {
        final ObjectMapper mapper = new ObjectMapper();

        final ObjectNode node = mapper.createObjectNode();
        node.put("type", Type.ERROR.name());
        final ArrayNode valueNode = node.putArray("value");
        valueNode.add(1);

        assertThrows(IllegalArgumentException.class, () -> Error.fromJSON(node));
    }

}
