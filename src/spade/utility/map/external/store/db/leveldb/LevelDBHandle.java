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
import java.math.BigInteger;

import org.apache.commons.io.FileUtils;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;

import spade.utility.HelperFunctions;
import spade.utility.map.external.store.db.DatabaseHandle;

/**
 * LevelDB handle created by LevelDBManager
 */
public class LevelDBHandle implements DatabaseHandle{
	
	public final boolean deleteOnClose;
	public final String dbPath;
	private DB db;
	
	/**
	 * @param dbPath directory path
	 * @param deleteOnClose delete the database on close or not
	 * @param db LevelDB database object
	 */
	protected LevelDBHandle(String dbPath, boolean deleteOnClose, DB db){
		this.dbPath = dbPath;
		this.deleteOnClose = deleteOnClose;
		this.db = db;
	}
	
	/**
	 * Don't expose as public for easy management
	 * 
	 * @return LevelDB instance
	 */
	protected DB getDb(){
		return db;
	}

	@Override
	public void put(byte[] key, byte[] value) throws Exception{
		db.put(key, value);
	}

	@Override
	public byte[] get(byte[] key) throws Exception{
		return db.get(key);
	}

	@Override
	public void remove(byte[] key) throws Exception{
		db.delete(key);
	}

	@Override
	public BigInteger sizeOnDiskInBytes() throws Exception{
		if(HelperFunctions.isNullOrEmpty(dbPath)){
			throw new Exception("NULL database path");
		}else{
			BigInteger sizeBytes = FileUtils.sizeOfDirectoryAsBigInteger(new File(dbPath));
			if(sizeBytes == null){
				throw new Exception("NULL size for database path: '"+dbPath+"'");
			}else{
				return sizeBytes;
			}
		}
	}

	@Override
	public boolean contains(byte[] key) throws Exception{
		return get(key) != null;
	}

	@Override
	public void close() throws Exception{
		LevelDBManager.instance.closeHandle(this);
	}
	
	@Override
	public void clear() throws Exception{
		db.close();
		factory.destroy(new File(dbPath), new Options());
		Options options = new Options().createIfMissing(true);
		db = factory.open(new File(dbPath), options);
	}

	@Override
	public int hashCode(){
		final int prime = 31;
		int result = 1;
		result = prime * result + ((dbPath == null) ? 0 : dbPath.hashCode());
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
		LevelDBHandle other = (LevelDBHandle)obj;
		if(dbPath == null){
			if(other.dbPath != null)
				return false;
		}else if(!dbPath.equals(other.dbPath))
			return false;
		return true;
	}

	@Override
	public String toString(){
		return "LevelDBHandle [deleteOnClose=" + deleteOnClose + ", dbPath=" + dbPath + "]";
	}
}