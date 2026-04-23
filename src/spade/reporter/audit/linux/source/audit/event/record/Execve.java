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
package spade.reporter.audit.linux.source.audit.event.record;

import java.util.HashMap;
import java.util.Map;

import spade.reporter.audit.linux.source.audit.event.ID;
import spade.reporter.audit.linux.source.audit.event.record.helper.AuditStringParser;
import spade.reporter.audit.linux.source.audit.event.record.helper.Header;
import spade.reporter.audit.linux.source.audit.event.record.helper.KeyValueParser;
import spade.utility.HelperFunctions;

/**
 * Record subclass for EXECVE audit records.
 *
 * Contains the argument count and individual arguments for an exec call.
 *
 * Example raw data: argc=1 a0="./server_mq"
 */
public class Execve extends Record{

	private final int argc;
	private final Map<Integer, String> args;

	public Execve(
		final ID id, final String rawRecord
	){
		super(id, Type.EXECVE, rawRecord);
		final Map<String, String> tempMap = KeyValueParser.parseKeyValuePairs(rawRecord);
		final Integer parsedArgc = HelperFunctions.parseInt(tempMap.get("argc"), null);
		this.argc = parsedArgc != null ? parsedArgc : 0;
		this.args = new HashMap<>();

		for(int i = 0; i < this.argc; i++){
			final String value = AuditStringParser.parse(rawRecord, "a" + i);
			args.put(i, value != null ? value : "");
		}
	}

	public int getArgc(){
		return argc;
	}

	public String getArg(final int i){
		return args.get(i);
	}

	public Map<Integer, String> getArgs(){
		return new HashMap<>(args);
	}

	public static class Creator extends Record.Creator{

		@Override
		public String validate(final Header header){
			final Type type = header.getType();
			return type == Type.EXECVE ? null : "Expected EXECVE, got: " + type;
		}

		@Override
		public Record create(final Header header) throws MalformedRecordException{
			final String error = validate(header);
			if(error != null) throw new MalformedRecordException(error, header.getRawLine());
			return new Execve(header.getId(), header.getRawLine());
		}
	}
}
