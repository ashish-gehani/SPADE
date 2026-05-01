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

package spade.utility.mcp;

import java.util.logging.Level;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;

import spade.utility.mcp.arg.Arg;
import spade.utility.mcp.arg.Parser;
import spade.utility.mcp.connection.Context;
import spade.utility.mcp.connection.SPADEControl;
import spade.utility.mcp.connection.SPADEQuery;
import spade.utility.mcp.tool.handler.AddStorage;
import spade.utility.mcp.tool.handler.ListStorages;
import spade.utility.mcp.tool.handler.PrintStorage;
import spade.utility.mcp.tool.handler.QuickGrailQuery;
import spade.utility.mcp.tool.handler.ReadQuickGrail;
import spade.utility.mcp.tool.handler.SetStorage;

public class Server {

    private SPADEQuery spadeQuery;
    private SPADEControl spadeControl;
    private McpSyncServer mcpServer;

    public static void log (final Level level, final String msg) {
        // Logging to error stream to avoid corrupting System.in. System.in is used by MCP server.
        System.err.println("[" + level.getName() + "] [" + Server.class.getName() + "] " + msg);
    }

    private void start(final Arg arg) throws Exception {
        this.spadeQuery = new SPADEQuery(arg.getHost(), arg.getSpadeQueryPort());
        this.spadeQuery.connect();

        this.spadeControl = new SPADEControl(arg.getHost(), arg.getSpadeControlPort());
        this.spadeControl.connect();

        final Context context = new Context(this.spadeQuery, this.spadeControl);

        final StdioServerTransportProvider transport =
            new StdioServerTransportProvider(McpJsonDefaults.getMapper());

        this.mcpServer = McpServer.sync(transport)
            .serverInfo("spade", "1.0")
            .toolCall(new spade.utility.mcp.tool.QuickGrailQuery().build(), new QuickGrailQuery(context)::handle)
            .toolCall(new spade.utility.mcp.tool.ReadQuickGrail().build(),  new ReadQuickGrail(context)::handle)
            .toolCall(new spade.utility.mcp.tool.SetStorage().build(),      new SetStorage(context)::handle)
            .toolCall(new spade.utility.mcp.tool.PrintStorage().build(),    new PrintStorage(context)::handle)
            .toolCall(new spade.utility.mcp.tool.ListStorages().build(),    new ListStorages(context)::handle)
            .toolCall(new spade.utility.mcp.tool.AddStorage().build(),      new AddStorage(context)::handle)
            .build();
    }

    private void stop() {
        if (this.spadeQuery != null) {
            this.spadeQuery.close();
        }
        if (this.spadeControl != null) {
            this.spadeControl.close();
        }
        if (this.mcpServer != null) {
            try { this.mcpServer.close(); } catch (Exception e) { /* ignore */ }
        }
    }

    public static void main(final String[] args) throws Exception{
        final Arg arg;
        try {
            arg = Parser.parse(args);
            log(Level.INFO, "Args - " + arg.toString());
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            Parser.printHelp();
            System.exit(1);
            return;
        }

        final Server server = new Server();
        try {
            server.start(arg);
            log(Level.INFO, "Server started");
        } catch (Exception e) {
            System.err.println("Failed to start: " + e.getMessage());
            throw e;
            // System.exit(1);
            // return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log(Level.INFO, "Server stopping");
            server.stop();
            log(Level.INFO, "Server stopped");
        }));

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
