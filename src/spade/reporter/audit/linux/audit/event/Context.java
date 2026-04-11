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

import spade.reporter.audit.linux.audit.event.record.Record;

/**
 * Parsing context for Linux Audit Subsystem events.
 *
 * Accumulates the {@link Record}s that belong to the same event (identified by
 * {@link ID} and {@link Timestamp}) so that {@link Factory} can construct a
 * typed {@link Event} once all records have been collected.
 */
public class Context extends spade.reporter.audit.core.event.Context{

	private ID id;
	private Timestamp timestamp;

	private final List<Record> records = new ArrayList<Record>();

	public Context(){
		super();
	}

	public ID getId(){
		return id;
	}

	public Timestamp getTimestamp(){
		return timestamp;
	}

	public void set(final ID id, final Timestamp timestamp){
		if(id == null){
			throw new IllegalArgumentException("ID cannot be NULL");
		}
		if(timestamp == null){
			throw new IllegalArgumentException("Timestamp cannot be NULL");
		}
		setId(id);
		setTimestamp(timestamp);
	}

	public boolean isSet(){
		return id != null && timestamp != null;
	}

	public boolean matches(final ID id, final Timestamp timestamp){
		return this.id != null && this.id.equals(id)
			&& this.timestamp != null && this.timestamp.equals(timestamp);
	}

	private void setId(final ID id){
		this.id = id;
	}

	private void setTimestamp(final Timestamp timestamp){
		this.timestamp = timestamp;
	}

	public void addRecord(final Record record){
		if(record == null){
			throw new IllegalArgumentException("Record cannot be NULL");
		}
		records.add(record);
	}

	public List<Record> getRecords(){
		return records;
	}

	public void reset(){
		this.id = null;
		this.timestamp = null;
		this.records.clear();
	}

}
