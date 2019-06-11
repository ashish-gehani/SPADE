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

import org.apache.commons.lang3.StringUtils;
import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.query.graph.execution.utility.Utility;
import spade.query.postgresql.kernel.Environment;
import spade.query.postgresql.utility.TreeStringSerializable;

import java.util.ArrayList;
import java.util.Set;

import static spade.query.graph.execution.utility.Utility.EDGE_TABLE;
import static spade.query.graph.execution.utility.Utility.VERTEX_TABLE;

/**
 * Get end points of all edges in a graph.
 */
public class GetEdgeEndpoint extends Instruction
{
    public enum Component
    {
        kSource,
        kDestination,
        kBoth
    }

    // Output graph.
    private Graph targetGraph;
    // Input graph containing edges.
    private Graph subjectGraph;
    // End-point component (source / destination, or both)
    private Component component;

    public GetEdgeEndpoint(Graph targetGraph, Graph subjectGraph, Component component)
    {
        this.targetGraph = targetGraph;
        this.subjectGraph = subjectGraph;
        this.component = component;
    }

    @Override
    public void execute(Environment env, ExecutionContext ctx)
    {
        Set<AbstractVertex> vertexSet = targetGraph.vertexSet();
        String sqlQuery = null;
        StringBuilder edgeHashes = new StringBuilder(200);
        if(component == Component.kSource || component == Component.kBoth)
        {
            for(AbstractEdge edge : subjectGraph.edgeSet())
            {
                edgeHashes.append(edge.bigHashCode()).append(", ");
            }
            sqlQuery = "SELECT * FROM " + VERTEX_TABLE + " WHERE hash = " +
                    "(SELECT sourceVertexHash FROM " + EDGE_TABLE +
                    " WHERE hash IN (" + edgeHashes.substring(0, edgeHashes.length() - 2) + "));";
        }
        Utility.executeGetVertex(vertexSet, sqlQuery);

        if(component == Component.kDestination || component == Component.kBoth)
        {
            for(AbstractEdge edge : subjectGraph.edgeSet())
            {
                edgeHashes.append(edge.bigHashCode()).append(", ");
            }
            sqlQuery = "SELECT * FROM " + VERTEX_TABLE + " WHERE hash = " +
                    "(SELECT destinationVertexHash FROM " + EDGE_TABLE +
                    " WHERE hash IN (" + edgeHashes.substring(0, edgeHashes.length() - 2) + "));";
        }
        Utility.executeGetVertex(vertexSet, sqlQuery);
        ctx.addResponse(targetGraph);

    }

    @Override
    public String getLabel()
    {
        return "GetEdgeEndpoint";
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
        inline_field_names.add("component");
        inline_field_values.add(component.name().substring(1));
    }

}
