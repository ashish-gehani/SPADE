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

package spade.client.commandline.input;

import java.io.IOException;

/**
 * Input source for the query command-line client.
 */
public interface User extends AutoCloseable {

    /**
     * Returns the path of the command history file.
     *
     * @return command history file path
     */
    String getCommandHistoryFile();

    /**
     * Reads the next command line from the input source.
     * Returns null on EOF.
     *
     * @return the next command string, or null on EOF
     * @throws IOException if an I/O error occurs
     */
    String readCommand() throws IOException;

    /**
     * Writes the next command to user input for loaded commands.
     *
     * @throws IOException if an I/O error occurs
     */
    void writeCommand(final String cmd) throws IOException;

    /**
     * Closes the input source and releases associated resources.
     */
    @Override
    void close();

}
