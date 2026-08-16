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

package spade.utility.mcp.server.tool.type.web.doc;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import spade.utility.setting.Helper;
import spade.utility.setting.InvalidSettingException;
import spade.utility.setting.SettingConvertException;
import spade.utility.setting.convert.CSV;
import spade.utility.setting.convert.URLs;

public class Parser {

    private static final String keyUrls = "web.doc.urls";

    public static Config parse(final String configFilePath) throws InvalidSettingException {
        final spade.utility.setting.Setting setting;
        try {
            setting = Helper.create("", configFilePath);
        } catch (Exception e) {
            throw new InvalidSettingException(
                "Failed to read config file '" + configFilePath + "'", e);
        }

        final List<String> rawUrls = parseRawUrls(setting, configFilePath);
        final List<URL> urls = new ArrayList<>();
        for (final String rawUrl : rawUrls) {
            urls.add(parseUrl(rawUrl, configFilePath));
        }

        return new Config(urls);
    }

    private static List<String> parseRawUrls(final spade.utility.setting.Setting setting, final String configFilePath) throws InvalidSettingException {
        try {
            return CSV.getCommaSeparatedStrings(setting, keyUrls);
        } catch (SettingConvertException e) {
            throw new InvalidSettingException(
                "Missing/Invalid value for '" + keyUrls + "' in config file '" + configFilePath + "'", e);
        }
    }

    private static URL parseUrl(final String rawUrl, final String configFilePath) throws InvalidSettingException {
        try {
            return URLs.parseUrl(rawUrl);
        } catch (IllegalArgumentException e) {
            throw new InvalidSettingException(
                "Invalid URL '" + rawUrl + "' for '" + keyUrls + "' in config file '" + configFilePath + "'", e);
        }
    }

}
