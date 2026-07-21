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

import spade.core.Graph;

/**
 * Output destination for the query command-line client.
 */
public interface User extends AutoCloseable {

    /**
     * Opens a file output stream for the given filepath.
     * If a file output stream is already open, it will be closed first.
     *
     * @param filepath path to the output file; must not be null
     * @throws IllegalArgumentException if filepath is null or the file cannot be opened
     */
    void openFile(String filepath) throws IllegalArgumentException;

    /**
     * Writes a string to the active output stream.
     * If a file is open, writes there and then closes the file.
     * Otherwise writes to standard output.
     *
     * @param str the string to write
     */
    void writeString(String str);

    /**
     * Writes a string followed by a system line separator to the active output stream.
     * If a file is open, writes there and then closes the file.
     * Otherwise writes to standard output.
     *
     * @param str the string to write; null is written as the string "null"
     */
    void writeStringLn(String str);

    /**
     * Writes a graph to the active output stream.
     * If a file is open, writes there and then closes the file.
     * Otherwise writes to standard output.
     *
     * @param graph the graph to write
     */
    void writeGraph(Graph graph);

    /**
     * Closes any open file output stream and releases resources.
     */
    @Override
    void close();

}
