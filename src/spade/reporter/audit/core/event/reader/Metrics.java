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
package spade.reporter.audit.core.event.reader;

import java.util.logging.Logger;

/**
 * Abstract counters for an event-reading pipeline.
 *
 * Tracks the number of {@link spade.reporter.audit.core.source.Event}s
 * delivered to the caller. Subclasses add source-specific counters by
 * declaring their own fields and appending them via {@link #toString()}.
 */
public abstract class Metrics {

    private final Logger logger = Logger.getLogger(this.getClass().getName());

    private long eventsRead = 0;

    protected Metrics() {
    }

    protected synchronized void incrementEventsRead() {
        eventsRead++;
    }

    public synchronized long getEventsRead() {
        return eventsRead;
    }

    /**
     * Returns the full snapshot line: class name, wall-clock timestamp,
     * and all counters. Subclasses override to append their own counters.
     */
    @Override
    public synchronized String toString() {
        return this.getClass().getName() + " ["
            + "timestamp=" + System.currentTimeMillis()
            + ", eventsRead=" + eventsRead
            + "]";
    }

    public synchronized void log() {
        logger.info(toString());
    }

}
