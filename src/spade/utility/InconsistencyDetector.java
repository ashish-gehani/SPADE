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
import java.util.HashSet;
import java.util.List;
/**
 * @author Carolina de Senne Garcia
 *
 */

public class InconsistencyDetector {
	
	private Set<Graph> groundGraphs;
	private Set<Graph> testGraphs;
	private Map<AbstractVertex,List<AbstractEdge>> outgoingGroundGraphEdges;
	private Map<String,Graph> hostnameToGraph; // maybe set of graphs, since one host can generate more than one graph?
		
	/**
	 * Constructs a new Inconsistency Detector
	 * 
	
	 */
	public InconsistencyDetector() {
		groundGraphs = new HashSet<Graph>();
		outgoingGroundGraphEdges = new HashMap<AbstractVertex,List<AbstractEdge>>();
	}
	
	public void update() {
		// TODO
		// add testsGraphs to groundGraphs
		// update outgoingGroundGraphEdges (if a list already existed just add the new edges to it)
		// update hostnameToGraph
	}


	/**
	 * Tests every graph in the test set against the correspondent graph in the ground set (if it exists)
	 * 
	 * @return true if found inconsistency or false if not
	 */
	public boolean findInconsistency() {
		for(Graph T: testGraphs) {
			if(hostnameToGraph.containsKey(T.getHostName())) {
				if(findInconsistency(hostnameToGraph.get(T.getHostName()),T))
					return true;
			}
		}
		return false;
	}
	
	/**
	 * Algorithm to detect basic inconsistencies between graphs G1 and G2
	 * 
	 * @return true if found inconsistency or false if not
	 */
	public boolean findInconsistency(Graph G1, Graph G2) {
		Set<AbstractVertex> V1 = G1.vertexSet();
		Set<AbstractVertex> V2 = G2.vertexSet();
		Set<AbstractEdge> E2 = G1.edgeSet();
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
					if(x.getDepth() < G1.getMaxDepth()) {
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

	
	public void setTestGraph(Set<Graph> t) {
		testGraphs = t;
	}
	
	/**
	 * @return ground graph groundGraph
	 */
	public Set<Graph> getGroundGraph() {
		return groundGraphs;
	}
	
	/**
	 * @return Graph testGraph
	 */
	public Set<Graph> getNewGraph() {
		return testGraphs;
	}
	
	/**
	 * @return outgoingEdges for ground graph groundGraph
	 */
	public Map<AbstractVertex,List<AbstractEdge>> getoutgoingGroundGraphEdges() {
		return outgoingGroundGraphEdges;
	}
	
}