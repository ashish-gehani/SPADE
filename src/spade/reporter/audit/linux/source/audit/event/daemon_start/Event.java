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
package spade.reporter.audit.linux.source.audit.event.daemon_start;

import spade.reporter.audit.linux.source.audit.event.Type;

import java.util.Arrays;

/**
 * Event subclass for DAEMON_START events.
 * Contains a single DaemonStart record.
 */
public class Event extends spade.reporter.audit.linux.source.audit.event.Event{

	private final spade.reporter.audit.linux.source.audit.event.record.DaemonStart record;

	protected Event(
		final spade.reporter.audit.linux.source.audit.event.record.DaemonStart record
	){
		super(record.getId(), Type.DAEMON_START);
		setRecords(Arrays.asList(record));
		this.record = record;
	}

	public spade.reporter.audit.linux.source.audit.event.record.DaemonStart getDaemonStartRecord(){
		return record;
	}
}
