/*
--------------------------------------------------------------------------------
SPADE - Support for Provenance Auditing in Distributed Environments.
Copyright (C) 2011 SRI International

This program is free software: you can redistribute it and/or
modify it under the terms of the GNU General Public License as
published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version.
testSet.iterator()
This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program. If not, see <http://www.gnu.org/licenses/>.
--------------------------------------------------------------------------------
 */
package spade.core;

import java.util.HashSet;
import java.util.Iterator;
import org.jgrapht.graph.DefaultDirectedGraph;
import spade.storage.Graphviz;

public class Lineage {

    private DefaultDirectedGraph<AbstractVertex, AbstractEdge> graph;

    public Lineage() {
        graph = new DefaultDirectedGraph<AbstractVertex, AbstractEdge>(AbstractEdge.class);
    }

    public boolean putVertex(AbstractVertex inputVertex) {
        return graph.addVertex(inputVertex);
    }

    public boolean putEdge(AbstractEdge inputEdge) {
        return graph.addEdge(inputEdge.getSrcVertex(), inputEdge.getDstVertex(), inputEdge);
    }

    public DefaultDirectedGraph<AbstractVertex, AbstractEdge> getGraph() {
        return graph;
    }

    public static Lineage intersection(Lineage inputLineage1, Lineage inputLineage2) {
        DefaultDirectedGraph<AbstractVertex, AbstractEdge> inputGraph1 = inputLineage1.getGraph();
        DefaultDirectedGraph<AbstractVertex, AbstractEdge> inputGraph2 = inputLineage2.getGraph();
        Lineage ResultGraph = new Lineage();
        HashSet vertices = new HashSet();
        HashSet edges = new HashSet();

        vertices.addAll(inputGraph1.vertexSet());
        vertices.retainAll(inputGraph2.vertexSet());
        edges.addAll(inputGraph1.edgeSet());
        edges.retainAll(inputGraph2.edgeSet());

        Iterator v = vertices.iterator();
        Iterator e = edges.iterator();
        while (v.hasNext()) {
            ResultGraph.putVertex((AbstractVertex) v.next());
        }
        while (e.hasNext()) {
            ResultGraph.putEdge((AbstractEdge) e.next());
        }

        return ResultGraph;
    }

    public static Lineage union(Lineage inputLineage1, Lineage inputLineage2) {
        DefaultDirectedGraph<AbstractVertex, AbstractEdge> inputGraph1 = inputLineage1.getGraph();
        DefaultDirectedGraph<AbstractVertex, AbstractEdge> inputGraph2 = inputLineage2.getGraph();
        Lineage resultGraph = new Lineage();
        HashSet vertices = new HashSet();
        HashSet edges = new HashSet();

        vertices.addAll(inputGraph1.vertexSet());
        vertices.addAll(inputGraph2.vertexSet());
        edges.addAll(inputGraph1.edgeSet());
        edges.addAll(inputGraph2.edgeSet());

        Iterator v = vertices.iterator();
        Iterator e = edges.iterator();
        while (v.hasNext()) {
            resultGraph.putVertex((AbstractVertex) v.next());
        }
        while (e.hasNext()) {
            resultGraph.putEdge((AbstractEdge) e.next());
        }

        return resultGraph;
    }

    public void exportDOT(String path) {
        try {
            Graphviz outputStorage = new Graphviz();
            outputStorage.initialize(path);
            Iterator vertexIterator = graph.vertexSet().iterator();
            Iterator edgeIterator = graph.edgeSet().iterator();
            while (vertexIterator.hasNext()) {
                outputStorage.putVertex((AbstractVertex) vertexIterator.next());
            }
            while (edgeIterator.hasNext()) {
                outputStorage.putEdge((AbstractEdge) edgeIterator.next());
            }
            outputStorage.shutdown();
        } catch (Exception exception) {
            exception.printStackTrace(System.err);
        }
    }
}
