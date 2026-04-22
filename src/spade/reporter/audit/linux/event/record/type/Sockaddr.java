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
package spade.reporter.audit.linux.event.record.type;

import java.util.Map;

import spade.reporter.audit.linux.event.ID;
import spade.reporter.audit.linux.event.record.MalformedRecordException;
import spade.reporter.audit.linux.event.record.Record;
import spade.reporter.audit.linux.event.record.Type;
import spade.reporter.audit.linux.event.record.helper.Header;
import spade.reporter.audit.linux.event.record.helper.KeyValueParser;

/**
 * Record subclass for SOCKADDR audit records.
 *
 * Contains socket address information.
 *
 * Example raw data: saddr=0100
 */
public class Sockaddr extends Record{

	/** Raw socket address value. */
	public final String saddr;

	public Sockaddr(
		final ID id, final String rawRecord
	){
		super(id, Type.SOCKADDR, rawRecord);
		final Map<String, String> parsedFields = KeyValueParser.parseKeyValuePairs(rawRecord);
		this.saddr = parsedFields.get("saddr");
	}

	public String getSaddr(){
		return saddr;
	}

	public static class Creator extends Record.Creator{

		@Override
		public String validate(final Header header){
			final Type type = header.getType();
			return type == Type.SOCKADDR ? null : "Expected SOCKADDR, got: " + type;
		}

		@Override
		public Record create(final Header header) throws MalformedRecordException{
			final String error = validate(header);
			if(error != null) throw new MalformedRecordException(error, header.getRawLine());
			return new Sockaddr(header.getId(), header.getRawLine());
		}
	}
}
