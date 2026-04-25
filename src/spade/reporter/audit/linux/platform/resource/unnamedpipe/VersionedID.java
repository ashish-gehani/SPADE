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
package spade.reporter.audit.linux.platform.resource.unnamedpipe;

import spade.reporter.audit.linux.type.credential.PID;

public class VersionedID extends spade.reporter.audit.linux.platform.resource.VersionedID{

	private final PID pid;

	public VersionedID(
		final UnnamedPipe unnamedPipe,
		final PID pid,
		final long version
	){
		super(unnamedPipe, version);
		if(pid == null){
			throw new IllegalArgumentException("pid cannot be NULL");
		}
		this.pid = pid;
	}

	public VersionedID(
		final UnnamedPipe unnamedPipe,
		final PID pid
	){
		super(unnamedPipe);
		if(pid == null){
			throw new IllegalArgumentException("pid cannot be NULL");
		}
		this.pid = pid;
	}

	public VersionedID(final VersionedID other){
		this(new UnnamedPipe(other.getUnnamedPipe()), new PID(other.pid), other.getVersion());
	}

	public UnnamedPipe getUnnamedPipe(){
		return (UnnamedPipe) getResource();
	}

	public PID getPid(){
		return pid;
	}

	@Override
	public VersionedID nextVersion(){
		return new VersionedID(new UnnamedPipe(getUnnamedPipe()), new PID(pid), getVersion() + 1);
	}

	private int fd0(){
		return getUnnamedPipe().getFd0().getValue();
	}

	private int fd1(){
		return getUnnamedPipe().getFd1().getValue();
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
		int c = Long.compare(this.pid.getValue(), o.pid.getValue());
		if(c != 0) return c;
		c = Integer.compare(this.fd0(), o.fd0());
		if(c != 0) return c;
		c = Integer.compare(this.fd1(), o.fd1());
		if(c != 0) return c;
		return Long.compare(this.getVersion(), o.getVersion());
	}

	@Override
	public boolean equals(final Object obj){
		if(this == obj) return true;
		if(!(obj instanceof VersionedID)) return false;
		final VersionedID other = (VersionedID) obj;
		return this.pid.equals(other.pid)
			&& this.fd0() == other.fd0()
			&& this.fd1() == other.fd1()
			&& this.getVersion() == other.getVersion();
	}

	@Override
	public int hashCode(){
		int result = pid.hashCode();
		result = 31 * result + Integer.hashCode(fd0());
		result = 31 * result + Integer.hashCode(fd1());
		result = 31 * result + Long.hashCode(getVersion());
		return result;
	}

}
