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

package spade.utility.graph.convert.json;

import spade.utility.setting.Helper;
import spade.utility.setting.InvalidSettingException;
import spade.utility.setting.Setting;
import spade.utility.setting.SettingConvertException;
import spade.utility.setting.convert.Strings;

public class Config {

    private static final String keyVersion = "metadata.version";
    private static final String keyVersionDescription = "metadata.version.description";

    private static final String defaultConfigFilePath = Helper.getDefaultConfigFilePath(Config.class);

    private final Metadata metadata;

    private Config(final Metadata metadata) {
        this.metadata = metadata;
    }

    public static Config load() throws InvalidSettingException {
        return load(defaultConfigFilePath);
    }

    public static Config load(final String configFilePath) throws InvalidSettingException {
        final Setting setting;
        try {
            setting = Helper.create("", configFilePath);
        } catch (Exception e) {
            throw new InvalidSettingException(
                "Failed to read config file '" + configFilePath + "'", e);
        }

        final String version = getVersion(setting, configFilePath);
        final String versionDescription = getVersionDescription(setting, configFilePath);

        return new Config(new Metadata(version, versionDescription));
    }

    private static String getVersion(final Setting setting, final String configFilePath) throws InvalidSettingException {
        try {
            return Strings.getNonBlankString(setting, keyVersion);
        } catch (SettingConvertException e) {
            throw new InvalidSettingException(
                "Missing/Invalid value for '" + keyVersion + "' in config file '" + configFilePath + "'", e);
        }
    }

    private static String getVersionDescription(final Setting setting, final String configFilePath) throws InvalidSettingException {
        try {
            return Strings.getNonBlankString(setting, keyVersionDescription);
        } catch (SettingConvertException e) {
            throw new InvalidSettingException(
                "Missing/Invalid value for '" + keyVersionDescription + "' in config file '" + configFilePath + "'", e);
        }
    }

    public Metadata getMetadata() {
        return metadata;
    }

    @Override
    public String toString() {
        return "Config[metadata=" + metadata + "]";
    }

}
