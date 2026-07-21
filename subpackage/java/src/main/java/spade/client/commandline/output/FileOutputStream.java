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

package spade.client.commandline.output;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;

import spade.core.Graph;
import spade.query.quickgrail.instruction.SaveGraph;

public class FileOutputStream implements OutputStream {

    private final String filepath;
    private final BufferedWriter writer;
    private boolean writerIsClosed = false;

    public FileOutputStream(final String filepath) throws IllegalArgumentException, FileNotFoundException, IOException {
        if (filepath == null) {
            throw new IllegalArgumentException("Filepath cannot be null");
        }
        this.filepath = filepath;
        this.writer = new BufferedWriter(new FileWriter(filepath));
    }

    private synchronized void ensureWriterIsOpen() throws IOException {
        if (this.writerIsClosed) {
            throw new IOException("Writer is closed");
        }
    }

    @Override
    public synchronized void writeString(final String str) throws IllegalArgumentException, IOException, Exception {
        ensureWriterIsOpen();
        if (str == null) {
            throw new IllegalArgumentException("String cannot be null");
        }
        this.writer.write(str);
    }

    @Override
    public synchronized void writeGraph(final Graph graph) throws IllegalArgumentException, IOException, Exception {
        ensureWriterIsOpen();
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }

        final SaveGraph.Format format = getFileExtension(this.filepath);
        final boolean closeWriter = false;
        Graph.exportGraphUsingWriter(format, this.writer, graph, closeWriter);
    }

    private SaveGraph.Format getFileExtension(final String filepath) {
        final String fp = filepath.trim();
        final int lastDotIndex = fp.lastIndexOf(".");
        final String ext = (lastDotIndex == -1) ? "" : fp.substring(lastDotIndex + 1).toLowerCase();

        switch (ext) {
            case "json":
                return SaveGraph.Format.kJson;
            case "dot":
                return SaveGraph.Format.kDot;
            default:
                return SaveGraph.Format.kDot;
        }
    }

    public synchronized void close() throws IOException {
        if (this.writerIsClosed) {
            return;
        }
        this.writer.close();
        this.writerIsClosed = true;
    }

    public synchronized boolean isClosed() {
        return this.writerIsClosed;
    }

    public String getFilepath() {
        return filepath;
    }

    @Override
    public Type getType() {
        return Type.FILE;
    }

}
