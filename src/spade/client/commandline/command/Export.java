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
import spade.client.commandline.command.exception.IllegalCommand;

/*
    Export result to a file (JSON or DOT format).
*/
public class Export extends AbstractCommand {

    private final String outputFilePath;

    public Export(final Type type, final String raw, final String outputFilePath)
        throws IllegalArgumentException {
        super(type, raw);
        this.outputFilePath = outputFilePath;
    }

    public String getOutputFilePath() {
        return outputFilePath;
    }

    public static Export create(final String raw)
        throws IllegalArgumentException, IllegalCommand {
        if (raw == null) {
            throw new IllegalArgumentException("Raw query command cannot be null");
        }
        final String expectedSyntax = "export > <json or dot filepath>";
        final String [] toks = raw.split("\\s+", 3);
        if (toks.length != 3) {
            throw new IllegalCommand(
                "Invalid 'export' syntax", expectedSyntax, raw
            );
        }
        if (!">".equals(toks[1])) {
            throw new IllegalCommand(
                "Invalid 'export' syntax - missing '>' operator", expectedSyntax, raw
            );
        }
        final String outputFilePath = toks[2];
        final Export instance = new Export(Type.EXPORT, raw, outputFilePath);
        return instance;
    }

    @Override
    protected final synchronized Object executeInternal(final ExecutionContext ctx) throws IllegalArgumentException {
        if (ctx == null) {
            throw new IllegalArgumentException("Execution context cannot be null");
        }
        final spade.client.commandline.output.User userOutput = ctx.getUserOutput();
        userOutput.openFile(outputFilePath);
        return null;
    }

    @Override
    protected synchronized void writeExecutionResultInternal(
        final spade.client.commandline.output.User userOutput
    ) throws IllegalArgumentException, CommandExecutionNotComplete, IllegalCommandResult {
        // no-op
    }

}
