/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2017 SRI International
 This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU General Public License as
 published by the Free Software Foundation, either version 3 of the
 License, or (at your option) any later version.
 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 General Public License for more details.
 You should have received a copy of the GNU General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
 --------------------------------------------------------------------------------
 */
package spade.query.postgresql;

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
        query.append("\"");
        query.append(PRIMARY_KEY);
        query.append("\"");
        query.append(" IN(");
        query.append("SELECT ");
        query.append("\"");
        query.append(PARENT_VERTEX_KEY);
        query.append("\"");
        query.append(" FROM ");
        query.append(EDGE_TABLE);
        query.append(" WHERE ");
        query.append("\"");
        query.append(CHILD_VERTEX_KEY);
        query.append("\"");
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
