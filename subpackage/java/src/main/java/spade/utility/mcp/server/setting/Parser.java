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

package spade.utility.mcp.server.setting;

import spade.utility.setting.Helper;
import spade.utility.setting.InvalidSettingException;
import spade.utility.setting.SettingConvertException;
import spade.utility.setting.convert.Strings;
import spade.utility.setting.convert.numbers.Ints;

public class Parser {

    private static final String keySpadeHost            = "spade.host";
    private static final String keySpadeQueryPort       = "spade.query.port";
    private static final String keySpadeControlPort     = "spade.control.port";
    private static final String keyMCPServerMode        = "mcp.server.mode";
    private static final String keyMCPHttpHostName      = "mcp.http.host.name";
    private static final String keyMCPHttpHostPort      = "mcp.http.host.port";
    private static final String keyMCPHttpHostEndpoint  = "mcp.http.host.endpoint";

    /**
     * @param defaultConfigFilePath the config file path {@code parse} falls back to when a key
     *                               is absent from arguments; owned by the caller (e.g. Main).
     */
    public static void printHelp(final String defaultConfigFilePath) {
        System.err.println("Usage:");
        System.err.println("  " + keySpadeHost               + "\t\t=<host>   Hostname of the SPADE server (falls back to config file '" + defaultConfigFilePath + "')");
        System.err.println("  " + keySpadeQueryPort           + "\t=<port>   Query port on SPADE server (falls back to config file '" + defaultConfigFilePath + "')");
        System.err.println("  " + keySpadeControlPort         + "\t=<port>   Control port on SPADE server (falls back to config file '" + defaultConfigFilePath + "')");
        System.err.println("  " + keyMCPServerMode            + "\t=(stdio|http)   MCP server mode (falls back to config file)");
        System.err.println("  " + keyMCPHttpHostName          + "\t=<host>   MCP Http host name (required if MCP server mode is http; falls back to config file)");
        System.err.println("  " + keyMCPHttpHostPort          + "\t=<port>   MCP Http host port (required if MCP server mode is http; falls back to config file)");
        System.err.println("  " + keyMCPHttpHostEndpoint      + "\t=<endpoint>   MCP Http host endpoint (required if MCP server mode is http; falls back to config file)");
    }

    public static Setting parse(final String arguments, final String configFilePath) throws InvalidSettingException {
        final spade.utility.setting.Setting setting;
        try {
            setting = Helper.create(arguments, configFilePath);
        } catch (Exception e) {
            throw new InvalidSettingException(
                "Failed to read arguments and config file '" + configFilePath + "'", e);
        }

        return new Setting(
            parseSpade(setting, configFilePath),
            parseMCP(setting, configFilePath)
        );
    }

    private static Setting.Spade parseSpade(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        final String host = parseSpadeHost(setting, configFilePath);
        final int queryPort = parseSpadeQueryPort(setting, configFilePath);
        final int controlPort = parseSpadeControlPort(setting, configFilePath);
        return new Setting.Spade(host, queryPort, controlPort);
    }

    private static Setting.MCP parseMCP(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        final ServerMode serverMode = parseMCPServerMode(setting, configFilePath);

        switch (serverMode) {
            case HTTP:
                return parseMCPHTTP(setting, configFilePath);
            case STDIO:
                return parseMCPSTDIO(setting, configFilePath);
            default:
                throw new InvalidSettingException("Unknown MCP server mode: " + serverMode.name);
        }
    }

    private static Setting.MCP parseMCPHTTP(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        final String httpHostName = parseMCPHttpHostName(setting, configFilePath);
        final int httpHostPort = parseMCPHttpHostPort(setting, configFilePath);
        final String httpHostEndpoint = parseMCPHttpHostEndpoint(setting, configFilePath);
        return new Setting.MCP(ServerMode.HTTP, httpHostName, httpHostPort, httpHostEndpoint);
    }

    private static Setting.MCP parseMCPSTDIO(final spade.utility.setting.Setting setting, final String configFilePath) {
        // STDIO mode has no additional settings; kept as its own function to mirror parseMCPHTTP.
        return new Setting.MCP(ServerMode.STDIO, null, -1, null);
    }

    private static String parseSpadeHost(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        try {
            return Strings.getString(setting, keySpadeHost);
        } catch (SettingConvertException e) {
            throw new InvalidSettingException("Missing/Empty value for '" + keySpadeHost + "' in arguments/config file '" + configFilePath + "'", e);
        }
    }

    private static int parseSpadeQueryPort(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        try {
            return Ints.get(setting, keySpadeQueryPort, 1, Integer.MAX_VALUE);
        } catch (SettingConvertException e) {
            throw new InvalidSettingException(
                "Invalid/Missing value for '" + keySpadeQueryPort + "' in arguments/config file '" + configFilePath + "'.", e);
        }
    }

    private static int parseSpadeControlPort(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        try {
            return Ints.get(setting, keySpadeControlPort, 1, Integer.MAX_VALUE);
        } catch (SettingConvertException e) {
            throw new InvalidSettingException(
                "Invalid/Missing value for '" + keySpadeControlPort + "' in arguments/config file '" + configFilePath + "'.", e);
        }
    }

    private static ServerMode parseMCPServerMode(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        final String rawMCPServerMode;
        try {
            rawMCPServerMode = Strings.getString(setting, keyMCPServerMode);
        } catch (SettingConvertException e) {
            throw new InvalidSettingException("Missing/Empty value for '" + keyMCPServerMode + "' in arguments/config file '" + configFilePath + "'", e);
        }
        for (final ServerMode mode : ServerMode.values()) {
            if (mode.name.equalsIgnoreCase(rawMCPServerMode)) {
                return mode;
            }
        }
        throw new InvalidSettingException(
            "Invalid value for '" + keyMCPServerMode + "' in arguments/config file '" + configFilePath + "': '" + rawMCPServerMode + "'");
    }

    private static String parseMCPHttpHostName(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        try {
            return Strings.getString(setting, keyMCPHttpHostName);
        } catch (SettingConvertException e) {
            throw new InvalidSettingException(
                "Missing/Empty value for '" + keyMCPHttpHostName + "' in arguments/config file '" + configFilePath + "' for HTTP mode", e);
        }
    }

    private static int parseMCPHttpHostPort(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        try {
            return Ints.get(setting, keyMCPHttpHostPort, 1, Integer.MAX_VALUE);
        } catch (SettingConvertException e) {
            throw new InvalidSettingException(
                "Invalid value for '" + keyMCPHttpHostPort + "' in arguments/config file '" + configFilePath + "'.", e);
        }
    }

    private static String parseMCPHttpHostEndpoint(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        try {
            return Strings.getString(setting, keyMCPHttpHostEndpoint);
        } catch (SettingConvertException e) {
            throw new InvalidSettingException(
                "Missing/Empty value for '" + keyMCPHttpHostEndpoint + "' in arguments/config file '" + configFilePath + "' for HTTP mode", e);
        }
    }

}
