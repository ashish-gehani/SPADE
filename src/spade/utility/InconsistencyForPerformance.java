package spade.utility;

import java.util.Map;
import java.util.Set;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Vertex;
import spade.core.Edge;
import spade.core.Graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
/**
 * @author Carolina de Senne Garcia
 *
 */

public class InconsistencyForPerformance {
	
	private Graph groundGraph;
	private Graph testGraph;
	private Map<AbstractVertex,List<AbstractEdge>> outgoingGroundGraphEdges;
	private Map<AbstractVertex,Integer> vertexDepth = new HashMap<AbstractVertex,Integer>();
	
	/**
	 * Constructs a new Inconsistency Detector
	 * 
	 * @param groundGraph ground (trusted) graph to take as a reference
	 * @param testGraph new provenance graph to analyse and detect possible existing inconsistencies
	 */
	public InconsistencyForPerformance(Graph groundGraph, Graph testGraph) {
		this.groundGraph = groundGraph;
		this.testGraph = testGraph;
		this.outgoingGroundGraphEdges = outgoingEdges(groundGraph);
		calculateDepthsBFS(groundGraph);
	}
	

	/**
	 * Calculates a simple BFS of the graph g to find the distances from the origin vertex to the other vertices
	 * Every edge has distance 1
	 * 
	 * @param g the graph to calculate BFS
	 * @param origin vertex from where the BFS should start
	 */
	public void calculateDepthsBFS(Graph g) {
		AbstractVertex origin = g.getRootVertex();
		Map<AbstractVertex,List<AbstractEdge>> outgoingEdges = outgoingEdges(g);
		vertexDepth.put(origin,0);
		List<AbstractVertex> FIFO = new ArrayList<AbstractVertex>();
		FIFO.add(origin);
		while(!FIFO.isEmpty()) {
			AbstractVertex v = FIFO.remove(0);
			int depth = vertexDepth.get(v);
			List<AbstractEdge> nextNodes = outgoingEdges.get(v);
			if(nextNodes == null)
				continue;
			for(AbstractEdge e : nextNodes) {
				AbstractVertex d = e.getParentVertex();
				if(!vertexDepth.containsKey(d)) {
					vertexDepth.put(d,depth+1);
					FIFO.add(d);
				}
			}
		}
	}


	/**
	 * Algorithm to detect basic inconsistencies in the new provenance graph testGraph, relating it to the existing ground (trusted) graph groundGraph
	 * 
	 * @return true if found inconsistency or false if not
	 */
	public boolean findInconsistency() {
		Set<AbstractVertex> V1 = groundGraph.vertexSet();
		Set<AbstractVertex> V2 = testGraph.vertexSet();
		Set<AbstractEdge> E2 = testGraph.edgeSet();
		for(AbstractVertex x : V1) {
			if(!V2.contains(x)) // x is in groundGraph and not in testGraph
				continue;
			List<AbstractEdge> E = outgoingGroundGraphEdges.get(x);
			if(E == null)
				continue;
			for(AbstractEdge e : E) {
				if(!E2.contains(e)) { // x -e-> y and e is in groundGraph and NOT in testGraph
					if(V2.contains(e.getParentVertex())) {// y is in testGraph
						System.out.println("Inconsistency Detected: missing edge");
						return true;
					}
					if(vertexDepth.get(x) < groundGraph.getMaxDepth()) {
						System.out.println("Inconsistency Detected: missing edge and vertex");
						return true;
					}
				}
			}
		}
		// verify if every vertex in the edges is in V2
		for(AbstractEdge e : E2) {
			if(!V2.contains(e.getChildVertex()) || !V2.contains(e.getParentVertex()))
				return true;
		}
		return false;
	}
	

