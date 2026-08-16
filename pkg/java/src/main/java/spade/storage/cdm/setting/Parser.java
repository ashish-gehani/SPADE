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

package spade.storage.cdm.setting;

import spade.utility.setting.Helper;
import spade.utility.setting.InvalidSettingException;
import spade.utility.setting.SettingConvertException;
import spade.utility.setting.convert.Booleans;
import spade.utility.setting.convert.Files;
import spade.utility.setting.convert.Strings;
import spade.utility.setting.convert.numbers.Ints;

public class Parser {

    private static final String keyHexUUIDs                 = "cdm.hex.uuids";
    private static final String keySession                  = "cdm.session";
    private static final String keyCreateHostConfig         = "cdm.create.host.config";
    private static final String keyHostFile                 = "cdm.host.file";
    private static final String keyReportingIntervalSeconds = "cdm.reporting.interval.seconds";
    private static final String keySsl                      = "cdm.ssl";
    private static final String keySslSecurityProtocol      = "cdm.ssl.security.protocol";
    private static final String keySslTrustStoreLocation    = "cdm.ssl.trust.store.location";
    private static final String keySslTrustStorePassword    = "cdm.ssl.trust.store.password";
    private static final String keySslKeyStoreLocation      = "cdm.ssl.key.store.location";
    private static final String keySslKeyStorePassword      = "cdm.ssl.key.store.password";
    private static final String keySslKeyPassword           = "cdm.ssl.key.password";

    public static Setting parse(final String arguments, final String configFilePath) throws InvalidSettingException {
        final spade.utility.setting.Setting setting;
        try {
            setting = Helper.create(arguments, configFilePath);
        } catch (Exception e) {
            throw new InvalidSettingException(
                "Failed to read arguments and config file '" + configFilePath + "'", e);
        }

        final boolean hexUUIDs = parseHexUUIDs(setting, configFilePath);
        final Integer session = parseSession(setting, configFilePath);
        final boolean createHostConfig = parseCreateHostConfig(setting, configFilePath);
        final String hostFile = parseHostFile(setting, configFilePath);
        final Integer reportingIntervalSeconds = parseReportingIntervalSeconds(setting, configFilePath);

        final boolean sslEnabled = parseSslEnabled(setting, configFilePath);
        final Setting.SSL ssl = (sslEnabled && spade.storage.kafka.setting.Parser.isServerMode(setting))
            ? parseSsl(setting, configFilePath)
            : null;

        return new Setting(hexUUIDs, session, createHostConfig, hostFile, reportingIntervalSeconds, ssl);
    }

    private static boolean parseHexUUIDs(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        try {
            return Booleans.optBoolean(setting, keyHexUUIDs, false);
        } catch (SettingConvertException e) {
            throw new InvalidSettingException("Invalid value for '" + keyHexUUIDs + "' in arguments/config file '" + configFilePath + "'", e);
        }
    }

    private static Integer parseSession(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        try {
            return Ints.opt(setting, keySession);
        } catch (SettingConvertException e) {
            throw new InvalidSettingException("Invalid value for '" + keySession + "' in arguments/config file '" + configFilePath + "'", e);
        }
    }

    private static boolean parseCreateHostConfig(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        try {
            return Booleans.optBoolean(setting, keyCreateHostConfig, true);
        } catch (SettingConvertException e) {
            throw new InvalidSettingException("Invalid value for '" + keyCreateHostConfig + "' in arguments/config file '" + configFilePath + "'", e);
        }
    }

    private static String parseHostFile(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        try {
            return Strings.getString(setting, keyHostFile);
        } catch (SettingConvertException e) {
            throw new InvalidSettingException("Missing/Empty value for '" + keyHostFile + "' in arguments/config file '" + configFilePath + "'", e);
        }
    }

    private static Integer parseReportingIntervalSeconds(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        try {
            return Ints.opt(setting, keyReportingIntervalSeconds);
        } catch (SettingConvertException e) {
            throw new InvalidSettingException("Invalid value for '" + keyReportingIntervalSeconds + "' in arguments/config file '" + configFilePath + "'", e);
        }
    }

    private static boolean parseSslEnabled(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        try {
            return Booleans.optBoolean(setting, keySsl, true);
        } catch (SettingConvertException e) {
            throw new InvalidSettingException("Invalid value for '" + keySsl + "' in arguments/config file '" + configFilePath + "'", e);
        }
    }

    private static Setting.SSL parseSsl(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        return new Setting.SSL(
            parseSslString(setting, keySslSecurityProtocol, configFilePath),
            parseSslReadableFile(setting, keySslTrustStoreLocation, configFilePath),
            parseSslString(setting, keySslTrustStorePassword, configFilePath),
            parseSslReadableFile(setting, keySslKeyStoreLocation, configFilePath),
            parseSslString(setting, keySslKeyStorePassword, configFilePath),
            parseSslString(setting, keySslKeyPassword, configFilePath)
        );
    }

    private static String parseSslString(final spade.utility.setting.Setting setting, final String key, final String configFilePath) throws InvalidSettingException {
        try {
            return Strings.getString(setting, key);
        } catch (SettingConvertException e) {
            throw new InvalidSettingException(
                "Missing/Empty value for '" + key + "' in arguments/config file '" + configFilePath + "' (required when '" + keySsl + "' is enabled)", e);
        }
    }

    private static String parseSslReadableFile(final spade.utility.setting.Setting setting, final String key, final String configFilePath) throws InvalidSettingException {
        try {
            return Files.getReadableFile(setting, key).getPath();
        } catch (SettingConvertException e) {
            throw new InvalidSettingException(
                "Invalid/Missing readable file for '" + key + "' in arguments/config file '" + configFilePath + "' (required when '" + keySsl + "' is enabled)", e);
        }
    }

}
