package spade.query.postgresql.execution;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractVertex;
import spade.core.Vertex;
import spade.core.Graph;
import spade.query.graph.execution.utility.Utility;
import spade.query.postgresql.kernel.Environment;
import spade.query.postgresql.utility.TreeStringSerializable;

import static spade.core.AbstractQuery.currentStorage;
import static spade.core.AbstractStorage.PRIMARY_KEY;
import static spade.query.graph.execution.utility.Utility.VERTEX_TABLE;
import static spade.query.postgresql.kernel.Resolver.formatString;


public class GetVertex extends Instruction
{
    // Output graph.
    private Graph targetGraph;
    private String field;
    private String operation;
    private String value;
    private Logger logger = Logger.getLogger(GetVertex.class.getName());

    public GetVertex(Graph targetGraph, String field, String operation, String value)
    {
        this.targetGraph = targetGraph;
        this.field = field;
        this.operation = operation;
        this.value = value;
    }

    @Override
    public void execute(Environment env, ExecutionContext ctx)
    {
        try
        {
            StringBuilder sqlQuery = new StringBuilder(100);
            sqlQuery.append("SELECT * FROM " + VERTEX_TABLE);
            if(field != null)
            {
                if(!field.equals("*"))
                {
                    // TODO: do something about wild card columns
                    sqlQuery.append(" WHERE " + formatString(field) + operation + formatString(value));
                }
            }
            logger.log(Level.INFO, "Following query: " + sqlQuery.toString());

            Utility.executeGetVertex(targetGraph.vertexSet(), sqlQuery.toString());
            ctx.addResponse(targetGraph);
        }
        catch(Exception ex)
        {
            logger.log(Level.SEVERE, "Error creating vertex set from the query: ", ex);
        }
    }


    @Override
    public String getLabel()
    {
        return "GetVertex";
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
        inline_field_names.add("field");
        inline_field_values.add(field);
        inline_field_names.add("operation");
        inline_field_values.add(operation);
        inline_field_names.add("value");
        inline_field_values.add(value);
    }

}
