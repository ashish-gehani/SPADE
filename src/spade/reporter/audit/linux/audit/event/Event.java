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

import java.util.ArrayList;
import java.util.List;

import spade.reporter.audit.core.source.MalformedEventException;
import spade.reporter.audit.linux.audit.event.record.Record;

/**
 * Concrete audit event for the Linux Audit Subsystem.
 *
 * Wraps a {@link spade.reporter.audit.core.source.Event} and exposes it
 * through the abstract {@link spade.reporter.audit.core.source.Event} contract.
 */
public abstract class Event extends spade.reporter.audit.core.source.Event{

	private final Type type;

	private final List<Record> records = new ArrayList<Record>();

	protected Event(final ID id, final Type type){
		super(id);
		if(type == null){
			throw new IllegalArgumentException("LAS event type cannot be NULL");
		}
		this.type = type;
	}

	public ID getId(){
		return (ID) super.getId();
	}

	public Num getNum(){
		return (Num) getId().getNum();
	}

	public Timestamp getTimestamp(){
		return (Timestamp) getId().getTimestamp();
	}

	public Type getType(){
		return type;
	}

	public void setRecords(final List<Record> records){
		if(records == null){
			throw new IllegalArgumentException("Records list cannot be NULL");
		}
		for(int i = 0; i < records.size(); i++){
			if(records.get(i) == null){
				throw new IllegalArgumentException("Record at index " + i + " cannot be NULL");
			}
		}
		this.records.clear();
		this.records.addAll(records);
	}

	public void unsetRecords(){
		this.records.clear();
	}

	public List<Record> getRecords(){
		return records;
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
