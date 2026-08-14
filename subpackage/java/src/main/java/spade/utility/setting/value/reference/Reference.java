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
package spade.utility.setting.value.reference;

import spade.utility.setting.ParseContext;
import spade.utility.setting.ResolveContext;
import spade.utility.setting.SettingParseException;
import spade.utility.setting.SettingResolveException;
import spade.utility.setting.value.Value;

public abstract class Reference extends Value {
  public static final String PREFIX = "$(";

  private final String value;
  private final ResolveContext resolveContext;
  private String resolvedValue;

  public Reference(String value, ResolveContext resolveContext, ParseContext parseContext) {
    super(parseContext);
    this.value = value;
    this.resolveContext = resolveContext;
  }

  public String get() {
    return value;
  }

  public ResolveContext getResolveContext() {
    return resolveContext;
  }

  @Override
  public String getResolvedValue() {
    return resolvedValue;
  }

  protected void setResolvedValue(String resolvedValue) {
    this.resolvedValue = resolvedValue;
  }

  @Override
  public spade.utility.setting.value.Type getValueType() {
    return spade.utility.setting.value.Type.REFERENCE;
  }

  public abstract Type getReferenceType();

  /**
   * Fetches the content this reference points to and stores it as the resolved value.
   */
  public abstract void resolve() throws SettingResolveException;

  public static Reference parse(ParseContext context, String valueString) throws SettingParseException {
    if (valueString == null) {
      throw new SettingParseException(context, "Reference value cannot be null.");
    }
    if (!valueString.startsWith(PREFIX)) {
      throw new SettingParseException(context, "Malformed reference value, missing opening '$(': " + valueString);
    }
    if (!valueString.endsWith(")")) {
      throw new SettingParseException(context, "Malformed reference value, missing closing ')': " + valueString);
    }
    String referenceString = valueString.substring(PREFIX.length(), valueString.length() - 1);
    if (referenceString.isEmpty()) {
      throw new SettingParseException(context, "Reference value cannot be empty: " + valueString);
    }
    int spaceIndex = referenceString.indexOf(' ');
    String keyword = spaceIndex == -1 ? referenceString : referenceString.substring(0, spaceIndex);
    String rest = spaceIndex == -1 ? "" : referenceString.substring(spaceIndex + 1).trim();
    if (keyword.equals(ConfigFile.KEYWORD)) {
      return ConfigFile.parse(context, rest);
    }
    if (keyword.equals(TextFile.KEYWORD)) {
      return TextFile.parse(context, rest);
    }
    throw new SettingParseException(context, "Unknown reference type: " + referenceString);
  }
}
