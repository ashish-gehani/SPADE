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
package spade.reporter.audit.linux.type.network.transport;

import spade.reporter.audit.linux.type.network.ip.IP;

public class Address{

	private final IP ip;
	private final int port;

	public Address(
		final IP ip,
		final int port
	){
		if(ip == null){
			throw new IllegalArgumentException("ip cannot be NULL");
		}
		this.ip = ip;
		this.port = port;
	}

	public IP getIP(){
		return ip;
	}

	public int getPort(){
		return port;
	}

	@Override
	public boolean equals(final Object obj){
		if(this == obj) return true;
		if(!(obj instanceof Address)) return false;
		final Address other = (Address) obj;
		return this.ip.equals(other.ip)
			&& this.port == other.port;
	}

	@Override
	public int hashCode(){
		int result = ip.hashCode();
		result = 31 * result + Integer.hashCode(port);
		return result;
	}

}
