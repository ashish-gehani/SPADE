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
package spade.query.quickgrail.execution;

import java.util.ArrayList;

import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.kernel.Environment;
import spade.query.quickgrail.utility.TreeStringSerializable;
import spade.storage.quickstep.QuickstepExecutor;

/**
 * Sample a subset of vertices / edges from a graph.
 */
public class LimitGraph extends Instruction {
  // Output graph.
  private Graph targetGraph;
  // Input graph.
  private Graph sourceGraph;
  // The maximum number of vertices / edges to sample.
  private int limit;

  public LimitGraph(Graph targetGraph, Graph sourceGraph, int limit) {
    this.targetGraph = targetGraph;
    this.sourceGraph = sourceGraph;
    this.limit = limit;
  }

  @Override
  public void execute(Environment env, ExecutionContext ctx) {
    QuickstepExecutor qs = ctx.getExecutor();

    String sourceVertexTable = sourceGraph.getVertexTableName();
    String sourceEdgeTable = sourceGraph.getEdgeTableName();

    long numVertices = qs.executeQueryForLongResult(
        "COPY SELECT COUNT(*) FROM " + sourceVertexTable + " TO stdout;");
    long numEdges = qs.executeQueryForLongResult(
        "COPY SELECT COUNT(*) FROM " + sourceEdgeTable + " TO stdout;");

    if (numVertices > 0) {
      qs.executeQuery("\\analyzerange " + sourceVertexTable + "\n" +
                      "INSERT INTO " + targetGraph.getVertexTableName() +
                      " SELECT id FROM " + sourceVertexTable + " GROUP BY id" +
                      " ORDER BY id LIMIT " + limit + ";");

    }
    if (numEdges > 0) {
      qs.executeQuery("\\analyzerange " + sourceEdgeTable + "\n" +
                      "INSERT INTO " + targetGraph.getEdgeTableName() +
                      " SELECT id FROM " + sourceEdgeTable + " GROUP BY id" +
                      " ORDER BY id LIMIT " + limit + ";");
    }
  }

  @Override
  public String getLabel() {
    return "LimitGraph";
  }

  @Override
  protected void getFieldStringItems(
      ArrayList<String> inline_field_names,
      ArrayList<String> inline_field_values,
      ArrayList<String> non_container_child_field_names,
      ArrayList<TreeStringSerializable> non_container_child_fields,
      ArrayList<String> container_child_field_names,
      ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields) {
    inline_field_names.add("targetGraph");
    inline_field_values.add(targetGraph.getName());
    inline_field_names.add("sourceGraph");
    inline_field_values.add(sourceGraph.getName());
    inline_field_names.add("limit");
    inline_field_values.add(String.valueOf(limit));
  }
}
