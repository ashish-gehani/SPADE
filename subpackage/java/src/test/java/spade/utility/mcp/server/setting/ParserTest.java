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

package spade.utility.mcp.server.setting;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import spade.utility.setting.InvalidSettingException;

public class ParserTest {

    private static String configPath() {
        return ParserTest.class.getResource("base.config").getPath();
    }

    private static String spadeMissingConfigPath() {
        return ParserTest.class.getResource("spade_missing.config").getPath();
    }

    @Test
    public void rejectsMissingSpadeHost() {
        assertThrows(InvalidSettingException.class, () -> Parser.parse(
            "spade.query.port=19998 spade.control.port=19999", spadeMissingConfigPath()));
    }

    @Test
    public void rejectsMissingSpadeQueryPort() {
        assertThrows(InvalidSettingException.class, () -> Parser.parse(
            "spade.host=localhost spade.control.port=19999", spadeMissingConfigPath()));
    }

    @Test
    public void rejectsMissingSpadeControlPort() {
        assertThrows(InvalidSettingException.class, () -> Parser.parse(
            "spade.host=localhost spade.query.port=19998", spadeMissingConfigPath()));
    }

    @Test
    public void rejectsInvalidSpadeQueryPortFormat() {
        assertThrows(InvalidSettingException.class, () -> Parser.parse("spade.query.port=notanumber", configPath()));
    }

    @Test
    public void rejectsInvalidSpadeControlPortFormat() {
        assertThrows(InvalidSettingException.class, () -> Parser.parse("spade.control.port=notanumber", configPath()));
    }

    @Test
    public void rejectsMissingServerMode() {
        assertThrows(InvalidSettingException.class, () -> Parser.parse("", configPath()));
    }

    @Test
    public void rejectsInvalidServerModeValue() {
        assertThrows(InvalidSettingException.class, () -> Parser.parse("mcp.server.mode=bogus", configPath()));
    }

    @Test
    public void rejectsHttpModeMissingHostName() {
        assertThrows(InvalidSettingException.class, () -> Parser.parse(
            "mcp.server.mode=http mcp.http.host.port=3000 mcp.http.host.endpoint=/mcp", configPath()));
    }

    @Test
    public void rejectsHttpModeMissingHostPort() {
        assertThrows(InvalidSettingException.class, () -> Parser.parse(
            "mcp.server.mode=http mcp.http.host.name=localhost mcp.http.host.endpoint=/mcp", configPath()));
    }

    @Test
    public void rejectsHttpModeMissingHostEndpoint() {
        assertThrows(InvalidSettingException.class, () -> Parser.parse(
            "mcp.server.mode=http mcp.http.host.name=localhost mcp.http.host.port=3000", configPath()));
    }

}