	/**
	 * Constructs a mapping from vertices to their outgoing edges
	 * 
	 * @param G a graph
	 * @return a map from the vertices to their respective outgoing edges
	 */
	public static Map<AbstractVertex,List<AbstractEdge>> outgoingEdges(Graph G) {
		Map<AbstractVertex,List<AbstractEdge>> out = new HashMap<AbstractVertex,List<AbstractEdge>>();
		Set<AbstractEdge> E = G.edgeSet();
		for(AbstractEdge e : E) {
			AbstractVertex v = e.getChildVertex();
			List<AbstractEdge> L = out.get(v);
			if(L == null)
				L = new ArrayList<AbstractEdge>();
			L.add(e);
			out.put(v, L);
		}
		return out;
	}

	
	/**
	 * @return ground graph groundGraph
	 */
	public Graph getGroundGraph() {
		return groundGraph;
	}
	
	/**
	 * @return Graph testGraph
	 */
	public Graph getNewGraph() {
		return testGraph;
	}
	
	/**
	 * @return outgoingEdges for ground graph groundGraph
	 */
	public Map<AbstractVertex,List<AbstractEdge>> getoutgoingGroundGraphEdges() {
		return outgoingGroundGraphEdges;
	}
	
	public static void main(String[] args) {
		//testInconsistency();
		//testGetLineage();
		//findBigLineage("/tmp/provenance.dot");
		//testRealGraph("/tmp/provenance.dot");
	}
	
	private static void testGetLineage() {
		Graph G0 = new Graph();
		AbstractVertex V0 = new Vertex();
		V0.addAnnotation("id", "0");
		AbstractVertex V1 = new Vertex();
		V1.addAnnotation("id", "1");
		AbstractVertex V2 = new Vertex();
		V2.addAnnotation("id", "2");
		AbstractVertex V3 = new Vertex();
		V3.addAnnotation("id", "3");
		AbstractVertex V4 = new Vertex();
		V4.addAnnotation("id", "4");
		AbstractVertex V5 = new Vertex();
		V5.addAnnotation("id", "5");
		// G0 - ground graph
		G0.putVertex(V0);
		G0.putVertex(V1);
		G0.putVertex(V2);
		G0.putVertex(V3);
		G0.putVertex(V4);
		G0.putEdge(new Edge(V0,V1));
		G0.putEdge(new Edge(V0,V2));
		G0.putEdge(new Edge(V1,V3));
		G0.putEdge(new Edge(V2,V3));
		G0.putEdge(new Edge(V2,V4));
		
		Graph L = G0.getLineage(V1.bigHashCode(), "ancest", 10);	
		System.out.println(L.vertexSet());
	}

