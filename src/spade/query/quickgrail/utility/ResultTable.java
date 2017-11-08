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
import java.util.Arrays;
import java.util.Scanner;

/**
 * Helper class for dealing with Quickstep result table.
 */
public class ResultTable {
  private final static int MIN_COLUMN_WIDTH = 16;


  public static class Row {
    private ArrayList<String> values = new ArrayList<String>();

    public void add(String value) {
      this.values.add(value);
    }

    public void add(Object value) {
      this.values.add(String.valueOf(value));
    }

    public void add(String[] values) {
      for (String value : values) {
        this.values.add(value);
      }
    }

    public String getValue(int column_id) {
      return values.get(column_id);
    }

    public int size() {
      return values.size();
    }
  }

  private ArrayList<Row> rows = new ArrayList<Row>();
  private Schema schema = null;

  public static ResultTable FromText(String text, char column_delimiter) {
    ResultTable table = new ResultTable();
    Scanner scanner = new Scanner(text);
    while (scanner.hasNextLine()) {
      String line = scanner.nextLine();
      // Skip last newline.
      if (line.isEmpty() && !scanner.hasNextLine()) {
        break;
      }
      table.addRow(ParseRow(line, column_delimiter));
    }
    scanner.close();
    return table;
  }

  public void addRow(Row row) {
    rows.add(row);
  }

  public void setSchema(Schema schema) {
    this.schema = schema;
  }

  private static Row ParseRow(String line, char column_delimiter) {
    // Currently we are not using the schema info to convert column types.
    Row row = new Row();
    StringBuilder sb = new StringBuilder();

    for (int i = 0; i < line.length(); ++i) {
      char ch = line.charAt(i);
      if (ch == column_delimiter) {
        row.add(sb.toString());
        sb.setLength(0);
        continue;
      }

      if (ch == '\\') {
        ch = line.charAt(++i);
        switch (ch) {
          case 'b':
            // Backspace
            ch = '\b';
            break;
          case 'f':
            // Form-feed
            ch = '\f';
            break;
          case 'n':
            // Newline
            ch = '\n';
            break;
          case 'r':
            // Carriage return
            ch = '\r';
            break;
          case 't':
            // Tab
            ch = '\t';
            break;
          case '0':  // Fall through for octal digits.
          case '1':
          case '2':
          case '3':
          case '4':
          case '5':
          case '6':
          case '7': {
            int value = ch - '0';
            for (int k = 0; k < 2; ++k) {
              ch = line.charAt(++i);
              value = value * 8 + ch - '0';
            }
            ch = (char) value;
          }
          default:
            break;
        }
      }
      sb.append(ch);
    }
    row.add(sb.toString());

    return row;
  }

  public int getNumRows() {
    return rows.size();
  }

  public int calculateNumColumns() {
    int numColumns = 0;
    for (int i = 0; i < rows.size(); ++i) {
      numColumns = Math.max(numColumns, rows.get(i).size());
    }
    return numColumns;
  }

  public ArrayList<Row> getRows() {
    return rows;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    final int[] columnWidths = calculateColumnWidths();
    final int numColumns = columnWidths.length;
    final String ruler = FormatRuler(columnWidths);
    final String blank = "";

    sb.append(ruler);
    if (schema != null) {
      final int numSchemaColumns = schema.getNumColumns();
      for (int i = 0; i < numColumns; ++i) {
        String value = i < numSchemaColumns ? schema.getColumnName(i) : blank;
        sb.append('|');
        FillCell(sb, value, columnWidths[i]);
      }
      sb.append("|\n");
      sb.append(ruler);
    }
    for (Row row : rows) {
      final int numColumnsInRow = row.size();
      for (int i = 0; i < numColumns; ++i) {
        String value = i < numColumnsInRow ? row.getValue(i) : blank;
        sb.append('|');
        FillCell(sb, value, columnWidths[i]);
      }
      sb.append("|\n");
    }
    sb.append(ruler);
    return sb.toString();
  }

  private int[] calculateColumnWidths() {
    int numColumns = 0;
    for (Row row : rows) {
      numColumns = Math.max(numColumns, row.size());
    }
    if (schema != null) {
      numColumns = Math.max(numColumns, schema.getNumColumns());
    }
    int[] columnWidths = new int[numColumns];
    Arrays.fill(columnWidths, MIN_COLUMN_WIDTH);
    for (Row row : rows) {
      for (int i = 0; i < row.size(); ++i) {
        columnWidths[i] = Math.max(columnWidths[i], row.getValue(i).length() + 2);
      }
    }
    if (schema != null) {
      for (int i = 0; i < schema.getNumColumns(); ++i) {
        columnWidths[i] = Math.max(columnWidths[i], schema.getColumnName(i).length() + 2);
      }
    }
    return columnWidths;
  }

  private static String FormatRuler(final int[] columnWidths) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < columnWidths.length; ++i) {
      sb.append('+');
      sb.append(CreateRepeated('-', columnWidths[i]));
    }
    sb.append("+\n");
    return sb.toString();
  }

  private static char[] CreateRepeated(char c, int n) {
    char[] str = new char[n];
    Arrays.fill(str, c);
    return str;
  }

  private static void FillCell(StringBuilder sb, String value, int maxWidth) {
    sb.append(' ');
    sb.append(value);
    final int residual = maxWidth - value.length() - 2;
    if (residual >= 0) {
      sb.append(CreateRepeated(' ', residual));
    }
    sb.append(' ');
  }

}


