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
package spade.reporter.audit.linux.audit.event.record.ubsi;

import java.util.Map;

import spade.reporter.audit.linux.audit.event.ID;
import spade.reporter.audit.linux.audit.event.record.MalformedRecordException;
import spade.reporter.audit.linux.audit.event.record.Record;
import spade.reporter.audit.linux.audit.event.record.Type;
import spade.reporter.audit.linux.audit.event.record.helper.AuditStringParser;
import spade.reporter.audit.linux.audit.event.record.helper.Header;
import spade.reporter.audit.linux.audit.event.record.helper.KeyValueParser;
import spade.reporter.audit.linux.audit.event.record.helper.ProcessInfo;

/**
 * Record subclass for UBSI_EXIT audit records.
 *
 * Marks the end of a unit execution. Contains only process data
 * (no unit block).
 *
 * Format:
 *   ppid=... pid=... ... comm="..." exe="..." key=(null)
 */
public class UbsiExit extends Record{

	/** Process identity fields. */
	public final ProcessInfo processInfo;
	/** Path of the executable. */
	public final String exe;

	public UbsiExit(
		final ID id, final String rawRecord
	) throws MalformedRecordException{
		super(id, Type.UBSI_EXIT, rawRecord);

		final Map<String, String> processFields = KeyValueParser.parseKeyValuePairs(rawRecord);
		this.exe = processFields.get("exe");
		this.processInfo = new ProcessInfo(
			processFields.get("pid"),
			processFields.get("ppid"),
			processFields.get("uid"),
			processFields.get("euid"),
			processFields.get("suid"),
			processFields.get("fsuid"),
			processFields.get("gid"),
			processFields.get("egid"),
			processFields.get("sgid"),
			processFields.get("fsgid"),
			AuditStringParser.mustParse(rawRecord, "comm")
		);
	}

	public ProcessInfo getProcessInfo(){
		return processInfo;
	}

	public String getExe(){
		return exe;
	}

	public static class Creator extends Record.Creator{

		@Override
		public String validate(final Header header){
			final Type type = header.getType();
			return type == Type.UBSI_EXIT ? null : "Expected UBSI_EXIT, got: " + type;
		}

		@Override
		public Record create(final Header header) throws MalformedRecordException{
			final String error = validate(header);
			if(error != null) throw new MalformedRecordException(error, header.getRawLine());
			return new UbsiExit(header.getId(), header.getRawLine());
		}
	}
}
