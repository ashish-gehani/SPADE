package spade.query.graph.execution;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.query.graph.kernel.Environment;
import spade.query.graph.utility.TreeStringSerializable;

import java.util.ArrayList;
import java.util.Set;

public class GetEdgesFromEndpoints extends Instruction
{
    // Output graph.
    private Graph targetGraph;
    // Input graph containing edges.
    private Graph subjectGraph;
    // Set of child endpoint vertices
    private Graph childGraph;
    // Set of parent endpoint vertices.
    private Graph parentGraph;

    public GetEdgesFromEndpoints(Graph targetGraph, Graph subjectGraph, Graph childGraph, Graph parentGraph)
    {
        this.targetGraph = targetGraph;
        this.subjectGraph = subjectGraph;
        this.childGraph = childGraph;
        this.parentGraph = parentGraph;
    }

    @Override
    public void execute(Environment env, ExecutionContext ctx)
    {
        Set<AbstractEdge> targetEdgeSet = targetGraph.edgeSet();
        Set<AbstractVertex> childVertexSet = childGraph.vertexSet();
        Set<AbstractVertex> parentVertexSet = parentGraph.vertexSet();
        for(AbstractEdge edge : subjectGraph.edgeSet())
        {
            AbstractVertex childVertex = edge.getChildVertex();
            AbstractVertex parentVertex = edge.getParentVertex();
            if(childVertexSet.contains(childVertex) &&
                    parentVertexSet.contains(parentVertex))
            {
                targetEdgeSet.add(edge);
            }
        }
        ctx.addResponse(targetGraph);
    }

    @Override
    public String getLabel()
    {
        return "GetEdgesFromEndpoints";
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
        inline_field_names.add("subjectGraph");
        inline_field_values.add(subjectGraph.getName());
    }

}
