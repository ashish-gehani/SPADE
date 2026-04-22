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
package spade.reporter.audit.linux.event.type;

import java.util.Arrays;
import java.util.List;

import spade.reporter.audit.linux.event.Event;
import spade.reporter.audit.linux.event.Type;
import spade.reporter.audit.core.event.MalformedEventException;
import spade.reporter.audit.linux.event.record.Record;

/**
 * Event for UBSI_EXIT events.
 *
 * Contains a single {@link UbsiExit} record that marks the end
 * of a unit execution.
 */
public class UbsiExit extends Event{

	private final spade.reporter.audit.linux.event.record.type.ubsi.UbsiExit record;

	protected UbsiExit(final spade.reporter.audit.linux.event.record.type.ubsi.UbsiExit record){
		super(record.getId(), Type.UBSI_EXIT);
		setRecords(Arrays.asList(record));
		this.record = record;
	}

	public spade.reporter.audit.linux.event.record.type.ubsi.UbsiExit getUbsiExitRecord(){
		return record;
	}

	public static class Creator extends Event.Creator{

		@Override
		protected String validate(final List<Record> records){
			if(records == null || records.size() != 1){
				return "Expected exactly one record for UbsiExit, got: "
					+ (records == null ? "null" : records.size());
			}
			final Record r = records.get(0);
			if(!(r.getType() == spade.reporter.audit.linux.event.record.Type.UBSI_EXIT)){
				return "Expected UBSI_EXIT record, got type: " + r.getType();
			}
			if(!(r instanceof spade.reporter.audit.linux.event.record.type.ubsi.UbsiExit)){
				return "Expected UBSI_EXIT record, got class: " + r.getClass().getName();
			}
			return null;
		}

		@Override
		protected Event create(final List<Record> records) throws MalformedEventException{
			final String error = validate(records);
			if(error != null) throw new MalformedEventException(error);
			return new UbsiExit(
				(spade.reporter.audit.linux.event.record.type.ubsi.UbsiExit) records.get(0));
		}
	}
}
