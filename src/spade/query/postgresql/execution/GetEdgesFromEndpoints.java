package spade.query.postgresql.execution;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.query.graph.execution.ExecutionContext;
import spade.query.graph.execution.Instruction;
import spade.query.graph.kernel.Environment;
import spade.query.graph.utility.CommonFunctions;
import spade.query.graph.utility.TreeStringSerializable;

import java.util.ArrayList;
import java.util.Set;

import static spade.query.graph.utility.CommonVariables.CHILD_VERTEX_KEY;
import static spade.query.graph.utility.CommonVariables.EDGE_TABLE;
import static spade.query.graph.utility.CommonVariables.PARENT_VERTEX_KEY;

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
        StringBuilder childHashes = new StringBuilder(200);
        StringBuilder parentHashes = new StringBuilder(200);
        for(AbstractVertex childVertex : childGraph.vertexSet())
        {
            childHashes.append("'");
            childHashes.append(childVertex.bigHashCode());
            childHashes.append("', ");
        }
        for(AbstractVertex parentVertex : parentGraph.vertexSet())
        {
            parentHashes.append("'");
            parentHashes.append(parentVertex.bigHashCode());
            parentHashes.append("', ");
        }

        StringBuilder sqlQuery = new StringBuilder(500);
        sqlQuery.append("SELECT * FROM ");
        sqlQuery.append(EDGE_TABLE);
        sqlQuery.append(" WHERE \"");
        sqlQuery.append(CHILD_VERTEX_KEY);
        sqlQuery.append("\" IN (");
        sqlQuery.append(childHashes.substring(0, childHashes.length() - 2));
        sqlQuery.append(") AND \"");
        sqlQuery.append(PARENT_VERTEX_KEY);
        sqlQuery.append("\" IN (");
        sqlQuery.append(parentHashes.substring(0, parentHashes.length() - 2));
        sqlQuery.append(");");

        Set<AbstractEdge> edgeSet = targetGraph.edgeSet();
        CommonFunctions.executeGetEdge(edgeSet, sqlQuery.toString());
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
