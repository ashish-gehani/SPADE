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

import java.util.Map;

import spade.utility.Result;

/**
 * A database manager for databases that can be used by external map.
 * All children must be singleton with an empty constructor.
 */
public abstract class DatabaseManager{

	/**
	 * Create the database argument object from arguments provided by user
	 * 
	 * @param arguments
	 * @return DatabaseArgument
	 */
	public abstract Result<DatabaseArgument> parseArgument(String arguments);
	/**
	 * Create the database argument object from arguments provided as key value map
	 * 
	 * @param arguments
	 * @return DatabaseArgument
	 */
	public abstract Result<DatabaseArgument> parseArgument(Map<String, String> arguments);
	/**
	 * Create a database handle from the database argument
	 * 
	 * @param argument
	 * @return DatabaseHandle
	 */
	public abstract Result<DatabaseHandle> createHandleFromArgument(DatabaseArgument argument);
	/**
	 * Close a database handle with all the necessary cleanup
	 * 
	 * @param handle DatabaseHandle
	 * @return true on success otherwise false
	 */
	public abstract Result<Boolean> closeHandle(DatabaseHandle handle);
	/**
	 * Shutdown the manager on JVM exit for any necessary cleanup
	 */
	public abstract void shutdown();
	
}
