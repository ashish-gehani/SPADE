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

package spade.utility.mcp.server.tool;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import spade.utility.mcp.server.tool.definition.Definition;
import spade.utility.mcp.server.tool.type.Type;

/**
 * Validates the tool config files shipped under {@code cfg/mcp/tool/}, the same way
 * {@link spade.utility.mcp.server.MainTest} validates the shipped {@code Main.config}. Relies on
 * the JVM's working directory being the repo root, same as {@code spade.core.Settings} itself
 * requires to resolve {@code cfg/*} paths.
 */
public class ShippedToolConfigsTest {

    @Test
    public void spadeControlConfigIsValid() {
        final String path = "cfg/mcp/tool/spade_control/config";
        final Definition definition = assertDoesNotThrow(() -> spade.utility.mcp.server.tool.definition.Parser.parse(path));
        assertEquals(Type.SPADE_CLI, definition.getType());
        assertDoesNotThrow(() -> spade.utility.mcp.server.tool.type.spade.cli.Parser.parse(path));
    }

    @Test
    public void spadeQueryConfigIsValid() {
        final String path = "cfg/mcp/tool/spade_query/config";
        final Definition definition = assertDoesNotThrow(() -> spade.utility.mcp.server.tool.definition.Parser.parse(path));
        assertEquals(Type.SPADE_CLI, definition.getType());
        assertDoesNotThrow(() -> spade.utility.mcp.server.tool.type.spade.cli.Parser.parse(path));
    }

    @Test
    public void spadeControlDocConfigIsValid() {
        final String path = "cfg/mcp/tool/spade_control_doc/config";
        final Definition definition = assertDoesNotThrow(() -> spade.utility.mcp.server.tool.definition.Parser.parse(path));
        assertEquals(Type.WEB_DOC, definition.getType());
        assertDoesNotThrow(() -> spade.utility.mcp.server.tool.type.web.doc.Parser.parse(path));
    }

    @Test
    public void spadeQueryDocConfigIsValid() {
        final String path = "cfg/mcp/tool/spade_query_doc/config";
        final Definition definition = assertDoesNotThrow(() -> spade.utility.mcp.server.tool.definition.Parser.parse(path));
        assertEquals(Type.WEB_DOC, definition.getType());
        assertDoesNotThrow(() -> spade.utility.mcp.server.tool.type.web.doc.Parser.parse(path));
    }

}
