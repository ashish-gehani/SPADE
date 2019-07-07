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
import spade.query.graph.execution.ExecutionContext;
import spade.query.graph.kernel.Environment;
import spade.query.graph.utility.TreeStringSerializable;

import java.util.ArrayList;

/**
 * Export a QuickGrail graph to string or DOT representation.
 */
public class ExportGraph extends Instruction
{
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
        if(format == Format.kNormal)
        {
            ctx.addResponse(targetGraph.toString());
        }
        else
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
