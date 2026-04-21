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

public class Config {
    
    private final long bufferMaxSize;
    private final boolean readBlocking;
    private final long readTimeoutMs;
    private final boolean writeBlocking;
    private final long writeTimeoutMs;
    private final LossMode lossMode;
    private final long snapshotIntervalMs;

    public Config (
        final long buffermaxSize,
        final boolean readBlocking,
        final long readTimeoutMs,
        final boolean writeBlocking,
        final long writeTimeoutMs,
        final LossMode lossMode,
        final long snapshotIntervalMs
    ) {
        this.bufferMaxSize = buffermaxSize;
        this.readBlocking = readBlocking;
        this.readTimeoutMs = readTimeoutMs;
        this.writeBlocking = writeBlocking;
        this.writeTimeoutMs = writeTimeoutMs;
        this.lossMode = lossMode;
        this.snapshotIntervalMs = snapshotIntervalMs;
    }

    public long getBufferMaxSize() { return bufferMaxSize; }
    public boolean isReadBlocking() { return readBlocking; }
    public long getReadTimeoutMs() { return readTimeoutMs; }
    public boolean isWriteBlocking() { return writeBlocking; }
    public long getWriteTimeoutMs() { return writeTimeoutMs; }
    public LossMode getLossMode() { return lossMode; }
    public long getSnapshotIntervalMs() { return snapshotIntervalMs; }

    @Override
    public String toString() {
        return "Config ["
            + "bufferMaxSize=" + bufferMaxSize
            + ", readBlocking=" + readBlocking
            + ", readTimeoutMs=" + readTimeoutMs
            + ", writeBlocking=" + writeBlocking
            + ", writeTimeoutMs=" + writeTimeoutMs
            + ", lossMode=" + lossMode
            + ", snapshotIntervalMs=" + snapshotIntervalMs
            + "]";
    }

}
