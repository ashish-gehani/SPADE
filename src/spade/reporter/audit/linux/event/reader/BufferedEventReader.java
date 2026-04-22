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
package spade.reporter.audit.linux.event.reader;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.reporter.audit.core.event.reader.Reader;
import spade.reporter.audit.core.util.channel.Channel;
import spade.reporter.audit.core.util.channel.ReadTimeoutExpired;
import spade.reporter.audit.linux.event.ID;
import spade.reporter.audit.linux.event.Event;
import spade.reporter.audit.linux.event.record.Record;

/**
 * Wraps an {@link EventReader} with an asynchronous {@link Channel} buffer.
 *
 * A background pump thread continuously reads {@link Event}s from the
 * {@link EventReader} and writes them into the {@link Channel}. Callers invoke
 * {@link #readEvent()} to dequeue events from the channel, decoupling the
 * producer (audit log parsing) from the consumer.
 *
 * The channel is closed once the pump thread reaches end-of-stream or
 * encounters an unrecoverable error, after which {@link #readEvent()} will
 * drain any remaining buffered events and then return {@code null}.
 *
 * The pump thread is started automatically from the constructor.
 */
public final class BufferedEventReader extends Reader<ID, Event> {

    private final Logger logger = Logger.getLogger(this.getClass().getName());

    private EventReader eventReader;
    private final Channel<Event> channel;
    private final Metrics metrics = new Metrics();
    private final Config config;
    private ScheduledExecutorService snapshotScheduler;
    private Thread pumpThread;

    public BufferedEventReader(
        final EventReader eventReader,
        final Channel<Event> channel,
        final Config config
    ) {
        if (channel == null) {
            throw new IllegalArgumentException("Channel cannot be NULL");
        }
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be NULL");
        }
        this.eventReader = eventReader;
        this.channel = channel;
        this.config = config;
        startSnapshotScheduler();
        start();
    }

    public Config getConfig() {
        return config;
    }

    private void startSnapshotScheduler() {
        final long snapshotIntervalMs = config.getSnapshotIntervalMs();
        if (snapshotIntervalMs <= 0) {
            snapshotScheduler = null;
            return;
        }
        snapshotScheduler = Executors.newSingleThreadScheduledExecutor();
        snapshotScheduler.scheduleAtFixedRate(
            metrics::log,
            snapshotIntervalMs,
            snapshotIntervalMs,
            TimeUnit.MILLISECONDS
        );
    }

    private void stopSnapshotScheduler() {
        if (snapshotScheduler == null) {
            return;
        }
        snapshotScheduler.shutdown();
        snapshotScheduler = null;
    }

    /**
     * Start the background pump thread.
     *
     * Called automatically from the constructor.
     */
    private synchronized void start() {
        if (pumpThread != null) {
            throw new IllegalStateException("Already started");
        }
        pumpThread = new Thread(this::pump, "buffered-event-reader-pump");
        pumpThread.setDaemon(true);
        pumpThread.start();
    }

    private void pump() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                final Event event;
                try {
                    event = eventReader.readEvent();
                } catch (final Exception e) {
                    logger.log(Level.SEVERE, "Failed to read event from EventReader", e);
                    break;
                }
                if (event == null) {
                    break;
                }
                try {
                    channel.write(event);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (final Exception e) {
                    logger.log(Level.WARNING, "Failed to write event to channel", e);
                }
            }
        } finally {
            channel.close();
        }
    }

    /**
     * Read the next {@link Event} from the channel buffer.
     *
     * Blocks according to the channel's read configuration. Returns
     * {@code null} once the channel is closed and drained.
     *
     * @return the next Event, or {@code null} at end of stream
     * @throws ReadTimeoutExpired if the channel read timeout expires before an
     *         event becomes available
     * @throws InterruptedException if the calling thread is interrupted while
     *         waiting
     */
    @Override
    public Event readEvent() throws InterruptedException, ReadTimeoutExpired {
        final Event event = channel.read();
        updateMetrics(event);
        return event;
    }

    private void updateMetrics(final Event event) {
        if (event == null) {
            return;
        }
        metrics.incrementEventsRead();
        long recordCount = 0;
        long byteCount = 0;
        for (final Record record : event.getRecords()) {
            recordCount++;
            final String raw = record.getRawRecord();
            if (raw != null) {
                byteCount += raw.length();
            }
        }
        metrics.incrementRecordsRead(recordCount);
        metrics.incrementBytesRead(byteCount);
    }

    public Metrics getMetrics() {
        return metrics;
    }

    @Override
    public synchronized void close() {
        stopSnapshotScheduler();
        if (pumpThread != null) {
            pumpThread.interrupt();
            pumpThread = null;
        }
        if (eventReader == null) {
            return;
        }
        try {
            eventReader.close();
        } finally {
            eventReader = null;
        }
    }

}
