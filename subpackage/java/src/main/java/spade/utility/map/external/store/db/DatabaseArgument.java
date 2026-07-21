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

/**
 * Database argument object to be used to create a DatabaseHandle object
 */
public abstract class DatabaseArgument{

	/**
	 * Key for specifying the value of 'deleteOnClose' in the database argument by user
	 */
	public static final String keyDeleteDbOnClose = "deleteOnClose";
	
	/**
	 * If 'true' the DatabaseHandle close call deletes the database on disk
	 * If 'false' the DatabaseHandle close call leaves the database on disk for reuse
	 */
	public final boolean deleteOnClose;
	
	/**
	 * @param deleteOnClose delete the database on DatabaseHandle close or not
	 */
	public DatabaseArgument(boolean deleteOnClose){
		this.deleteOnClose = deleteOnClose;
	}
	
	@Override
	public int hashCode(){
		final int prime = 31;
		int result = 1;
		result = prime * result + (deleteOnClose ? 1231 : 1237);
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
		DatabaseArgument other = (DatabaseArgument)obj;
		if(deleteOnClose != other.deleteOnClose)
			return false;
		return true;
	}

	@Override
	public String toString(){
		return "DatabaseArgument [deleteOnClose=" + deleteOnClose + "]";
	}
}
