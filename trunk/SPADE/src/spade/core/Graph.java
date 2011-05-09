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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class Graph implements Serializable {

    // This class uses a set of vertices and a set of edges to represent the
    // graph.

    private Set<AbstractVertex> vertexSet;
    private Set<AbstractEdge> edgeSet;
    public String details;

    public Graph() {
        vertexSet = new HashSet<AbstractVertex>();
        edgeSet = new HashSet<AbstractEdge>();
        details = "";
    }

    public boolean putVertex(AbstractVertex inputVertex) {
        return vertexSet.add(inputVertex);
    }

    public boolean putEdge(AbstractEdge inputEdge) {
        return edgeSet.add(inputEdge);
    }

    public Set<AbstractVertex> vertexSet() {
        return vertexSet;
    }

    public Set<AbstractEdge> edgeSet() {
        return edgeSet;
    }

    // This method is used to create a new graph as an intersection of the two
    // given input graphs. This is done simply by using set functions on the
    // vertex and edge sets.
    public static Graph getIntersection(Graph graph1, Graph graph2) {
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

    // This method is used to create a new graph as a union of the two
    // given input graphs. This is done simply by using set functions on the
    // vertex and edge sets.
    public static Graph getUnion(Graph graph1, Graph graph2) {
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

    // This method is used to export the graph to a DOT file which is useful for
    // visualization.
    public void exportDOT(String path) {
        try {
            spade.storage.Graphviz outputStorage = new spade.storage.Graphviz();
            outputStorage.initialize(path);
            Iterator vertexIterator = vertexSet.iterator();
            while (vertexIterator.hasNext()) {
                outputStorage.putVertex((AbstractVertex) vertexIterator.next());
            }
            Iterator edgeIterator = edgeSet.iterator();
            while (edgeIterator.hasNext()) {
                outputStorage.putEdge((AbstractEdge) edgeIterator.next());
            }
            outputStorage.shutdown();
        } catch (Exception exception) {
            exception.printStackTrace(System.err);
        }
    }
}
