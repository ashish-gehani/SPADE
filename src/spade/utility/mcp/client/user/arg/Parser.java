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

package spade.utility.mcp.client.user.arg;

import java.util.Map;

import spade.core.Settings;
import spade.utility.HelperFunctions;
import spade.utility.Result;
import spade.utility.mcp.client.user.Client;

public class Parser {

    private static final String defaultConfigFilePath = Settings.getDefaultConfigFilePath(Client.class);
    private static final String serverConfigFilePath =
        Settings.getDefaultConfigFilePath(spade.utility.mcp.server.Server.class);

    private static final String argMcpUrl          = "mcp_url";
    private static final String argAnthropicApiKey = "anthropic_api_key";
    private static final String argAnthropicModel  = "anthropic_model";
    private static final String argUserClientMode  = "user_client_mode";
    private static final String argWebHost         = "web_host";
    private static final String argWebPort         = "web_port";
    private static final String argLlmType         = "llm_type";
    private static final String argVerbose         = "verbose";
    private static final String argOnlyTools       = "only_tools";

    // Fallback keys read from the MCP server's own config file when mcp_url is absent
    private static final String keyServerMCPHttpHostName     = "mcp_http_host_name";
    private static final String keyServerMCPHttpHostPort     = "mcp_http_host_port";
    private static final String keyServerMCPHttpHostEndpoint = "mcp_http_host_endpoint";

    public static void printHelp() {
        System.err.println("Usage:");
        System.err.println("  " + argMcpUrl          + "=<url>                  (optional) URL of the MCP HTTP server. Falls back to config file, then constructed from the MCP server's own config file '" + serverConfigFilePath + "'");
        System.err.println("  " + argLlmType          + "=(anthropic|mock)     (required) LLM type");
        System.err.println("  " + argAnthropicApiKey + "=<key>                  (required for anthropic) Anthropic API key");
        System.err.println("  " + argAnthropicModel  + "=<model>                (required for anthropic) Anthropic model name");
        System.err.println("  " + argUserClientMode  + "=(cli|web)              (required) User client mode");
        System.err.println("  " + argWebHost         + "=<host>                 (required for web) Web server host");
        System.err.println("  " + argWebPort         + "=<port>                 (required for web) Web server port");
        System.err.println("  " + argVerbose         + "=<true|false>           (optional) Enable verbose output");
        System.err.println("  " + argOnlyTools       + "=<true|false>           (optional, mock only) LLM only respond with tool calls");
    }

    public static Arg parse(final String[] args) throws Exception {
        if (args == null) {
            throw new Exception("NULL args");
        }
        return parse(String.join(" ", args), defaultConfigFilePath);
    }

    public static Arg parse(final String arguments, final String configFilePath) throws Exception {
        final Map<String, String> map;
        try {
            map = HelperFunctions.parseKeyValuePairsFrom(arguments == null ? "" : arguments, new String[]{configFilePath});
        } catch (Exception e) {
            throw new Exception("Failed to read arguments and config file '" + configFilePath + "'", e);
        }

        final String mcpUrl = parseMcpUrl(map, configFilePath);
        final LLMType llmType = parseLlmType(map, configFilePath);

        String anthropicApiKey = null;
        String anthropicModel = null;
        if (llmType == LLMType.ANTHROPIC) {
            anthropicApiKey = parseAnthropicApiKey(map, configFilePath);
            anthropicModel = parseAnthropicModel(map, configFilePath);
        }

        final UserClientMode userClientMode = parseUserClientMode(map, configFilePath);

        String webHost = null;
        int webPort = -1;
        if (userClientMode == UserClientMode.WEB) {
            webHost = parseWebHost(map, configFilePath);
            webPort = parseWebPort(map, configFilePath);
        }

        final boolean verbose = parseOptionalBoolean(map, argVerbose);
        final boolean onlyTools = parseOptionalBoolean(map, argOnlyTools);

        return new Arg(mcpUrl, anthropicApiKey, anthropicModel, userClientMode, webHost, webPort, llmType, verbose, onlyTools);
    }

