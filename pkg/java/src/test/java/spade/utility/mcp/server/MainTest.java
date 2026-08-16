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

package spade.utility.mcp.server;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

import spade.utility.mcp.server.setting.Parser;

public class MainTest {

    @Test
    public void currentMainConfigIsValid() {
        // Explicitly targets Main.getDefaultConfigFilePath() (the shipped
        // cfg/spade.utility.mcp.server.Main.config), rather than relying on Main.parse(String[])'s
        // implicit fallback to it. Relies on the JVM's working directory being the repo root,
        // same as spade.core.Settings itself requires to resolve cfg/*.config paths.
        assertDoesNotThrow(() -> Parser.parse("", Main.getDefaultConfigFilePath()));
    }

}
