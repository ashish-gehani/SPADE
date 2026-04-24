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
package spade.reporter.audit.linux.source.audit.event.record.type.saddr;

import java.net.Inet4Address;
import java.net.InetAddress;

import spade.reporter.audit.linux.type.network.ip.V4;
import spade.reporter.audit.linux.type.network.transport.Address;

/** AF_INET sockaddr: dotted-decimal address and decimal port. */
public class IPv4Saddr extends Saddr{

	private final Address address;

	private IPv4Saddr(final String hex, final Address address){
		super(hex, Family.IPV4);
		this.address = address;
	}

	static IPv4Saddr create(final String hex){
		Address address = null;
		if(hex.length() >= 17){
			try{
				final int port = Integer.parseInt(hex.substring(4, 8), 16);
				final byte[] bytes = new byte[]{
					(byte) Integer.parseInt(hex.substring(8, 10), 16),
					(byte) Integer.parseInt(hex.substring(10, 12), 16),
					(byte) Integer.parseInt(hex.substring(12, 14), 16),
					(byte) Integer.parseInt(hex.substring(14, 16), 16)
				};
				address = new Address(new V4((Inet4Address) InetAddress.getByAddress(bytes)), port);
			}catch(final Exception e){
				address = null;
			}
		}
		return new IPv4Saddr(hex, address);
	}

	/** Transport-layer address (IPv4 + port), or null if the hex was too short or unparseable. */
	public Address getAddress(){
		return address;
	}
}
