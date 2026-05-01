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

package spade.utility.mcp.tool.handler;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;

import spade.utility.mcp.connection.Context;

public class AddStorage extends Handler {

    public AddStorage(final Context context) {
        super(context);
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
            result = this.context.getSpadeControl().send("add storage " + storageName);
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
