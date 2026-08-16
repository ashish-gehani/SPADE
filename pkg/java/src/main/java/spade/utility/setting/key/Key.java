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
package spade.utility.setting.key;

import spade.utility.setting.ParseContext;
import spade.utility.setting.SettingParseException;

public class Key {
  private final Key namespace;
  private final String name;
  private final String fullName;
  private final ParseContext parseContext;

  public Key(Key namespace, String name, ParseContext parseContext) {
    this.namespace = namespace;
    this.name = name;
    this.fullName = namespace == null ? name : namespace.getFullName() + "." + name;
    this.parseContext = parseContext;
  }

  /**
   * Returns this key's own name, without any namespace prefix.
   */
  public String getSimpleName() {
    return name;
  }

  public Key getNamespace() {
    return namespace;
  }

  /**
   * Returns the fully-qualified name, built by walking up the namespace chain
   * to the root and joining each level's simple name with {@code .}.
   */
  public String getFullName() {
    return fullName;
  }

  public ParseContext getParseContext() {
    return parseContext;
  }

  @Override
  public String toString() {
    return "Key [fullName=" + getFullName() + ", parseContext=" + parseContext + "]";
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof Key)) {
      return false;
    }
    return fullName.equals(((Key) obj).fullName);
  }

  @Override
  public int hashCode() {
    return fullName.hashCode();
  }

  public static Key parse(ParseContext context, String keyString) throws SettingParseException {
    if (keyString == null || keyString.isEmpty()) {
      throw new SettingParseException(context, "Key cannot be null or empty.");
    }
    if (keyString.contains("=")) {
      throw new SettingParseException(context, "Key cannot contain '=': " + keyString);
    }
    Key key = null;
    for (String part : keyString.split("\\.", -1)) {
      if (part.isEmpty()) {
        throw new SettingParseException(context, "Key contains an empty segment: " + keyString);
      }
      key = new Key(key, part, context);
    }
    return key;
  }
}
