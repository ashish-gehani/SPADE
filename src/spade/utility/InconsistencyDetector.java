package spade.utility;

import java.util.Map;
import java.util.Set;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Vertex;
import spade.core.Edge;
import spade.core.Graph;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
/**
 * @author Carolina de Senne Garcia
 *
 */

public class InconsistencyDetector {
	
	private Set<Graph> cachedGraphs;
	private Set<Graph> responseGraphs;
	private Map<AbstractVertex,List<AbstractEdge>> outgoingCachedGraphEdges;
	private Map<AbstractVertex,List<AbstractEdge>> outgoingResponseGraphEdges;
		
	/**
	 * Constructs a new Inconsistency Detector
	 * 
	 */
	public InconsistencyDetector() {
		cachedGraphs = new HashSet<Graph>();
		responseGraphs = new HashSet<Graph>();
		cachedGraphs.add(new Graph());
		outgoingCachedGraphEdges = new HashMap<AbstractVertex,List<AbstractEdge>>();
		outgoingResponseGraphEdges = new HashMap<AbstractVertex,List<AbstractEdge>>();
	}
	
	/**
	 * Updates the cached graph by adding a checked response graph to it
	 *  should be called after an inconsistency detection that returned false
	 *  Updates the cached main graph by adding all the vertices and edges in responseGraphs set to it
	 *  Updates the outgoingCachedGraphEdges mapping
	 */
	public void update() {
		Graph mainCachedGraph = cachedGraphs.iterator().next();
		for(Graph G: responseGraphs) {
			Map<AbstractVertex,List<AbstractEdge>> outgoingGEdges = outgoingEdges(G);
			for(AbstractVertex V: G.vertexSet()) {
				// put new vertex in cached graph
				mainCachedGraph.putVertex(V);
				// update outgoingCachedGraphEdges
				List<AbstractEdge> cachedEdgeList = outgoingCachedGraphEdges.get(V);
				if(cachedEdgeList == null)
					cachedEdgeList = new ArrayList<AbstractEdge>();
				// if a list already existed just add the new edges to it
				cachedEdgeList.addAll(outgoingGEdges.get(V));
				outgoingCachedGraphEdges.put(V,cachedEdgeList);
			}
			for(AbstractEdge e: G.edgeSet()) {
				mainCachedGraph.putEdge(e);
			}			
		}
	}


	/**
	 * Tests every graph in the test set against the correspondent graph in the ground set (if it exists)
	 * 
	 * @return true if found inconsistency or false if not
	 */
	public boolean findInconsistency() {
		for(Graph T: responseGraphs) {
				if(findInconsistency(getCachedGraph(),T))
					return true;
		}
		return false;
	}
	
	/**
	 * 
	 * @return main cached graph in cachedGraphs set
	 */
	private Graph getCachedGraph() {
		return cachedGraphs.iterator().next();
	}

	/**
	 * Algorithm to detect basic inconsistencies between graphs G1 and G2
	 * 
	 * @return true if found inconsistency or false if not
	 */
	public boolean findInconsistency(Graph reference, Graph test) {
		SimpleDateFormat referenceTime = new SimpleDateFormat(reference.getComputeTime());
		SimpleDateFormat testTime = new SimpleDateFormat(test.getComputeTime());	
		// TODO there is no function to compare them
		
		Set<AbstractVertex> referenceVertexSet = reference.vertexSet();
		Set<AbstractVertex> testVertexSet = test.vertexSet();
		Set<AbstractEdge> testEdgeSet = test.edgeSet();
		for(AbstractVertex x : testVertexSet) {
			if(!referenceVertexSet.contains(x)) // x is not in ground
				continue;
			List<AbstractEdge> referenceEdgeSet = outgoingCachedGraphEdges.get(x);
			if(referenceEdgeSet == null)
				continue;
			for(AbstractEdge e : referenceEdgeSet) {
				if(!testEdgeSet.contains(e)) { // x -e-> y and e is in reference and NOT in test
					if(testVertexSet.contains(e.getParentVertex())) {// y is in testGraph
						System.out.println("Inconsistency Detected: missing edge");
						return true;
					}
					if(x.getDepth() < reference.getMaxDepth()) {
						System.out.println("Inconsistency Detected: missing edge and vertex");
						return true;
					}
				}
			}
		}
		// verify if every vertex in the edges is in V2
		for(AbstractEdge e : testEdgeSet) {
			if(!testVertexSet.contains(e.getChildVertex()) || !testVertexSet.contains(e.getParentVertex()))
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

	
	public void setResponseGraph(Set<Graph> t) {
		responseGraphs = t;
	}
	
	/**
	 * @return ground graph groundGraph
	 */
	public Set<Graph> getCachedGraphs() {
		return cachedGraphs;
	}
	
	/**
	 * @return Graph testGraph
	 */
	public Set<Graph> getResponseGraph() {
		return responseGraphs;
	}
	
	/**
	 * @return outgoingEdges for ground graph groundGraph
	 */
	public Map<AbstractVertex,List<AbstractEdge>> getoutgoingCachedGraphEdges() {
		return outgoingCachedGraphEdges;
	}
	
}