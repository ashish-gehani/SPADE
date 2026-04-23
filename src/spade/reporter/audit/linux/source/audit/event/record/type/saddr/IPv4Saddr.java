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

/** AF_INET sockaddr: dotted-decimal address and decimal port. */
public class IPv4Saddr extends Saddr{

	private final String address;
	private final String port;

	private IPv4Saddr(final String hex, final String address, final String port){
		super(hex, Family.IPV4);
		this.address = address;
		this.port = port;
	}

	static IPv4Saddr create(final String hex){
		String address = null, port = null;
		if(hex.length() >= 17){
			try{
				port = Integer.toString(Integer.parseInt(hex.substring(4, 8), 16));
				final int oct1 = Integer.parseInt(hex.substring(8, 10), 16);
				final int oct2 = Integer.parseInt(hex.substring(10, 12), 16);
				final int oct3 = Integer.parseInt(hex.substring(12, 14), 16);
				final int oct4 = Integer.parseInt(hex.substring(14, 16), 16);
				address = String.format("%d.%d.%d.%d", oct1, oct2, oct3, oct4);
			}catch(final Exception e){
				address = null;
				port = null;
			}
		}
		return new IPv4Saddr(hex, address, port);
	}

	/** Dotted-decimal address, or null if the hex was too short or unparseable. */
	public String getAddress(){
		return address;
	}

	/** Decimal port number as a string, or null if the hex was too short or unparseable. */
	public String getPort(){
		return port;
	}
}
