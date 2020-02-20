/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2018 SRI International

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
package spade.query.quickgrail.types;

public class LongType extends Type {
  static private LongType instance;

  public static LongType GetInstance() {
    if (instance == null) {
      instance = new LongType();
    }
    return instance;
  }

  @Override
  public TypeID getTypeID() {
    return TypeID.kLong;
  }

  @Override
  public String getName() {
    return "Long";
  }

  @Override
  public Object parseValueFromString(String text) {
    return Long.parseLong(text);
  }

  @Override
  public String printValueToString(Object value) {
    assert (value instanceof Long);
    return String.valueOf(value);
  }
}
