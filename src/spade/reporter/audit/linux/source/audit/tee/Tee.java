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
package spade.reporter.audit.linux.source.audit.tee;

import java.util.logging.Level;
import java.util.logging.Logger;

import spade.reporter.audit.core.source.reader.Reader;
import spade.reporter.audit.linux.source.audit.event.Event;
import spade.reporter.audit.linux.source.audit.event.ID;
import spade.reporter.audit.linux.source.audit.reader.EventReader;
import spade.reporter.audit.linux.source.audit.writer.EventWriter;

/**
 * Reads events from the input pipeline, mirrors each one to the output
 * pipeline, and returns it to the caller.
 *
 * The write to {@link EventWriter} is best-effort: a write failure is
 * logged at {@code WARNING} and the event is still returned.
 *
 * Lifecycle: construct → repeated {@link #readEvent()} → {@link #close()}.
 */
public final class Tee extends Reader<ID, Event>{

    private final Logger logger = Logger.getLogger(this.getClass().getName());

    private final boolean verbose;
    private EventReader reader;
    private EventWriter writer;

    public Tee(
        final EventReader reader,
        final EventWriter writer,
        final boolean verbose
    ) {
        if (reader == null) {
            throw new IllegalArgumentException("reader cannot be NULL");
        }
        if (writer == null) {
            throw new IllegalArgumentException("writer cannot be NULL");
        }
        this.reader = reader;
        this.writer = writer;
        this.verbose = verbose;
        start();
    }

    public boolean isVerbose() {
        return verbose;
    }

    private void start() {
        if (verbose) {
            logger.log(Level.INFO, "Tee started");
        }
    }

    /**
     * Read the next event from the input pipeline, mirror it to the output
     * pipeline, and return it.
     *
     * @return the next Event, or {@code null} at end of stream
     * @throws Exception if reading from the input pipeline fails
     */
    @Override
    public Event readEvent() throws Exception {
        final Event event = reader.readEvent();
        if (event == null) {
            return null;
        }
        if (verbose) {
            logger.log(Level.INFO, "Tee read event id=" + event.getId());
        }
        writeEvent(event);
        return event;
    }

    private void writeEvent(final Event event) {
        try {
            writer.writeEvent(event);
        } catch (final Exception e) {
            logger.log(Level.WARNING, "Failed to write event to output", e);
        }
    }

    public spade.reporter.audit.linux.source.audit.writer.Metrics getWriterMetrics() {
        return writer.getMetrics();
    }

    @Override
    public synchronized void close() {
        if (reader != null) {
            try {
                reader.close();
            } finally {
                reader = null;
            }
        }
        if (writer != null) {
            try {
                writer.close();
            } catch (final Exception e) {
                logger.log(Level.SEVERE, "Failed to close EventWriter", e);
            } finally {
                writer = null;
            }
        }
    }

}
