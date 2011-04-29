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
package spade.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.List;
import jline.ArgumentCompletor;
import jline.ConsoleReader;
import jline.MultiCompletor;
import jline.NullCompletor;
import jline.SimpleCompletor;

public class QueryClient {

    private static PrintStream outputStream;
    private static PrintStream errorStream;
    private static PrintStream SPADEQueryIn;
    private static BufferedReader SPADEQueryOut;
    private static String inputPath;
    private static String outputPath;
    private static volatile boolean shutdown;
    private static final String historyFile = "query.history";

    public static void main(String args[]) {

        outputStream = System.out;
        errorStream = System.err;
        inputPath = args[0];
        outputPath = args[1];
        shutdown = false;

        try {
            int exitValue = Runtime.getRuntime().exec("mkfifo " + outputPath).waitFor();
            if (exitValue != 0) {
                throw new Exception();
            }
            SPADEQueryIn = new PrintStream(new FileOutputStream(inputPath));
        } catch (Exception exception) {
            outputStream.println("Query pipes not ready!");
            System.exit(0);
        }
        
        Runnable outputReader = new Runnable() {

            public void run() {
                try {
                    SPADEQueryOut = new BufferedReader(new FileReader(outputPath));
                    while (!shutdown) {
                        if (SPADEQueryOut.ready()) {
                            String outputLine = SPADEQueryOut.readLine();
                            if (outputLine != null) {
                                outputStream.println(outputLine);
                            }
                        }
                        Thread.sleep(10);
                    }
                } catch (Exception exception) {
                    exception.printStackTrace(errorStream);
                }
            }
        };
        new Thread(outputReader).start();

        try {

            outputStream.println("");
            outputStream.println("SPADE 2.0 Query Client");
            outputStream.println("");

            ConsoleReader commandReader = new ConsoleReader();
            commandReader.getHistory().setHistoryFile(new File(historyFile));

            List argCompletor7 = new LinkedList();
            argCompletor7.add(new SimpleCompletor(new String[]{"query"}));
            argCompletor7.add(new NullCompletor());

            List completors = new LinkedList();
            completors.add(new ArgumentCompletor(argCompletor7));

            commandReader.addCompletor(new MultiCompletor(completors));

            SPADEQueryIn.println(outputPath + " ");
            while (true) {
                try {
                    String line = commandReader.readLine();
                    if (line.equalsIgnoreCase("exit")) {
                        shutdown = true;
                        Runtime.getRuntime().exec("rm -f " + outputPath).waitFor();
                        break;
                    } else {
                        SPADEQueryIn.println(outputPath + " " + line);
                    }
                } catch (Exception exception) {
                }
            }
        } catch (Exception exception) {
        }
    }

}
