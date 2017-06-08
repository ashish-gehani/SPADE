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

import java.sql.ResultSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Queue;
import java.util.Stack;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Iterator;

/**
 * This is the base class from which concrete storage types inherit.
 *
 * @author Dawood Tariq and Raza Ahmad
 */
public abstract class AbstractStorage
{
    public static final String PRIMARY_KEY = "hash";
    public static final String CHILD_VERTEX_KEY = "childVertexHash";
    public static final String PARENT_VERTEX_KEY = "parentHash";
    public static final String DIRECTION_ANCESTORS = Settings.getProperty("direction_ancestors");
    public static final String DIRECTION_DESCENDANTS = Settings.getProperty("direction_descendants");
    protected static Map<Long, String> m = new HashMap<>();

    /**
     * The arguments with which this storage was initialized.
     */
    public String arguments;
    /**
     * The number of vertices that this storage instance has successfully
     * received.
     */
    long vertexCount;
    /**
     * The number of edges that this storage instance has successfully received.
     */
    long edgeCount;

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
    public abstract boolean shutdown();

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
    public abstract AbstractEdge getEdge(String childVertexHash, String parentVertexHash);

    /**
     * This function queries the underlying storage and retrieves the vertex
     * matching the given criteria.
     *
     * @param vertexHash hash of the vertex to find.
     * @return returns vertex object matching the given hash OR NULL.
     */
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
    public Graph getLineage(String hash, String direction, int maxDepth)
    {
        Graph result = new Graph();
        AbstractVertex previousVertex = null;
        AbstractVertex currentVertex = null;
        int depth = 0;
        Queue<AbstractVertex> queue = new LinkedList<>();
        queue.add(getVertex(hash));
        //TODO: keep a visited array
        while(!queue.isEmpty() && depth < maxDepth)
        {
            currentVertex = queue.remove();
            String currentHash = currentVertex.getAnnotation("hash");
            if(DIRECTION_ANCESTORS.startsWith(direction.toLowerCase()))
                queue.addAll(getParents(currentHash).vertexSet());
            else if(DIRECTION_DESCENDANTS.startsWith(direction.toLowerCase()))
                queue.addAll(getChildren(currentHash).vertexSet());

            result.putVertex(currentVertex);
            //TODO: make provision for returning multiple edges between same pair of vertices
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
            //TODO: make provision for returning multiple edges between same pair of vertices
            graph.putEdge(getEdge(previous.getAnnotation("hash"), curr.getAnnotation("hash")));
            previous = curr;
        }


        return graph;
    }

    public abstract Object executeQuery(String query);
}
