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

import java.util.ArrayList;
import java.util.List;

import spade.utility.setting.Setting;
import spade.utility.setting.SettingConvertException;
import spade.utility.setting.keyvalue.KeyValue;

public class CSV {
  private CSV() {
  }

  public static List<String> getCommaSeparatedStrings(Setting setting, String key) throws SettingConvertException {
    return toCommaSeparatedStrings(Lookup.required(setting, key));
  }

  public static List<String> optCommaSeparatedStrings(Setting setting, String key) throws SettingConvertException {
    return optCommaSeparatedStrings(setting, key, null);
  }

  public static List<String> optCommaSeparatedStrings(Setting setting, String key, List<String> defaultValue) throws SettingConvertException {
    KeyValue keyValue = setting.getKeyValue(key);
    return keyValue == null ? defaultValue : toCommaSeparatedStrings(keyValue);
  }

  private static List<String> toCommaSeparatedStrings(KeyValue keyValue) throws SettingConvertException {
    try {
      return parseCommaSeparatedStrings(keyValue.getValue().getResolvedValue());
    } catch (IllegalArgumentException e) {
      throw new SettingConvertException(keyValue, e.getMessage());
    }
  }

  /**
   * See the "CSV" section of the package {@code README.md} for the parsing rules.
   */
  public static List<String> parseCommaSeparatedStrings(String value) {
    List<String> result = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean inQuotes = false;
    boolean closedQuote = false;
    boolean quotedField = false;
    int i = 0;
    int length = value.length();
    while (i < length) {
      char c = value.charAt(i);
      if (inQuotes) {
        if (c == '\\' && i + 1 < length && value.charAt(i + 1) == '"') {
          current.append('"');
          i += 2;
        } else if (c == '"') {
          inQuotes = false;
          closedQuote = true;
          i++;
        } else {
          current.append(c);
          i++;
        }
      } else if (c == '"') {
        if (closedQuote || !current.toString().trim().isEmpty()) {
          throw new IllegalArgumentException("Invalid CSV value, unexpected quote: '" + value + "'.");
        }
        current.setLength(0);
        quotedField = true;
        inQuotes = true;
        i++;
      } else if (c == ',') {
        result.add(quotedField ? current.toString() : current.toString().trim());
        current.setLength(0);
        closedQuote = false;
        quotedField = false;
        i++;
      } else if (closedQuote) {
        if (!Character.isWhitespace(c)) {
          throw new IllegalArgumentException("Invalid CSV value, unexpected character after closing quote: '" + value + "'.");
        }
        i++;
      } else {
        current.append(c);
        i++;
      }
    }
    if (inQuotes) {
      throw new IllegalArgumentException("Invalid CSV value, unterminated quote: '" + value + "'.");
    }
    result.add(quotedField ? current.toString() : current.toString().trim());
    return result;
  }
}
