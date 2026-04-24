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
package spade.reporter.audit.linux.platform.syscall;

import java.util.HashMap;
import java.util.Map;

public abstract class Table {

	private final Map<Integer, Syscall> table = new HashMap<>();

	protected void put(final int num, final Syscall syscall){
		if(syscall == null){
			throw new IllegalArgumentException("syscall cannot be NULL");
		}
		if(table.containsKey(num)){
			throw new IllegalArgumentException("Duplicate syscall number: " + num);
		}
		table.put(num, syscall);
	}

	protected void put(final Syscall syscall){
		put(syscall.num, syscall);
	}

	public Syscall get(final int num) throws NoSuchSyscall{
		final Syscall syscall = table.get(num);
		if(syscall == null){
			throw NoSuchSyscall.forNum(num);
		}
		return syscall;
	}

	public boolean contains(final int num){
		return table.containsKey(num);
	}

}
