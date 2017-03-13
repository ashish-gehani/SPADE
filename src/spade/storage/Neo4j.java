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
package spade.storage;

import java.util.Date;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.TransactionTerminatedException;

import org.neo4j.graphdb.factory.GraphDatabaseBuilder;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

import org.neo4j.graphdb.schema.IndexDefinition;
import spade.core.AbstractStorage;
import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Vertex;
import spade.core.Cache;
import spade.core.Graph;


/**
 * Neo4j storage implementation.
 *
 * @author Dawood Tariq, Hasanat Kazmi and Raza Ahmad
 */
public class Neo4j extends AbstractStorage
{
    private static final String VERTEX_INDEX = "vertexIndex";
    private static final String EDGE_INDEX = "edgeIndex";

    private GraphDatabaseService graphDb;

    private enum RelationshipTypes implements RelationshipType {EDGE}

    private enum NodeTypes implements Label {VERTEX}

    private String neo4jDatabaseDirectoryPath = null;
    static final Logger logger = Logger.getLogger(Neo4j.class.getName());
    private final String NEO4J_CONFIG_FILE = "cfg/neo4j.properties";


    // Performance tuning note: Set this to higher value (e.g. 100000) to commit less often to db - This increases ingestion rate.
    // Downside: Any external (non atomic) quering to database won't report non-committed data.
    private final int GLOBAL_TX_SIZE = 100000;
    // Performance tuning note: This is time in sec that storage is flushed. Increase this to increase throughput / ingestion rate.
    // Downside: Any external (non atomic) quering to database won't report non-committed data.
    private final int MAX_WAIT_TIME_BEFORE_FLUSH = 15000; // ms
    private Transaction globalTx;
    private int globalTxCount = 0;
    private Date lastFlushTime;

    @Override
    public boolean initialize(String arguments)
    {
        try
        {
            neo4jDatabaseDirectoryPath = arguments;
            if (neo4jDatabaseDirectoryPath == null)
            {
                return false;
            }
            GraphDatabaseBuilder graphDbBuilder = new GraphDatabaseFactory().newEmbeddedDatabaseBuilder(neo4jDatabaseDirectoryPath);
            try
            {
                graphDbBuilder.loadPropertiesFromFile(NEO4J_CONFIG_FILE);
                logger.log(Level.INFO, "Neo4j configurations loaded from config file.");
            }
            catch (Exception exception)
            {
                //TODO: load something default here
                logger.log(Level.INFO, "Default Neo4j configurations loaded.");
            }

            graphDb = graphDbBuilder.newGraphDatabase();
//            IndexDefinition indexDefinition = graphDb.schema()
//                                                .indexFor(NodeTypes.VERTEX)
//                                                .on(PRIMARY_KEY)
//                                                .create();

        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
            return false;
        }

        return true;
    }

    @Override
    public boolean flushTransactions() {
        if (Calendar.getInstance().getTime().getTime() - lastFlushTime.getTime() > MAX_WAIT_TIME_BEFORE_FLUSH) {
            globalTxCheckin(true);
            lastFlushTime = Calendar.getInstance().getTime();
        }
        return true;
    }

    @Override
    public boolean shutdown()
    {
        // Flush all transactions before shutting down the database
        // make sure buffers are done, and stop and join all threads
        globalTxFinalize();
        graphDb.shutdown(); // look at register shutdownhook in http://neo4j.com/docs/stable/tutorials-java-embedded-setup.html

        return true;
    }

    void globalTxCheckin()
    {
        globalTxCheckin(false);
    }

    void globalTxCheckin(boolean forcedFlush)
    {
        if ((globalTxCount % GLOBAL_TX_SIZE == 0) || (forcedFlush == true))
        {
            globalTxFinalize();
            globalTx = graphDb.beginTx();
        }
        globalTxCount++;
    }

    void globalTxFinalize()
    {
        if (globalTx != null)
        {
            try
            {
                globalTx.success();
            }
            finally
            {
                globalTx.close();
            }
        }
        globalTxCount = 0;
    }

