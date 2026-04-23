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
package spade.reporter.audit.linux.source.audit.event.record.ubsi;

import java.util.Map;

import spade.reporter.audit.linux.source.audit.event.ID;
import spade.reporter.audit.linux.source.audit.event.record.MalformedRecordException;
import spade.reporter.audit.linux.source.audit.event.record.Record;
import spade.reporter.audit.linux.source.audit.event.record.Type;
import spade.reporter.audit.linux.source.audit.event.record.helper.Header;
import spade.reporter.audit.linux.source.audit.event.record.helper.KeyValueParser;
import spade.reporter.audit.linux.source.audit.event.record.helper.ProcessInfo;
import spade.reporter.audit.linux.source.audit.event.record.helper.StringHelper;

/**
 * Record subclass for USER records with ubsi_intercepted subtype.
 *
 * Contains syscall-like fields from UBSI interception.
 *
 * Raw format:
 *   ubsi_intercepted="syscall=%d success=%s exit=%ld a0=%x a1=%x a2=0 a3=0
 *   items=0 ppid=%d pid=%d uid=%u gid=%u euid=%u suid=%u fsuid=%u egid=%u sgid=%u fsgid=%u comm=%s"
 */
public class UbsiRaw extends Record{

	private static final String UBSI_RAW_RECORD_KEY = "ubsi_intercepted";
	private static final String MARKER_UBSI_RAW_RECORD = (
		UBSI_RAW_RECORD_KEY + "="
	);

	private final int syscall;
	private final boolean success;
	private final long exit;
	private final long a0;
	private final long a1;
	private final long a2;
	private final long a3;
	private final int items;
	private final ProcessInfo processInfo;

	public UbsiRaw(
		final ID id, final String rawRecord
	) throws MalformedRecordException{
		super(id, Type.UBSI_RAW, rawRecord);

		final String subRecord = StringHelper.substringBetween(rawRecord,
				UBSI_RAW_RECORD_KEY + "=\"", "\"");
		if(subRecord == null){
			throw new MalformedRecordException(
					"Missing ubsi_intercepted sub-record in USER record", rawRecord);
		}

		final Map<String, String> fields = KeyValueParser.parseKeyValuePairs(subRecord);
		this.syscall = Integer.parseInt(fields.get("syscall"));
		this.success = parseSuccess(fields.get("success"));
		this.exit = Long.parseLong(fields.get("exit"));
		this.a0 = Long.parseUnsignedLong(fields.get("a0"), 16);
		this.a1 = Long.parseUnsignedLong(fields.get("a1"), 16);
		this.a2 = Long.parseUnsignedLong(fields.get("a2"), 16);
		this.a3 = Long.parseUnsignedLong(fields.get("a3"), 16);
		this.items = Integer.parseInt(fields.get("items"));
		this.processInfo = ProcessInfo.parse(subRecord);
	}

	public int getSyscall(){ return syscall; }
	public boolean getSuccess(){ return success; }
	public long getExit(){ return exit; }
	public long getArg0(){ return a0; }
	public long getArg1(){ return a1; }
	public long getArg2(){ return a2; }
	public long getArg3(){ return a3; }
	public int getItems(){ return items; }
	public ProcessInfo getProcessInfo(){ return processInfo; }

	private static boolean parseSuccess(final String value){
		return "true".equalsIgnoreCase(value)
			|| "yes".equalsIgnoreCase(value)
			|| "1".equals(value);
	}

	public static class Creator extends Record.Creator{

		@Override
		public String validate(final Header header){
			if(header.getType() != spade.reporter.audit.linux.source.audit.event.record.Type.UBSI_RAW){
				return "Expected UBSI_RAW, got: " + header.getType();
			}
			if(!(header.getRawLine().indexOf(MARKER_UBSI_RAW_RECORD) >= 0)){
				return "Expected substr '" + MARKER_UBSI_RAW_RECORD + "', got: " + header.getRawLine();
			}
			return null;
		}

		@Override
		public Record create(final Header header) throws MalformedRecordException{
			final String error = validate(header);
			if(error != null) throw new MalformedRecordException(error, header.getRawLine());
			return new UbsiRaw(header.getId(), header.getRawLine());
		}
	}
}
