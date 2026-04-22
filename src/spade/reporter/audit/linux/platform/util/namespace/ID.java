/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2026 SRI International

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
package spade.reporter.audit.linux.platform.util.namespace;

import spade.reporter.audit.linux.platform.util.fs.Inode;

public class ID{

	private final Type type;
	private final Inode inode;

	public ID(final Type type, final Inode inode){
		if(type == null){
			throw new IllegalArgumentException("type cannot be NULL");
		}
		if(inode == null){
			throw new IllegalArgumentException("inode cannot be NULL");
		}
		this.type = type;
		this.inode = inode;
	}

	public Type getType(){
		return type;
	}

	public Inode getInode(){
		return inode;
	}

	public ID(final ID other){
		this(other.type, new Inode(other.inode));
	}

	@Override
	public boolean equals(final Object obj){
		if(this == obj) return true;
		if(!(obj instanceof ID)) return false;
		final ID other = (ID) obj;
		return this.type == other.type
				&& this.inode.getValue() == other.inode.getValue();
	}

	@Override
	public int hashCode(){
		int result = type.hashCode();
		result = 31 * result + Long.hashCode(inode.getValue());
		return result;
	}

}
