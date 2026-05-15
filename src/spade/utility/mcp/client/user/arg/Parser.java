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

public class Parser {

    private static final String argMcpUrl          = "--mcpUrl";
    private static final String argAnthropicApiKey  = "--anthropicApiKey";
    private static final String argAnthropicModel   = "--anthropicModel";
    private static final String argUserClientMode   = "--userClientMode";
    private static final String argWebHost          = "--webHost";
    private static final String argWebPort          = "--webPort";
    private static final String argLlmType          = "--llmType";
    private static final String argVerbose          = "--verbose";
    private static final String argOnlyTools        = "--only-tools";

    public static void printHelp() {
        System.err.println("Usage:");
        System.err.println("  " + argMcpUrl          + "=<url>                  (required) URL of the MCP HTTP server");
        System.err.println("  " + argLlmType          + "=(anthropic|mock)     (required) LLM type");
        System.err.println("  " + argAnthropicApiKey + "=<key>                  (required for anthropic) Anthropic API key");
        System.err.println("  " + argAnthropicModel  + "=<model>                (required for anthropic) Anthropic model name");
        System.err.println("  " + argUserClientMode  + "=(cli|web)              (required) User client mode");
        System.err.println("  " + argWebHost         + "=<host>                 (required for web) Web server host");
        System.err.println("  " + argWebPort         + "=<port>                 (required for web) Web server port");
        System.err.println("  " + argVerbose         + "                        (optional) Enable verbose output");
        System.err.println("  " + argOnlyTools       + "                     (optional, mock only) LLM only respond with tool calls");
    }

    public static Arg parse(final String[] args) throws Exception {
        if (args == null) {
            throw new Exception("NULL args");
        }

        String mcpUrl = null;
        String anthropicApiKey = null;
        String anthropicModel = null;
        String rawUserClientMode = null;
        String webHost = null;
        String rawWebPort = null;
        String rawLlmType = null;
        boolean verbose = false;
        boolean onlyTools = false;

        for (final String arg : args) {
            if (arg == null) {
                continue;
            }
            if (arg.startsWith(argMcpUrl + "=")) {
                mcpUrl = arg.substring(argMcpUrl.length() + 1).trim();
            } else if (arg.startsWith(argAnthropicApiKey + "=")) {
                anthropicApiKey = arg.substring(argAnthropicApiKey.length() + 1).trim();
            } else if (arg.startsWith(argAnthropicModel + "=")) {
                anthropicModel = arg.substring(argAnthropicModel.length() + 1).trim();
            } else if (arg.startsWith(argUserClientMode + "=")) {
                rawUserClientMode = arg.substring(argUserClientMode.length() + 1).trim();
            } else if (arg.startsWith(argWebHost + "=")) {
                webHost = arg.substring(argWebHost.length() + 1).trim();
            } else if (arg.startsWith(argWebPort + "=")) {
                rawWebPort = arg.substring(argWebPort.length() + 1).trim();
            } else if (arg.startsWith(argLlmType + "=")) {
                rawLlmType = arg.substring(argLlmType.length() + 1).trim();
            } else if (arg.equals(argVerbose)) {
                verbose = true;
            } else if (arg.equals(argOnlyTools)) {
                onlyTools = true;
            }
        }

        if (mcpUrl == null || mcpUrl.isEmpty()) {
            throw new Exception("Missing required argument '" + argMcpUrl + "'");
        }
        if (rawLlmType == null) {
            throw new Exception("Missing required argument '" + argLlmType + "'");
        }
        if (rawUserClientMode == null) {
            throw new Exception("Missing required argument '" + argUserClientMode + "'");
        }

        LLMType llmType = null;
        for (final LLMType type : LLMType.values()) {
            if (type.name.equalsIgnoreCase(rawLlmType)) {
                llmType = type;
                break;
            }
        }
        if (llmType == null) {
            throw new Exception("Invalid value for argument '" + argLlmType + "': '" + rawLlmType + "'");
        }

        if (llmType == LLMType.ANTHROPIC) {
            if (anthropicApiKey == null || anthropicApiKey.isEmpty()) {
                throw new Exception("Missing required argument '" + argAnthropicApiKey + "' for anthropic llm type");
            }
            if (anthropicModel == null || anthropicModel.isEmpty()) {
                throw new Exception("Missing required argument '" + argAnthropicModel + "' for anthropic llm type");
            }
        }

        UserClientMode userClientMode = null;
        for (final UserClientMode mode : UserClientMode.values()) {
            if (mode.name.equalsIgnoreCase(rawUserClientMode)) {
                userClientMode = mode;
                break;
            }
        }
        if (userClientMode == null) {
            throw new Exception("Invalid value for argument '" + argUserClientMode + "': '" + rawUserClientMode + "'");
        }

        int webPort = -1;
        if (userClientMode == UserClientMode.WEB) {
            if (webHost == null || webHost.isEmpty()) {
                throw new Exception("Missing required argument '" + argWebHost + "' for web mode");
            }
            if (rawWebPort == null) {
                throw new Exception("Missing required argument '" + argWebPort + "' for web mode");
            }
            try {
                webPort = Integer.parseInt(rawWebPort);
            } catch (NumberFormatException e) {
                throw new Exception("Invalid value for argument '" + argWebPort + "': '" + rawWebPort + "'");
            }
        }

        return new Arg(mcpUrl, anthropicApiKey, anthropicModel, userClientMode, webHost, webPort, llmType, verbose, onlyTools);
    }

}
