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

import java.util.LinkedList;
import java.util.Queue;

import spade.client.commandline.input.User;

/**
 * Test implementation of {@link User} backed by an in-memory command queue.
 * Tests enqueue commands via {@link #writeCommand} and the client reads them
 * via {@link #readCommand}. Returns null once the queue is exhausted.
 */
public class UserTestInput implements User {

    private final Queue<String> commands = new LinkedList<String>();

    /**
     * Adds a command to the end of the queue.
     * The next {@link #readCommand} call will return commands in the order
     * they were written.
     *
     * @param command the command string to enqueue; may be null
     */
    public void writeCommand(final String command) {
        commands.add(command);
    }

    @Override
    public String getCommandHistoryFile() {
        return null;
    }

    @Override
    public String readCommand() {
        return commands.poll();
    }

    @Override
    public void close() {
        commands.clear();
    }

}
