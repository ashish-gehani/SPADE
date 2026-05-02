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

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServer.SingleSessionSyncSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;

import spade.utility.mcp.arg.Arg;
import spade.utility.mcp.tool.Registry;
import spade.utility.mcp.tool.Tool;

public class Stdio extends Server {

    private McpSyncServer mcpServer;

    public Stdio(
        final Arg arg,
        final Registry toolRegistry
    ){
        super(arg, toolRegistry);
    }

    public void initialize() throws Exception {
        final StdioServerTransportProvider transport =
            new StdioServerTransportProvider(McpJsonDefaults.getMapper());

        final SingleSessionSyncSpecification builder = McpServer.sync(transport);
        builder.serverInfo("spade", "1.0");
        for (final Tool tool : this.getToolRegistry().getTools()) {
            builder.toolCall(tool.build(), tool::handle);
        }
        this.mcpServer = builder.build();
        
    }

    public void start() throws Exception {
        // no-op
    }

    public void shutdown() {
        super.shutdown();
        if (this.mcpServer != null) {
            try { this.mcpServer.close(); } catch (Exception e) { /* ignore */ }
        }
    }
}
