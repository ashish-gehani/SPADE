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
package spade.reporter.audit.syscall;

public final class Syscall{

	// Returned when a system call is not in a table
	public static final Syscall UNSUPPORTED = new Syscall(Type.UNSUPPORTED, -1, "UNSUPPORTED");

	private final Type type;
	private final int num;
	private final String name;

	public Syscall(final Type type, final int num, final String name){
		this.type = type;
		this.num = num;
		this.name = name;
	}

	public Type getType(){
		return type;
	}

	public int getNum(){
		return num;
	}

	public String getName(){
		return name;
	}

	@Override
	public String toString(){
		return "Syscall [type=" + type + ", num=" + num + ", name=" + name + "]";
	}
}
