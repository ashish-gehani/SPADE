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

package spade.utility.mcp.server.tool.type.spade.cli;

import java.io.File;

import spade.utility.setting.Helper;
import spade.utility.setting.InvalidSettingException;
import spade.utility.setting.SettingConvertException;
import spade.utility.setting.convert.Files;
import spade.utility.setting.convert.Strings;
import spade.utility.setting.convert.numbers.Ints;

public class Parser {

    private static final String keyHost                      = "spade.host";
    private static final String keyPort                      = "spade.port";
    private static final String keyServerPublicKeystorePath  = "spade.keystore.serverPublicPath";
    private static final String keyClientPrivateKeystorePath = "spade.keystore.clientPrivatePath";
    private static final String keyPasswordPublicKeystore    = "spade.keystore.passwordPublic";
    private static final String keyPasswordPrivateKeystore   = "spade.keystore.passwordPrivate";

    public static Config parse(final String configFilePath) throws InvalidSettingException {
        final spade.utility.setting.Setting setting;
        try {
            setting = Helper.create("", configFilePath);
        } catch (Exception e) {
            throw new InvalidSettingException(
                "Failed to read config file '" + configFilePath + "'", e);
        }

        final String host = parseNonBlankString(setting, keyHost, configFilePath);
        final int port = parsePort(setting, configFilePath);
        final File serverPublicKeystorePath = parseReadableFile(setting, keyServerPublicKeystorePath, configFilePath);
        final File clientPrivateKeystorePath = parseReadableFile(setting, keyClientPrivateKeystorePath, configFilePath);
        final char[] passwordPublicKeystore = parsePassword(setting, keyPasswordPublicKeystore, configFilePath);
        final char[] passwordPrivateKeystore = parsePassword(setting, keyPasswordPrivateKeystore, configFilePath);

        return new Config(
            host,
            port,
            serverPublicKeystorePath,
            clientPrivateKeystorePath,
            passwordPublicKeystore,
            passwordPrivateKeystore
        );
    }

    private static String parseNonBlankString(final spade.utility.setting.Setting setting, final String key, final String configFilePath) throws InvalidSettingException {
        try {
            return Strings.getNonBlankString(setting, key);
        } catch (SettingConvertException e) {
            throw new InvalidSettingException(
                "Missing/Empty value for '" + key + "' in config file '" + configFilePath + "'", e);
        }
    }

    private static int parsePort(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        try {
            return Ints.get(setting, keyPort, 1, 65535);
        } catch (SettingConvertException e) {
            throw new InvalidSettingException(
                "Missing/Invalid value for '" + keyPort + "' in config file '" + configFilePath + "'", e);
        }
    }

    private static File parseReadableFile(final spade.utility.setting.Setting setting, final String key, final String configFilePath) throws InvalidSettingException {
        try {
            return Files.getReadableFile(setting, key);
        } catch (SettingConvertException e) {
            throw new InvalidSettingException(
                "Missing/Invalid value for '" + key + "' in config file '" + configFilePath + "'", e);
        }
    }

    private static char[] parsePassword(final spade.utility.setting.Setting setting, final String key, final String configFilePath) throws InvalidSettingException {
        return parseNonBlankString(setting, key, configFilePath).toCharArray();
    }

}
