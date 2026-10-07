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
package spade.reporter.audit.operation;

import java.util.List;

public final class Operation{

	private final Type type;
	private final String name;
	private final List<String> syscallNames;

	public Operation(final Type type, final String name, final List<String> syscallNames){
		this.type = type;
		this.name = name;
		this.syscallNames = List.copyOf(syscallNames);
	}

	public Type getType(){
		return type;
	}

	public String getName(){
		return name;
	}

	/**
	 * Returns the system call names (as in the arch system call tables) for the operation. Empty if none are configured.
	 */
	public List<String> getSyscallNames(){
		return syscallNames;
	}

	@Override
	public String toString(){
		return "Operation [type=" + type + ", name=" + name + ", syscallNames=" + syscallNames + "]";
	}
}
