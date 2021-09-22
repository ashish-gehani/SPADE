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

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.ObjectOutputStream;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.utility.FileUtility;
import spade.utility.HelperFunctions;
import spade.utility.Result;

/**
 * @author raza
 */
public abstract class AbstractAnalyzer{

	public static enum HelpType{ ALL, CONTROL, CONSTRAINT, GRAPH, ENV, REMOTE }
	
	private final Logger logger = Logger.getLogger(this.getClass().getName());
	
	private static final String configKeyNameUseScaffold = "use_scaffold";
	private static final String configKeyNameUseTransformer = "use_transformer";
	private static final String configKeyNameEpsilon = "epsilon";

	private Boolean useScaffold = null;
	private Boolean useTransformer = null;

	private String arguments;

	public void setArguments(final String arguments){
		this.arguments = arguments;
	}

	public String getArguments(){
		return arguments;
	}
	
	private Double epsilon = null;

	// true return means can continue. false return means that cannot continue.
	private final boolean readGlobalConfigFromConfigFile(Class<? extends AbstractAnalyzer> clazz){
		if(useScaffold == null || useTransformer == null){
			String configFilePath = Settings.getDefaultConfigFilePath(clazz);
			if(configFilePath == null){
				logger.log(Level.SEVERE, "NULL config file path for class: " + clazz);
				return false;
			}else{
				try{
					File configFile = new File(configFilePath);
					if(configFile.exists()){
						if(configFile.isFile()){
							try{
								Map<String, String> configMap = FileUtility.readConfigFileAsKeyValueMap(configFilePath, "=");
								if(useScaffold == null){
									if(!setGlobalConfigUseScaffold("config file '"+configFilePath+"'", 
											configMap.get(configKeyNameUseScaffold))){
										return false;
									}
								}
								
								if(useTransformer == null){
									if(!setGlobalConfigUseTransformer("config file '"+configFilePath+"'", 
											configMap.get(configKeyNameUseTransformer))){
										return false;
									}
								}
								
								if(epsilon == null){
									if(!setGlobalConfigEpsilon("config file '"+configFilePath+"'", 
										configMap.get(configKeyNameEpsilon))){
										return false;
									}
								}
							}catch(Exception e){
								logger.log(Level.SEVERE, "Failed to read config file at path: " + configFilePath, e);
								return false;
							}
						}else{
							logger.log(Level.SEVERE, "Config file exists but is not a file: " + configFilePath);
							return false;
						}
					}
				}catch(Exception e){
					logger.log(Level.SEVERE, "Failed to check config file at path: " + configFilePath, e);
					return false;
				}
			}
		}
		return true;
	}
	
	// true return means can continue. false return means that cannot continue.
	private final boolean setGlobalConfigUseScaffold(String valueSource, String value){
		if(value == null){
			return true;
		}else{
			Result<Boolean> result = HelperFunctions.parseBoolean(value);
			if(result.error){
				logger.log(Level.SEVERE, "Invalid boolean value for '"+configKeyNameUseScaffold + "' in " + valueSource);
				logger.log(Level.SEVERE, result.toErrorString());
				return false;
			}else{
				this.useScaffold = result.result.booleanValue();
				return true;
			}
		}
	}
	
	// true return means can continue. false return means that cannot continue.
	private final boolean setGlobalConfigUseTransformer(String valueSource, String value){
		if(value == null){
			return true;
		}else{
			Result<Boolean> result = HelperFunctions.parseBoolean(value);
			if(result.error){
				logger.log(Level.SEVERE, "Invalid boolean value for '"+configKeyNameUseTransformer + "' in " + valueSource);
				logger.log(Level.SEVERE, result.toErrorString());
				return false;
			}else{
				this.useTransformer = result.result.booleanValue();
				return true;
			}
		}
	}
	
	// true return means can continue. false return means that cannot continue.
	private final boolean setGlobalConfigEpsilon(String valueSource, String value){
		if(value == null){
			return true;
		}else{
			Result<Double> result = HelperFunctions.parseDouble(value, -1, Double.POSITIVE_INFINITY);
			if(result.error){
				logger.log(Level.SEVERE, "Invalid boolean value for '"+configKeyNameEpsilon + "' in " + valueSource);
				logger.log(Level.SEVERE, result.toErrorString());
				return false;
			}else{
				this.epsilon = result.result;
				return true;
			}
		}
	}

