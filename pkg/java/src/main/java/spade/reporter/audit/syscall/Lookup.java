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
import java.util.EnumMap;
import java.util.Map;

import spade.reporter.audit.arch.Type;
import spade.utility.setting.InvalidSettingException;

/**
 * System call tables for every supported arch, each loaded from its default config file.
 */
public class Lookup{

	private final Map<Type, Table> archToTable;

	public Lookup() throws InvalidSettingException{
		final Map<Type, Table> archToTable = new EnumMap<Type, Table>(Type.class);
		for(final Type arch : Type.values()){
			archToTable.put(arch, createTable(arch));
		}
		this.archToTable = Collections.unmodifiableMap(archToTable);
	}

	private static Table createTable(final Type arch) throws InvalidSettingException{
		switch(arch){
			case X86_64:	return new spade.reporter.audit.arch.x86_64.syscall.Table();
			case ARM64:		return new spade.reporter.audit.arch.arm64.syscall.Table();
			default:		throw new IllegalArgumentException("Unexpected arch: " + arch);
		}
	}

	/**
	 * Returns the system call with the number in the arch's table, or {@link Syscall#UNSUPPORTED} if the table has none.
	 */
	public Syscall getByNum(final Type arch, final int num){
		return getTable(arch).getByNum(num);
	}

	/**
	 * Returns the system call with the name in the arch's table, or {@link Syscall#UNSUPPORTED} if the table has none.
	 */
	public Syscall getByName(final Type arch, final String name){
		return getTable(arch).getByName(name);
	}

	private Table getTable(final Type arch){
		if(arch == null){
			throw new IllegalArgumentException("NULL arch");
		}
		return archToTable.get(arch);
	}
}
