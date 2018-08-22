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

import org.apache.commons.collections.CollectionUtils;
import spade.core.AbstractEdge;
import spade.core.Graph;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static spade.storage.Neo4j.RelationshipTypes;

/**
 * @author raza
 */
public class GetEdge extends Neo4j<Set<AbstractEdge>, Map<String, List<String>>>
{
    private static final Logger logger = Logger.getLogger(GetEdge.class.getName());

    @Override
    public Set<AbstractEdge> execute(Map<String, List<String>> parameters, Integer limit)
    {
        Set<AbstractEdge> edgeSet = null;
        try
        {
            StringBuilder edgeQueryBuilder = new StringBuilder(50);
            for (Map.Entry<String, List<String>> entry : parameters.entrySet())
            {
                String colName = entry.getKey();
                List<String> values = entry.getValue();
                edgeQueryBuilder.append(colName);
                edgeQueryBuilder.append(":");
                edgeQueryBuilder.append(values.get(COL_VALUE));
                String boolOperator = values.get(BOOLEAN_OPERATOR);
                if (boolOperator != null)
                    edgeQueryBuilder.append(boolOperator).append(" ");
            }
            spade.storage.Neo4j neo4jStorage = (spade.storage.Neo4j) currentStorage;
            Graph result = neo4jStorage.getEdges(null, null, edgeQueryBuilder.toString());
            edgeSet = result.edgeSet();
            if (!CollectionUtils.isEmpty(edgeSet))
                return edgeSet;
        }
        catch (Exception ex)
        {
            logger.log(Level.SEVERE, "Error creating edge set!", ex);
        }

        return edgeSet;
    }
}
