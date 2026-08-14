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

import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;

import spade.utility.mcp.server.tool.Tool;
import spade.utility.mcp.server.tool.definition.Definition;
import spade.utility.setting.InvalidSettingException;

public class Doc extends Tool {

    private final Config config;

    public Doc(final Definition definition, final String configFilePath) throws InvalidSettingException {
        super(definition);

        this.config = Parser.parse(configFilePath);
    }

    @Override
    public McpSchema.Tool build() {
        final Definition definition = getDefinition();

        final Map<String, Object> properties = new HashMap<>();
        final List<String> required = new ArrayList<>();
        for (final Definition.Property property : definition.getProperties()) {
            final Map<String, Object> propertySchema = new HashMap<>();
            propertySchema.put("type", property.getType());
            propertySchema.put("description", property.getDescription());
            if (!property.getPossibleValues().isEmpty()) {
                propertySchema.put("enum", property.getPossibleValues());
            }
            properties.put(property.getName(), propertySchema);
            if (property.isRequired()) {
                required.add(property.getName());
            }
        }

        return McpSchema.Tool.builder()
            .name(definition.getName())
            .description(definition.getDescription())
            .inputSchema(new McpSchema.JsonSchema(
                "object",
                properties,
                required,
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
        final HttpClient client = HttpClient.newHttpClient();
        final StringBuilder combined = new StringBuilder();
        try {
            for (final URL url : config.getUrls()) {
                final HttpRequest httpRequest = HttpRequest.newBuilder(url.toURI()).GET().build();
                final HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                if (combined.length() > 0) {
                    combined.append("\n\n");
                }
                combined.append(Jsoup.parse(response.body()).text());
            }
        } catch (Exception e) {
            return McpSchema.CallToolResult.builder()
                .addTextContent("Error: " + e.getMessage())
                .isError(true)
                .build();
        }

        return McpSchema.CallToolResult.builder()
            .addTextContent(combined.toString())
            .isError(false)
            .build();
    }

}
