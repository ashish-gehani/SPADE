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
package spade.core;

import java.io.File;
import java.lang.reflect.Constructor;
import java.text.SimpleDateFormat;
import java.util.AbstractMap.SimpleEntry;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import spade.utility.FileUtility;
import spade.utility.HelperFunctions;
import spade.utility.LogManager;

public class Unikernel{
	
	private static volatile boolean shutdown = false;
	private static volatile boolean executedExitFunction = false;
	
	private static void setupSPADELogging() throws RuntimeException{
		System.setProperty("java.util.logging.manager", spade.utility.LogManager.class.getName());
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tb %1$td, %1$tY %1$tl:%1$tM:%1$tS %1$Tp %2$s %4$s: %5$s%6$s%n");
		
        final String spadeRootKey = "spade_root";
        final String spadeRootValue = Settings.getProperty(spadeRootKey);
        
        if(spadeRootValue == null){
        	throw new RuntimeException("NULL value for SPADE root in main config file with key '"+spadeRootKey+"'");
        }
        
        final String logDirectoryPath = spadeRootValue + File.separator + "log";
        final File logDirectory = new File(logDirectoryPath);
        
        try{
        	if(!logDirectory.exists()){
        		logDirectory.mkdirs();
        	}else{ // exists
        		if(!logDirectory.isDirectory()){
    				throw new RuntimeException("SPADE log directory at path '"+logDirectory.getAbsolutePath()+"' is not a directory");
    			}
        	}
        }catch(Exception e){
        	throw new RuntimeException("Invalid SPADE log directory path", e);
        }
        
        String logFilename = System.getProperty("spade.log");
        if(logFilename == null){
        	final String logPrefix = "SPADE_";
        	final String logStartTimePattern = "MM.dd.yyyy-H.mm.ss";
        	try{
        		final Date currentDate = new java.util.Date(System.currentTimeMillis());
        		final String logStartTime = new SimpleDateFormat(logStartTimePattern).format(currentDate);
        		logFilename = logPrefix + logStartTime + ".log";
        	}catch(Exception e){
        		throw new RuntimeException("Failed to format date and time", e);
        	}
        }
        
        final String logFilePath = logDirectory.getAbsolutePath() + File.separator + logFilename;
        
        final String spadeLoggingLevelKey = "logger_level";
        String spadeLoggingLevelValue = Settings.getProperty(spadeLoggingLevelKey);
        if(spadeLoggingLevelValue == null){
        	spadeLoggingLevelValue = "ALL";
        	//throw new RuntimeException("NULL value for SPADE log level in main config file with key '"+spadeLoggingLevelKey+"'");
        }
        
        final Level level;
        
        try{
        	level = Level.parse(spadeLoggingLevelValue);
        }catch(Exception e){
        	throw new RuntimeException("Invalid SPADE logging level value in main config with key '"+spadeLoggingLevelKey+"': "
        		+ spadeLoggingLevelValue, e);
        }
        
        try{
        	final Handler logFileHandler = new FileHandler(logFilePath);
        	logFileHandler.setFormatter(new SimpleFormatter());
        	logFileHandler.setLevel(level);
			Logger.getLogger("").addHandler(logFileHandler);
        }catch(Exception e){
        	throw new RuntimeException("Failed to initialize SPADE log handler", e);
        }
	}
	
