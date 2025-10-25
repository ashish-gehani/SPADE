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

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.Query;
import spade.core.Settings;
import spade.query.execution.Context;
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

	public QuickGrailExecutor() throws Exception {
		final String configFile = Settings.getDefaultConfigFilePath(this.getClass());
		try{
			final Map<String, String> map = FileUtility.readConfigFileAsKeyValueMap(configFile, "=");
			debug = ArgumentFunctions.mustParseBoolean(keyDebug, map);
		}catch(Exception e){
			throw new Exception("Failed to parse configuration file: '" + configFile + "'", e);
		}
	}

	public void execute(final Query query, Context ctx){
		if (query == null)
			throw new IllegalArgumentException("NULL query to execute");
		if (ctx == null)
			throw new IllegalArgumentException("NULL context to execute");
		try{
			final QueryInstructionExecutor executor = ctx.getExecutor();

			final DSLParserWrapper parserWrapper = new DSLParserWrapper();

			final ParseProgram parseProgram = parserWrapper.fromText(query.query);

			final QuickGrailQueryResolver resolver = new QuickGrailQueryResolver();
			final Program program = resolver.resolveProgram(parseProgram, executor.getQueryEnvironment());

			if(debug){
				logger.log(Level.INFO, "Parse tree:\n" + parseProgram.toString());
				logger.log(Level.INFO, "Execution plan:\n" + program.toString());
			}

			try{
				final int instructionsSize = program.getInstructionsSize();
				for(int i = 0; i < instructionsSize; i++){
					final Instruction<? extends Serializable> instruction = program.getInstruction(i);
					try{
						instruction.execute(ctx);
					}catch(Exception e){
						throw e;
					}
				}

			}finally{
				executor.getQueryEnvironment().doGarbageCollection();
			}

			Serializable result = "OK";
			// Only here if success
			if(program.getInstructionsSize() > 0){
				final Serializable lastInstructionResult = program.getInstruction(program.getInstructionsSize() - 1)
						.getResult();
				if(lastInstructionResult != null){
					result = lastInstructionResult;
				}
			}
			query.querySucceeded(result);
		}catch(Exception e){
			logger.log(Level.SEVERE, null, e);

			final StringWriter stackTrace = new StringWriter();
			final PrintWriter pw = new PrintWriter(stackTrace);
			pw.println("Error evaluating QuickGrail command:");
			pw.println("------------------------------------------------------------");
			pw.println(e.getMessage());
			pw.println("------------------------------------------------------------");

			query.queryFailed(new Exception(stackTrace.toString(), e));
		}
	}

}
