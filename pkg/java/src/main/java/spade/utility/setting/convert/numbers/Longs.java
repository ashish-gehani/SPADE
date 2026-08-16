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
package spade.utility.setting.convert.numbers;

import spade.utility.setting.Setting;
import spade.utility.setting.SettingConvertException;
import spade.utility.setting.convert.Lookup;
import spade.utility.setting.keyvalue.KeyValue;

public class Longs {
  private Longs() {
  }

  public static long get(Setting setting, String key) throws SettingConvertException {
    return get(setting, key, null, null);
  }

  /**
   * Parses a long, requiring it to fall within {@code [min, max]}. Either bound may be
   * {@code null} to leave that side unchecked.
   */
  public static long get(Setting setting, String key, Long min, Long max) throws SettingConvertException {
    return to(Lookup.required(setting, key), min, max);
  }

  public static Long opt(Setting setting, String key) throws SettingConvertException {
    return opt(setting, key, null, null, null);
  }

  public static Long opt(Setting setting, String key, Long min, Long max) throws SettingConvertException {
    return opt(setting, key, min, max, null);
  }

  public static Long opt(Setting setting, String key, Long min, Long max, Long defaultValue) throws SettingConvertException {
    KeyValue keyValue = setting.getKeyValue(key);
    return keyValue == null ? defaultValue : to(keyValue, min, max);
  }

  private static long to(KeyValue keyValue, Long min, Long max) throws SettingConvertException {
    try {
      return parse(keyValue.getValue().getResolvedValue(), min, max);
    } catch (IllegalArgumentException e) {
      throw new SettingConvertException(keyValue, e.getMessage());
    }
  }

  public static long parse(String value, Long min, Long max) {
    long result;
    try {
      result = Long.parseLong(value);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Not a valid long: '" + value + "'.");
    }
    if (min != null && result < min) {
      throw new IllegalArgumentException("Value " + result + " is below the minimum of " + min + ".");
    }
    if (max != null && result > max) {
      throw new IllegalArgumentException("Value " + result + " is above the maximum of " + max + ".");
    }
    return result;
  }
}
