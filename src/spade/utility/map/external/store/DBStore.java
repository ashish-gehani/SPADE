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
package spade.utility.map.external.store;

import java.math.BigInteger;

import spade.utility.Converter;
import spade.utility.map.external.store.db.DatabaseHandle;

/**
 * Database backed store
 * 
 * @param <K> key type
 * @param <V> value type
 */
public class DBStore<K, V> extends Store<K, V>{

	/**
	 * DatabaseHandle as implemented by one of the database manager
	 */
	private DatabaseHandle dbHandle;
	
	/**
	 * @param dbHandle 			NON-NULL
	 * @param keyConverter 		NON-NULL
	 * @param valueConverter	NON-NULL
	 */
	public DBStore(DatabaseHandle dbHandle, Converter<K, byte[]> keyConverter, Converter<V, byte[]> valueConverter){
		super(keyConverter, valueConverter);
		this.dbHandle = dbHandle;
	}
	
	@Override
	public void put(K key, V value) throws Exception{
		byte[] keyBytes = keyConverter.serialize(key);
		byte[] valueBytes = valueConverter.serialize(value);
		dbHandle.put(keyBytes, valueBytes);
	}
	
	@Override
	public V get(K key) throws Exception{
		byte[] keyBytes = keyConverter.serialize(key);
		byte[] valueBytes = dbHandle.get(keyBytes);
		return valueConverter.deserialize(valueBytes);
	}
	
	@Override
	public void remove(K key) throws Exception{
		byte[] keyBytes = keyConverter.serialize(key);
	    dbHandle.remove(keyBytes);
	}
	
	@Override
	public void close() throws Exception{
		dbHandle.close();
	}
	
	@Override
	public BigInteger getSizeOnDiskInBytes() throws Exception{
		return dbHandle.sizeOnDiskInBytes();
	}
	
	@Override
	public boolean contains(K key) throws Exception{
		byte[] keyBytes = keyConverter.serialize(key);
		return dbHandle.contains(keyBytes);
	}

}
