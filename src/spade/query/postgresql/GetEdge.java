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

import org.apache.commons.collections.CollectionUtils;
import spade.core.AbstractEdge;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * @author raza
 */
public class GetEdge extends PostgreSQL<Set<AbstractEdge>, Map<String, List<String>>>
{
    public GetEdge()
    {
        register();
    }

    @Override
    public Set<AbstractEdge> execute(Map<String, List<String>> parameters, Integer limit)
    {
        Set<AbstractEdge> edgeSet = null;
        StringBuilder query = new StringBuilder(100);
        try
        {
            query.append("SELECT * FROM ");
            query.append(EDGE_TABLE);
            query.append(" WHERE ");
            for (Map.Entry<String, List<String>> entry : parameters.entrySet())
            {
                List<String> values = entry.getValue();
                String colName = entry.getKey();
                query.append("\"");
                query.append(colName);
                query.append("\"");
                query.append(values.get(COMPARISON_OPERATOR));
                query.append("'");
                query.append(values.get(COL_VALUE));
                query.append("'");
                query.append(" ");
                String boolOperator = values.get(BOOLEAN_OPERATOR);
                if (boolOperator != null)
                    query.append(boolOperator).append(" ");
            }
            if (limit != null)
                query.append(" LIMIT ").append(limit);
            query.append(";");

            Logger.getLogger(GetEdge.class.getName()).log(Level.INFO, "Following query: " + query.toString());
            edgeSet = prepareEdgeSetFromSQLResult(query.toString());
            if(!CollectionUtils.isEmpty(edgeSet))
                return edgeSet;
        }
        catch (Exception ex)
        {
            Logger.getLogger(GetEdge.class.getName()).log(Level.SEVERE, "Error creating edge set!", ex);
        }

        return edgeSet;
    }
}
