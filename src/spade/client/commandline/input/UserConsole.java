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

import java.io.File;
import java.io.IOException;

import jline.ConsoleReader;

/*
    Interactive console input backed by jline ConsoleReader.
    Reads commands from standard input with history support.
*/
public class UserConsole implements User {

    private static final String COMMAND_PROMPT = "-> ";

    private final String commandHistoryFile;

    private final ConsoleReader commandReader;

    public UserConsole(final String commandHistoryFile) throws IOException {
        this.commandHistoryFile = commandHistoryFile;
        this.commandReader = new ConsoleReader();
        commandReader.getHistory().setHistoryFile(new File(commandHistoryFile));
    }

    @Override
    public String getCommandHistoryFile() {
        return commandHistoryFile;
    }

    @Override
    public String readCommand() throws IOException {
        System.out.println();
        return this.commandReader.readLine(COMMAND_PROMPT);
    }

    @Override
    public void writeCommand(final String cmd) throws IOException {
        System.out.println();
        System.out.println(COMMAND_PROMPT + cmd);
    }

    @Override
    public void close() {
        // ConsoleReader (jline 1.x) has no close method
    }

}
