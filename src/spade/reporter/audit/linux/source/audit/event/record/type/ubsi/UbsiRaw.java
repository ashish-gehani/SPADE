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
 * Record subclass for USER records with ubsi_intercepted subtype.
 *
 * Contains syscall-like fields from UBSI interception.
 *
 * Raw format:
 *   ubsi_intercepted="syscall=%d success=%s exit=%ld a0=%x a1=%x a2=0 a3=0
 *   items=0 ppid=%d pid=%d uid=%u gid=%u euid=%u suid=%u fsuid=%u egid=%u sgid=%u fsgid=%u comm=%s"
 */
public class UbsiRaw extends Record {

	private static final String UBSI_RAW_RECORD_KEY = "ubsi_intercepted";
	private static final String MARKER_UBSI_RAW_RECORD = (
		UBSI_RAW_RECORD_KEY + "="
	);

	private final String syscall;
	private final String success;
	private final String exit;
	private final String a0;
	private final String a1;
	private final String a2;
	private final String a3;
	private final String items;
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
		this.syscall = fields.get("syscall");
		this.success = fields.get("success");
		this.exit = fields.get("exit");
		this.a0 = fields.get("a0");
		this.a1 = fields.get("a1");
		this.a2 = fields.get("a2");
		this.a3 = fields.get("a3");
		this.items = fields.get("items");
		this.processInfo = new ProcessInfo(
				fields.get("pid"), fields.get("ppid"),
				fields.get("uid"), fields.get("euid"), fields.get("suid"), fields.get("fsuid"),
				fields.get("gid"), fields.get("egid"), fields.get("sgid"), fields.get("fsgid"),
				AuditStringParser.mustParse(subRecord, "comm"));
	}

	public String getSyscall(){ return syscall; }
	public String getSuccess(){ return success; }
	public String getExit(){ return exit; }
	public String getArg0(){ return a0; }
	public String getArg1(){ return a1; }
	public String getArg2(){ return a2; }
	public String getArg3(){ return a3; }
	public String getItems(){ return items; }
	public ProcessInfo getProcessInfo(){ return processInfo; }

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
