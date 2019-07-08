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
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.core.Vertex;
import spade.query.graph.execution.ExecutionContext;
import spade.query.graph.execution.Instruction;
import spade.query.graph.kernel.Environment;
import spade.query.graph.utility.TreeStringSerializable;

import static spade.core.AbstractQuery.currentStorage;
import static spade.query.graph.utility.CommonVariables.PRIMARY_KEY;

/**
 * Evaluate an arbitrary SQL query to get vertices.
 */
public class EvaluateGetVertexQuery extends Instruction
{
    private Graph targetGraph;
    private String sqlQuery;
    private static Logger logger = Logger.getLogger(EvaluateGetVertexQuery.class.getName());

    public EvaluateGetVertexQuery(Graph targetGraph, String sqlQuery)
    {
        this.targetGraph = targetGraph;
        this.sqlQuery = sqlQuery;
    }

    @Override
    public void execute(Environment env, ExecutionContext ctx)
    {
        Set<AbstractVertex> vertexSet = targetGraph.vertexSet();
        logger.log(Level.INFO, "Executing query: " + sqlQuery);
        ResultSet result = (ResultSet) currentStorage.executeQuery(sqlQuery);
        ResultSetMetaData metadata;
        try
        {
            metadata = result.getMetaData();
            int columnCount = metadata.getColumnCount();

            Map<Integer, String> columnLabels = new HashMap<>();
            for(int i = 1; i <= columnCount; i++)
            {
                columnLabels.put(i, metadata.getColumnName(i));
            }

            while(result.next())
            {
                AbstractVertex vertex = new Vertex();
                for(int i = 1; i <= columnCount; i++)
                {
                    String colName = columnLabels.get(i);
                    String value = result.getString(i);
                    if(value != null)
                    {
                        if(colName != null && !colName.equals(PRIMARY_KEY))
                        {
                            vertex.addAnnotation(colName, value);
                        }
                    }
                }
                vertexSet.add(vertex);
            }
        }
        catch(SQLException ex)
        {
            logger.log(Level.SEVERE, "Error executing GetVertex Query", ex);
        }

        ctx.addResponse(targetGraph);
    }

    @Override
    public String getLabel()
    {
        return "EvaluateGetVertexQuery";
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
        inline_field_names.add("sqlQuery");
        inline_field_values.add(sqlQuery);
    }
}
