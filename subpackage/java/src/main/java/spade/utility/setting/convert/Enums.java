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

public class Enums {
  private Enums() {
  }

  public static <E extends Enum<E>> E getEnum(Setting setting, String key, Class<E> enumType) throws SettingConvertException {
    return toEnum(Lookup.required(setting, key), enumType);
  }

  public static <E extends Enum<E>> E optEnum(Setting setting, String key, Class<E> enumType) throws SettingConvertException {
    KeyValue keyValue = setting.getKeyValue(key);
    return keyValue == null ? null : toEnum(keyValue, enumType);
  }

  private static <E extends Enum<E>> E toEnum(KeyValue keyValue, Class<E> enumType) throws SettingConvertException {
    try {
      return parseEnum(keyValue.getValue().getResolvedValue(), enumType);
    } catch (IllegalArgumentException e) {
      throw new SettingConvertException(keyValue, e.getMessage());
    }
  }

  public static <E extends Enum<E>> E parseEnum(String value, Class<E> enumType) {
    try {
      return Enum.valueOf(enumType, value);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Not a valid " + enumType.getSimpleName() + ": '" + value + "'.");
    }
  }
}
