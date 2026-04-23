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

/** AF_INET6 sockaddr: colon-separated hextet address and decimal port. */
public class IPv6Saddr extends Saddr{

	private final String address;
	private final String port;

	private IPv6Saddr(final String hex, final String address, final String port){
		super(hex, Family.IPV6);
		this.address = address;
		this.port = port;
	}

	static IPv6Saddr create(final String hex){
		String address = null, port = null;
		if(hex.length() >= 49){
			try{
				port = Integer.toString(Integer.parseInt(hex.substring(4, 8), 16));
				address = String.format("%s:%s:%s:%s:%s:%s:%s:%s",
					hex.substring(16, 20), hex.substring(20, 24),
					hex.substring(24, 28), hex.substring(28, 32),
					hex.substring(32, 36), hex.substring(36, 40),
					hex.substring(40, 44), hex.substring(44, 48));
			}catch(final Exception e){
				address = null;
				port = null;
			}
		}
		return new IPv6Saddr(hex, address, port);
	}

	/** Colon-separated hextet address, or null if the hex was too short or unparseable. */
	public String getAddress(){
		return address;
	}

	/** Decimal port number as a string, or null if the hex was too short or unparseable. */
	public String getPort(){
		return port;
	}
}
