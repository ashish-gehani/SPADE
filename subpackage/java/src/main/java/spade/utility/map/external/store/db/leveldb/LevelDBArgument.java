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

import spade.utility.map.external.store.db.DatabaseArgument;

/**
 *	LevelDB arguments necessary to create LevelDB handle using LevelDBManager
 */
public class LevelDBArgument extends DatabaseArgument{
	
	public static final String keyDatabasePath = "databasePath";
	
	/**
	 * Directory path of the database
	 */
	public final String databasePath;
	
	/**
	 * @param databasePath directory path
	 * @param deleteOnClose delete the database on close or not
	 */
	protected LevelDBArgument(String databasePath, boolean deleteOnClose){
		super(deleteOnClose);
		this.databasePath = databasePath;
	}
	
	@Override
	public int hashCode(){
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((databasePath == null) ? 0 : databasePath.hashCode());
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
		LevelDBArgument other = (LevelDBArgument)obj;
		if(databasePath == null){
			if(other.databasePath != null)
				return false;
		}else if(!databasePath.equals(other.databasePath))
			return false;
		return true;
	}

	@Override
	public String toString(){
		return "LevelDBArgument [databasePath=" + databasePath + ", deleteOnClose=" + deleteOnClose + "]";
	}
}