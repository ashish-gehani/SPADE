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

import spade.core.AbstractStorage;
import spade.core.Kernel;
import spade.core.analyzer.command.execution.Context;
import spade.core.exception.StorageNotQueryable;
import spade.core.analyzer.QueryableStorage;
import spade.core.analyzer.command.exception.CommandFailure;
import spade.core.analyzer.command.exception.ServerFailure;
import spade.core.analyzer.command.exception.UnexpectedFailure;

/*
    Set the current storage.
*/
public class SetStorage extends AbstractCommand {

    private final String storageName;

    public SetStorage(final Type type, final String raw, final String storageName)
        throws IllegalArgumentException {
        super(type, raw);
        this.storageName = storageName;
    }

    public static SetStorage create(final String raw)
        throws ServerFailure, CommandFailure {
        if (raw == null) {
            throw new IllegalArgumentException("Raw query command cannot be null");
        }
        final String expectedSyntax = "set storage <storage name>";
        final String trimmed = raw.trim();
        final String toks[] = trimmed.split("\\s+", 3);
        if (
            toks.length != 3
            || !"set".equalsIgnoreCase(toks[0].trim())
            || !"storage".equalsIgnoreCase(toks[1].trim())
        ) {
            throw new CommandFailure(
                "Invalid command syntax. Expected: " + expectedSyntax + ". Actual: " + raw
            );
        }
        final String storageName = toks[2].trim();
        final SetStorage instance = new SetStorage(Type.SET_STORAGE, raw, storageName);
        return instance;
    }

    public String getStorageName() {
        return storageName;
    }

    private final synchronized void clearCurrentStorage(final Context ctx) {
        final QueryableStorage storageCurrent = ctx.getStorage();
        if (storageCurrent == null) {
            return;
        }
        storageCurrent.doQueryStateCleanup();
        ctx.setStorage(null);
    }

    private final synchronized void setCurrentStorage(
        final Context ctx,
        final QueryableStorage storage
    ) {
        ctx.setStorage(storage);
    }

    @Override
    protected Serializable executeInternal(
        final Context ctx
    ) throws CommandFailure, ServerFailure, UnexpectedFailure {
        if (ctx == null) {
            throw new ServerFailure("Execution context cannot be null");
        }

        if(storageName == null || storageName.isBlank()){
            throw new ServerFailure("Cannot set current storage to null/empty");
        }

        final AbstractStorage storage = Kernel.getStorage(storageName);
        if(storage == null){
            throw new CommandFailure("Storage '" + storageName + "' not found.");
        }

        try {
            final QueryableStorage queryableStorage = new QueryableStorage(storage);
            clearCurrentStorage(ctx);
            setCurrentStorage(ctx, queryableStorage);
            return "Storage '" + storageName + "' successfully set for querying";
        } catch (StorageNotQueryable e) {
            throw new CommandFailure("Storage '" + storageName + "' is not queryable.", e);
        }
    }
}
