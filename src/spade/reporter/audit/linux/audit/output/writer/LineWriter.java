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
package spade.reporter.audit.linux.audit.output.writer;

/**
 * A destination for raw audit log lines.
 *
 * Implementations write one line at a time to an underlying destination (e.g.
 * a file or a rotating set of files).
 */
public abstract class LineWriter implements AutoCloseable {

    private final Type type;

    protected LineWriter(final Type type) {
        if (type == null) {
            throw new IllegalArgumentException("Type cannot be NULL");
        }
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    /**
     * Write a line to the destination.
     *
     * Implementations append a newline after the supplied text.
     *
     * @param line the text to write (without a trailing newline)
     * @return the number of bytes written, including the appended newline
     * @throws Exception if writing fails
     */
    public abstract long writeLine(String line) throws Exception;

    /**
     * Release any resources held by this writer.
     *
     * @throws Exception if closing fails
     */
    @Override
    public abstract void close() throws Exception;

}
