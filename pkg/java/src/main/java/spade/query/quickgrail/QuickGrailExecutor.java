/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2018 SRI International

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
package spade.query.quickgrail;

import java.io.Serializable;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.Settings;
import spade.core.analyzer.command.exception.CommandFailure;
import spade.core.analyzer.command.exception.ServerFailure;
import spade.core.analyzer.command.exception.UnexpectedFailure;
import spade.query.execution.Context;
import spade.query.quickgrail.core.AbstractQueryEnvironment;
import spade.query.quickgrail.core.Instruction;
import spade.query.quickgrail.core.Program;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.query.quickgrail.core.QuickGrailQueryResolver;
import spade.query.quickgrail.parser.DSLParserWrapper;
import spade.query.quickgrail.parser.ParseProgram;
import spade.utility.ArgumentFunctions;
import spade.utility.FileUtility;

/**
 * Top level class for the QuickGrail graph query executor.
 */
public class QuickGrailExecutor{

	private final Logger logger = Logger.getLogger(this.getClass().getName());

	private final String keyDebug = "debug";

	private boolean debug;

	public QuickGrailExecutor() throws UnexpectedFailure {
		final String configFile = Settings.getDefaultConfigFilePath(this.getClass());
		try{
			final Map<String, String> map = FileUtility.readConfigFileAsKeyValueMap(configFile, "=");
			this.debug = ArgumentFunctions.mustParseBoolean(keyDebug, map);
		}catch(Exception e){
			throw new UnexpectedFailure(
				"Failed to parse configuration file: '" + configFile + "'", e
			);
		}
	}

	private ParseProgram parseProgram(final String query) 
		throws ServerFailure, CommandFailure {
		if (query == null) {
			throw new ServerFailure("NULL query to execute");
		}

		try {
			final DSLParserWrapper parserWrapper = new DSLParserWrapper();
			final ParseProgram parseProgram = parserWrapper.fromText(query);
			if (debug) {
				logger.log(Level.INFO, "Parse tree:\n" + parseProgram.toString());
			}
			return parseProgram;
		} catch (Exception e) {
			throw new CommandFailure("Failed to parse query", e);
		}
	}

	private Program resolveProgram(
		final QueryInstructionExecutor executor,
		final ParseProgram parseProgram
	) throws ServerFailure, UnexpectedFailure {
		if (executor == null) {
			throw new ServerFailure("NULL query instruction executor for resolution");
		}
		if (parseProgram == null) {
			throw new ServerFailure("NULL parsed program to resolve");
		}

		final QuickGrailQueryResolver resolver = new QuickGrailQueryResolver();
		final AbstractQueryEnvironment environment = executor.getQueryEnvironment();
		if (environment == null) {
			throw new ServerFailure("NULL query environment for resolution");
		}

		try {
			final Program program = resolver.resolveProgram(
				parseProgram, environment
			);
			if (debug) {
				logger.log(Level.INFO, "Execution plan:\n" + program.toString());
			}
			return program;
		} catch (Exception e) {
			throw new UnexpectedFailure("Failed to resolve query", e);
		}
	}

	private void executeProgram(
		final Context ctx,
		final Program program
	) throws ServerFailure, UnexpectedFailure {
		if (ctx == null) {
			throw new ServerFailure("NULL context for execution");
		}
		if (program == null) {
			throw new ServerFailure("NULL program for execution");
		}

		final int instructionsSize = program.getInstructionsSize();
		for(int i = 0; i < instructionsSize; i++){
			final Instruction<? extends Serializable> instruction = 
				program.getInstruction(i);
			if (instruction == null) {
				throw new ServerFailure("NULL instruction found in program");
			}
			try{
				instruction.execute(ctx);
			}catch(Exception e){
				throw new UnexpectedFailure("Failed to execute instruction", e);
			}
		}
	}

	private Serializable getExecutionResult(
		final Program program
	) throws ServerFailure {
		if (program == null) {
			throw new ServerFailure("NULL program for execution");
		}

		final int instrSize = program.getInstructionsSize();
		if (instrSize == 0) {
			return "OK";
		}

		final Instruction<? extends Serializable> lastInstr = 
			program.getInstruction(instrSize - 1);
		if (lastInstr == null) {
			throw new ServerFailure("NULL instruction in program");
		}

		final Serializable lastInstrResult = lastInstr.getResult();
		if(lastInstrResult == null){
			return "OK";
		}

		return lastInstrResult;
	}

	public Serializable execute(final String query, final Context ctx)
		throws ServerFailure, CommandFailure, UnexpectedFailure {
		if (query == null) {
			throw new ServerFailure("NULL query to execute");
		}
		if (ctx == null) {
			throw new ServerFailure("NULL context to execute");
		}

		try {
			final ParseProgram parseProgram = parseProgram(query);
			final Program program = resolveProgram(
				ctx.getExecutor(),
				parseProgram
			);
			executeProgram(ctx, program);
			final Serializable result = getExecutionResult(program);
			return result;
		} catch (ServerFailure | CommandFailure | UnexpectedFailure e) {
			logger.log(Level.SEVERE, "Failed to execute query", e);
			throw e;
		} catch (RuntimeException e) {
			logger.log(Level.SEVERE, "Failed to execute query", e);
			throw new UnexpectedFailure("Runtime error in query execution", e);
		}
	}

}
