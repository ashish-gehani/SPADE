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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.query.graph.execution.ExecutionContext;
import spade.query.graph.execution.GetPath;
import spade.query.graph.kernel.Environment;
import spade.query.graph.utility.TreeStringSerializable;
import spade.query.postgresql.execution.Instruction;

/**
 * Similar to GetPath but the result graph only contains vertices / edges that
 * are on the shortest paths.
 * <p>
 * Warning: This operation could be very slow when the input graph is large.
 * Right now, it just gets a sample path instead of the shortest path
 */
public class GetShortestPath extends Instruction
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

    public GetShortestPath(Graph targetGraph, Graph subjectGraph,
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
        GetPath getPath = new GetPath(targetGraph, subjectGraph, sourceGraph,
                destinationGraph, maxDepth);
        getPath.execute(env, ctx);
        // traverse a path in the graph of all paths
        Set<AbstractVertex> pathVertices = new HashSet<>();
        Set<AbstractEdge> pathEdges = new HashSet<>();
        Set<AbstractVertex> destinationVertexSet = destinationGraph.vertexSet();
        AbstractVertex sourceVertex = sourceGraph.vertexSet().iterator().next();
        pathVertices.add(sourceVertex);
        while(!destinationVertexSet.contains(sourceVertex))
        {
            //TODO: utilize LinkedHashSet structure to speed this up?
            for(AbstractEdge pathEdge : targetGraph.edgeSet())
            {
                if(pathEdge.getChildVertex().equals(sourceVertex))
                {
                    sourceVertex = pathEdge.getParentVertex();
                    pathVertices.add(sourceVertex);
                    pathEdges.add(pathEdge);
                    break;
                }
            }
        }
        targetGraph.vertexSet().retainAll(pathVertices);
        targetGraph.edgeSet().retainAll(pathEdges);
        ctx.addResponse(targetGraph);
    }

    @Override
    public String getLabel()
    {
        return "GetShortestPath";
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
