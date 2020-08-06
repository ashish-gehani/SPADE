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
package spade.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.utility.ABEGraph;
import spade.utility.HelperFunctions;
import spade.utility.RemoteSPADEQueryConnection;

/**
 * @author raza
 */
public abstract class AbstractRemoteResolver{

	// verify the response - need the nonce
	// decrypt the graph
	private static final Logger logger = Logger.getLogger(AbstractRemoteResolver.class.getName());

	public abstract List<Query> resolve();

	// Assumes that SPADE kernel is setup properly with things required by this
	// function

	public static class ExecuteRemoteQuery implements Callable<List<Query>>{
		private final String remoteAddress;
		private final int remotePort;
		private final List<Query> queries;
		
		private final String storageName;

		public ExecuteRemoteQuery(
				String remoteAddress, int remotePort,
				String storageName,
				List<Query> queries){
			this.remoteAddress = remoteAddress;
			this.remotePort = remotePort;
			this.queries = queries;
			this.storageName = storageName;

			// nonce can be null

			if(HelperFunctions.isNullOrEmpty(storageName)){
				throw new RuntimeException("NULL/Empty storage name");
			}
			if(HelperFunctions.isNullOrEmpty(remoteAddress)){
				throw new RuntimeException("NULL/Empty remote address");
			}
			if(remotePort < 0){
				throw new RuntimeException("Invalid port number: " + remotePort);
			}
			if(queries == null || queries.isEmpty()){
				throw new RuntimeException("NULL/Empty query list");
			}
			for(Query query : queries){
				if(query == null){
					throw new RuntimeException("NULL/Empty query in query list");
				}
			}
		}

		@Override
		public List<Query> call() throws Exception{
			List<Query> queryResponses = new ArrayList<Query>();

			try(RemoteSPADEQueryConnection connection = new RemoteSPADEQueryConnection(Kernel.getHostName(), remoteAddress, remotePort)){

				connection.connect(Kernel.getClientSocketFactory(), 5*1000);
				
				connection.setStorage(storageName);
				
				for(Query query : queries){
					final String newNonce = query.queryNonce == null ? String.valueOf(System.nanoTime()) : query.queryNonce;
//					query.setQueryNonce(newNonce); API changed!
					
					query = connection.executeQuery(query);
					
					// Have the result.
					// int vertexCount = 0, edgeCount = 0;;
					boolean allVerified = true;
					Set<spade.core.Graph> simpleGraphs = query.getAllResultsOfExactType(spade.core.Graph.class);
					Set<ABEGraph> encyrpytedGraphs = query.getAllResultsOfExactType(ABEGraph.class);
					Set<spade.core.Graph> allGraphs = new HashSet<spade.core.Graph>();
					allGraphs.addAll(simpleGraphs);
					allGraphs.addAll(encyrpytedGraphs);
					
					for(Graph allGraph : allGraphs){
						// vertexCount += graph.vertexSet().size();
						// edgeCount += graph.edgeSet().size();
						boolean verified = allGraph.verifySignature(newNonce);
						allVerified = allVerified && verified;
						if(!verified){
							logger.log(Level.WARNING, "Not able to verify signature of remote graph by host '"
									+ allGraph.getHostName() + "'");
						}
					}

					if(allVerified){
						logger.log(Level.INFO, "Signatures of all remote graph verified successfully");
						// TODO patching of the graph
					}else{
						logger.log(Level.WARNING, "Not able to verify signature of some remote graphs.");
					}

					queryResponses.add(query);
				}
				
			}

			return queryResponses;
		}
	}
}
