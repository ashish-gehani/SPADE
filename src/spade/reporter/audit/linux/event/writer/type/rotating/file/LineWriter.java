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
package spade.reporter.audit.linux.event.writer.type.rotating.file;

import java.util.logging.Level;
import java.util.logging.Logger;

import spade.reporter.audit.linux.event.writer.Type;

/**
 * A {@link spade.reporter.audit.linux.event.writer.output.writer.LineWriter} that
 * writes lines to a rotating set of files.
 *
 * Lines are written to a current file until {@code rotateAfterBytes} bytes have
 * been written, at which point the current file is closed and a new one is
 * opened. Files are named:
 * <ul>
 *   <li>First file: {@code basePath}</li>
 *   <li>Subsequent files: {@code basePath.1}, {@code basePath.2}, …</li>
 * </ul>
 *
 * Rotation is disabled when {@code rotateAfterBytes} is zero or negative.
 */
public class LineWriter extends spade.reporter.audit.linux.event.writer.LineWriter {

    private final Logger logger = Logger.getLogger(this.getClass().getName());

    private final String basePath;
    private final long rotateAfterBytes;
    private final boolean rotationEnabled;

    private spade.reporter.audit.linux.event.writer.LineWriter currentWriter;
    private int currentFileIndex = 0;
    private long totalBytesWritten = 0;

    public LineWriter(
        final String basePath,
        final long rotateAfterBytes
    ) throws Exception {
        super(Type.ROTATING_FILE);
        if (basePath == null) {
            throw new IllegalArgumentException("Base path cannot be NULL");
        }
        this.basePath = basePath;
        this.rotateAfterBytes = rotateAfterBytes;
        this.rotationEnabled = rotateAfterBytes > 0;
        this.currentWriter = new spade.reporter.audit.linux.event.writer.type.file.LineWriter(currentFilePath());
    }

    private String currentFilePath() {
        return currentFileIndex == 0 ? basePath : basePath + "." + currentFileIndex;
    }

    @Override
    public long writeLine(final String line) throws Exception {
        if (line == null) {
            return 0;
        }
        final long bytesWritten = currentWriter.writeLine(line);
        totalBytesWritten += bytesWritten;
        if (rotationEnabled && totalBytesWritten >= rotateAfterBytes) {
            rotate();
        }
        return bytesWritten;
    }

    private void rotate() throws Exception {
        try {
            currentWriter.close();
        } catch (final Exception e) {
            logger.log(Level.SEVERE, "Failed to close file before rotation", e);
        }
        currentFileIndex++;
        totalBytesWritten = 0;
        currentWriter = new spade.reporter.audit.linux.event.writer.type.file.LineWriter(currentFilePath());
    }

    @Override
    public void close() throws Exception {
        if (currentWriter == null) {
            return;
        }
        try {
            currentWriter.close();
        } finally {
            currentWriter = null;
        }
    }

}
