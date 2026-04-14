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
 * Record subclass for IPC (Inter-Process Communication) audit records.
 *
 * Example raw data: ouid=1000 ogid=1000 mode=0666
 */
public class Ipc extends Record{

	public final String ouid;
	public final String ogid;
	public final String mode;

	public Ipc(
		final ID id, final String rawRecord
	){
		super(id, Type.IPC, rawRecord);
		final Map<String, String> parsedFields = KeyValueParser.parseKeyValuePairs(rawRecord);
		this.ouid = parsedFields.get("ouid");
		this.ogid = parsedFields.get("ogid");
		this.mode = parsedFields.get("mode");
	}

	public String getOuid(){
		return ouid;
	}

	public String getOgid(){
		return ogid;
	}

	public String getMode(){
		return mode;
	}

	public static class Creator extends Record.Creator{

		@Override
		public String validate(final Header header){
			final Type type = header.getType();
			return type == Type.IPC ? null : "Expected IPC, got: " + type;
		}

		@Override
		public Record create(final Header header) throws MalformedRecordException{
			final String error = validate(header);
			if(error != null) throw new MalformedRecordException(error, header.getRawLine());
			return new Ipc(header.getId(), header.getRawLine());
		}
	}
}
