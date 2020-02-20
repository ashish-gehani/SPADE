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
import java.util.HashMap;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Vertex;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.kernel.Environment;
import spade.query.quickgrail.utility.QuickstepUtil;
import spade.query.quickgrail.utility.TreeStringSerializable;
import spade.storage.quickstep.QuickstepExecutor;

/**
 * Export a QuickGrail graph to spade.core.Graph or to DOT representation.
 */
public class ExportGraph extends Instruction {
  private static final int kNonForceExportLimit = 4096;

  private Graph targetGraph;
  private Format format;
  private boolean force;

  public enum Format {
    kNormal,
    kDot
  }

  public ExportGraph(Graph targetGraph, Format format, boolean force) {
    this.targetGraph = targetGraph;
    this.format = format;
    this.force = force;
  }

  @Override
  public void execute(Environment env, ExecutionContext ctx) {
    QuickstepExecutor qs = ctx.getExecutor();

    if (!force) {
      long numVertices = QuickstepUtil.GetNumVertices(qs, targetGraph);
      long numEdges = QuickstepUtil.GetNumEdges(qs, targetGraph);
      if (numVertices + numEdges > kNonForceExportLimit) {
        String cmd = (format == Format.kNormal ? "dump" : "visualize");
        ctx.addResponse("It may take a long time to transfer/print the result data due to " +
                        "too many vertices/edges: " + numVertices + "/" + numEdges + "\n" +
                        "Please use *" + cmd + " force ...* to force the transfer");
        return;
      }
    }

    qs.executeQuery("DROP TABLE m_init_vertex;\n" +
                    "DROP TABLE m_vertex;\n" +
                    "DROP TABLE m_edge;\n" +
                    "CREATE TABLE m_init_vertex(id INT);\n" +
                    "CREATE TABLE m_vertex(id INT);\n" +
                    "CREATE TABLE m_edge(id LONG);");

    String targetVertexTable = targetGraph.getVertexTableName();
    String targetEdgeTable = targetGraph.getEdgeTableName();
    qs.executeQuery("INSERT INTO m_init_vertex" +
                    " SELECT id FROM " + targetVertexTable + ";\n" +
                    "INSERT INTO m_init_vertex" +
                    " SELECT src FROM edge WHERE id IN (SELECT id FROM " + targetEdgeTable + ");\n" +
                    "INSERT INTO m_init_vertex" +
                    " SELECT dst FROM edge WHERE id IN (SELECt id FROM " + targetEdgeTable + ");\n" +
                    "\\analyzerange " + targetVertexTable + " " + targetEdgeTable + "\n" +
                    "INSERT INTO m_vertex SELECT id FROM m_init_vertex GROUP BY id;\n" +
                    "INSERT INTO m_edge SELECt id FROM " + targetEdgeTable + " GROUP BY id;");

    HashMap<Integer, AbstractVertex> vertices = exportVertices(qs, "m_vertex");
    HashMap<Long, AbstractEdge> edges = exportEdges(qs, "m_edge");

    qs.executeQuery("DROP TABLE m_init_vertex;\n" +
                    "DROP TABLE m_vertex;\n" +
                    "DROP TABLE m_edge;");

    spade.core.Graph graph = new spade.core.Graph();
    graph.vertexSet().addAll(vertices.values());
    graph.edgeSet().addAll(edges.values());

    if (format == Format.kNormal) {
      ctx.addResponse(graph.toString());
    } else {
      ctx.addResponse(graph.exportGraph());
    }
  }

