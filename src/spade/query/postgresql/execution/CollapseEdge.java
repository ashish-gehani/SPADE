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

import java.util.ArrayList;

import spade.core.Graph;
import spade.query.graph.execution.ExecutionContext;
import spade.query.graph.execution.Instruction;
import spade.query.graph.utility.CommonFunctions;
import spade.query.graph.kernel.Environment;
import spade.query.graph.utility.TreeStringSerializable;

import static spade.query.graph.utility.CommonVariables.CHILD_VERTEX_KEY;
import static spade.query.graph.utility.CommonVariables.EDGE_TABLE;
import static spade.query.graph.utility.CommonVariables.PARENT_VERTEX_KEY;

/**
 * Collapse all edges whose specified fields are the same.
 */
public class CollapseEdge extends Instruction
{
    // Output graph.
    private Graph targetGraph;
    // Input graph.
    private Graph subjectGraph;
    // Fields to check.
    private ArrayList<String> fields;

    public CollapseEdge(Graph targetGraph, Graph subjectGraph, ArrayList<String> fields)
    {
        this.targetGraph = targetGraph;
        this.subjectGraph = subjectGraph;
        this.fields = fields;
    }

    @Override
    public void execute(Environment env, ExecutionContext ctx)
    {
        StringBuilder sqlQuery = new StringBuilder(100);
        sqlQuery.append("SELECT * FROM ");
        sqlQuery.append(EDGE_TABLE);
        sqlQuery.append(" GROUP BY \"");
        sqlQuery.append(CHILD_VERTEX_KEY);
        sqlQuery.append("\", \"");
        sqlQuery.append(PARENT_VERTEX_KEY);
        sqlQuery.append("\", ");
        for(String field : fields)
        {
            sqlQuery.append("\"");
            sqlQuery.append(field);
            sqlQuery.append("\", ");
        }

        String getEdgeQuery = sqlQuery.substring(0, sqlQuery.length() - 2);
        CommonFunctions.executeGetEdge(targetGraph, getEdgeQuery, false);
        targetGraph.vertexSet().addAll(subjectGraph.vertexSet());
        ctx.addResponse(targetGraph);
    }

    @Override
    public String getLabel()
    {
        return "CollapseEdge";
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
    }
}
