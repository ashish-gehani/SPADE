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
package spade.reporter.audit.linux.process;

import spade.reporter.audit.core.util.statetable.Indexable;

public class ID implements Indexable<ID>{

	// Host PID
	private final String pid;

	public ID(final String pid){
		if(pid == null){
			throw new IllegalArgumentException("pid cannot be NULL");
		}
		this.pid = pid;
	}

	public String getPid(){
		return pid;
	}

	@Override
	public int compareTo(final ID other){
		if(other == null){
			throw new IllegalArgumentException("Cannot compare to NULL");
		}
		final int cmp = this.pid.compareTo(other.pid);
		return cmp;
	}

	@Override
	public boolean equals(final Object obj){
		if(this == obj) return true;
		if(!(obj instanceof ID)) return false;
		final ID other = (ID) obj;
		return this.pid.equals(other.pid);
	}

	@Override
	public int hashCode(){
		return 31 * pid.hashCode();
	}

}
