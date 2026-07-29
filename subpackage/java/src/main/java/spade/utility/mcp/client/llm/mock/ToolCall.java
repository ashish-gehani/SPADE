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

import com.fasterxml.jackson.databind.node.ObjectNode;

public class ToolCall {

    private final String name;
    private final String toolName;
    private final ObjectNode input;
    private final String result;

    public ToolCall(
        final String name,
        final String toolName,
        final ObjectNode input,
        final String result
    ) {
        this.name = name;
        this.toolName = toolName;
        this.input = input;
        this.result = result;
    }

    public String getName() {
        return name;
    }

    public String getToolName() {
        return toolName;
    }

    public ObjectNode getInput() {
        return input;
    }

    public String getResult() {
        return result;
    }

    @Override
    public String toString() {
        return "ToolCall[name=" + name
            + ", toolName=" + toolName
            + ", input=" + input
            + ", result=" + result
            + "]";
    }

}
