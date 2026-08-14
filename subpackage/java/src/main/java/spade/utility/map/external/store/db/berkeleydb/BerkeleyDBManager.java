/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2019 SRI International

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
package spade.utility.map.external.store.db.berkeleydb;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;

import spade.utility.HelperFunctions;
import spade.utility.FileUtility;
import spade.utility.Result;
import spade.utility.map.external.store.db.DatabaseArgument;
import spade.utility.map.external.store.db.DatabaseHandle;
import spade.utility.map.external.store.db.DatabaseManager;

/**
 * BerkeleyDB implementation of DatabaseManager
 * Singleton
 */
public class BerkeleyDBManager extends DatabaseManager{

	public final static BerkeleyDBManager instance = new BerkeleyDBManager();
	private BerkeleyDBManager(){}

	/**
	 * A map of currently opened environment handles.
	 * Unlike BerkeleyDB, one environment can be opened only once.
	 * Map from directory path to environment handle.
	 */
	private final Map<String, BerkeleyDBEnvironmentHandle> environmentHandles = new HashMap<String, BerkeleyDBEnvironmentHandle>();

	/**
	 * Create BerkeleyDBArgument.
	 * Sample: "environmentPath=<dirPath> dbName=<dbName> deleteOnClose=<true/false>"
	 * 
	 * If 'environmentPath' directory does not exist then it must be creatable.
	 * 'dbName' must not be in use for the given 'environmentPath' in the current JVM process.
	 * 'deleteOnClose' would delete the database only (not the environment) on database close.
	 * 
	 * @param arguments See above sample
	 */
	@Override
	public synchronized Result<DatabaseArgument> parseArgument(final String arguments){
		if(HelperFunctions.isNullOrEmpty(arguments)){
			return Result.failed("NULL/Empty arguments");
		}else{
			Result<HashMap<String, String>> mapResult = HelperFunctions.parseKeysValuesInString(arguments);
			if(mapResult.error){
				return Result.failed("Failed to parse arguments to map", mapResult);
			}else{
				return parseArgument(mapResult.result);
			}
		}
	}
	
	/**
	 * Create BerkeleyDBArgument.
	 * Must contains valid values for keys: 'environmentPath', 'dbName', 'deleteOnClose'.
	 * All values must be non-null and non-empty.
	 * 
	 * If 'environmentPath' directory does not exist then it must be creatable.
	 * 'dbName' must not be in use for the given 'environmentPath' in the current JVM process.
	 * 'deleteOnClose' would delete the database only (not the environment) on database close.
	 */
	@Override
	public synchronized Result<DatabaseArgument> parseArgument(final Map<String, String> arguments){
		if(arguments == null){
			return Result.failed("NULL arguments map");
		}else if(arguments.isEmpty()){
			return Result.failed("Empty arguments map");
		}else{
			final String envPathUser = arguments.get(BerkeleyDBArgument.keyEnvironmentPath);
			if(HelperFunctions.isNullOrEmpty(envPathUser)){
				return Result.failed("NULL/Empty '"+BerkeleyDBArgument.keyEnvironmentPath+"'");
			}else{
				final String dbName = arguments.get(BerkeleyDBArgument.keyDatabaseName);
				if(HelperFunctions.isNullOrEmpty(dbName)){
					return Result.failed("NULL/Empty '"+BerkeleyDBArgument.keyDatabaseName+"'");
				}else{
					Result<Boolean> deleteDbOnCloseResult = HelperFunctions.parseBoolean(
							arguments.get(BerkeleyDBArgument.keyDeleteDbOnClose)
							);
					if(deleteDbOnCloseResult.error){
						return Result.failed("Failed to parse '"+BerkeleyDBArgument.keyDeleteDbOnClose+"'", deleteDbOnCloseResult);
					}else{
						final boolean deleteDbOnClose = deleteDbOnCloseResult.result;
						return Result.successful(new BerkeleyDBArgument(envPathUser, dbName, deleteDbOnClose));
					}
				}
			}
		}
	}

