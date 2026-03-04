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
import spade.core.AbstractStorage;
import spade.core.analyzer.QueryableStorage;
import spade.core.analyzer.command.exception.CommandFailure;
import spade.core.analyzer.command.exception.ServerFailure;
import spade.core.analyzer.command.exception.UnexpectedFailure;

/*
    Print the current storage.
*/
public class PrintStorage extends AbstractCommand {

    public PrintStorage(final Type type, final String raw)
        throws IllegalArgumentException {
        super(type, raw);
    }

    public static PrintStorage create(final String raw)
        throws ServerFailure, CommandFailure {
        if (raw == null) {
            throw new IllegalArgumentException("Raw query command cannot be null");
        }
        final String expectedSyntax = "print storage";
        final String trimmed = raw.trim();
        final String toks[] = trimmed.split("\\s+", 2);
        if (
            toks.length != 2
            || !"print".equalsIgnoreCase(toks[0].trim())
            || !"storage".equalsIgnoreCase(toks[1].trim())
        ) {
            throw new CommandFailure(
                "Invalid command syntax. Expected: " + expectedSyntax + ". Actual: " + raw
            );
        }
        final PrintStorage instance = new PrintStorage(Type.PRINT_STORAGE, raw);
        return instance;
    }

    @Override
    protected Serializable executeInternal(
        final Context ctx
    ) throws CommandFailure, ServerFailure, UnexpectedFailure {
        if (ctx == null) {
            throw new ServerFailure("Execution context cannot be null");
        }
        if (!ctx.isStorageSet()) {
            throw new CommandFailure("No current storage set");
        }
        final QueryableStorage qStorage = ctx.getStorage();
        if (qStorage == null) {
            throw new CommandFailure("Null queryable storage set");
        }
        final AbstractStorage storage = qStorage.getStorage();
        if (storage == null) {
            throw new CommandFailure("Null storage set");
        }
        final String storageName = storage.getClass().getSimpleName();
        return storageName;
    }
}
