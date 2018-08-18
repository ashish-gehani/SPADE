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

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Result;
import spade.core.AbstractEdge;
import spade.core.AbstractQuery;
import spade.core.AbstractVertex;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static spade.storage.Neo4j.NodeTypes;
import static spade.storage.Neo4j.convertNodeToVertex;
import static spade.storage.Neo4j.convertRelationshipToEdge;
/**
 * @author raza
 */
public abstract class Neo4j<R, P> extends AbstractQuery<R, P>
{
    // vertex alias to use while querying
    protected static final String VERTEX_ALIAS = "v";
    // edge alias to use while querying
    protected static final String EDGE_ALIAS = "e";

    protected String prepareGetVertexQuery(Map<String, List<String>> parameters, Integer limit)
    {
        StringBuilder query = new StringBuilder(100);
        query.append("MATCH (").append(VERTEX_ALIAS).append(":").append(NodeTypes.VERTEX).append(")");
        query.append(" WHERE ");
        for (Map.Entry<String, List<String>> entry : parameters.entrySet())
        {
            String colName = entry.getKey();
            List<String> values = entry.getValue();
            query.append(VERTEX_ALIAS).append(".");
            query.append(colName);
            query.append(values.get(COMPARISON_OPERATOR));
            query.append("'");
            query.append(values.get(COL_VALUE));
            query.append("'");
            query.append(" ");
            String boolOperator = values.get(BOOLEAN_OPERATOR);
            if (boolOperator != null)
                query.append(boolOperator);
        }
        if (limit != null)
            query.append(" LIMIT ").append(limit);
        query.append("RETURN ").append(VERTEX_ALIAS).append(")");


        return query.toString();
    }

    protected Set<AbstractVertex> prepareVertexSetFromNeo4jResult(String query)
    {
        Set<AbstractVertex> vertexSet = new HashSet<>();
        try
        {
            Result result = (Result) currentStorage.executeQuery(query);
            Iterator<Node> nodeSet = result.columnAs(VERTEX_ALIAS);
            AbstractVertex vertex;
            while(nodeSet.hasNext())
            {
                vertex = convertNodeToVertex(nodeSet.next());
                if(!vertex.isEmpty())
                    vertexSet.add(vertex);
            }
        }
        catch (Exception ex)
        {
            Logger.getLogger(Neo4j.class.getName()).log(Level.SEVERE, "Vertex set querying unsuccessful!", ex);
        }

        return vertexSet;
    }

    protected Set<AbstractEdge> prepareEdgeSetFromNeo4jResult(String query)
    {
        Set<AbstractEdge> edgeSet = new HashSet<>();
        try
        {
            Result result = (Result) currentStorage.executeQuery(query);
            Iterator<Relationship> relationshipSet = result.columnAs(EDGE_ALIAS);
            AbstractEdge edge;
            while(relationshipSet.hasNext())
            {
                edge = convertRelationshipToEdge(relationshipSet.next());
                if(!edge.isEmpty())
                    edgeSet.add(edge);
            }
        }
        catch (Exception ex)
        {
            Logger.getLogger(Neo4j.class.getName()).log(Level.SEVERE, "Edge set querying unsuccessful!", ex);
        }

        return edgeSet;
    }
}
