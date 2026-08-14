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
package spade.utility.setting.value;

import spade.utility.setting.ParseContext;
import spade.utility.setting.SettingParseException;

public class Literal extends Value {
  private final String value;

  public Literal(String value, ParseContext parseContext) {
    super(parseContext);
    this.value = value;
  }

  public String get() {
    return value;
  }

  @Override
  public Type getValueType() {
    return Type.LITERAL;
  }

  @Override
  public String getResolvedValue() {
    return value;
  }

  @Override
  public String toString() {
    return "Literal [value=" + value + "]";
  }

  public static Literal parse(ParseContext context, String valueString) throws SettingParseException {
    if (valueString.startsWith("\"")) {
      if (valueString.length() < 2 || !valueString.endsWith("\"")) {
        throw new SettingParseException(context, "Malformed literal value, missing closing '\"': " + valueString);
      }
      String quoted = valueString.substring(1, valueString.length() - 1);
      return new Literal(quoted.replace("\\\"", "\""), context);
    }
    return new Literal(valueString, context);
  }
}
