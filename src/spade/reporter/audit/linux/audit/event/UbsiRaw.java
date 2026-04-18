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
package spade.reporter.audit.linux.audit.event;

import java.util.Arrays;
import java.util.List;

import spade.reporter.audit.core.source.MalformedEventException;
import spade.reporter.audit.linux.audit.event.record.Record;

/**
 * Event for UBSI_RAW events.
 *
 * Contains a single {@link UBSIRaw} record with syscall-like fields
 * from UBSI interception.
 */
public class UbsiRaw extends Event{

	private final spade.reporter.audit.linux.audit.event.record.ubsi.UbsiRaw record;

	protected UbsiRaw(
		final spade.reporter.audit.linux.audit.event.record.ubsi.UbsiRaw record
	){
		super(record.getId(), Type.UBSI_RAW);
		setRecords(Arrays.asList(record));
		this.record = record;
	}

	public spade.reporter.audit.linux.audit.event.record.ubsi.UbsiRaw getUBSIRawRecord(){
		return record;
	}

	protected static class Creator extends Event.Creator{

		@Override
		protected String validate(final List<Record> records){
			if(records == null || records.size() != 1){
				return "Expected exactly one record for UbsiRaw event, got: "
					+ (records == null ? "null" : records.size());
			}
			final Record r = records.get(0);
			if(!(r.getType() == spade.reporter.audit.linux.audit.event.record.Type.UBSI_RAW)){
				return "Expected UBSI_RAW record, got type: " + r.getType();
			}
			if(!(r instanceof spade.reporter.audit.linux.audit.event.record.ubsi.UbsiRaw)){
				return "Expected UBSI_RAW record, got class: " + r.getClass().getName();
			}
			return null;
		}

		@Override
		protected Event create(final List<Record> records) throws MalformedEventException{
			final String error = validate(records);
			if(error != null) throw new MalformedEventException(error);
			return new UbsiRaw(
				(spade.reporter.audit.linux.audit.event.record.ubsi.UbsiRaw) records.get(0));
		}
	}
}
