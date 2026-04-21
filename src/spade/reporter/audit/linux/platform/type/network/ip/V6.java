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
package spade.reporter.audit.linux.platform.type.network.ip;

import java.net.Inet6Address;

public class V6 extends IP{

	private final Inet6Address address;

	public V6(
		final Inet6Address address
	){
		super(Type.V6);
		if(address == null){
			throw new IllegalArgumentException("address cannot be NULL");
		}
		this.address = address;
	}

	public Inet6Address getAddress(){
		return address;
	}

	@Override
	public boolean equals(final Object obj){
		if(this == obj) return true;
		if(!(obj instanceof V6)) return false;
		return this.address.equals(((V6) obj).address);
	}

	@Override
	public int hashCode(){
		return address.hashCode();
	}

}
