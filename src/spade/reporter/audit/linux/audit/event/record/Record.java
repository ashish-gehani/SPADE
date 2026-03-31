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
package spade.reporter.audit.linux.audit.event.record;

import spade.reporter.audit.linux.audit.event.ID;
import spade.reporter.audit.linux.audit.event.Timestamp;
import spade.reporter.audit.linux.audit.event.record.helper.Header;

/**
 * Abstract class representing a single Linux Audit Subsystem record.
 *
 * A record is one line in the audit log. Multiple records with the same
 * event ID form an event.
 *
 * Each subclass holds record-type-specific parsed fields.
 */
public abstract class Record{

	private final ID eventId;
	private final Timestamp time;
	private final Type type;
	private final String rawRecord;

	protected Record(final ID eventId, final Timestamp time, final Type type,
			final String rawRecord){
		this.eventId = eventId;
		this.time = time;
		this.type = type;
		this.rawRecord = rawRecord;
	}

	public ID getEventId(){
		return eventId;
	}

	public Timestamp getTime(){
		return time;
	}

	public Type getType(){
		return type;
	}

	public String getRawRecord(){
		return rawRecord;
	}

	@Override
	public int hashCode(){
		final int prime = 31;
		int result = 1;
		result = prime * result + ((eventId == null) ? 0 : eventId.hashCode());
		result = prime * result + ((time == null) ? 0 : time.hashCode());
		result = prime * result + ((type == null) ? 0 : type.hashCode());
		result = prime * result + ((rawRecord == null) ? 0 : rawRecord.hashCode());
		return result;
	}

	@Override
	public boolean equals(final Object obj){
		if(this == obj)
			return true;
		if(obj == null)
			return false;
		if(getClass() != obj.getClass())
			return false;
		final Record other = (Record)obj;
		if(eventId == null){
			if(other.eventId != null)
				return false;
		}else if(!eventId.equals(other.eventId))
			return false;
		if(time == null){
			if(other.time != null)
				return false;
		}else if(!time.equals(other.time))
			return false;
		if(type != other.type)
			return false;
		if(rawRecord == null){
			if(other.rawRecord != null)
				return false;
		}else if(!rawRecord.equals(other.rawRecord))
			return false;
		return true;
	}

	@Override
	public String toString(){
		return "Record [rawRecord=" + rawRecord + "]";
	}

	public static abstract class Creator{

		/*
			Returns null if the header is valid for this record type,
			or a detailed error message string if not.
		*/
		public abstract String validate(final Header header);

		/*
			Given a parsed header, check whether the record of class
			(in the outer scope) can be created or not.

			Returns true if yes, else false
		*/
		public final boolean matches(final Header header){
			return validate(header) == null;
		}

		public abstract Record create(
			final Header header
		) throws MalformedRecordException;

	}
}
