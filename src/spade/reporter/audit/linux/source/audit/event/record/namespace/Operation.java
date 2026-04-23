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
package spade.reporter.audit.linux.source.audit.event.record.namespace;

public enum Operation{
	NEWPROCESS,
	SETNS,
	UNSHARE,
	UNKNOWN;

	public static Operation parse(final String value){
		if(value == null){
			return UNKNOWN;
		}
		final String stripped = value.startsWith("ns_") ? value.substring(3) : value;
		for(final Operation op : Operation.values()){
			if(op.name().equalsIgnoreCase(stripped)){
				return op;
			}
		}
		return UNKNOWN;
	}
}
