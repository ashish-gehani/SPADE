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

import spade.core.analyzer.command.exception.CommandFailure;
import spade.core.analyzer.command.exception.ServerFailure;
import spade.core.analyzer.command.exception.UnexpectedFailure;
import spade.core.analyzer.command.execution.Context;

/*
    Parent of all commands
*/
public abstract class AbstractCommand {

    private final Type type;
    private final String raw;

    private boolean isExecutionComplete = false;

    private Serializable result;

    public AbstractCommand(final Type type, final String raw) 
        throws IllegalArgumentException {
        if (type == null) {
            throw new IllegalArgumentException("The type cannot be null");
        }
        if (raw == null) {
            throw new IllegalArgumentException("The raw command cannot be null");
        }
        this.type = type;
        this.raw = raw;
    }

    public final Type getType() {
        return this.type;
    }

    public final String getRaw() {
        return this.raw;
    }

    public synchronized final void execute(final Context ctx)
        throws CommandFailure, ServerFailure, UnexpectedFailure {
        try {
            this.result = this.executeInternal(ctx);
        } catch (CommandFailure | ServerFailure | UnexpectedFailure e) {
            throw e;
        } catch (Exception e) {
            throw new UnexpectedFailure("Unexpected command execution failure", e);
        } finally {
            this.isExecutionComplete = true;
        }
    }

    public synchronized final boolean isExecutionComplete() {
        return this.isExecutionComplete;
    }

    public synchronized final Serializable getResult() {
        return this.result;
    }

    @Override
    public String toString() {
        return "Command(type=" + type + ", raw='" + raw + ")";
    }

    /*
        Execute the command using the provided context.
    */
    protected abstract Serializable executeInternal(
        final Context ctx
    ) throws CommandFailure, ServerFailure, UnexpectedFailure;

}
