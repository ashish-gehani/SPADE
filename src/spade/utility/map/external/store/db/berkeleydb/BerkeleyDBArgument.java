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

import spade.utility.map.external.store.db.DatabaseArgument;

/**
 * BerkeleyDB arguments necessary to create a BerkeleyDB DatabaseHandle
 */
public class BerkeleyDBArgument extends DatabaseArgument{

	public static final String keyEnvironmentPath = "environmentPath",
			keyDatabaseName = "dbName";

	/**
	 * Path on disk where the environment exists or to create
	 */
	public final String environmentPath;
	/**
	 * Name of existing database or a new database in the specified environment
	 */
	public final String dbName;
	
	/**
	 * @param environmentPath 	Non-NULL/Non-Empty string
	 * @param dbName 			Non-NULL/Non-Empty
	 * @param deleteOnClose		true/false
	 */
	protected BerkeleyDBArgument(String environmentPath, String dbName, boolean deleteOnClose){
		super(deleteOnClose);
		this.environmentPath = environmentPath;
		this.dbName = dbName;
	}
	
	@Override
	public int hashCode(){
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((dbName == null) ? 0 : dbName.hashCode());
		result = prime * result + ((environmentPath == null) ? 0 : environmentPath.hashCode());
		return result;
	}
	
	@Override
	public boolean equals(Object obj){
		if(this == obj)
			return true;
		if(!super.equals(obj))
			return false;
		if(getClass() != obj.getClass())
			return false;
		BerkeleyDBArgument other = (BerkeleyDBArgument)obj;
		if(dbName == null){
			if(other.dbName != null)
				return false;
		}else if(!dbName.equals(other.dbName))
			return false;
		if(environmentPath == null){
			if(other.environmentPath != null)
				return false;
		}else if(!environmentPath.equals(other.environmentPath))
			return false;
		return true;
	}
	
	@Override
	public String toString(){
		return "BerkeleyDBArgument [environmentPath=" + environmentPath + ", dbName=" + dbName + ", deleteOnClose="
				+ deleteOnClose + "]";
	}
}