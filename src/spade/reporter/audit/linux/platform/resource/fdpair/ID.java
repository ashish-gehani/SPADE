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
package spade.reporter.audit.linux.platform.resource.fdpair;

import spade.reporter.audit.linux.platform.process.State;

public class ID extends spade.reporter.audit.linux.platform.resource.ID{

	public ID(final FDPair fdPair, final State processState){
		super(fdPair, processState);
	}

	public FDPair getFDPair(){
		return (FDPair) getResource();
	}

	private long pid(){
		return getProcessState().getCred().getProcess().getPid().getValue();
	}

	private int fd0(){
		return getFDPair().getFd0().getValue();
	}

	private int fd1(){
		return getFDPair().getFd1().getValue();
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
		int c = Long.compare(this.pid(), o.pid());
		if(c != 0) return c;
		c = Integer.compare(this.fd0(), o.fd0());
		if(c != 0) return c;
		return Integer.compare(this.fd1(), o.fd1());
	}

	@Override
	public boolean equals(final Object obj){
		if(this == obj) return true;
		if(!(obj instanceof ID)) return false;
		final ID other = (ID) obj;
		return this.pid() == other.pid()
			&& this.fd0() == other.fd0()
			&& this.fd1() == other.fd1();
	}

	@Override
	public int hashCode(){
		int result = Long.hashCode(pid());
		result = 31 * result + Integer.hashCode(fd0());
		result = 31 * result + Integer.hashCode(fd1());
		return result;
	}

}
