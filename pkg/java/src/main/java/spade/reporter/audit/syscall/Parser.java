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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import spade.utility.setting.Helper;
import spade.utility.setting.InvalidSettingException;
import spade.utility.setting.Setting;
import spade.utility.setting.convert.Enums;
import spade.utility.setting.convert.numbers.Ints;
import spade.utility.setting.keyvalue.KeyValue;

/**
 * Parses a system call table config file. One system call per line, name to number:
 *
 * <pre>
 * read = 0
 * write = 1
 * </pre>
 *
 * Each name must be a {@link Type} (in lowercase), other than {@link Type#UNSUPPORTED}. Numbers must be unique. If a name appears more than once, the first occurrence is used (see
 * spade/utility/setting/DESIGN.md).
 */
public class Parser{

	public static Config parse(final String configFilePath) throws InvalidSettingException{
		final Setting setting;
		try{
			setting = Helper.create("", configFilePath);
		}catch(Exception e){
			throw new InvalidSettingException(
					"Failed to read config file '" + configFilePath + "'", e);
		}

		final List<Syscall> syscalls = new ArrayList<Syscall>();
		final Map<Integer, String> numToName = new HashMap<Integer, String>();
		for(final KeyValue keyValue : setting.getKeyValues()){
			final String name = keyValue.getKey().getFullName();
			final Type type = parseType(name, configFilePath);
			final int num = parseNum(keyValue, name, configFilePath);
			final String existingName = numToName.putIfAbsent(num, name);
			if(existingName != null){
				throw new InvalidSettingException(
						"Duplicate number " + num + " for '" + existingName + "' and '" + name + "' in config file '" + configFilePath + "'");
			}
			syscalls.add(new Syscall(type, num, name));
		}

		return new Config(syscalls);
	}

	private static Type parseType(final String name, final String configFilePath) throws InvalidSettingException{
		final Type type;
		try{
			type = Enums.parseEnum(name.toUpperCase(Locale.ROOT), Type.class);
		}catch(IllegalArgumentException e){
			throw new InvalidSettingException(
					"Unknown system call name '" + name + "' in config file '" + configFilePath + "': " + e.getMessage(), e);
		}
		if(type == Type.UNSUPPORTED){
			throw new InvalidSettingException(
					"Reserved system call name '" + name + "' in config file '" + configFilePath + "'");
		}
		return type;
	}

	private static int parseNum(final KeyValue keyValue, final String name, final String configFilePath) throws InvalidSettingException{
		try{
			return Ints.parse(keyValue.getValue().getResolvedValue(), 0, null);
		}catch(IllegalArgumentException e){
			throw new InvalidSettingException(
					"Invalid value for '" + name + "' in config file '" + configFilePath + "': " + e.getMessage(), e);
		}
	}
}
