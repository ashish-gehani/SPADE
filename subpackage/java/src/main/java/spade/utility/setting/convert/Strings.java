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

import spade.utility.setting.Setting;
import spade.utility.setting.SettingConvertException;
import spade.utility.setting.keyvalue.KeyValue;

public class Strings {
  private Strings() {
  }

  public static String getString(Setting setting, String key) throws SettingConvertException {
    return parseString(Lookup.required(setting, key).getValue().getResolvedValue());
  }

  public static String optString(Setting setting, String key) {
    return optString(setting, key, null);
  }

  public static String optString(Setting setting, String key, String defaultValue) {
    KeyValue keyValue = setting.getKeyValue(key);
    return keyValue == null ? defaultValue : parseString(keyValue.getValue().getResolvedValue());
  }

  public static String getNonBlankString(Setting setting, String key) throws SettingConvertException {
    return toNonBlankString(Lookup.required(setting, key));
  }

  public static String optNonBlankString(Setting setting, String key) throws SettingConvertException {
    return optNonBlankString(setting, key, null);
  }

  public static String optNonBlankString(Setting setting, String key, String defaultValue) throws SettingConvertException {
    KeyValue keyValue = setting.getKeyValue(key);
    return keyValue == null ? defaultValue : toNonBlankString(keyValue);
  }

  private static String toNonBlankString(KeyValue keyValue) throws SettingConvertException {
    try {
      return parseNonBlankString(keyValue.getValue().getResolvedValue());
    } catch (IllegalArgumentException e) {
      throw new SettingConvertException(keyValue, e.getMessage());
    }
  }

  public static String parseString(String value) {
    return value;
  }

  public static String parseNonBlankString(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Value must not be blank.");
    }
    return value;
  }
}
