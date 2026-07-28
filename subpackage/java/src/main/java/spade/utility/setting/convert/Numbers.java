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

public class Numbers {
  private Numbers() {
  }

  public static long getLong(Setting setting, String key) throws SettingConvertException {
    return getLong(setting, key, null, null);
  }

  /**
   * Parses a long, requiring it to fall within {@code [min, max]}. Either bound may be
   * {@code null} to leave that side unchecked.
   */
  public static long getLong(Setting setting, String key, Long min, Long max) throws SettingConvertException {
    return toLong(Lookup.required(setting, key), min, max);
  }

  public static Long optLong(Setting setting, String key) throws SettingConvertException {
    return optLong(setting, key, null, null, null);
  }

  public static Long optLong(Setting setting, String key, Long min, Long max) throws SettingConvertException {
    return optLong(setting, key, min, max, null);
  }

  public static Long optLong(Setting setting, String key, Long min, Long max, Long defaultValue) throws SettingConvertException {
    KeyValue keyValue = setting.getKeyValue(key);
    return keyValue == null ? defaultValue : toLong(keyValue, min, max);
  }

  private static long toLong(KeyValue keyValue, Long min, Long max) throws SettingConvertException {
    try {
      return parseLong(keyValue.getValue().getResolvedValue(), min, max);
    } catch (IllegalArgumentException e) {
      throw new SettingConvertException(keyValue, e.getMessage());
    }
  }

  public static long parseLong(String value, Long min, Long max) {
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

  public static int getInt(Setting setting, String key) throws SettingConvertException {
    return getInt(setting, key, null, null);
  }

  /**
   * Parses an int, requiring it to fall within {@code [min, max]}. Either bound may be
   * {@code null} to leave that side unchecked.
   */
  public static int getInt(Setting setting, String key, Integer min, Integer max) throws SettingConvertException {
    return toInt(Lookup.required(setting, key), min, max);
  }

  public static Integer optInt(Setting setting, String key) throws SettingConvertException {
    return optInt(setting, key, null, null, null);
  }

  public static Integer optInt(Setting setting, String key, Integer min, Integer max) throws SettingConvertException {
    return optInt(setting, key, min, max, null);
  }

  public static Integer optInt(Setting setting, String key, Integer min, Integer max, Integer defaultValue) throws SettingConvertException {
    KeyValue keyValue = setting.getKeyValue(key);
    return keyValue == null ? defaultValue : toInt(keyValue, min, max);
  }

  private static int toInt(KeyValue keyValue, Integer min, Integer max) throws SettingConvertException {
    try {
      return parseInt(keyValue.getValue().getResolvedValue(), min, max);
    } catch (IllegalArgumentException e) {
      throw new SettingConvertException(keyValue, e.getMessage());
    }
  }

  public static int parseInt(String value, Integer min, Integer max) {
    int result;
    try {
      result = Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Not a valid int: '" + value + "'.");
    }
    if (min != null && result < min) {
      throw new IllegalArgumentException("Value " + result + " is below the minimum of " + min + ".");
    }
    if (max != null && result > max) {
      throw new IllegalArgumentException("Value " + result + " is above the maximum of " + max + ".");
    }
    return result;
  }

  public static double getDouble(Setting setting, String key) throws SettingConvertException {
    return getDouble(setting, key, null, null);
  }

  /**
   * Parses a double, requiring it to fall within {@code [min, max]}. Either bound may be
   * {@code null} to leave that side unchecked.
   */
  public static double getDouble(Setting setting, String key, Double min, Double max) throws SettingConvertException {
    return toDouble(Lookup.required(setting, key), min, max);
  }

  public static Double optDouble(Setting setting, String key) throws SettingConvertException {
    return optDouble(setting, key, null, null, null);
  }

  public static Double optDouble(Setting setting, String key, Double min, Double max) throws SettingConvertException {
    return optDouble(setting, key, min, max, null);
  }

  public static Double optDouble(Setting setting, String key, Double min, Double max, Double defaultValue) throws SettingConvertException {
    KeyValue keyValue = setting.getKeyValue(key);
    return keyValue == null ? defaultValue : toDouble(keyValue, min, max);
  }

  private static double toDouble(KeyValue keyValue, Double min, Double max) throws SettingConvertException {
    try {
      return parseDouble(keyValue.getValue().getResolvedValue(), min, max);
    } catch (IllegalArgumentException e) {
      throw new SettingConvertException(keyValue, e.getMessage());
    }
  }

  public static double parseDouble(String value, Double min, Double max) {
    double result;
    try {
      result = Double.parseDouble(value);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Not a valid double: '" + value + "'.");
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
