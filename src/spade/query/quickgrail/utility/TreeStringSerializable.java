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

/**
 * Utility class for dumping QuickGrail intermediate representations
 * (e.g. parse trees, symbol table, instructions).
 */
public abstract class TreeStringSerializable {
  private final static int kMaxLineWidth = 120;
  private final static NullNode null_node = new NullNode();

  public abstract String getLabel();

  @Override
  public String toString() {
    return getNodeString(true, true, "", "");
  }

  public String getShortString() {
    ArrayList<String> inline_field_names = new ArrayList<String>();
    ArrayList<String> inline_field_values = new ArrayList<String>();
    ArrayList<String> non_container_child_field_names = new ArrayList<String>();
    ArrayList<TreeStringSerializable> non_container_child_fields = new ArrayList<TreeStringSerializable>();
    ArrayList<String> container_child_field_names = new ArrayList<String>();
    ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields = new ArrayList<ArrayList<? extends TreeStringSerializable>>();

    getFieldStringItems(inline_field_names, inline_field_values,
        non_container_child_field_names, non_container_child_fields,
        container_child_field_names, container_child_fields);

    return getHeadString(true, true, "", "", inline_field_names,
        inline_field_values, false);
  }

  protected abstract void getFieldStringItems(
      ArrayList<String> inline_field_names,
      ArrayList<String> inline_field_values,
      ArrayList<String> non_container_child_field_names,
      ArrayList<TreeStringSerializable> non_container_child_fields,
      ArrayList<String> container_child_field_names,
      ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields);

  protected String getNodeString(boolean is_root, boolean is_last,
      String parent_prefix, String node_name) {
    ArrayList<String> inline_field_names = new ArrayList<String>();
    ArrayList<String> inline_field_values = new ArrayList<String>();
    ArrayList<String> non_container_child_field_names = new ArrayList<String>();
    ArrayList<TreeStringSerializable> non_container_child_fields = new ArrayList<TreeStringSerializable>();
    ArrayList<String> container_child_field_names = new ArrayList<String>();
    ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields = new ArrayList<ArrayList<? extends TreeStringSerializable>>();

    getFieldStringItems(inline_field_names, inline_field_values,
        non_container_child_field_names, non_container_child_fields,
        container_child_field_names, container_child_fields);

    StringBuffer ret = new StringBuffer(
        getHeadString(is_root, is_last, parent_prefix, node_name,
            inline_field_names, inline_field_values, true));
    ret.append("\n");

    String child_prefix = (is_root ? "" : GetNewIndentedStringPrefix(is_last,
        parent_prefix));

    int last_index = non_container_child_fields.size() - 1;
    for (int i = 0; i < last_index; i++) {
      TreeStringSerializable node = non_container_child_fields.get(i);
      if (node == null) {
        node = null_node;
      }
      ret.append(node.getNodeString(false, false, child_prefix,
          non_container_child_field_names.get(i)));
    }

    if (!non_container_child_fields.isEmpty()) {
      if (container_child_fields.isEmpty()) {
        TreeStringSerializable node = non_container_child_fields
            .get(last_index);
        if (node == null) {
          node = null_node;
        }
        ret.append(node.getNodeString(false, true, child_prefix,
            non_container_child_field_names.get(last_index)));

      } else {
        TreeStringSerializable node = non_container_child_fields
            .get(last_index);
        if (node == null) {
          node = null_node;
        }
        ret.append(node.getNodeString(false, false, child_prefix,
            non_container_child_field_names.get(last_index)));
      }
    }

    last_index = container_child_fields.size() - 1;
    for (int i = 0; i < last_index; i++) {
      ret.append(GetNodeListString(false, child_prefix,
          container_child_field_names.get(i), container_child_fields.get(i)));
    }

    if (!container_child_fields.isEmpty()) {
      ret.append(GetNodeListString(true, child_prefix,
          container_child_field_names.get(last_index),
          container_child_fields.get(last_index)));
    }

    return ret.toString();
  }

