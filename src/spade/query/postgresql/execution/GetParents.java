package spade.query.postgresql.execution;

import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.query.graph.utility.CommonFunctions;
import spade.query.postgresql.kernel.Environment;
import spade.query.postgresql.utility.TreeStringSerializable;

import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import static spade.query.graph.utility.CommonVariables.CHILD_VERTEX_KEY;
import static spade.query.graph.utility.CommonVariables.EDGE_TABLE;
import static spade.query.graph.utility.CommonVariables.PARENT_VERTEX_KEY;
import static spade.query.graph.utility.CommonVariables.PRIMARY_KEY;
import static spade.query.graph.utility.CommonVariables.VERTEX_TABLE;
import static spade.query.postgresql.kernel.Resolver.formatString;

public class GetParents extends Instruction
{
    // Output graph.
    private Graph targetGraph;
    // Set of starting vertices
    private Graph subjectGraph;
    private String field;
    private String operation;
    private String value;
    private Logger logger = Logger.getLogger(GetParents.class.getName());

    public GetParents(Graph targetGraph, Graph subjectGraph, String field, String operation, String value)
    {
        this.targetGraph = targetGraph;
        this.subjectGraph = subjectGraph;
        this.field = field;
        this.operation = operation;
        this.value = value;
    }

    @Override
    public void execute(Environment env, ExecutionContext ctx)
    {
        try
        {
            StringBuilder edgeHashes = new StringBuilder(100);
            for(AbstractVertex vertex : subjectGraph.vertexSet())
            {
                edgeHashes.append(vertex.bigHashCode()).append(", ");
            }
            StringBuilder sqlQuery = new StringBuilder(200);
            sqlQuery.append("SELECT * FROM ");
            sqlQuery.append(VERTEX_TABLE);
            sqlQuery.append(" WHERE ");
            sqlQuery.append(PRIMARY_KEY);
            sqlQuery.append(" IN (");
            sqlQuery.append("SELECT ");
            sqlQuery.append(PARENT_VERTEX_KEY);
            sqlQuery.append(" FROM ");
            sqlQuery.append(EDGE_TABLE);
            sqlQuery.append(" WHERE ");
            sqlQuery.append(CHILD_VERTEX_KEY);
            sqlQuery.append(" IN (");
            sqlQuery.append(edgeHashes.substring(0, edgeHashes.length() - 2));
            sqlQuery.append("))");

            if(field != null)
            {
                if(!field.equals("*"))
                {
                    // TODO: handle wild card columns
                    sqlQuery.append(" AND ");
                    sqlQuery.append(formatString(field));
                    sqlQuery.append(operation);
                    sqlQuery.append(formatString(value));
                }
            }

            CommonFunctions.executeGetVertex(targetGraph.vertexSet(), sqlQuery.toString());
            ctx.addResponse(targetGraph);
        }
        catch(Exception ex)
        {
            logger.log(Level.SEVERE, "Error fetching parents of the vertex", ex);
        }
    }

    @Override
    public String getLabel()
    {
        return "GetParents";
    }

    @Override
    protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values, ArrayList<String> non_container_child_field_names, ArrayList<TreeStringSerializable> non_container_child_fields, ArrayList<String> container_child_field_names, ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields)
    {

    }
}
