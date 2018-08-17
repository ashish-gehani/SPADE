package spade.utility;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Graph;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
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
	private Map<AbstractVertex,List<AbstractEdge>> outgoingCachedGraphEdges;
	private Map<AbstractVertex,List<AbstractEdge>> outgoingResponseGraphEdges;
	private static final Logger logger = Logger.getLogger(DiscrepancyDetector.class.getName());
	private Queue<Graph> removeGraphs = new LinkedList<>();
		
	/**
	 * Constructs a new Inconsistency Detector
	 * 
	 */
	public DiscrepancyDetector()
	{
		cachedGraphs = new HashSet<>();
		responseGraphs = new HashSet<>();
		cachedGraphs.add(new Graph());
		outgoingCachedGraphEdges = new HashMap<>();
		outgoingResponseGraphEdges = new HashMap<>();
	}
	
	/**
	 * Updates the cached graph by adding a checked response graph to it
	 *  should be called after an inconsistency detection that returned false
	 *  Updates the cached main graph by adding all the vertices and edges in responseGraphs set to it
	 *  Updates the outgoingCachedGraphEdges mapping
	 */
	public void update()
	{
		try
		{
			Properties props = new Properties();
			props.load(new FileInputStream("update_cache.txt"));
			boolean update_cache = Boolean.parseBoolean(props.getProperty("update_cache"));

			Graph mainCachedGraph = cachedGraphs.iterator().next();
			File file = null;
			if(!update_cache)
			{
				file = new File("graph_cache");
				FileOutputStream fos = new FileOutputStream(file);
				ObjectOutputStream oos = new ObjectOutputStream(fos);
				oos.writeObject(mainCachedGraph);
				oos.flush();
				oos.close();
				fos.close();
			}
			int cache_size = mainCachedGraph.vertexSet().size() + mainCachedGraph.edgeSet().size();
			String stats = "graph cache size: " + cache_size;
			logger.log(Level.INFO, stats);
			long start_time = System.nanoTime();
			for(Graph response: responseGraphs)
			{
				removeGraphs.add(response);
//				Map<AbstractVertex, List<AbstractEdge>> outgoingEdges = outgoingEdges(response);
//				logger.log(Level.INFO, "outgoingCachedGraphEdges.size: " + outgoingCachedGraphEdges.size());
				for(AbstractVertex responseVertex: response.vertexSet())
				{
					// put new vertex in cached graph
					mainCachedGraph.putVertex(responseVertex);
					// update outgoingCachedGraphEdges
//					List<AbstractEdge> cachedEdgeList = outgoingCachedGraphEdges.get(responseVertex);
//					if(cachedEdgeList == null)
//						cachedEdgeList = new ArrayList<>();
//					// if a list already existed just add the new edges to it
//					if(cachedEdgeList.size() > 0)
//					{
//						cachedEdgeList.addAll(outgoingEdges.get(responseVertex));
//						outgoingCachedGraphEdges.put(responseVertex, cachedEdgeList);
//					}
				}
				for(AbstractEdge e: response.edgeSet())
				{
					mainCachedGraph.putEdge(e);
				}
			}
			long execution_time = System.nanoTime() - start_time;
			logger.log(Level.INFO, "cache merge time(ns): " + execution_time);
//			logger.log(Level.INFO, "outgoingCachedGraphEdges.size: " + outgoingCachedGraphEdges.size());
			if(!update_cache)
			{
				FileInputStream fis = new FileInputStream(file);
				ObjectInputStream ois = new ObjectInputStream(fis);
				cachedGraphs = new HashSet<>();
				mainCachedGraph = (Graph) ois.readObject();
				cachedGraphs.add(mainCachedGraph);
				cache_size = mainCachedGraph.vertexSet().size() + mainCachedGraph.edgeSet().size();
				stats = "again--graph cache size: " + cache_size;
				logger.log(Level.INFO, stats);
				ois.close();
				fis.close();
			}
		}
		catch(Exception ex)
		{
			logger.log(Level.INFO, "Error updating cache", ex);
		}
	}


	/**
	 * Tests every graph in the test set against the correspondent graph in the ground set (if it exists)
	 * 
	 * @return true if found inconsistency or false if not
	 */
	public int findInconsistency()
	{
		int inconsistencyCount = 0;
		for(Graph T: responseGraphs)
		{
			Graph g_earlier = getCachedGraph();
			inconsistencyCount += findInconsistency(g_earlier, T);
			try
			{
				Properties props = new Properties();
				props.load(new FileInputStream("prune_graph.txt"));
				boolean prune_graph = Boolean.parseBoolean(props.getProperty("prune_graph"));
				if(prune_graph)
				{
					Graph graph = removeGraphs.remove();
					g_earlier.remove(graph);
					cachedGraphs = new HashSet<>();
					cachedGraphs.add(g_earlier);
//					logger.log(Level.INFO, "outgoingCachedGraphEdges.size(): " + outgoingCachedGraphEdges.size());
//					for(AbstractVertex vertex: graph.vertexSet())
//					{
//						outgoingCachedGraphEdges.remove(vertex);
//					}
//					logger.log(Level.INFO, "outgoingCachedGraphEdges.size(): " + outgoingCachedGraphEdges.size());
					findInconsistency(g_earlier, T);
				}
			}
			catch(Exception ex)
			{
				logger.log(Level.WARNING, "error finding inconsistency", ex);
			}
		}

		return inconsistencyCount;
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
	private int findInconsistency(Graph g_earlier, Graph g_later)
	{
//		SimpleDateFormat referenceTime = new SimpleDateFormat(g_earlier.getComputeTime());
//		SimpleDateFormat testTime = new SimpleDateFormat(g_later.getComputeTime());
		// TODO there is no function to compare them

		int inconsistencyCount = 0;
		Set<AbstractVertex> referenceVertexSet = g_earlier.vertexSet();
		Set<AbstractEdge> referenceEdgeSet = g_earlier.edgeSet();
		Set<AbstractVertex> testVertexSet = g_later.vertexSet();
		Set<AbstractEdge> testEdgeSet = g_later.edgeSet();
		for(AbstractVertex x : testVertexSet)
		{
			if(!referenceVertexSet.contains(x)) // x is not in ground
				continue;
//			List<AbstractEdge> referenceEdgeSet = outgoingCachedGraphEdges.get(x);
//			if(referenceEdgeSet == null)
//				continue;
//			else
//				logger.log(Level.INFO, "referenceEdgeSet not null");
			for(AbstractEdge e : referenceEdgeSet)
			{
				// x -e-> y and e is in g_earlier and NOT in g_later
				if(!testEdgeSet.contains(e))
				{
					// y is in testGraph
					if(testVertexSet.contains(e.getChildVertex()))
					{
						logger.log(Level.WARNING, "Inconsistency Detected: missing edge");
						inconsistencyCount++;
					}
					if(x.getDepth() < g_earlier.getMaxDepth())
					{
						logger.log(Level.WARNING, "Inconsistency Detected: missing edge and vertex");
						inconsistencyCount++;
					}
				}
			}
		}
		// verify if every vertex in the edges is in V2
		for(AbstractEdge e : testEdgeSet)
		{
			if(!testVertexSet.contains(e.getChildVertex()) || !testVertexSet.contains(e.getParentVertex()))
				inconsistencyCount++;
		}
		logger.log(Level.INFO, "inconsistencyCount: " + inconsistencyCount);
		int cache_size = g_earlier.vertexSet().size() + g_earlier.edgeSet().size();
		String stats = "graph cache size: " + cache_size;
		logger.log(Level.INFO, stats);

		return inconsistencyCount;
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
			AbstractVertex v = e.getParentVertex();
			List<AbstractEdge> L = out.get(v);
			if(L == null)
				L = new ArrayList<>();
			L.add(e);
			logger.log(Level.INFO, "L.size: " + L.size());
			out.put(v, L);
		}
		logger.log(Level.INFO, "out.size: " + out.size());
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