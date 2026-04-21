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
package spade.reporter.audit.linux.platform.resource.fs;

import spade.reporter.audit.linux.platform.process.State;

public class ID extends spade.reporter.audit.linux.platform.resource.ID{

	public ID(final Path path, final State processState){
		super(path, processState);
	}

	public Path getPath(){
		return (Path) getResource();
	}

	private String getResolvedPath(){
		return getPath().getPath().getResolvedPath();
	}

	private Type pathType(){
		return getPath().getPathType();
	}

	private long inode(){
		return getPath().getInode().getValue();
	}

	@Override
	public int compareTo(final spade.reporter.audit.linux.platform.resource.ID other){
		if(other == null){
			throw new IllegalArgumentException("Cannot compare to NULL");
		}
		if(this == other) return 0;
		if(!(other instanceof ID)){
			return Integer.compare(
				this.getResource().getType().ordinal(),
				other.getResource().getType().ordinal()
			);
		}
		final ID o = (ID) other;
		int c = this.pathType().compareTo(o.pathType());
		if(c != 0) return c;
		c = Long.compare(this.inode(), o.inode());
		if(c != 0) return c;
		return this.getResolvedPath().compareTo(o.getResolvedPath());
	}

	@Override
	public boolean equals(final Object obj){
		if(this == obj) return true;
		if(!(obj instanceof ID)) return false;
		final ID other = (ID) obj;
		return this.pathType() == other.pathType()
			&& this.inode() == other.inode()
			&& this.getResolvedPath().equals(other.getResolvedPath());
	}

	@Override
	public int hashCode(){
		int result = pathType().hashCode();
		result = 31 * result + Long.hashCode(inode());
		result = 31 * result + getResolvedPath().hashCode();
		return result;
	}

}
