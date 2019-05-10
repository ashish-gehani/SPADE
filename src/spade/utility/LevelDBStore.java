/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2012 SRI International

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
package spade.utility;

import static org.fusesource.leveldbjni.JniDBFactory.factory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigInteger;

import org.apache.commons.io.FileUtils;

import org.iq80.leveldb.CompressionType;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.WriteOptions;

public class LevelDBStore<V extends Serializable> implements ExternalStore<V>{

	private final String dbName;
	private final String dbDirPath;
	
	private final DB db;

	protected LevelDBStore(String dbDirPath, String dbName) throws Exception{
		this.dbDirPath = dbDirPath;
		this.dbName = dbName;
		
		WriteOptions writeOptions = new WriteOptions();
		writeOptions.sync(false);
		Options options = new Options();
		options.createIfMissing(true);
		options.compressionType(CompressionType.NONE);
        
		db = factory.open(new File(this.dbDirPath), options);
	}
	
	@Override
	public V get(String key) throws Exception{
		if(key != null){
			byte[] valueBytes = db.get(key.getBytes());
			if(valueBytes != null){
				ByteArrayInputStream byteInputStream = new ByteArrayInputStream(valueBytes);
				ObjectInputStream objectInputStream = new ObjectInputStream(byteInputStream);
				return (V)objectInputStream.readObject();
			}else{
				return null;
			}
		}else{
			return null;
		}
	}

	@Override
	public void put(String key, V value) throws Exception{
		if(key != null && value != null){
			ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
			ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteOutputStream);
			objectOutputStream.writeObject(value);
			objectOutputStream.flush();
			byte[] valueBytes = byteOutputStream.toByteArray(); 
		
			db.put(key.getBytes(), valueBytes);
		}
	}

	@Override
	public void remove(String key) throws Exception{
		if(key != null){
			db.delete(key.getBytes());
		}
	}

	@Override
	public void clear() throws Exception{
		// TODO
	}

	@Override
	public void close() throws Exception{
		db.close();
	}

	@Override
	public void delete() throws Exception{
		try{
			if(FileUtility.doesPathExist(dbDirPath)){
				if(!FileUtility.deleteDirectory(dbDirPath)){
					throw new Exception();
				}
			}
		}catch(Exception e){
			throw new Exception(e.getMessage() + ". Path deletion failed: " + dbDirPath, e);
		}
	}
	
	@Override
	public BigInteger sizeInBytesOfPersistedData() throws Exception{
		try{
			if(FileUtility.doesPathExist(dbDirPath)){
				return FileUtility.getSizeInBytes(dbDirPath);
			}else{
				throw new Exception("Does not exist");
			}
		}catch(Exception e){
			throw new Exception(e.getMessage() + ". Failed to get size for path: " + dbDirPath, e);
		}
	}
	
}

