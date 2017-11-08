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
 * Similar to GetPath but treats the graph edges as indirected.
 */
public class GetLink extends Instruction {
  // Output graph.
  private Graph targetGraph;
  // Input graph.
  private Graph subjectGraph;
  // Set of source vertices.
  private Graph srcGraph;
  // Set of destination vertices.
  private Graph dstGraph;
  // Max path length.
  private Integer maxDepth;

  public GetLink(Graph targetGraph, Graph subjectGraph,
                 Graph srcGraph, Graph dstGraph,
                 Integer maxDepth) {
    this.targetGraph = targetGraph;
    this.subjectGraph = subjectGraph;
    this.srcGraph = srcGraph;
    this.dstGraph = dstGraph;
    this.maxDepth = maxDepth;
  }

  @Override
  public void execute(Environment env, ExecutionContext ctx) {
    QuickstepExecutor qs = ctx.getExecutor();

    qs.executeQuery("DROP TABLE m_cur;\n" +
                    "DROP TABLE m_next;\n" +
                    "DROP TABLE m_answer;\n" +
                    "CREATE TABLE m_cur (id INT);\n" +
                    "CREATE TABLE m_next (id INT);\n" +
                    "CREATE TABLE m_answer (id INT);");

    String filter;
    if (Environment.IsBaseGraph(subjectGraph)) {
      filter = "";
    } else {
      filter = " AND edge.id IN (SELECT id FROM " + subjectGraph.getEdgeTableName() + ")";
    }

    // Create subgraph edges table.
    qs.executeQuery("DROP TABLE m_sgconn;\n" +
                    "CREATE TABLE m_sgconn (src INT, dst INT, depth INT);");

    qs.executeQuery("INSERT INTO m_cur SELECT id FROM " + dstGraph.getVertexTableName() + ";\n" +
                    "INSERT INTO m_answer SELECT id FROM m_cur;\n" +
                    "\\analyzerange edge\n");

    String loopStmts =
        "\\analyzerange m_cur\n" +
        "INSERT INTO m_sgconn SELECT src, dst, $depth FROM edge" +
        " WHERE dst IN (SELECT id FROM m_cur)" + filter + ";\n" +
        "INSERT INTO m_sgconn SELECT src, dst, $depth FROM edge" +
        " WHERE src IN (SELECT id FROM m_cur)" + filter + ";\n" +
        "DROP TABLE m_next;\n" + "CREATE TABLE m_next (id INT);\n" +
        "INSERT INTO m_next SELECT src FROM edge" +
        " WHERE dst IN (SELECT id FROM m_cur)" + filter + " GROUP BY src;\n" +
        "INSERT INTO m_next SELECT dst FROM edge" +
        " WHERE src IN (SELECT id FROM m_cur)" + filter + " GROUP BY dst;\n" +
        "DROP TABLE m_cur;\n" + "CREATE TABLE m_cur (id INT);\n" +
        "\\analyzerange m_answer\n" +
        "INSERT INTO m_cur SELECT id FROM m_next WHERE id NOT IN (SELECT id FROM m_answer);\n" +
        "INSERT INTO m_answer SELECT id FROM m_cur;";
    for (int i = 0; i < maxDepth; ++i) {
      qs.executeQuery(loopStmts.replace("$depth", String.valueOf(i+1)));

      String worksetSizeQuery = "COPY SELECT COUNT(*) FROM m_cur TO stdout;";
      if (qs.executeQueryForLongResult(worksetSizeQuery) == 0) {
        break;
      }
    }

    qs.executeQuery("DROP TABLE m_cur;\n" +
                    "DROP TABLE m_next;\n" +
                    "CREATE TABLE m_cur (id INT);\n" +
                    "CREATE TABLE m_next (id INT);");

    qs.executeQuery("\\analyzerange m_answer\n" +
                    "INSERT INTO m_cur SELECT id FROM " + srcGraph.getVertexTableName() +
                    " WHERE id IN (SELECT id FROM m_answer);\n");

    qs.executeQuery("DROP TABLE m_answer;\n" +
                    "CREATE TABLE m_answer (id INT);\n" +
                    "INSERT INTO m_answer SELECT id FROM m_cur;" +
                    "\\analyzerange m_answer m_sgconn\n");

    loopStmts =
        "DROP TABLE m_next;\n" + "CREATE TABLE m_next (id int);\n" +
        "\\analyzerange m_cur\n" +
        "INSERT INTO m_next SELECT dst FROM m_sgconn" +
        " WHERE src IN (SELECT id FROM m_cur)" +
        " AND depth + $depth <= " + String.valueOf(maxDepth) + " GROUP BY dst;\n" +
        "INSERT INTO m_next SELECT src FROM m_sgconn" +
        " WHERE dst IN (SELECT id FROM m_cur)" +
        " AND depth + $depth <= " + String.valueOf(maxDepth) + " GROUP BY src;\n" +
        "DROP TABLE m_cur;\n" + "CREATE TABLE m_cur (id INT);\n" +
        "\\analyzerange m_answer\n" +
        "INSERT INTO m_cur SELECT id FROM m_next WHERE id NOT IN (SELECT id FROM m_answer);\n" +
        "INSERT INTO m_answer SELECT id FROM m_cur;";
    for (int i = 0; i < maxDepth; ++i) {
      qs.executeQuery(loopStmts.replace("$depth", String.valueOf(i)));

      String worksetSizeQuery = "COPY SELECT COUNT(*) FROM m_cur TO stdout;";
      if (qs.executeQueryForLongResult(worksetSizeQuery) == 0) {
        break;
      }
    }

    String targetVertexTable = targetGraph.getVertexTableName();
    String targetEdgeTable = targetGraph.getEdgeTableName();

    qs.executeQuery("\\analyzerange m_answer\n" +
                    "INSERT INTO " + targetVertexTable + " SELECT id FROM m_answer;\n" +
                    "INSERT INTO " + targetEdgeTable + " SELECT id FROM edge" +
                    " WHERE src IN (SELECT id FROM m_answer)" +
                    " AND dst IN (SELECT id FROM m_answer)" + filter + ";");

    qs.executeQuery("DROP TABLE m_cur;\n" +
                    "DROP TABLE m_next;\n" +
                    "DROP TABLE m_answer;\n" +
                    "DROP TABLE m_sgconn;");
  }

  @Override
  public String getLabel() {
    return "GetLink";
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
    inline_field_names.add("srcGraph");
    inline_field_values.add(srcGraph.getName());
    inline_field_names.add("dstGraph");
    inline_field_values.add(dstGraph.getName());
    inline_field_names.add("maxDepth");
    inline_field_values.add(String.valueOf(maxDepth));
  }
}
