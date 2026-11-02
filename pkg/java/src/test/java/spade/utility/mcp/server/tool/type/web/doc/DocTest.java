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

package spade.utility.mcp.server.tool.type.web.doc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import spade.utility.mcp.server.tool.definition.Definition;
import spade.utility.mcp.server.tool.type.Type;
import spade.utility.setting.InvalidSettingException;

public class DocTest {

    // Opt-in because remote test environments may lack resources to run a local web server.
    // Opt in locally with -Dspade.utility.mcp.server.tool.type.web.doc.DocTest.fetchesLocalHtmlAndExtractsText=true.
    @Test
    @EnabledIfSystemProperty(named = "spade.utility.mcp.server.tool.type.web.doc.DocTest.fetchesLocalHtmlAndExtractsText", matches = "true")
    @Timeout(10)
    public void fetchesLocalHtmlAndExtractsText(@TempDir final Path tempDir) throws Exception {
        final HttpServer server = startServer();
        try {
            final Doc doc = createDoc(tempDir, server.getAddress().getPort());

            final McpSchema.CallToolResult result = doc.handle(null, null);

            assertFalse(Boolean.TRUE.equals(result.isError()));
            assertEquals(1, result.content().size());
            assertEquals("SPADE Hello world & friends.", ((McpSchema.TextContent) result.content().get(0)).text());
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer startServer() throws IOException {
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 1);
        server.createContext("/doc", DocTest::serveHtml);
        server.start();
        return server;
    }

    private static void serveHtml(final HttpExchange exchange) throws IOException {
        final byte[] html = "<html><body><h1>SPADE</h1><p>Hello <b>world</b> &amp; friends.</p></body></html>"
            .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, html.length);
        try (var body = exchange.getResponseBody()) {
            body.write(html);
        }
    }

    private static Doc createDoc(final Path tempDir, final int port)
        throws IOException, InvalidSettingException {
        final Path config = tempDir.resolve("doc.config");
        Files.writeString(config, "web.doc.urls = http://127.0.0.1:" + port + "/doc\n");
        return new Doc(new Definition("local_doc", "Local HTML", Type.WEB_DOC, List.of()), config.toString());
    }

}
