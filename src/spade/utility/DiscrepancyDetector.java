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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.collections.CollectionUtils;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Cache;
import spade.core.Graph;
import spade.core.Settings;
import spade.query.quickgrail.instruction.GetLineage;

/**
 * @author Carolina de Senne Garcia
 *
 */

public class DiscrepancyDetector{

	private static final Logger logger = Logger.getLogger(DiscrepancyDetector.class.getName());

	private Cache graphCache = Cache.instance;

	private Queue<Graph> prunableGraphs = new LinkedList<>();

	private Boolean findInconsistency, pruneGraph, updateCache;

	public DiscrepancyDetector(){
		final String keyFindInconsistency = "find_inconsistency";
		final String keyPruneGraph = "prune_graph";
		final String keyUpdateCache = "update_cache";

		final String configFilePath = Settings.getDefaultConfigFilePath(this.getClass());

		Map<String, String> map = null;
		try{
			map = FileUtility.readConfigFileAsKeyValueMap(configFilePath, "=");
			if(map == null){
				throw new Exception("NULL config map");
			}

			final String findInconsistencyString = map.get(keyFindInconsistency);
			final String pruneGraphString = map.get(keyPruneGraph);
			final String updateCacheString = map.get(keyUpdateCache);

			Result<Boolean> findInconsistencyResult = HelperFunctions.parseBoolean(findInconsistencyString);
			if(findInconsistencyResult.error){
				throw new Exception("Failed to read key value '" + keyFindInconsistency + "': "
						+ findInconsistencyResult.toErrorString());
			}

			findInconsistency = findInconsistencyResult.result.booleanValue();

			Result<Boolean> pruneGraphResult = HelperFunctions.parseBoolean(pruneGraphString);
			if(pruneGraphResult.error){
				throw new Exception(
						"Failed to read key value '" + keyPruneGraph + "': " + pruneGraphResult.toErrorString());
			}

			pruneGraph = pruneGraphResult.result.booleanValue();

			Result<Boolean> updateCacheResult = HelperFunctions.parseBoolean(updateCacheString);
			if(updateCacheResult.error){
				throw new Exception(
						"Failed to read key value '" + keyUpdateCache + "': " + updateCacheResult.toErrorString());
			}

			updateCache = updateCacheResult.result.booleanValue();
		}catch(Exception e){
			throw new RuntimeException("Failed to read config file: " + configFilePath, e);
		}
	}

	// true if no error
	public synchronized boolean doDiscrepancyDetection(final spade.core.Graph newGraph,
			final Set<AbstractVertex> newGraphStartVertices, final int newGraphMaxDepth,
			final GetLineage.Direction newGraphDirection, String remoteHostName){
		if(findInconsistency){
			int discrepancyCount = findDiscrepancy(newGraph, newGraphStartVertices, newGraphMaxDepth, newGraphDirection,
					remoteHostName);
			logger.log(Level.INFO, "Discrepancy Count: " + discrepancyCount);
			if(discrepancyCount == 0){
				update(newGraph);
				return true;
			}else{
				return false;
			}
		}else{
			return true;
		}
	}

