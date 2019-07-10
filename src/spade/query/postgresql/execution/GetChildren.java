package spade.query.postgresql.execution;

import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.query.graph.execution.ExecutionContext;
import spade.query.graph.execution.Instruction;
import spade.query.graph.utility.CommonFunctions;
import spade.query.graph.kernel.Environment;
import spade.query.graph.utility.TreeStringSerializable;

import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import static spade.query.graph.utility.CommonVariables.CHILD_VERTEX_KEY;
import static spade.query.graph.utility.CommonVariables.EDGE_TABLE;
import static spade.query.graph.utility.CommonVariables.PARENT_VERTEX_KEY;
import static spade.query.graph.utility.CommonVariables.PRIMARY_KEY;
import static spade.query.graph.utility.CommonVariables.VERTEX_TABLE;

public class GetChildren extends Instruction
{
    // Output graph.
    private Graph targetGraph;
    // Input Graph. Not used for Postgres
    private Graph subjectGraph;
    // Set of starting parent vertices
    private Graph startGraph;
    private String field;
    private String operation;
    private String value;
    private Logger logger = Logger.getLogger(GetChildren.class.getName());

    public GetChildren(Graph targetGraph, Graph subjectGraph,
                       Graph startGraph, String field, String operation, String value)
    {
        this.targetGraph = targetGraph;
        this.subjectGraph = subjectGraph;
        this.startGraph = startGraph;
        this.field = field;
        this.operation = operation;
        this.value = value;
    }

    @Override
    public void execute(Environment env, ExecutionContext ctx)
    {
        try
        {
            StringBuilder parentVertexHashes = new StringBuilder(100);
            for(AbstractVertex parentVertex : this.startGraph.vertexSet())
            {
                parentVertexHashes.append("'");
                parentVertexHashes.append(parentVertex.bigHashCode());
                parentVertexHashes.append("'");
                parentVertexHashes.append(", ");
            }
            StringBuilder getChildrenQuery = new StringBuilder(200);
            getChildrenQuery.append("SELECT * FROM ");
            getChildrenQuery.append(VERTEX_TABLE);
            getChildrenQuery.append(" WHERE \"");
            getChildrenQuery.append(PRIMARY_KEY);
            getChildrenQuery.append("\" IN (");
            getChildrenQuery.append("SELECT \"");
            getChildrenQuery.append(CHILD_VERTEX_KEY);
            getChildrenQuery.append("\" FROM ");
            getChildrenQuery.append(EDGE_TABLE);
            getChildrenQuery.append(" WHERE \"");
            getChildrenQuery.append(PARENT_VERTEX_KEY);
            getChildrenQuery.append("\" IN (");
            getChildrenQuery.append(parentVertexHashes.substring(0, parentVertexHashes.length() - 2));
            getChildrenQuery.append("))");

            if(field != null)
            {
                if(!field.equals("*"))
                {
                    // TODO: handle wild card columns
                    getChildrenQuery.append(" AND \"");
                    getChildrenQuery.append(field);
                    getChildrenQuery.append("\"");
                    getChildrenQuery.append(operation);
                    getChildrenQuery.append("'");
                    getChildrenQuery.append(value);
                    getChildrenQuery.append("'");
                }
            }

            CommonFunctions.executeGetVertex(targetGraph.vertexSet(), getChildrenQuery.toString());
            ctx.addResponse(targetGraph);
        }
        catch(Exception ex)
        {
            logger.log(Level.SEVERE, "Error fetching children of the vertex", ex);
        }
    }

    @Override
    public String getLabel()
    {
        return "GetChildren";
    }

    @Override
    protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values, ArrayList<String> non_container_child_field_names, ArrayList<TreeStringSerializable> non_container_child_fields, ArrayList<String> container_child_field_names, ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields)
    {

    }
}
