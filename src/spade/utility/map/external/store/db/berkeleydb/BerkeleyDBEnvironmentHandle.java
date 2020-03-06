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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.sleepycat.je.Environment;

import spade.utility.HelperFunctions;
import spade.utility.Result;

/**
 * BerkeleyDB environment handle managed by the BerkeleyDBManager
 */
public class BerkeleyDBEnvironmentHandle{
	
	/**
	 * Path on disk for the BerkeleyDB environment
	 */
	protected final String directoryPath; // Unique
	/**
	 * Whether the directory path for BerkeleyDB environment is deletable or not.
	 * It is only deletable if it was created by the current JVM instance and if there are no
	 * undeleted databases in this environment.
	 */
	protected final boolean deletable;
	/**
	 * BerkeleyDB environment object
	 */
	private final Environment environment;
	/**
	 * Map of currently opened BerkeleyDB database handles
	 * Map from the database name to the BerkeleyDB database handle
	 */
	private final Map<String, BerkeleyDBHandle> dbHandles = new HashMap<String, BerkeleyDBHandle>();

	/**
	 * @param directoryPath Path of the BerkeleyDB environment on disk
	 * @param environment BerkeleyDB environment object
	 * @param deletable Only 'true' if the directory for the BerkeleyDB environment was created by this JVM instance
	 */
	protected BerkeleyDBEnvironmentHandle(String directoryPath, Environment environment, boolean deletable){
		this.deletable = deletable;
		this.directoryPath = directoryPath;
		this.environment = environment;
	}
	
	protected Environment getEnvironment(){
		return environment;
	}
	
	protected Map<String, BerkeleyDBHandle> getDbHandles(){
		return dbHandles;
	}

	/**
	 * Put a new BerkeleyDB database handle and must not exist already
	 * 
	 * @param dbHandle BerkeleyDB database handle
	 * @return Result with true or with error
	 */
	protected synchronized Result<Boolean> putDBHandle(BerkeleyDBHandle dbHandle){
		if(dbHandle == null){
			return Result.failed("NULL database handle");
		}else{
			String dbName = dbHandle.dbName;
			if(HelperFunctions.isNullOrEmpty(dbName)){
				return Result.failed("NULL/Empty database name in database handle");
			}else{
				if(dbHandles.get(dbName) != null){
					return Result.failed("Database handle already exists with name: '"+dbName+"'");
				}else{
					dbHandles.put(dbName, dbHandle);
					return Result.successful(true);
				}
			}
		}
	}

	/**
	 * Check if a database handle with the given name is already opened
	 * 
	 * @param dbName name of the database
	 * @return Result with true/false or with error
	 */
	protected synchronized Result<Boolean> containsDBHandleWithName(String dbName){
		if(HelperFunctions.isNullOrEmpty(dbName)){
			return Result.failed("NULL/Empty database name");
		}else{
			if(dbHandles.get(dbName) == null){
				return Result.successful(false);
			}else{
				return Result.successful(true);
			}
		}
	}

	/**
	 * Check if a database exists in the environment on disk. Doesn't check if the database is opened or not
	 * 
	 * @param dbName name of the database
	 * @return Result with true/false or with error
	 */
	protected synchronized Result<Boolean> containsDatabaseInEnvironmentWithName(String dbName){
		if(HelperFunctions.isNullOrEmpty(dbName)){
			return Result.failed("NULL/Empty database name");
		}else{
			Result<ArrayList<String>> databaseNamesResult = getDatabaseNamesInEnvironment();
			if(databaseNamesResult.error){
				return Result.failed("Failed to check if environment contains the database with name: '"+dbName+"'", databaseNamesResult);
			}else{
				ArrayList<String> databaseNames = databaseNamesResult.result;
				return Result.successful(databaseNames.contains(dbName));
			}
		}
	}

	/**
	 * Returns the list of databases in the environment on the disk
	 * 
	 * @return Result with list of database name or error
	 */
	protected synchronized Result<ArrayList<String>> getDatabaseNamesInEnvironment(){
		try{
			ArrayList<String> databaseNames = new ArrayList<String>(environment.getDatabaseNames());
			return Result.successful(databaseNames);
		}catch(Exception e){
			return Result.failed("Failed to get database names from environment", e, null);
		}
	}

	/**
	 * Returns the list of database handles opened
	 * 
	 * @return Result with list of database name or error
	 */
	protected synchronized Result<ArrayList<String>> getDatabaseHandleNames(){
		ArrayList<String> names = new ArrayList<String>(dbHandles.keySet());
		return Result.successful(names);
	}

	@Override
	public int hashCode(){
		final int prime = 31;
		int result = 1;
		result = prime * result + ((directoryPath == null) ? 0 : directoryPath.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj){
		if(this == obj)
			return true;
		if(obj == null)
			return false;
		if(getClass() != obj.getClass())
			return false;
		BerkeleyDBEnvironmentHandle other = (BerkeleyDBEnvironmentHandle)obj;
		if(directoryPath == null){
			if(other.directoryPath != null)
				return false;
		}else if(!directoryPath.equals(other.directoryPath))
			return false;
		return true;
	}

	@Override
	public String toString(){
		return "BerkeleyDBEnvironmentHandle [directoryPath=" + directoryPath + ", deletable=" + deletable + ", dbHandles="
				+ dbHandles.keySet() + "]";
	}
	
}