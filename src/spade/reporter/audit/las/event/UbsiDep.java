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
package spade.reporter.audit.las.event;

import java.util.List;

import spade.reporter.audit.las.event.record.Record;

/**
 * Event for UBSI_DEP (dependency) events.
 *
 * Contains a single {@link UbsiDep} record with reading and writing unit data.
 */
public class UbsiDep extends Event{

	private final spade.reporter.audit.las.event.record.ubsi.UbsiDep record;

	public UbsiDep(final spade.reporter.audit.las.event.record.ubsi.UbsiDep record){
		super(record.getEventId(), record.getTime(), Type.UBSI_DEP);
		addRecord(record);
		this.record = record;
	}

	public spade.reporter.audit.las.event.record.ubsi.UbsiDep getUbsiDepRecord(){
		return record;
	}

	protected static class Creator extends Event.Creator{

		@Override
		protected String validate(final List<Record> records){
			if(records == null || records.size() != 1){
				return "Expected exactly one record for UbsiDep, got: "
					+ (records == null ? "null" : records.size());
			}
			final Record r = records.get(0);
			if(!(r instanceof spade.reporter.audit.las.event.record.ubsi.UbsiDep)){
				return "Expected UbsiDep record, got: "
					+ (r == null ? "null" : r.getClass().getName());
			}
			return null;
		}

		@Override
		protected Event create(final List<Record> records) throws MalformedEventException{
			final String error = validate(records);
			if(error != null) throw new MalformedEventException(error);
			return new UbsiDep((spade.reporter.audit.las.event.record.ubsi.UbsiDep) records.get(0));
		}
	}
}
