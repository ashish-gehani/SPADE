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

package spade.client.commandline.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import spade.client.commandline.output.User;
import spade.core.Graph;

/**
 * Test implementation of {@link User} that captures all output in memory.
 * Tests inspect what was written via {@link #consumeOutput} and
 * {@link #consumeGraphs}.
 */
public class UserTestOutput implements User {

    private final StringBuilder outputBuffer = new StringBuilder();
    private final List<Graph> graphs = new ArrayList<Graph>();

    @Override
    public void openFile(final String filepath) throws IllegalArgumentException {
        // No file I/O in tests
    }

    @Override
    public void writeString(final String str) {
        outputBuffer.append(str);
    }

    @Override
    public void writeStringLn(final String str) {
        outputBuffer.append(String.valueOf(str)).append(System.lineSeparator());
    }

    @Override
    public void writeGraph(final Graph graph) {
        graphs.add(graph);
    }

    /**
     * Returns all string output written so far and clears the buffer.
     *
     * @return accumulated string output
     */
    public String consumeOutput() {
        final String content = outputBuffer.toString();
        outputBuffer.setLength(0);
        return content;
    }

    /**
     * Returns all graphs written so far and clears the list.
     *
     * @return unmodifiable snapshot of accumulated graphs
     */
    public List<Graph> consumeGraphs() {
        final List<Graph> snapshot = Collections.unmodifiableList(
            new ArrayList<Graph>(graphs)
        );
        graphs.clear();
        return snapshot;
    }

    @Override
    public void close() {
        outputBuffer.setLength(0);
        graphs.clear();
    }

}
