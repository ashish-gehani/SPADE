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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
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
import spade.core.Settings;
import spade.query.quickgrail.QuickGrailExecutor;
import spade.utility.CommonFunctions;
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
		d("this worked!");
		final String queryServerPortString = Settings.getProperty(configKeyNameQueryServerPort);
		final Result<Long> queryServerPortResult = CommonFunctions.parseLong(queryServerPortString, 10, 0,
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
					d("waiting on socket accept");
					Socket queryClientSocket = queryServerListenerSocket.accept();
					d("socket accepted");
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
						d("Proper shutdown of server");
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
			CommonFunctions.sleepSafe(millisWaitSocketClose);
			// Stop listening for any more client connections
			closeServerSocket(this.queryServerListenerSocket);
			CommonFunctions.sleepSafe(millisWaitSocketClose);
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
//		private final PrintWriter queryOutputWriter;
		private final ObjectOutputStream queryOutputWriter;
		private final BufferedReader queryInputReader;

		private volatile boolean queryClientShutdown = false;
		
		private QuickGrailExecutor quickGrailExecutor = null;

		private QueryConnection(Socket socket){
			if(socket == null){
				throw new IllegalArgumentException("NULL query client socket");
			}else{
				try{
					OutputStream outStream = socket.getOutputStream();
					InputStream inStream = socket.getInputStream();
//					this.queryOutputWriter = new PrintWriter(outStream);
					this.queryOutputWriter = new ObjectOutputStream(outStream);
					this.queryInputReader = new BufferedReader(new InputStreamReader(inStream));
					this.clientSocket = socket;
				}catch(Exception e){
					throw new IllegalArgumentException("Failed to create query IO streams", e);
				}
			}
		}

		@Override
		public String readLineFromClient() throws Exception{
			return queryInputReader.readLine();
		}

		@Override
		public void writeToClient(Object data) throws Exception{
//			queryOutputWriter.println(String.valueOf(data));
			queryOutputWriter.writeObject(data);
			queryOutputWriter.flush();
		}

		@Override
		public Object executeQuery(String query) throws Exception{
			return quickGrailExecutor.execute(query);
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

		//		@Override
		//		public void run(){
		//			try{
		//				OutputStream outStream = querySocket.getOutputStream();
		//				InputStream inStream = querySocket.getInputStream();
		//				ObjectOutputStream queryOutputStream = new ObjectOutputStream(outStream);
		//				BufferedReader queryInputStream = new BufferedReader(new InputStreamReader(inStream));
		//
		//				while(!SHUTDOWN){
		//					// Commands read from the input stream and executed.
		//					String line = queryInputStream.readLine();
		//					long start_time = System.currentTimeMillis();
		//					if(line.equalsIgnoreCase(QueryCommands.QUERY_EXIT.value)){
		//						break;
		//					}else if(line.toLowerCase().startsWith("set")){
		//						// set storage for querying
		//						String output = parseSetStorage(line);
		//						queryOutputStream.writeObject(output);
		//					}else if(AbstractQuery.getCurrentStorage() == null){
		//						String message = "No storage set for querying. " + "Use command: 'set storage <storage_name>'";
		//						queryOutputStream.writeObject(message);
		//					}else if(line.equals(QueryCommands.QUERY_LIST_CONSTRAINTS.value)){
		//						StringBuilder output = new StringBuilder(100);
		//						output.append("Constraint Name\t\t | Constraint Expression\n");
		//						output.append("-------------------------------------------------\n");
		//						for(Map.Entry<String, String> currentEntry : constraints.entrySet()){
		//							String key = currentEntry.getKey();
		//							String value = currentEntry.getValue();
		//							output.append(key).append("\t\t\t | ").append(value).append("\n");
		//						}
		//						output.append("-------------------------------------------------\n");
		//						queryOutputStream.writeObject(output.toString());
		//					}else if(line.contains(":")){
		//						// hoping its a constraint
		//						String output = createConstraint(line);
		//						queryOutputStream.writeObject(output);
		//					}else{
		//						line = replaceConstraintNames(line.trim());
		//						try{
		//							boolean success = parseQuery(line);
		//							if(!success){
		//								String message = "Function name not valid! Make sure you follow the guidelines";
		//								throw new Exception(message);
		//							}
		//							AbstractQuery queryClass;
		//							Class<?> returnType;
		//							Object result;
		//							String functionClassName = getFunctionClassName(functionName);
		//							if(functionClassName == null){
		//								String message = "Required query class not available!";
		//								throw new Exception(message);
		//							}
		//							queryClass = (AbstractQuery)Class.forName(functionClassName).newInstance();
		//							returnType = Class.forName(getReturnType(functionName));
		//							result = queryClass.execute(functionArguments);
		//							if(result != null && returnType.isAssignableFrom(result.getClass())){
		//								if(result instanceof Graph){
		//									if(isRemoteResolutionRequired()){
		//										logger.log(Level.INFO, "Performing remote resolution.");
		//										// TODO: Could use a factory pattern here to get remote resolver
		//										remoteResolver = new Recursive((Graph)result, functionName,
		//												Integer.parseInt(maxLength), direction);
		//										Thread remoteResolverThread = new Thread(remoteResolver,
		//												"Recursive-AbstractResolver");
		//										remoteResolverThread.start();
		//										// wait for thread to complete to get the final graph
		//										remoteResolverThread.join();
		//										// final graph is a set of un-stitched graphs
		//										Set<Graph> finalGraphSet = remoteResolver.getFinalGraph();
		//										clearRemoteResolutionRequired();
		//										discrepancyDetector.setResponseGraph(finalGraphSet);
		//										int discrepancyCount = discrepancyDetector.findDiscrepancy();
		//										logger.log(Level.WARNING, "discrepancyCount: " + discrepancyCount);
		//										if(discrepancyCount == 0){
		//											discrepancyDetector.update();
		//										}
		//										for(Graph graph : finalGraphSet){
		//											result = Graph.union((Graph)result, graph);
		//										}
		//										logger.log(Level.INFO, "Remote resolution completed.");
		//									}
		//									if(USE_TRANSFORMER){
		//										logger.log(Level.INFO, "Applying transformers on the final result.");
		//										Map<String, Object> queryMetaDataMap = getQueryMetaData((Graph)result);
		//										QueryMetaData queryMetaData = new QueryMetaData(queryMetaDataMap);
		//										result = iterateTransformers((Graph)result, queryMetaData);
		//										logger.log(Level.INFO, "Transformers applied successfully.");
		//									}
		//								}
		//								// if result output is to be converted into dot file format
		//								if(EXPORT_RESULT){
		//									Graph temp_result = new Graph();
		//									if(functionName.equalsIgnoreCase("GetEdge")){
		//										temp_result.edgeSet().addAll((Set<AbstractEdge>)result);
		//										result = temp_result;
		//									}else if(functionName.equalsIgnoreCase("GetVertex")){
		//										temp_result.vertexSet().addAll((Set<AbstractVertex>)result);
		//										result = temp_result;
		//									}
		//									result = ((Graph)result).exportGraph();
		//									EXPORT_RESULT = false;
		//								}
		//							}else{
		//								logger.log(Level.SEVERE, "Return type null or mismatch!");
		//							}
		//							long elapsed_time = System.currentTimeMillis() - start_time;
		//							logger.log(Level.INFO, "Time taken for query: " + elapsed_time + " ms");
		//							if(result != null){
		//								queryOutputStream.writeObject(result.toString());
		//							}else{
		//								queryOutputStream.writeObject("Result Empty");
		//							}
		//						}catch(Exception ex){
		//							logger.log(Level.SEVERE, "Error executing query request!", ex);
		//							queryOutputStream.writeObject("Error");
		//						}
		//					}
		//				}
		//				queryInputStream.close();
		//				queryOutputStream.close();
		//
		//				inStream.close();
		//				outStream.close();
		//			}catch(Exception ex){
		//				Logger.getLogger(CommandLine.QueryConnection.class.getName()).log(Level.SEVERE, null, ex);
		//			}finally{
		//				try{
		//					querySocket.close();
		//				}catch(Exception ex){
		//					Logger.getLogger(CommandLine.QueryConnection.class.getName()).log(Level.SEVERE, null, ex);
		//				}
		//			}
		//		}
	}
}