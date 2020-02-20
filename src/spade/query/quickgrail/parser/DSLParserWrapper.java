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
package spade.query.quickgrail.parser;

import java.io.IOException;

import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

public class DSLParserWrapper {
  public ParseProgram fromText(String text) {
    CharStream input = CharStreams.fromString(text);
    return fromCharStream(input);
  }

  public ParseProgram fromFile(String filename) throws IOException {
    CharStream input = CharStreams.fromFileName(filename);
    return fromCharStream(input);
  }

  public ParseProgram fromStdin() throws IOException {
    CharStream input = CharStreams.fromStream(System.in);
    return fromCharStream(input);
  }

  private ParseProgram fromCharStream(CharStream input) {
    DSLLexer lexer = new DSLLexer(input);
    DSLParser parser = new DSLParser(new CommonTokenStream(lexer));
    parser.setErrorHandler(new BailErrorStrategy());
    return parser.program().r;
  }
}
