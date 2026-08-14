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

package spade.storage.kafka.setting;

import spade.utility.setting.Helper;
import spade.utility.setting.InvalidSettingException;
import spade.utility.setting.SettingConvertException;
import spade.utility.setting.convert.Strings;

public class Parser {

    private static final String keyOutputFile      = "kafka.output.file";
    private static final String keySchema           = "kafka.schema";
    private static final String keyServer           = "kafka.output.server";
    private static final String keyTopic            = "kafka.output.topic";
    private static final String keyProducerId       = "kafka.output.producer.id";

    public static Setting parse(final String arguments, final String configFilePath) throws InvalidSettingException {
        final spade.utility.setting.Setting setting;
        try {
            setting = Helper.create(arguments, configFilePath);
        } catch (Exception e) {
            throw new InvalidSettingException(
                "Failed to read arguments and config file '" + configFilePath + "'", e);
        }

        final String schema = parseSchema(setting, configFilePath);
        final Setting.Output output = isFileMode(setting) ? parseOutput(setting, configFilePath) : null;
        final Setting.Server server = isServerMode(setting) ? parseServer(setting, configFilePath) : null;

        return new Setting(schema, output, server);
    }

    /**
     * True if a file writer should be created, i.e. {@code kafka.output.file} is set.
     */
    public static boolean isFileMode(final spade.utility.setting.Setting rawSetting) {
        return rawSetting.getKeyValue(keyOutputFile) != null;
    }

    /**
     * True if a server writer should be created: either one of the server keys is explicitly
     * set, or {@code kafka.output.file} is absent (server-only is the default when nothing is
     * set at all).
     */
    public static boolean isServerMode(final spade.utility.setting.Setting rawSetting) {
        return rawSetting.getKeyValue(keyServer) != null
            || rawSetting.getKeyValue(keyTopic) != null
            || rawSetting.getKeyValue(keyProducerId) != null
            || rawSetting.getKeyValue(keyOutputFile) == null;
    }

    private static String parseSchema(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        try {
            return Strings.getString(setting, keySchema);
        } catch (SettingConvertException e) {
            throw new InvalidSettingException("Missing/Empty value for '" + keySchema + "' in arguments/config file '" + configFilePath + "'", e);
        }
    }

    private static Setting.Output parseOutput(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        try {
            return new Setting.Output(Strings.getString(setting, keyOutputFile));
        } catch (SettingConvertException e) {
            throw new InvalidSettingException("Missing/Empty value for '" + keyOutputFile + "' in arguments/config file '" + configFilePath + "'", e);
        }
    }

    private static Setting.Server parseServer(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        return new Setting.Server(
            parseServerHost(setting, configFilePath),
            parseServerTopic(setting, configFilePath),
            parseServerProducerId(setting, configFilePath)
        );
    }

    private static String parseServerHost(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        try {
            return Strings.getString(setting, keyServer);
        } catch (SettingConvertException e) {
            throw new InvalidSettingException("Missing/Empty value for '" + keyServer + "' in arguments/config file '" + configFilePath + "'", e);
        }
    }

    private static String parseServerTopic(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        try {
            return Strings.getString(setting, keyTopic);
        } catch (SettingConvertException e) {
            throw new InvalidSettingException("Missing/Empty value for '" + keyTopic + "' in arguments/config file '" + configFilePath + "'", e);
        }
    }

    private static String parseServerProducerId(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        try {
            return Strings.getString(setting, keyProducerId);
        } catch (SettingConvertException e) {
            throw new InvalidSettingException("Missing/Empty value for '" + keyProducerId + "' in arguments/config file '" + configFilePath + "'", e);
        }
    }

}
