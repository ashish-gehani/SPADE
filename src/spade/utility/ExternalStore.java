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

import java.io.Serializable;

/**
 * This interface must be implemented by classes that need to be used as external storage for ExternalMemoryMap class
 *
 * @param Object to serialize against a String key
 */

public interface ExternalStore<V extends Serializable>{
	/**
	 * Any initialization code required by the external storage
	 * @return true if successfully initialized
	 * @throws Exception Any implementation dependent exception
	 */
	public boolean init() throws Exception;
	/**
	 * Any shutdown to be called when external storage no longer needed
	 * @return true if successful
	 * @throws Exception Any implementation dependent exception
	 */
	public boolean shutdown() throws Exception;
	/**
	 * A function to get the value storage against the provided key
	 * @param key Key to look for
	 * @return Object value against the key
	 * @throws Exception Any implementation dependent exception
	 */
	public V get(String key) throws Exception;
	/**
	 * A function to add a key value pair
	 * @param key
	 * @param value
	 * @throws Exception Any implementation dependent exception
	 */
	public void put(String key, V value) throws Exception;
	/**
	 * A function to remove the key value if it exists
	 * @param key
	 * @throws Exception Any implementation dependent exception
	 */
	public void remove(String key) throws Exception;
	/**
	 * A function to remove all key value pairs in the external storage
	 * @throws Exception Any implementation dependent exception
	 */
	public void clear() throws Exception;
}