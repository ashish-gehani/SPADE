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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import spade.utility.mcp.client.llm.LLM;
import spade.utility.mcp.client.llm.mock.scenario.Registry;
import spade.utility.mcp.client.llm.mock.scenario.Scenario;
import spade.utility.setting.InvalidSettingException;

public class Mock extends LLM {

    private static final Logger logger = Logger.getLogger(Mock.class.getName());

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<Scenario> scenarios;
    private final boolean verbose;
    private int scenarioIndex = 0;
    private int stepIndex = 0;

    public Mock(final List<String> scenarioNames, final boolean verbose) throws InvalidSettingException {
        this(scenarioNames, new Registry(new ObjectMapper()), verbose);
    }

    // package-private: lets tests inject a Registry backed by a test-only config file
    // instead of the default (production) scenario registry config.
    Mock(final List<String> scenarioNames, final Registry registry, final boolean verbose) throws InvalidSettingException {
        if (scenarioNames == null || scenarioNames.isEmpty()) {
            throw new InvalidSettingException("No mock scenarios specified");
        }
        final List<Scenario> resolved = new ArrayList<>();
        for (final String scenarioName : scenarioNames) {
            final Scenario scenario = registry.get(scenarioName);
            if (scenario == null) {
                throw new InvalidSettingException("Unknown mock scenario: '" + scenarioName + "'");
            }
            resolved.add(scenario);
        }
        this.scenarios = Collections.unmodifiableList(resolved);
        this.verbose = verbose;
    }

    private void log(final String msg) {
        if (!verbose) {
            return;
        }
        logger.log(Level.INFO, msg);
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

        Scenario scenario = scenarios.get(scenarioIndex);
        List<ToolCall> toolCalls = scenario.getToolCalls();
        while (stepIndex >= toolCalls.size()) {
            log("respond: scenario '" + scenario.getName() + "' completed");
            if (scenarioIndex + 1 >= scenarios.size()) {
                return respondText("Scenario '" + scenario.getName() + "' completed successfully. All scenarios completed.");
            }
            scenarioIndex++;
            stepIndex = 0;
            scenario = scenarios.get(scenarioIndex);
            toolCalls = scenario.getToolCalls();
        }

        final ToolCall toolCall = toolCalls.get(stepIndex);
        log("respond: scenario '" + scenario.getName() + "' step " + stepIndex + " name=" + toolCall.getName() + " tool=" + toolCall.getToolName());
        return respondWithToolCall(toolUseId(scenarioIndex, stepIndex), toolCall.getToolName(), toolCall.getInput());
    }

    private void verifyToolResult(final JsonNode toolResults) throws Exception {
        final Scenario scenario = scenarios.get(scenarioIndex);
        final ToolCall toolCall = scenario.getToolCalls().get(stepIndex);
        final String expectedToolUseId = toolUseId(scenarioIndex, stepIndex);

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
        log("verifyToolResult: scenario '" + scenario.getName() + "' step " + stepIndex + " (" + toolCall.getName() + ") OK");
    }

    private static String toolUseId(final int scenarioIndex, final int stepIndex) {
        return "scenario_" + scenarioIndex + "_step_" + stepIndex;
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