	/**
	 * Validates the passed argument as the correct argument for this database manager
	 * 
	 * @param genericArgument DatabaseArgument must be BerkeleyDBArgument
	 * @return BerkeleyDBArgument object otherwise error
	 */
	private synchronized Result<BerkeleyDBArgument> validateArguments(final DatabaseArgument genericArgument){
		if(genericArgument == null){
			return Result.failed("NULL");
		}else if(!genericArgument.getClass().equals(BerkeleyDBArgument.class)){
			return Result.failed("Database argument class must be BerkeleyDBArgument but is '"+genericArgument.getClass()+"'");
		}else{
			BerkeleyDBArgument argument = (BerkeleyDBArgument)genericArgument;
			if(HelperFunctions.isNullOrEmpty(argument.environmentPath) || 
					HelperFunctions.isNullOrEmpty(argument.dbName)){
				return Result.failed("Must specify environment path and database name");
			}else{
				return Result.successful(argument);
			}
		}
	}
	
	/**
	 * Does the cleanup in case the createHandleFromArgument fails in the middle.
	 * 
	 * If environment handle was created for this call then close it.
	 * If environment directory was created for this then delete that directory.
	 * 
	 * ANYTHING can be NULL
	 * 
	 * @param envPath environment path
	 * @param envPathCreated environment path directory created or not
	 * @param envHandle environment handle
	 * @param newEnvHandle environment handle created or not
	 */
	private synchronized void _openDatabaseHandleCleanup(String envPath, boolean envPathCreated,
			BerkeleyDBEnvironmentHandle envHandle, boolean newEnvHandle){
		if(envHandle != null){
			if(newEnvHandle){
				try{
					envHandle.getEnvironment().close();
				}catch(Exception e){
					
				}
			}
		}
		if(!HelperFunctions.isNullOrEmpty(envPath)){
			if(envPathCreated){
				try{
					FileUtils.forceDelete(new File(envPath));
				}catch(Exception e){
					
				}
			}
		}
	}
	
	/**
	 * If 'environmentPath' directory does not exist then it is created (also marked as deletable since created just now).
	 * If an environment handle not opened with the path 'environmentPath' then a new one created.
	 * Use existing environment and environment handle if already present.
	 * 
	 * Database with name 'dbName' must not be in use already.
	 * Create database with name 'dbName' (if does not exist) in the given environment.
	 * 
	 * @param genericArgument DatabaseArgument must be BerkeleyDBArgument
	 * @return The BerkeleyDBHandle created from DatabaseArgument or error
	 */
	@Override
	public synchronized Result<DatabaseHandle> createHandleFromArgument(final DatabaseArgument genericArgument){
		Result<BerkeleyDBArgument> validResult = validateArguments(genericArgument);
		if(validResult.error){
			return Result.failed("Invalid argument", validResult);
		}else{
			BerkeleyDBArgument argument = validResult.result;
			final String envPathUser = argument.environmentPath;
			final String dbName = argument.dbName;
			final boolean deleteOnClose = argument.deleteOnClose;
			
			Result<String> envPathResult = FileUtility.getCanonicalPathResult(envPathUser);
			if(envPathResult.error){
				return Result.failed("Failed to get canonical path for '"+envPathUser+"'", envPathResult);
			}else{
				final String envPath = envPathResult.result;
				BerkeleyDBEnvironmentHandle envHandle = environmentHandles.get(envPath);
				boolean envPathCreated = false;
				boolean newEnvHandle = false;
				
				if(envHandle == null){
					Result<Boolean> pathExistsResult = FileUtility.doesPathExistResult(envPath);
					if(pathExistsResult.error){
						return Result.failed("Failed to check if environment directory exists", pathExistsResult);
					}else{
						if(!pathExistsResult.result){
							Result<Boolean> createDirectoriesResult = FileUtility.createDirectoriesResult(envPath);
							if(createDirectoriesResult.error){
								return Result.failed("Failed to create environment directory", createDirectoriesResult);
							}else{
								if(!createDirectoriesResult.result){
									return Result.failed("Silently failed to create environment directory: '"+envPath+"'");
								}else{
									envPathCreated = true;
									Result<Environment> envResult = createBerkeleyDBEnvironmentObject(envPath);
									if(envResult.error){
										_openDatabaseHandleCleanup(envPath, envPathCreated, null, false);
										return Result.failed("Failed", envResult);
									}else{
										newEnvHandle = true;
										envHandle = new BerkeleyDBEnvironmentHandle(envPath, envResult.result, true);
									}
								}
							}
						}else{
							Result<Boolean> isDirectoryResult = FileUtility.isDirectoryResult(envPath);
							if(isDirectoryResult.error){
								return Result.failed("Failed to check if environment directory is a directory", isDirectoryResult);
							}else{
								if(!isDirectoryResult.result){
									return Result.failed("Environment path must be a directory");
								}else{
									Result<Environment> envResult = createBerkeleyDBEnvironmentObject(envPath);
									if(envResult.error){
										return Result.failed("Failed", envResult);
									}else{
										newEnvHandle = true;
										envHandle = new BerkeleyDBEnvironmentHandle(envPath, envResult.result, false);
									}
								}
							}
						}
					}
				}
				
				Result<Boolean> handleExistsResult = envHandle.containsDBHandleWithName(dbName);
				if(handleExistsResult.error){
					_openDatabaseHandleCleanup(envPath, envPathCreated, envHandle, newEnvHandle);
					return Result.failed("Failed to check if database handle exists", handleExistsResult);
				}else{
					if(handleExistsResult.result){
						_openDatabaseHandleCleanup(envPath, envPathCreated, envHandle, newEnvHandle);
						return Result.failed("Database already opened");
					}else{
						Result<Boolean> dbExistsResult = envHandle.containsDatabaseInEnvironmentWithName(dbName);
						if(dbExistsResult.error){
							_openDatabaseHandleCleanup(envPath, envPathCreated, envHandle, newEnvHandle);
							return Result.failed("Failed to check if database exists in environment", dbExistsResult);
						}else{
							boolean createDb = !dbExistsResult.result;
							Result<Database> dbResult = createOpenDatabase(envHandle.getEnvironment(), dbName, createDb);
							if(dbResult.error){
								_openDatabaseHandleCleanup(envPath, envPathCreated, envHandle, newEnvHandle);
								return Result.failed("Failed to create database object", dbResult);
							}else{
								Database database = dbResult.result;
								BerkeleyDBHandle dbHandle = new BerkeleyDBHandle(dbName, database, envHandle, deleteOnClose);
								envHandle.putDBHandle(dbHandle);
								
								if(newEnvHandle){
									environmentHandles.put(envPath, envHandle);
								}
								
								return Result.successful(dbHandle);
							}
						}
					}
				}
			}
		}
	}
	