	private static void testInconsistency() {
		Graph G0 = new Graph();
		Graph G1 = new Graph();
		Graph G2 = new Graph();
		Graph G3 = new Graph();
		
		AbstractVertex V0 = new Vertex();
		V0.addAnnotation("id", "0");
		AbstractVertex V1 = new Vertex();
		V1.addAnnotation("id", "1");
		AbstractVertex V2 = new Vertex();
		V2.addAnnotation("id", "2");
		AbstractVertex V3 = new Vertex();
		V3.addAnnotation("id", "3");
		AbstractVertex V4 = new Vertex();
		V4.addAnnotation("id", "4");
		AbstractVertex V5 = new Vertex();
		V5.addAnnotation("id", "5");
		
		// G0 - ground graph
		G0.putVertex(V0);
		G0.putVertex(V1);
		G0.putVertex(V2);
		G0.putVertex(V3);
		G0.putVertex(V4);
		G0.putEdge(new Edge(V0,V1));
		G0.putEdge(new Edge(V0,V2));
		G0.putEdge(new Edge(V1,V3));
		G0.putEdge(new Edge(V2,V3));
		G0.putEdge(new Edge(V2,V4));
		G0.setRootVertex(V0);
		
		// groundGraph - Missing Edge
		G1.putVertex(V0);
		G1.putVertex(V1);
		G1.putVertex(V2);
		G1.putVertex(V3);
		G1.putVertex(V4);
		G1.putEdge(new Edge(V0,V1));
		G1.putEdge(new Edge(V0,V2));
		G1.putEdge(new Edge(V1,V3));
		G1.putEdge(new Edge(V2,V4));
		G1.setRootVertex(V0);
		
		InconsistencyForPerformance I1 = new InconsistencyForPerformance(G0, G1);
		System.out.println("I1 "+I1.findInconsistency());
		
		// testGraph - Missing Edge and Vertex
		G2.putVertex(V0);
		G2.putVertex(V1);
		G2.putVertex(V2);
		G2.putVertex(V3);
		G2.putEdge(new Edge(V0,V1));
		G2.putEdge(new Edge(V0,V2));
		G2.putEdge(new Edge(V1,V3));
		G2.putEdge(new Edge(V2,V3));
		G2.setRootVertex(V0);
		
		InconsistencyForPerformance I2 = new InconsistencyForPerformance(G0, G2);
		System.out.println("I2 "+ I2.findInconsistency());
		
		// G3 - Additional Edges + Vertex (okay)
		G3.putVertex(V0);
		G3.putVertex(V1);
		G3.putVertex(V2);
		G3.putVertex(V3);
		G3.putVertex(V4);
		G3.putVertex(V5);
		G3.putEdge(new Edge(V0,V1));
		G3.putEdge(new Edge(V0,V2));
		G3.putEdge(new Edge(V0,V3));
		G3.putEdge(new Edge(V1,V3));
		G3.putEdge(new Edge(V2,V3));
		G3.putEdge(new Edge(V2,V4));
		G3.putEdge(new Edge(V2,V5));
		G3.setRootVertex(V0);
		
		InconsistencyForPerformance I3 = new InconsistencyForPerformance(G0, G3);
		System.out.println("I3 "+I3.findInconsistency());
	}
	
	private static void findBigLineage(String path) {
		Graph importG = Graph.importGraph(path);
		int i = 0;
		int maxI = 0;
		long maxSize = 0;
		for(AbstractVertex V : importG.vertexSet()) {
			if(i == 95)
				System.out.println(V.bigHashCode());
			Graph ground = importG.getLineage(V.bigHashCode(), "ancest", 100000);
			if(ground.vertexSet().size() > maxSize) {
				maxI = i;
				maxSize = ground.vertexSet().size();
			}
			i++;
		}
		System.out.println(maxI+": "+maxSize);
	}
	
	private static void testRealGraph(String path) {
		Graph importG = Graph.importGraph(path);
		Graph ground = importG.getLineage("4f559f06dc6697086d05306208618511", "ancest", 100000);
		
		System.out.println("First test: MISSING EDGE");
		Graph removedEdge = removeEdge(ground);
		InconsistencyForPerformance I1 = new InconsistencyForPerformance(ground, removedEdge);
		I1.findInconsistency();
		
		System.out.println("Second test: MISSING EDGE and VERTEX");
		Graph removedVertex = removeVertex(ground);
		InconsistencyForPerformance I2 = new InconsistencyForPerformance(ground, removedVertex);
		I2.findInconsistency();
	}
	
	public static Graph removeEdge(Graph G) {
		Graph N = new Graph();
		int i = 0;
		for(AbstractEdge e : G.edgeSet()) {
			N.putVertex(e.getChildVertex());
			N.putVertex(e.getParentVertex());
			i++;
			if(i == 42)
				continue;
			N.putEdge(e);
		}
		return N;
	}
	
	public static Graph removeVertex(Graph G) {
		Graph N = new Graph();
		int i = 0;
		AbstractVertex removedV=null;
		for(AbstractVertex V : G.vertexSet()) {
			if(i == 42) {
				removedV = V;
			}
			i++;	
		}
		for(AbstractEdge e : G.edgeSet()) {
			if(e.getChildVertex().equals(removedV) || e.getParentVertex().equals(removedV))
				continue;
			N.putVertex(e.getChildVertex());
			N.putVertex(e.getParentVertex());
			N.putEdge(e);
		}
		return N;
	}
	
}