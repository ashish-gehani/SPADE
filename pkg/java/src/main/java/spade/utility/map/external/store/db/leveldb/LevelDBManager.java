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
package spade.utility.map.external.store.db.leveldb;

import static org.fusesource.leveldbjni.JniDBFactory.factory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;

import spade.utility.HelperFunctions;
import spade.utility.FileUtility;
import spade.utility.Result;
import spade.utility.map.external.store.db.DatabaseArgument;
import spade.utility.map.external.store.db.DatabaseHandle;
import spade.utility.map.external.store.db.DatabaseManager;

/**
 * LevelDB implementation of DatabaseManager
 * Singleton
 */
public class LevelDBManager extends DatabaseManager{

	private static final Logger logger = Logger.getLogger(LevelDBManager.class.getName());
	
	public static final LevelDBManager instance = new LevelDBManager();
	private LevelDBManager(){}
	
	/**
	 * A map of currently opened database handles.
	 * Map from directory path to database handle.
	 */
	private final Map<String, LevelDBHandle> databases = new HashMap<String, LevelDBHandle>();
	
	/**
	 * Create LevelDBArgument.
	 * Sample: "databasePath=<dirPath> deleteOnClose=<true/false>"
	 * 
	 * If 'databasePath' directory does not exist then it must be creatable.
	 * 'deleteOnClose' would delete the database on database close if true.
	 * 
	 * @param arguments See above sample
	 */
	@Override
	public synchronized Result<DatabaseArgument> parseArgument(String arguments){
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
	 * Create LevelDBArgument.
	 * Must contains valid values for keys: 'databasePath', 'deleteOnClose'.
	 * All values must be non-null and non-empty.
	 * 
	 * If 'databasePath' directory does not exist then it must be creatable.
	 * 'deleteOnClose' would delete the database on database close if true.
	 */
	@Override
	public synchronized Result<DatabaseArgument> parseArgument(Map<String, String> arguments){
		if(arguments == null){
			return Result.failed("NULL arguments map");
		}else if(arguments.isEmpty()){
			return Result.failed("Empty arguments map");
		}else{
			final String dbPathUser = arguments.get(LevelDBArgument.keyDatabasePath);
			if(HelperFunctions.isNullOrEmpty(dbPathUser)){
				return Result.failed("NULL/Empty '"+LevelDBArgument.keyDatabasePath+"'");
			}else{
				Result<Boolean> deleteDbOnCloseResult = HelperFunctions.parseBoolean(
						arguments.get(LevelDBArgument.keyDeleteDbOnClose)
						);
				if(deleteDbOnCloseResult.error){
					return Result.failed("Failed to parse '"+LevelDBArgument.keyDeleteDbOnClose+"'", deleteDbOnCloseResult);
				}else{
					final boolean deleteDbOnClose = deleteDbOnCloseResult.result;
					return Result.successful(new LevelDBArgument(dbPathUser, deleteDbOnClose));
				}
			}
		}
	}
	
	/**
	 * Validates the passed argument as the correct argument for this database manager
	 * 
	 * @param genericArgument DatabaseArgument must be LevelDBArgument
	 * @return LevelDBArgument object otherwise error
	 */
	private synchronized Result<LevelDBArgument> validateArguments(final DatabaseArgument genericArgument){
		if(genericArgument == null){
			return Result.failed("NULL");
		}else if(!genericArgument.getClass().equals(LevelDBArgument.class)){
			return Result.failed("Database argument class must be LevelDBArgument but is '"+genericArgument.getClass()+"'");
		}else{
			LevelDBArgument argument = (LevelDBArgument)genericArgument;
			if(HelperFunctions.isNullOrEmpty(argument.databasePath)){
				return Result.failed("Must specify database path");
			}else{
				return Result.successful(argument);
			}
		}
	}

	/**
	 * If 'databasePath' directory does not exist then it is created.
	 * 
	 * Database at path 'databasePath' must not be in use already.
	 * 
	 * @param genericArgument DatabaseArgument must be LevelDBArgument
	 * @return The LevelDBHandle created from DatabaseArgument or error
	 */
	@Override
	public synchronized Result<DatabaseHandle> createHandleFromArgument(final DatabaseArgument genericArgument){
		Result<LevelDBArgument> valid = validateArguments(genericArgument);
		if(valid.error){
			return Result.failed("Failed database handle open", valid);
		}else{
			LevelDBArgument argument = valid.result;
			final String dbPathUnresolved = argument.databasePath;
			Result<String> canonicalResult = FileUtility.getCanonicalPathResult(dbPathUnresolved);
			if(canonicalResult.error){
				return Result.failed("Failed to get canonical path for '"+dbPathUnresolved+"'", canonicalResult);
			}else{
				final String dbPathCanonical = canonicalResult.result;
				if(databases.get(dbPathCanonical) != null){
					return Result.failed("Database already in opened and in use");
				}else{
					Result<Boolean> existsResult = FileUtility.doesPathExistResult(dbPathCanonical);
					if(existsResult.error){
						return Result.failed("Failed to check if database path exists", existsResult);
					}else{
						boolean dirCreated = false;
						if(!existsResult.result){
							Result<Boolean> createDirResult = FileUtility.createDirectoriesResult(dbPathCanonical);
							if(createDirResult.error){
								return Result.failed("Failed to create database path", createDirResult);
							}else{
								if(!createDirResult.result){
									return Result.failed("Silently failed to create database path");
								}else{
									dirCreated = true;
								}
							}
						}else{ // already exists
							Result<Boolean> isDirectoryResult = FileUtility.isDirectoryResult(dbPathCanonical);
							if(isDirectoryResult.error){
								return Result.failed("Failed to check if database path is a directory", isDirectoryResult);
							}else{
								if(!isDirectoryResult.result){
									return Result.failed("Database path is not a directory");
								}
							}
						}
						final boolean deleteOnClose = argument.deleteOnClose;
						try{
							Options options = new Options().createIfMissing(true);
							DB db = factory.open(new File(dbPathCanonical), options);
							if(db == null){
								if(dirCreated){
									try{
										FileUtils.forceDelete(new File(dbPathCanonical));
									}catch(Exception e){
										
									}
								}
								return Result.failed("Silently failed to open database at path: '"+dbPathCanonical+"'");
							}else{
								LevelDBHandle dbHandle = new LevelDBHandle(dbPathCanonical, deleteOnClose, db);
								databases.put(dbPathCanonical, dbHandle);
								return Result.successful(dbHandle);
							}
						}catch(Exception e){
							if(dirCreated){
								try{
									FileUtils.forceDelete(new File(dbPathCanonical));
								}catch(Exception e2){
									
								}
							}
							return Result.failed("Failed to create database handle", e, null);
						}
					}
				}
			}
		}
	}

	/**
	 * Optimistic. Cleans whatever it can while ignoring errors.
	 * 
	 * Close the database.
	 * Delete the database if deleteOnClose set to 'true'.
	 * Free the handle for this database path.
	 * 
	 * @param genericDbHandle Must be LevelDBHandle
	 * @return true/false on successful. Error on unrecoverable error.
	 */
	@Override
	public synchronized Result<Boolean> closeHandle(final DatabaseHandle genericDbHandle){
		if(genericDbHandle == null){
			return Result.failed("NULL database handle");
		}else if(!genericDbHandle.getClass().equals(LevelDBHandle.class)){
			return Result.failed("Database handle class mismatch for LevelDBHandle: '"+genericDbHandle.getClass()+"'");
		}else{
			LevelDBHandle dbHandle = (LevelDBHandle)genericDbHandle;
			final String dbPath = dbHandle.dbPath;
			final boolean deleteOnClose = dbHandle.deleteOnClose;
			
			databases.remove(dbPath);
			
			boolean succeeded = true;
			
			final DB db = dbHandle.getDb();
			if(db != null){
				try{
					db.close();
				}catch(Exception e){
					succeeded = false;
					logger.log(Level.WARNING, "Failed to close LevelDB handle at path: '"+dbPath+"'", e);
				}
			}
			if(deleteOnClose){
				try{
					factory.destroy(new File(dbPath), new Options());
				}catch(Exception e){
					succeeded = false;
					logger.log(Level.WARNING, "Failed to delete LevelDB handle at path: '"+dbPath+"'", e);
				}
			}
			return Result.successful(succeeded);
		}
	}

	/**
	 * Closes all databases one by one.
	 */
	@Override
	public synchronized void shutdown(){
		Map<String, LevelDBHandle> copy = new HashMap<String, LevelDBHandle>(databases);
		for(Map.Entry<String, LevelDBHandle> entries : copy.entrySet()){
			LevelDBHandle databaseHandle = entries.getValue();
			closeHandle(databaseHandle);
		}
		databases.clear();
	}
	
}
