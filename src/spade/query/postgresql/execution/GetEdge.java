package spade.query.postgresql.execution;

import spade.core.Graph;
import spade.query.graph.execution.ExecutionContext;
import spade.query.graph.utility.CommonFunctions;
import spade.query.graph.kernel.Environment;
import spade.query.graph.utility.TreeStringSerializable;

import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import static spade.query.graph.utility.CommonVariables.EDGE_TABLE;
import static spade.query.postgresql.kernel.Resolver.formatString;


public class GetEdge extends Instruction
{
    // Output graph.
    private Graph targetGraph;
    private String field;
    private String operation;
    private String value;
    private Logger logger = Logger.getLogger(GetEdge.class.getName());

    public GetEdge(Graph targetGraph, String field, String operation, String value)
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
            StringBuilder getEdgeQuery = new StringBuilder(100);
            getEdgeQuery.append("SELECT * FROM " + EDGE_TABLE);
            if(field != null)
            {
                if(!field.equals("*"))
                {
                    // TODO: handle wild card columns
                    getEdgeQuery.append(" WHERE ");
                    getEdgeQuery.append(formatString(field));
                    getEdgeQuery.append(operation);
                    getEdgeQuery.append(formatString(value));
                }
            }
            CommonFunctions.executeGetEdge(targetGraph.edgeSet(), getEdgeQuery.toString());
            ctx.addResponse(targetGraph);
        }
        catch(Exception ex)
        {
            logger.log(Level.SEVERE, "Error creating edge set from the query", ex);
        }
    }


    @Override
    public String getLabel()
    {
        return "GetEdge";
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
