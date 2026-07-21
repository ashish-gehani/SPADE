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

package spade.utility.mcp.server.arg;

import java.util.Map;

import spade.core.Kernel;
import spade.core.Settings;
import spade.utility.HelperFunctions;
import spade.utility.Result;
import spade.utility.mcp.server.Server;

public class Parser {

    private static final String defaultConfigFilePath = Settings.getDefaultConfigFilePath(Server.class);
    private static final String kernelConfigFilePath = Settings.getDefaultConfigFilePath(Kernel.class);

    private static final String argSpadeHost            = "spade_host";
    private static final String argSpadeQueryPort       = "spade_query_port";
    private static final String argSpadeControlPort     = "spade_control_port";
    private static final String argMCPServerMode        = "mcp_server_mode";
    private static final String argMCPHttpHostName      = "mcp_http_host_name";
    private static final String argMCPHttpHostPort      = "mcp_http_host_port";
    private static final String argMCPHttpHostEndpoint  = "mcp_http_host_endpoint";

    // Fallback keys read from spade.core.Kernel.config when the keys above are absent
    private static final String keyKernelCommandLineQueryPort = "commandline_query_port";
    private static final String keyKernelLocalControlPort     = "local_control_port";

    public static void printHelp() {
        System.err.println("Usage:");
        System.err.println("  " + argSpadeHost               + "\t\t=<host>   Hostname of the SPADE server (falls back to config file '" + defaultConfigFilePath + "')");
        System.err.println("  " + argSpadeQueryPort           + "\t=<port>   Query port on SPADE server (falls back to config file, then to '" + keyKernelCommandLineQueryPort + "' in '" + kernelConfigFilePath + "')");
        System.err.println("  " + argSpadeControlPort         + "\t=<port>   Control port on SPADE server (falls back to config file, then to '" + keyKernelLocalControlPort + "' in '" + kernelConfigFilePath + "')");
        System.err.println("  " + argMCPServerMode            + "\t=(stdio|http)   MCP server mode (falls back to config file)");
        System.err.println("  " + argMCPHttpHostName          + "\t=<host>   MCP Http host name (required if MCP server mode is http; falls back to config file)");
        System.err.println("  " + argMCPHttpHostPort          + "\t=<port>   MCP Http host port (required if MCP server mode is http; falls back to config file)");
        System.err.println("  " + argMCPHttpHostEndpoint      + "\t=<endpoint>   MCP Http host endpoint (required if MCP server mode is http; falls back to config file)");
    }

    public static Arg parse(final String[] args) throws Exception {
        if (args == null) {
            throw new Exception("NULL args");
        }
        return parse(toArguments(args), defaultConfigFilePath);
    }

    public static Arg parse(final String arguments, final String configFilePath) throws Exception {
        final Map<String, String> map;
        try {
            map = HelperFunctions.parseKeyValuePairsFrom(
                arguments == null ? "" : arguments, new String[]{configFilePath, kernelConfigFilePath});
        } catch (Exception e) {
            throw new Exception(
                "Failed to read arguments and config files '" + configFilePath + "', '" + kernelConfigFilePath + "'", e);
        }

        final String spadeHost = parseSpadeHost(map, configFilePath);
        final int spadeQueryPort = parseSpadeQueryPort(map, configFilePath);
        final int spadeControlPort = parseSpadeControlPort(map, configFilePath);
        final MCPServerMode mcpServerMode = parseMCPServerMode(map, configFilePath);

        String mcpHttpHostName = null;
        int mcpHttpHostPort = -1;
        String mcpHttpHostEndpoint = null;
        if (mcpServerMode == MCPServerMode.HTTP) {
            mcpHttpHostName = parseMCPHttpHostName(map, configFilePath);
            mcpHttpHostPort = parseMCPHttpHostPort(map, configFilePath);
            mcpHttpHostEndpoint = parseMCPHttpHostEndpoint(map, configFilePath);
        }

        return new Arg(
            spadeHost,
            spadeQueryPort,
            spadeControlPort,
            mcpServerMode,
            mcpHttpHostName,
            mcpHttpHostPort,
            mcpHttpHostEndpoint
        );
    }

