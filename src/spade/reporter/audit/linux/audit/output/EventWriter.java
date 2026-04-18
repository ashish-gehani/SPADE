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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.reporter.audit.core.source.writer.Writer;
import spade.reporter.audit.linux.audit.event.Event;
import spade.reporter.audit.linux.audit.event.record.Record;

/**
 * Writes {@link Event} objects to a {@link RecordWriter}.
 *
 * Each call to {@link #writeEvent(Event)} writes all records of the event
 * in order via the underlying {@link RecordWriter}.
 */
public final class EventWriter extends Writer<Event> {

    private final Logger logger = Logger.getLogger(this.getClass().getName());

    private RecordWriter recordWriter;
    private final Metrics metrics = new Metrics();
    private final Config config;
    private ScheduledExecutorService snapshotScheduler;

    public EventWriter(final RecordWriter recordWriter, final Config config) {
        super();
        if (recordWriter == null) {
            throw new IllegalArgumentException("RecordWriter cannot be NULL");
        }
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be NULL");
        }
        this.recordWriter = recordWriter;
        this.config = config;
        startSnapshotScheduler();
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
     * Write all records of an event to the underlying {@link RecordWriter}.
     *
     * @param event the event to write (ignored if {@code null})
     * @return the total number of bytes written across all records
     * @throws Exception if writing fails
     */
    @Override
    public long writeEvent(final Event event) throws Exception {
        if (event == null) {
            return 0;
        }
        long recordCount = 0;
        long totalBytesWritten = 0;
        try {
            for (final Record record : event.getRecords()) {
                totalBytesWritten += recordWriter.writeRecord(record);
                recordCount++;
            }
        } catch (final Exception e) {
            updateMetrics(recordCount, totalBytesWritten, true);
            throw e;
        }
        updateMetrics(recordCount, totalBytesWritten, false);
        return totalBytesWritten;
    }

    private void updateMetrics(final long recordCount, final long byteCount, final boolean failed) {
        if (failed) {
            metrics.incrementWriteFailures();
        } else {
            metrics.incrementEventsWritten();
        }
        metrics.incrementRecordsWritten(recordCount);
        metrics.incrementBytesWritten(byteCount);
    }

    public Metrics getMetrics() {
        return metrics;
    }

    @Override
    public void close() {
        stopSnapshotScheduler();
        if (recordWriter == null) {
            return;
        }
        try {
            recordWriter.close();
        } catch (final Exception e) {
            logger.log(Level.SEVERE, "Failed to close the record writer", e);
        } finally {
            recordWriter = null;
        }
    }

}
