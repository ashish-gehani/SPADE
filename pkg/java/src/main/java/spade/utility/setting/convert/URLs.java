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
package spade.utility.setting.convert;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import spade.utility.setting.Setting;
import spade.utility.setting.SettingConvertException;
import spade.utility.setting.keyvalue.KeyValue;

public class URLs {
  private URLs() {
  }

  public static URL getUrl(Setting setting, String key) throws SettingConvertException {
    return toUrl(Lookup.required(setting, key));
  }

  public static URL optUrl(Setting setting, String key) throws SettingConvertException {
    return optUrl(setting, key, null);
  }

  public static URL optUrl(Setting setting, String key, URL defaultValue) throws SettingConvertException {
    KeyValue keyValue = setting.getKeyValue(key);
    return keyValue == null ? defaultValue : toUrl(keyValue);
  }

  private static URL toUrl(KeyValue keyValue) throws SettingConvertException {
    try {
      return parseUrl(keyValue.getValue().getResolvedValue());
    } catch (IllegalArgumentException e) {
      throw new SettingConvertException(keyValue, e.getMessage());
    }
  }

  public static URL parseUrl(String value) {
    try {
      return new URI(value).toURL();
    } catch (URISyntaxException | MalformedURLException | IllegalArgumentException e) {
      throw new IllegalArgumentException("Not a valid URL: '" + value + "'.");
    }
  }
}
