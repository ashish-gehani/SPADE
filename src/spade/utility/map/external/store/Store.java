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

/**
 * Store for external map
 *
 * @param <K> type of key
 * @param <V> type of value
 */
public abstract class Store<K, V>{

	/**
	 * Converter for key
	 */
	public final Converter<K, byte[]> keyConverter;
	/**
	 * Converter for value
	 */
	public final Converter<V, byte[]> valueConverter;
	
	/**
	 * @param keyConverter converter for the key. Non-NULL
	 * @param valueConverter converter for the value. Non-NULL
	 */
	public Store(Converter<K, byte[]> keyConverter, Converter<V, byte[]> valueConverter){
		this.keyConverter = keyConverter;
		this.valueConverter = valueConverter;
	}
	
	/**
	 * Put into store
	 * 
	 * @param key
	 * @param value
	 * @throws Exception any underlying store and database exception
	 */
	public abstract void put(K key, V value) throws Exception;
	
	/**
	 * Get from store
	 * 
	 * @param key
	 * @return NULL or value for the key
	 * @throws Exception any underlying store and database exception
	 */
	public abstract V get(K key) throws Exception;
	
	/**
	 * Remove the key from store
	 * 
	 * @param key
	 * @throws Exception any underlying store and database exception
	 */
	public abstract void remove(K key) throws Exception;
	
	/**
	 * Close the store and all other associated resources
	 * 
	 * @throws Exception any underlying store and database exception
	 */
	public abstract void close() throws Exception;
	
	/**
	 * Get the size on disk
	 * 
	 * @return Size in bytes
	 * @throws Exception any underlying store and database exception
	 */
	public abstract BigInteger getSizeOnDiskInBytes() throws Exception;
	
	/**
	 * Returns whether the key was present in the store. Useful for distinguishing between NULL and NO-VALUE
	 * 
	 * @param key
	 * @return true/false
	 * @throws Exception any underlying store and database exception
	 */
	public abstract boolean contains(K key) throws Exception;
	
	/**
	 * Clear the database i.e. remove everything
	 * @throws Exception any underlying store and database exception
	 */
	public abstract void clear() throws Exception;
}