  protected String GetNodeListString(boolean is_last, String prefix,
      String node_name, ArrayList<? extends TreeStringSerializable> node_list) {
    StringBuffer ret = new StringBuffer();
    String item_prefix = prefix;
    if (!node_name.isEmpty()) {
      ret.append(item_prefix);
      ret.append("+-").append(node_name).append("=").append("\n");
      item_prefix = GetNewIndentedStringPrefix(is_last, prefix);
    }

    if (node_list.isEmpty()) {
      ret.append(item_prefix).append("+-[]\n");
      return ret.toString();
    }

    int last_index = node_list.size() - 1;
    for (int i = 0; i < last_index; i++) {
      ret.append(node_list.get(i).getNodeString(false, false, item_prefix, ""));
    }
    if (!node_name.isEmpty()) {
      ret.append(node_list.get(last_index).getNodeString(false, true,
          item_prefix, ""));
    } else {
      ret.append(node_list.get(last_index).getNodeString(false,
          (node_name.isEmpty() ? is_last : true), item_prefix, ""));
    }

    return ret.toString();
  }

  protected String getHeadString(boolean is_root, boolean is_last,
      String parent_prefix, String node_name, ArrayList<String> field_names,
      ArrayList<String> field_values, boolean multi_line) {
    StringBuffer ret = new StringBuffer();

    String name = getLabel();
    String prefix = GetNewIndentedStringPrefix(is_last, parent_prefix);
    StringBuffer current_line = new StringBuffer(parent_prefix);
    if (!is_root) {
      current_line.append("+-");
    }

    if (!node_name.isEmpty()) {
      current_line.append(node_name).append("=");
      if (multi_line && current_line.length() + name.length() > kMaxLineWidth) {
        ret.append(current_line).append("\n");
        current_line.setLength(0);
        current_line.append(prefix);
      }
    }

    current_line.append(name);

    if (!field_names.isEmpty()) {
      AppendInlineString(prefix, "[", multi_line, current_line, ret);
      for (int i = 0; i < field_names.size(); i++) {
        StringBuffer inline_field = new StringBuffer(field_names.get(i));
        inline_field.append("=").append(EscapeString(field_values.get(i)));
        if (i < field_names.size() - 1) {
          inline_field.append(",");
        }
        AppendInlineString(prefix, inline_field.toString(), multi_line,
            current_line, ret);
      }
      AppendInlineString(prefix, "]", false, current_line, ret);
    }

    ret.append(current_line.toString());
    return ret.toString();
  }

  private static void AppendInlineString(String prefix, String inline_field,
      boolean multi_line, StringBuffer current_line, StringBuffer output) {
    if (multi_line
        && current_line.length() + inline_field.length() > kMaxLineWidth) {
      output.append(current_line).append("\n");
      current_line.setLength(0);
      current_line.append(prefix);
    }
    current_line.append(inline_field);
  }

  private static String GetNewIndentedStringPrefix(boolean is_last,
      String prefix) {
    StringBuffer new_prefix = new StringBuffer(prefix);
    if (!is_last) {
      new_prefix.append("|").append(" ");
    } else {
      new_prefix.append("  ");
    }
    return new_prefix.toString();
  }

  private static String EscapeString(String str) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < str.length(); ++i) {
      char c = str.charAt(i);
      switch (c) {
        case '\b':
          sb.append("\\b");
          break;
        case '\n':
          sb.append("\\n");
          break;
        case '\r':
          sb.append("\\r");
          break;
        case '\t':
          sb.append("\\t");
          break;
        case '\\':
          sb.append("\\\\");
          break;
        default:
          sb.append(c);
          break;
      }
    }
    return sb.toString();
  }
}

class NullNode extends TreeStringSerializable {
  @Override
  public String getLabel() {
    return "null";
  }

  @Override
  protected void getFieldStringItems(
      ArrayList<String> inline_field_names,
      ArrayList<String> inline_field_values,
      ArrayList<String> non_container_child_field_names,
      ArrayList<TreeStringSerializable> non_container_child_fields,
      ArrayList<String> container_child_field_names,
      ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields) {
  }
}