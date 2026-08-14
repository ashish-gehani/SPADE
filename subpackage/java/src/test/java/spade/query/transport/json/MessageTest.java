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
package spade.query.transport.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.ObjectNode;

import spade.query.transport.json.data.text.Text;

public class MessageTest {

    private static Message minimalMessage() {
        return Message.builder().build();
    }

    @Test
    public void builderSetsAllFields() {
        final Message subquery = minimalMessage();

        final Message message = Message.builder()
                .localHostName("local")
                .remoteHostName("remote")
                .query("SELECT *")
                .nonce("abc123")
                .data(new Text(Collections.singletonList("hello")))
                .remoteSubqueries(Collections.singletonList(subquery))
                .build();

        assertEquals("local", message.getLocalHostName());
        assertEquals("remote", message.getRemoteHostName());
        assertEquals("SELECT *", message.getQuery());
        assertEquals("abc123", message.getNonce());
        assertEquals(Collections.singletonList("hello"), ((Text) message.getData()).getValue());
        assertEquals(1, message.getRemoteSubqueries().size());
        assertEquals(subquery, message.getRemoteSubqueries().get(0));
    }

    @Test
    public void toJSONIncludesEveryKeyEvenWhenNull() {
        final ObjectNode node = minimalMessage().toJSON();

        assertTrue(node.has("localHostName"));
        assertTrue(node.get("localHostName").isNull());
        assertTrue(node.has("remoteHostName"));
        assertTrue(node.get("remoteHostName").isNull());
        assertTrue(node.has("query"));
        assertTrue(node.get("query").isNull());
        assertTrue(node.has("nonce"));
        assertTrue(node.get("nonce").isNull());
        assertTrue(node.has("data"));
        assertTrue(node.get("data").isNull());
        assertTrue(node.has("remoteSubqueries"));
        assertTrue(node.get("remoteSubqueries").isNull());
    }

    @Test
    public void fromJSONRoundTripsMessageWithNullOptionalFields() {
        final Message message = minimalMessage();

        final Message roundTripped = Message.fromJSON(message.toJSON());

        assertNull(roundTripped.getLocalHostName());
        assertNull(roundTripped.getRemoteHostName());
        assertNull(roundTripped.getQuery());
        assertNull(roundTripped.getNonce());
        assertNull(roundTripped.getData());
        assertNull(roundTripped.getRemoteSubqueries());
    }

    @Test
    public void fromJSONRoundTripsFullMessageWithNestedSubqueries() {
        final Message subquery = Message.builder()
                .localHostName("sub-local")
                .remoteHostName("sub-remote")
                .query("sub query")
                .nonce("sub-nonce")
                .data(new Text(Collections.singletonList("sub-data")))
                .remoteSubqueries(null)
                .build();

        final Message message = Message.builder()
                .localHostName("local")
                .remoteHostName("remote")
                .query("SELECT *")
                .nonce("abc123")
                .data(new Text(Collections.singletonList("hello")))
                .remoteSubqueries(Collections.singletonList(subquery))
                .build();

        final Message roundTripped = Message.fromJSON(message.toJSON());

        assertEquals("local", roundTripped.getLocalHostName());
        assertEquals("remote", roundTripped.getRemoteHostName());
        assertEquals("SELECT *", roundTripped.getQuery());
        assertEquals("abc123", roundTripped.getNonce());
        assertEquals(Collections.singletonList("hello"), ((Text) roundTripped.getData()).getValue());
        assertEquals(1, roundTripped.getRemoteSubqueries().size());

        final Message roundTrippedSubquery = roundTripped.getRemoteSubqueries().get(0);
        assertEquals("sub-local", roundTrippedSubquery.getLocalHostName());
        assertEquals(Collections.singletonList("sub-data"), ((Text) roundTrippedSubquery.getData()).getValue());
        assertNull(roundTrippedSubquery.getRemoteSubqueries());
    }

    @Test
    public void toJSONThrowsWhenMessageIsNull() {
        assertThrows(IllegalArgumentException.class, () -> new Convert().toJSON(null));
    }

    @Test
    public void fromJSONThrowsWhenNodeIsNull() {
        assertThrows(IllegalArgumentException.class, () -> Message.fromJSON(null));
    }

    @Test
    public void fromJSONThrowsWhenLocalHostNameKeyIsMissing() {
        final ObjectNode node = minimalMessage().toJSON();
        node.remove("localHostName");

        assertThrows(IllegalArgumentException.class, () -> Message.fromJSON(node));
    }

    @Test
    public void fromJSONThrowsWhenDataKeyIsMissing() {
        final ObjectNode node = minimalMessage().toJSON();
        node.remove("data");

        assertThrows(IllegalArgumentException.class, () -> Message.fromJSON(node));
    }

    @Test
    public void fromJSONThrowsWhenRemoteSubqueriesKeyIsMissing() {
        final ObjectNode node = minimalMessage().toJSON();
        node.remove("remoteSubqueries");

        assertThrows(IllegalArgumentException.class, () -> Message.fromJSON(node));
    }

    @Test
    public void fromJSONThrowsWhenDataValueIsNotAnObjectOrNull() {
        final ObjectNode node = minimalMessage().toJSON();
        node.put("data", "not-an-object");

        assertThrows(IllegalArgumentException.class, () -> Message.fromJSON(node));
    }

    @Test
    public void fromJSONThrowsWhenRemoteSubqueriesEntryIsNotAnObject() {
        final ObjectNode node = minimalMessage().toJSON();
        node.putArray("remoteSubqueries").add("not-an-object");

        assertThrows(IllegalArgumentException.class, () -> Message.fromJSON(node));
    }

}
