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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;

import spade.utility.mcp.server.connection.Context;

public class ReadQuickGrailDoc extends Tool {

    private static final String RESOURCE_PATH = "spade/query/quickgrail/README.md";

    public ReadQuickGrailDoc(final Context context) {
        super(context);
    }

    @Override
    public McpSchema.Tool build() {
        final Map<String, Object> properties = new HashMap<>();

        return McpSchema.Tool.builder()
            .name("read_quickgrail_doc")
            .description("Return the QuickGrail query language reference (README)")
            .inputSchema(new McpSchema.JsonSchema(
                "object",
                properties,
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
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (is == null) {
                return McpSchema.CallToolResult.builder()
                    .addTextContent("Error: QuickGrail README not found in classpath")
                    .isError(true)
                    .build();
            }
            final String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return McpSchema.CallToolResult.builder()
                .addTextContent(content)
                .isError(false)
                .build();
        } catch (Exception e) {
            return McpSchema.CallToolResult.builder()
                .addTextContent("Error reading QuickGrail README: " + e.getMessage())
                .isError(true)
                .build();
        }
    }

}
