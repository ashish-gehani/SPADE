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
package spade.query.neo4j;

import spade.core.Graph;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static spade.core.AbstractStorage.PARENT_VERTEX_KEY;
import static spade.core.AbstractStorage.PRIMARY_KEY;

/**
 * @author raza
 */
public class GetChildren extends Neo4j<Graph, Map<String, List<String>>>
{
    private static final Logger logger = Logger.getLogger(GetChildren.class.getName());
    @Override
    public Graph execute(Map<String, List<String>> parameters, Integer limit)
    {
        try
        {
            String query = PRIMARY_KEY;
            List<String> values = parameters.get(PARENT_VERTEX_KEY);
            query += ":" ;
            query += values.get(COL_VALUE);

            spade.storage.Neo4j neo4jStorage = (spade.storage.Neo4j) currentStorage;
            Graph children = neo4jStorage.getChildren(query);
            return children;
        }
        catch (Exception ex)
        {
            logger.log(Level.SEVERE, "Error retrieving children!", ex);
            return null;
        }
    }
}
