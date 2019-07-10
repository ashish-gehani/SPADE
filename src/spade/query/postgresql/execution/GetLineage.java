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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import static spade.core.AbstractAnalyzer.setRemoteResolutionRequired;
import static spade.query.graph.utility.CommonVariables.Direction;

/**
 * Get the lineage of a set of vertices in a graph.
 */
public class GetLineage extends Instruction
{
    // Output graph.
    private Graph targetGraph;
    // Input graph. Not used for Postgres
    private Graph subjectGraph;
    // Set of starting vertices.
    private Graph startGraph;
    // Max depth.
    private Integer depth;
    // Direction (ancestors / descendants, or both).
    private Direction direction;

    public GetLineage(Graph targetGraph, Graph subjectGraph,
                      Graph startGraph, Integer depth, Direction direction)
    {
        this.targetGraph = targetGraph;
        this.subjectGraph = subjectGraph;
        this.startGraph = startGraph;
        this.depth = depth;
        this.direction = direction;
    }

    @Override
    public void execute(Environment env, ExecutionContext ctx)
    {
        Set<AbstractVertex> remainingVertices = new HashSet<>();
        Set<AbstractVertex> visitedVertices = new HashSet<>();
        int current_depth = 0;

        Set<AbstractVertex> startingVertexSet = startGraph.vertexSet();
        targetGraph.setRootVertexSet(startingVertexSet);
        for(AbstractVertex startingVertex : startingVertexSet)
        {
            startingVertex.setDepth(current_depth);
            remainingVertices.add(startingVertex);
            targetGraph.putVertex(startingVertex);
        }

        while(!remainingVertices.isEmpty() && current_depth < this.depth)
        {
            Set<AbstractVertex> currentSet = new HashSet<>();
            Graph neighbors = new Graph();
            Graph currentGraph = new Graph();
            currentGraph.vertexSet().addAll(remainingVertices);
            if(direction == Direction.kAncestor || direction == Direction.kBoth)
            {
                GetParents getParents = new GetParents(neighbors, subjectGraph, currentGraph,
                        null, null, null);
                getParents.execute(env, ctx);
            }
            else if(direction == Direction.kDescendant || direction == Direction.kBoth)
            {
                GetChildren getChildren = new GetChildren(neighbors, subjectGraph, currentGraph,
                        null, null, null);
                getChildren.execute(env, ctx);
            }
            targetGraph.vertexSet().addAll(neighbors.vertexSet());
            targetGraph.edgeSet().addAll(neighbors.edgeSet());
            visitedVertices.addAll(remainingVertices);

            // for remote querying and discrepancy checking
            for(AbstractVertex neighborVertex : neighbors.vertexSet())
            {
                neighborVertex.setDepth(current_depth + 1);
                if(!visitedVertices.contains(neighborVertex))
                {
                    currentSet.add(neighborVertex);
                }
                if(neighborVertex.isCompleteNetworkVertex())
                {
                    setRemoteResolutionRequired();
                    targetGraph.putNetworkVertex(neighborVertex, current_depth);
                }
            }
            remainingVertices.clear();
            remainingVertices.addAll(currentSet);
            current_depth++;
        }
        targetGraph.setComputeTime(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z").format(new Date()));
    }

    @Override
    public String getLabel()
    {
        return "GetLineage";
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
        inline_field_names.add("startGraph");
        inline_field_values.add(startGraph.getName());
        inline_field_names.add("depth");
        inline_field_values.add(String.valueOf(depth));
        inline_field_names.add("direction");
        inline_field_values.add(direction.name().substring(1));
    }
}
