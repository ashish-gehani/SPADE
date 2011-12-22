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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.LinkedList;
import java.util.List;
import jline.ArgumentCompletor;
import jline.Completor;
import jline.ConsoleReader;
import jline.MultiCompletor;
import jline.NullCompletor;
import jline.SimpleCompletor;
import spade.core.Kernel;

public class ControlClient {

    private static PrintStream outputStream;
    private static PrintStream errorStream;
    private static PrintStream SPADEControlIn;
    private static BufferedReader SPADEControlOut;
    private static volatile boolean shutdown;
    private static final String historyFile = "cfg/control.history";
    private static final String COMMAND_PROMPT = "-> ";
    private static final int THREAD_SLEEP_DELAY = 10;

    public static void main(String args[]) {

        outputStream = System.out;
        errorStream = System.err;

        shutdown = false;

        Runnable outputReader = new Runnable() {

            public void run() {
                try {
                    SocketAddress sockaddr = new InetSocketAddress("localhost", Kernel.LOCAL_CONTROL_PORT);
                    Socket remoteSocket = new Socket();
                    remoteSocket.connect(sockaddr, Kernel.CONNECTION_TIMEOUT);
                    OutputStream outStream = remoteSocket.getOutputStream();
                    InputStream inStream = remoteSocket.getInputStream();
                    SPADEControlOut = new BufferedReader(new InputStreamReader(inStream));
                    SPADEControlIn = new PrintStream(outStream);
            
                    while (!shutdown) {
                        if (SPADEControlOut.ready()) {
                            // This thread keeps reading from the output pipe and
                            // printing to the current output stream.
                            String outputLine = SPADEControlOut.readLine();
                            if (outputLine != null) {
                                outputStream.println(outputLine);
                            }
                            if (outputLine.equals("")) {
                                outputStream.print(COMMAND_PROMPT);
                            }
                        }
                        Thread.sleep(THREAD_SLEEP_DELAY);
                    }
                    SPADEControlOut.close();
                } catch (Exception exception) {
                    System.out.println("Error connecting to SPADE");
                    System.exit(-1);
                }
            }
        };
        new Thread(outputReader).start();

        try {

            outputStream.println("");
            outputStream.println("SPADE 2.0 Control Client");
            outputStream.println("");

            // Set up command history and tab completion.

            ConsoleReader commandReader = new ConsoleReader();
            commandReader.getHistory().setHistoryFile(new File(historyFile));

            List<Completor> argCompletor1 = new LinkedList<Completor>();
            argCompletor1.add(new SimpleCompletor(new String[]{"add"}));
            argCompletor1.add(new SimpleCompletor(new String[]{"filter", "storage", "sketch", "reporter"}));
            argCompletor1.add(new NullCompletor());

            List<Completor> argCompletor2 = new LinkedList<Completor>();
            argCompletor2.add(new SimpleCompletor(new String[]{"remove"}));
            argCompletor2.add(new SimpleCompletor(new String[]{"filter", "storage", "sketch", "reporter"}));
            argCompletor2.add(new NullCompletor());

            List<Completor> argCompletor5 = new LinkedList<Completor>();
            argCompletor5.add(new SimpleCompletor(new String[]{"list"}));
            argCompletor5.add(new SimpleCompletor(new String[]{"filters", "storages", "reporters", "all"}));
            argCompletor5.add(new NullCompletor());

            List<Completor> argCompletor7 = new LinkedList<Completor>();
            argCompletor7.add(new SimpleCompletor(new String[]{"config"}));
            argCompletor7.add(new SimpleCompletor(new String[]{"load", "save"}));
            argCompletor7.add(new NullCompletor());

            List<Completor> completors = new LinkedList<Completor>();
            completors.add(new ArgumentCompletor(argCompletor1));
            completors.add(new ArgumentCompletor(argCompletor2));
            completors.add(new ArgumentCompletor(argCompletor5));
            completors.add(new ArgumentCompletor(argCompletor7));

            commandReader.addCompletor(new MultiCompletor(completors));

            SPADEControlIn.println("");
            while (true) {
                String line = commandReader.readLine();
                if (line.split("\\s")[0].equalsIgnoreCase("query")) {
                    // Do not allow query commands from this control shell.
                    SPADEControlIn.println("");
                } else if (line.equalsIgnoreCase("exit")) {
                    shutdown = true;
                    SPADEControlIn.close();
                    break;
                } else if (line.equalsIgnoreCase("shutdown")) {
                    shutdown = true;
                    SPADEControlIn.println("shutdown");
                    SPADEControlIn.close();
                    break;
                } else {
                    SPADEControlIn.println(line);
                }
            }
        } catch (Exception exception) {
            exception.printStackTrace(errorStream);
        }
    }
}
