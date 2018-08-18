/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2015 SRI International

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
package spade.core;

import spade.query.scaffold.Scaffold;
import spade.query.scaffold.ScaffoldFactory;

import java.io.FileInputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;

import static spade.core.Kernel.CONFIG_PATH;
import static spade.core.Kernel.FILE_SEPARATOR;
import static spade.core.Kernel.SPADE_ROOT;


/**
 * This is the base class from which concrete storage types inherit.
 *
 * @author Dawood Tariq and Raza Ahmad
 */
public abstract class AbstractStorage
{
    public static final String PRIMARY_KEY = "hash";
    public static final String CHILD_VERTEX_KEY = "childVertexHash";
    public static final String PARENT_VERTEX_KEY = "parentVertexHash";
    public static final String DIRECTION = "direction";
    public static final String MAX_DEPTH = "maxDepth";
    public static final String MAX_LENGTH = "maxLength";
    public static final String DIRECTION_ANCESTORS = "ancestors";
    public static final String DIRECTION_DESCENDANTS = "descendants";
    public static final String DIRECTION_BOTH = "both";
    protected Logger logger;

    /**
     * The arguments with which this storage was initialized.
     */
    public String arguments;
    /**
     * The number of vertices that this storage instance has successfully
     * received.
     */
    protected long vertexCount;
    /**
     * The number of edges that this storage instance has successfully received.
     */
    protected long edgeCount;

    protected static Properties databaseConfigs = new Properties();

    /**
     * Variables and functions for computing performance stats
     */
    protected boolean reportingEnabled = false;
    protected long reportingInterval;
    protected long reportEveryMs;
    protected long startTime, lastReportedTime;
    protected long lastReportedVertexCount, lastReportedEdgeCount;

    private static String configFile = CONFIG_PATH + FILE_SEPARATOR + "spade.core.AbstractStorage.config";
    /**
     * Variables and functions for managing scaffold storage
     */
    public static Scaffold scaffold = null;
    public static boolean BUILD_SCAFFOLD;
    public static String SCAFFOLD_PATH;
    public static String SCAFFOLD_DATABASE_NAME;
    static
    {
        try
        {
            databaseConfigs.load(new FileInputStream(configFile));
            BUILD_SCAFFOLD = Boolean.parseBoolean(databaseConfigs.getProperty("build_scaffold"));
            SCAFFOLD_PATH = SPADE_ROOT + databaseConfigs.getProperty("scaffold_path");
            SCAFFOLD_DATABASE_NAME = databaseConfigs.getProperty("scaffold_database_name");
            if(BUILD_SCAFFOLD)
            {
                scaffold = ScaffoldFactory.createScaffold(SCAFFOLD_DATABASE_NAME);
                if(!scaffold.initialize(SCAFFOLD_PATH))
                {
                    Logger.getLogger(AbstractStorage.class.getName()).log(Level.WARNING, "Scaffold not set!");
                }
            }
        }
        catch(Exception ex)
        {
            // default settings
            BUILD_SCAFFOLD = false;
            SCAFFOLD_PATH = SPADE_ROOT + "db/scaffold";
            SCAFFOLD_DATABASE_NAME = "BerkeleyDB";
            Logger.getLogger(AbstractStorage.class.getName()).log(Level.WARNING,
            "Loading scaffold configurations from file '" + configFile + "' " +
                    " unsuccessful! Falling back to default settings", ex);
        }
    }

    protected boolean insertScaffoldEntry(AbstractEdge incomingEdge)
    {
        return scaffold.insertEntry(incomingEdge);
    }

    /* For testing purposes only. Set scaffold through Settings file normally. */
    public static void setScaffold(Scaffold scaffold)
    {
        AbstractStorage.scaffold = scaffold;
        BUILD_SCAFFOLD = true;
    }

    /**
     * This method is invoked by the kernel to initialize the storage.
     *
     * @param arguments The arguments with which this storage is to be
     * initialized.
     * @return True if the storage was initialized successfully.
     */
    public abstract boolean initialize(String arguments);

