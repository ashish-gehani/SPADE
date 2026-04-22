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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.reporter.audit.core.event.MalformedEventException;
import spade.reporter.audit.linux.event.record.Record;
import spade.reporter.audit.linux.event.type.Syscall;
import spade.reporter.audit.linux.event.type.DaemonStart;
import spade.reporter.audit.linux.event.type.UbsiEntry;
import spade.reporter.audit.linux.event.type.UbsiExit;
import spade.reporter.audit.linux.event.type.UbsiDep;
import spade.reporter.audit.linux.event.type.UbsiRaw;
import spade.reporter.audit.linux.event.type.Netio;
import spade.reporter.audit.linux.event.type.Netfilter;

/**
 * Concrete event factory for the Linux Audit Subsystem.
 *
 * Iterates a fixed, ordered list of {@link Event.Creator} instances, calling
 * {@link Event.Creator#matches} on each until one accepts the record list from
 * the {@link Context}, then delegates to {@link Event.Creator#create}.
 *
 * {@link Syscall.Creator} is listed first because a syscall event may contain
 * USER records alongside the SYSCALL record.
 *
 * Returns {@code null} if no creator matches or if event construction fails.
 */
public final class Factory {

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

	public Event create(
		final Context context
	) throws MalformedEventException{
		if(context == null){
			throw new IllegalArgumentException("Context cannot be NULL");
		}
		final List<Record> records = context.getRecords();
		if(records == null || records.isEmpty()){
			return null;
		}
		for(final Event.Creator creator : creators){
			if(creator.matches(records)){
				return creator.create(records);
			}
		}
		if(verbose){
			logger.log(Level.INFO, "No creator matched for records of size {0}",
				records.size());
		}
		return null;
	}

}
