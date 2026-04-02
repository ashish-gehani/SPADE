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
package spade.reporter.audit.linux.audit.output;

import spade.reporter.audit.linux.audit.event.record.Record;
import spade.reporter.audit.linux.audit.output.writer.LineWriter;

/**
 * Writes {@link Record} objects to a {@link LineWriter}.
 *
 * Each call to {@link #writeRecord(Record)} serialises the record via
 * {@link Record#getRawRecord()} and delegates to the underlying
 * {@link LineWriter}.
 */
public final class RecordWriter implements AutoCloseable {

    private LineWriter lineWriter;

    public RecordWriter(final LineWriter lineWriter) {
        if (lineWriter == null) {
            throw new IllegalArgumentException("LineWriter cannot be NULL");
        }
        this.lineWriter = lineWriter;
    }

    /**
     * Write a record to the underlying {@link LineWriter}.
     *
     * @param record the record to write (ignored if {@code null})
     * @return the number of bytes written, including the appended newline
     * @throws Exception if writing fails
     */
    public long writeRecord(final Record record) throws Exception {
        if (record == null) {
            return 0;
        }
        return lineWriter.writeLine(record.getRawRecord());
    }

    @Override
    public void close() throws Exception {
        if (lineWriter == null) {
            return;
        }
        try {
            lineWriter.close();
        } finally {
            lineWriter = null;
        }
    }

}
