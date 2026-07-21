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

import java.util.ArrayList;

import spade.query.quickgrail.utility.TreeStringSerializable;

public class ParseAssignment extends ParseStatement {
  public enum AssignmentType {
    kEqual,
    kPlusEqual,
    kMinusEqual,
    kIntersectEqual
  }

  public static AssignmentType ResolveAssignmentType(String input) {
    switch (input) {
      case "=": return AssignmentType.kEqual;
      case "+=": return AssignmentType.kPlusEqual;
      case "-=": return AssignmentType.kMinusEqual;
      case "&=": return AssignmentType.kIntersectEqual;
      default:
        break;
    }
    return null;
  }

  private AssignmentType assignmentType;
  private ParseVariable lhs;
  private ParseExpression rhs;

  public ParseAssignment(int lineNumber, int columnNumber,
                         AssignmentType assignmentType,
                         ParseVariable lhs,
                         ParseExpression rhs) {
    super(lineNumber, columnNumber, ParseStatement.StatementType.kAssignment);
    this.assignmentType = assignmentType;
    this.lhs = lhs;
    this.rhs = rhs;
  }

  public AssignmentType getAssignmentType() {
    return assignmentType;
  }

  public ParseVariable getLhs() {
    return lhs;
  }

  public ParseExpression getRhs() {
    return rhs;
  }

  @Override
  public String getLabel() {
    return "Assignment";
  }

  @Override
  protected void getFieldStringItems(
      ArrayList<String> inline_field_names,
      ArrayList<String> inline_field_values,
      ArrayList<String> non_container_child_field_names,
      ArrayList<TreeStringSerializable> non_container_child_fields,
      ArrayList<String> container_child_field_names,
      ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields) {
    inline_field_names.add("assignmentType");
    inline_field_values.add(assignmentType.name().substring(1));
    non_container_child_field_names.add("lhs");
    non_container_child_fields.add(lhs);
    non_container_child_field_names.add("rhs");
    non_container_child_fields.add(rhs);
  }
}