	@SuppressWarnings("unchecked")
	private static <T> SimpleEntry<T, String> mustGetSPADEModuleInstanceAndArgumentsFromConfig(
			final Logger logger,
			final String configFilePath, final Map<String, String> configMap, 
			final String spadeModuleKey, String spadeModulePackage) throws RuntimeException{
		final String moduleValue = configMap.get(spadeModuleKey);
		if(HelperFunctions.isNullOrEmpty(moduleValue)){
			final String errorMessage = "NULL/Empty value of key '"+spadeModuleKey+"' in config file '"+configFilePath+"'";
			logger.log(Level.SEVERE, errorMessage);
			throw new RuntimeException(errorMessage);
		}
		
		final String[] moduleValueTokens = moduleValue.trim().split("\\s+", 2); // max two
		final String moduleName = moduleValueTokens[0];
		final String moduleArguments = (moduleValueTokens.length > 1) ? moduleValueTokens[1] : "";
		
		final Class<? extends T> moduleClass;
		final String qualifiedModuleName = spadeModulePackage + "." + moduleName;
		
		try{
			moduleClass = (Class<? extends T>)Class.forName(qualifiedModuleName);
		}catch(Throwable e){
			String errorMessage = "Invalid qualified name '"+qualifiedModuleName+"' from key '"+spadeModuleKey+"'";
			logger.log(Level.SEVERE, errorMessage, e);
			throw new RuntimeException(errorMessage, e);
		}
		
		final T moduleInstance;
		
		try{
			final Constructor<T> emptyConstructor = (Constructor<T>)moduleClass.getDeclaredConstructor();
			moduleInstance = emptyConstructor.newInstance();
		}catch(Throwable e){
			String errorMessage = "Failed to create '"+qualifiedModuleName+"' object from key '"+spadeModuleKey+"'";
			logger.log(Level.SEVERE, errorMessage, e);
			throw new RuntimeException(errorMessage, e);
		}
		
		return new SimpleEntry<T, String>(moduleInstance, moduleArguments);
	}
	
