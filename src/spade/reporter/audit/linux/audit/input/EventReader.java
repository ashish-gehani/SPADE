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
package spade.reporter.audit.linux.audit.input;

import java.util.logging.Level;
import java.util.logging.Logger;

import spade.reporter.audit.core.event.reader.Reader;
import spade.reporter.audit.linux.audit.event.Context;
import spade.reporter.audit.linux.audit.event.Event;
import spade.reporter.audit.linux.audit.event.Factory;
import spade.reporter.audit.linux.audit.event.ID;
import spade.reporter.audit.linux.audit.event.Timestamp;
import spade.reporter.audit.linux.audit.event.record.Record;

/**
 * Reads audit {@link Event}s from a {@link RecordReader}.
 *
 * Records sharing the same {@link ID} and {@link Timestamp} are accumulated in
 * a {@link Context}. When a new (ID, Timestamp) pair is encountered the
 * buffered context is flushed through the {@link Factory} to produce a typed
 * {@link Event}, then the context is reset for the next group.
 */
public final class EventReader extends Reader<Event, Context> {

    private final Logger logger = Logger.getLogger(this.getClass().getName());

    private RecordReader recordReader;
    private boolean eof;
    private final Context context;

    public EventReader(
        final Factory eventFactory,
        final RecordReader recordReader
    ) {
        super(eventFactory);
        if (recordReader == null) {
            throw new IllegalArgumentException("RecordReader cannot be NULL");
        }
        this.recordReader = recordReader;
        this.eof = false;
        this.context = new Context();
    }

    @Override
    public spade.reporter.audit.core.event.Factory<Event, Context> getEventFactory() {
        return super.getEventFactory();
    }

    /**
     * Read the next complete event from the stream.
     *
     * Records are fetched one at a time from the {@link RecordReader} and
     * accumulated in the {@link Context} by (ID, Timestamp). When a new
     * (ID, Timestamp) pair is seen the previous context is flushed through the
     * event factory to produce an {@link Event}.
     *
     * @return the next Event, or {@code null} at end of stream
     * @throws Exception if reading or parsing fails
     */
    @Override
    public synchronized Event readEvent() throws Exception {
        while (!eof) {
            final Record record = recordReader.readRecord();
            if (record == null) {
                eof = true;
                break;
            }

            final ID id = record.getEventId();
            final Timestamp timestamp = record.getTime();

            if (!context.isSet()) {
                context.set(id, timestamp);
                context.addRecord(record);
                continue;
            }

            if (context.matches(id, timestamp)) {
                context.addRecord(record);
                continue;
            }

            final Event event = getEventFactory().create(context);
            context.reset();
            context.set(id, timestamp);
            context.addRecord(record);
            if (event != null) {
                return event;
            }
        }

        if (!context.isSet()) {
            return null;
        }

        final Event event = getEventFactory().create(context);
        context.reset();
        return event;
    }

    @Override
    public synchronized void close() {
        if (recordReader == null) {
            return;
        }
        try {
            recordReader.close();
        } catch (final Exception e) {
            logger.log(Level.SEVERE, "Failed to close the record reader", e);
        } finally {
            recordReader = null;
        }
    }

}
