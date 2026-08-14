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
import spade.utility.setting.value.reference.Reference;

public abstract class Value {
  private final ParseContext parseContext;

  public Value(ParseContext parseContext) {
    this.parseContext = parseContext;
  }

  public ParseContext getParseContext() {
    return parseContext;
  }

  public abstract Type getValueType();

  public abstract String getResolvedValue();

  public static Value parse(ParseContext context, String valueString) throws SettingParseException {
    if (valueString == null) {
      throw new SettingParseException(context, "Value cannot be null.");
    }
    if (valueString.startsWith(Reference.PREFIX)) {
      return Reference.parse(context, valueString);
    }
    return Literal.parse(context, valueString);
  }
}
