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
 * Subtract one graph from the other.
 */
public class SubtractGraph extends Instruction {
  // Output graph.
  private Graph outputGraph;
  // Minuend graph.
  private Graph minuendGraph;
  // Subtrahend graph.
  private Graph subtrahendGraph;
  // Graph components that should be involved in the operation (vertices / edges, or both).
  private Graph.Component component;

  public SubtractGraph(Graph outputGraph, Graph minuendGraph, Graph subtrahendGraph, Graph.Component component) {
    this.outputGraph = outputGraph;
    this.minuendGraph = minuendGraph;
    this.subtrahendGraph = subtrahendGraph;
    this.component = component;
  }

  @Override
  public void execute(Environment env, ExecutionContext ctx) {
    String outputVertexTable = outputGraph.getVertexTableName();
    String outputEdgeTable = outputGraph.getEdgeTableName();
    String minuendVertexTable = minuendGraph.getVertexTableName();
    String minuendEdgeTable = minuendGraph.getEdgeTableName();
    String subtrahendVertexTable = subtrahendGraph.getVertexTableName();
    String subtrahendEdgeTable = subtrahendGraph.getEdgeTableName();

    QuickstepExecutor qs = ctx.getExecutor();
    if (component == null || component == Graph.Component.kVertex) {
      qs.executeQuery("\\analyzerange " + subtrahendVertexTable + "\n");
      qs.executeQuery("INSERT INTO " + outputVertexTable +
                      " SELECT id FROM " + minuendVertexTable +
                      " WHERE id NOT IN (SELECT id FROM " + subtrahendVertexTable + ");");
    }
    if (component == null || component == Graph.Component.kEdge) {
      qs.executeQuery("\\analyzerange " + subtrahendEdgeTable + "\n");
      qs.executeQuery("INSERT INTO " + outputEdgeTable +
                      " SELECT id FROM " + minuendEdgeTable +
                      " WHERE id NOT IN (SELECT id FROM " + subtrahendEdgeTable + ");");
    }
  }

  @Override
  public String getLabel() {
    return "SubtractGraph";
  }

  @Override
  protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
      ArrayList<String> non_container_child_field_names, ArrayList<TreeStringSerializable> non_container_child_fields,
      ArrayList<String> container_child_field_names,
      ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields) {
    inline_field_names.add("outputGraph");
    inline_field_values.add(outputGraph.getName());
    inline_field_names.add("minuendGraph");
    inline_field_values.add(minuendGraph.getName());
    inline_field_names.add("subtrahendGraph");
    inline_field_values.add(subtrahendGraph.getName());
    if (this.component != null) {
      inline_field_names.add("component");
      inline_field_values.add(component.name());
    }
  }
}
