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
import java.util.Set;

import spade.core.AbstractEdge;
import spade.core.Graph;
import spade.query.graph.execution.ExecutionContext;
import spade.query.graph.utility.CommonFunctions;
import spade.query.graph.kernel.Environment;
import spade.query.graph.utility.TreeStringSerializable;

import static spade.query.graph.utility.CommonVariables.EDGE_TABLE;
import static spade.query.graph.utility.CommonVariables.PRIMARY_KEY;

/**
 * Insert a list of edgeHashes into a graph by hash.
 */
public class InsertLiteralEdge extends Instruction
{
    // The target graph to insert the edgeHashes.
    private Graph targetGraph;
    // Edge hashes to be inserted.
    private ArrayList<String> edgeHashes;

    public InsertLiteralEdge(Graph targetGraph, ArrayList<String> edges)
    {
        this.targetGraph = targetGraph;
        this.edgeHashes = edges;
    }

    @Override
    public void execute(Environment env, ExecutionContext ctx)
    {
        StringBuilder sqlQuery = new StringBuilder();
        sqlQuery.append("SELECT * FROM ");
        sqlQuery.append(EDGE_TABLE);
        sqlQuery.append(" WHERE ");
        sqlQuery.append(PRIMARY_KEY);
        sqlQuery.append(" IN (");
        for(String edgeHash : edgeHashes)
        {
            sqlQuery.append(edgeHash);
            sqlQuery.append(", ");
        }
        String query = sqlQuery.substring(0, sqlQuery.length() - 2) + ")";
        Set<AbstractEdge> edgeSet = targetGraph.edgeSet();
        CommonFunctions.executeGetEdge(edgeSet, query);
        ctx.addResponse(targetGraph);

    }

    @Override
    public String getLabel()
    {
        return "InsertLiteralEdge";
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
        inline_field_names.add("edgeHashes");
        inline_field_values.add("{" + String.join(",", edgeHashes) + "}");
    }
}
