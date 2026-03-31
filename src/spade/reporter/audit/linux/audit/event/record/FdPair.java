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
package spade.reporter.audit.linux.audit.event.record;

import java.util.Map;

import spade.reporter.audit.core.event.ID;
import spade.reporter.audit.linux.audit.event.Timestamp;
import spade.reporter.audit.linux.audit.event.record.helper.Header;
import spade.reporter.audit.linux.audit.event.record.helper.KeyValueParser;

/**
 * Record subclass for FD_PAIR audit records.
 *
 * Contains a pair of file descriptors.
 *
 * Example raw data: fd0=3 fd1=4
 */
public class FdPair extends Record{

	public final String fd0;
	public final String fd1;

	public FdPair(
		final ID eventId, final Timestamp time, final String rawRecord
	){
		super(eventId, time, Type.FD_PAIR, rawRecord);
		final Map<String, String> parsedFields = KeyValueParser.parseKeyValuePairs(rawRecord);
		this.fd0 = parsedFields.get("fd0");
		this.fd1 = parsedFields.get("fd1");
	}

	public String getFd0(){
		return fd0;
	}

	public String getFd1(){
		return fd1;
	}

	public static class Creator extends Record.Creator{

		@Override
		public String validate(final Header header){
			final Type type = header.getType();
			return type == Type.FD_PAIR ? null : "Expected FD_PAIR, got: " + type;
		}

		@Override
		public Record create(final Header header) throws MalformedRecordException{
			final String error = validate(header);
			if(error != null) throw new MalformedRecordException(error, header.getRawLine());
			return new FdPair(header.getEventId(), header.getTime(), header.getRawLine());
		}
	}
}
