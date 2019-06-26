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

import java.util.logging.Level;
import java.util.logging.Logger;

import spade.utility.map.external.store.db.DatabaseManager;
import spade.utility.map.external.store.db.berkeleydb.BerkeleyDBManager;
import spade.utility.map.external.store.db.leveldb.LevelDBManager;

/**
 * Available stores
 */
public enum StoreName{
	BerkeleyDB(BerkeleyDBManager.instance), LevelDB(LevelDBManager.instance);
	
	protected final DatabaseManager dbManager;
	private StoreName(DatabaseManager dbManager){
		this.dbManager = dbManager;
	}
	
	//////////////////////////////////////////

	private static final Logger logger = Logger.getLogger(StoreName.class.getName());

	/**
	 * To be called only once at JVM shutdown. Calls shutdown for all registered database manager children.
	 */
	public static void shutdownAll(){
		for(StoreName storeName : StoreName.values()){
			DatabaseManager dbManager = storeName.dbManager;
			if(dbManager != null){
				try{
					dbManager.shutdown();
				}catch(Exception e){
					logger.log(Level.SEVERE, "Failed to shutdown DB manager: " + dbManager, e);
				}
			}
		}
	}
}
