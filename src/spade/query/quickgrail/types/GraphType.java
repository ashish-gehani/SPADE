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

import spade.query.quickgrail.entities.Graph;

public class GraphType extends Type {
  static private GraphType instance;

  public static GraphType GetInstance() {
    if (instance == null) {
      instance = new GraphType();
    }
    return instance;
  }

  @Override
  public TypeID getTypeID() {
    return TypeID.kGraph;
  }

  @Override
  public String getName() {
    return "Graph";
  }

  @Override
  public Object parseValueFromString(String text) {
    throw new RuntimeException("Not supported");
  }

  @Override
  public String printValueToString(Object value) {
    assert (value instanceof Graph);
    return ((Graph)value).getName();
  }
}
