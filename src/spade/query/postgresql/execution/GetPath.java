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
import spade.query.graph.execution.IntersectGraph;
import spade.query.graph.kernel.Environment;
import spade.query.graph.utility.CommonVariables.Direction;
import spade.query.graph.utility.TreeStringSerializable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Get a graph that includes all the paths from a set of source vertices to a
 * set of destination vertices.
 */
public class GetPath extends Instruction
{
    // Output graph.
    private Graph targetGraph;
    // Input graph.
    private Graph subjectGraph;
    // Set of source vertices.
    private Graph sourceGraph;
    // Set of destination vertices.
    private Graph destinationGraph;
    // Max path length.
    private Integer maxDepth;

    private static final Logger logger = Logger.getLogger(GetPath.class.getName());

    public GetPath(Graph targetGraph, Graph subjectGraph,
                   Graph sourceGraph, Graph destinationGraph, Integer maxDepth)
    {
        this.targetGraph = targetGraph;
        this.subjectGraph = subjectGraph;
        this.sourceGraph = sourceGraph;
        this.destinationGraph = destinationGraph;
        this.maxDepth = maxDepth;
    }

    @Override
    public void execute(Environment env, ExecutionContext ctx)
    {
        // compute ancestor graph from the source vertices
        Direction direction;
        direction = Direction.kAncestor;
        Graph ancestorGraph = env.allocateGraph();
        GetLineage ancestorLineage = new GetLineage(ancestorGraph, subjectGraph, sourceGraph, maxDepth, direction);
        ancestorLineage.execute(env, ctx);
        logger.log(Level.INFO, "ancestorGraph: " + ancestorGraph.toString());

        // compute descendant graph from the destination vertices
        direction = Direction.kDescendant;
        Graph descendantGraph = env.allocateGraph();
        GetLineage descendantLineage = new GetLineage(descendantGraph, subjectGraph, destinationGraph,
                maxDepth, direction);
        descendantLineage.execute(env, ctx);
        logger.log(Level.INFO, "descendantGraph: " + descendantGraph.toString());

        // retain only common edges in both results, and their endpoint vertices
        IntersectGraph intersectGraph = new IntersectGraph(targetGraph, ancestorGraph, descendantGraph,
                IntersectGraph.Component.kEdge);
        intersectGraph.execute(env, ctx);
        // check if any of the source and destination vertices are retained
        Set<AbstractVertex> targetVertexSet = targetGraph.vertexSet();
        if(Collections.disjoint(targetVertexSet, sourceGraph.vertexSet()) ||
                Collections.disjoint(targetVertexSet, destinationGraph.vertexSet()))
        {
            targetGraph.vertexSet().clear();
            targetGraph.edgeSet().clear();
        }

        ctx.addResponse(targetGraph);
    }

    @Override
    public String getLabel()
    {
        return "GetPath";
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
        inline_field_names.add("sourceGraph");
        inline_field_values.add(sourceGraph.getName());
        inline_field_names.add("destinationGraph");
        inline_field_values.add(destinationGraph.getName());
        inline_field_names.add("maxDepth");
        inline_field_values.add(String.valueOf(maxDepth));
    }
}
