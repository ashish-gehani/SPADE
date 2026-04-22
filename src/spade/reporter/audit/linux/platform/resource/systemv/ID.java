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
package spade.reporter.audit.linux.platform.resource.systemv;

import spade.reporter.audit.linux.platform.process.State;

public class ID extends spade.reporter.audit.linux.platform.resource.ID{

	public ID(final SystemV systemV, final State processState){
		super(systemV, processState);
	}

	public SystemV getSystemV(){
		return (SystemV) getResource();
	}

	private Type systemVType(){
		return getSystemV().getSystemVType();
	}

	private String id(){
		return getSystemV().getId();
	}

	private long ownerUid(){
		return getSystemV().getOwnerUID().getValue();
	}

	private long ownerGid(){
		return getSystemV().getOwnerGID().getValue();
	}

	private spade.reporter.audit.linux.platform.util.namespace.ID ipcNamespace(){
		return getProcessState().getNamespace().getIpc();
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
		int c = this.systemVType().compareTo(o.systemVType());
		if(c != 0) return c;
		c = this.id().compareTo(o.id());
		if(c != 0) return c;
		c = Long.compare(this.ownerUid(), o.ownerUid());
		if(c != 0) return c;
		c = Long.compare(this.ownerGid(), o.ownerGid());
		if(c != 0) return c;
		spade.reporter.audit.linux.platform.util.namespace.ID thisIpc = this.ipcNamespace();
		spade.reporter.audit.linux.platform.util.namespace.ID otherIpc = o.ipcNamespace();
		c = thisIpc.getType().compareTo(otherIpc.getType());
		if(c != 0) return c;
		return Long.compare(thisIpc.getInode().getValue(), otherIpc.getInode().getValue());
	}

	@Override
	public boolean equals(final Object obj){
		if(this == obj) return true;
		if(!(obj instanceof ID)) return false;
		final ID other = (ID) obj;
		return this.systemVType() == other.systemVType()
			&& this.id().equals(other.id())
			&& this.ownerUid() == other.ownerUid()
			&& this.ownerGid() == other.ownerGid()
			&& this.ipcNamespace().equals(other.ipcNamespace());
	}

	@Override
	public int hashCode(){
		int result = systemVType().hashCode();
		result = 31 * result + id().hashCode();
		result = 31 * result + Long.hashCode(ownerUid());
		result = 31 * result + Long.hashCode(ownerGid());
		result = 31 * result + ipcNamespace().hashCode();
		return result;
	}

}
