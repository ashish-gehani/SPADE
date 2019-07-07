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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.Graph;
import spade.query.graph.execution.ExecutionContext;
import spade.query.graph.kernel.Environment;
import spade.query.graph.utility.TreeStringSerializable;

import static spade.core.AbstractQuery.currentStorage;
import static spade.query.graph.utility.CommonVariables.EDGE_TABLE;
import static spade.query.graph.utility.CommonVariables.VERTEX_TABLE;

/**
 * Show statistics of a graph.
 */
public class StatGraph extends Instruction
{
    private Graph targetGraph;
    private static Logger logger = Logger.getLogger(StatGraph.class.getName());

    public StatGraph(Graph targetGraph)
    {
        this.targetGraph = targetGraph;
    }

    @Override
    public void execute(Environment env, ExecutionContext ctx)
    {
        String numVerticesQuery = "SELECT COUNT(*) FROM " + VERTEX_TABLE + ";";
        logger.log(Level.INFO, "Executing query: " + numVerticesQuery);
        try
        {
            ResultSet result = (ResultSet) currentStorage.executeQuery(numVerticesQuery);
            result.next();
            int numVertices = result.getInt(1);

            String numEdgesQuery = "SELECT COUNT(*) FROM " + EDGE_TABLE + ";";
            result = (ResultSet) currentStorage.executeQuery(numEdgesQuery);
            result.next();
            int numEdges = result.getInt(1);
            ;
            String stat = "# vertices = " + numVertices + ", # edges = " + numEdges;
            ctx.addResponse(stat);
        }
        catch(SQLException ex)
        {
            logger.log(Level.SEVERE, "Error executing StatGraph query", ex);
        }

    }

    @Override
    public String getLabel()
    {
        return "StatGraph";
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
    }
}
