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
package spade.analyzer;

import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractAnalyzer;
import spade.core.Kernel;
import spade.core.SPADEQuery;
import spade.core.Settings;
import spade.query.quickgrail.QuickGrailExecutor;
import spade.utility.HelperFunctions;
import spade.utility.Result;

/**
 * @author raza
 */
public class CommandLine extends AbstractAnalyzer{

	private static final Logger logger = Logger.getLogger(CommandLine.class.getName());
	private static final String configKeyNameQueryServerPort = "commandline_query_port";
	private static final long millisWaitSocketClose = 100;

	// Current state of the CommandLine analyzer
	private volatile boolean shutdown = false;

	// Globals
	private ServerSocket queryServerListenerSocket = null;
	private final List<QueryConnection> queryClientConnections = new ArrayList<QueryConnection>();

	private void addQueryClientConnection(QueryConnection queryConnection){
		synchronized(queryClientConnections){
			if(queryConnection == null){
				return;
			}else{
				for(QueryConnection existingQueryConnection : queryClientConnections){
					if(existingQueryConnection == queryConnection){
						return;
					}
				}
				// Here only if it doesn't already exists
				queryClientConnections.add(queryConnection);
			}
		}
	}

	private void removeQueryClientConnection(QueryConnection queryConnection){
		synchronized(queryClientConnections){
			int index = -1;
			for(int a = 0; a < queryClientConnections.size(); a++){
				if(queryClientConnections.get(a) == queryConnection){
					index = a;
				}
			}
			if(index > -1){
				queryClientConnections.remove(index);
			}
		}
	}

	private void closeClientSocket(Socket socket){
		try{
			socket.close();
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to close query client socket", e);
		}
	}

	private void closeServerSocket(ServerSocket socket){
		try{
			socket.close();
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to close query server socket", e);
		}
	}

	@Override
	public final boolean initializeConcreteAnalyzer(String arguments){
		final String queryServerPortString = Settings.getProperty(configKeyNameQueryServerPort);
		final Result<Long> queryServerPortResult = HelperFunctions.parseLong(queryServerPortString, 10, 0,
				Integer.MAX_VALUE);
		if(queryServerPortResult.error){
			logger.log(Level.SEVERE,
					"Failed to parse query server port for analyzer using key '" + configKeyNameQueryServerPort + "'");
			logger.log(Level.SEVERE, queryServerPortResult.toErrorString());
		}else{
			final int queryServerPort = queryServerPortResult.result.intValue();
			try{
				this.queryServerListenerSocket = Kernel.createServerSocket(queryServerPort);
				try{
					Thread mainThread = new Thread(queryServerListenerRunnable,
							this.getClass().getSimpleName() + "AnalyzerServer-Thread");
					mainThread.start(); // Start
					logger.log(Level.INFO, "Query server listening on port: " + queryServerPort);
					return true;
				}catch(Exception e){
					logger.log(Level.SEVERE, "Failed to start query server thread", e);
					try{
						this.queryServerListenerSocket.close();
					}catch(Exception e1){
						logger.log(Level.SEVERE, "Failed to close socket 'Query server socket'", e1);
					}
				}
			}catch(Exception e){
				logger.log(Level.SEVERE, "Failed to create query server socket", e);
			}
		}
		return false;
	}

	private final Runnable queryServerListenerRunnable = new Runnable(){
		@Override
		public void run(){
			while(!shutdown){
				try{
					Socket queryClientSocket = queryServerListenerSocket.accept();
					try{
						QueryConnection thisConnection = new QueryConnection(queryClientSocket);
						Thread connectionThread = new Thread(thisConnection);
						connectionThread.start(); // Start
						// Add to the list at the end
						addQueryClientConnection(thisConnection);
					}catch(Exception e){
						logger.log(Level.SEVERE, "Failed setup for accepted query client socket", e);
						closeClientSocket(queryClientSocket);
					}

				}catch(Exception e){
					if(shutdown){
						// here because the server socket was closed because of a shutdown
					}else{
						logger.log(Level.SEVERE, "Unexpected exception on query server socket", e);
						closeServerSocket(queryServerListenerSocket); // Close the server socket since we are stopping
					}
					break;
				}
			}

			shutdown();
		}
	};

