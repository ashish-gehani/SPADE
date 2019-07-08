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
package spade.query.postgresql.execution;

import java.util.ArrayList;

import spade.core.Graph;
import spade.query.graph.execution.ExecutionContext;
import spade.query.graph.execution.Instruction;
import spade.query.graph.kernel.Environment;
import spade.query.graph.utility.TreeStringSerializable;

import static spade.query.graph.utility.CommonVariables.EDGE_TABLE;
import static spade.query.graph.utility.CommonVariables.VERTEX_TABLE;

/**
 * Sample a random subset of <limit> number of vertices and edges from a graph.
 */
public class LimitGraph extends Instruction
{
    // Output graph.
    private Graph targetGraph;
    // Input graph.
    private Graph subjectGraph;
    // The maximum number of vertices / edges to sample.
    private int limit;

    public LimitGraph(Graph targetGraph, Graph subjectGraph, int limit)
    {
        this.targetGraph = targetGraph;
        this.subjectGraph = subjectGraph;
        this.limit = limit;
    }

    @Override
    public void execute(Environment env, ExecutionContext ctx)
    {
        String getVertexQuery = "SELECT * FROM " + VERTEX_TABLE + " ORDER BY random() LIMIT " + limit;
        EvaluateGetVertexQuery evaluateGetVertexQuery = new EvaluateGetVertexQuery(targetGraph, getVertexQuery);
        evaluateGetVertexQuery.execute(env, ctx);

        String getEdgeQuery = "SELECT * FROM " + EDGE_TABLE + " ORDER BY random() LIMIT " + limit;
        EvaluateGetEdgeQuery evaluateGetEdgeQuery = new EvaluateGetEdgeQuery(targetGraph, getEdgeQuery);
        evaluateGetEdgeQuery.execute(env, ctx);
        ctx.addResponse(targetGraph);
    }

    @Override
    public String getLabel()
    {
        return "LimitGraph";
    }

    @Override
    protected void getFieldStringItems(
            ArrayList<String> inline_field_names,
            ArrayList<String> inline_field_values,
            ArrayList<String> non_container_child_field_names,
            ArrayList<TreeStringSerializable> non_container_child_fields,
            ArrayList<String> container_child_field_names,
            ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields)
    {
        inline_field_names.add("targetGraph");
        inline_field_values.add(targetGraph.getName());
        inline_field_names.add("subjectGraph");
        inline_field_values.add(subjectGraph.getName());
        inline_field_names.add("limit");
        inline_field_values.add(String.valueOf(limit));
    }
}
