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
package spade.reporter.audit.core.source.reader;

import spade.reporter.audit.core.source.event.Event;
import spade.reporter.audit.core.source.event.IDable;

/**
 * Abstract class for reading audit events from an arbitrary source.
 *
 * @param <I> the ID type of the events
 * @param <T> the concrete {@link Event} subtype this reader produces
 */
public abstract class Reader<I extends IDable, T extends Event<I>> implements AutoCloseable {

	/**
	 * Read the next complete event from the source.
	 *
	 * @return the next event, or null if end of stream
	 * @throws Exception if reading or parsing fails
	 */
	public abstract T readEvent() throws Exception;

	/**
	 * Close the reader and release resources.
	 */
	@Override
	public abstract void close();
}
