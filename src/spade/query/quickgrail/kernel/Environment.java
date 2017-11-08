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
package spade.query.quickgrail.kernel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;

import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.entities.GraphMetadata;
import spade.query.quickgrail.utility.QuickstepUtil;
import spade.query.quickgrail.utility.TreeStringSerializable;
import spade.storage.quickstep.QuickstepExecutor;

/**
 * QuickGrail compile-time environment (also used in runtime) mainly for
 * managing symbols (e.g. mapping from graph variables to underlying Quickstep
 * tables).
 */
public class Environment extends TreeStringSerializable {
  public final static Graph kBaseGraph = new Graph("trace_base");

  private HashMap<String, String> symbols;
  private QuickstepExecutor qs;

  public Environment(QuickstepExecutor qs) {
    this.qs = qs;
    this.symbols = new HashMap<String, String>();

    // Initialize the symbols table if it does not exist.
    String probeOutput = qs.executeQuery("SELECT COUNT(*) FROM symbols;");
    if (probeOutput == null || probeOutput.contains("ERROR")) {
      qs.executeQuery("CREATE TABLE symbols (name VARCHAR(128), value VARCHAR(128));");
    }

    // Initialize the symbols buffer.
    String lines = qs.executeQuery("COPY SELECT * FROM symbols TO stdout WITH (DELIMITER ',');");
    for (String line : lines.split("\n")) {
      String[] items = line.split(",");
      if (items.length == 2) {
        symbols.put(items[0], items[1]);
      }
    }
  }

  public void clear() {
    qs.executeQuery("DROP TABLE symbols;");
    qs.executeQuery("CREATE TABLE symbols (name VARCHAR(128), value VARCHAR(128));");
    symbols.clear();
    gc();
  }

  public Graph allocateGraph() {
    String idCounterStr = symbols.get("id_counter");
    if (idCounterStr == null) {
      idCounterStr = "0";
    }
    int idCounter = Integer.parseInt(idCounterStr);
    String nextIdStr = String.valueOf(++idCounter);
    setValue("id_counter", nextIdStr);
    return new Graph("trace_" + nextIdStr);
  }

  public GraphMetadata allocateGraphMetadata() {
    String idCounterStr = symbols.get("id_counter");
    if (idCounterStr == null) {
      idCounterStr = "0";
    }
    int idCounter = Integer.parseInt(idCounterStr);
    String nextIdStr = String.valueOf(++idCounter);
    setValue("id_counter", nextIdStr);
    return new GraphMetadata("meta_" + nextIdStr);
  }

  public String lookup(String symbol) {
    switch (symbol) {
      case "$base": return kBaseGraph.getName();
    }
    return symbols.get(symbol);
  }

  public void setValue(String symbol, String value) {
    switch (symbol) {
      case "$base":
        throw new RuntimeException("Cannot reassign reserved variables.");
    }
    if (symbols.containsKey(symbol)) {
      qs.executeQuery("UPDATE symbols SET value = '" + value +
                      "' WHERE name = '" + symbol + "';");
    } else {
      qs.executeQuery("INSERT INTO symbols VALUES('" + symbol + "', '" + value + "');");
    }
    symbols.put(symbol, value);
  }

  public void eraseSymbol(String symbol) {
    switch (symbol) {
      case "$base":
        throw new RuntimeException("Cannot erase reserved symbols.");
    }
    if (symbols.containsKey(symbol)) {
      symbols.remove(symbol);
      qs.executeQuery("DELETE FROM symbols WHERE name = '" + symbol + "';");
    }
  }

  private boolean isGarbageTable(HashSet<String> referencedTables, String table) {
    if (table.startsWith("m_")) {
      return true;
    }
    if (table.startsWith("trace") || table.startsWith("meta")) {
      return !referencedTables.contains(table);
    }
    return false;
  }

  public final Map<String, String> getSymbols() {
    return symbols;
  }

  public void gc() {
    HashSet<String> referencedTables = new HashSet<String>();
    referencedTables.add(kBaseGraph.getVertexTableName());
    referencedTables.add(kBaseGraph.getEdgeTableName());
    for (String graphName : symbols.values()) {
      Graph graph = new Graph(graphName);
      referencedTables.add(graph.getVertexTableName());
      referencedTables.add(graph.getEdgeTableName());
    }
    ArrayList<String> allTables = QuickstepUtil.GetAllTableNames(qs);
    StringBuilder dropQuery = new StringBuilder();
    for (String table : allTables) {
      if (isGarbageTable(referencedTables, table)) {
        dropQuery.append("DROP TABLE " + table + ";\n");
      }
    }
    if (dropQuery.length() > 0) {
      qs.executeQuery(dropQuery.toString());
    }
  }

  public static boolean IsBaseGraph(Graph graph) {
    return graph.getName().equals(kBaseGraph.getName());
  }

  @Override
  public String getLabel() {
    return "Environment";
  }

  @Override
  protected void getFieldStringItems(
      ArrayList<String> inline_field_names,
      ArrayList<String> inline_field_values,
      ArrayList<String> non_container_child_field_names,
      ArrayList<TreeStringSerializable> non_container_child_fields,
      ArrayList<String> container_child_field_names,
      ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields) {
    for (Entry<String, String> entry : symbols.entrySet()) {
      inline_field_names.add(entry.getKey());
      inline_field_values.add(entry.getValue());
    }
  }
}
