/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2017 SRI International
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
package spade.client;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;

import spade.client.commandline.ExecutionContext;
import spade.client.commandline.MainLoop;
import spade.client.commandline.ServerClientChannel;
import spade.client.commandline.ServerClientSocketChannel;
import spade.client.commandline.ServerQueryEnvironmentState;
import spade.client.commandline.UserArguments;
import spade.client.commandline.UserArgumentsParser;
import spade.client.commandline.command.Factory;
import spade.client.commandline.command.Source;
import spade.client.commandline.command.exception.IllegalCommand;
import spade.client.commandline.output.OutputStreamFactory;
import spade.core.Settings;

/**
 * @author raza
 */
public class CommandLine{

	private String getDefaultConfigFilePath() {
		return Settings.getDefaultConfigFilePath(this.getClass());
	}

	private void addInitialLoadCommand(final ExecutionContext execCtx)
		throws IllegalCommand, IllegalArgumentException {
		final String configFilePath = getDefaultConfigFilePath();
        final String cmdStr = "load " + configFilePath;
		execCtx.pushCommand(Source.CONSOLE, cmdStr);
	}
	
	private void run(String args[]) throws Exception {

		final String localHostName;
		final UserArguments userArgs;
		final ExecutionContext execCtx;

		userArgs = UserArgumentsParser.parse(args);

		if (userArgs.isShowHelp()) {
			System.out.println(UserArgumentsParser.help());
			return;
		}

		localHostName = "localhost";
		try (
			ServerClientChannel scc =
			new ServerClientSocketChannel(localHostName, userArgs.getRemoteHostName())
		) {
			scc.connect();
			try (
				spade.client.commandline.input.User userInput =
					new spade.client.commandline.input.UserConsole(userArgs.getCommandHistoryFile())
				) {
				try (
					spade.client.commandline.output.UserConsole userOutput =
						new spade.client.commandline.output.UserConsole(
							OutputStreamFactory.createStandardOutputStream(),
        					new BufferedWriter(new OutputStreamWriter(System.err))
						)
					) {
					execCtx = new ExecutionContext(userArgs, new Factory(), scc, userOutput);

					setupShutdownThread(execCtx);
					addInitialLoadCommand(execCtx);

					if (!userArgs.isBatchMode()) {
						userOutput.writeProgramHeader(localHostName);
					}

					try{
						new MainLoop().start(execCtx, userInput, userOutput);
					} finally {
						// Here when loop exited because of shutdown thread (Ctrl+C) or exit command.

						final ServerQueryEnvironmentState sqes = new ServerQueryEnvironmentState();
						sqes.fetchBestEffort(execCtx);
						sqes.saveToFile(getDefaultConfigFilePath());
					}
				}
			}
		}
	}

	private void setupShutdownThread(final ExecutionContext execCtx) {
		Runtime.getRuntime().addShutdownHook(
			new Thread(
				new Runnable() {
					@Override
					public void run(){
						execCtx.shutdown();
					}
				}
			)
		);
	}

	public static void main(String [] args) throws Exception {
		try {
			new CommandLine().run(args);
		} catch (Exception e) {
			System.err.println(
				"CommandLine client exited with error: "
				+ e.getMessage()
			);
		}
	}
}
