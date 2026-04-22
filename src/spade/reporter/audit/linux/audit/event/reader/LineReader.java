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
package spade.reporter.audit.linux.audit.event.reader;

/**
 * A source of raw audit log lines.
 *
 * Implementations read one line at a time from an underlying source (e.g. a
 * process stdout stream) and signal end-of-stream by returning {@code null}.
 */
public abstract class LineReader implements AutoCloseable {

	private final Type type;

	protected LineReader(final Type type) {
		if (type == null) {
			throw new IllegalArgumentException("Type cannot be NULL");
		}
		this.type = type;
	}

	public Type getType() {
		return type;
	}

	/**
	 * Read the next line from the source.
	 *
	 * @return the next line, or {@code null} at end of stream
	 * @throws Exception if reading fails
	 */
	public abstract String readLine() throws Exception;

	/**
	 * Release any resources held by this reader.
	 *
	 * @throws Exception if closing fails
	 */
	@Override
	public abstract void close() throws Exception;

}
