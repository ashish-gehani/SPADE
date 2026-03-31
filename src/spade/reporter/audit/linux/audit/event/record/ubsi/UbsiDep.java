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

import spade.reporter.audit.core.event.ID;
import spade.reporter.audit.linux.audit.event.Timestamp;
import spade.reporter.audit.linux.audit.event.record.MalformedRecordException;
import spade.reporter.audit.linux.audit.event.record.Record;
import spade.reporter.audit.linux.audit.event.record.Type;
import spade.reporter.audit.linux.audit.event.record.helper.AuditStringParser;
import spade.reporter.audit.linux.audit.event.record.helper.Header;
import spade.reporter.audit.linux.audit.event.record.helper.KeyValueParser;
import spade.reporter.audit.linux.audit.event.record.helper.ProcessInfo;
import spade.reporter.audit.linux.audit.event.record.helper.StringHelper;

/**
 * Record subclass for UBSI_DEP (dependency) audit records.
 *
 * Represents a dependency between a reading unit and a writing unit.
 * Contains two unit blocks: dep (writing unit, suffix "0") and unit
 * (reading unit, no suffix), followed by process data.
 *
 *
 * UBSI_DEP format:
 * -> type=UBSI_DEP msg=ubsi(1601572509.571:506):
 * 		dep=(pid=701 thread_time=1601572509.571 unitid=901 iteration=0 time=1601572509.571 count=0),
 * 		unit=(pid=702 thread_time=1601572509.571 unitid=898 iteration=0 time=1601572509.571 count=0)
 * 		ppid=700 pid=702 auid=1000 uid=1000 gid=1000 euid=1000 suid=1000 fsuid=1000 egid=1000 sgid=1000 fsgid=1000
 * 		tty=pts0 ses=3 comm="synth" exe="" key=(null)
 *
 */
public class UbsiDep extends Record{

	/** The unit that performed the read. */
	public final Unit readingUnit;
	/** The unit that performed the write. */
	public final Unit writingUnit;
	/** Process identity fields. */
	public final ProcessInfo processInfo;
	/** Path of the executable. */
	public final String exe;

	public UbsiDep(
		final ID eventId, final Timestamp time, final String rawRecord
	) throws MalformedRecordException{
		super(eventId, time, Type.UBSI_DEP, rawRecord);

		this.writingUnit = Unit.parse(rawRecord, "dep");
		this.readingUnit = Unit.parse(rawRecord, "unit");

		final String processData = StringHelper.substringAfter(rawRecord, ") ");
		if(processData == null){
			throw new MalformedRecordException("Missing process data in UBSI_DEP record", rawRecord);
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

	public Unit getReadingUnit(){
		return readingUnit;
	}

	public Unit getWritingUnit(){
		return writingUnit;
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
			return type == Type.UBSI_DEP ? null : "Expected UBSI_DEP, got: " + type;
		}

		@Override
		public Record create(final Header header) throws MalformedRecordException{
			final String error = validate(header);
			if(error != null) throw new MalformedRecordException(error, header.getRawLine());
			return new UbsiDep(header.getEventId(), header.getTime(), header.getRawLine());
		}
	}
}
