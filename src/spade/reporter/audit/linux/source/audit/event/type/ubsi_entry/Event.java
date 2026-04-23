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
package spade.reporter.audit.linux.source.audit.event.type.ubsi_entry;

import java.util.Arrays;

import spade.reporter.audit.linux.source.audit.event.Type;
import spade.reporter.audit.linux.source.audit.event.record.helper.ProcessInfo;
import spade.reporter.audit.linux.source.audit.event.record.type.ubsi.Unit;

/**
 * Event for UBSI_ENTRY events.
 * Contains a single UbsiEntry record that marks the start of a new unit execution.
 */
public class Event extends spade.reporter.audit.linux.source.audit.event.Event{

	private final spade.reporter.audit.linux.source.audit.event.record.type.ubsi.UbsiEntry record;

	protected Event(final spade.reporter.audit.linux.source.audit.event.record.type.ubsi.UbsiEntry record){
		super(record.getId(), Type.UBSI_ENTRY);
		setRecords(Arrays.asList(record));
		this.record = record;
	}

	public spade.reporter.audit.linux.source.audit.event.record.type.ubsi.UbsiEntry getUbsiEntryRecord(){
		return record;
	}

	public ProcessInfo getProcessInfo(){
		return record.getProcessInfo();
	}

	public Unit getUnit(){
		return record.getUnit();
	}
}
