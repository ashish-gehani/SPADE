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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;

import spade.client.commandline.ExecutionContext;
import spade.client.commandline.UserArguments;
import spade.client.commandline.command.exception.IllegalCommandResult;
import spade.client.commandline.command.exception.CommandExecutionNotComplete;
import spade.client.commandline.command.exception.IllegalCommand;

/*
    Load queries from a file.
*/
public class Load extends AbstractCommand {

    private final String queriesFilePath;

    public Load(final Type type, final String raw, final String queriesFilePath) 
        throws IllegalArgumentException {
        super(type, raw);
        this.queriesFilePath = queriesFilePath;
    }

    public String getQueriesFilePath() {
        return queriesFilePath;
    }

    public static Load create(final String raw)
        throws IllegalArgumentException, IllegalCommand {
        if (raw == null) {
            throw new IllegalArgumentException("Raw query command cannot be null");
        }
        final String expectedSyntax = "load <filepath>";
        final String [] toks = raw.split("\\s+", 2);
        if (toks.length != 2) {
            throw new IllegalCommand(
                "Invalid 'load' syntax", expectedSyntax, raw
            );
        }
        final String queriesFilePath = toks[1];
        final Load instance = new Load(Type.LOAD, raw, queriesFilePath);
        return instance;
    }

    @Override
    protected synchronized Object executeInternal(final ExecutionContext ctx) throws IllegalArgumentException {
        if (ctx == null) {
            throw new IllegalArgumentException("Execution context cannot be null");
        }
        if (queriesFilePath == null) {
            throw new IllegalArgumentException("Queries file path cannot be null");
        }

        final UserArguments userArgs = ctx.getUserArguments();
        final int maxQueriesInFile = userArgs.getMaxQueriesInFile();

        final LinkedList<AbstractCommand> commands = new LinkedList<>();

        // Read all queries from file
        try (BufferedReader reader = new BufferedReader(new FileReader(queriesFilePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (commands.size() >= maxQueriesInFile) {
                    final spade.client.commandline.output.User userOutput = ctx.getUserOutput();
                    userOutput.writeStringLn(
                        "Only read upto '" + commands.size() + "' queries in file."
                    );
                    break;
                }

                final String trimmedLine = line.trim();

                // Skip empty lines and comments
                if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                    continue;
                }

                final AbstractCommand cmd = ctx.createCommand(trimmedLine);
                commands.add(cmd);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(
                "Failed to read queries file '" + queriesFilePath + "': " + e.getMessage(), e
            );
        } catch (IllegalCommand e) {
            throw new IllegalArgumentException(
                "Invalid query in file '" + queriesFilePath + "': " + e.getMessage(), e
            );
        }

        // Prepend all commands to the execution context
        ctx.prependCommands(commands);

        return null;
    }

    @Override
    protected synchronized void writeExecutionResultInternal(
        final spade.client.commandline.output.User userOutput
    ) throws IllegalArgumentException, CommandExecutionNotComplete, IllegalCommandResult {
        // no-op
    }
}
