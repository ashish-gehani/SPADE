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

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import spade.reporter.audit.syscall.Lookup;
import spade.utility.setting.Helper;
import spade.utility.setting.InvalidSettingException;

/**
 * Operations for each {@link Type}, as read from the operation config file.
 */
public class Table{

	private final Map<Type, Operation> typeToOperation;

	public Table(final Lookup lookup) throws InvalidSettingException{
		this(Helper.getDefaultConfigFilePath(Table.class), lookup);
	}

	/**
	 * System call names in the config file are checked against the arch system call tables in the lookup.
	 */
	public Table(final String configFilePath, final Lookup lookup) throws InvalidSettingException{
		final Config config = Parser.parse(configFilePath, lookup);

		final Map<Type, Operation> typeToOperation = new EnumMap<Type, Operation>(Type.class);
		for(final Operation operation : config.getOperations()){
			typeToOperation.put(operation.getType(), operation);
		}
		this.typeToOperation = Collections.unmodifiableMap(typeToOperation);
	}

	public Operation get(final Type type){
		return typeToOperation.get(type);
	}
}
