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

package spade.client.commandline.command;

import spade.client.commandline.command.exception.IllegalCommand;

/*
    Query command factory to return an instance of the query command.
*/
public class Factory {

    public AbstractCommand createCommand(final String raw)
        throws IllegalArgumentException, IllegalCommand {
        if (raw == null) {
            throw new IllegalArgumentException("Raw query command cannot be null");
        }
        final String trimmed = raw.trim();
        final String[] tokens = trimmed.split("\\s+");
        final String firstToken = tokens[0];
        switch (firstToken) {
            case "":
                return Empty.create(raw);
            case "load":
                return Load.create(raw);
            case "export":
                return Export.create(raw);
            case "exit":
            case "quit":
                return Exit.create(raw);
            default:
                return Server.create(raw);
        }
    }

}
