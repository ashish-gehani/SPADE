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
package spade.reporter.audit.linux.source.audit.event.record.type;

import spade.reporter.audit.linux.source.audit.event.ID;
import spade.reporter.audit.linux.source.audit.event.record.MalformedRecordException;
import spade.reporter.audit.linux.source.audit.event.record.Record;
import spade.reporter.audit.linux.source.audit.event.record.Type;
import spade.reporter.audit.linux.source.audit.event.record.helper.AuditStringParser;
import spade.reporter.audit.linux.source.audit.event.record.helper.Header;

/**
 * Record subclass for CWD (Current Working Directory) audit records.
 *
 * Example raw data: cwd="/"
 */
public class Cwd extends Record{

	private final String cwd;

	public Cwd(
		final ID id, final String rawRecord
	) throws MalformedRecordException{
		super(id, Type.CWD, rawRecord);
		this.cwd = AuditStringParser.mustParse(rawRecord, "cwd");
	}

	public String getCwd(){
		return cwd;
	}

	public static class Creator extends Record.Creator{

		@Override
		public String validate(final Header header){
			final Type type = header.getType();
			return type == Type.CWD ? null : "Expected CWD, got: " + type;
		}

		@Override
		public Record create(final Header header) throws MalformedRecordException{
			final String error = validate(header);
			if(error != null) throw new MalformedRecordException(error, header.getRawLine());
			return new Cwd(header.getId(), header.getRawLine());
		}
	}
}
