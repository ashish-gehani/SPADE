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
package spade.reporter.audit.linux.platform.resource.sharedmemory;

import spade.reporter.audit.linux.platform.resource.Type;
import spade.reporter.audit.linux.type.namespace.ID;

public class VersionedID extends spade.reporter.audit.linux.platform.resource.VersionedID{

	private final ID ipcNamespace;

	public VersionedID(
		final SharedMemory sharedMemory,
		final ID ipcNamespace,
		final long version
	){
		super(sharedMemory, version);
		if(ipcNamespace == null){
			throw new IllegalArgumentException("ipcNamespace cannot be NULL");
		}
		this.ipcNamespace = ipcNamespace;
	}

	public VersionedID(
		final SharedMemory sharedMemory,
		final ID ipcNamespace
	){
		super(sharedMemory);
		if(ipcNamespace == null){
			throw new IllegalArgumentException("ipcNamespace cannot be NULL");
		}
		this.ipcNamespace = ipcNamespace;
	}

	public VersionedID(final VersionedID other){
		this(new SharedMemory(other.getSharedMemory()), new ID(other.ipcNamespace), other.getVersion());
	}

	public SharedMemory getSharedMemory(){
		return (SharedMemory) getResource();
	}

	public ID getIpcNamespace(){
		return ipcNamespace;
	}

	@Override
	public VersionedID nextVersion(){
		return new VersionedID(new SharedMemory(getSharedMemory()), new ID(ipcNamespace), getVersion() + 1);
	}

	private Type type(){
		return getSharedMemory().getType();
	}

	private String id(){
		return getSharedMemory().getId();
	}

	private long ownerUID(){
		return getSharedMemory().getOwnerUID().getValue();
	}

	private long ownerGID(){
		return getSharedMemory().getOwnerGID().getValue();
	}

	@Override
	public int compareTo(final spade.reporter.audit.linux.platform.resource.VersionedID other){
		if(other == null){
			throw new IllegalArgumentException("Cannot compare to NULL");
		}
		if(this == other) return 0;
		if(!(other instanceof VersionedID)){
			return super.compareTo(other);
		}
		final VersionedID o = (VersionedID) other;
		int c = this.type().compareTo(o.type());
		if(c != 0) return c;
		c = this.id().compareTo(o.id());
		if(c != 0) return c;
		c = Long.compare(this.ownerUID(), o.ownerUID());
		if(c != 0) return c;
		c = Long.compare(this.ownerGID(), o.ownerGID());
		if(c != 0) return c;
		c = this.ipcNamespace.getType().compareTo(o.ipcNamespace.getType());
		if(c != 0) return c;
		c = Long.compare(this.ipcNamespace.getInode().getValue(), o.ipcNamespace.getInode().getValue());
		if(c != 0) return c;
		return Long.compare(this.getVersion(), o.getVersion());
	}

	@Override
	public boolean equals(final Object obj){
		if(this == obj) return true;
		if(!(obj instanceof VersionedID)) return false;
		final VersionedID other = (VersionedID) obj;
		return this.getSharedMemory().equals(other.getSharedMemory())
			&& this.ipcNamespace.equals(other.ipcNamespace)
			&& this.getVersion() == other.getVersion();
	}

	@Override
	public int hashCode(){
		int result = getSharedMemory().hashCode();
		result = 31 * result + ipcNamespace.hashCode();
		result = 31 * result + Long.hashCode(getVersion());
		return result;
	}

}