	/**
	 * Updates the cached graph by adding a checked response graph to it should be
	 * called after an discrepancy detection that returned false
	 */
	private void update(final spade.core.Graph response){
		try{
			File file = null;
			if(!updateCache){
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
			String stats = "graph cache size before update. Total: " + cache_size + ". vertices: " + vertex_count
					+ ", edges: " + edge_count;
			logger.log(Level.INFO, stats);
			long start_time = System.nanoTime();
			graphCache.addGraph(response);
			prunableGraphs.add(response);
			for(AbstractVertex responseVertex : response.vertexSet()){
				// put new vertex in cached graph
				graphCache.addVertex(responseVertex);
			}
			for(AbstractEdge responseEdge : response.edgeSet()){
				graphCache.addEdge(responseEdge);
			}
			long execution_time = System.nanoTime() - start_time;
			logger.log(Level.INFO, "cache merge time(ns): " + execution_time);
			if(!updateCache){
				FileInputStream fis = new FileInputStream(file);
				ObjectInputStream ois = new ObjectInputStream(fis);
				graphCache = (Cache)ois.readObject();
				vertex_count = graphCache.getVertexCount();
				edge_count = graphCache.getEdgeCount();
				cache_size = vertex_count + edge_count;
				stats = "graph cache size after reload. Total: " + cache_size + ". vertices: " + vertex_count
						+ ", edges: " + edge_count;
				logger.log(Level.INFO, stats);
				ois.close();
				fis.close();
			}else{
				vertex_count = graphCache.getVertexCount();
				edge_count = graphCache.getEdgeCount();
				cache_size = vertex_count + edge_count;
				stats = "graph cache size after update. Total: " + cache_size + ". vertices: " + vertex_count
						+ ", edges: " + edge_count;
				logger.log(Level.INFO, stats);
			}
		}catch(Exception ex){
			logger.log(Level.INFO, "Error updating cache", ex);
		}
	}

	/**
	 * Tests every graph in the test set against the correspondent graph in the
	 * ground set (if it exists)
	 *
	 * @return true if found discrepancy or false if not
	 */
	private int findDiscrepancy(final spade.core.Graph newGraph, final Set<AbstractVertex> newGraphStartVertices,
			final int newGraphMaxDepth, final GetLineage.Direction newGraphDirection, String newGraphHostName){
		int totalDiscrepancyCount = 0;
		final Graph g_later = newGraph; 
		int discrepancyCount = 0;
		Graph g_earlier = graphCache.getCache();
		discrepancyCount = findDiscrepancy(g_earlier, g_later, newGraphStartVertices, newGraphMaxDepth, newGraphDirection);
		logger.log(Level.WARNING, discrepancyCount + " discrepancies found in response from Host: " + newGraphHostName);
		totalDiscrepancyCount += discrepancyCount;
		try{
			if(pruneGraph){
				logger.log(Level.INFO, "prune_graph: true");
				logger.log(Level.INFO, "g_later size before pruning, vertices: " + g_later.vertexSet().size()
						+ ", edges: " + g_later.edgeSet().size());
				Graph graph = prunableGraphs.remove();
				graphCache.removeGraph(graph);
				logger.log(Level.INFO, "g_later size after pruning, vertices: " + g_later.vertexSet().size()
						+ ", edges: " + g_later.edgeSet().size());
				g_earlier = graphCache.getCache();
				int postPruneDiscrepancyCount = findDiscrepancy(g_earlier, g_later, newGraphStartVertices, newGraphMaxDepth, newGraphDirection);
				logger.log(Level.INFO, postPruneDiscrepancyCount + " discrepancies found after pruning");
			}
		}catch(Exception ex){
			logger.log(Level.WARNING, "Error finding discrepancy", ex);
		}
		return totalDiscrepancyCount;
	}

	// New algorithm to detect discrepancies
	private int findDiscrepancy(Graph g_earlier, Graph g_later, final Set<AbstractVertex> startVertices,
			final int maxDepth, final GetLineage.Direction direction){
		logger.log(Level.INFO,
				"g_earlier size, vertices: " + g_earlier.vertexSet().size() + ", edges: " + g_earlier.edgeSet().size());
		logger.log(Level.INFO,
				"g_later size, vertices: " + g_later.vertexSet().size() + ", edges: " + g_later.edgeSet().size());
		logger.log(Level.INFO,
				"re-running remote query on cache. direction: " + direction + ", depth: " + maxDepth);
		Graph cache_result = g_earlier.getLineage(startVertices, direction, maxDepth);

		Set<AbstractVertex> cache_resultVertexSet = cache_result.vertexSet();
		logger.log(Level.INFO, "cache_result vertices: " + cache_resultVertexSet.size());
		Set<AbstractEdge> cache_resultEdgeSet = cache_result.edgeSet();
		logger.log(Level.INFO, "cache_result edges: " + cache_resultEdgeSet.size());

		Set<AbstractVertex> g_laterVertexSet = g_later.vertexSet();
		logger.log(Level.INFO, "g_laterVertexSet size:" + g_laterVertexSet.size());
		Set<AbstractEdge> g_laterEdgeSet = g_later.edgeSet();
		logger.log(Level.INFO, "g_laterEdgeSet size: " + g_laterEdgeSet.size());

		// subtract remote response from local response
		Collection vertexDifference = CollectionUtils.subtract(cache_resultVertexSet, g_laterVertexSet);
		int missingVertices = vertexDifference.size();
		Collection edgeDifference = CollectionUtils.subtract(cache_resultEdgeSet, g_laterEdgeSet);
		int missingEdges = edgeDifference.size();
		int discrepancyCount = missingVertices + missingEdges;

		logger.log(Level.INFO, "discrepancy count: " + discrepancyCount + ". missing vertices: " + missingVertices
				+ ". missing edges: " + missingEdges);

		return discrepancyCount;
	}

	/**
	 * Algorithm to detect basic discrepancies between graphs g_earlier and g_later
	 *
	 * @return true if found discrepancy or false if not
	 */
/*	private int findDiscrepancyOld(Graph g_earlier, Graph g_later){
		logger.log(Level.INFO,
				"g_earlier size, vertices: " + g_earlier.vertexSet().size() + ", edges: " + g_earlier.edgeSet().size());
		logger.log(Level.INFO,
				"g_later size, vertices: " + g_later.vertexSet().size() + ", edges: " + g_later.edgeSet().size());
		int discrepancyCount = 0;
		Set<AbstractVertex> g_earlierVertexSet = g_earlier.vertexSet();
		Set<AbstractEdge> g_earlierEdgeSet = g_earlier.edgeSet();
		Set<AbstractVertex> g_laterVertexSet = g_later.vertexSet();
		Set<AbstractEdge> g_laterEdgeSet = g_later.edgeSet();
		for(AbstractVertex x : g_laterVertexSet){
			// if x is not in G_earlier
			if(!g_earlierVertexSet.contains(x)){
				continue;
			}
			for(AbstractEdge e : g_earlierEdgeSet){
				AbstractVertex startingVertex;
				// for descendant query, x will be parent vertex
				if(DIRECTION_DESCENDANTS.startsWith(queryDirection))
					startingVertex = e.getParentVertex();
				else
					startingVertex = e.getChildVertex();
				// selects edges that have x as its starting vertex
				// y->x for descendant query
				if(startingVertex.equals(x)){
					// if e=(x, y) is present in G_earlier but not in G_later
					if(!g_laterEdgeSet.contains(e)){
						// choosing y as endingVertex
						// for descendant query, y will be child vertex
						AbstractVertex endingVertex;
						if(DIRECTION_DESCENDANTS.startsWith(queryDirection))
							endingVertex = e.getChildVertex();
						else
							endingVertex = e.getParentVertex();
						if(x.getDepth() < g_later.getMaxDepth()){
							logger.log(Level.WARNING, "Discrepancy detected: missing edge and vertex collapsed");
							logger.log(Level.INFO, "missing edge: " + e.toString());
							logger.log(Level.INFO, "ending vertex: " + endingVertex.toString());
							discrepancyCount++;
						}
					}
				}
			}
		}
		// verify if both vertices in an edge are in g_later
		for(AbstractEdge e : g_laterEdgeSet){
			if(!g_laterVertexSet.contains(e.getChildVertex()) || !g_laterVertexSet.contains(e.getParentVertex())){
				logger.log(Level.WARNING, "Discrepancy detected: dangling edge");
				discrepancyCount++;
			}
		}
		// verify if this vertex is an end point to at least one edge
		for(AbstractVertex x : g_laterVertexSet){
			boolean isChildOrParent = false;
			for(AbstractEdge e : g_laterEdgeSet){
				AbstractVertex endpoint;
				if(DIRECTION_DESCENDANTS.startsWith(queryDirection)){
					endpoint = e.getChildVertex();
				}else{
					endpoint = e.getParentVertex();
				}
				if(endpoint.equals(x) || x.equals(g_later.getFirstRootVertex())){
					isChildOrParent = true;
					// break;
				}
			}
			if(!isChildOrParent && g_laterEdgeSet.size() > 0){
				logger.log(Level.WARNING, "Discrepancy detected: dangling vertex");
				discrepancyCount++;
			}
		}

		int vertex_count = g_earlier.vertexSet().size();
		int edge_count = g_earlier.edgeSet().size();
		int cache_size = vertex_count + edge_count;
		String stats = "graph cache size. Total: " + cache_size + ". vertices: " + vertex_count + ", edges: "
				+ edge_count;
		logger.log(Level.INFO, stats);

		return discrepancyCount;
	}*/
	
}
