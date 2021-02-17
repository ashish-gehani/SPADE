/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2020 SRI International
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
package spade.resolver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import spade.core.AbstractRemoteResolver;
import spade.core.AbstractVertex;
import spade.core.Kernel;
import spade.core.Query;
import spade.core.Settings;
import spade.query.quickgrail.RemoteResolver;
import spade.query.quickgrail.instruction.GetLineage;
import spade.utility.HelperFunctions;

public class RemoteLineageResolver extends AbstractRemoteResolver{

	private static final int NTHREADS = 10;
	
	private final Map<AbstractVertex, Integer> localNetworkVertexToLocalDepth = new HashMap<AbstractVertex, Integer>();
	private final int maxDepth;
	private final GetLineage.Direction direction;
	private final String nonce;

	private final String storageClassName;

	private final Map<String, Set<AbstractVertex>> remoteIpToNetworkVertices = new HashMap<String, Set<AbstractVertex>>();

	public RemoteLineageResolver(Map<AbstractVertex, Integer> localNetworkVertexToLocalDepth, int maxDepth,
			GetLineage.Direction direction, String nonce, String storageClassName){
		this.localNetworkVertexToLocalDepth.putAll(localNetworkVertexToLocalDepth);
		this.maxDepth = maxDepth;
		this.direction = direction;
		this.nonce = nonce; // can be null
		this.storageClassName = storageClassName;

		if(maxDepth < 0){
			throw new RuntimeException("Negative max depth: " + maxDepth);
		}
		if(direction == null){
			throw new RuntimeException("NULL direction");
		}
		if(HelperFunctions.isNullOrEmpty(storageClassName)){
			throw new RuntimeException("NULL/Empty storage class name");
		}

		for(Map.Entry<AbstractVertex, Integer> entry : localNetworkVertexToLocalDepth.entrySet()){
			AbstractVertex vertex = entry.getKey();
			Integer depth = entry.getValue();
			if(vertex == null){
				throw new RuntimeException("NULL network vertex in map");
			}
			if(depth == null){
				throw new RuntimeException("NULL depth for vertex in map");
			}
			if(depth < 0){
				throw new RuntimeException("Negative depth for vertex in map");
			}
			String remoteAddress = RemoteResolver.getRemoteAddress(vertex);
			if(HelperFunctions.isNullOrEmpty(remoteAddress)){
				throw new RuntimeException("Negative depth for vertex in map");
			}
			Set<AbstractVertex> remoteVerticesSet = remoteIpToNetworkVertices.get(remoteAddress);
			if(remoteVerticesSet == null){
				remoteVerticesSet = new HashSet<AbstractVertex>();
				remoteIpToNetworkVertices.put(remoteAddress, remoteVerticesSet);
			}
			remoteVerticesSet.add(vertex);
		}
	}

	@Override
	public List<Query> resolve(){
		int clientPort = Settings.getCommandLineQueryPort();
				
		Map<String, List<Query>> allQueries = new HashMap<String, List<Query>>();
		for(Map.Entry<String, Set<AbstractVertex>> entry : remoteIpToNetworkVertices.entrySet()){
			String remoteAddress = entry.getKey();
			Set<AbstractVertex> vertices = entry.getValue();
			
			List<Query> queriesForSingleHost = new ArrayList<Query>();
			allQueries.put(remoteAddress, queriesForSingleHost);
			
			for(AbstractVertex vertex : vertices){
				String query = buildRemoteGetLineageQuery(vertex, localNetworkVertexToLocalDepth.get(vertex));
				Query q = new Query(Kernel.getHostName(), remoteAddress, query, nonce);
				queriesForSingleHost.add(q);
			}
		}
		
		List<Query> queryResponses = new ArrayList<Query>();
		ExecutorService executorService = Executors.newFixedThreadPool(NTHREADS);
		
		try{
			List<Future<List<Query>>> futures = new ArrayList<>();
			
			for(Map.Entry<String, List<Query>> entry : allQueries.entrySet()){
				String remoteAddress = entry.getKey();
				List<Query> queries = entry.getValue();
				if(queries.size() > 0){
					Callable<List<Query>> queryExecutor = new ExecuteRemoteQuery(
							remoteAddress, clientPort, storageClassName, queries);
					Future<List<Query>> future = executorService.submit(queryExecutor);
					futures.add(future);
				}
			}
			
			// Going to wait
			
			for(Future<List<Query>> future : futures){
				try{
					List<Query> queryResponseSublist = future.get();
					if(queryResponseSublist != null){
						queryResponses.addAll(queryResponseSublist);
					}
				}catch(Exception e){
					throw new RuntimeException("Failed to get query results", e);
				}
			}
			
		}finally{
			executorService.shutdown();
		}
		
		return queryResponses;
	}

	private String buildRemoteGetLineageQuery(AbstractVertex localNetworkVertex, final int localDepth){
		final int remoteDepth = maxDepth - localDepth;
		String query = "";
		query += "dump $base.getLineage($base.getVertex("; // Dump so that we don't have to store it anywhere
		query += formatQueryName(RemoteResolver.getAnnotationLocalAddress()) + "="
				+ formatQueryValue(RemoteResolver.getRemoteAddress(localNetworkVertex));
		query += " and ";
		query += formatQueryName(RemoteResolver.getAnnotationLocalPort()) + "="
				+ formatQueryValue(RemoteResolver.getRemotePort(localNetworkVertex));
		query += " and ";
		query += formatQueryName(RemoteResolver.getAnnotationRemoteAddress()) + "="
				+ formatQueryValue(RemoteResolver.getLocalAddress(localNetworkVertex));
		query += " and ";
		query += formatQueryName(RemoteResolver.getAnnotationRemotePort()) + "="
				+ formatQueryValue(RemoteResolver.getLocalPort(localNetworkVertex));
		query += ")";
		query += ", " + remoteDepth;
		query += ", " + getFormattedDirection(direction);
		query += ", 1);"; // finish of get lineage with remote resolve & export = 1 TODO
		return query;
	}

	private String formatQueryName(String name){
		return '"' + name + '"';
	}

	private String formatQueryValue(String name){
		return "'" + name + "'";
	}

	private String getFormattedDirection(GetLineage.Direction direction){
		switch(direction){
		case kAncestor:
			return "'a'";
		case kDescendant:
			return "'d'";
		case kBoth:
			return "'b'";
		default:
			throw new RuntimeException("Unexpected direction: " + direction);
		}
	}
}