    /**
     * This function inserts the given vertex into the underlying storage(s) and
     * updates the cache(s) accordingly.
     *
     * @param incomingVertex vertex to insert into the storage
     * @return returns true if the insertion is successful. Insertion is considered
     * not successful if the vertex is already present in the storage.
     */
    @Override
    public boolean putVertex(AbstractVertex incomingVertex)
    {
        String hashCode = incomingVertex.bigHashCode();
        if (Cache.isPresent(hashCode))
            return true;

        globalTxCheckin();
        Node newVertex = graphDb.createNode(NodeTypes.VERTEX);
        newVertex.setProperty(PRIMARY_KEY, hashCode);
        for (Map.Entry<String, String> currentEntry : incomingVertex.getAnnotations().entrySet())
        {
            String key = currentEntry.getKey();
            String value = currentEntry.getValue();
            newVertex.setProperty(key, value);
        }

        return true;
    }

    /**
     * This function inserts the given edge into the underlying storage(s) and
     * updates the cache(s) accordingly.
     *
     * @param incomingEdge edge to insert into the storage
     * @return returns true if the insertion is successful. Insertion is considered
     * not successful if the edge is already present in the storage.
     */
    @Override
    public boolean putEdge(AbstractEdge incomingEdge)
    {
        String hashCode = incomingEdge.bigHashCode();
        if (Cache.isPresent(hashCode))
            return true;

        globalTxCheckin();
        Node srcNode = graphDb.findNode(NodeTypes.VERTEX, PRIMARY_KEY,
                                        incomingEdge.getSourceVertex().bigHashCode());
        Node dstNode = graphDb.findNode(NodeTypes.VERTEX, PRIMARY_KEY,
                                        incomingEdge.getDestinationVertex().bigHashCode());
        Relationship newEdge = srcNode.createRelationshipTo(dstNode, RelationshipTypes.EDGE);
        newEdge.setProperty(PRIMARY_KEY, hashCode);
        for (Map.Entry<String, String> currentEntry : incomingEdge.getAnnotations().entrySet())
        {
            String key = currentEntry.getKey();
            String value = currentEntry.getValue();
            newEdge.setProperty(key, value);
        }

        return true;
    }

    /**
     * This function queries the underlying storage and retrieves the edge
     * matching the given criteria.
     *
     * @param sourceVertexHash hash of the source vertex.
     * @param destinationVertexHash hash of the destination vertex.
     * @return returns edge object matching the given vertices OR NULL.
     */
    @Override
    public AbstractEdge getEdge(String sourceVertexHash, String destinationVertexHash)
    {
        AbstractEdge edge = null;
        try (Transaction tx = graphDb.beginTx())
        {
            Node srcNode = graphDb.findNode(NodeTypes.VERTEX, PRIMARY_KEY, sourceVertexHash);
            Node dstNode = graphDb.findNode(NodeTypes.VERTEX, PRIMARY_KEY, destinationVertexHash);
            Iterable<Relationship> srcNodeRelationships = srcNode.getRelationships(Direction.OUTGOING);
            for(Relationship srcNodeRelationship : srcNodeRelationships)
            {
                if(srcNodeRelationship.getEndNode().equals(dstNode))
                {
                    edge = convertRelationshipToEdge(srcNodeRelationship);
                    break;
                }
            }
            tx.success();
        }
        catch (TransactionTerminatedException ex)
        {
            Logger.getLogger(Neo4j.class.getName()).log(Level.SEVERE, "Transaction terminated.", ex);
        }

        return edge;
    }