	/**
	 * Creates BerkeleyDB database object 
	 * 
	 * @param environment BerkeleyDB environment object
	 * @param databaseName name of the database
	 * @param create true - must create database, false - database must already exist.
	 * @return BerkeleyDB database object or error
	 */
	private synchronized Result<Database> createOpenDatabase(Environment environment, String databaseName, boolean create){
		DatabaseConfig databaseConfig = new DatabaseConfig();
		databaseConfig.setAllowCreate(create).setExclusiveCreate(create);
		try{
			Database database = environment.openDatabase(null, databaseName, databaseConfig);
			return Result.successful(database);
		}catch(Exception e){
			return Result.failed("Failed to create database", e, null);
		}
	}
	
	/**
	 * Creates BerkeleyDB environment object
	 * 
	 * @param path environment path which must already exist
	 * @return BerkeleyDB environment object or error
	 */
	private synchronized Result<Environment> createBerkeleyDBEnvironmentObject(String path){
		try{
			EnvironmentConfig envConfig = new EnvironmentConfig();
			envConfig.setAllowCreate(true);
			Environment environment = new Environment(new File(path), envConfig);
			return Result.successful(environment);
		}catch(Exception e){
			return Result.failed("Failed to create BerkeleyDB environment object", e, null);
		}
	}

	/**
	 * Optimistic. Cleans whatever it can while ignoring errors.
	 * 
	 * Close the database.
	 * Delete the database if deleteOnClose set to 'true'.
	 * Free the handle for this database name.
	 * If no more database handles opened for the environment then environment closed, deleted (if created and is empty), and handle freed.
	 * 
	 * 
	 * @param genericDbHandle Must be BerkeleyDBHandle
	 * @return true/false on successful. Error on unrecoverable error.
	 */
	@Override
	public synchronized Result<Boolean> closeHandle(final DatabaseHandle genericDbHandle){
		return _closeDatabaseHandle(genericDbHandle);
	}
	
