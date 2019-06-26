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
package spade.utility.map.external.store.db;

import java.math.BigInteger;

/**
 * A key value based database handle to be used by the external map
 */
public interface DatabaseHandle{

	/**
	 * Put 'value' for 'key'
	 * 
	 * @param key
	 * @param value
	 * @throws Exception any exception by the underlying database
	 */
	public void put(byte[] key, byte[] value) throws Exception;
	/**
	 * Returns the value for 'key'. NULL if not found.
	 * 
	 * @param key
	 * @return value/NULL
	 * @throws Exception any exception by the underlying database
	 */
	public byte[] get(byte[] key) throws Exception;
	/**
	 * Removes the value for 'key'
	 * 
	 * @param key
	 * @throws Exception any exception by the underlying database
	 */
	public void remove(byte[] key) throws Exception;
	/**
	 * Returns the size in bytes of the database on disk
	 * @return size in bytes on disk
	 * @throws Exception any exception by the underlying database
	 */
	public BigInteger sizeOnDiskInBytes() throws Exception;
	/**
	 * Close the database handle and free any resources necessary
	 * @throws Exception any exception by the underlying database
	 */
	public void close() throws Exception;
	/**
	 * Clear all the data
	 * @throws Exception any exception by the underlying database
	 */
	public void clear() throws Exception;
	/**
	 * Returns 'true' if the a key value pair with 'key' exists otherwise 'false'
	 * To check if the NULL returned by 'get' function means if value was null or if the key didn't exist
	 * 
	 * @param key
	 * @return true/false
	 * @throws Exception any exception by the underlying database
	 */
	public boolean contains(byte[] key) throws Exception;
	
}
