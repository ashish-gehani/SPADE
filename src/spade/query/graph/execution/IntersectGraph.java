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
package spade.query.graph.execution;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.query.graph.kernel.Environment;
import spade.query.graph.utility.TreeStringSerializable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Intersect two graphs (i.e. find common vertices and edges).
 */

public class IntersectGraph extends Instruction
{
    // Output graph.
    private Graph targetGraph;
    // Input graphs.
    private Graph lhsGraph;
    private Graph rhsGraph;

    public IntersectGraph(Graph targetGraph, Graph lhsGraph, Graph rhsGraph)
    {
        this.targetGraph = targetGraph;
        this.lhsGraph = lhsGraph;
        this.rhsGraph = rhsGraph;
    }

    @Override
    public void execute(Environment env, ExecutionContext ctx)
    {
        Set<AbstractVertex> targetVertexSet = targetGraph.vertexSet();
        Set<AbstractEdge> targetEdgeSet = targetGraph.edgeSet();

        targetVertexSet.addAll(lhsGraph.vertexSet());
        targetVertexSet.retainAll(rhsGraph.vertexSet());
        targetEdgeSet.addAll(lhsGraph.edgeSet());
        targetEdgeSet.retainAll(rhsGraph.edgeSet());

        ctx.addResponse(targetGraph);
    }

    @Override
    public String getLabel()
    {
        return "IntersectGraph";
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
        inline_field_names.add("lhsGraph");
        inline_field_values.add(lhsGraph.getName());
        inline_field_names.add("rhsGraph");
        inline_field_values.add(rhsGraph.getName());
    }
}
