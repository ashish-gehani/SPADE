package spade.query.sql.postgresql;

import spade.core.Graph;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static spade.core.AbstractStorage.CHILD_VERTEX_KEY;
import static spade.core.AbstractStorage.PARENT_VERTEX_KEY;
import static spade.core.AbstractStorage.PRIMARY_KEY;

/**
 * @author raza
 */
public class GetParents extends PostgreSQL<Graph, Map<String, List<String>>>
{
    public GetParents()
    {
        register();
    }

    @Override
    public Graph execute(Map<String, List<String>> parameters, Integer limit)
    {
        //TODO: add support for more selections
        // implicit assumption that parameters contain annotation CHILD_VERTEX_KEY
        StringBuilder query = new StringBuilder(100);

        query.append("SELECT * FROM ");
        query.append(VERTEX_TABLE);
        query.append(" WHERE ");
        query.append(PRIMARY_KEY);
        query.append(" IN(");
        query.append("SELECT ");
        query.append(PARENT_VERTEX_KEY);
        query.append(" FROM ");
        query.append(EDGE_TABLE);
        query.append(" WHERE ");
        query.append(CHILD_VERTEX_KEY);
        query.append(" = ");
        query.append("'");
        List<String> entry = parameters.get(CHILD_VERTEX_KEY);
        if(entry != null)
            query.append(entry.get(COL_VALUE));
        else
            return null;
        query.append("'");
        query.append(")");
        if(limit != null)
            query.append(" LIMIT ").append(limit);
        query.append(";");

        Logger.getLogger(GetParents.class.getName()).log(Level.INFO, "Following query: " + query.toString());
        Graph parents = new Graph();
        parents.vertexSet().addAll(prepareVertexSetFromSQLResult(query.toString()));


        return parents;
    }
}
