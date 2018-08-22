/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2017 SRI International
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
package spade.utility;

import java.util.Map;
import java.util.Set;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Graph;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Carolina de Senne Garcia
 *
 */

public class DiscrepancyDetector
{
	
	private Set<Graph> cachedGraphs;
	private Set<Graph> responseGraphs;
	private static final Logger logger = Logger.getLogger(DiscrepancyDetector.class.getName());
		
	/**
	 * Constructs a new Discrepancy Detector
	 * 
	 */
	public DiscrepancyDetector()
	{
		cachedGraphs = new HashSet<>();
		responseGraphs = new HashSet<>();
		cachedGraphs.add(new Graph());
	}
	
	/**
	 * Updates the cached graph by adding a checked response graph to it
	 *  should be called after an discrepancy detection that returned false
	 *  Updates the cached main graph by adding all the vertices and edges in responseGraphs set to it
	 */
	public void update()
	{
		Graph mainCachedGraph = cachedGraphs.iterator().next();
		for(Graph response: responseGraphs)
		{
			for(AbstractVertex responseVertex: response.vertexSet())
			{
				mainCachedGraph.putVertex(responseVertex);
			}
			for(AbstractEdge e: response.edgeSet())
			{
				mainCachedGraph.putEdge(e);
			}			
		}
	}


	/**
	 * Tests every graph in the test set against the correspondent graph in the ground set (if it exists)
	 * 
	 * @return true if found discrepancy or false if not
	 */
	public int findDiscrepancy ()
	{
		int discrepancyCount = 0;
		for(Graph T: responseGraphs)
		{
			discrepancyCount += findDiscrepancy(getCachedGraph(), T);
		}

		return discrepancyCount;
	}
	
	/**
	 * 
	 * @return main cached graph in cachedGraphs set
	 */
	private Graph getCachedGraph()
	{
		return cachedGraphs.iterator().next();
	}

	/**
	 * Algorithm to detect basic discrepancies between graphs g_earlier and g_later
	 * 
	 * @return count of discrepancies found
	 */
	private int findDiscrepancy(Graph g_earlier, Graph g_later)
	{
		int discrepancyCount = 0;
		Set<AbstractVertex> referenceVertexSet = g_earlier.vertexSet();
		Set<AbstractEdge> referenceEdgeSet = g_earlier.edgeSet();
		Set<AbstractVertex> testVertexSet = g_later.vertexSet();
		Set<AbstractEdge> testEdgeSet = g_later.edgeSet();
		for(AbstractVertex x : testVertexSet)
		{
			if(!referenceVertexSet.contains(x)) // x is not in ground
				continue;
			for(AbstractEdge e : referenceEdgeSet)
			{
				// x -e-> y and e is in g_earlier and NOT in g_later
				if(!testEdgeSet.contains(e))
				{
					// y is in testGraph
					if(testVertexSet.contains(e.getParentVertex()))
					{
						logger.log(Level.WARNING, "Discrepancy Detected: missing edge");
						discrepancyCount++;
					}
					if(x.getDepth() < g_earlier.getMaxDepth())
					{
						logger.log(Level.WARNING, "Discrepancy Detected: missing edge and vertex");
						discrepancyCount++;
					}
				}
			}
		}
		// verify if every vertex in the edges is in V2
		for(AbstractEdge e : testEdgeSet)
		{
			if(!testVertexSet.contains(e.getChildVertex()) || !testVertexSet.contains(e.getParentVertex()))
				discrepancyCount++;
		}

		return discrepancyCount;
	}
	

	/**
	 * Constructs a mapping from vertices to their outgoing edges
	 * 
	 * @param G a graph
	 * @return a map from the vertices to their respective outgoing edges
	 */
	public static Map<AbstractVertex,List<AbstractEdge>> outgoingEdges(Graph G)
	{
		Map<AbstractVertex,List<AbstractEdge>> out = new HashMap<>();
		Set<AbstractEdge> E = G.edgeSet();
		for(AbstractEdge e : E)
		{
			AbstractVertex v = e.getChildVertex();
			List<AbstractEdge> L = out.get(v);
			if(L == null)
				L = new ArrayList<>();
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
}