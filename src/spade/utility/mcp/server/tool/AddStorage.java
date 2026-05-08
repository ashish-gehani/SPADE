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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;

import spade.utility.mcp.server.connection.Context;

public class AddStorage extends Tool {

    public AddStorage(final Context ctx){
        super(ctx);
    }

    @Override
    public McpSchema.Tool build() {
        final Map<String, Object> storageNameProp = new HashMap<>();
        storageNameProp.put("type", "string");
        storageNameProp.put("description", "Name of the SPADE storage to add");
        storageNameProp.put("enum", Arrays.asList("Neo4j", "Quickstep", "PostgreSQL"));

        final Map<String, Object> properties = new HashMap<>();
        properties.put("storageName", storageNameProp);

        return McpSchema.Tool.builder()
            .name("add_storage")
            .description("Add a SPADE storage")
            .inputSchema(new McpSchema.JsonSchema(
                "object",
                properties,
                Collections.singletonList("storageName"),
                false,
                null,
                null
            ))
            .build();
    }

    @Override
    public McpSchema.CallToolResult handle(
        final McpSyncServerExchange exchange,
        final McpSchema.CallToolRequest request
    ) {
        final String storageName = (String) request.arguments().get("storageName");
        if (storageName == null || storageName.isBlank()) {
            return McpSchema.CallToolResult.builder()
                .addTextContent("Error: null/empty storageName argument")
                .isError(true)
                .build();
        }

        final String result;
        try {
            result = this.getContext().getSpadeControl().send("add storage " + storageName);
        } catch (Exception e) {
            return McpSchema.CallToolResult.builder()
                .addTextContent("Error: " + e.getMessage())
                .isError(true)
                .build();
        }

        return McpSchema.CallToolResult.builder()
            .addTextContent(result)
            .isError(false)
            .build();
    }
}
