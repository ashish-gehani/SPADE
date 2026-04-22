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
package spade.reporter.audit.linux.source.audit.writer.type.file;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;

import spade.reporter.audit.linux.source.audit.writer.Type;

/**
 * A {@link spade.reporter.audit.linux.event.writer.output.writer.LineWriter} that
 * writes lines to a single file.
 *
 * Each {@link #writeLine(String)} call appends the supplied text plus a
 * trailing newline to the file and flushes immediately.
 */
public class LineWriter extends spade.reporter.audit.linux.source.audit.writer.LineWriter {

    private final String filePath;
    private BufferedWriter writer;

    public LineWriter(final String filePath) throws Exception {
        super(Type.FILE);
        if (filePath == null) {
            throw new IllegalArgumentException("File path cannot be NULL");
        }
        this.filePath = filePath;
        this.writer = new BufferedWriter(
            new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8)
        );
    }

    public String getFilePath() {
        return filePath;
    }

    @Override
    public long writeLine(final String line) throws Exception {
        if (line == null) {
            return 0;
        }
        final String lineWithNewline = line + "\n";
        writer.write(lineWithNewline);
        writer.flush();
        return lineWithNewline.getBytes(StandardCharsets.UTF_8).length;
    }

    @Override
    public void close() throws Exception {
        if (writer == null) {
            return;
        }
        try {
            writer.close();
        } finally {
            writer = null;
        }
    }

}
