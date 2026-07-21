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

package spade.utility.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ChatHistory {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ArrayNode messages = mapper.createArrayNode();

    public ArrayNode getMessages() {
        return messages;
    }

    public void addUserMessage(final String text) {
        final ObjectNode message = mapper.createObjectNode();
        message.put("role", "user");
        message.put("content", text);
        messages.add(message);
    }

    public void addAssistantMessage(final JsonNode content) {
        final ObjectNode message = mapper.createObjectNode();
        message.put("role", "assistant");
        message.set("content", content);
        messages.add(message);
    }

    public void addToolResults(final ArrayNode toolResults) {
        final ObjectNode message = mapper.createObjectNode();
        message.put("role", "user");
        message.set("content", toolResults);
        messages.add(message);
    }

    public ObjectMapper getMapper() {
        return mapper;
    }

}
