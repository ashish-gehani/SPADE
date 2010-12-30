/*
--------------------------------------------------------------------------------
SPADE - Support for Provenance Auditing in Distributed Environments.
Copyright (C) 2010 SRI International

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

import java.util.Iterator;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.jgrapht.traverse.DepthFirstIterator;

public class Lineage implements Comparable {

    private DefaultDirectedGraph<Vertex, Edge> graph;
    private Vertex root;
    private int depth;
    private String pruneExpression;

    public Lineage(Vertex inputSource, int inputDepth) {
        root = inputSource;
        graph = new DefaultDirectedGraph<Vertex, Edge>(Edge.class);
        depth = inputDepth;
    }

    public Lineage(Vertex inputSource, String prune) {
        root = inputSource;
        graph = new DefaultDirectedGraph<Vertex, Edge>(Edge.class);
        pruneExpression = prune;
    }

    public Lineage(DefaultDirectedGraph<Vertex, Edge> graphLineage, Vertex inputSource, int inputDepth) {
        root = inputSource;
        graph = graphLineage;
        depth = inputDepth;
    }

    public Lineage(DefaultDirectedGraph<Vertex, Edge> graphLineage, Vertex inputSource, String prune) {
        root = inputSource;
        graph = graphLineage;
        pruneExpression = prune;
    }

    public Iterator<Vertex> BreadthFirstIterator() {
        return new BreadthFirstIterator(graph);
    }

    public Iterator<Vertex> DepthFirstIterator() {
        return new DepthFirstIterator(graph);
    }

    public boolean putVertex(Vertex inputVertex) {
        if (inputVertex.getVertexType().equals("Process")) {
            return graph.addVertex((Process) inputVertex);
        } else if (inputVertex.getVertexType().equals("Artifact")) {
            return graph.addVertex((Artifact) inputVertex);
        } else if (inputVertex.getVertexType().equals("Agent")) {
            return graph.addVertex((Agent) inputVertex);
        } else {
            return false;
        }
    }

    public boolean putEdge(Edge inputEdge) {
        String edgeType = inputEdge.getEdgeType();
        if (edgeType.equals("Used")) {
            return graph.addEdge(((Used) inputEdge).getProcess(), ((Used) inputEdge).getArtifact(), ((Used) inputEdge));
        } else if (edgeType.equals("WasControlledBy")) {
            return graph.addEdge(((WasControlledBy) inputEdge).getProcess(), ((WasControlledBy) inputEdge).getAgent(), ((WasControlledBy) inputEdge));
        } else if (edgeType.equals("WasDerivedFrom")) {
            return graph.addEdge(((WasDerivedFrom) inputEdge).getArtifact1(), ((WasDerivedFrom) inputEdge).getArtifact2(), ((WasDerivedFrom) inputEdge));
        } else if (edgeType.equals("WasGeneratedBy")) {
            return graph.addEdge(((WasGeneratedBy) inputEdge).getArtifact(), ((WasGeneratedBy) inputEdge).getProcess(), ((WasGeneratedBy) inputEdge));
        } else if (edgeType.equals("WasTriggeredBy")) {
            return graph.addEdge(((WasTriggeredBy) inputEdge).getProcess1(), ((WasTriggeredBy) inputEdge).getProcess2(), ((WasTriggeredBy) inputEdge));
        } else {
            return false;
        }
    }

    public int getMaxDepth() {
        return depth;
    }

    public String getPruneExpression() {
        return pruneExpression;
    }

    public int compareTo(Object inputLineage) {
        return 0;
    }

    public DefaultDirectedGraph<Vertex, Edge> getGraph() {
        return graph;
    }
}
