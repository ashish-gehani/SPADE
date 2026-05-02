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

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;

import spade.core.Query;
import spade.utility.mcp.connection.Context;

public class PrintStorage extends Tool {

    public PrintStorage(final Context ctx){
        super(ctx);
    }

    @Override
    public McpSchema.Tool build() {
        return McpSchema.Tool.builder()
            .name("print_storage")
            .description("Print the name of the currently active SPADE storage")
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
        final Query result;
        try {
            result = this.getContext().getSpadeQuery().query("print storage");
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
