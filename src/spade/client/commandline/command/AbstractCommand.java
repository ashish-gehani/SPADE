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
    Parent of all commands
*/
public abstract class AbstractCommand {

    private final Source source;
    private final Type type;
    private final String raw;

    private boolean isExecutionComplete = false;
    private Object executionResult;

    public AbstractCommand(final Source source, final Type type, final String raw) 
        throws IllegalArgumentException {
        if (source == null) {
            throw new IllegalArgumentException("The source cannot be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("The type cannot be null");
        }
        if (raw == null) {
            throw new IllegalArgumentException("The raw command cannot be null");
        }
        this.source = source;
        this.type = type;
        this.raw = raw;
    }

    public final Source getSource() {
        return this.source;
    }

    public final Type getType() {
        return this.type;
    }

    public final String getRaw() {
        return this.raw;
    }

    public synchronized final void execute(final ExecutionContext ctx)
        throws IllegalArgumentException {
        try {
            this.executionResult = this.executeInternal(ctx);
        } catch (Exception e) {
            throw e;
        } finally {
            this.isExecutionComplete = true;
        }
    }

    public synchronized final boolean isExecutionComplete() {
        return this.isExecutionComplete;
    }

    public synchronized final Object getExecutionResult() {
        return this.executionResult;
    }

    public synchronized final void writeExecutionResult(
        final spade.client.commandline.output.User userOutput
    ) throws IllegalArgumentException, CommandExecutionNotComplete, IllegalCommandResult {
        if (userOutput == null) {
            throw new IllegalArgumentException("NULL user output instance");
        }
        if (!isExecutionComplete()) {
            throw new CommandExecutionNotComplete("Command '" + getRaw() + "' is still in progress");
        }
        writeExecutionResultInternal(userOutput);
    }

    /*
        Execute the command using the provided context.
    */
    protected abstract Object executeInternal(
        final ExecutionContext ctx
    ) throws IllegalArgumentException;

    protected abstract void writeExecutionResultInternal(
        final spade.client.commandline.output.User userOutput
    ) throws IllegalArgumentException, CommandExecutionNotComplete, IllegalCommandResult;
}
