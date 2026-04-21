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
package spade.reporter.audit.core.util.channel;

import java.util.logging.Logger;

public class Metrics {

    private final Logger logger = Logger.getLogger(this.getClass().getName());

    private long lostRecords = 0;
    private long eventsRead = 0;
    private long eventsWritten = 0;
    private long totalReadWaitMs = 0;
    private long totalWriteWaitMs = 0;

    synchronized void recordLost() {
        lostRecords++;
    }

    synchronized void recordRead(final long waitMs) {
        eventsRead++;
        totalReadWaitMs += waitMs;
    }

    synchronized void recordWritten(final long waitMs) {
        eventsWritten++;
        totalWriteWaitMs += waitMs;
    }

    synchronized void snapshot() {
        logger.info(
            this.getClass().getName() + " snapshot ["
            + "timestamp=" + System.currentTimeMillis()
            + ", lostRecords=" + lostRecords
            + ", eventsRead=" + eventsRead
            + ", eventsWritten=" + eventsWritten
            + ", totalReadWaitMs=" + totalReadWaitMs
            + ", totalWriteWaitMs=" + totalWriteWaitMs
            + "]"
        );
    }

}
