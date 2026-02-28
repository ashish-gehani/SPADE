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

package spade.client.commandline;

import java.util.LinkedList;

import spade.client.commandline.command.AbstractCommand;
import spade.client.commandline.command.Factory;
import spade.client.commandline.command.exception.IllegalCommand;

/*
    The query command execution context.
*/
public class ExecutionContext {

    /*
        Flag to indicate that 'exit' command has been executed.
    */
    private boolean isShutdown;

    /*
        User arguments parsed from CLI.
    */
    private final UserArguments userArguments;

    /*
        Command factory to get instance of command based on text input.
    */
    private final Factory cmdFactory;

    /*
        Queue of pending commands.
    */
    private final LinkedList<AbstractCommand> cmds = new LinkedList<>();

    /*
        Server-client channel for communication.
    */
    private final ServerClientChannel serverClientChannel;

    /*
        Manage user output.
    */
    private final spade.client.commandline.output.User userOutput;


    public ExecutionContext(
        final UserArguments userArguments,
        final Factory cmdFactory,
        final ServerClientChannel serverClientChannel,
        final spade.client.commandline.output.User userOutput
    ) throws IllegalArgumentException {
        if (userArguments == null) {
            throw new IllegalArgumentException("NULL user arguments");
        }
        if (cmdFactory == null) {
            throw new IllegalArgumentException("NULL query command factory");
        }
        if (serverClientChannel == null) {
            throw new IllegalArgumentException("NULL server-client channel");
        }
        if (!serverClientChannel.isConnected()) {
            throw new IllegalArgumentException("Server-client channel is not connected");
        }
        if (userOutput == null) {
            throw new IllegalArgumentException("NULL user output");
        }
        this.userArguments = userArguments;
        this.cmdFactory = cmdFactory;
        this.serverClientChannel = serverClientChannel;
        this.userOutput = userOutput;
    }

    public void pushCommand(final String raw)
        throws IllegalCommand, IllegalArgumentException {
        final AbstractCommand cmd = createCommand(raw);
        cmds.addLast(cmd);
    }

    public AbstractCommand createCommand(final String raw)
        throws IllegalCommand, IllegalArgumentException {
        return cmdFactory.createCommand(raw);
    }

    public void prependCommands(final LinkedList<AbstractCommand> commands) throws IllegalArgumentException {
        if (commands == null) {
            throw new IllegalArgumentException("Commands list cannot be null");
        }
        // Add all commands at the front while maintaining their order
        int index = 0;
        for (final AbstractCommand cmd : commands) {
            if (cmd == null) {
                throw new IllegalArgumentException("Command at index " + index + " cannot be null");
            }
            cmds.add(index, cmd);
            index++;
        }
    }

    public boolean hasCommands() {
        return !cmds.isEmpty();
    }
    
    public AbstractCommand getNextCommand() {
        if (!hasCommands()) {
            return null;
        }
        return cmds.removeFirst();
    }

    public void clearCommands() {
        cmds.clear();
    }

    public UserArguments getUserArguments() {
        return userArguments;
    }

    public spade.client.commandline.output.User getUserOutput() {
        return userOutput;
    }

    public ServerClientChannel getServerClientChannel() {
        return serverClientChannel;
    }

    public void shutdown() {
        isShutdown = true;
    }

    public boolean isShutdown() {
        return isShutdown;
    }

}
