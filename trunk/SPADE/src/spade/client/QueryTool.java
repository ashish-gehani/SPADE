/*
--------------------------------------------------------------------------------
SPADE - Support for Provenance Auditing in Distributed Environments.
Copyright (C) 2011 SRI International

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

import java.io.FileOutputStream;
import java.io.PrintStream;

public class QueryTool {

    private static PrintStream errorStream;
    private static PrintStream SPADEQueryIn;
    private static String inputPath;
    private static String outputPath;

    public static void main(String args[]) {

        errorStream = System.err;
        inputPath = args[0];
        outputPath = args[1];

        try {
            // Create the output pipe for queries.
            SPADEQueryIn = new PrintStream(new FileOutputStream(inputPath));
        } catch (Exception exception) {
            errorStream.println("Query pipe not ready!");
            System.exit(0);
        }

        SPADEQueryIn.println(outputPath + " ");

        // Build the query expression from the argument tokens.
        String line = "";
        for (int i = 2; i < args.length; i++) {
            line += args[i] + " ";
        }

        if (!line.equalsIgnoreCase("exit")) {
            // The output path is embedded in each query sent to SPADE
            // as the first token of the query. This is to allow multiple
            // query clients to work simultaneously with SPADE.
            SPADEQueryIn.println(outputPath + " " + line);
        }
    }
}
