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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;

import spade.utility.mcp.client.llm.mock.scenario.Registry;
import spade.utility.mcp.client.llm.mock.scenario.Scenario;
import spade.utility.setting.InvalidSettingException;

/*
 llm.mock.scenarios (formerly llm.mock.scenario, singular) accepts a
 comma-separated list of scenario names. These tests exercise Mock's
 chaining across scenarios: a completed scenario must NOT end the
 conversation turn (stop_reason "tool_use" must continue) until the last
 listed scenario finishes, since a single mcp Client.chat() call keeps
 calling respond() as long as stop_reason stays "tool_use".

 Uses the same test-only scenario config as RegistryTest (base.config:
 "list_then_query" has 2 tool calls, "second_scenario" has 1) rather than
 the production cfg/spade.utility.mcp.client.llm.mock.scenario.Registry.config,
 so this test doesn't break if that operational config changes.
 */
public class MockTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static String configPath() {
        return MockTest.class.getResource("/spade/utility/mcp/client/llm/mock/scenario/base.config").getPath();
    }

    private Registry registry() throws InvalidSettingException {
        return new Registry(mapper, configPath());
    }

    @Test
    public void rejectsNullScenarioList() throws Exception {
        assertThrows(InvalidSettingException.class, () -> new Mock(null, registry()));
    }

    @Test
    public void rejectsEmptyScenarioList() throws Exception {
        assertThrows(InvalidSettingException.class, () -> new Mock(List.of(), registry()));
    }

    @Test
    public void rejectsUnknownScenarioName() throws Exception {
        assertThrows(InvalidSettingException.class, () -> new Mock(List.of("does_not_exist"), registry()));
    }

    @Test
    public void chainsThroughAllListedScenariosBeforeEndingTurn() throws Exception {
        // list_then_query has 2 steps, second_scenario has 1 (base.config).
        final Registry registry = registry();
        final Mock mock = new Mock(List.of("list_then_query", "second_scenario"), registry);

        // The expected result text for each step, in chained order, so the
        // fake tool_result content this test sends back satisfies
        // Mock.verifyToolResult's "actual.contains(expected)" check.
        final List<String> expectedResults = new java.util.ArrayList<>();
        for (final String scenarioName : List.of("list_then_query", "second_scenario")) {
            final Scenario scenario = registry.get(scenarioName);
            for (final ToolCall toolCall : scenario.getToolCalls()) {
                expectedResults.add(toolCall.getResult());
            }
        }

        final ArrayNode messages = mapper.createArrayNode();
        messages.add(userMessage("start"));

        int toolUseCount = 0;
        JsonNode response;
        while (true) {
            response = mock.respond(messages, mapper.createArrayNode());
            final String stopReason = response.get("stop_reason").asText();

            final ObjectNode assistantMessage = mapper.createObjectNode();
            assistantMessage.put("role", "assistant");
            assistantMessage.set("content", response.get("content"));
            messages.add(assistantMessage);

            if (!"tool_use".equals(stopReason)) {
                break;
            }

            final JsonNode toolUseBlock = response.get("content").get(0);
            messages.add(toolResultMessage(toolUseBlock.get("id").asText(), expectedResults.get(toolUseCount)));
            toolUseCount++;
        }

        // 2 + 1 = 3 tool calls total, chained within this single loop - no
        // premature "end_turn" between the two scenarios.
        assertEquals(3, toolUseCount);
        assertEquals("end_turn", response.get("stop_reason").asText());
        final String finalText = response.get("content").get(0).get("text").asText();
        assertTrue(finalText.contains("All scenarios completed"));
    }

    @Test
    public void mismatchedToolResultThrows() throws Exception {
        final Mock mock = new Mock(List.of("list_then_query"), registry());

        final ArrayNode messages = mapper.createArrayNode();
        messages.add(userMessage("start"));

        final JsonNode firstResponse = mock.respond(messages, mapper.createArrayNode());
        final JsonNode toolUseBlock = firstResponse.get("content").get(0);

        final ObjectNode assistantMessage = mapper.createObjectNode();
        assistantMessage.put("role", "assistant");
        assistantMessage.set("content", firstResponse.get("content"));
        messages.add(assistantMessage);
        messages.add(toolResultMessage(toolUseBlock.get("id").asText(), "completely wrong result"));

        assertThrows(Exception.class, () -> mock.respond(messages, mapper.createArrayNode()));
    }

    private ObjectNode userMessage(final String text) {
        final ObjectNode message = mapper.createObjectNode();
        message.put("role", "user");
        message.put("content", text);
        return message;
    }

    private ObjectNode toolResultMessage(final String toolUseId, final String content) {
        final ObjectNode toolResultBlock = mapper.createObjectNode();
        toolResultBlock.put("type", "tool_result");
        toolResultBlock.put("tool_use_id", toolUseId);
        toolResultBlock.put("content", content);

        final ArrayNode contentArray = mapper.createArrayNode();
        contentArray.add(toolResultBlock);

        final ObjectNode message = mapper.createObjectNode();
        message.put("role", "user");
        message.set("content", contentArray);
        return message;
    }

}
