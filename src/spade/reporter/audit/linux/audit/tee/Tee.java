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
package spade.reporter.audit.linux.audit.tee;

import java.util.logging.Level;
import java.util.logging.Logger;

import spade.reporter.audit.core.event.Event;
import spade.reporter.audit.core.event.Factory;
import spade.reporter.audit.core.event.Reader;
import spade.reporter.audit.linux.audit.input.BufferedEventReader;
import spade.reporter.audit.linux.audit.output.EventWriter;

/**
 * Reads events from the input pipeline, mirrors each one to the output
 * pipeline, and returns it to the caller.
 *
 * The write to {@link EventWriter} is best-effort: a write failure is
 * logged at {@code WARNING} and the event is still returned. Metrics are
 * owned by the underlying reader and writer; this class only exposes
 * delegating getters.
 *
 * Lifecycle: construct → {@link #start()} → repeated {@link #readEvent()}
 * → {@link #close()}.
 */
public final class Tee extends Reader {

    private final Logger logger = Logger.getLogger(this.getClass().getName());

    private final boolean verbose;
    private BufferedEventReader reader;
    private EventWriter writer;

    public Tee(
        final Factory eventFactory,
        final BufferedEventReader reader,
        final EventWriter writer,
        final boolean verbose
    ) {
        super(eventFactory);
        if (reader == null) {
            throw new IllegalArgumentException("BufferedEventReader cannot be NULL");
        }
        if (writer == null) {
            throw new IllegalArgumentException("EventWriter cannot be NULL");
        }
        this.reader = reader;
        this.writer = writer;
        this.verbose = verbose;
    }

    /**
     * Start the underlying {@link BufferedEventReader} pump thread.
     *
     * Must be called before the first {@link #readEvent()}.
     */
    public void start() {
        reader.start();
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
            logger.log(Level.FINE, "Tee read event id=" + event.getId());
        }
        writeEvent(event);
        return event;
    }

    private void writeEvent(final Event event) {
        if (!(event instanceof spade.reporter.audit.linux.audit.event.Event)) {
            return;
        }
        try {
            writer.writeEvent((spade.reporter.audit.linux.audit.event.Event) event);
        } catch (final Exception e) {
            logger.log(Level.WARNING, "Failed to write event to output", e);
        }
    }

    public spade.reporter.audit.linux.audit.input.Metrics getReaderMetrics() {
        return reader.getMetrics();
    }

    public spade.reporter.audit.linux.audit.output.Metrics getWriterMetrics() {
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