    /**
     * This method is invoked by the kernel to shut down the storage.
     *
     * @return True if the storage was shut down successfully.
     */
    public boolean shutdown()
    {
        if(BUILD_SCAFFOLD)
        {
            scaffold.shutdown();
        }

        return true;
    }

    protected void computeStats()
    {
        long currentTime = System.currentTimeMillis();
        if((currentTime - lastReportedTime) >= reportEveryMs)
        {
            printStats();
            lastReportedTime = currentTime;
            lastReportedVertexCount = vertexCount;
            lastReportedEdgeCount = edgeCount;
        }
    }

    protected void printStats()
    {
        long currentTime = System.currentTimeMillis();
        float overallTime = (float) (currentTime - startTime) / 1000; // # in secs
        float intervalTime = (float) (currentTime - lastReportedTime) / 1000; // # in secs
        if(overallTime > 0 && intervalTime > 0)
        {
            // # records/sec
            float overallVertexVolume = (float) vertexCount / overallTime;
            float overallEdgeVolume = (float) edgeCount / overallTime;
            // # records/sec

            long intervalVertexCount = vertexCount - lastReportedVertexCount;
            long intervalEdgeCount = edgeCount - lastReportedEdgeCount;
            float intervalVertexVolume = (float) (intervalVertexCount) / intervalTime;
            float intervalEdgeVolume = (float) (intervalEdgeCount) / intervalTime;
            logger.log(Level.INFO, "Overall Stats => rate: {0} vertex/sec and {1} edge/sec. count: vertices: {2} and edges: {3}. In total {4} seconds.\n" +
                            "Interval Stats => rate: {5} vertex/sec and {6} edge/sec. count: vertices: {7} and edges: {8}. In {9} seconds.",
                    new Object[]{overallVertexVolume, overallEdgeVolume, vertexCount, edgeCount, overallTime, intervalVertexVolume,
                            intervalEdgeVolume, intervalVertexCount, intervalEdgeCount, intervalTime});
        }
    }


    /**
     * This method returns current edge count.
     *
     * @return edge count
     */
    public long getEdgeCount(){
        return edgeCount;
    }

    /**
     * This method returns current vertex count.
     *
     * @return vertex count
     */
    public long getVertexCount(){
        return vertexCount;
    }

    /**
     * This method is triggered by the Kernel to flush transactions.
     *
     * @return True if the transactions were flushed successfully.
     */
    public boolean flushTransactions() {
        return true;
    }

    /**
     * This function queries the underlying storage and retrieves the edge
     * matching the given criteria.
     *
     * @param childVertexHash hash of the source vertex.
     * @param parentVertexHash hash of the destination vertex.
     * @return returns edge object matching the given vertices OR NULL.
     */
    @Deprecated
    public abstract AbstractEdge getEdge(String childVertexHash, String parentVertexHash);

    /**
     * This function queries the underlying storage and retrieves the vertex
     * matching the given criteria.
     *
     * @param vertexHash hash of the vertex to find.
     * @return returns vertex object matching the given hash OR NULL.
     */
    @Deprecated
    public abstract AbstractVertex getVertex(String vertexHash);


    /**
     * This function finds the children of a given vertex.
     * A child is defined as a vertex which is the source of a
     * direct edge between itself and the given vertex.
     *
     * @param parentHash hash of the given vertex
     * @return returns graph object containing children of the given vertex OR NULL.
     */
    public abstract Graph getChildren(String parentHash);

    /**
     * This function finds the parents of a given vertex.
     * A parent is defined as a vertex which is the destination of a
     * direct edge between itself and the given vertex.
     *
     * @param childVertexHash hash of the given vertex
     * @return returns graph object containing parents of the given vertex OR NULL.
     */
    public abstract Graph getParents(String childVertexHash);


    /**
     * This function inserts the given edge into the underlying storage(s) and
     * updates the cache(s) accordingly.
     * @param incomingEdge edge to insert into the storage
     * @return returns true if the insertion is successful. Insertion is considered
     * not successful if the edge is already present in the storage.
     */
    public abstract boolean putEdge(AbstractEdge incomingEdge);

