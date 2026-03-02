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

import java.io.IOException;

import spade.client.commandline.command.AbstractCommand;
import spade.client.commandline.command.Source;
import spade.client.commandline.command.exception.CommandExecutionNotComplete;
import spade.client.commandline.command.exception.IllegalCommand;
import spade.client.commandline.command.exception.IllegalCommandResult;

public class MainLoop {
    
    public void start(
        final ExecutionContext execCtx,
        final spade.client.commandline.input.User userInput,
        final spade.client.commandline.output.User userOutput
    ) throws 
        IllegalArgumentException, 
        IllegalCommand,
        CommandExecutionNotComplete, 
        IllegalCommandResult,
        IOException,
        Exception {
        if (execCtx == null) {
            throw new IllegalArgumentException("NULL execution context");
        }
        if (userInput == null) {
            throw new IllegalArgumentException("NULL user input");
        }
        if (userOutput == null) {
            throw new IllegalArgumentException("NULL user output");
        }
		
		while (!execCtx.isShutdown()) {
			final AbstractCommand cmd = execCtx.getNextCommand();
			if (cmd != null) {

                // We are executing a loaded command. We need to show the user
                // the command that is being executing rather than silently 
                // executing it.
                if (cmd.getSource() == Source.LOAD) {
                    userInput.writeCommand(cmd.getRaw());
                }

				cmd.execute(execCtx);
			    cmd.writeExecutionResult(userOutput);
                if (execCtx.isShutdown()) {
                    break;
                } else {
                    continue;
                }
			}

            // If command was null, just read the next command from user.

			final String rawCmdStr = userInput.readCommand();
            if (rawCmdStr == null) {
                execCtx.shutdown();
                continue;
            }
			execCtx.pushCommand(Source.CONSOLE, rawCmdStr);
		}
    }

}
