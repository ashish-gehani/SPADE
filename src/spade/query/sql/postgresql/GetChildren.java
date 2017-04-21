package spade.query.sql.postgresql;

import spade.core.Graph;

import java.util.Map;
import java.util.List;

import static spade.core.AbstractStorage.CHILD_VERTEX_KEY;
import static spade.core.AbstractStorage.PRIMARY_KEY;
import static spade.core.AbstractStorage.PARENT_VERTEX_KEY;

/**
 * @author raza
 */
public class GetChildren extends PostgreSQL<Graph, Map<String, List<String>>>
{
    @Override
    public Graph execute(Map<String, List<String>> parameters, Integer limit)
    {
        StringBuilder query = new StringBuilder(100);

        query.append("SELECT * FROM ");
        query.append(VERTEX_TABLE);
        query.append(" WHERE ");
        query.append(PRIMARY_KEY);
        query.append(" IN(");
        query.append("SELECT ");
        query.append(CHILD_VERTEX_KEY);
        query.append(" FROM ");
        query.append(EDGE_TABLE);
        query.append(" WHERE ");
        query.append(PARENT_VERTEX_KEY);
        query.append(" = ");
        query.append(parameters.get(PRIMARY_KEY));
        query.append(")");
        if(limit != null)
            query.append(" LIMIT ").append(limit);

        Graph children = new Graph();
        children.vertexSet().addAll(prepareVertexSetFromSQLResult(query.toString()));


        return children;
    }
}
