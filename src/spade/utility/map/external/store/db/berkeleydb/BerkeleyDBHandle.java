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
import java.math.BigInteger;

import org.apache.commons.io.FileUtils;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Environment;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;

import spade.utility.HelperFunctions;
import spade.utility.map.external.store.db.DatabaseHandle;

/**
 * BerkeleyDB database handle managed by the BerkeleyDBManager
 */
public class BerkeleyDBHandle implements DatabaseHandle{
	
	/**
	 * Name of the database
	 */
	protected final String dbName;
	/**
	 * BerkeleyDB database object
	 */
	private Database database;
	/**
	 * BerkeleyDB environment handle
	 */
	private BerkeleyDBEnvironmentHandle environmentHandle;
	/**
	 * Delete the database on close or not
	 */
	protected final boolean deleteOnClose;

	/**
	 * @param dbName name of the database
	 * @param database BerkeleyDB database object
	 * @param environmentHandle environment handle created by BerkeleyDBManager
	 * @param deleteOnClose delete the database on close or not
	 */
	protected BerkeleyDBHandle(String dbName, Database database, BerkeleyDBEnvironmentHandle environmentHandle, boolean deleteOnClose){
		this.dbName = dbName;
		this.database = database;
		this.environmentHandle = environmentHandle;
		this.deleteOnClose = deleteOnClose;
	}
	
	protected Database getDatabase(){
		return database;
	}
	
	protected BerkeleyDBEnvironmentHandle getEnvironmentHandle(){
		return environmentHandle;
	}

	@Override
	public void put(byte[] key, byte[] value) throws Exception{
		DatabaseEntry keyEntry = new DatabaseEntry(key);
		DatabaseEntry valueEntry = new DatabaseEntry(value);
		OperationStatus status = database.put(null, keyEntry, valueEntry);
		switch(status){
			case SUCCESS: break;
			default: new Exception("Non-success status returned from BerkeleyDB: '"+status+"'");
		}
	}

	@Override
	public byte[] get(byte[] key) throws Exception{
		DatabaseEntry keyEntry = new DatabaseEntry(key);
		DatabaseEntry valueEntry = new DatabaseEntry();
		OperationStatus status = database.get(null, keyEntry, valueEntry, LockMode.DEFAULT);
		switch(status){
			case SUCCESS: return valueEntry.getData();
			case NOTFOUND: return null;
			default: throw new Exception("Non-success status returned from BerkeleyDB: '"+status+"'");
		}
	}

	@Override
	public boolean contains(byte[] key) throws Exception{
		DatabaseEntry keyEntry = new DatabaseEntry(key);
		DatabaseEntry valueEntry = new DatabaseEntry();
		OperationStatus status = database.get(null, keyEntry, valueEntry, LockMode.DEFAULT);
		switch(status){
			case SUCCESS: return true;
			case NOTFOUND: return false;
			default: throw new Exception("Non-success status returned from BerkeleyDB: '"+status+"'");
		}
	}

	@Override
	public void remove(byte[] key) throws Exception{
		DatabaseEntry keyEntry = new DatabaseEntry(key);
		database.delete(null, keyEntry);
	}

	@Override
	public BigInteger sizeOnDiskInBytes() throws Exception{
		if(environmentHandle == null){
			throw new Exception("NULL enviroment handle");
		}else{
			String envPath = environmentHandle.directoryPath;
			if(HelperFunctions.isNullOrEmpty(envPath)){
				throw new Exception("NULL environment path");
			}else{
				BigInteger sizeBytes = FileUtils.sizeOfDirectoryAsBigInteger(new File(envPath));
				if(sizeBytes == null){
					throw new Exception("NULL size for environment path: '"+envPath+"'");
				}else{
					return sizeBytes;
				}
			}
		}
	}

	@Override
	public void close() throws Exception{
		BerkeleyDBManager.instance.closeHandle(this);
	}
	
	@Override
	public void clear() throws Exception{
		if(environmentHandle == null){
			throw new Exception("NULL enviroment handle");
		}else{
			Environment environment = environmentHandle.getEnvironment();
			if(environment == null){
				throw new Exception("NULL enviroment");
			}else{
				database.close();
				environment.removeDatabase(null, dbName);
				DatabaseConfig databaseConfig = new DatabaseConfig();
				databaseConfig.setAllowCreate(true).setExclusiveCreate(true);
				database = environment.openDatabase(null, dbName, databaseConfig);
			}
		}
	}

	@Override
	public int hashCode(){
		final int prime = 31;
		int result = 1;
		result = prime * result + ((dbName == null) ? 0 : dbName.hashCode());
		result = prime * result + ((environmentHandle == null) ? 0 : environmentHandle.hashCode());
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
		BerkeleyDBHandle other = (BerkeleyDBHandle)obj;
		if(dbName == null){
			if(other.dbName != null)
				return false;
		}else if(!dbName.equals(other.dbName))
			return false;
		if(deleteOnClose != other.deleteOnClose)
			return false;
		if(environmentHandle == null){
			if(other.environmentHandle != null)
				return false;
		}else if(!HelperFunctions.objectsEqual(environmentHandle.directoryPath, other.environmentHandle.directoryPath))
			return false;
		return true;
	}

	@Override
	public String toString(){
		String environmentPath = null;
		Boolean environmentDeletable = null;
		if(environmentHandle != null){
			environmentPath = environmentHandle.directoryPath;
			environmentDeletable = environmentHandle.deletable;
		}
		return "BerkeleyDBHandle [dbName=" + dbName + ", environmentPath=" + environmentPath + 
				", environmentDeletable=" + environmentDeletable + 
				", deleteDBOnClose=" + deleteOnClose + "]";
	}
}