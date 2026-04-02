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

import spade.reporter.audit.OutputLog;
import spade.reporter.audit.linux.audit.output.writer.Type;

public class Config {

    private final OutputLog outputLog;
    private final long snapshotIntervalMs;

    public Config(final OutputLog outputLog, final long snapshotIntervalMs) {
        if (outputLog == null) {
            throw new IllegalArgumentException("OutputLog cannot be NULL");
        }
        this.outputLog = outputLog;
        this.snapshotIntervalMs = snapshotIntervalMs;
    }

    public boolean hasFilePath() {
        return outputLog.isEnabled();
    }

    public String getFilePath() {
        return outputLog.getOutputLogPath();
    }

    public Type getLineWriterType() {
        if (!outputLog.isEnabled()) {
            return Type.NO_OP;
        }
        return outputLog.isRotationEnabled() ? Type.ROTATING_FILE : Type.FILE;
    }

    /**
     * {@link OutputLog} expresses the rotation threshold in lines (audit
     * records) but the rotating {@code LineWriter} operates on bytes.
     * Convert using {@link OutputLog#APPROXIMATE_BYTES_PER_LINE}.
     */
    public long getRotationBytes() {
        return (long) outputLog.getRotateLogAfterLines() * OutputLog.APPROXIMATE_BYTES_PER_LINE;
    }

    public long getSnapshotIntervalMs() {
        return snapshotIntervalMs;
    }

}
