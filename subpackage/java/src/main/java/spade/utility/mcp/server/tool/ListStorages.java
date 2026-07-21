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

import java.util.Collections;
import java.util.HashMap;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;

import spade.utility.mcp.server.connection.Context;

public class ListStorages extends Tool {

    public ListStorages(final Context ctx){
        super(ctx);
    }

    @Override
    public McpSchema.Tool build() {
        return McpSchema.Tool.builder()
            .name("list_storages")
            .description("List all active SPADE storages")
            .inputSchema(new McpSchema.JsonSchema(
                "object",
                new HashMap<>(),
                Collections.emptyList(),
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
        final String result;
        try {
            result = this.getContext().getSpadeControl().send("list storages");
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
