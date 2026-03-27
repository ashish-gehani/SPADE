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
package spade.reporter.audit.reader;


import spade.reporter.audit.las.event.Event;

public abstract class Reader implements AutoCloseable{

	private final spade.reporter.audit.las.event.record.Factory recordFactory;
	private final spade.reporter.audit.las.event.Factory eventFactory;

	public Reader(
		final spade.reporter.audit.las.event.record.Factory recordFactory,
		final spade.reporter.audit.las.event.Factory eventFactory
	) throws Exception {
		if(recordFactory == null){
			throw new IllegalArgumentException("Record factory cannot be NULL");
		}
		if(eventFactory == null){
			throw new IllegalArgumentException("Event factory cannot be NULL");
		}
		this.recordFactory = recordFactory;
		this.eventFactory = eventFactory;
	}

	protected spade.reporter.audit.las.event.record.Factory getRecordFactory(){
		return recordFactory;
	}

	protected spade.reporter.audit.las.event.Factory getEventFactory(){
		return eventFactory;
	}

	/**
	 * Read the next event from the source.
	 *
	 * @return the next Event, or null if end of source
	 * @throws Exception if reading or parsing fails
	 */
	public abstract Event readEvent() throws Exception;

	@Override
	public abstract void close();
}
