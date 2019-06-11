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

import spade.core.Graph;
import spade.query.postgresql.kernel.Environment;
import spade.query.postgresql.utility.TreeStringSerializable;

import java.util.ArrayList;

/**
 * Union one graph into the other.
 */
public class UnionGraph extends Instruction
{
    // The target graph.
    private Graph targetGraph;
    // The source graph to be unioned into the target graph.
    private Graph sourceGraph;

    public UnionGraph(Graph targetGraph, Graph sourceGraph)
    {
        this.targetGraph = targetGraph;
        this.sourceGraph = sourceGraph;
    }

    @Override
    public void execute(Environment env, ExecutionContext ctx)
    {
        targetGraph.vertexSet().addAll(sourceGraph.vertexSet());
        targetGraph.edgeSet().addAll(sourceGraph.edgeSet());
        // adding network maps
        targetGraph.networkMap().putAll(sourceGraph.networkMap());

        ctx.addResponse(targetGraph);
    }

    @Override
    public String getLabel()
    {
        return "UnionGraph";
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
        inline_field_names.add("sourceGraph");
        inline_field_values.add(sourceGraph.getName());
    }
}
