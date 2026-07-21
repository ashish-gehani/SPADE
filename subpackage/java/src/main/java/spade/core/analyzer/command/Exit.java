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

import java.io.Serializable;

import spade.core.analyzer.command.execution.Context;
import spade.core.analyzer.command.exception.CommandFailure;
import spade.core.analyzer.command.exception.ServerFailure;
import spade.core.analyzer.command.exception.UnexpectedFailure;

/*
    Shutdown the client connection.
*/
public class Exit extends AbstractCommand {

    public Exit(final Type type, final String raw)
        throws IllegalArgumentException {
        super(type, raw);
    }

    public static Exit create(final String raw)
        throws ServerFailure, CommandFailure {
        if (raw == null) {
            throw new ServerFailure("Raw query command cannot be null");
        }
        final String expectedSyntax = "exit | quit";
        final String trimmed = raw.trim();
        if (!trimmed.equals("exit") && !trimmed.equals("quit")) {
            throw new CommandFailure(
                "Invalid command syntax. Expected: " + expectedSyntax + ". Actual: " + raw
            );
        }
        final Exit instance = new Exit(Type.EXIT, raw);
        return instance;
    }

    @Override
    protected Serializable executeInternal(
        final Context ctx
    ) throws CommandFailure, ServerFailure, UnexpectedFailure {
        if (ctx == null) {
            throw new ServerFailure("Execution context cannot be null");
        }
        ctx.shutdown();
        return null;
    }
}
