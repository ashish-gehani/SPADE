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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashSet;
import java.util.Iterator;
import org.jgrapht.Graph;
import org.jgrapht.ext.DOTExporter;
import org.jgrapht.ext.EdgeNameProvider;
import org.jgrapht.ext.VertexNameProvider;
import org.jgrapht.graph.DefaultDirectedGraph;

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

    public static Lineage intersection(DefaultDirectedGraph<AbstractVertex, AbstractEdge> inputGraph1, DefaultDirectedGraph<AbstractVertex, AbstractEdge> inputGraph2) {
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

    public static Lineage union(DefaultDirectedGraph<AbstractVertex, AbstractEdge> inputGraph1, DefaultDirectedGraph<AbstractVertex, AbstractEdge> inputGraph2) {
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

    public static void exportDOT(Graph<AbstractVertex, AbstractEdge> inputGraph, String path) {
        try {
            FileWriter tempfileWriter = new FileWriter("temp_out.tmp", false);
            DOTExporter dotExporter = new DOTExporter(new IDProvider(), new LabelProvider(), new EdgeProvider());
            dotExporter.export(tempfileWriter, inputGraph);
            tempfileWriter.close();
            FileWriter fileWriter = new FileWriter(path, false);
            BufferedReader tempfileReader = new BufferedReader(new FileReader("temp_out.tmp"));
            String inString = "";
            fileWriter.write(tempfileReader.readLine() + "\n");
            fileWriter.write("graph [rankdir = \"RL\"];\nnode [fontname=\"Helvetica\" fontsize=\"10\" shape=\"Mrecord\"];\nedge [fontname=\"Helvetica\" fontsize=\"10\"];\n");
            while ((inString = tempfileReader.readLine()) != null) {
                fileWriter.write(inString + "\n");
            }
            fileWriter.close();
            tempfileReader.close();
            File deleteFile = new File("temp_out.tmp");
            deleteFile.delete();
        } catch (Exception exception) {
            exception.printStackTrace(System.err);
        }
    }
}

class IDProvider implements VertexNameProvider<AbstractVertex> {

    @Override
    public String getVertexName(AbstractVertex v) {
        return Integer.toString(v.hashCode());
    }
}

class LabelProvider implements VertexNameProvider<AbstractVertex> {

    @Override
    public String getVertexName(AbstractVertex v) {
        return v.toString();
    }
}

class EdgeProvider implements EdgeNameProvider<AbstractEdge> {

    @Override
    public String getEdgeName(AbstractEdge e) {
        return e.getEdgeType();
    }
}
