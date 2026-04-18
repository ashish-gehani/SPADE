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

import spade.reporter.audit.core.source.Context;
import spade.reporter.audit.core.source.Event;
import spade.reporter.audit.core.source.Factory;

/**
 * Abstract class for reading audit events from an arbitrary source.
 *
 * Implementations return typed {@code T} objects produced by a
 * {@link Factory} from a {@code V} parsing context.
 *
 * @param <T> the concrete {@link Event} subtype this reader produces
 * @param <V> the concrete {@link Context} subtype the factory consumes
 */
public abstract class Reader<T extends Event, V extends Context> implements AutoCloseable {

	private final Factory<T, V> eventFactory;

	protected Reader(
		final Factory<T, V> eventFactory
	){
		if(eventFactory == null){
			throw new IllegalArgumentException("Event factory cannot be NULL");
		}
		this.eventFactory = eventFactory;
	}

	protected Factory<T, V> getEventFactory() {
		return this.eventFactory;
	}

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
