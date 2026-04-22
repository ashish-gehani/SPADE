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
package spade.reporter.audit.linux.source.audit.event.record.type.ubsi;

import java.util.Map;

import spade.reporter.audit.linux.source.audit.event.ID;
import spade.reporter.audit.linux.source.audit.event.record.MalformedRecordException;
import spade.reporter.audit.linux.source.audit.event.record.Record;
import spade.reporter.audit.linux.source.audit.event.record.Type;
import spade.reporter.audit.linux.source.audit.event.record.helper.AuditStringParser;
import spade.reporter.audit.linux.source.audit.event.record.helper.Header;
import spade.reporter.audit.linux.source.audit.event.record.helper.KeyValueParser;
import spade.reporter.audit.linux.source.audit.event.record.helper.ProcessInfo;
import spade.reporter.audit.linux.source.audit.event.record.helper.StringHelper;

/**
 * Record subclass for UBSI_ENTRY audit records.
 *
 * Marks the start of a new unit execution. Contains a single unit block
 * with unit identification fields, followed by process data.
 *
 * Format:
 *   unit=(pid=X thread_time=Y unitid=Z iteration=W time=T count=C)
 *   ppid=... pid=... ... comm="..." exe="..." key=(null)
 */
public class UbsiEntry extends Record{

	/** The unit that started execution. */
	public final Unit unit;
	/** Process identity fields. */
	public final ProcessInfo processInfo;
	/** Path of the executable. */
	public final String exe;

	public UbsiEntry(
		final ID id, final String rawRecord
	) throws MalformedRecordException{
		super(id, Type.UBSI_ENTRY, rawRecord);

		this.unit = Unit.parse(rawRecord, "unit");

		final String processData = StringHelper.substringAfter(rawRecord, ") ");
		if(processData == null){
			throw new MalformedRecordException("Missing process data in UBSI_ENTRY record", rawRecord);
		}
		final Map<String, String> processFields = KeyValueParser.parseKeyValuePairs(processData);
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
			AuditStringParser.mustParse(processData, "comm")
		);
	}

	public Unit getUnit(){
		return unit;
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
			return type == Type.UBSI_ENTRY ? null : "Expected UBSI_ENTRY, got: " + type;
		}

		@Override
		public Record create(final Header header) throws MalformedRecordException{
			final String error = validate(header);
			if(error != null) throw new MalformedRecordException(error, header.getRawLine());
			return new UbsiEntry(header.getId(), header.getRawLine());
		}
	}
}
