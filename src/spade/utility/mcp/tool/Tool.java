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

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;

import spade.utility.mcp.connection.Context;

public abstract class Tool {

    private final Context ctx;

    public Tool(final Context ctx) {
        if (ctx == null) {
            throw new IllegalArgumentException("NULL ctx");
        }
        this.ctx = ctx;
    }

    public final Context getContext(){
        return ctx;
    }

    public abstract McpSchema.Tool build();

    public abstract McpSchema.CallToolResult handle(
        final McpSyncServerExchange exchange,
        final McpSchema.CallToolRequest request
    );

}