	public final boolean initialize(String arguments){
		Map<String, String> argsMap = HelperFunctions.parseKeyValPairs(arguments);
		
		if(!setGlobalConfigUseScaffold("Arguments", argsMap.get(configKeyNameUseScaffold))){
			return false;
		}
		
		if(!setGlobalConfigUseTransformer("Arguments", argsMap.get(configKeyNameUseTransformer))){
			return false;
		}
		
		if(!setGlobalConfigEpsilon("Arguments", argsMap.get(configKeyNameEpsilon))){
			return false;
		}
		
		if(!readGlobalConfigFromConfigFile(this.getClass())){
			return false;
		}
		
		if(!readGlobalConfigFromConfigFile(AbstractAnalyzer.class)){
			return false;
		}
		
		if(useScaffold == null){
			logger.log(Level.SEVERE, "NULL '"+configKeyNameUseScaffold+"' value. Must specify in arguments or config files");
			return false;
		}
		
		if(useTransformer == null){
			logger.log(Level.SEVERE, "NULL '"+configKeyNameUseTransformer+"' value. Must specify in arguments or config files");
			return false;
		}
		
		if(epsilon == null){
			logger.log(Level.SEVERE, "NULL '"+configKeyNameEpsilon+"' value. Must specify in arguments or config files");
			return false;
		}
		
		logger.log(Level.INFO, "Arguments: {0}={1}, {2}={3}, {4}={5}",
				new Object[]{
						configKeyNameUseScaffold, useScaffold,
						configKeyNameUseTransformer, useTransformer,
						configKeyNameEpsilon, epsilon
				});
		
		// Only here if success
		
		return this.initializeConcreteAnalyzer(arguments);
	}
	
	public abstract boolean initializeConcreteAnalyzer(String arguments);

	public abstract void shutdown();

	public abstract class QueryConnection implements Runnable{

		private static final String commandSetStorage = "set storage <storage_name>";

		protected final Logger logger = Logger.getLogger(this.getClass().getName());

		private AbstractStorage currentStorage;

		private Query getErrorSPADEQuery(){
			Query spadeQuery = new Query("<NULL>", "<NULL>", "<NULL>", "<NULL>");
			spadeQuery.queryFailed("Exiting!");
			return spadeQuery;
		}

		private void privatize(final Query spadeQuery) throws Exception{
			if(epsilon > -1){
				if(spadeQuery.getResult() instanceof spade.query.quickgrail.core.GraphStatistic){
					final spade.query.quickgrail.core.GraphStatistic graphStatistic =
						(spade.query.quickgrail.core.GraphStatistic)spadeQuery.getResult();
					try{
						graphStatistic.privatize(epsilon);
					}catch(Exception e){
						throw new Exception("Failed to privatize graph statistics", e);
					}
				}
			}
		}

		private void transformGraph(final Query spadeQuery) throws Exception{
			boolean isResultAGraph = spadeQuery != null && spadeQuery.getResult() instanceof spade.core.Graph;
			if(isResultAGraph){
				Graph finalGraph = (spade.core.Graph)spadeQuery.getResult();
				if(useTransformer){
					finalGraph = iterateTransformers(finalGraph, spadeQuery.getTransformerExecutionContext());
				}
				finalGraph.setHostName(Kernel.getHostName()); // Set it here because the graph might be modified by the transformers
				finalGraph.addSignature(spadeQuery.queryNonce);

				spadeQuery.updateGraphResult(finalGraph); // Update the query result with the transformed result graph
			}
		}

		private void handleSPADEQueryError(final Query spadeQuery){
			if(spadeQuery.getError() != null){
				// Check if exception is serializable
				try(final ObjectOutputStream oos = new ObjectOutputStream(new ByteArrayOutputStream())){
					oos.writeObject(spadeQuery.getError());
				}catch(Throwable t){
					// The exception is not serializable
					final String queryErrorString;
					logger.log(Level.SEVERE, "Non-serializable query-error", t);
					if(spadeQuery.getError() instanceof Throwable){
						queryErrorString = ((Throwable)spadeQuery.getError()).getMessage();
						logger.log(Level.SEVERE, "Query-error!", (Throwable)spadeQuery.getError());
					}else{
						queryErrorString = String.valueOf(spadeQuery.getError());
						logger.log(Level.SEVERE, "Query-error! " + spadeQuery.getError());
					}
					spadeQuery.queryFailed(new Exception("Query error: " + queryErrorString));
				}
			}
		}
		
