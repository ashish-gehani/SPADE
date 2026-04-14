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

import spade.reporter.audit.core.event.MalformedEventException;
import spade.reporter.audit.linux.audit.event.record.Record;

/**
 * Event for UBSI_ENTRY events.
 *
 * Contains a single {@link UbsiEntry} record that marks the start
 * of a new unit execution.
 */
public class UbsiEntry extends Event{

	private final spade.reporter.audit.linux.audit.event.record.ubsi.UbsiEntry record;

	protected UbsiEntry(final spade.reporter.audit.linux.audit.event.record.ubsi.UbsiEntry record){
		super(record.getId(), Type.UBSI_ENTRY);
		setRecords(Arrays.asList(record));
		this.record = record;
	}

	public spade.reporter.audit.linux.audit.event.record.ubsi.UbsiEntry getUbsiEntryRecord(){
		return record;
	}

	public String getPid(){
		return record.processInfo.pid;
	}

	public String getUnitId(){
		return record.unit.id;
	}

	public String getUnitIteration(){
		return record.unit.iteration;
	}

	public String getUnitCount(){
		return record.unit.count;
	}

	public String getUnitStartTime(){
		return record.unit.time;
	}

	protected static class Creator extends Event.Creator{

		@Override
		protected String validate(final List<Record> records){
			if(records == null || records.size() != 1){
				return "Expected exactly one record for UbsiEntry, got: "
					+ (records == null ? "null" : records.size());
			}
			final Record r = records.get(0);
			if(!(r.getType() == spade.reporter.audit.linux.audit.event.record.Type.UBSI_ENTRY)){
				return "Expected UBSI_ENTRY record, got type: " + r.getType();
			}
			if(!(r instanceof spade.reporter.audit.linux.audit.event.record.ubsi.UbsiEntry)){
				return "Expected UBSI_ENTRY record, got class: " + r.getClass().getName();
			}
			return null;
		}

		@Override
		protected Event create(final List<Record> records) throws MalformedEventException{
			final String error = validate(records);
			if(error != null) throw new MalformedEventException(error);
			return new UbsiEntry(
				(spade.reporter.audit.linux.audit.event.record.ubsi.UbsiEntry) records.get(0));
		}
	}
}