    /**
     * This function queries the underlying storage and retrieves the vertex
     * matching the given criteria.
     *
     * @param hash hash of the vertex to find.
     * @return returns vertex object matching the given hash OR NULL.
     */
    @Override
    public AbstractVertex getVertex(String hash)
    {
        AbstractVertex vertex = null;
        try (Transaction tx = graphDb.beginTx())
        {
            Node node = graphDb.findNode(NodeTypes.VERTEX, PRIMARY_KEY, hash);
            vertex = convertNodeToVertex(node);
            tx.success();
        }
        catch (TransactionTerminatedException ex)
        {
            Logger.getLogger(Neo4j.class.getName()).log(Level.SEVERE, "Transaction terminated.", ex);
        }

        return vertex;
    }

    private Set<AbstractEdge> prepareEdgeSetFromNeo4jResult(Result result, String sourceVertexHash, String destinationVertexHash)
    {
        Set<AbstractEdge> edgeSet = new HashSet<>();
        while(result.hasNext())
        {
            AbstractVertex sourceVertex = getVertex(sourceVertexHash);
            AbstractVertex destinationVertex = getVertex(destinationVertexHash);
            AbstractEdge edge = new Edge(sourceVertex, destinationVertex);
            Map<String,Object> row = result.next();
            for (String key : result.columns())
            {
                edge.addAnnotation(key, row.get(key).toString());
            }
            edgeSet.add(edge);
        }

        return edgeSet;
    }

    private Set<AbstractVertex> prepareVertexSetFromNeo4jResult(Result result)
    {
        Set<AbstractVertex> vertexSet = new HashSet<>();
        while(result.hasNext())
        {
            AbstractVertex vertex = new Vertex();
            Map<String,Object> row = result.next();
            for (String key : result.columns())
            {
                vertex.addAnnotation(key, row.get(key).toString());
            }
            vertexSet.add(vertex);
        }

        return vertexSet;
    }

    /**
     * This function finds the children of a given vertex.
     * A child is defined as a vertex which is the source of a
     * direct edge between itself and the given vertex.
     *
     * @param parentHash hash of the given vertex
     * @return returns graph object containing children of the given vertex OR NULL.
     */
    @Override
    public Graph getChildren(String parentHash)
    {
        Graph children = null;
        try (Transaction tx = graphDb.beginTx())
        {
            children = new Graph();
            Node parentNode = graphDb.findNode(NodeTypes.VERTEX, PRIMARY_KEY, parentHash);
            for(Relationship relationship: parentNode.getRelationships(Direction.OUTGOING))
            {
                children.putVertex(convertNodeToVertex(relationship.getEndNode()));
            }
            tx.success();
        }

        return children;
    }

    /**
     * This function finds the parents of a given vertex.
     * A parent is defined as a vertex which is the destination of a
     * direct edge between itself and the given vertex.
     *
     * @param childHash hash of the given vertex
     * @return returns graph object containing parents of the given vertex OR NULL.
     */
    @Override
    public Graph getParents(String childHash)
    {
        Graph parents = null;
        try (Transaction tx = graphDb.beginTx())
        {
            parents = new Graph();
            Node childNode = graphDb.findNode(NodeTypes.VERTEX, PRIMARY_KEY, childHash);
            for(Relationship relationship: childNode.getRelationships(Direction.INCOMING))
            {
                parents.putVertex(convertNodeToVertex(relationship.getStartNode()));
            }
            tx.success();
        }

        return parents;
    }

    private AbstractVertex convertNodeToVertex(Node node)
    {
        AbstractVertex resultVertex = new Vertex();
        for (String key : node.getPropertyKeys())
        {
            if(!key.equalsIgnoreCase(PRIMARY_KEY))
                resultVertex.addAnnotation(key, (String) node.getProperty(key));
        }


        return resultVertex;
    }

    private AbstractEdge convertRelationshipToEdge(Relationship relationship)
    {
        AbstractEdge resultEdge = new Edge(convertNodeToVertex(relationship.getStartNode()),
                convertNodeToVertex(relationship.getEndNode()));
        for (String key : relationship.getPropertyKeys())
        {
            if(!key.equalsIgnoreCase(PRIMARY_KEY))
                resultEdge.addAnnotation(key, (String) relationship.getProperty(key));
        }


        return resultEdge;
    }
}
