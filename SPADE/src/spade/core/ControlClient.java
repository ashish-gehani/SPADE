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

public class ControlClient {

    private static PrintStream outputStream;
    private static PrintStream errorStream;
    private static PrintStream SPADEControlIn;
    private static BufferedReader SPADEControlOut;
    private static String outputPath;
    private static volatile boolean shutdown;
    private static final String historyFile = "control.history";

    public static void main(String args[]) {

        outputStream = System.out;
        errorStream = System.err;
        shutdown = false;
        outputPath = args[1];

        try {
            SPADEControlIn = new PrintStream(new FileOutputStream(args[0]));
        } catch (Exception exception) {
            outputStream.println("Control pipes not ready!");
            System.exit(0);
        }
        
        Runnable outputReader = new Runnable() {

            public void run() {
                try {
                    SPADEControlOut = new BufferedReader(new FileReader(outputPath));
                    while (!shutdown) {
                        String outputLine = SPADEControlOut.readLine();
                        if (outputLine != null) {
                            outputStream.println(outputLine);
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
            outputStream.println("SPADE 2.0 Control Client");

            ConsoleReader commandReader = new ConsoleReader();
            commandReader.getHistory().setHistoryFile(new File(historyFile));

            List argCompletor1 = new LinkedList();
            argCompletor1.add(new SimpleCompletor(new String[]{"add"}));
            argCompletor1.add(new SimpleCompletor(new String[]{"filter", "storage", "reporter"}));
            argCompletor1.add(new NullCompletor());

            List argCompletor2 = new LinkedList();
            argCompletor2.add(new SimpleCompletor(new String[]{"remove"}));
            argCompletor2.add(new SimpleCompletor(new String[]{"filter", "storage", "reporter"}));
            argCompletor2.add(new NullCompletor());

            List argCompletor5 = new LinkedList();
            argCompletor5.add(new SimpleCompletor(new String[]{"list"}));
            argCompletor5.add(new SimpleCompletor(new String[]{"filters", "storages", "reporters", "all"}));
            argCompletor5.add(new NullCompletor());

            List argCompletor7 = new LinkedList();
            argCompletor7.add(new SimpleCompletor(new String[]{"config"}));
            argCompletor7.add(new SimpleCompletor(new String[]{"load", "save"}));
            argCompletor7.add(new NullCompletor());

            List completors = new LinkedList();
            completors.add(new ArgumentCompletor(argCompletor1));
            completors.add(new ArgumentCompletor(argCompletor2));
            completors.add(new ArgumentCompletor(argCompletor5));
            completors.add(new ArgumentCompletor(argCompletor7));

            commandReader.addCompletor(new MultiCompletor(completors));

            while (true) {
                String line = commandReader.readLine();
                if (line.split("\\s")[0].equalsIgnoreCase("query")) {
                    SPADEControlIn.println("");
                } else if (line.equalsIgnoreCase("exit")) {
                    shutdown = true;
                    System.exit(0);
                } else if (line.equalsIgnoreCase("shutdown")) {
                    shutdown = true;
                    SPADEControlIn.println("exit");
                    SPADEControlOut.close();
                    SPADEControlIn.close();
                    System.exit(0);
                } else {
                    SPADEControlIn.println(line);
                }
            }
        } catch (Exception exception) {
            exception.printStackTrace(errorStream);
        }
    }

}
