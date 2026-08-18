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

public class Booleans {
  private Booleans() {
  }

  public static boolean getBoolean(Setting setting, String key) throws SettingConvertException {
    return toBoolean(Lookup.required(setting, key));
  }

  public static Boolean optBoolean(Setting setting, String key) throws SettingConvertException {
    return optBoolean(setting, key, null);
  }

  public static Boolean optBoolean(Setting setting, String key, Boolean defaultValue) throws SettingConvertException {
    KeyValue keyValue = setting.getKeyValue(key);
    if (keyValue == null) {
      return defaultValue;
    }
    return toBoolean(keyValue);
  }

  private static boolean toBoolean(KeyValue keyValue) throws SettingConvertException {
    try {
      return parseBoolean(keyValue.getValue().getResolvedValue());
    } catch (IllegalArgumentException e) {
      throw new SettingConvertException(keyValue, e.getMessage());
    }
  }

  public static boolean parseBoolean(String value) {
    if ("true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value) || "1".equals(value)) {
      return true;
    }
    if ("false".equalsIgnoreCase(value) || "no".equalsIgnoreCase(value) || "0".equals(value)) {
      return false;
    }
    throw new IllegalArgumentException("Not a valid boolean: '" + value + "'.");
  }
}