    /**
     * This function inserts the given vertex into the underlying storage(s) and
     * updates the cache(s) accordingly.
     * @param incomingVertex vertex to insert into the storage
     * @return returns true if the insertion is successful. Insertion is considered
     * not successful if the vertex is already present in the storage.
     */
    public abstract boolean putVertex(AbstractVertex incomingVertex);

    /**
     * This function finds the lineage of the graph starting from a source vertex.
     *
     * @param hash hash of the source vertex
     * @param direction direction of traversal from the source vertex
     * @param maxDepth Maximum depth from source vertex to traverse
     *
     * @return returns the graph comprising of the subgraph starting from the source vertex
     * up till the specified depth OR NULL.
     */
    @Deprecated
    public Graph getLineage(String hash, String direction, int maxDepth)
    {
        Graph result = new Graph();
        AbstractVertex previousVertex = null;
        AbstractVertex currentVertex = null;
        int depth = 0;
        Queue<AbstractVertex> queue = new LinkedList<>();
        queue.add(getVertex(hash));
        while(!queue.isEmpty() && depth < maxDepth)
        {
            currentVertex = queue.remove();
            String currentHash = currentVertex.getAnnotation("hash");
            if(DIRECTION_ANCESTORS.startsWith(direction.toLowerCase()))
                queue.addAll(getParents(currentHash).vertexSet());
            else if(DIRECTION_DESCENDANTS.startsWith(direction.toLowerCase()))
                queue.addAll(getChildren(currentHash).vertexSet());

            result.putVertex(currentVertex);
            if(previousVertex != null)
                result.putEdge(getEdge(previousVertex.getAnnotation("hash"), currentHash));

            previousVertex = currentVertex;
            depth++;
        }

        return result;
    }


    /**
     * This function finds all possible paths between source and destination vertices.
     *
     * @param childVertexHash hash of the source vertex
     * @param parentVertexHash hash of the destination vertex
     * @param maxPathLength maximum length of any path to find
     *
     * @return returns graph containing all paths between the given source and destination vertex OR NULL.
     */
    @Deprecated
    public Graph getPaths(String childVertexHash, String parentVertexHash, int maxPathLength)
    {
        Set<Graph> allPaths = new HashSet<>();
        Stack<AbstractVertex>currentPath = new Stack<>();
        AbstractVertex previousVertex = null;
        AbstractVertex currentVertex = null;
        int pathLength = 0;
        Queue<AbstractVertex> queue = new LinkedList<>();
        queue.add(getVertex(childVertexHash));
        Graph children = null;
        while(!queue.isEmpty())
        {
            pathLength++;
            if(pathLength > maxPathLength)
                children = null;
            currentVertex = queue.remove();
            String currentHash = currentVertex.getAnnotation("hash");
            currentPath.push(currentVertex);
            if(currentHash.equals(parentVertexHash))
            {
                allPaths.add(convertStackToGraph(currentPath));
            }
            else
            {
                children = getChildren(currentHash);
            }

            if(children != null)
            {
                queue.addAll(children.vertexSet());
                continue;
            }

            currentPath.pop();
        }

        // merge graphs for each path
        Graph resultGraph = new Graph();
        for (Graph path: allPaths)
        {
            resultGraph = Graph.union(resultGraph, path);
        }


        return resultGraph;
    }


    /**
     * This helper function converts a stack of vertices into
     * a corresponding graph of vertices. It also finds and adds
     * the edges between those vertices.
     * @param stack stack of vertices to convert into a graph
     * @return returns a graph consisting only of vertices present in the stack
     */
    @Deprecated
    protected Graph convertStackToGraph(Stack<AbstractVertex> stack)
    {
        Graph graph = new Graph();
        Iterator<AbstractVertex> iter = stack.iterator();
        AbstractVertex previous = iter.next();
        graph.putVertex(previous);
        while(iter.hasNext())
        {
            AbstractVertex curr = iter.next();
            graph.putVertex(curr);
            graph.putEdge(getEdge(previous.getAnnotation("hash"), curr.getAnnotation("hash")));
            previous = curr;
        }


        return graph;
    }

    public abstract Object executeQuery(String query);
}
