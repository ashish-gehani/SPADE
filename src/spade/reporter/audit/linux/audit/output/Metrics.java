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

import java.util.logging.Logger;

/**
 * Counters for the audit-writing pipeline.
 *
 * Extends {@link spade.reporter.audit.core.source.writer.Metrics} (which
 * tracks {@code eventsWritten} and {@code writeFailures}) with the
 * Linux-audit-specific counters: the number of records those events
 * expanded into, and the total bytes written to the underlying
 * destination. Mutators are package-private and single-purpose;
 * getters, {@link #toString()} and {@link #log()} are public.
 */
public class Metrics extends spade.reporter.audit.core.source.writer.Metrics {

    private final Logger logger = Logger.getLogger(this.getClass().getName());

    private long recordsWritten = 0;
    private long bytesWritten = 0;

    public Metrics() {
        super();
    }

    @Override
    protected synchronized void incrementEventsWritten() {
        super.incrementEventsWritten();
    }

    @Override
    protected synchronized void incrementWriteFailures() {
        super.incrementWriteFailures();
    }

    synchronized void incrementRecordsWritten(final long records) {
        recordsWritten += records;
    }

    synchronized void incrementBytesWritten(final long bytes) {
        bytesWritten += bytes;
    }

    public synchronized long getRecordsWritten() {
        return recordsWritten;
    }

    public synchronized long getBytesWritten() {
        return bytesWritten;
    }

    @Override
    public synchronized String toString() {
        return this.getClass().getName() + " ["
            + "timestamp=" + System.currentTimeMillis()
            + ", eventsWritten=" + getEventsWritten()
            + ", recordsWritten=" + recordsWritten
            + ", bytesWritten=" + bytesWritten
            + ", writeFailures=" + getWriteFailures()
            + "]";
    }

    @Override
    public synchronized void log() {
        logger.info(toString());
    }

}
