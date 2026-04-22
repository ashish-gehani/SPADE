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
package spade.reporter.audit.linux.platform.process;

import spade.reporter.audit.linux.platform.util.credential.PID;

public class VersionedID extends spade.reporter.audit.core.platform.process.ID<VersionedID>{

	private final PID pid;
	private final long version;

	public VersionedID(final VersionedID other){
		this(new PID(other.pid), other.version);
	}

	public VersionedID(final PID pid, final long version){
		if(pid == null){
			throw new IllegalArgumentException("pid cannot be NULL");
		}
		this.pid = pid;
		this.version = version;
	}

	public PID getPid(){
		return pid;
	}

	public long getVersion(){
		return version;
	}

	public VersionedID nextVersion(){
		return new VersionedID(pid, version + 1);
	}

	@Override
	public int compareTo(final VersionedID other){
		if(other == null){
			throw new IllegalArgumentException("Cannot compare to NULL");
		}
		final int pidCmp = Long.compare(this.pid.getValue(), other.pid.getValue());
		if(pidCmp != 0){
			return pidCmp;
		}
		return Long.compare(this.version, other.version);
	}

	@Override
	public boolean equals(final Object obj){
		if(this == obj) return true;
		if(!(obj instanceof VersionedID)) return false;
		final VersionedID other = (VersionedID) obj;
		return this.pid.getValue() == other.pid.getValue()
			&& this.version == other.version;
	}

	@Override
	public int hashCode(){
		return 31 * Long.hashCode(pid.getValue()) + Long.hashCode(version);
	}

}