	public static void main(final String[] args) throws RuntimeException{
		// Initialize logging
		try{
			setupSPADELogging();
		}catch(Throwable t){
			throw new RuntimeException("Failed to initialize SPADE logger", t);
		}
		final Logger logger = Logger.getLogger(Unikernel.class.getName());
		
        /// Read config
		final String configFilePath = Settings.getDefaultConfigFilePath(Unikernel.class);
		final Map<String, String> configMap = new HashMap<String, String>();
		try{
			configMap.putAll(FileUtility.readConfigFileAsKeyValueMap(configFilePath, "="));
		}catch(Throwable e){
			final String errorMessage = "Failed to read config file '"+configFilePath+"'";
			logger.log(Level.SEVERE, errorMessage, e);
			throw new RuntimeException(errorMessage, e);
		}

		/// Setup storage
		final SimpleEntry<AbstractStorage, String> storageResult = 
				mustGetSPADEModuleInstanceAndArgumentsFromConfig(logger, configFilePath, configMap, "storage", "spade.storage");
		
		final AbstractStorage storageInstance = storageResult.getKey();
		final String storageArguments = storageResult.getValue();
		
		try{
			logger.log(Level.INFO, "Adding storage '"+storageInstance.getClass().getSimpleName()+"' ... ");
			if(!storageInstance.initialize(storageArguments)){
				throw new RuntimeException("Failed to initialize storage");
			}else{
				logger.log(Level.INFO, "Storage arguments: '"+storageArguments+"'");
				logger.log(Level.INFO, "Storage '"+storageInstance.getClass().getSimpleName()+"' added successfully");
			}
		}catch(Throwable e){
			final String errorMessage = "Error in initializing storage '"+storageInstance.getClass().getSimpleName()+"'";
			logger.log(Level.SEVERE, errorMessage, e);
			throw new RuntimeException(errorMessage, e);
		}
		
		/// Create the buffer between the storage and the reporter
		final Buffer buffer = new Buffer();
		
		/// Start storage thread
		final long storageThreadSleepWait = 10;
		final int bufferBatch = 1000000;
		final Thread storageThread = new Thread(
				new Runnable(){
					public void run(){
						while(!shutdown || !buffer.isEmpty()){
							for(int i = 0; i < bufferBatch; i++){
								final int bufferSize;
								synchronized(buffer){
									bufferSize = buffer.size();
								}
								switch(bufferSize){
									case 0: break; // Empty so no need to continue till end of batchBuffer
									default:{
										final Object bufferElement;
										synchronized(buffer){
											bufferElement = buffer.getBufferElement();
										}
										if(bufferElement instanceof AbstractVertex){
											try{
												storageInstance.putVertex((AbstractVertex)bufferElement);
												storageInstance.vertexCount++;
											}catch(Throwable e){
												logger.log(Level.WARNING, "Failed to put vertex in storage: " + bufferElement);
											}
										}else if(bufferElement instanceof AbstractEdge){
											try{
												storageInstance.putEdge((AbstractEdge)bufferElement);
												storageInstance.edgeCount++;
											}catch(Throwable e){
												logger.log(Level.WARNING, "Failed to put edge in storage: " + bufferElement);
											}
										}
									}
									break;
								}
							}
							HelperFunctions.sleepSafe(storageThreadSleepWait);
						}
						// We are shutting down
						try{
							logger.log(Level.INFO, "Shutting down storage '"+storageInstance.getClass().getSimpleName()+"' ...");
							storageInstance.shutdown();
							logger.log(Level.INFO, "Successfully shut down storage '"+storageInstance.getClass().getSimpleName()+"'. (Vertices="+storageInstance.getVertexCount()+", Edges="+storageInstance.getEdgeCount()+")");
						}catch(Throwable t0){
							logger.log(Level.SEVERE, "Failed to shutdown storage cleanly", t0);
						}
					}
				},
				"storage-thread");
		
		/// Setup reporter
		final SimpleEntry<AbstractReporter, String> reporterResult = 
				mustGetSPADEModuleInstanceAndArgumentsFromConfig(logger, configFilePath, configMap, "reporter", "spade.reporter");
		
		final AbstractReporter reporterInstance = reporterResult.getKey();
		final String reporterArguments = reporterResult.getValue();
		
		// Start the storage thread after reporter is verified
		storageThread.start();
		// Register shutdown thread
		Runtime.getRuntime().addShutdownHook(new Thread(){
			@Override
			public void run(){
				// We are shutting down
				// Try to shutdown reporter first and then set the shutdown variable to true which would shutdown the storage after consuming the buffer completely
				try{
					logger.log(Level.INFO, "Shutting down reporter '"+reporterInstance.getClass().getSimpleName()+"' ...");
					reporterInstance.shutdown();
					logger.log(Level.INFO, "Successfully shut down reporter '"+reporterInstance.getClass().getSimpleName()+"'");
				}catch(Throwable t0){
					logger.log(Level.SEVERE, "Failed to shutdown reporter cleanly", t0);
				}
				shutdown = true;
				// Wait for storage thread to exit after the buffer is flushed
				waitForThreadAndThenExit(logger, storageThread);
			}
		});
		
		reporterInstance.setBuffer(buffer);
		
		try{
			logger.log(Level.INFO, "Adding reporter '"+reporterInstance.getClass().getSimpleName()+"' ... ");
			if(!reporterInstance.launch(reporterArguments)){
				throw new RuntimeException("Failed to launch reporter");
			}else{
				logger.log(Level.INFO, "Reporter arguments: '"+reporterArguments+"'");
				logger.log(Level.INFO, "Reporter '"+reporterInstance.getClass().getSimpleName()+"' added successfully");
			}
		}catch(Throwable e){
			final String errorMessage = "Error in initializing reporter '"+reporterInstance.getClass().getSimpleName()+"'";
			logger.log(Level.SEVERE, errorMessage, e);
			
			// Do cleanup of storage before exiting
			shutdown = true;
			waitForThreadAndThenExit(logger, storageThread);
			throw new RuntimeException(errorMessage, e);
		}
		
		// Continue running until the storage thread is running
		waitForThreadAndThenExit(logger, storageThread);
	}
	
	// Cleanup function
	private synchronized static void waitForThreadAndThenExit(final Logger logger, final Thread thread){
		if(!executedExitFunction){
			try{
				thread.join();
			}catch(Throwable t0){
				logger.log(Level.SEVERE, "Failed to successfully wrap-up storage thread", t0);
			}
			executedExitFunction = true;
			LogManager.shutdownReset();
		}
	}
}
