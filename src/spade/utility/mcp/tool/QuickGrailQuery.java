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

package spade.utility.mcp.tool;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import io.modelcontextprotocol.spec.McpSchema;

public class QuickGrailQuery implements Tool {

    @Override
    public McpSchema.Tool build() {
        final Map<String, Object> queryProp = new HashMap<>();
        queryProp.put("type", "string");
        queryProp.put("description", "The QuickGrail query to execute");

        final Map<String, Object> properties = new HashMap<>();
        properties.put("query", queryProp);

        return McpSchema.Tool.builder()
            .name("query")
            .description("Execute a QuickGrail query and return the result")
            .inputSchema(new McpSchema.JsonSchema(
                "object",
                properties,
                Collections.singletonList("query"),
                false,
                null,
                null
            ))
            .build();
    }

}
