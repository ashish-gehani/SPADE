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
package spade.reporter.audit.linux.event;

import java.util.ArrayList;
import java.util.List;

import spade.reporter.audit.linux.event.record.Record;

/**
 * Parsing context for Linux Audit Subsystem events.
 *
 * Accumulates the {@link Record}s that belong to the same event (identified by
 * {@link ID}) so that {@link Factory} can construct a
 * typed {@link Event} once all records have been collected.
 */
public class Context{

	private ID id;

	private final List<Record> records = new ArrayList<Record>();

	public Context(){
		super();
	}

	public ID getId(){
		return id;
	}

	public void set(final ID id){
		if(id == null){
			throw new IllegalArgumentException("ID cannot be NULL");
		}
		this.id = id;
	}

	public boolean isSet(){
		return id != null;
	}

	public boolean matches(final ID id){
		return this.id != null && this.id.equals(id);
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
		this.records.clear();
	}

}
