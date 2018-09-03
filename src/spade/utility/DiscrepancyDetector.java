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
	private static final Logger logger = Logger.getLogger(DiscrepancyDetector.class.getName());
	private Queue<Graph> removeGraphs = new LinkedList<>();
		
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
			for(Graph response : responseGraphs)
			{
				removeGraphs.add(response);
				for(AbstractVertex responseVertex : response.vertexSet())
				{
					// put new vertex in cached graph
					mainCachedGraph.putVertex(responseVertex);
				}
				for(AbstractEdge responseEdge : response.edgeSet())
				{
					mainCachedGraph.putEdge(responseEdge);
				}
			}
			long execution_time = System.nanoTime() - start_time;
			logger.log(Level.INFO, "cache merge time(ns): " + execution_time);
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
		} catch(Exception ex)
		{
			logger.log(Level.INFO, "Error updating cache", ex);
		}
	}


	/**
	 * Tests every graph in the test set against the correspondent graph in the ground set (if it exists)
	 *
	 * @return true if found discrepancy or false if not
	 */
	public int findDiscrepancy()
	{
		int discrepancyCount = 0;
		for(Graph g_later : responseGraphs)
		{
			Graph g_earlier = getCachedGraph();
			discrepancyCount += findDiscrepancy(g_earlier, g_later);
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
					findDiscrepancy(g_earlier, g_later);
				}
			} catch(Exception ex)
			{
				logger.log(Level.WARNING, "error finding discrepancy", ex);
			}
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
	 * @return true if found discrepancy or false if not
	 */
	private int findDiscrepancy(Graph g_earlier, Graph g_later)
	{
		int discrepancyCount = 0;
		Set<AbstractVertex> g_earlierVertexSet = g_earlier.vertexSet();
		Set<AbstractEdge> g_earlierEdgeSet = g_earlier.edgeSet();
		Set<AbstractVertex> g_laterVertexSet = g_later.vertexSet();
		Set<AbstractEdge> g_laterEdgeSet = g_later.edgeSet();
		for(AbstractVertex x : g_laterVertexSet)
		{
			// x is not in ground
			if(!g_earlierVertexSet.contains(x))
				continue;
			for(AbstractEdge e : g_earlierEdgeSet)
			{
				if(e.getParentVertex().equals(x))
				{
					// x -e-> y and e is in g_earlier and NOT in g_later
					if(!g_laterEdgeSet.contains(e))
					{
						// y is in g_later
						if(g_laterVertexSet.contains(e.getChildVertex()))
						{
							logger.log(Level.WARNING, "Discrepancy Detected: missing edge");
							discrepancyCount++;
						} else if(x.getDepth() < g_later.getMaxDepth())
						{
							logger.log(Level.WARNING, "Discrepancy Detected: missing edge and vertex");
							discrepancyCount++;
						}
					}
				}
			}
		}
		// verify if both vertices in an edge are in g_later
		for(AbstractEdge e : g_laterEdgeSet)
		{
			if(!g_laterVertexSet.contains(e.getChildVertex()) || !g_laterVertexSet.contains(e.getParentVertex()))
			{
				logger.log(Level.WARNING, "Discrepancy Detected: missing vertex");
				discrepancyCount++;
			}
		}
		// verify if this vertex is an end point to at least one edge
		for(AbstractVertex x : g_laterVertexSet)
		{
			boolean isChild = false;
			for(AbstractEdge e : g_laterEdgeSet)
			{
				if(e.getChildVertex().equals(x) || x.equals(g_later.getRootVertex()))
				{
					isChild = true;
					// break;
				}
			}
			if(!isChild)
			{
				logger.log(Level.WARNING, "Discrepancy Detected: dangling vertex, " + x.bigHashCode());
				discrepancyCount++;
			}
		}

		logger.log(Level.INFO, "discrepancyCount: " + discrepancyCount);
		int cache_size = g_earlier.vertexSet().size() + g_earlier.edgeSet().size();
		String stats = "graph cache size: " + cache_size;
		logger.log(Level.INFO, stats);

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

	public void setResponseGraph(Set<Graph> t)
	{
		responseGraphs = t;
	}

	/**
	 * @return ground graph groundGraph
	 */
	public Set<Graph> getCachedGraphs()
	{
		return cachedGraphs;
	}

	/**
	 * @return Graph testGraph
	 */
	public Set<Graph> getResponseGraph()
	{
		return responseGraphs;
	}
}