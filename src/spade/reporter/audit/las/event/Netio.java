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
 * Event subclass for NETIO_INTERCEPTED events.
 * Contains a single NetioInterceptedRecord (from USER record).
 */
public class Netio extends Event{

	private final spade.reporter.audit.las.event.record.Netio record;

	public Netio(
		final spade.reporter.audit.las.event.record.Netio record
	){
		super(record.getEventId(), record.getTime(), Type.NETIO);
		addRecord(record);
		this.record = record;
	}

	public spade.reporter.audit.las.event.record.Netio getNetioRecord(){
		return record;
	}

	protected static class Creator extends Event.Creator{

		@Override
		protected String validate(final List<Record> records){
			if(records == null || records.size() != 1){
				return "Expected exactly one record for Netio event, got: "
					+ (records == null ? "null" : records.size());
			}
			final Record r = records.get(0);
			if(!(r instanceof spade.reporter.audit.las.event.record.Netio)){
				return "Expected Netio record, got: "
					+ (r == null ? "null" : r.getClass().getName());
			}
			return null;
		}

		@Override
		protected Event create(final List<Record> records) throws MalformedEventException{
			final String error = validate(records);
			if(error != null) throw new MalformedEventException(error);
			return new Netio(
				(spade.reporter.audit.las.event.record.Netio) records.get(0));
		}

	}
}
