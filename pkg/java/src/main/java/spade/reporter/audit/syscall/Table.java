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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import spade.utility.setting.InvalidSettingException;

public abstract class Table{

	private final Map<Integer, Syscall> numToSyscall;
	private final Map<String, Syscall> nameToSyscall;

	protected Table(final String configFilePath) throws InvalidSettingException{
		final Config config = Parser.parse(configFilePath);

		final Map<Integer, Syscall> numToSyscall = new HashMap<Integer, Syscall>();
		final Map<String, Syscall> nameToSyscall = new HashMap<String, Syscall>();
		for(final Syscall syscall : config.getSyscalls()){
			numToSyscall.put(syscall.getNum(), syscall);
			nameToSyscall.put(syscall.getName(), syscall);
		}
		this.numToSyscall = Collections.unmodifiableMap(numToSyscall);
		this.nameToSyscall = Collections.unmodifiableMap(nameToSyscall);
	}

	/**
	 * Returns the system call with the name, or {@link Syscall#UNSUPPORTED} if the table has none.
	 */
	public Syscall getByName(final String name){
		return nameToSyscall.getOrDefault(name, Syscall.UNSUPPORTED);
	}

	/**
	 * Returns the system call with the number, or {@link Syscall#UNSUPPORTED} if the table has none.
	 */
	public Syscall getByNum(final int num){
		return numToSyscall.getOrDefault(num, Syscall.UNSUPPORTED);
	}
}
