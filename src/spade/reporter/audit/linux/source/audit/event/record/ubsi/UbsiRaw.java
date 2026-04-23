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

import spade.reporter.audit.linux.source.audit.event.ID;
import spade.reporter.audit.linux.source.audit.event.record.MalformedRecordException;
import spade.reporter.audit.linux.source.audit.event.record.Record;
import spade.reporter.audit.linux.source.audit.event.record.Type;
import spade.reporter.audit.linux.source.audit.event.record.helper.Header;
import spade.reporter.audit.linux.source.audit.event.record.helper.StringHelper;
import spade.reporter.audit.linux.source.audit.event.record.helper.SyscallInfo;

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

	private final SyscallInfo syscallInfo;

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

		this.syscallInfo = SyscallInfo.parse(subRecord);
	}

	public SyscallInfo getSyscallInfo(){ return syscallInfo; }

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
