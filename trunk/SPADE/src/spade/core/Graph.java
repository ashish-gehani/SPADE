/*
--------------------------------------------------------------------------------
SPADE - Support for Provenance Auditing in Distributed Environments.
Copyright (C) 2011 SRI International

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

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class is used to represent query responses using sets for edges and vertices.
 * 
 * @author Dawood
 */
public class Graph implements Serializable {

    private Set<AbstractVertex> vertexSet;
    private Set<AbstractEdge> edgeSet;
    private Map<AbstractVertex, Integer> networkMap;
    /** For query results spanning multiple hosts, this is used to indicate whether
     * the network boundaries have been properly transformed.
     * 
     */
    public boolean transformed;

    /**
     * An empty constructor.
     */
    public Graph() {
        vertexSet = new HashSet<AbstractVertex>();
        edgeSet = new HashSet<AbstractEdge>();
        networkMap = new HashMap<AbstractVertex, Integer>();
        transformed = false;
    }

    /**
     * This method is used to put the network vertices in the network vertex map.
     * The network vertex map is used when doing remote querying.
     * 
     * @param inputVertex The network vertex
     * @param depth The depth of this vertex from the source vertex
     */
    public void putNetworkVertex(AbstractVertex inputVertex, int depth) {
        networkMap.put(inputVertex, depth);
    }

    /**
     * Add a vertex to the graph object. The vertex is sent to the transformers
     * before it is finally committed.
     * 
     * @param inputVertex The vertex to be added
     */
    public void putVertex(AbstractVertex inputVertex) {
        inputVertex.resultGraph = this;
        Kernel.sendToTransformers(inputVertex);
    }

    /**
     * Add an edge to the graph object. The edge is sent to the transformers
     * before it is finally committed.
     * 
     * @param inputEdge The edge to be added
     */
    public void putEdge(AbstractEdge inputEdge) {
        inputEdge.resultGraph = this;
        Kernel.sendToTransformers(inputEdge);
    }
    
    /**
     * Commit a vertex to this graph.
     * 
     * @param inputVertex The vertex to be committed
     */
    public void commitVertex(AbstractVertex inputVertex) {
        vertexSet.add(inputVertex);
    }

    /**
     * Commit an edge to this graph.
     * 
     * @param inputEdge The edge to be committed
     */
    public void commitEdge(AbstractEdge inputEdge) {
        edgeSet.add(inputEdge);
    }
    
    /**
     * Returns the set containing the vertices.
     * 
     * @return The set containing the vertices.
     */
    public Set<AbstractVertex> vertexSet() {
        return vertexSet;
    }

    /**
     * Returns the set containing the edges.
     * 
     * @return The set containing edges.
     */
    public Set<AbstractEdge> edgeSet() {
        return edgeSet;
    }

    /**
     * Returns the map of network vertices for this graph.
     * 
     * @return The map containing the network vertices and their depth relative to the source vertex.
     */
    public Map<AbstractVertex, Integer> networkMap() {
        return networkMap;
    }

    /**
     * This method is used to create a new graph as an intersection of the two
     * given input graphs. This is done simply by using set functions on the
     * vertex and edge sets.
     * 
     * @param graph1 Input graph 1 
     * @param graph2 Input graph 2
     * @return The result graph
     */
    public static Graph intersection(Graph graph1, Graph graph2) {
        Graph resultGraph = new Graph();
        Set<AbstractVertex> vertices = new HashSet<AbstractVertex>();
        Set<AbstractEdge> edges = new HashSet<AbstractEdge>();

        vertices.addAll(graph1.vertexSet());
        vertices.retainAll(graph2.vertexSet());
        edges.addAll(graph1.edgeSet());
        edges.retainAll(graph2.edgeSet());

        resultGraph.vertexSet().addAll(vertices);
        resultGraph.edgeSet().addAll(edges);

        return resultGraph;
    }

    /**
     * This method is used to create a new graph as a union of the two
     * given input graphs. This is done simply by using set functions on the
     * vertex and edge sets.
     * 
     * @param graph1 Input graph 1 
     * @param graph2 Input graph 2
     * @return The result graph
     */
    public static Graph union(Graph graph1, Graph graph2) {
        Graph resultGraph = new Graph();
        Set<AbstractVertex> vertices = new HashSet<AbstractVertex>();
        Set<AbstractEdge> edges = new HashSet<AbstractEdge>();

        vertices.addAll(graph1.vertexSet());
        vertices.addAll(graph2.vertexSet());
        edges.addAll(graph1.edgeSet());
        edges.addAll(graph2.edgeSet());

        resultGraph.vertexSet().addAll(vertices);
        resultGraph.edgeSet().addAll(edges);

        return resultGraph;
    }

    /**
     * This method is used to create a new graph obtained by removing all
     * elements of the second graph from the first graph given as inputs.
     * 
     * @param graph1 Input graph 1 
     * @param graph2 Input graph 2
     * @return The result graph
     */
    public static Graph remove(Graph graph1, Graph graph2) {
        Graph resultGraph = new Graph();
        Set<AbstractVertex> vertices = new HashSet<AbstractVertex>();
        Set<AbstractEdge> edges = new HashSet<AbstractEdge>();

        vertices.addAll(graph1.vertexSet());
        vertices.removeAll(graph2.vertexSet());
        edges.addAll(graph1.edgeSet());
        edges.removeAll(graph2.edgeSet());

        resultGraph.vertexSet().addAll(vertices);
        resultGraph.edgeSet().addAll(edges);

        return resultGraph;
    }

    /**
     * This method is used to export the graph to a DOT file which is useful for visualization.
     * 
     * @param path The path to export the file to.
     */
    public void exportDOT(String path) {
        try {
            spade.storage.Graphviz outputStorage = new spade.storage.Graphviz();
            outputStorage.initialize(path);
            for (AbstractVertex vertex : vertexSet) {
                outputStorage.putVertex(vertex);
            }
            for (AbstractEdge edge : edgeSet) {
                outputStorage.putEdge(edge);
            }
            outputStorage.shutdown();
        } catch (Exception exception) {
            Logger.getLogger(Graph.class.getName()).log(Level.SEVERE, null, exception);
        }
    }
}
