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


import spade.core.AbstractEdge;
import spade.storage.PostgreSQL;

import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

import static spade.core.AbstractStorage.CHILD_VERTEX_KEY;
import static spade.core.AbstractStorage.PARENT_VERTEX_KEY;
import static spade.core.AbstractStorage.PRIMARY_KEY;


/**
 * @author raza
 */
public class PutEdge extends spade.query.postgresql.PostgreSQL<Boolean, AbstractEdge>
{

    @Override
    public Boolean execute(AbstractEdge edge, Integer limit)
    {
        if(edge == null)
            return false;

        StringBuilder query = new StringBuilder( 100);
        query.append("INSERT INTO ");
        query.append(EDGE_TABLE);
        query.append(" (");
        query.append(PRIMARY_KEY);  //hash is not part of annotations
        query.append(", ");

        int i = 0;
        for (Map.Entry<String, String> annotation: edge.getAnnotations().entrySet())
        {
            String key = annotation.getKey();
            // Sanitize column name to remove special characters
            String colName = spade.storage.PostgreSQL.sanitizeColumn(key);

            // Add column if does not already exist
            AddColumn addColumn = new AddColumn();
            Map<String, String> addColumnParameters = new HashMap<>();
            addColumnParameters.put(EDGE_TABLE, colName);
            addColumn.execute(addColumnParameters, null);

            query.append(colName);
            i++;
            if(i < edge.getAnnotations().size())
                query.append(", ");
        }

        Map<String, String> annotations = edge.getAnnotations();
        annotations.put(PRIMARY_KEY, edge.bigHashCode());
        annotations.put(CHILD_VERTEX_KEY, edge.getChildVertex().bigHashCode());
        annotations.put(PARENT_VERTEX_KEY, edge.getParentVertex().bigHashCode());
        query.append(") VALUES (");

        // Add the annotation values
        i = 0;
        for(String colValue: annotations.values())
        {
            query.append("'");
            query.append(PostgreSQL.sanitizeString(colValue));
            query.append("'");
            i++;
            if(i < annotations.size())
                query.append(", ");
        }
        query.append(")");
        ResultSet success = (ResultSet) currentStorage.executeQuery(query.toString());


        if (success != null)
            return true;

        return false;
    }
}