    private static String toArguments(final String[] args) {
        final StringBuilder sb = new StringBuilder();
        for (final String arg : args) {
            if (arg == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(arg);
        }
        return sb.toString();
    }

    private static String parseSpadeHost(final Map<String, String> map, final String configFilePath) throws Exception {
        final String spadeHost = map.get(argSpadeHost);
        if (HelperFunctions.isNullOrEmpty(spadeHost)) {
            throw new Exception("Missing/Empty value for '" + argSpadeHost + "' in arguments/config file '" + configFilePath + "'");
        }
        return spadeHost;
    }

    private static int parseSpadeQueryPort(final Map<String, String> map, final String configFilePath) throws Exception {
        String rawSpadeQueryPort = map.get(argSpadeQueryPort);
        if (HelperFunctions.isNullOrEmpty(rawSpadeQueryPort)) {
            // Fall back to the SPADE Kernel's own commandline_query_port
            rawSpadeQueryPort = map.get(keyKernelCommandLineQueryPort);
        }
        final Result<Long> queryPortResult = HelperFunctions.parseLong(rawSpadeQueryPort, 10, 1, Integer.MAX_VALUE);
        if (queryPortResult.error) {
            throw new Exception(
                "Invalid value for '" + argSpadeQueryPort + "' in arguments/config file '" + configFilePath + "'. "
                    + queryPortResult.toErrorString());
        }
        return queryPortResult.result.intValue();
    }

    private static int parseSpadeControlPort(final Map<String, String> map, final String configFilePath) throws Exception {
        String rawSpadeControlPort = map.get(argSpadeControlPort);
        if (HelperFunctions.isNullOrEmpty(rawSpadeControlPort)) {
            // Fall back to the SPADE Kernel's own local_control_port
            rawSpadeControlPort = map.get(keyKernelLocalControlPort);
        }
        final Result<Long> controlPortResult = HelperFunctions.parseLong(rawSpadeControlPort, 10, 1, Integer.MAX_VALUE);
        if (controlPortResult.error) {
            throw new Exception(
                "Invalid value for '" + argSpadeControlPort + "' in arguments/config file '" + configFilePath + "'. "
                    + controlPortResult.toErrorString());
        }
        return controlPortResult.result.intValue();
    }

    private static MCPServerMode parseMCPServerMode(final Map<String, String> map, final String configFilePath) throws Exception {
        final String rawMCPServerMode = map.get(argMCPServerMode);
        if (HelperFunctions.isNullOrEmpty(rawMCPServerMode)) {
            throw new Exception("Missing/Empty value for '" + argMCPServerMode + "' in arguments/config file '" + configFilePath + "'");
        }
        for (final MCPServerMode mode : MCPServerMode.values()) {
            if (mode.name.equalsIgnoreCase(rawMCPServerMode)) {
                return mode;
            }
        }
        throw new Exception(
            "Invalid value for '" + argMCPServerMode + "' in arguments/config file '" + configFilePath + "': '" + rawMCPServerMode + "'");
    }

    private static String parseMCPHttpHostName(final Map<String, String> map, final String configFilePath) throws Exception {
        final String mcpHttpHostName = map.get(argMCPHttpHostName);
        if (HelperFunctions.isNullOrEmpty(mcpHttpHostName)) {
            throw new Exception(
                "Missing/Empty value for '" + argMCPHttpHostName + "' in arguments/config file '" + configFilePath + "' for HTTP mode");
        }
        return mcpHttpHostName;
    }

    private static int parseMCPHttpHostPort(final Map<String, String> map, final String configFilePath) throws Exception {
        final String rawMCPHttpHostPort = map.get(argMCPHttpHostPort);
        final Result<Long> httpPortResult = HelperFunctions.parseLong(rawMCPHttpHostPort, 10, 1, Integer.MAX_VALUE);
        if (httpPortResult.error) {
            throw new Exception(
                "Invalid value for '" + argMCPHttpHostPort + "' in arguments/config file '" + configFilePath + "'. "
                    + httpPortResult.toErrorString());
        }
        return httpPortResult.result.intValue();
    }

    private static String parseMCPHttpHostEndpoint(final Map<String, String> map, final String configFilePath) throws Exception {
        final String mcpHttpHostEndpoint = map.get(argMCPHttpHostEndpoint);
        if (HelperFunctions.isNullOrEmpty(mcpHttpHostEndpoint)) {
            throw new Exception(
                "Missing/Empty value for '" + argMCPHttpHostEndpoint + "' in arguments/config file '" + configFilePath + "' for HTTP mode");
        }
        return mcpHttpHostEndpoint;
    }

}
