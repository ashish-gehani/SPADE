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
package spade.reporter.audit.linux.platform.resource.memory;

import spade.reporter.audit.linux.platform.resource.Resource;

public class Memory extends Resource{

	private final long address;
	private final long size;

	public Memory(final Memory other){
		this(other.address, other.size);
	}

	public Memory(
		final long address,
		final long size
	){
		super(spade.reporter.audit.linux.platform.resource.Type.MEMORY);
		this.address = address;
		this.size = size;
	}

	public long getAddress(){
		return address;
	}

	public long getSize(){
		return size;
	}

	@Override
	public boolean equals(final Object obj){
		if(this == obj) return true;
		if(!(obj instanceof Memory)) return false;
		final Memory other = (Memory) obj;
		return this.address == other.address
			&& this.size == other.size;
	}

	@Override
	public int hashCode(){
		int result = Long.hashCode(address);
		result = 31 * result + Long.hashCode(size);
		return result;
	}

}
