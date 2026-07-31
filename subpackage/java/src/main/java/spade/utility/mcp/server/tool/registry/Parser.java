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

package spade.utility.mcp.server.tool.registry;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import spade.utility.setting.Helper;
import spade.utility.setting.InvalidSettingException;
import spade.utility.setting.SettingConvertException;
import spade.utility.setting.convert.CSV;
import spade.utility.setting.convert.Files;

public class Parser {

    private static final String keyToolConfigs = "registry.tools";

    public static Config parse(final String configFilePath) throws InvalidSettingException {
        final spade.utility.setting.Setting setting;
        try {
            setting = Helper.create("", configFilePath);
        } catch (Exception e) {
            throw new InvalidSettingException(
                "Failed to read config file '" + configFilePath + "'", e);
        }

        final List<String> rawToolConfigFilePaths = parseRawToolConfigFilePaths(setting, configFilePath);
        final List<File> toolConfigFilePaths = new ArrayList<>();
        for (final String rawToolConfigFilePath : rawToolConfigFilePaths) {
            toolConfigFilePaths.add(parseReadableFile(rawToolConfigFilePath, configFilePath));
        }

        return new Config(toolConfigFilePaths);
    }

    private static List<String> parseRawToolConfigFilePaths(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        try {
            return CSV.getCommaSeparatedStrings(setting, keyToolConfigs);
        } catch (SettingConvertException e) {
            throw new InvalidSettingException(
                "Missing/Invalid value for '" + keyToolConfigs + "' in config file '" + configFilePath + "'", e);
        }
    }

    private static File parseReadableFile(final String rawToolConfigFilePath, final String configFilePath) throws InvalidSettingException {
        try {
            return Files.parseReadableFile(rawToolConfigFilePath);
        } catch (IllegalArgumentException e) {
            throw new InvalidSettingException(
                "Invalid value '" + rawToolConfigFilePath + "' for '" + keyToolConfigs + "' in config file '" + configFilePath + "': " + e.getMessage(), e);
        }
    }

}
