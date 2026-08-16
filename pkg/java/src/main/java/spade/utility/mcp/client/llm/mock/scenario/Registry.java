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

package spade.utility.mcp.client.llm.mock.scenario;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import spade.utility.mcp.client.llm.mock.ToolCall;
import spade.utility.setting.Helper;
import spade.utility.setting.InvalidSettingException;
import spade.utility.setting.SettingConvertException;
import spade.utility.setting.convert.Strings;

public class Registry {

    private static final String defaultConfigFilePath = Helper.getDefaultConfigFilePath(Registry.class);

    private final String configFilePath;
    private final Map<String, Scenario> scenarios;

    public Registry(final ObjectMapper mapper) throws InvalidSettingException {
        this(mapper, defaultConfigFilePath);
    }

    public Registry(final ObjectMapper mapper, final String configFilePath) throws InvalidSettingException {
        this.configFilePath = configFilePath;

        final spade.utility.setting.Setting setting;
        try {
            setting = Helper.create("", configFilePath);
        } catch (Exception e) {
            throw new InvalidSettingException("Failed to read config file '" + configFilePath + "'", e);
        }

        final Map<String, Scenario> scenarios = new LinkedHashMap<>();
        for (int i = 1; ; i++) {
            final String name = Strings.optString(setting, "mock.scenario." + i + ".name");
            if (name == null) {
                break;
            }
            final Scenario scenario = parseScenario(setting, mapper, i, name);
            scenarios.put(scenario.getName(), scenario);
        }
        this.scenarios = Collections.unmodifiableMap(scenarios);
    }

    private Scenario parseScenario(
        final spade.utility.setting.Setting setting,
        final ObjectMapper mapper,
        final int scenarioIndex,
        final String scenarioName
    ) throws InvalidSettingException {
        final List<ToolCall> toolCalls = new ArrayList<>();
        for (int j = 1; ; j++) {
            final String prefix = "mock.scenario." + scenarioIndex + ".tool." + j + ".";
            final String toolCallName = Strings.optString(setting, prefix + "name");
            if (toolCallName == null) {
                break;
            }
            toolCalls.add(parseToolCall(setting, mapper, prefix, toolCallName));
        }
        return new Scenario(scenarioName, toolCalls);
    }

    private ToolCall parseToolCall(
        final spade.utility.setting.Setting setting,
        final ObjectMapper mapper,
        final String prefix,
        final String toolCallName
    ) throws InvalidSettingException {
        final String toolName;
        try {
            toolName = Strings.getString(setting, prefix + "tool.name");
        } catch (SettingConvertException e) {
            throw new InvalidSettingException(
                "Missing/Empty value for '" + prefix + "tool.name' in config file '" + configFilePath + "'", e);
        }

        final String rawInput = Strings.optString(setting, prefix + "input");
        final ObjectNode input;
        if (rawInput == null || rawInput.isEmpty()) {
            input = mapper.createObjectNode();
        } else {
            final JsonNode parsed;
            try {
                parsed = mapper.readTree(rawInput);
            } catch (Exception e) {
                throw new InvalidSettingException(
                    "Invalid JSON for '" + prefix + "input' in config file '" + configFilePath + "': '" + rawInput + "'", e);
            }
            if (!parsed.isObject()) {
                throw new InvalidSettingException(
                    "Value for '" + prefix + "input' in config file '" + configFilePath + "' must be a JSON object: '" + rawInput + "'");
            }
            input = (ObjectNode) parsed;
        }

        final String result;
        try {
            result = Strings.getString(setting, prefix + "result");
        } catch (SettingConvertException e) {
            throw new InvalidSettingException(
                "Missing/Empty value for '" + prefix + "result' in config file '" + configFilePath + "'", e);
        }

        return new ToolCall(toolCallName, toolName, input, result);
    }

    public List<String> names() {
        return List.copyOf(scenarios.keySet());
    }

    public Scenario get(final String name) {
        return scenarios.get(name);
    }

}
