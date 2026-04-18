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

import java.util.logging.Logger;

/**
 * Counters for the audit-reading pipeline.
 *
 * Extends {@link spade.reporter.audit.core.source.reader.Metrics} (which
 * tracks {@code eventsRead}) with the Linux-audit-specific counters:
 * the number of records those events were assembled from, and the total
 * raw bytes those records represent. Mutators are package-private and
 * single-purpose; getters, {@link #toString()} and {@link #log()} are
 * public.
 */
public class Metrics extends spade.reporter.audit.core.source.reader.Metrics {

    private final Logger logger = Logger.getLogger(this.getClass().getName());

    private long recordsRead = 0;
    private long bytesRead = 0;

    public Metrics() {
        super();
    }

    @Override
    protected synchronized void incrementEventsRead() {
        super.incrementEventsRead();
    }

    synchronized void incrementRecordsRead(final long records) {
        recordsRead += records;
    }

    synchronized void incrementBytesRead(final long bytes) {
        bytesRead += bytes;
    }

    public synchronized long getRecordsRead() {
        return recordsRead;
    }

    public synchronized long getBytesRead() {
        return bytesRead;
    }

    @Override
    public synchronized String toString() {
        return this.getClass().getName() + " ["
            + "timestamp=" + System.currentTimeMillis()
            + ", eventsRead=" + getEventsRead()
            + ", recordsRead=" + recordsRead
            + ", bytesRead=" + bytesRead
            + "]";
    }

    @Override
    public synchronized void log() {
        logger.info(toString());
    }

}
