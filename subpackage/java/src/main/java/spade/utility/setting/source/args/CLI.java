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
package spade.utility.setting.source.args;

import java.util.ArrayList;
import java.util.List;

import spade.utility.setting.SettingParseException;
import spade.utility.setting.keyvalue.KeyValue;
import spade.utility.setting.keyvalue.Map;

public class CLI extends Args {
  private final String value;

  public CLI(String value) {
    this.value = value;
  }

  public String get() {
    return value;
  }

  @Override
  public Type getType() {
    return Type.CLI;
  }

  @Override
  public Map parse() throws SettingParseException {
    List<KeyValue> keyValues = new ArrayList<>();
    for (String token : tokenize(value)) {
      keyValues.add(KeyValue.parse(this, token));
    }
    return Map.of(keyValues);
  }

  /**
   * Splits on whitespace, except within double-quoted spans (so a quoted value like
   * {@code a=". b s"} is kept as one token). A backslash-escaped quote does not open or
   * close a span.
   */
  private static List<String> tokenize(String value) {
    List<String> tokens = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean inQuotes = false;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '\\' && i + 1 < value.length()) {
        current.append(c).append(value.charAt(i + 1));
        i++;
        continue;
      }
      if (c == '"') {
        inQuotes = !inQuotes;
        current.append(c);
        continue;
      }
      if (Character.isWhitespace(c) && !inQuotes) {
        if (current.length() > 0) {
          tokens.add(current.toString());
          current.setLength(0);
        }
        continue;
      }
      current.append(c);
    }
    if (current.length() > 0) {
      tokens.add(current.toString());
    }
    return tokens;
  }

  @Override
  public String toString() {
    return "CLI [value=" + value + "]";
  }
}
