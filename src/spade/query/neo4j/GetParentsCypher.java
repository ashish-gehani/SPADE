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

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Result;
import spade.core.Graph;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static spade.storage.Neo4j.convertNodeToVertex;

/**
 * @author raza
 */
public class GetParentsCypher extends Neo4j<Graph, Map<String, List<String>>>
{
    @Override
    public Graph execute(Map<String, List<String>> parameters, Integer limit)
    {
        String vertexQuery = prepareGetVertexQuery(parameters, limit);
        Result result = (Result) currentStorage.executeQuery(vertexQuery);
        Iterator<Node> nodeSet = result.columnAs(VERTEX_ALIAS);
        Node node;
        if(nodeSet.hasNext())
        {
            // starting point can only be one vertex
            node = nodeSet.next();
        }
        else
            return null;
        Iterable<Relationship> relationships = node.getRelationships(Direction.OUTGOING);
        Graph parents = new Graph();
        for(Relationship relationship: relationships)
        {
            parents.putVertex(convertNodeToVertex(relationship.getEndNode()));
        }


        return parents;
    }
}
