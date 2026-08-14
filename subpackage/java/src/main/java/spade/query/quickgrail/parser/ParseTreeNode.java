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
package spade.query.quickgrail.parser;

import spade.query.quickgrail.utility.TreeStringSerializable;

public abstract class ParseTreeNode extends TreeStringSerializable {
  private int lineNumber;
  private int columnNumber;

  public ParseTreeNode(int lineNumber, int columnNumber) {
    this.lineNumber = lineNumber;
    this.columnNumber = columnNumber;
  }

  public int getLineNumber() {
    return lineNumber;
  }

  public int getColumnNumber() {
  	  return columnNumber;
  }

  public String getLocationString() {
    return "line " + lineNumber + ", column " + columnNumber;
  }
}
