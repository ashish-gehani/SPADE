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

import spade.core.Query;
import spade.utility.mcp.server.connection.Context;

public class SetStorage extends Tool {

    public SetStorage(final Context context) {
        super(context);
    }

    @Override
    public McpSchema.Tool build() {
        final Map<String, Object> storageNameProp = new HashMap<>();
        storageNameProp.put("type", "string");
        storageNameProp.put("description", "Name of the SPADE storage to set for querying");
        storageNameProp.put("enum", Arrays.asList("Neo4j", "Quickstep", "PostgreSQL"));

        final Map<String, Object> properties = new HashMap<>();
        properties.put("storageName", storageNameProp);

        return McpSchema.Tool.builder()
            .name("set_storage")
            .description("Set the active SPADE storage to query against")
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

        final Query result;
        try {
            result = this.getContext().getSpadeQuery().query("set storage " + storageName);
        } catch (Exception e) {
            return McpSchema.CallToolResult.builder()
                .addTextContent("Error: " + e.getMessage())
                .isError(true)
                .build();
        }

        if (!result.wasQuerySuccessful()) {
            return McpSchema.CallToolResult.builder()
                .addTextContent("Error: " + result.getError())
                .isError(true)
                .build();
        }

        final String resultText = result.getResult() != null ? result.getResult().toString() : "";
        return McpSchema.CallToolResult.builder()
            .addTextContent(resultText)
            .isError(false)
            .build();
    }

}
