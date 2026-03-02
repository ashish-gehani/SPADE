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
import java.io.IOException;
import java.io.OutputStreamWriter;

import spade.core.Graph;
import spade.query.quickgrail.instruction.SaveGraph;

public class StandardOutputStream implements OutputStream {

    private final BufferedWriter writer;
    private boolean writerIsClosed = false;

    public StandardOutputStream() {
        this.writer = new BufferedWriter(new OutputStreamWriter(System.out));
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
        this.writer.flush();
    }

    @Override
    public synchronized void writeGraph(final Graph graph) throws IllegalArgumentException, IOException, Exception {
        ensureWriterIsOpen();
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }

        final SaveGraph.Format format = SaveGraph.Format.kDot;
        final boolean closeWriter = false;
        Graph.exportGraphUsingWriter(format, this.writer, graph, closeWriter);
        this.writer.flush();
    }

    public synchronized void close() throws IOException {
        if (this.writerIsClosed) {
            return;
        }
        this.writer.flush();
        this.writerIsClosed = true;
    }

    public synchronized boolean isClosed() {
        return this.writerIsClosed;
    }

    @Override
    public Type getType() {
        return Type.STANDARD_OUT;
    }

}
