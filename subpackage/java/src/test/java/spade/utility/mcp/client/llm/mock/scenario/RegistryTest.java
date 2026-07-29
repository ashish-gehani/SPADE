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

package spade.utility.mcp.client.llm.mock.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import spade.utility.mcp.client.llm.mock.ToolCall;
import spade.utility.setting.InvalidSettingException;

public class RegistryTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static String configPath(final String name) {
        return RegistryTest.class.getResource(name).getPath();
    }

    @Test
    public void parsesScenariosFromConfig() throws InvalidSettingException {
        final Registry registry = new Registry(mapper, configPath("base.config"));

        assertEquals(List.of("list_then_query", "second_scenario"), registry.names());

        final Scenario first = registry.get("list_then_query");
        final List<ToolCall> firstToolCalls = first.getToolCalls();
        assertEquals(2, firstToolCalls.size());

        final ToolCall listStep = firstToolCalls.get(0);
        assertEquals("list_storages_step", listStep.getName());
        assertEquals("list_storages", listStep.getToolName());
        assertEquals(0, listStep.getInput().size());
        assertEquals("No storages added.", listStep.getResult());

        final ToolCall queryStep = firstToolCalls.get(1);
        assertEquals("query_step", queryStep.getName());
        assertEquals("query", queryStep.getToolName());
        assertEquals("stat $base", queryStep.getInput().get("query").asText());
        assertEquals("OK", queryStep.getResult());

        final Scenario second = registry.get("second_scenario");
        assertEquals(1, second.getToolCalls().size());
    }

    @Test
    public void unknownScenarioNameReturnsNull() throws InvalidSettingException {
        final Registry registry = new Registry(mapper, configPath("base.config"));
        assertNull(registry.get("does_not_exist"));
    }

    @Test
    public void emptyConfigYieldsNoScenarios() throws InvalidSettingException {
        final Registry registry = new Registry(mapper, configPath("empty.config"));
        assertTrue(registry.names().isEmpty());
    }

    @Test
    public void rejectsMissingToolName() {
        assertThrows(InvalidSettingException.class,
            () -> new Registry(mapper, configPath("missing_tool_name.config")));
    }

    @Test
    public void rejectsMissingResult() {
        assertThrows(InvalidSettingException.class,
            () -> new Registry(mapper, configPath("missing_result.config")));
    }

    @Test
    public void rejectsInvalidInputJson() {
        assertThrows(InvalidSettingException.class,
            () -> new Registry(mapper, configPath("invalid_input.config")));
    }

    @Test
    public void rejectsNonObjectInput() {
        assertThrows(InvalidSettingException.class,
            () -> new Registry(mapper, configPath("non_object_input.config")));
    }

}