		@Override
		public final void run(){
			while(!this.isShutdown()){
				Query spadeQuery = null;
				try{
					spadeQuery = readLineFromClient(); // overwrite existing
				}catch(EOFException eofe){
					break;
				}catch(Exception e){
					if(this.isShutdown()){
						// here because of shutdown. No error.
					}else{
						logger.log(Level.SEVERE, "Failed to read query from client", e);
					}
					safeWriteToClient(getErrorSPADEQuery());
					break;
				}
				if(spadeQuery == null){ // End of stream
					// safeWriteToClient(getErrorSPADEQuery()); // connection closed so cannot write
					break; // exit while loop
				}

				if(spadeQuery.query.trim().toLowerCase().equals("exit") || spadeQuery.query.trim().toLowerCase().equals("quit")){
					spadeQuery.querySucceeded("Exiting!");
					// safeWriteToClient(spadeQuery); // connection closed so cannot write
					break;
				}else{
					final String trimmedQuery = spadeQuery.query.trim();
					String queryTokens[] = trimmedQuery.split("\\s+", 3);
					if(queryTokens.length >= 2 && queryTokens[0].toLowerCase().equals("set")
							&& queryTokens[1].toLowerCase().equals("storage")){
						final String storageName = queryTokens.length == 3 ? queryTokens[2] : null;
						safeWriteToClient(setStorage(storageName, spadeQuery));
						continue;
					}else if(queryTokens.length == 2 && queryTokens[0].toLowerCase().equals("print")
							&& queryTokens[1].toLowerCase().equals("storage")){
						AbstractStorage currentStorage = getCurrentStorage();
						if(currentStorage == null){
							spadeQuery.querySucceeded("No current storage set");
						}else{
							spadeQuery.querySucceeded(currentStorage.getClass().getSimpleName());
						}
						safeWriteToClient(spadeQuery);
						continue;
					}else if(queryTokens[0].toLowerCase().equals("help")){
						try{
							HelpType helpType = null;
							if(queryTokens.length > 1){
								if(queryTokens.length > 2){
									throw new RuntimeException("Unexpected number of arguments to help command. Expected only one");
								}
								helpType = HelpType.valueOf(queryTokens[1].toUpperCase());
							}else{
								helpType = HelpType.ALL;
							}
							String result = getQueryHelpTextAsString(helpType);
							spadeQuery.querySucceeded(result);
						}catch(Exception e){
							spadeQuery.queryFailed(new RuntimeException("Failed to execute help command: " + e.getMessage()));
						}
						safeWriteToClient(spadeQuery);
						continue;
					}else{
						// Some other query
						AbstractStorage thisStorage = getCurrentStorage();
						if(thisStorage == null){
							spadeQuery.queryFailed("No storage set for querying. Use command: '" + commandSetStorage + "'.");
							safeWriteToClient(spadeQuery);
						}else{
							if(!Kernel.isStoragePresent(thisStorage)){
								try{
									doQueryingShutdownForCurrentStorage();
								}catch(Exception e){
									logger.log(Level.SEVERE,
											"Failed to successfully shutdown querying for the removed storage: " + "'"
													+ thisStorage.getClass().getSimpleName() + "'.",
													e);
								}
								setCurrentStorage(null);
								spadeQuery.queryFailed("Previously set storage '" + thisStorage.getClass().getSimpleName()
										+ "' has been " + "removed. Use command: '" + commandSetStorage + "'.");
								safeWriteToClient(spadeQuery);
							}else{
								// Can execute query finally
								try{
									spadeQuery = executeQuery(spadeQuery);
									
									transformGraph(spadeQuery);

									privatize(spadeQuery);
									
									handleSPADEQueryError(spadeQuery);
									
									safeWriteToClient(spadeQuery);
								}catch(Exception e){
									logger.log(Level.SEVERE, "Failed to execute query: '" + spadeQuery.query + "'", e);
									spadeQuery.queryFailed(new Exception("Failed to execute query: " + e.getMessage(), e));
									safeWriteToClient(spadeQuery);
								}
							}
						}
					}
				}
			}

			this.shutdown();

			// Exited the main loop
			AbstractStorage thisStorage = getCurrentStorage();
			if(thisStorage != null){
				try{
					doQueryingShutdownForCurrentStorage();
				}catch(Exception e){
					logger.log(Level.SEVERE, "Failed to successfully shutdown querying for the removed storage: " + "'"
							+ thisStorage.getClass().getSimpleName() + "'.", e);
				}
				setCurrentStorage(null);
			}
		}

