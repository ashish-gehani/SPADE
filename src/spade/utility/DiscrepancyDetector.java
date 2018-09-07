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

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Cache;
import spade.core.Graph;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static spade.core.AbstractStorage.DIRECTION_DESCENDANTS;

/**
 * @author Carolina de Senne Garcia
 *
 */

public class DiscrepancyDetector
{
	private Set<Graph> responseGraphs;
	private Cache graphCache;
	private Queue<Graph> prunableGraphs = new LinkedList<>();
	private String queryDirection;
	private static final Logger logger = Logger.getLogger(DiscrepancyDetector.class.getName());

	/**
	 * Constructs a new Discrepancy Detector
	 *
	 */
	public DiscrepancyDetector()
	{
		graphCache = Cache.getInstance();
		responseGraphs = new HashSet<>();
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

			File file = null;
			if(!update_cache)
			{
				logger.log(Level.INFO, "update_cache: false");
				file = new File("graph_cache");
				FileOutputStream fos = new FileOutputStream(file);
				ObjectOutputStream oos = new ObjectOutputStream(fos);
				oos.writeObject(graphCache);
				oos.flush();
				oos.close();
				fos.close();
			}
			int vertex_count = graphCache.getVertexCount();
			int edge_count = graphCache.getEdgeCount();
			int cache_size = vertex_count + edge_count;
			String stats = "graph cache size before update. Total: " + cache_size + ". vertices: " + vertex_count + ", edges: " + edge_count;
			logger.log(Level.INFO, stats);
			long start_time = System.nanoTime();
			for(Graph response : responseGraphs)
			{
				graphCache.addGraph(response);
				prunableGraphs.add(response);
				for(AbstractVertex responseVertex : response.vertexSet())
				{
					// put new vertex in cached graph
					graphCache.addVertex(responseVertex);
				}
				for(AbstractEdge responseEdge : response.edgeSet())
				{
					graphCache.addEdge(responseEdge);
				}
			}
			long execution_time = System.nanoTime() - start_time;
			logger.log(Level.INFO, "cache merge time(ns): " + execution_time);
			if(!update_cache)
			{
				FileInputStream fis = new FileInputStream(file);
				ObjectInputStream ois = new ObjectInputStream(fis);
				graphCache = (Cache) ois.readObject();
				vertex_count = graphCache.getVertexCount();
				edge_count = graphCache.getEdgeCount();
				cache_size = vertex_count + edge_count;
				stats = "graph cache size after reload. Total: " + cache_size + ". vertices: " + vertex_count + ", edges: " + edge_count;
				logger.log(Level.INFO, stats);
				ois.close();
				fis.close();
			} else
			{
				vertex_count = graphCache.getVertexCount();
				edge_count = graphCache.getEdgeCount();
				cache_size = vertex_count + edge_count;
				stats = "graph cache size after update. Total: " + cache_size + ". vertices: " + vertex_count + ", edges: " + edge_count;
				logger.log(Level.INFO, stats);
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
		int totalDiscrepancyCount = 0;
		for(Graph g_later : responseGraphs)
		{
			int discrepancyCount = 0;
			Graph g_earlier = graphCache.getCache();
			discrepancyCount = findDiscrepancy(g_earlier, g_later);
			logger.log(Level.WARNING, discrepancyCount + " discrepancies found in response from Host: " + g_later.getHostName());
			totalDiscrepancyCount += discrepancyCount;
			try
			{
				Properties props = new Properties();
				props.load(new FileInputStream("prune_graph.txt"));
				boolean prune_graph = Boolean.parseBoolean(props.getProperty("prune_graph"));
				if(prune_graph)
				{
					logger.log(Level.INFO, "prune_graph: true");
					Graph graph = prunableGraphs.remove();
					graphCache.removeGraph(graph);
					g_earlier = graphCache.getCache();
					findDiscrepancy(g_earlier, g_later);
				}
			} catch(Exception ex)
			{
				logger.log(Level.WARNING, "Error finding discrepancy", ex);
			}
		}
		return totalDiscrepancyCount;
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
			{
				continue;
			}
			for(AbstractEdge e : g_earlierEdgeSet)
			{
				AbstractVertex startingVertex;
				if(DIRECTION_DESCENDANTS.startsWith(queryDirection))
					startingVertex = e.getParentVertex();
				else
					startingVertex = e.getChildVertex();
				if(startingVertex.equals(x))
				{
					// x -e-> y and e is in g_earlier and NOT in g_later
					if(!g_laterEdgeSet.contains(e))
					{
						AbstractVertex endingVertex;
						if(DIRECTION_DESCENDANTS.startsWith(queryDirection))
							endingVertex = e.getChildVertex();
						else
							endingVertex = e.getParentVertex();
						// y is in g_later
						if(g_laterVertexSet.contains(endingVertex))
						{
							logger.log(Level.WARNING, "Discrepancy detected: missing edge");
							discrepancyCount++;
						} else if(x.getDepth() < g_later.getMaxDepth())
						{
							logger.log(Level.WARNING, "Discrepancy detected: missing edge and vertex");
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
				logger.log(Level.WARNING, "Discrepancy detected: dangling edge");
				discrepancyCount++;
			}
		}
		// verify if this vertex is an end point to at least one edge
		for(AbstractVertex x : g_laterVertexSet)
		{
			boolean isChildOrParent = false;
			for(AbstractEdge e : g_laterEdgeSet)
			{
				AbstractVertex endpoint;
				if(DIRECTION_DESCENDANTS.startsWith(queryDirection))
				{
					endpoint = e.getChildVertex();
				} else
				{
					endpoint = e.getParentVertex();
				}
				if(endpoint.equals(x) || x.equals(g_later.getRootVertex()))
				{
					isChildOrParent = true;
					// break;
				}
			}
			if(!isChildOrParent && g_laterEdgeSet.size() > 0)
			{
				logger.log(Level.WARNING, "Discrepancy detected: dangling vertex");
				discrepancyCount++;
			}
		}

		int vertex_count = g_earlier.vertexSet().size();
		int edge_count = g_earlier.edgeSet().size();
		int cache_size = vertex_count + edge_count;
		String stats = "graph cache size. Total: " + cache_size + ". vertices: " + vertex_count + ", edges: " + edge_count;
		logger.log(Level.INFO, stats);

		return discrepancyCount;
	}

	public void setResponseGraph(Set<Graph> t)
	{
		responseGraphs = t;
	}

	/**
	 * @return Graph testGraph
	 */
	public Set<Graph> getResponseGraph()
	{
		return responseGraphs;
	}

	public String getQueryDirection()
	{
		return queryDirection;
	}

	public void setQueryDirection(String queryDirection)
	{
		this.queryDirection = queryDirection;
	}

}