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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.reporter.audit.las.event.record.Record;

/**
 * Factory class for creating Event subclass instances from a set of Records.
 *
 * Iterates a fixed, ordered list of {@link Event.Creator} instances, calling
 * {@link Event.Creator#matches} on each until one accepts the record list,
 * then delegates to {@link Event.Creator#create}.
 *
 * Syscall.Creator is listed first because a syscall event may contain USER
 * records alongside the SYSCALL record.
 */
public final class Factory{

	private final Logger logger = Logger.getLogger(this.getClass().getName());

	private final boolean verbose;

	private final List<Event.Creator> creators = Collections.unmodifiableList(Arrays.asList(
		new Syscall.Creator(),
		new DaemonStart.Creator(),
		new UbsiEntry.Creator(),
		new UbsiExit.Creator(),
		new UbsiDep.Creator(),
		new UbsiRaw.Creator(),
		new Netio.Creator(),
		new Netfilter.Creator()
	));

	public Factory(final boolean verbose){
		this.verbose = verbose;
	}

	public boolean isVerbose(){
		return verbose;
	}

	/**
	 * Creates the appropriate Event subclass from a set of Records
	 * that share the same event ID.
	 *
	 * @param records the set of records for this event
	 * @return the appropriate Event subclass, or null if no creator matches
	 * @throws MalformedEventException if a matching creator fails to create the event
	 */
	public Event createEvent(final Set<Record> records) throws MalformedEventException{
		if(records == null || records.isEmpty()){
			return null;
		}

		final List<Record> recordList = new ArrayList<>(records);

		for(final Event.Creator creator : creators){
			if(creator.matches(recordList)){
				return creator.create(recordList);
			}
		}

		if(verbose){
			final String eventId = recordList.get(0).getEventId();
			logger.log(Level.FINE, "No creator matched for event {0}", eventId);
		}
		return null;
	}
}
