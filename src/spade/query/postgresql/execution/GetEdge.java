package spade.query.postgresql.execution;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Graph;
import spade.core.Vertex;
import spade.query.postgresql.kernel.Environment;
import spade.query.postgresql.utility.TreeStringSerializable;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static spade.core.AbstractQuery.currentStorage;
import static spade.core.AbstractStorage.PRIMARY_KEY;
import static spade.query.graph.execution.utility.Utility.EDGE_TABLE;
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
            StringBuilder sqlQuery = new StringBuilder(100);
            sqlQuery.append("SELECT * FROM " + EDGE_TABLE);
            if(field != null)
            {
                if(!field.equals("*"))
                {
                    // TODO: do something about wild card columns
                    sqlQuery.append(" WHERE " + formatString(field) + operation + formatString(value));
                }
            }
            logger.log(Level.INFO, "Following query: " + sqlQuery.toString());

            ResultSet result = (ResultSet) currentStorage.executeQuery(sqlQuery.toString());
            ResultSetMetaData metadata = result.getMetaData();
            int columnCount = metadata.getColumnCount();

            Map<Integer, String> columnLabels = new HashMap<>();
            for(int i = 1; i <= columnCount; i++)
            {
                columnLabels.put(i, metadata.getColumnName(i));
            }

            Set<AbstractEdge> edgeSet = targetGraph.edgeSet();
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
                        if(colName != null && !colName.equals(PRIMARY_KEY))
                        {
                            edge.addAnnotation(colName, value);
                        }
                    }
                }
                edgeSet.add(edge);
            }
            ctx.addResponse(targetGraph);
        }
        catch(Exception ex)
        {
            logger.log(Level.SEVERE, "Error creating edge set from the query: ", ex);
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
