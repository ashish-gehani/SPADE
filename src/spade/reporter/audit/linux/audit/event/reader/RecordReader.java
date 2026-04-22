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

import java.util.logging.Level;
import java.util.logging.Logger;

import spade.reporter.audit.linux.audit.event.record.Factory;
import spade.reporter.audit.linux.audit.event.record.MalformedRecordException;
import spade.reporter.audit.linux.audit.event.record.Record;

/**
 * Reads parsed {@link Record} objects from a {@link LineReader}.
 *
 * Each call to {@link #readRecord()} fetches the next non-null line from the
 * underlying {@link LineReader}, parses it via the supplied {@link Factory},
 * and returns the resulting {@link Record}. Lines that the factory skips
 * (returns {@code null} for) are silently dropped. Malformed lines are logged
 * and skipped.
 *
 * Returns {@code null} at end of stream.
 */
public final class RecordReader implements AutoCloseable {

    private final Logger logger = Logger.getLogger(this.getClass().getName());

    private LineReader lineReader;
    private final Factory recordFactory;

    public RecordReader(final LineReader lineReader, final Factory recordFactory) {
        if (lineReader == null) {
            throw new IllegalArgumentException("LineReader cannot be NULL");
        }
        if (recordFactory == null) {
            throw new IllegalArgumentException("Factory cannot be NULL");
        }
        this.lineReader = lineReader;
        this.recordFactory = recordFactory;
    }

    /**
     * Read the next {@link Record} from the stream.
     *
     * Skips lines that the factory does not recognise. Returns {@code null} at
     * end of stream.
     *
     * @return the next Record, or {@code null} at end of stream
     * @throws Exception if the underlying {@link LineReader} throws
     */
    public Record readRecord() throws Exception {
        while (true) {
            final String line = lineReader.readLine();
            if (line == null) {
                return null;
            }
            try {
                final Record record = recordFactory.create(line);
                if (record != null) {
                    return record;
                }
            } catch (final MalformedRecordException e) {
                logger.log(Level.WARNING, "Skipping malformed audit record", e);
            }
        }
    }

    @Override
    public void close() throws Exception {
        if (lineReader == null) {
            return;
        }
        try {
            lineReader.close();
        } finally {
            lineReader = null;
        }
    }

}
