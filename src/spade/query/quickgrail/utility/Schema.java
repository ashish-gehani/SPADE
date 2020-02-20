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
package spade.query.quickgrail.utility;

import java.util.ArrayList;

import spade.query.quickgrail.types.Type;

/**
 * Helper class for representing Quickstep result table schema.
 */
public class Schema {
  private ArrayList<String> column_names = new ArrayList<String>();
  private ArrayList<Type> column_types = new ArrayList<Type>();

  public void addColumn(String column_name, Type column_type) {
    column_names.add(column_name);
    column_types.add(column_type);
  }

  public int getNumColumns() {
    return column_names.size();
  }

  public String getColumnName(int column_id) {
    return column_names.get(column_id);
  }

  public Type getColumnType(int column_id) {
    return column_types.get(column_id);
  }
}
