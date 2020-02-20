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
 * Get the lineage of a set of vertices in a graph.
 */
public class GetLineage extends Instruction {
  public enum Direction {
    kAncestor,
    kDescendant,
    kBoth
  }

  // Output graph.
  private Graph targetGraph;
  // Input graph.
  private Graph subjectGraph;
  // Set of starting vertices.
  private Graph startGraph;
  // Max depth.
  private Integer depth;
  // Direction (ancestors / descendants, or both).
  private Direction direction;

  public GetLineage(Graph targetGraph, Graph subjectGraph,
                    Graph startGraph, Integer depth, Direction direction) {
    this.targetGraph = targetGraph;
    this.subjectGraph = subjectGraph;
    this.startGraph = startGraph;
    this.depth = depth;
    this.direction = direction;
  }

  @Override
  public void execute(Environment env, ExecutionContext ctx) {
    ArrayList<Direction> oneDirs = new ArrayList<Direction>();
    if (direction == Direction.kBoth) {
      oneDirs.add(Direction.kAncestor);
      oneDirs.add(Direction.kDescendant);
    } else {
      oneDirs.add(direction);
    }

    QuickstepExecutor qs = ctx.getExecutor();

    String targetVertexTable = targetGraph.getVertexTableName();
    String targetEdgeTable = targetGraph.getEdgeTableName();

    String subjectEdgeTable = subjectGraph.getEdgeTableName();
    String filter = "";
    if (!Environment.IsBaseGraph(subjectGraph)) {
      qs.executeQuery("\\analyzerange " + subjectEdgeTable + "\n");
      filter = " AND edge.id IN (SELECT id FROM " + subjectEdgeTable + ")";
    }

    for (Direction oneDir : oneDirs) {
      executeOneDirection(oneDir, qs, filter);
      qs.executeQuery("\\analyzerange m_answer m_answer_edge\n" +
                      "INSERT INTO " + targetVertexTable + " SELECT id FROM m_answer;\n" +
                      "INSERT INTO " + targetEdgeTable + " SELECT id FROM m_answer_edge GROUP BY id;");
    }

    qs.executeQuery("DROP TABLE m_cur;\n" +
                    "DROP TABLE m_next;\n" +
                    "DROP TABLE m_answer;\n" +
                    "DROP TABLE m_answer_edge;");
  }

  private void executeOneDirection(Direction dir, QuickstepExecutor qs, String filter) {
    String src, dst;
    if (dir == Direction.kAncestor) {
      src = "src";
      dst = "dst";
    } else {
      assert dir == Direction.kDescendant;
      src = "dst";
      dst = "src";
    }

    qs.executeQuery("DROP TABLE m_cur;\n" +
                    "DROP TABLE m_next;\n" +
                    "DROP TABLE m_answer;\n" +
                    "DROP TABLE m_answer_edge;\n" +
                    "CREATE TABLE m_cur (id INT);\n" +
                    "CREATE TABLE m_next (id INT);\n" +
                    "CREATE TABLE m_answer (id INT);\n" +
                    "CREATE TABLE m_answer_edge (id LONG);");

    String startVertexTable = startGraph.getVertexTableName();
    qs.executeQuery("INSERT INTO m_cur SELECT id FROM " + startVertexTable + ";\n" +
                    "INSERT INTO m_answer SELECT id FROM m_cur;");

    String loopStmts =
        "DROP TABLE m_next;\n" + "CREATE TABLE m_next (id INT);\n" +
        "\\analyzerange m_cur\n" +
        "INSERT INTO m_next SELECT " + dst + " FROM edge" +
        " WHERE " + src + " IN (SELECT id FROM m_cur)" + filter +
        " GROUP BY " + dst + ";\n" +
        "INSERT INTO m_answer_edge SELECT id FROM edge" +
        " WHERE " + src + " IN (SELECT id FROM m_cur)" + filter + ";\n" +
        "DROP TABLE m_cur;\n" + "CREATE TABLE m_cur (id INT);\n" +
        "\\analyzerange m_answer\n" +
        "INSERT INTO m_cur SELECT id FROM m_next WHERE id NOT IN (SELECT id FROM m_answer);\n" +
        "INSERT INTO m_answer SELECT id FROM m_cur;";
    for (int i = 0; i < depth; ++i) {
      qs.executeQuery(loopStmts);

      String worksetSizeQuery = "COPY SELECT COUNT(*) FROM m_cur TO stdout;";
      if (qs.executeQueryForLongResult(worksetSizeQuery) == 0) {
        break;
      }
    }
  }

  @Override
  public String getLabel() {
    return "GetLineage";
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
    inline_field_names.add("subjectGraph");
    inline_field_values.add(subjectGraph.getName());
    inline_field_names.add("startGraph");
    inline_field_values.add(startGraph.getName());
    inline_field_names.add("depth");
    inline_field_values.add(String.valueOf(depth));
    inline_field_names.add("direction");
    inline_field_values.add(direction.name().substring(1));
  }
}
