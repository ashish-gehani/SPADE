package spade.query.graph.execution;

import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.query.graph.utility.CommonFunctions;
import spade.query.postgresql.execution.ExecutionContext;
import spade.query.postgresql.execution.Instruction;
import spade.query.postgresql.kernel.Environment;
import spade.query.postgresql.utility.TreeStringSerializable;

import java.util.ArrayList;
import java.util.Set;

public class GetVertex extends Instruction
{
    // Output graph.
    private Graph targetGraph;
    private Graph subjectGraph;
    private String field;
    private String operation;
    private String value;

    public GetVertex(Graph targetGraph, Graph subjectGraph, String field, String operation, String value)
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
        Set<AbstractVertex> vertexSet = targetGraph.vertexSet();
        if(field != null)
        {
            for(AbstractVertex subjectVertex : subjectGraph.vertexSet())
            {
                String subject_value = subjectVertex.getAnnotation(field);
                boolean comparison = CommonFunctions.compareValues(subject_value, value, operation);
                if(comparison)
                {
                    vertexSet.add(subjectVertex);
                }
            }
        }
        else
        {
            vertexSet.addAll(subjectGraph.vertexSet());
        }
        ctx.addResponse(targetGraph);
    }

    @Override
    public String getLabel()
    {
        return "GetVertex";
    }

    @Override
    protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values, ArrayList<String> non_container_child_field_names, ArrayList<TreeStringSerializable> non_container_child_fields, ArrayList<String> container_child_field_names, ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields)
    {

    }
}
