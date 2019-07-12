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

import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.query.graph.execution.ExecutionContext;
import spade.query.graph.execution.Instruction;
import spade.query.graph.kernel.Environment;
import spade.query.graph.utility.TreeStringSerializable;
import spade.query.graph.execution.GetEdgeEndpoints.Component;

import java.util.ArrayList;
import java.util.Set;

/**
 * Let $S be the subject graph and $T be the skeleton graph.
 * The operation $S.getSubgraph($T) is to find all the vertices and edges that
 * are spanned by the skeleton graph.
 */
public class GetSubgraph extends Instruction
{
    private Graph targetGraph;
    private Graph subjectGraph;
    private Graph skeletonGraph;

    public GetSubgraph(Graph targetGraph, Graph subjectGraph, Graph skeletonGraph)
    {
        this.targetGraph = targetGraph;
        this.subjectGraph = subjectGraph;
        this.skeletonGraph = skeletonGraph;
    }

    @Override
    public void execute(Environment env, ExecutionContext ctx)
    {
        // Get all vertices that are in skeleton and subject.
        Set<AbstractVertex> skeletonVertexSet = skeletonGraph.vertexSet();
        targetGraph.vertexSet().addAll(skeletonVertexSet);
        // Get endpoints of all edges in skeleton that are in subject
        GetEdgeEndpoints getEdgeEndpoint = new GetEdgeEndpoints(targetGraph, skeletonGraph, Component.kBoth);
        getEdgeEndpoint.execute(env, ctx);

        // Get all edges between the vertices gathered above.
        GetEdgesFromEndpoints getEdgesFromEndpoints = new GetEdgesFromEndpoints(targetGraph, subjectGraph,
                targetGraph, targetGraph);
        getEdgesFromEndpoints.execute(env, ctx);
        ctx.addResponse(targetGraph);

    }

    @Override
    public String getLabel()
    {
        return "GetSubgraph";
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
        inline_field_names.add("skeletonGraph");
        inline_field_values.add(skeletonGraph.getName());
    }
}
