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

import java.util.List;
import java.util.logging.Level;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import spade.utility.mcp.client.llm.LLM;
import spade.utility.mcp.client.llm.mock.scenario.Registry;
import spade.utility.mcp.client.llm.mock.scenario.Scenario;
import spade.utility.setting.InvalidSettingException;

public class Mock extends LLM {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Scenario scenario;
    private int stepIndex = 0;

    public Mock(final String scenarioName) throws InvalidSettingException {
        final Registry registry = new Registry(mapper);
        this.scenario = registry.get(scenarioName);
        if (this.scenario == null) {
            throw new InvalidSettingException("Unknown mock scenario: '" + scenarioName + "'");
        }
    }

    private void log(final String msg) {
        System.err.println("[" + Level.INFO.getName() + "] [" + Mock.class.getName() + "] " + msg);
    }

    @Override
    public JsonNode respond(final ArrayNode messages, final ArrayNode tools) throws Exception {
        final JsonNode lastContent = messages.get(messages.size() - 1).get("content");

        // A tool_result turn is a "user" message whose content is an array of tool_result
        // blocks, as opposed to the plain-string content of a regular user prompt.
        if (lastContent != null && lastContent.isArray()) {
            verifyToolResult(lastContent);
            stepIndex++;
        }

        final List<ToolCall> toolCalls = scenario.getToolCalls();
        if (stepIndex >= toolCalls.size()) {
            log("respond: scenario '" + scenario.getName() + "' completed");
            return respondText("Scenario '" + scenario.getName() + "' completed successfully.");
        }

        final ToolCall toolCall = toolCalls.get(stepIndex);
        log("respond: step " + stepIndex + " name=" + toolCall.getName() + " tool=" + toolCall.getToolName());
        return respondWithToolCall(toolUseId(stepIndex), toolCall.getToolName(), toolCall.getInput());
    }

    private void verifyToolResult(final JsonNode toolResults) throws Exception {
        final ToolCall toolCall = scenario.getToolCalls().get(stepIndex);
        final String expectedToolUseId = toolUseId(stepIndex);

        JsonNode matching = null;
        for (final JsonNode block : toolResults) {
            if (expectedToolUseId.equals(block.path("tool_use_id").asText())) {
                matching = block;
                break;
            }
        }
        if (matching == null) {
            throw new Exception(
                "Scenario '" + scenario.getName() + "' step " + stepIndex + " (" + toolCall.getName()
                    + "): expected a tool_result for tool_use_id '" + expectedToolUseId + "', got none");
        }

        final String actual = matching.path("content").asText();
        final String expected = toolCall.getResult();
        if (!actual.contains(expected)) {
            throw new Exception(
                "Scenario '" + scenario.getName() + "' step " + stepIndex + " (" + toolCall.getName()
                    + "): expected result to contain '" + expected + "', got '" + actual + "'");
        }
        log("verifyToolResult: step " + stepIndex + " (" + toolCall.getName() + ") OK");
    }

    private static String toolUseId(final int stepIndex) {
        return "scenario_step_" + stepIndex;
    }

    public JsonNode respondText(final String text) {
        log("respondText: text=" + text);
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
        log("respondWithToolCall: id=" + toolUseId + " tool=" + toolName + " input=" + toolInput);
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

    public ObjectNode createInput() {
        return mapper.createObjectNode();
    }

    @Override
    public void close() {
        // no-op
    }

}