	// Optimistic
	private synchronized Result<Boolean> _closeDatabaseHandle(final DatabaseHandle genericDbHandle){
		if(genericDbHandle != null){
			if(!genericDbHandle.getClass().equals(BerkeleyDBHandle.class)){
				return Result.failed("Database handle class must be DBHandle but is '"+genericDbHandle.getClass()+"'");
			}
			
			BerkeleyDBHandle dbHandle = (BerkeleyDBHandle)genericDbHandle;
			final Database db = dbHandle.getDatabase();
			if(db != null){
				try{
					db.close();
				}catch(Exception e){
					
				}
			}
			final String dbName = dbHandle.dbName;
			final boolean deleteDbOnClose = dbHandle.deleteOnClose;
			
			final BerkeleyDBEnvironmentHandle envHandle = dbHandle.getEnvironmentHandle();
			if(envHandle != null){
				envHandle.getDbHandles().remove(dbName);
				
				final Environment env = envHandle.getEnvironment();
				if(env != null){
					if(deleteDbOnClose){
						try{
							env.removeDatabase(null, dbName);
						}catch(Exception e){
							
						}
					}
				}
				
				final boolean envDeletable = envHandle.deletable;
				
				Result<ArrayList<String>> handleListResult = envHandle.getDatabaseHandleNames();
				if(!handleListResult.error){
					if(handleListResult.result.isEmpty()){
						environmentHandles.remove(envHandle.directoryPath);
						try{
							env.close();
						}catch(Exception e){
							
						}
						if(envDeletable){
							Result<ArrayList<String>> dbInEnvResult = envHandle.getDatabaseNamesInEnvironment();
							if(!dbInEnvResult.error){
								if(dbInEnvResult.result.isEmpty()){
									try{
										FileUtils.forceDelete(new File(envHandle.directoryPath));
									}catch(Exception e){
										
									}
								}
							}
						}
					}
				}
			}
			return Result.successful(true);
		}else{
			return Result.failed("NULL database handle");
		}
	}
	
	/**
	 * Closes all databases, and environment one by one.
	 */
	@Override
	public synchronized void shutdown(){
		Result<Boolean> error = null;
		Map<String, BerkeleyDBEnvironmentHandle> envHandlesCopy = new HashMap<String, BerkeleyDBEnvironmentHandle>(environmentHandles);
		for(Map.Entry<String, BerkeleyDBEnvironmentHandle> entry : envHandlesCopy.entrySet()){
			String envPath = entry.getKey();
			BerkeleyDBEnvironmentHandle envHandle = entry.getValue();
			if(envHandle == null){
				error = Result.failed("NULL environment handle with path: '"+envPath+"'", error);
			}else{
				Map<String, BerkeleyDBHandle> dbHandlesCopy = new HashMap<String, BerkeleyDBHandle>(envHandle.getDbHandles());
				for(Map.Entry<String, BerkeleyDBHandle> dbEntry : dbHandlesCopy.entrySet()){
					String dbName = dbEntry.getKey();
					BerkeleyDBHandle dbHandle = dbEntry.getValue();
					if(dbHandle == null){
						error = Result.failed("NULL database handle with name: '"+dbName+"'", error);
					}else{
						_closeDatabaseHandle(dbHandle);
					}
				}
				envHandle.getDbHandles().clear();
			}
		}
		environmentHandles.clear();
	}
	
	/**
	 * Print the databases in the environment at the path specified
	 * 
	 * @param envPath path of the environment on disk
	 */
	private static void statEnvRaw(String envPath){
		if(envPath != null){
			Environment env = null;
			try{
				env = new Environment(new File(envPath), null);
				
				System.out.println(
						String.format("envPath=%s, dbsOnDisk=%s", 
								envPath, env.getDatabaseNames()
								));
				
			}catch(Exception e){
				e.printStackTrace(System.err);
			}finally{
				if(env != null){
					try{
						env.close();
					}catch(Exception e){
						e.printStackTrace(System.err);
					}
				}
			}
		}
	}
	
	/**
	 * To check the status of a BerkeleyDB environment
	 * 
	 * @param args paths of directories
	 */
	public static void main(String[] args){
		if(args.length > 0){
			for(String arg : args){
				statEnvRaw(arg);
			}
		}
	}
}