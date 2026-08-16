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

package spade.utility.mcp.server.tool.definition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import spade.utility.mcp.server.tool.type.Type;
import spade.utility.setting.Helper;
import spade.utility.setting.InvalidSettingException;
import spade.utility.setting.SettingConvertException;
import spade.utility.setting.convert.Enums;
import spade.utility.setting.convert.Strings;

public class Parser {

    private static final String keyName        = "tool.name";
    private static final String keyDescription = "tool.description";
    private static final String keyType        = "tool.type";
    private static final String keyProperties  = "tool.properties.yaml";

    private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public static Definition parse(final String configFilePath) throws InvalidSettingException {
        final spade.utility.setting.Setting setting;
        try {
            setting = Helper.create("", configFilePath);
        } catch (Exception e) {
            throw new InvalidSettingException(
                "Failed to read config file '" + configFilePath + "'", e);
        }

        final String name = parseName(setting, configFilePath);
        final String description = parseDescription(setting, configFilePath);
        final Type type = parseType(setting, configFilePath);
        final List<Definition.Property> properties = parseProperties(setting, configFilePath);

        return new Definition(name, description, type, properties);
    }

    private static String parseName(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        try {
            return Strings.getNonBlankString(setting, keyName);
        } catch (SettingConvertException e) {
            throw new InvalidSettingException(
                "Missing/Empty value for '" + keyName + "' in config file '" + configFilePath + "'", e);
        }
    }

    private static String parseDescription(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        try {
            return Strings.getNonBlankString(setting, keyDescription);
        } catch (SettingConvertException e) {
            throw new InvalidSettingException(
                "Missing/Empty value for '" + keyDescription + "' in config file '" + configFilePath + "'", e);
        }
    }

    private static Type parseType(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        try {
            return Enums.getEnum(setting, keyType, Type.class);
        } catch (SettingConvertException e) {
            throw new InvalidSettingException(
                "Missing/Invalid value for '" + keyType + "' in config file '" + configFilePath + "'", e);
        }
    }

    /**
     * The 'properties' key is expected to be a {@code $(text_file <path>)} reference, so its
     * resolved value is the YAML file's raw content rather than a file path.
     */
    private static List<Definition.Property> parseProperties(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        final String yaml;
        try {
            yaml = Strings.optNonBlankString(setting, keyProperties);
        } catch (SettingConvertException e) {
            throw new InvalidSettingException(
                "Invalid value for '" + keyProperties + "' in config file '" + configFilePath + "'", e);
        }
        if (yaml == null) {
            return List.of();
        }

        final List<YamlProperty> yamlProperties;
        try {
            yamlProperties = yamlMapper.readValue(yaml, new TypeReference<List<YamlProperty>>() { });
        } catch (IOException e) {
            throw new InvalidSettingException(
                "Failed to parse YAML for '" + keyProperties + "' in config file '" + configFilePath + "'", e);
        }

        final List<Definition.Property> properties = new ArrayList<>();
        for (final YamlProperty yamlProperty : yamlProperties) {
            properties.add(toProperty(yamlProperty, configFilePath));
        }
        return properties;
    }

    private static Definition.Property toProperty(final YamlProperty yamlProperty, final String configFilePath) throws InvalidSettingException {
        final String name;
        try {
            name = Strings.parseNonBlankString(yamlProperty.name);
        } catch (IllegalArgumentException e) {
            throw new InvalidSettingException(
                "Missing/Empty 'name' for a property in '" + keyProperties + "' in config file '" + configFilePath + "'", e);
        }

        final String type;
        try {
            type = Strings.parseNonBlankString(yamlProperty.type);
        } catch (IllegalArgumentException e) {
            throw new InvalidSettingException(
                "Missing/Empty 'type' for property '" + name + "' in '" + keyProperties + "' in config file '" + configFilePath + "'", e);
        }

        final String description;
        try {
            description = Strings.parseNonBlankString(yamlProperty.description);
        } catch (IllegalArgumentException e) {
            throw new InvalidSettingException(
                "Missing/Empty 'description' for property '" + name + "' in '" + keyProperties + "' in config file '" + configFilePath + "'", e);
        }

        if (yamlProperty.required == null) {
            throw new InvalidSettingException(
                "Missing 'required' for property '" + name + "' in '" + keyProperties + "' in config file '" + configFilePath + "'");
        }

        return new Definition.Property(name, type, description, yamlProperty.possibleValues, yamlProperty.required);
    }

    private static class YamlProperty {
        public String name;
        public String type;
        public String description;
        public List<String> possibleValues;
        public Boolean required;
    }

}
