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

import spade.reporter.audit.linux.audit.event.Event;
import spade.reporter.audit.linux.audit.event.record.Record;

/**
 * Writes {@link Event} objects to a {@link RecordWriter}.
 *
 * Each call to {@link #writeEvent(Event)} writes all records of the event
 * in order via the underlying {@link RecordWriter}.
 */
public final class EventWriter implements AutoCloseable {

    private RecordWriter recordWriter;

    public EventWriter(final RecordWriter recordWriter) {
        if (recordWriter == null) {
            throw new IllegalArgumentException("RecordWriter cannot be NULL");
        }
        this.recordWriter = recordWriter;
    }

    /**
     * Write all records of an event to the underlying {@link RecordWriter}.
     *
     * @param event the event to write (ignored if {@code null})
     * @return the total number of bytes written across all records
     * @throws Exception if writing fails
     */
    public long writeEvent(final Event event) throws Exception {
        if (event == null) {
            return 0;
        }
        long totalBytesWritten = 0;
        for (final Record record : event.getRecords()) {
            totalBytesWritten += recordWriter.writeRecord(record);
        }
        return totalBytesWritten;
    }

    @Override
    public void close() throws Exception {
        if (recordWriter == null) {
            return;
        }
        try {
            recordWriter.close();
        } finally {
            recordWriter = null;
        }
    }

}
