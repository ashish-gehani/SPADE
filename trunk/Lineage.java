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

import java.io.*;
import java.util.*;
import org.jgrapht.Graph;
import org.jgrapht.ext.DOTExporter;
import org.jgrapht.ext.EdgeNameProvider;
import org.jgrapht.ext.VertexNameProvider;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.jgrapht.traverse.DepthFirstIterator;

public class Lineage implements Comparable {

    private DefaultDirectedGraph<Vertex, Edge> graph;
    private Vertex root;
    private int depth;
    private String pruneExpression;

    public Lineage() {
        graph = new DefaultDirectedGraph<Vertex, Edge>(Edge.class);
    }

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
        return graph.addVertex(inputVertex);
    }

    public boolean putEdge(Edge inputEdge) {
        return graph.addEdge(inputEdge.getSrcVertex(), inputEdge.getDstVertex(), inputEdge);
    }

    public int getMaxDepth() {
        return depth;
    }

    public String getPruneExpression() {
        return pruneExpression;
    }

    public DefaultDirectedGraph<Vertex, Edge> getGraph() {
        return graph;
    }

    public static Lineage intersection(DefaultDirectedGraph<Vertex, Edge> g1, DefaultDirectedGraph<Vertex, Edge> g2) {
        Lineage output = new Lineage();
        HashSet vertices = new HashSet();
        HashSet edges = new HashSet();

        vertices.addAll(g1.vertexSet());
        vertices.retainAll(g2.vertexSet());
        edges.addAll(g1.edgeSet());
        edges.retainAll(g2.edgeSet());

        Iterator v = vertices.iterator();
        Iterator e = edges.iterator();
        while (v.hasNext()) {
            output.putVertex((Vertex) v.next());
        }
        while (e.hasNext()) {
            output.putEdge((Edge) e.next());
        }

        return output;
    }

    public static Lineage union(DefaultDirectedGraph<Vertex, Edge> g1, DefaultDirectedGraph<Vertex, Edge> g2) {
        Lineage output = new Lineage();
        HashSet vertices = new HashSet();
        HashSet edges = new HashSet();

        vertices.addAll(g1.vertexSet());
        vertices.addAll(g2.vertexSet());
        edges.addAll(g1.edgeSet());
        edges.addAll(g2.edgeSet());

        Iterator v = vertices.iterator();
        Iterator e = edges.iterator();
        while (v.hasNext()) {
            output.putVertex((Vertex) v.next());
        }
        while (e.hasNext()) {
            output.putEdge((Edge) e.next());
        }

        return output;
    }

    public static void exportDOT(Graph<Vertex, Edge> g, String path) {
        try {
            FileWriter out = new FileWriter("temp_out.tmp", false);
            DOTExporter d = new DOTExporter(new IDProvider(), new LabelProvider(), new EdgeProvider());
            d.export(out, g);
            out.close();
            FileWriter out2 = new FileWriter(path, false);
            BufferedReader in = new BufferedReader(new FileReader("temp_out.tmp"));
            String inString = "";
            out2.write(in.readLine() + "\n");
            out2.write("graph [rankdir = \"RL\"];\nnode [fontname=\"Helvetica\" fontsize=\"10\" shape=\"Mrecord\"];\nedge [fontname=\"Helvetica\" fontsize=\"10\"];\n");
            while ((inString = in.readLine()) != null) {
                out2.write(inString + "\n");
            }
            out2.close();
            in.close();
            File deleteFile = new File("temp_out.tmp");
            deleteFile.delete();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int compareTo(Object o) {
        return 0;
    }
}

class IDProvider implements VertexNameProvider<Vertex> {

    @Override
    public String getVertexName(Vertex v) {
        return Integer.toString(v.hashCode());
    }
}

class LabelProvider implements VertexNameProvider<Vertex> {

    @Override
    public String getVertexName(Vertex v) {
        return v.toString();
    }
}

class EdgeProvider implements EdgeNameProvider<Edge> {

    @Override
    public String getEdgeName(Edge e) {
        return e.getEdgeType();
    }
}
