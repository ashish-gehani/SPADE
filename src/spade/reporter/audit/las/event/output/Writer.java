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
package spade.reporter.audit.las.event.output;

import spade.reporter.audit.las.event.Event;

/**
 * Abstract class for writing audit event data to an arbitrary destination.
 *
 * Implementations write raw audit record lines. Each line is a single audit record.
 */
public abstract class Writer implements AutoCloseable{

	private final java.io.OutputStream stream;

	public Writer(
		final java.io.OutputStream outputStream
	){
		if (outputStream == null) {
			throw new IllegalArgumentException("NULL output stream");
		}
		this.stream = outputStream;
	}

	public java.io.OutputStream getStream () {
		return this.stream;
	}

	/**
	 * Write an audit event to the output.
	 *
	 * @param event the audit event to write
	 * @return The number of bytes written
	 * @throws Exception if writing fails
	 */
	public abstract long writeEvent(final Event event) throws Exception;

	/**
	 * Close the writer and release resources.
	 */
	@Override
	public abstract void close();
}
