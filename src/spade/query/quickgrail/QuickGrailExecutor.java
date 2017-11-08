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
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import spade.core.AbstractQuery;
import spade.query.quickgrail.kernel.Environment;
import spade.query.quickgrail.kernel.Program;
import spade.query.quickgrail.kernel.Resolver;
import spade.query.quickgrail.parser.DSLParserWrapper;
import spade.query.quickgrail.parser.ParseProgram;
import spade.storage.quickstep.QuickstepExecutor;

/**
 * Top level class for the QuickGrail graph query executor.
 */
public class QuickGrailExecutor extends AbstractQuery<String> {
  private QuickstepExecutor qs;

  public QuickGrailExecutor(QuickstepExecutor qs) {
    this.qs = qs;
  }

  @Override
  public String execute(String query) {
    ArrayList<Object> responses = null;
    try {
      DSLParserWrapper parserWrapper = new DSLParserWrapper();
      ParseProgram parseProgram = parserWrapper.fromText(query);

      qs.logInfo("Parse tree:\n" + parseProgram.toString());

      Environment env = new Environment(qs);

      Resolver resolver = new Resolver();
      Program program = resolver.resolveProgram(parseProgram, env);

      qs.logInfo("Execution plan:\n" + program.toString());

      try {
        responses = program.execute(qs);
      } finally {
        env.gc();
      }
    } catch (Exception e)  {
      responses = new ArrayList<Object>();
      StringWriter stackTrace = new StringWriter();
      PrintWriter pw = new PrintWriter(stackTrace);
      pw.println("Error evaluating QuickGrail command:");
      pw.println("------------------------------------------------------------");
      // e.printStackTrace(pw);
      pw.println(e.getMessage());
      pw.println("------------------------------------------------------------");
      responses.add(stackTrace.toString());
    }

    if (responses == null || responses.isEmpty()) {
      return "OK";
    } else {
      // Currently only return the last response.
      Object response = responses.get(responses.size()-1);
      return response == null ? "" : response.toString();
    }
  }

  @Override
  public String execute(Map<String, List<String>> parameters, Integer limit) {
    throw new RuntimeException("Not supported");
  }
}
