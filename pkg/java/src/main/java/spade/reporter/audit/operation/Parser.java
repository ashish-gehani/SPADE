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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import spade.reporter.audit.syscall.Syscall;
import spade.reporter.audit.syscall.Lookup;
import spade.utility.setting.Helper;
import spade.utility.setting.InvalidSettingException;
import spade.utility.setting.Setting;
import spade.utility.setting.SettingConvertException;
import spade.utility.setting.convert.CSV;
import spade.utility.setting.convert.Strings;

/**
 * Parses an operation config file. Keys per operation in {@link Type}:
 *
 * <pre>
 * READ.name = read
 * READ.syscalls = read,readv,pread,preadv
 * UNIT_DEPENDENCY.name = "unit dependency"
 * </pre>
 *
 * Every operation must have a non-blank name, and names must be unique. The syscalls key is optional: a comma-separated
 * list of system call names. Each system call name must be in the system call table of at least one arch, and can be
 * listed only once across all operations.
 */
public class Parser{

	private static final String KEY_NAME = "name";
	private static final String KEY_SYSCALLS = "syscalls";

	public static Config parse(final String configFilePath, final Lookup lookup) throws InvalidSettingException{
		final Setting setting;
		try{
			setting = Helper.create("", configFilePath);
		}catch(Exception e){
			throw new InvalidSettingException(
					"Failed to read config file '" + configFilePath + "'", e);
		}

		final List<Operation> operations = new ArrayList<Operation>();
		final Map<String, Type> nameToType = new HashMap<String, Type>();
		final Map<String, Type> syscallNameToType = new HashMap<String, Type>();
		for(final Type type : Type.values()){
			final String name = parseName(setting, type, configFilePath);
			final Type existingType = nameToType.putIfAbsent(name, type);
			if(existingType != null){
				throw new InvalidSettingException(
						"Duplicate name '" + name + "' for '" + existingType + "' and '" + type + "' in config file '" + configFilePath + "'");
			}
			final List<String> syscallNames = parseSyscallNames(setting, type, lookup, configFilePath);
			for(final String syscallName : syscallNames){
				final Type existingSyscallType = syscallNameToType.putIfAbsent(syscallName, type);
				if(existingSyscallType != null){
					throw new InvalidSettingException(
							"Duplicate system call name '" + syscallName + "' for '" + existingSyscallType + "' and '" + type + "' in config file '" + configFilePath + "'");
				}
			}
			operations.add(new Operation(type, name, syscallNames));
		}

		return new Config(operations);
	}

	private static String parseName(final Setting setting, final Type type, final String configFilePath) throws InvalidSettingException{
		final String key = type.name() + "." + KEY_NAME;
		try{
			return Strings.getNonBlankString(setting, key);
		}catch(SettingConvertException e){
			throw new InvalidSettingException(
					"Invalid value for '" + key + "' in config file '" + configFilePath + "': " + e.getMessage(), e);
		}
	}

	private static List<String> parseSyscallNames(final Setting setting, final Type type, final Lookup lookup, final String configFilePath) throws InvalidSettingException{
		final String key = type.name() + "." + KEY_SYSCALLS;
		final List<String> syscallNames;
		try{
			syscallNames = CSV.optCommaSeparatedStrings(setting, key, List.of());
		}catch(SettingConvertException e){
			throw new InvalidSettingException(
					"Invalid value for '" + key + "' in config file '" + configFilePath + "': " + e.getMessage(), e);
		}

		final Set<String> seen = new HashSet<String>();
		for(final String syscallName : syscallNames){
			if(syscallName.isBlank()){
				throw new InvalidSettingException(
						"Blank system call name for '" + key + "' in config file '" + configFilePath + "'");
			}
			if(!seen.add(syscallName)){
				throw new InvalidSettingException(
						"Duplicate system call name '" + syscallName + "' for '" + key + "' in config file '" + configFilePath + "'");
			}
			if(!isInAnyArch(lookup, syscallName)){
				throw new InvalidSettingException(
						"Unknown system call name '" + syscallName + "' for '" + key + "' in config file '" + configFilePath + "': not in any arch system call table");
			}
		}
		return syscallNames;
	}

	private static boolean isInAnyArch(final Lookup lookup, final String syscallName){
		for(final spade.reporter.audit.arch.Type arch : spade.reporter.audit.arch.Type.values()){
			if(lookup.getByName(arch, syscallName) != Syscall.UNSUPPORTED){
				return true;
			}
		}
		return false;
	}
}
