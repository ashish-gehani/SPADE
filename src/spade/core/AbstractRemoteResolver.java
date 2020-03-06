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

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLSocket;

import spade.utility.HelperFunctions;

/**
 * @author raza
 */
public abstract class AbstractRemoteResolver{

	private static final Logger logger = Logger.getLogger(AbstractRemoteResolver.class.getName());

	public abstract List<SPADEQuery> resolve();

	// Assumes that SPADE kernel is setup properly with things required by this
	// function

	public static class ExecuteRemoteQuery implements Callable<List<SPADEQuery>>{
		private final String localHostName;
		private final String remoteHostName;
		private final String remoteAddress;
		private final int remotePort;
		private final List<String> queries;
		private final String nonce;

		public ExecuteRemoteQuery(String localHostName, String remoteHostName, String remoteAddress, int remotePort,
				List<String> queries, String nonce){
			this.localHostName = localHostName;
			this.remoteAddress = remoteAddress;
			this.remotePort = remotePort;
			this.queries = queries;
			this.nonce = nonce;

			this.remoteHostName = remoteHostName != null ? remoteHostName : remoteAddress;

			// nonce can be null

			if(HelperFunctions.isNullOrEmpty(remoteAddress)){
				throw new RuntimeException("NULL/Empty remote address");
			}
			if(remotePort < 0){
				throw new RuntimeException("Invalid port number: " + remotePort);
			}
			if(queries == null || queries.isEmpty()){
				throw new RuntimeException("NULL/Empty query list");
			}
			for(String query : queries){
				if(HelperFunctions.isNullOrEmpty(query)){
					throw new RuntimeException("NULL/Empty query in query list");
				}
			}
		}

		@Override
		public List<SPADEQuery> call() throws Exception{
			List<SPADEQuery> queryResponses = new ArrayList<SPADEQuery>();

			SSLSocket querySocket = Kernel.sslConnect(remoteAddress, remotePort, 5 * 1000);
			ObjectOutputStream queryWriter = new ObjectOutputStream(querySocket.getOutputStream());
			ObjectInputStream queryResponseReader = new ObjectInputStream(querySocket.getInputStream());

			try{

				for(String query : queries){
					final String newNonce = nonce == null ? String.valueOf(System.nanoTime()) : nonce;

					SPADEQuery spadeQuery = new SPADEQuery(localHostName, remoteHostName, query, newNonce);
					spadeQuery.setQuerySentByClientAtMillis();

					queryWriter.writeObject(spadeQuery);
					queryWriter.flush();
					Object resultObject = queryResponseReader.readObject();
					spadeQuery = (SPADEQuery)resultObject; // overwrite
					spadeQuery.setQueryReceivedBackByClientAtMillis();

					// Have the result.
					// int vertexCount = 0, edgeCount = 0;;
					boolean allVerified = true;
					Set<spade.core.Graph> graphs = spadeQuery.getAllResultsOfExactType(spade.core.Graph.class);
					for(Graph graph : graphs){
						// vertexCount += graph.vertexSet().size();
						// edgeCount += graph.edgeSet().size();
						boolean verified = graph.verifySignature(newNonce);
						allVerified = allVerified && verified;
						if(!verified){
							logger.log(Level.WARNING, "Not able to verify signature of remote graph by host '"
									+ graph.getHostName() + "'");
						}
					}

					if(allVerified){
						logger.log(Level.INFO, "Signatures of all remote graph verified successfully");
						// TODO patching of the graph
					}else{
						logger.log(Level.WARNING, "Not able to verify signature of some remote graphs.");
					}

					queryResponses.add(spadeQuery);
				}

				queryWriter.writeObject(new SPADEQuery(localHostName, remoteHostName, "exit", null));
				queryWriter.flush();
				// There won't be any response
				
			}finally{
				// Closing these will close the connection on the remote side too
				queryResponseReader.close();
				queryWriter.close();
				querySocket.close();
			}

			return queryResponses;
		}
	}
}
