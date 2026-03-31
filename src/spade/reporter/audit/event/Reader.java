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
package spade.reporter.audit.event;


/**
 * Abstract class for reading audit events from an arbitrary source.
 *
 * Implementations return typed Event objects.
 */
public abstract class Reader implements AutoCloseable {

	private final Factory eventFactory;

	protected Reader(
		final Factory eventFactory
	){
		if(eventFactory == null){
			throw new IllegalArgumentException("Event factory cannot be NULL");
		}
		this.eventFactory = eventFactory;
	}

	protected Factory getEventFactory() {
		return this.eventFactory;
	}

	/**
	 * Read the next complete event from the source.
	 *
	 * @return the next Event, or null if end of stream
	 * @throws Exception if reading or parsing fails
	 */
	public abstract Event readEvent() throws Exception;

	/**
	 * Close the reader and release resources.
	 */
	@Override
	public abstract void close();
}
