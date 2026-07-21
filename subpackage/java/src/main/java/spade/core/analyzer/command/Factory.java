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

package spade.core.analyzer.command;

import spade.core.analyzer.command.exception.CommandFailure;
import spade.core.analyzer.command.exception.ServerFailure;

/*
    Query command factory to return an instance of the query command.
*/
public class Factory {

    public AbstractCommand createCommand(final String raw)
        throws ServerFailure, CommandFailure {
        if (raw == null) {
            throw new ServerFailure("Raw query command cannot be null");
        }
        final String trimmed = raw.trim();
        final String[] tokens = trimmed.split("\\s+");
        final String firstToken = tokens[0];
        switch (firstToken) {
            case "exit":
            case "quit":
                return Exit.create(raw);
            case "print":
                return PrintStorage.create(raw);
            case "set":
                return SetStorage.create(raw);
            default:
                return QuickGrail.create(raw);
        }
    }

}
