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

import spade.utility.HelperFunctions;
import spade.utility.Result;

public class Parser {

    private static final String argSpadeHost            = "--spadeHost";
    private static final String argSpadeQueryPort       = "--spadeQueryPort";
    private static final String argSpadeControlPort     = "--spadeControlPort";
    private static final String argMCPServerMode        = "--mcpServerMode";
    private static final String argMCPHttpHostName      = "--mcpHttpHostName";
    private static final String argMCPHttpHostPort      = "--mcpHttpHostPort";
    private static final String argMCPHttpHostEndpoint  = "--mcpHttpHostEndpoint";

    public static void printHelp() {
        System.err.println("Usage:");
        System.err.println("  " + argSpadeHost               + "\t\t=<host>   (required) Hostname of the SPADE server");
        System.err.println("  " + argSpadeQueryPort     + "\t=<port>   (required) Query port on SPADE server");
        System.err.println("  " + argSpadeControlPort   + "\t=<port>   (required) Control port on SPADE server");
        System.err.println("  " + argMCPServerMode   + "\t=(stdio|http)   (required) MCP server mode");
        System.err.println("  " + argMCPHttpHostName   + "\t=<host>   (required) MCP Http host name (if MCP server mode is http)");
        System.err.println("  " + argMCPHttpHostPort   + "\t=<port>   (required) MCP Http host port (if MCP server mode is http)");
        System.err.println("  " + argMCPHttpHostEndpoint   + "\t=<endpoint>   (required) MCP Http host endpoint (if MCP server mode is http)");
    }

    public static Arg parse(final String[] args) throws Exception {
        if (args == null) {
            throw new Exception("NULL args");
        }

        String spadeHost = null;
        String rawSpadeQueryPort = null;
        String rawSpadeControlPort = null;
        String rawMCPServerMode = null;
        String mcpHttpHostName = null;
        String rawMCPHttpHostPort = null;
        String mcpHttpHostEndpoint = null;

        for (final String arg : args) {
            if (arg == null) {
                continue;
            }
            if (arg.startsWith(argSpadeHost + "=")) {
                spadeHost = arg.substring(argSpadeHost.length() + 1).trim();
            } else if (arg.startsWith(argSpadeQueryPort + "=")) {
                rawSpadeQueryPort = arg.substring(argSpadeQueryPort.length() + 1).trim();
            } else if (arg.startsWith(argSpadeControlPort + "=")) {
                rawSpadeControlPort = arg.substring(argSpadeControlPort.length() + 1).trim();
            } else if (arg.startsWith(argMCPServerMode + "=")) {
                rawMCPServerMode = arg.substring(argMCPServerMode.length() + 1).trim();
            } else if (arg.startsWith(argMCPHttpHostName + "=")) {
                mcpHttpHostName = arg.substring(argMCPHttpHostName.length() + 1).trim();
            } else if (arg.startsWith(argMCPHttpHostPort + "=")) {
                rawMCPHttpHostPort = arg.substring(argMCPHttpHostPort.length() + 1).trim();
            } else if (arg.startsWith(argMCPHttpHostEndpoint + "=")) {
                mcpHttpHostEndpoint = arg.substring(argMCPHttpHostEndpoint.length() + 1).trim();
            }
        }

        if (spadeHost == null || spadeHost.isEmpty()) {
            throw new Exception("Missing required argument '" + argSpadeHost + "'");
        }

        if (rawSpadeQueryPort == null) {
            throw new Exception("Missing required argument '" + argSpadeQueryPort + "'");
        }

        if (rawSpadeControlPort == null) {
            throw new Exception("Missing required argument '" + argSpadeControlPort + "'");
        }

        if (rawMCPServerMode == null) {
            throw new Exception("Missing required argument '" + argMCPServerMode + "'");
        }

        final Result<Long> queryPortResult = HelperFunctions.parseLong(rawSpadeQueryPort, 10, 1, Integer.MAX_VALUE);
        if (queryPortResult.error) {
            throw new Exception(
                "Invalid value for argument '" + argSpadeQueryPort + "': " + queryPortResult.toErrorString());
        }

        final Result<Long> controlPortResult = HelperFunctions.parseLong(rawSpadeControlPort, 10, 1, Integer.MAX_VALUE);
        if (controlPortResult.error) {
            throw new Exception(
                "Invalid value for argument '" + argSpadeControlPort + "': " + controlPortResult.toErrorString());
        }

        MCPServerMode mcpServerMode = null;
        for (final MCPServerMode mode : MCPServerMode.values()) {
            if (mode.name.equalsIgnoreCase(rawMCPServerMode)) {
                mcpServerMode = mode;
                break;
            }
        }
        if (mcpServerMode == null) {
            throw new Exception("Invalid value for argument '" + argMCPServerMode + "': '" + rawMCPServerMode + "'");
        }

        int mcpHttpHostPort = -1;
        if (mcpServerMode == MCPServerMode.HTTP) {
            if (mcpHttpHostName == null || mcpHttpHostName.isEmpty()) {
                throw new Exception("Missing required argument '" + argMCPHttpHostName + "' for HTTP mode");
            }
            if (rawMCPHttpHostPort == null) {
                throw new Exception("Missing required argument '" + argMCPHttpHostPort + "' for HTTP mode");
            }
            if (mcpHttpHostEndpoint == null || mcpHttpHostEndpoint.isEmpty()) {
                throw new Exception("Missing required argument '" + argMCPHttpHostEndpoint + "' for HTTP mode");
            }
            final Result<Long> httpPortResult = HelperFunctions.parseLong(rawMCPHttpHostPort, 10, 1, Integer.MAX_VALUE);
            if (httpPortResult.error) {
                throw new Exception(
                    "Invalid value for argument '" + argMCPHttpHostPort + "': " + httpPortResult.toErrorString());
            }
            mcpHttpHostPort = httpPortResult.result.intValue();
        }

        return new Arg(
            spadeHost,
            queryPortResult.result.intValue(),
            controlPortResult.result.intValue(),
            mcpServerMode,
            mcpHttpHostName,
            mcpHttpHostPort,
            mcpHttpHostEndpoint
        );
    }

}
