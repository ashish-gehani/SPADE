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

import com.restfb.types.Post;
import spade.core.Graph;
import spade.query.graph.execution.ExecutionContext;
import spade.query.graph.execution.Instruction;
import spade.query.graph.kernel.Environment;
import spade.query.graph.utility.CommonFunctions;
import spade.query.graph.utility.TreeStringSerializable;
import spade.query.postgresql.utility.PostgresUtil;

import java.util.ArrayList;

import static spade.query.graph.utility.CommonVariables.EDGE_TABLE;
import static spade.query.graph.utility.CommonVariables.VERTEX_TABLE;

/**
 * Export a QuickGrail graph to string or DOT representation.
 */
public class ExportGraph extends Instruction
{
    private static final int kNonForceVisualizeLimit = 10000;
    private static final int kNonForceDumpLimit = 100;

    private Graph targetGraph;
    private Format format;
    private boolean force;

    public enum Format
    {
        kNormal,
        kDot
    }

    public ExportGraph(Graph targetGraph, Format format, boolean force)
    {
        this.targetGraph = targetGraph;
        this.format = format;
        this.force = force;
    }

    @Override
    public void execute(Environment env, ExecutionContext ctx)
    {
        if(!force)
        {
            long numVertices = PostgresUtil.getNumVertices(targetGraph);
            long numEdges = PostgresUtil.getNumEdges(targetGraph);
            long graphSize = numEdges + numVertices;
            if(format == Format.kNormal && (graphSize > kNonForceDumpLimit))
            {
                ctx.addResponse("It may take a long time to print the result data due to " +
                        "too many vertices/edges: " + numVertices + "/" + numEdges + "\n" +
                        "Please use 'dump force ...' to force the transfer");
                return;
            }
            if(format == Format.kDot && (graphSize > kNonForceVisualizeLimit))
            {
                ctx.addResponse("It may take a long time to transfer the result data due to " +
                        "too many vertices/edges: " + numVertices + "/" + numEdges + "\n" +
                        "Please use 'dump visualize ...' to force the transfer");
                return;
            }
        }
        if(Environment.IsBaseGraph(targetGraph))
        {
            // gets all vertices too
            String edgeQuery = "SELECT * FROM " + EDGE_TABLE;
            CommonFunctions.executeGetEdge(targetGraph, edgeQuery, true);
        }
        if(format == Format.kNormal)
        {
            ctx.addResponse(targetGraph.toString());
        }
        else if(format == Format.kDot)
        {
            ctx.addResponse(targetGraph.exportGraph());
        }
    }

    @Override
    public String getLabel()
    {
        return "ExportGraph";
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
        inline_field_names.add("format");
        inline_field_values.add(format.name());
        inline_field_names.add("force");
        inline_field_values.add(String.valueOf(force));
    }
}
