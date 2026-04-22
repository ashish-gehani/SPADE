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
package spade.reporter.audit.linux.audit.event.type.ubsi_entry;

import java.util.Arrays;

import spade.reporter.audit.linux.audit.event.HandlerContext;
import spade.reporter.audit.linux.audit.event.Type;

/**
 * Event for UBSI_ENTRY events.
 * Contains a single UbsiEntry record that marks the start of a new unit execution.
 */
public class Event extends spade.reporter.audit.linux.audit.event.Event{

	private final spade.reporter.audit.linux.audit.event.record.type.ubsi.UbsiEntry record;

	protected Event(final spade.reporter.audit.linux.audit.event.record.type.ubsi.UbsiEntry record){
		super(record.getId(), Type.UBSI_ENTRY);
		setRecords(Arrays.asList(record));
		this.record = record;
	}

	public spade.reporter.audit.linux.audit.event.record.type.ubsi.UbsiEntry getUbsiEntryRecord(){
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

	@Override
	public void handle(final HandlerContext context){
		new Handler(this).handle(context);
	}
}
