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

import spade.core.AbstractEdge;
import spade.core.AbstractStorage;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Graph;
import spade.core.Vertex;
import spade.query.graph.execution.ExecutionContext;
import spade.query.graph.execution.Instruction;
import spade.query.graph.kernel.Environment;
import spade.query.graph.utility.TreeStringSerializable;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static spade.core.AbstractQuery.currentStorage;
import static spade.query.graph.utility.CommonVariables.PRIMARY_KEY;

/**
 * Evaluate an arbitrary SQL query to get edges.
 */
public class EvaluateGetEdgeQuery extends Instruction
{
    private Graph targetGraph;
    private String sqlQuery;
    private static Logger logger = Logger.getLogger(EvaluateGetEdgeQuery.class.getName());

    public EvaluateGetEdgeQuery(Graph targetGraph, String sqlQuery)
    {
        this.targetGraph = targetGraph;
        this.sqlQuery = sqlQuery;
    }


    @Override
    public void execute(Environment env, ExecutionContext ctx)
    {
        Set<AbstractEdge> edgeSet = targetGraph.edgeSet();
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
                // TODO: apply the new world where vertices with only hashes could be created
                AbstractVertex childVertex = new Vertex();
                AbstractVertex parentVertex = new Vertex();
                AbstractEdge edge = new Edge(childVertex, parentVertex);
                for(int i = 1; i <= columnCount; i++)
                {
                    String colName = columnLabels.get(i);
                    String value = result.getString(i);
                    if(value != null)
                    {
                        if(colName != null && !colName.equals(AbstractStorage.PRIMARY_KEY))
                        {
                            edge.addAnnotation(colName, value);
                        }
                    }
                }
                edgeSet.add(edge);
            }
        }
        catch(SQLException ex)
        {
            logger.log(Level.SEVERE, "Error executing GetEdge Query", ex);
        }

        ctx.addResponse(targetGraph);
    }

    @Override
    public String getLabel()
    {
        return "EvaluateGetEdgeQuery";
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
