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

package spade.utility.mcp.client.setting;

import spade.utility.setting.Helper;
import spade.utility.setting.InvalidSettingException;
import spade.utility.setting.SettingConvertException;
import spade.utility.setting.convert.Strings;
import spade.utility.setting.convert.numbers.Ints;

public class Parser {

    private static final String keyMCPHost                 = "mcp.host";
    private static final String keyMCPPort                 = "mcp.port";
    private static final String keyMCPEndpoint              = "mcp.endpoint";
    private static final String keyLLMType                 = "llm.type";
    private static final String keyLLMAnthropicApiKey       = "llm.anthropic.api.key";
    private static final String keyLLMAnthropicModel        = "llm.anthropic.model";
    private static final String keyLLMMockScenario          = "llm.mock.scenario";
    private static final String keyUserMode                = "user.mode";
    private static final String keyUserWebHost              = "user.web.host";
    private static final String keyUserWebPort              = "user.web.port";
    private static final String keyVerbose                 = "verbose";

    public static void printHelp() {
        System.err.println("Usage:");
        System.err.println("  " + keyMCPHost              + "\t\t=<host>                 (required) Hostname of the MCP HTTP server");
        System.err.println("  " + keyMCPPort              + "\t\t=<port>                 (required) Port of the MCP HTTP server");
        System.err.println("  " + keyMCPEndpoint          + "\t\t=<endpoint>             (required) Endpoint of the MCP HTTP server");
        System.err.println("  " + keyLLMType              + "\t\t=(anthropic|mock)       (required) LLM type");
        System.err.println("  " + keyLLMAnthropicApiKey   + "\t=<key>                  (required for anthropic) Anthropic API key");
        System.err.println("  " + keyLLMAnthropicModel    + "\t=<model>                (required for anthropic) Anthropic model name");
        System.err.println("  " + keyLLMMockScenario      + "\t=<name>                 (required for mock) Name of the premade scenario to replay");
        System.err.println("  " + keyUserMode             + "\t\t=(cli|web)              (required) User client mode");
        System.err.println("  " + keyUserWebHost          + "\t\t=<host>                 (required for web) Web server host");
        System.err.println("  " + keyUserWebPort          + "\t\t=<port>                 (required for web) Web server port");
        System.err.println("  " + keyVerbose              + "\t\t=<true|false>           (optional) Enable verbose output");
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
            parseMCP(setting, configFilePath),
            parseLLM(setting, configFilePath),
            parseUser(setting, configFilePath),
            parseOptionalBoolean(setting, keyVerbose)
        );
    }

    private static Setting.MCP parseMCP(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        final String host = parseMCPHost(setting, configFilePath);
        final int port = parseMCPPort(setting, configFilePath);
        final String endpoint = parseMCPEndpoint(setting, configFilePath);
        return new Setting.MCP(host, port, endpoint);
    }

    private static String parseMCPHost(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        try {
            return Strings.getString(setting, keyMCPHost);
        } catch (SettingConvertException e) {
            throw new InvalidSettingException("Missing/Empty value for '" + keyMCPHost + "' in arguments/config file '" + configFilePath + "'", e);
        }
    }

    private static int parseMCPPort(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        try {
            return Ints.get(setting, keyMCPPort, 1, Integer.MAX_VALUE);
        } catch (SettingConvertException e) {
            throw new InvalidSettingException(
                "Invalid/Missing value for '" + keyMCPPort + "' in arguments/config file '" + configFilePath + "'.", e);
        }
    }

    private static String parseMCPEndpoint(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        try {
            return Strings.getString(setting, keyMCPEndpoint);
        } catch (SettingConvertException e) {
            throw new InvalidSettingException("Missing/Empty value for '" + keyMCPEndpoint + "' in arguments/config file '" + configFilePath + "'", e);
        }
    }

    private static Setting.LLM parseLLM(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        final LLMType type = parseLLMType(setting, configFilePath);

        switch (type) {
            case ANTHROPIC:
                return new Setting.LLM(
                    type,
                    parseLLMAnthropicApiKey(setting, configFilePath),
                    parseLLMAnthropicModel(setting, configFilePath),
                    null
                );
            case MOCK:
                return new Setting.LLM(type, null, null, parseLLMMockScenario(setting, configFilePath));
            default:
                throw new InvalidSettingException("Unknown LLM type: " + type.name);
        }
    }

    private static LLMType parseLLMType(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        final String rawType;
        try {
            rawType = Strings.getString(setting, keyLLMType);
        } catch (SettingConvertException e) {
            throw new InvalidSettingException("Missing/Empty value for '" + keyLLMType + "' in arguments/config file '" + configFilePath + "'", e);
        }
        for (final LLMType type : LLMType.values()) {
            if (type.name.equalsIgnoreCase(rawType)) {
                return type;
            }
        }
        throw new InvalidSettingException(
            "Invalid value for '" + keyLLMType + "' in arguments/config file '" + configFilePath + "': '" + rawType + "'");
    }

    private static String parseLLMAnthropicApiKey(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        try {
            return Strings.getString(setting, keyLLMAnthropicApiKey);
        } catch (SettingConvertException e) {
            throw new InvalidSettingException(
                "Missing/Empty value for '" + keyLLMAnthropicApiKey + "' in arguments/config file '" + configFilePath + "' for anthropic llm type", e);
        }
    }

    private static String parseLLMAnthropicModel(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        try {
            return Strings.getString(setting, keyLLMAnthropicModel);
        } catch (SettingConvertException e) {
            throw new InvalidSettingException(
                "Missing/Empty value for '" + keyLLMAnthropicModel + "' in arguments/config file '" + configFilePath + "' for anthropic llm type", e);
        }
    }

    private static String parseLLMMockScenario(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        try {
            return Strings.getString(setting, keyLLMMockScenario);
        } catch (SettingConvertException e) {
            throw new InvalidSettingException(
                "Missing/Empty value for '" + keyLLMMockScenario + "' in arguments/config file '" + configFilePath + "' for mock llm type", e);
        }
    }

    private static Setting.User parseUser(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        final UserClientMode mode = parseUserMode(setting, configFilePath);

        switch (mode) {
            case WEB:
                return new Setting.User(
                    mode,
                    parseUserWebHost(setting, configFilePath),
                    parseUserWebPort(setting, configFilePath)
                );
            case CLI:
                return new Setting.User(mode, null, -1);
            default:
                throw new InvalidSettingException("Unknown user client mode: " + mode.name);
        }
    }

    private static UserClientMode parseUserMode(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        final String rawMode;
        try {
            rawMode = Strings.getString(setting, keyUserMode);
        } catch (SettingConvertException e) {
            throw new InvalidSettingException("Missing/Empty value for '" + keyUserMode + "' in arguments/config file '" + configFilePath + "'", e);
        }
        for (final UserClientMode mode : UserClientMode.values()) {
            if (mode.name.equalsIgnoreCase(rawMode)) {
                return mode;
            }
        }
        throw new InvalidSettingException(
            "Invalid value for '" + keyUserMode + "' in arguments/config file '" + configFilePath + "': '" + rawMode + "'");
    }

    private static String parseUserWebHost(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        try {
            return Strings.getString(setting, keyUserWebHost);
        } catch (SettingConvertException e) {
            throw new InvalidSettingException(
                "Missing/Empty value for '" + keyUserWebHost + "' in arguments/config file '" + configFilePath + "' for web mode", e);
        }
    }

    private static int parseUserWebPort(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        try {
            return Ints.get(setting, keyUserWebPort, 1, Integer.MAX_VALUE);
        } catch (SettingConvertException e) {
            throw new InvalidSettingException(
                "Invalid/Missing value for '" + keyUserWebPort + "' in arguments/config file '" + configFilePath + "' for web mode.", e);
        }
    }

    private static boolean parseOptionalBoolean(final spade.utility.setting.Setting setting, final String key) throws InvalidSettingException {
        final String raw = Strings.optString(setting, key);
        if (raw == null || raw.isEmpty()) {
            return false;
        }
        if (raw.equalsIgnoreCase("true")) {
            return true;
        }
        if (raw.equalsIgnoreCase("false")) {
            return false;
        }
        throw new InvalidSettingException("Invalid value for '" + key + "': '" + raw + "' (expected true|false)");
    }

}
