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

package spade.utility.mcp.client.llm.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import spade.utility.mcp.client.llm.LLM;

public class Mock extends LLM {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Tools tools = new Tools(mapper);
    private final Text text = new Text();
    private final java.util.Random random = new java.util.Random();

    @Override
    public JsonNode respond(final ArrayNode messages, final ArrayNode tools) {
        if (random.nextBoolean()) {
            return respondWithRandomToolCall();
        }
        return respondText(text.randomResponse());
    }

    public JsonNode respondText(final String text) {
        final ObjectNode textBlock = mapper.createObjectNode();
        textBlock.put("type", "text");
        textBlock.put("text", text);

        final ArrayNode content = mapper.createArrayNode();
        content.add(textBlock);

        final ObjectNode response = mapper.createObjectNode();
        response.put("stop_reason", "end_turn");
        response.set("content", content);
        return response;
    }

    public JsonNode respondWithToolCall(final String toolUseId, final String toolName, final ObjectNode toolInput) {
        final ObjectNode toolBlock = mapper.createObjectNode();
        toolBlock.put("type", "tool_use");
        toolBlock.put("id", toolUseId);
        toolBlock.put("name", toolName);
        toolBlock.set("input", toolInput != null ? toolInput : mapper.createObjectNode());

        final ArrayNode content = mapper.createArrayNode();
        content.add(toolBlock);

        final ObjectNode response = mapper.createObjectNode();
        response.put("stop_reason", "tool_use");
        response.set("content", content);
        return response;
    }

    public JsonNode respondWithRandomToolCall() {
        final String toolName = tools.randomToolName();
        return respondWithToolCall(tools.randomToolUseId(), toolName, tools.randomInputFor(toolName));
    }

    public ObjectNode createInput() {
        return mapper.createObjectNode();
    }

    @Override
    public void close() {
        // no-op
    }

}
