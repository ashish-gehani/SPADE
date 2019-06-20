package spade.query.graph.execution;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.query.graph.utility.CommonFunctions;
import spade.query.postgresql.execution.ExecutionContext;
import spade.query.postgresql.execution.Instruction;
import spade.query.postgresql.kernel.Environment;
import spade.query.postgresql.utility.TreeStringSerializable;

import java.util.ArrayList;
import java.util.Set;

public class GetParents extends Instruction
{
    // Output graph
    private Graph targetGraph;
    // Input graph
    private Graph subjectGraph;
    // Set of starting child vertices
    private Graph startGraph;
    private String field;
    private String operation;
    private String value;

    public GetParents(Graph targetGraph, Graph subjectGraph, Graph startGraph, String field, String operation, String value)
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
        Set<AbstractVertex> targetVertexSet = targetGraph.vertexSet();
        Set<AbstractEdge> targetEdgeSet = targetGraph.edgeSet();
        for(AbstractVertex childVertex : startGraph.vertexSet())
        {
            for(AbstractEdge subjectEdge : subjectGraph.edgeSet())
            {
                if(subjectEdge.getChildVertex().equals(childVertex))
                {
                    AbstractVertex parentVertex = subjectEdge.getParentVertex();
                    boolean comparison = true;
                    if(field != null)
                    {
                        if(!field.equals("*"))
                        {
                            String parent_value = parentVertex.getAnnotation(field);
                            comparison = CommonFunctions.compareValues(parent_value, value, operation);
                        }
                    }
                    if(comparison)
                    {
                        targetVertexSet.add(parentVertex);
                        targetEdgeSet.add(subjectEdge);
                    }
                }
            }
        }
        ctx.addResponse(targetGraph);
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
