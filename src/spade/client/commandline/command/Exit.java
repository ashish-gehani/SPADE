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
import spade.utility.exception.CommandExecutionNotComplete;
import spade.utility.exception.IllegalCommand;
import spade.utility.exception.IllegalCommandResult;

/*
    Exit the query client.
*/
public class Exit extends AbstractCommand {

    public Exit(final Source source, final Type type, final String raw)
        throws IllegalArgumentException {
        super(source, type, raw);
    }

    public static Exit create(final Source source, final String raw)
        throws IllegalArgumentException, IllegalCommand {
        if (raw == null) {
            throw new IllegalArgumentException("Raw query command cannot be null");
        }
        final String expectedSyntax = "exit|quit";
        final String trimmed = raw.trim();
        if (!trimmed.equals("exit") && !trimmed.equals("quit")) {
            throw new IllegalCommand(
                "Invalid 'exit' syntax", expectedSyntax, raw
            );
        }
        final Exit instance = new Exit(source, Type.EXIT, raw);
        return instance;
    }

    @Override
    protected final synchronized Object executeInternal(final ExecutionContext ctx) throws IllegalArgumentException {
        if (ctx == null) {
            throw new IllegalArgumentException("Execution context cannot be null");
        }
        ctx.clearCommands();
        ctx.shutdown();
        return null;
    }

    @Override
    protected synchronized void writeExecutionResultInternal(
        final spade.client.commandline.output.User userOutput
    ) throws IllegalArgumentException, CommandExecutionNotComplete, IllegalCommandResult {
        // no-op
    }
}
