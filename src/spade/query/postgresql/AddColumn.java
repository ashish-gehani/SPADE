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

import spade.storage.PostgreSQL;

import java.sql.ResultSet;
import java.util.Map;
/**
 * @author raza
 */
public class AddColumn extends spade.query.postgresql.PostgreSQL<Boolean, Map<String, String>>
{

    @Override
    public Boolean execute(Map<String, String> parameters, Integer limit)
    {
        Map.Entry<String, String> entry = parameters.entrySet().iterator().next();
        String tableName = entry.getKey();
        String colName = entry.getValue();
        PostgreSQL postgresqlStorage = (PostgreSQL) currentStorage;
        // Check if this column already exists for this table
        if ((tableName.equalsIgnoreCase(VERTEX_TABLE)) && postgresqlStorage.vertexAnnotationIsPresent(colName))
            return true;
        else if ((tableName.equalsIgnoreCase(EDGE_TABLE)) && postgresqlStorage.edgeAnnotationIsPresent(colName))
            return true;

        String query = "ALTER TABLE " +
                        tableName +
                        " ADD COLUMN " +
                        colName +
                        " VARCHAR(256);";
        ResultSet success = postgresqlStorage.executeQuery(query);
        if(success == null)
            return false;

        if (tableName.equalsIgnoreCase(VERTEX_TABLE))
        {
            postgresqlStorage.addVertexAnnotation(colName);
        }
        else if (tableName.equalsIgnoreCase(EDGE_TABLE))
        {
            postgresqlStorage.addEdgeAnnotation(colName);
        }

        return true;
    }
}
