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

import java.io.File;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.client.QueryMetaData;
import spade.utility.CommonFunctions;
import spade.utility.DiscrepancyDetector;
import spade.utility.FileUtility;
import spade.utility.Result;

/**
 * @author raza
 */
public abstract class AbstractAnalyzer{

	private final Logger logger = Logger.getLogger(this.getClass().getName());
	
	private static final String configKeyNameUseScaffold = "use_scaffold";
	private static final String configKeyNameUseTransformer = "use_transformer";
	
	private Boolean useScaffold = null;
	private Boolean useTransformer = null;
	
	protected AbstractResolver remoteResolver;
	/**
	 * remoteResolutionRequired is used by query module to signal the Analyzer to
	 * resolve any outstanding remote parts of result graph.
	 */
	private static boolean remoteResolutionRequired = false;

	protected static final DiscrepancyDetector discrepancyDetector = new DiscrepancyDetector();
	
	public static void d(String msg){
		Logger.getLogger(AbstractAnalyzer.class.getName()).log(Level.SEVERE,
				"{0}: {1}", new Object[]{"DBG_HASS", msg});
	}

	
	

	public static void setRemoteResolutionRequired(){
		remoteResolutionRequired = true;
	}

	public static void clearRemoteResolutionRequired(){
		remoteResolutionRequired = false;
	}

	public static boolean isRemoteResolutionRequired(){
		return remoteResolutionRequired;
	}
	
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
			Result<Boolean> result = CommonFunctions.parseBoolean(value);
			if(result.error){
				logger.log(Level.SEVERE, "Invalid boolean value for '"+configKeyNameUseScaffold + "' in " + valueSource);
				logger.log(Level.SEVERE, result.toErrorString());
				return false;
			}else{
				d("found usescaffold in " + valueSource);
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
			Result<Boolean> result = CommonFunctions.parseBoolean(value);
			if(result.error){
				logger.log(Level.SEVERE, "Invalid boolean value for '"+configKeyNameUseTransformer + "' in " + valueSource);
				logger.log(Level.SEVERE, result.toErrorString());
				return false;
			}else{
				d("found usetransformer in " + valueSource);
				this.useTransformer = result.result.booleanValue();
				return true;
			}
		}
	}

	public final boolean initialize(String arguments){
		d("In abstractanalyzer initialize");
		Map<String, String> argsMap = CommonFunctions.parseKeyValPairs(arguments);
		
		if(!setGlobalConfigUseScaffold("Arguments", argsMap.get(configKeyNameUseScaffold))){
			return false;
		}
		
		if(!setGlobalConfigUseTransformer("Arguments", argsMap.get(configKeyNameUseTransformer))){
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
		
		logger.log(Level.INFO, "Arguments: {0}={1}, {2}={3}",
				new Object[]{
						configKeyNameUseScaffold, useScaffold,
						configKeyNameUseTransformer, useTransformer
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

		@Override
		public final void run(){
			while(!this.isShutdown()){
				String query = null;
				try{
					d("going to wait for reading query");
					query = readLineFromClient();
				}catch(Exception e){
					if(this.isShutdown()){
						d("proper client shutdown");
						// here because of shutdown. No error.
					}else{
						logger.log(Level.SEVERE, "Failed to read query from client", e);
					}
					break;
				}
				d("query read: " + query);
				if(query == null){ // End of stream
					break;
				}else if(query.trim().toLowerCase().equals("exit") || query.trim().toLowerCase().equals("quit")){
					break;
				}else{
					final String trimmedQuery = query.trim();
					String queryTokens[] = trimmedQuery.split("\\s+", 3);
					if(queryTokens.length >= 2 && queryTokens[0].toLowerCase().equals("set")
							&& queryTokens[1].toLowerCase().equals("storage")){
						final String storageName = queryTokens.length == 3 ? queryTokens[2] : null;
						final String setStorageOutput = setStorage(storageName);
						safeWriteToClient(setStorageOutput);
						continue;
					}else if(queryTokens.length == 2 && queryTokens[0].toLowerCase().equals("print")
							&& queryTokens[1].toLowerCase().equals("storage")){
						AbstractStorage currentStorage = getCurrentStorage();
						if(currentStorage == null){
							safeWriteToClient("No current storage set");
						}else{
							safeWriteToClient(currentStorage.getClass().getSimpleName());
						}
						continue;
					}else{
						// Some other query
						AbstractStorage thisStorage = getCurrentStorage();
						if(thisStorage == null){
							safeWriteToClient("No storage set for querying. Use command: '" + commandSetStorage + "'.");
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
								safeWriteToClient("Previously set storage '" + thisStorage.getClass().getSimpleName()
										+ "' has been " + "removed. Use command: '" + commandSetStorage + "'.");
							}else{
								// Can execute query
								try{
									Object queryOutput = executeQuery(query);
									try{
										// TODO iterate transformers on the result
									}catch(Exception e){

									}
									safeWriteToClient(queryOutput);
								}catch(Exception e){
									logger.log(Level.SEVERE, "Failed to execute query: '" + query + "'", e);
									safeWriteToClient("Failed to execute query. Error message: " + e.getMessage());
								}
							}
						}
					}
				}
			}

			safeWriteToClient("Exiting!");
			
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

		public abstract String readLineFromClient() throws Exception;

		public abstract void writeToClient(Object data) throws Exception;

		public abstract void doQueryingSetupForCurrentStorage() throws Exception;

		public abstract void doQueryingShutdownForCurrentStorage() throws Exception;

		public abstract Object executeQuery(String query) throws Exception;

		public abstract void shutdown();

		public abstract boolean isShutdown();

		private final void safeWriteToClient(Object data){
			try{
				writeToClient(data);
			}catch(Exception e){
				logger.log(Level.WARNING, "Failed to write to query client: '" + data + "'", e);
			}
		}

		private final String setStorage(String storageName){
			if(storageName == null){
				return "Missing storage_name in command: '" + commandSetStorage + "'.";
			}else if(storageName.trim().isEmpty()){
				return "Empty storage_name in command: '" + commandSetStorage + "'.";
			}else{
				AbstractStorage storage = Kernel.getStorage(storageName);
				if(storage == null){
					return "Storage '" + storageName + "' not found.";
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
						return "Storage '" + storageName + "' successfully set for querying.";
					}catch(Exception e){
						logger.log(Level.SEVERE,
								"Failed to set storage: " + "'" + storage.getClass().getSimpleName() + "'", e);
						setCurrentStorage(null); // Undo the set storage in case of an error
						return "Failed to set storage '" + storage.getClass().getSimpleName() + "'. Use command: '"
						+ commandSetStorage + "'. Error: " + e.getMessage();
					}
				}
			}
		}

		public synchronized final AbstractStorage getCurrentStorage(){
			return currentStorage;
		}

		private synchronized final void setCurrentStorage(AbstractStorage storage){
			this.currentStorage = storage;
		}

		// TODO
		private Graph iterateTransformers(Graph graph, QueryMetaData queryMetaData){
			synchronized(Kernel.transformers){
				for(int i = 0; i < Kernel.transformers.size(); i++){
					AbstractTransformer transformer = Kernel.transformers.get(i);
					if(graph != null){
						try{
							graph = transformer.putGraph(graph, queryMetaData);
							if(graph != null){
								// commit after every transformer to enable reading without error
								graph.commitIndex();
							}
						}catch(Exception ex){
							Logger.getLogger(QueryConnection.class.getName()).log(Level.SEVERE,
									"Error in applying transformer!", ex);
						}
					}else{
						break;
					}
				}
			}
			return graph;
		}
	}

}
