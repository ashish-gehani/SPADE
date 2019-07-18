package spade.query.postgresql.execution;

import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.query.graph.execution.ExecutionContext;
import spade.query.graph.execution.Instruction;
import spade.query.graph.utility.CommonFunctions;
import spade.query.graph.kernel.Environment;
import spade.query.graph.utility.TreeStringSerializable;

import java.util.ArrayList;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static spade.query.graph.utility.CommonVariables.CHILD_VERTEX_KEY;
import static spade.query.graph.utility.CommonVariables.EDGE_TABLE;
import static spade.query.graph.utility.CommonVariables.PARENT_VERTEX_KEY;
import static spade.query.graph.utility.CommonVariables.PRIMARY_KEY;
import static spade.query.graph.utility.CommonVariables.VERTEX_TABLE;

public class GetParents extends Instruction
{
    // Output graph.
    private Graph targetGraph;
    // Input Graph. Not used for Postgres
    private Graph subjectGraph;
    // Set of starting child vertices
    private Graph startGraph;
    private String field;
    private String operation;
    private String value;
    private Logger logger = Logger.getLogger(GetParents.class.getName());

    public GetParents(Graph targetGraph, Graph subjectGraph,
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
            StringBuilder childVertexHashes = new StringBuilder(100);
            for(AbstractVertex childVertex : this.startGraph.vertexSet())
            {
                childVertexHashes.append("'");
                childVertexHashes.append(childVertex.bigHashCode());
                childVertexHashes.append("'");
                childVertexHashes.append(", ");
            }
            StringBuilder getParentsQuery = new StringBuilder(200);
            getParentsQuery.append("SELECT * FROM ");
            getParentsQuery.append(VERTEX_TABLE);
            getParentsQuery.append(" WHERE \"");
            getParentsQuery.append(PRIMARY_KEY);
            getParentsQuery.append("\" IN (");
            getParentsQuery.append("SELECT \"");
            getParentsQuery.append(PARENT_VERTEX_KEY);
            getParentsQuery.append("\" FROM ");
            getParentsQuery.append(EDGE_TABLE);
            getParentsQuery.append(" WHERE \"");
            getParentsQuery.append(CHILD_VERTEX_KEY);
            getParentsQuery.append("\" IN (");
            getParentsQuery.append(childVertexHashes.substring(0, childVertexHashes.length() - 2));
            getParentsQuery.append("))");

            if(field != null)
            {
                if(!field.equals("*"))
                {
                    // TODO: handle wild card columns
                    getParentsQuery.append(" AND \"");
                    getParentsQuery.append(field);
                    getParentsQuery.append("\"");
                    getParentsQuery.append(operation);
                    getParentsQuery.append("'");
                    getParentsQuery.append(value);
                    getParentsQuery.append("'");
                }
            }
            Set<AbstractVertex> parentVertexSet = targetGraph.vertexSet();
            CommonFunctions.executeGetVertex(parentVertexSet, getParentsQuery.toString());

            if(parentVertexSet.size() > 0)
            {
                // get all edges between child and parent vertices
                StringBuilder parentVertexHashes = new StringBuilder(200);
                for(AbstractVertex parentVertex : parentVertexSet)
                {
                    parentVertexHashes.append("'");
                    parentVertexHashes.append(parentVertex.bigHashCode());
                    parentVertexHashes.append("'");
                    parentVertexHashes.append(", ");
                }
                CommonFunctions.getAllVertexEdges(targetGraph, childVertexHashes, parentVertexHashes);
            }
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
