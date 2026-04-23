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
package spade.reporter.audit.linux.source.audit.event.netfilter;

import java.util.Arrays;

import spade.reporter.audit.linux.source.audit.event.Type;

/**
 * Event subclass for NETFILTER events.
 * Contains a single Netfilter record (from USER record).
 */
public class Event extends spade.reporter.audit.linux.source.audit.event.Event{

	private final spade.reporter.audit.linux.source.audit.event.record.Netfilter record;

	protected Event(
		final spade.reporter.audit.linux.source.audit.event.record.Netfilter record
	){
		super(record.getId(), Type.NETFILTER);
		setRecords(Arrays.asList(record));
		this.record = record;
	}

	public spade.reporter.audit.linux.source.audit.event.record.Netfilter getNetfilterRecord(){
		return record;
	}
}
