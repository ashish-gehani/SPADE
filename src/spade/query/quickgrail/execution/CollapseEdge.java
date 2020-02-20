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
 * Collapse all edges whose specified fields are the same.
 */
public class CollapseEdge extends Instruction {
  // Input graph.
  private Graph targetGraph;
  // Output graph.
  private Graph sourceGraph;
  // Fields to check.
  private ArrayList<String> fields;

  public CollapseEdge(Graph targetGraph, Graph sourceGraph, ArrayList<String> fields) {
    this.targetGraph = targetGraph;
    this.sourceGraph = sourceGraph;
    this.fields = fields;
  }

  @Override
  public void execute(Environment env, ExecutionContext ctx) {
    String sourceVertexTable = sourceGraph.getVertexTableName();
    String sourceEdgeTable = sourceGraph.getEdgeTableName();
    String targetVertexTable = targetGraph.getVertexTableName();
    String targetEdgeTable = targetGraph.getEdgeTableName();

    QuickstepExecutor qs = ctx.getExecutor();
    qs.executeQuery("\\analyzerange " + sourceVertexTable + " " + sourceEdgeTable + "\n");
    qs.executeQuery("INSERT INTO " + targetVertexTable +
                    " SELECT id FROM " + sourceVertexTable + ";");

    StringBuilder tables = new StringBuilder();
    StringBuilder predicates = new StringBuilder();
    StringBuilder groups = new StringBuilder();

    for (int i = 0; i < fields.size(); ++i) {
      String edgeAnnoName = "ea" + i;
      tables.append(", edge_anno " + edgeAnnoName);
      predicates.append(" AND e.id = " + edgeAnnoName + ".id" +
                        " AND " + edgeAnnoName + ".field = '" + fields.get(i) + "'");
      groups.append(", " + edgeAnnoName + ".value");
    }

    qs.executeQuery("INSERT INTO " + targetEdgeTable +
                    " SELECT MIN(e.id) FROM edge e" + tables.toString() +
                    " WHERE e.id IN (SELECT id FROM " + sourceEdgeTable + ")" + predicates.toString() +
                    " GROUP BY src, dst" + groups.toString() + ";");
  }

  @Override
  public String getLabel() {
    return "CollapseEdge";
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
  }
}