		public abstract Query readLineFromClient() throws Exception;

		public abstract void writeToClient(Query query) throws Exception;

		public abstract void doQueryingSetupForCurrentStorage() throws Exception;

		public abstract void doQueryingShutdownForCurrentStorage() throws Exception;

		public abstract String getQueryHelpTextAsString(HelpType type) throws Exception;
		public abstract Query executeQuery(Query query) throws Exception;

		public abstract void shutdown();

		public abstract boolean isShutdown();

		private final void safeWriteToClient(Query data){
			try{
				writeToClient(data);
			}catch(Exception e){
				logger.log(Level.WARNING, "Failed to write to query client: '" + data + "'", e);
			}
		}

		private final Query setStorage(String storageName, Query spadeQuery){
			if(storageName == null){
				spadeQuery.queryFailed("Missing storage_name in command: '" + commandSetStorage + "'.");
				return spadeQuery;
			}else if(storageName.trim().isEmpty()){
				spadeQuery.queryFailed("Empty storage_name in command: '" + commandSetStorage + "'.");
				return spadeQuery;
			}else{
				AbstractStorage storage = Kernel.getStorage(storageName);
				if(storage == null){
					spadeQuery.queryFailed("Storage '" + storageName + "' not found.");
					return spadeQuery;
				}else{
					if(getCurrentStorage() != null){
						try{
							doQueryingShutdownForCurrentStorage();
						}catch(Exception e){
							logger.log(Level.SEVERE, "Failed to successfully shutdown querying for storage: " + "'"
									+ getCurrentStorage().getClass().getSimpleName() + "'.", e);
						}
						setCurrentStorage(null);
					}
					try{
						setCurrentStorage(storage);
						// Current storage set before calling the code below
						doQueryingSetupForCurrentStorage();
						spadeQuery.querySucceeded("Storage '" + storageName + "' successfully set for querying.");
						return spadeQuery;
					}catch(Exception e){
						logger.log(Level.SEVERE,
								"Failed to set storage: " + "'" + storage.getClass().getSimpleName() + "'", e);
						setCurrentStorage(null); // Undo the set storage in case of an error
						spadeQuery.queryFailed("Failed to set storage '" + storage.getClass().getSimpleName() + "'. Use command: '"
						+ commandSetStorage + "'. Error: " + e.getMessage());
						return spadeQuery;
					}
				}
			}
		}

		public synchronized final AbstractStorage getCurrentStorage(){
			return currentStorage;
		}

		public synchronized final void setCurrentStorage(AbstractStorage storage){
			this.currentStorage = storage;
		}

		private Graph iterateTransformers(Graph graph, final AbstractTransformer.ExecutionContext executionContext){
			synchronized(Kernel.transformers){
				for(int i = 0; i < Kernel.transformers.size(); i++){
					try{
						AbstractTransformer transformer = Kernel.transformers.get(i);
						final Result<Graph> executeResult = AbstractTransformer.execute(transformer, graph, executionContext);
						if(executeResult.error){
							throw new RuntimeException(executeResult.errorMessage, executeResult.exception);
						}
						graph = executeResult.result;
					}catch(Exception e){
						throw new RuntimeException("Failed to apply transformer '"
								+Kernel.transformers.get(i) == null ? "<NULL transformer>" : Kernel.transformers.get(i).getClass().getSimpleName()
								+"'", e);
					}
				}
			}
			return graph;
		}
	}

}
