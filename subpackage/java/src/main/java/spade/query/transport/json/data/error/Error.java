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

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import spade.query.transport.json.data.Data;
import spade.query.transport.json.data.Type;

public class Error extends Data {

    private static final String keyValue = "value";

    private final List<String> value;

    public Error(final Throwable throwable) {
        this(lines(throwable));
    }

    public Error(final List<String> value) {
        super(Type.ERROR);
        if (value == null) {
            throw new IllegalArgumentException("NULL value");
        }
        this.value = value;
    }

    public List<String> getValue() {
        return value;
    }

    @Override
    public ObjectNode toJSON() {
        final ObjectNode node = newNode();
        final ArrayNode valueNode = node.putArray(keyValue);
        for (final String line : value) {
            valueNode.add(line);
        }
        return node;
    }

    public static Data fromJSON(final ObjectNode node) {
        if (node == null) {
            throw new IllegalArgumentException("NULL node");
        }

        final JsonNode valueNode = node.get(keyValue);
        if (valueNode == null || !valueNode.isArray()) {
            throw new IllegalArgumentException("Missing/invalid '" + keyValue + "' in: " + node);
        }

        final List<String> value = new ArrayList<>();
        for (final JsonNode lineNode : valueNode) {
            if (!lineNode.isTextual()) {
                throw new IllegalArgumentException("Invalid entry in '" + keyValue + "' in: " + node);
            }
            value.add(lineNode.asText());
        }

        return new Error(value);
    }

    // Walks the cause chain (this throwable, then getCause(), then its cause, and so
    // on) and for each one keeps only the message and the single stack trace element
    // it was thrown from - not the full trace - since the full trace of every
    // throwable in the chain is far more than a caller of this graph transport needs.
    private static List<String> lines(final Throwable throwable) {
        if (throwable == null) {
            throw new IllegalArgumentException("NULL throwable");
        }

        final List<String> lines = new ArrayList<>();
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            final StackTraceElement[] stackTrace = current.getStackTrace();
            if (stackTrace.length > 0) {
                lines.add(current.toString() + " at " + stackTrace[0].toString());
            } else {
                lines.add(current.toString());
            }
        }
        return lines;
    }

}
