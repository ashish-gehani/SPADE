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

import spade.reporter.audit.linux.type.network.ip.Type;

public enum IpVersion{
	IPV4(Type.V4),
	IPV6(Type.V6),
	UNKNOWN(null);

	public final Type ipType;

	IpVersion(final Type ipType){
		this.ipType = ipType;
	}

	public static IpVersion parse(final String value){
		if(value == null){
			return UNKNOWN;
		}
		for(final IpVersion v : IpVersion.values()){
			if(v.name().equalsIgnoreCase(value)){
				return v;
			}
		}
		return UNKNOWN;
	}
}
