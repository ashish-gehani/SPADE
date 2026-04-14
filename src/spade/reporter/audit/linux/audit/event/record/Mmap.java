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

import spade.reporter.audit.linux.audit.event.ID;
import spade.reporter.audit.linux.audit.event.record.helper.Header;
import spade.reporter.audit.linux.audit.event.record.helper.KeyValueParser;

/**
 * Record subclass for MMAP audit records.
 *
 * Contains memory mapping information.
 *
 * Example raw data: fd=3 flags=0x2
 */
public class Mmap extends Record{

	public final String fd;
	public final String flags;

	public Mmap(
		final ID id, final String rawRecord
	){
		super(id, Type.MMAP, rawRecord);
		final Map<String, String> parsedFields = KeyValueParser.parseKeyValuePairs(rawRecord);
		this.fd = parsedFields.get("fd");
		this.flags = parsedFields.get("flags");
	}

	public String getFd(){
		return fd;
	}

	public String getFlags(){
		return flags;
	}

	public static class Creator extends Record.Creator{

		@Override
		public String validate(final Header header){
			final Type type = header.getType();
			return type == Type.MMAP ? null : "Expected MMAP, got: " + type;
		}

		@Override
		public Record create(final Header header) throws MalformedRecordException{
			final String error = validate(header);
			if(error != null) throw new MalformedRecordException(error, header.getRawLine());
			return new Mmap(header.getId(), header.getRawLine());
		}
	}
}
