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
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

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
        Set<AbstractVertex> targetVertexSet = targetGraph.vertexSet();
        Set<AbstractVertex> vertexSet = subjectGraph.vertexSet();
        List<AbstractVertex> vertexList = new LinkedList<>(vertexSet);
        Collections.shuffle(vertexList);
        int lower_limit = Math.min(limit, vertexList.size());
        for(int i = 0; i < lower_limit; i++)
        {
            AbstractVertex vertex = vertexList.get(i);
            targetVertexSet.add(vertex);
        }

        Set<AbstractEdge> targetEdgeSet = targetGraph.edgeSet();
        Set<AbstractEdge> edgeSet = targetGraph.edgeSet();
        List<AbstractEdge> edgeList = new LinkedList<>(edgeSet);
        Collections.shuffle(edgeList);
        lower_limit = Math.min(limit, edgeList.size());
        for(int i = 0; i < lower_limit; i++)
        {
            AbstractEdge edge = edgeList.get(i);
            targetEdgeSet.add(edge);
        }
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
