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

import spade.client.commandline.ExecutionContext;
import spade.client.commandline.command.exception.CommandExecutionNotComplete;
import spade.client.commandline.command.exception.IllegalCommandResult;

/*
    Empty query command (whitespace or empty string).
*/
public class Empty extends AbstractCommand {

    public Empty(final Source source, final Type type, final String raw)
        throws IllegalArgumentException {
        super(source, type, raw);
    }

    public static Empty create(final Source source, final String raw)
        throws IllegalArgumentException {
        if (raw == null) {
            throw new IllegalArgumentException("Raw query command cannot be null");
        }
        final Empty instance = new Empty(source, Type.EMPTY, raw);
        return instance;
    }

    @Override
    protected synchronized final Object executeInternal(final ExecutionContext ctx) throws IllegalArgumentException {
        if (ctx == null) {
            throw new IllegalArgumentException("Execution context cannot be null");
        }
        return null; // no-op
    }

    @Override
    protected synchronized void writeExecutionResultInternal(
        final spade.client.commandline.output.User userOutput
    ) throws IllegalArgumentException, CommandExecutionNotComplete, IllegalCommandResult {
        // no-op
    }

}