	public synchronized final void shutdown(){
		if(!shutdown){
			shutdown = true;
			HelperFunctions.sleepSafe(millisWaitSocketClose);
			// Stop listening for any more client connections
			closeServerSocket(this.queryServerListenerSocket);
			HelperFunctions.sleepSafe(millisWaitSocketClose);
			// Close all the clients
			synchronized(queryClientConnections){
				for(QueryConnection queryConnection : new ArrayList<QueryConnection>(queryClientConnections)){
					queryConnection.shutdown();
				}
			}
		}
	}

	private class QueryConnection extends AbstractAnalyzer.QueryConnection{
		private final Socket clientSocket;
		private final ObjectOutputStream queryOutputWriter;
		private final ObjectInputStream queryInputReader;

		private volatile boolean queryClientShutdown = false;
		
		private QuickGrailExecutor quickGrailExecutor = null;

		private QueryConnection(Socket socket){
			if(socket == null){
				throw new IllegalArgumentException("NULL query client socket");
			}else{
				try{
					OutputStream outStream = socket.getOutputStream();
					InputStream inStream = socket.getInputStream();
					this.queryOutputWriter = new ObjectOutputStream(outStream);
					this.queryInputReader = new ObjectInputStream(inStream);
					this.clientSocket = socket;
				}catch(Exception e){
					throw new IllegalArgumentException("Failed to create query IO streams", e);
				}
			}
		}

		@Override
		public SPADEQuery readLineFromClient() throws Exception{
			return (SPADEQuery)queryInputReader.readObject();
		}

		@Override
		public void writeToClient(SPADEQuery query) throws Exception{
			if(query != null){
				query.setQuerySentBackToClientAtMillis();
			}
			queryOutputWriter.writeObject(query);
			queryOutputWriter.flush();
		}

		@Override
		public SPADEQuery executeQuery(SPADEQuery query) throws Exception{
			if(query != null){
				query = quickGrailExecutor.execute(query);
			}
			return query;
		}

		@Override
		public void doQueryingSetupForCurrentStorage() throws Exception{
			quickGrailExecutor = new QuickGrailExecutor(getCurrentStorage().getQueryInstructionExecutor());
		}

		@Override
		public void doQueryingShutdownForCurrentStorage() throws Exception{
			quickGrailExecutor = null;
		}

		@Override
		public synchronized void shutdown(){
			if(!queryClientShutdown){
				queryClientShutdown = true;
				try{
					queryOutputWriter.close();
				}catch(Exception e){
					logger.log(Level.SEVERE, "Failed to close query output stream", e);
				}
				try{
					queryInputReader.close();
				}catch(Exception e){
					logger.log(Level.SEVERE, "Failed to close query input stream", e);
				}
				closeClientSocket(this.clientSocket);
				removeQueryClientConnection(this);
			}
		}

		@Override
		public boolean isShutdown(){
			return queryClientShutdown;
		}
	}
}

/*
 * How to use discrepancy-dev branch:
 * 
 * 1) 2 machines. file send between the two machines
 * 2) copy cfg/keys/public/self.*.public to cfg/keys/public/<hostname>.*.public to the each other host
 * 2) same network artifacts in both graphs on the machines (complete one)
 * 4) get lineage that would go to the remote host
 * 5) remote resolution would be set automatically
 * 6) true should be in find_inconsistency.txt file
 * 7) to introduce discrepancy -> 
 * 	a) get lineage q1 with real data
 *  b) get lineage q2 with the deletion of an edge or a vertex from the result of q1 previously gotten
 */
                 
