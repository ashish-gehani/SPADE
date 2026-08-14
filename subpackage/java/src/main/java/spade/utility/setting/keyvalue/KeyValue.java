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
package spade.utility.setting.keyvalue;

import spade.utility.setting.ParseContext;
import spade.utility.setting.SettingParseException;
import spade.utility.setting.SettingResolveException;
import spade.utility.setting.key.Key;
import spade.utility.setting.source.Source;
import spade.utility.setting.value.Value;
import spade.utility.setting.value.reference.Reference;

public class KeyValue {
  private final Key key;
  private final Value value;
  private final ParseContext parseContext;

  public KeyValue(Key key, Value value, ParseContext parseContext) {
    this.key = key;
    this.value = value;
    this.parseContext = parseContext;
  }

  public Key getKey() {
    return key;
  }

  public Value getValue() {
    return value;
  }

  public ParseContext getParseContext() {
    return parseContext;
  }

  @Override
  public String toString() {
    return "KeyValue [key=" + key + ", value=" + value + ", parseContext=" + parseContext + "]";
  }

  public void resolve() throws SettingResolveException {
    if (value instanceof Reference) {
      ((Reference) value).resolve();
    }
  }

  public static KeyValue parse(Source source, String line) throws SettingParseException {
    ParseContext context = new ParseContext(source, line);
    if (line == null) {
      throw new SettingParseException(context, "Line cannot be null.");
    }
    int equalsIndex = line.indexOf('=');
    if (equalsIndex == -1) {
      throw new SettingParseException(context, "Line is missing '=' delimiter.");
    }
    Key key = Key.parse(context, line.substring(0, equalsIndex).trim());
    Value value = Value.parse(context, line.substring(equalsIndex + 1).trim());
    return new KeyValue(key, value, context);
  }
}
