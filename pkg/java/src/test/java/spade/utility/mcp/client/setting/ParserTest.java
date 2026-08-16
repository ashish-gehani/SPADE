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

package spade.utility.mcp.client.setting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import spade.utility.setting.InvalidSettingException;

public class ParserTest {

    private static String configPath() {
        return ParserTest.class.getResource("base.config").getPath();
    }

    private static String mcpMissingConfigPath() {
        return ParserTest.class.getResource("mcp_missing.config").getPath();
    }

    @Test
    public void rejectsMissingMCPHost() {
        assertThrows(InvalidSettingException.class, () -> Parser.parse(
            "mcp.port=3000 mcp.endpoint=/mcp", mcpMissingConfigPath()));
    }

    @Test
    public void rejectsMissingMCPPort() {
        assertThrows(InvalidSettingException.class, () -> Parser.parse(
            "mcp.host=localhost mcp.endpoint=/mcp", mcpMissingConfigPath()));
    }

    @Test
    public void rejectsMissingMCPEndpoint() {
        assertThrows(InvalidSettingException.class, () -> Parser.parse(
            "mcp.host=localhost mcp.port=3000", mcpMissingConfigPath()));
    }

    @Test
    public void rejectsInvalidMCPPortFormat() {
        assertThrows(InvalidSettingException.class, () -> Parser.parse("mcp.port=notanumber", configPath()));
    }

    @Test
    public void rejectsMissingLLMType() {
        assertThrows(InvalidSettingException.class, () -> Parser.parse("", configPath()));
    }

    @Test
    public void rejectsInvalidLLMTypeValue() {
        assertThrows(InvalidSettingException.class, () -> Parser.parse("llm.type=bogus", configPath()));
    }

    @Test
    public void rejectsAnthropicMissingApiKey() {
        assertThrows(InvalidSettingException.class, () -> Parser.parse(
            "llm.type=anthropic llm.anthropic.model=claude-sonnet-5", configPath()));
    }

    @Test
    public void rejectsAnthropicMissingModel() {
        assertThrows(InvalidSettingException.class, () -> Parser.parse(
            "llm.type=anthropic llm.anthropic.api.key=key", configPath()));
    }

    @Test
    public void rejectsMockMissingScenario() {
        assertThrows(InvalidSettingException.class, () -> Parser.parse("llm.type=mock", configPath()));
    }

    @Test
    public void parsesSingleMockScenarioAsSingletonList() throws InvalidSettingException {
        final Setting setting = Parser.parse(
            "llm.type=mock llm.mock.scenarios=test_scenario user.mode=cli", configPath());
        assertEquals(List.of("test_scenario"), setting.getLLM().getMockScenarios());
    }

    @Test
    public void parsesCommaSeparatedMockScenariosInOrder() throws InvalidSettingException {
        final Setting setting = Parser.parse(
            "llm.type=mock llm.mock.scenarios=first_scenario,second_scenario,third_scenario user.mode=cli", configPath());
        assertEquals(
            List.of("first_scenario", "second_scenario", "third_scenario"),
            setting.getLLM().getMockScenarios());
    }

    @Test
    public void rejectsMissingUserMode() {
        assertThrows(InvalidSettingException.class, () -> Parser.parse(
            "llm.type=mock llm.mock.scenarios=test_scenario", configPath()));
    }

    @Test
    public void rejectsInvalidUserModeValue() {
        assertThrows(InvalidSettingException.class, () -> Parser.parse(
            "llm.type=mock llm.mock.scenarios=test_scenario user.mode=bogus", configPath()));
    }

    @Test
    public void rejectsWebModeMissingHost() {
        assertThrows(InvalidSettingException.class, () -> Parser.parse(
            "llm.type=mock llm.mock.scenarios=test_scenario user.mode=web user.web.port=8081", configPath()));
    }

    @Test
    public void rejectsWebModeMissingPort() {
        assertThrows(InvalidSettingException.class, () -> Parser.parse(
            "llm.type=mock llm.mock.scenarios=test_scenario user.mode=web user.web.host=localhost", configPath()));
    }

    @Test
    public void rejectsInvalidVerboseValue() {
        assertThrows(InvalidSettingException.class, () -> Parser.parse(
            "llm.type=mock llm.mock.scenarios=test_scenario user.mode=cli verbose=notabool", configPath()));
    }

}
