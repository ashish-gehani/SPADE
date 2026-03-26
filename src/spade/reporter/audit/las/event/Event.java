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

import java.util.ArrayList;
import java.util.List;

import spade.reporter.audit.las.event.record.Record;

/**
 * Abstract class representing a Linux Audit Subsystem event.
 *
 * An event is an ordered collection of records that share the same event ID
 * and timestamp. The event type is determined by the primary record.
 */
public abstract class Event{

	private final String eventId;
	private final String time;
	private final Type eventType;
	private final List<Record> records = new ArrayList<>();

	protected Event(
		final String eventId, final String time, final Type eventType
	){
		this.eventId = eventId;
		this.time = time;
		this.eventType = eventType;
	}

	public String getEventId(){
		return eventId;
	}

	public String getTime(){
		return time;
	}

	public Type getEventType(){
		return eventType;
	}

	public List<Record> getRecords(){
		return new ArrayList<>(records);
	}

	protected void addRecord(final Record record){
		if (record == null) {
			return;
		}
		this.records.add(record);
	}

	protected void clearRecords(){
		this.records.clear();
	}

	@Override
	public String toString(){
		return "Event [eventId=" + eventId + ", time=" + time + ", eventType=" + eventType
				+ ", recordCount=" + records.size() + "]";
	}

	protected static abstract class Creator{

		/*
			Returns null if the list of records is valid for this event type,
			or a detailed error message string if not.
		*/
		protected abstract String validate(final List<Record> records);

		/*
			Given a list of records, check whether the event of class
			(in the outer scope) can be created or not.

			Returns true if yes, else false
		*/
		protected final boolean matches(final List<Record> records){
			return validate(records) == null;
		}

		protected abstract Event create(
			final List<Record> records
		) throws MalformedEventException;

	}
}
