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
package spade.utility.setting;

import spade.utility.setting.keyvalue.KeyValue;

public class SettingConvertException extends Exception {
  private final KeyValue keyValue;

  /**
   * Used when the key was found but its value could not be converted to the requested type;
   * the key value's source and line are attached to the message for debugging.
   */
  public SettingConvertException(KeyValue keyValue, String msg) {
    super(msg + ". Line: " + keyValue.getParseContext().getLine() + ". Source: " + keyValue.getParseContext().getSource());
    this.keyValue = keyValue;
  }

  /**
   * Used when a required key was not found at all, so there is no key value to attribute the
   * error to.
   */
  public SettingConvertException(String msg) {
    super(msg);
    this.keyValue = null;
  }

  public KeyValue getKeyValue() {
    return keyValue;
  }
}