  private HashMap<Integer, AbstractVertex> exportVertices(
      QuickstepExecutor qs, String targetVertexTable) {
    HashMap<Integer, AbstractVertex> vertices = new HashMap<Integer, AbstractVertex>();
    long numVertices = qs.executeQueryForLongResult(
        "COPY SELECT COUNT(*) FROM " + targetVertexTable + " TO stdout;");
    if (numVertices == 0) {
      return vertices;
    }

    qs.executeQuery("\\analyzerange " + targetVertexTable + "\n");

    String vertexAnnoStr = qs.executeQuery(
        "COPY SELECT * FROM vertex_anno WHERE id IN (SELECT id FROM " +
        targetVertexTable + ") TO stdout WITH (DELIMITER e'\\n');");
    String[] vertexAnnoLines = vertexAnnoStr.split("\n");
    vertexAnnoStr = null;

    assert vertexAnnoLines.length % 3 == 0;
    for (int i = 0; i < vertexAnnoLines.length; i += 3) {
      // TODO: accelerate with cache.
      Integer id = Integer.parseInt(vertexAnnoLines[i]);
      AbstractVertex vertex = vertices.get(id);
      if (vertex == null) {
        vertex = new Vertex();
        vertices.put(id, vertex);
      }
      vertex.addAnnotation(vertexAnnoLines[i+1], vertexAnnoLines[i+2]);
    }
    return vertices;
  }

  private HashMap<Long, AbstractEdge> exportEdges(
      QuickstepExecutor qs, String targetEdgeTable) {
    HashMap<Long, AbstractEdge> edges = new HashMap<Long, AbstractEdge>();

    long numEdges = qs.executeQueryForLongResult(
        "COPY SELECT COUNT(*) FROM " + targetEdgeTable + " TO stdout;");
    if (numEdges == 0) {
      return edges;
    }

    qs.executeQuery("DROP TABLE m_answer;\n" +
                    "CREATE TABLE m_answer(id INT);\n" +
                    "DROP TABLE m_answer_edge;\n" +
                    "CREATE TABLE m_answer_edge(id LONG, src INT, dst INT);\n" +
                    "\\analyzerange " + targetEdgeTable + "\n" +
                    "INSERT INTO m_answer_edge SELECT * FROM edge" +
                    " WHERE id IN (SELECT id FROM " + targetEdgeTable + ");\n" +
                    "INSERT INTO m_answer SELECT src FROM m_answer_edge;\n" +
                    "INSERT INTO m_answer SELECT dst FROM m_answer_edge;");

    HashMap<Integer, AbstractVertex> vertices = exportVertices(qs, "m_answer");

    String edgeStr = qs.executeQuery(
        "COPY SELECT * FROM m_answer_edge TO stdout WITH (DELIMITER e'\\n');");
    String[] edgeLines = edgeStr.split("\n");
    edgeStr = null;

    assert edgeLines.length % 3 == 0;
    for (int i = 0; i < edgeLines.length; i += 3) {
      Long id = Long.parseLong(edgeLines[i]);
      Integer src = Integer.parseInt(edgeLines[i+1]);
      Integer dst = Integer.parseInt(edgeLines[i+2]);
      edges.put(id, new Edge(vertices.get(src), vertices.get(dst)));
    }
    edgeLines = null;

    String edgeAnnoStr = qs.executeQuery(
        "COPY SELECT * FROM edge_anno WHERE id IN (SELECT id FROM " +
        targetEdgeTable + ") TO stdout WITH (DELIMITER e'\\n');");
    String[] edgeAnnoLines = edgeAnnoStr.split("\n");
    edgeAnnoStr = null;

    assert edgeAnnoLines.length % 3 == 0;
    for (int i = 0; i < edgeAnnoLines.length; i += 3) {
      // TODO: accelerate with cache.
      Long id = Long.parseLong(edgeAnnoLines[i]);
      AbstractEdge edge = edges.get(id);
      if (edge == null) {
        continue;
      }
      edge.addAnnotation(edgeAnnoLines[i+1], edgeAnnoLines[i+2]);
    }
    qs.executeQuery("DROP TABLE m_answer;\n" +
                    "DROP TABLE m_answer_edge;");
    return edges;
  }

  @Override
  public String getLabel() {
    return "ExportGraph";
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
    inline_field_names.add("format");
    inline_field_values.add(format.name());
    inline_field_names.add("force");
    inline_field_values.add(String.valueOf(force));
  }
}
