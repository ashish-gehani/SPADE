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
package spade.reporter.audit.core.event.writer;

import spade.reporter.audit.core.event.Event;
import spade.reporter.audit.core.event.HandlerContext;
import spade.reporter.audit.core.event.IDable;

/**
 * Abstract class for writing audit events to an arbitrary sink.
 *
 * @param <V> the ID type of the events
 * @param <C> the context type for event handling
 * @param <T> the concrete {@link Event} subtype this writer accepts
 */
public abstract class Writer<V extends IDable, C extends HandlerContext, T extends Event<V, C>> implements AutoCloseable{

	protected Writer(){

	}

	/**
	 * Write one event to the underlying sink.
	 *
	 * @param event the event to write
	 * @return the number of bytes written
	 * @throws Exception if writing fails
	 */
	public abstract long writeEvent(final T event) throws Exception;

	@Override
	public abstract void close();

}
