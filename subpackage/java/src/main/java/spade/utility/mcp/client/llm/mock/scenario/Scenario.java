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

import java.util.Collections;
import java.util.List;

import spade.utility.mcp.client.llm.mock.ToolCall;

public class Scenario {

    private final String name;
    private final List<ToolCall> toolCalls;

    public Scenario(
        final String name,
        final List<ToolCall> toolCalls
    ) {
        this.name = name;
        this.toolCalls = Collections.unmodifiableList(toolCalls);
    }

    public String getName() {
        return name;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    @Override
    public String toString() {
        return "Scenario[name=" + name
            + ", toolCalls=" + toolCalls
            + "]";
    }

}