    private static String parseMcpUrl(final Map<String, String> map, final String configFilePath) throws Exception {
        final String mcpUrl = map.get(argMcpUrl);
        if (!HelperFunctions.isNullOrEmpty(mcpUrl)) {
            return mcpUrl;
        }

        // Fall back to constructing it from the MCP server's own HTTP config
        final Map<String, String> serverMap;
        try {
            serverMap = HelperFunctions.parseKeyValuePairsFrom("", new String[]{serverConfigFilePath});
        } catch (Exception e) {
            throw new Exception(
                "Missing '" + argMcpUrl + "' in arguments/config file '" + configFilePath
                    + "', and failed to read MCP server config file '" + serverConfigFilePath + "'", e);
        }
        final String mcpHttpHostName = serverMap.get(keyServerMCPHttpHostName);
        final String mcpHttpHostPort = serverMap.get(keyServerMCPHttpHostPort);
        final String mcpHttpHostEndpoint = serverMap.get(keyServerMCPHttpHostEndpoint);
        if (HelperFunctions.isNullOrEmpty(mcpHttpHostName)
                || HelperFunctions.isNullOrEmpty(mcpHttpHostPort)
                || HelperFunctions.isNullOrEmpty(mcpHttpHostEndpoint)) {
            throw new Exception(
                "Missing '" + argMcpUrl + "' in arguments/config file '" + configFilePath
                    + "', and MCP server config file '" + serverConfigFilePath + "' does not have all of '"
                    + keyServerMCPHttpHostName + "', '" + keyServerMCPHttpHostPort + "', '" + keyServerMCPHttpHostEndpoint + "'");
        }
        return "http://" + mcpHttpHostName + ":" + mcpHttpHostPort + mcpHttpHostEndpoint;
    }

    private static LLMType parseLlmType(final Map<String, String> map, final String configFilePath) throws Exception {
        final String rawLlmType = map.get(argLlmType);
        if (HelperFunctions.isNullOrEmpty(rawLlmType)) {
            throw new Exception("Missing/Empty value for '" + argLlmType + "' in arguments/config file '" + configFilePath + "'");
        }
        for (final LLMType type : LLMType.values()) {
            if (type.name.equalsIgnoreCase(rawLlmType)) {
                return type;
            }
        }
        throw new Exception(
            "Invalid value for '" + argLlmType + "' in arguments/config file '" + configFilePath + "': '" + rawLlmType + "'");
    }

    private static String parseAnthropicApiKey(final Map<String, String> map, final String configFilePath) throws Exception {
        final String anthropicApiKey = map.get(argAnthropicApiKey);
        if (HelperFunctions.isNullOrEmpty(anthropicApiKey)) {
            throw new Exception(
                "Missing/Empty value for '" + argAnthropicApiKey + "' in arguments/config file '" + configFilePath + "' for anthropic llm type");
        }
        return anthropicApiKey;
    }

    private static String parseAnthropicModel(final Map<String, String> map, final String configFilePath) throws Exception {
        final String anthropicModel = map.get(argAnthropicModel);
        if (HelperFunctions.isNullOrEmpty(anthropicModel)) {
            throw new Exception(
                "Missing/Empty value for '" + argAnthropicModel + "' in arguments/config file '" + configFilePath + "' for anthropic llm type");
        }
        return anthropicModel;
    }

    private static UserClientMode parseUserClientMode(final Map<String, String> map, final String configFilePath) throws Exception {
        final String rawUserClientMode = map.get(argUserClientMode);
        if (HelperFunctions.isNullOrEmpty(rawUserClientMode)) {
            throw new Exception("Missing/Empty value for '" + argUserClientMode + "' in arguments/config file '" + configFilePath + "'");
        }
        for (final UserClientMode mode : UserClientMode.values()) {
            if (mode.name.equalsIgnoreCase(rawUserClientMode)) {
                return mode;
            }
        }
        throw new Exception(
            "Invalid value for '" + argUserClientMode + "' in arguments/config file '" + configFilePath + "': '" + rawUserClientMode + "'");
    }

    private static String parseWebHost(final Map<String, String> map, final String configFilePath) throws Exception {
        final String webHost = map.get(argWebHost);
        if (HelperFunctions.isNullOrEmpty(webHost)) {
            throw new Exception(
                "Missing/Empty value for '" + argWebHost + "' in arguments/config file '" + configFilePath + "' for web mode");
        }
        return webHost;
    }

    private static int parseWebPort(final Map<String, String> map, final String configFilePath) throws Exception {
        final String rawWebPort = map.get(argWebPort);
        final Result<Long> webPortResult = HelperFunctions.parseLong(rawWebPort, 10, 1, Integer.MAX_VALUE);
        if (webPortResult.error) {
            throw new Exception(
                "Invalid value for '" + argWebPort + "' in arguments/config file '" + configFilePath + "'. "
                    + webPortResult.toErrorString());
        }
        return webPortResult.result.intValue();
    }

    private static boolean parseOptionalBoolean(final Map<String, String> map, final String key) throws Exception {
        final String raw = map.get(key);
        if (HelperFunctions.isNullOrEmpty(raw)) {
            return false;
        }
        final Result<Boolean> result = HelperFunctions.parseBoolean(raw);
        if (result.error) {
            throw new Exception("Invalid value for '" + key + "': " + result.toErrorString());
        }
        return result.result;
    }

}
