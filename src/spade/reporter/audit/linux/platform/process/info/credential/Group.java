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
package spade.reporter.audit.linux.platform.process.info.credential;

import spade.reporter.audit.linux.platform.type.credential.GID;

public class Group{

	private final GID gid;
	private final GID egid;
	private final GID sgid;
	private final GID fsgid;

	public Group(final GID gid, final GID egid, final GID sgid, final GID fsgid){
		if(gid == null){
			throw new IllegalArgumentException("gid cannot be NULL");
		}
		if(egid == null){
			throw new IllegalArgumentException("egid cannot be NULL");
		}
		if(sgid == null){
			throw new IllegalArgumentException("sgid cannot be NULL");
		}
		if(fsgid == null){
			throw new IllegalArgumentException("fsgid cannot be NULL");
		}
		this.gid = gid;
		this.egid = egid;
		this.sgid = sgid;
		this.fsgid = fsgid;
	}

	public GID getGid(){ return gid; }
	public GID getEgid(){ return egid; }
	public GID getSgid(){ return sgid; }
	public GID getFsgid(){ return fsgid; }

	public Group(final Group other){
		this(
			new GID(other.gid),
			new GID(other.egid),
			new GID(other.sgid),
			new GID(other.fsgid)
		);
	}

	@Override
	public boolean equals(final Object obj){
		if(this == obj) return true;
		if(!(obj instanceof Group)) return false;
		final Group other = (Group) obj;
		return this.gid.getValue() == other.gid.getValue()
				&& this.egid.getValue() == other.egid.getValue()
				&& this.sgid.getValue() == other.sgid.getValue()
				&& this.fsgid.getValue() == other.fsgid.getValue();
	}

	@Override
	public int hashCode(){
		int result = Long.hashCode(gid.getValue());
		result = 31 * result + Long.hashCode(egid.getValue());
		result = 31 * result + Long.hashCode(sgid.getValue());
		result = 31 * result + Long.hashCode(fsgid.getValue());
		return result;
	}

}
