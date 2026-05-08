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

package spade.utility.mcp.client.user;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.eclipse.jetty.ee10.servlet.DefaultServlet;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.ServerConnector;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import spade.utility.mcp.client.llm.LLM;
import spade.utility.mcp.client.MCPClient;

public class Web extends Client {

    private final String host;
    private final int port;
    private org.eclipse.jetty.server.Server jettyServer;

    public Web(
        final MCPClient mcpClient,
        final LLM llm,
        final String host,
        final int port
    ) {
        super(mcpClient, llm);
        this.host = host;
        this.port = port;
    }

    @Override
    public void run() throws Exception {
        final java.net.URL resourceUrl = Web.class.getResource("/spade/utility/mcp/client/user/webserver");
        if (resourceUrl == null) {
            throw new Exception("Webserver resources not found on classpath");
        }

        final ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        context.setBaseResourceAsString(resourceUrl.toExternalForm());

        final ServletHolder defaultHolder = new ServletHolder("default", DefaultServlet.class);
        defaultHolder.setInitParameter("dirAllowed", "false");
        context.addServlet(defaultHolder, "/");

        context.addServlet(new ServletHolder(new HttpServlet() {
            @Override
            protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
                final String prompt = new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                final String response;
                try {
                    response = getMCPClient().chat(prompt);
                } catch (Exception e) {
                    resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    resp.setContentType("text/plain;charset=utf-8");
                    resp.getWriter().write(e.getMessage());
                    return;
                }
                resp.setContentType("text/plain;charset=utf-8");
                resp.getWriter().write(response);
            }
        }), "/query");

        context.addServlet(new ServletHolder(new HttpServlet() {
            @Override
            protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
                final String body = new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                resp.setContentType("text/plain;charset=utf-8");
                resp.getWriter().write(body);
            }
        }), "/test_echo");

        context.addServlet(new ServletHolder(new HttpServlet() {
            @Override
            protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                resp.setContentType("text/plain;charset=utf-8");
                resp.getWriter().write("Done");
            }
        }), "/test_sleep");

        this.jettyServer = new org.eclipse.jetty.server.Server();
        final ServerConnector connector = new ServerConnector(this.jettyServer);
        connector.setHost(host);
        connector.setPort(port);
        this.jettyServer.addConnector(connector);
        this.jettyServer.setHandler(context);
        this.jettyServer.start();

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

        this.jettyServer.join();
    }

    @Override
    public void shutdown() {
        super.shutdown();
        if (this.jettyServer != null) {
            try { this.jettyServer.stop(); } catch (Exception e) { /* ignore */ }
        }
    }

}
