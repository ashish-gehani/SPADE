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

package spade.utility.mcp.client;

import java.time.Duration;

import spade.utility.mcp.client.llm.LLM;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;

public class MCPClient {

    private final McpSyncClient mcpClient;
    private final LLM llm;
    private final ChatHistory chatHistory;
    private final boolean verbose;

    public MCPClient(final String url, final LLM llm, final boolean verbose) {
        if (llm == null) {
            throw new IllegalArgumentException("NULL llm");
        }
        final HttpClientStreamableHttpTransport transport =
            HttpClientStreamableHttpTransport.builder(url).build();
        this.mcpClient = McpClient.sync(transport)
            .requestTimeout(Duration.ofSeconds(30))
            .build();
        this.llm = llm;
        this.chatHistory = new ChatHistory();
        this.verbose = verbose;
    }

    public McpSchema.InitializeResult initialize() {
        return this.mcpClient.initialize();
    }

    public McpSchema.ListToolsResult listTools() {
        return this.mcpClient.listTools(null);
    }

    public McpSchema.CallToolResult callTool(final String name, final Map<String, Object> arguments) {
        return this.mcpClient.callTool(new McpSchema.CallToolRequest(name, arguments));
    }

    public String chat(final String prompt) throws Exception {
        final List<McpSchema.Tool> mcpTools = listTools().tools();
        final ArrayNode tools = toAnthropicTools(mcpTools);

        chatHistory.addUserMessage(prompt);

        while (true) {
            final JsonNode response = llm.respond(chatHistory.getMessages(), tools);
            final String stopReason = response.get("stop_reason").asText();
            final JsonNode content = response.get("content");

            chatHistory.addAssistantMessage(content);

            if (!"tool_use".equals(stopReason)) {
                for (final JsonNode block : content) {
                    if ("text".equals(block.path("type").asText())) {
                        return block.path("text").asText();
                    }
                }
                return "";
            }

            final ArrayNode toolResults = chatHistory.getMapper().createArrayNode();
            for (final JsonNode block : content) {
                if (!"tool_use".equals(block.path("type").asText())) {
                    continue;
                }
                final String toolUseId = block.get("id").asText();
                final String toolName = block.get("name").asText();
                final Map<String, Object> toolInput =
                    chatHistory.getMapper().convertValue(block.get("input"), Map.class);

                final McpSchema.CallToolResult result = callTool(toolName, toolInput);

                if (verbose) {
                    System.err.println("[MCP] tool=" + toolName
                        + " response=" + chatHistory.getMapper().writeValueAsString(result.content()));
                }

                final ObjectNode toolResult = chatHistory.getMapper().createObjectNode();
                toolResult.put("type", "tool_result");
                toolResult.put("tool_use_id", toolUseId);
                toolResult.put("content", chatHistory.getMapper().writeValueAsString(result.content()));
                toolResults.add(toolResult);
            }

            chatHistory.addToolResults(toolResults);
        }
    }

    public void close() {
        try {
            this.mcpClient.close();
        } catch (Exception e) {
            // ignore
        }
    }

    private ArrayNode toAnthropicTools(final List<McpSchema.Tool> mcpTools) {
        final ArrayNode tools = chatHistory.getMapper().createArrayNode();
        for (final McpSchema.Tool tool : mcpTools) {
            final ObjectNode t = chatHistory.getMapper().createObjectNode();
            t.put("name", tool.name());
            t.put("description", tool.description());
            t.set("input_schema", chatHistory.getMapper().valueToTree(tool.inputSchema()));
            tools.add(t);
        }
        return tools;
    }

}
