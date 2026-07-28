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

import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServer.StreamableSyncSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;

import spade.utility.mcp.server.setting.Setting;
import spade.utility.mcp.server.tool.Registry;
import spade.utility.mcp.server.tool.Tool;

public class Http extends Server {

    private McpSyncServer mcpServer;
    private org.eclipse.jetty.server.Server jettyServer;

    public Http(
        final Setting setting,
        final Registry toolRegistry
    ){
        super(setting, toolRegistry);
    }

    public void initialize() throws Exception {
        final HttpServletStreamableServerTransportProvider transportProvider =
            HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(McpJsonDefaults.getMapper())
                .mcpEndpoint(this.getSetting().getMCP().getHttpHostEndpoint())
                .build();

        final StreamableSyncSpecification mcpServerBuilder = McpServer.sync(transportProvider);
        mcpServerBuilder.serverInfo(NAME, VERSION);
        for (final Tool tool : this.getToolRegistry().getTools()) {
            mcpServerBuilder.toolCall(tool.build(), tool::handle);
        }
        this.mcpServer = mcpServerBuilder.build();

        final ServletContextHandler contextHandler = new ServletContextHandler();
        contextHandler.setContextPath("/");

        contextHandler.addServlet(new ServletHolder(transportProvider), this.getSetting().getMCP().getHttpHostEndpoint() + "/*");

        this.jettyServer = new org.eclipse.jetty.server.Server();
        final ServerConnector connector = new ServerConnector(this.jettyServer);
        connector.setHost(this.getSetting().getMCP().getHttpHostName());
        connector.setPort(this.getSetting().getMCP().getHttpHostPort());
        this.jettyServer.addConnector(connector);
        this.jettyServer.setHandler(contextHandler);
    }

    public void start() throws Exception {
        this.jettyServer.start();
        this.getState().setRunning(true);
    }

    public void shutdown() {
        super.shutdown();
        if (this.mcpServer != null) {
            try { this.mcpServer.close(); } catch (Exception e) { /* ignore */ }
        }
        if (this.jettyServer != null) {
            try { this.jettyServer.stop(); } catch (Exception e) { /* ignore */ }
        }
    }
}
