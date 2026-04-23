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
package spade.reporter.audit.linux.source.audit.event.record.netfilter;

public enum Protocol{
	TCP(spade.reporter.audit.linux.type.network.transport.Protocol.TCP),
	UDP(spade.reporter.audit.linux.type.network.transport.Protocol.UDP),
	UNKNOWN(null);

	public final spade.reporter.audit.linux.type.network.transport.Protocol protocol;

	Protocol(final spade.reporter.audit.linux.type.network.transport.Protocol protocol){
		this.protocol = protocol;
	}

	public static Protocol parse(final String value){
		if(value == null){
			return UNKNOWN;
		}
		for(final Protocol p : Protocol.values()){
			if(p.name().equalsIgnoreCase(value)){
				return p;
			}
		}
		return UNKNOWN;
	}
}
