/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2015 SRI International

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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

import spade.core.Settings;

/**
 *
 * @author Dawood Tariq
 */
public class Android {

    private static PrintStream outputStream;
    private static PrintStream errorStream;
    private static PrintStream SPADEControlIn;
    private static BufferedReader SPADEControlOut;
    private static final BufferedReader commandReader = new BufferedReader(new InputStreamReader(System.in));
    private static volatile boolean shutdown = false;
    private static final String COMMAND_PROMPT = "-> ";
    private static final int THREAD_SLEEP_DELAY = 10;

    public static void main(String args[]) {

        outputStream = System.out;
        errorStream = System.err;

        Runnable outputReader = new Runnable() {
            @Override
            public void run() {
                try {
                    while (!shutdown) {
                        if (SPADEControlOut.ready()) {
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
                    exception.printStackTrace(errorStream);
                    System.exit(-1);
                }
            }
        };

        try {
            SocketAddress sockaddr = new InetSocketAddress("localhost", Settings.getLocalControlPort());
            Socket remoteSocket = new Socket();
            remoteSocket.connect(sockaddr, Settings.getConnectionTimeoutMillis());
            OutputStream outStream = remoteSocket.getOutputStream();
            InputStream inStream = remoteSocket.getInputStream();
            SPADEControlOut = new BufferedReader(new InputStreamReader(inStream));
            SPADEControlIn = new PrintStream(outStream);
            new Thread(outputReader).start();

            if (args.length == 1 && args[0].equals("shutdown")) {
                shutdown = true;
                SPADEControlIn.println("shutdown");
                SPADEControlIn.close();
                return;
            }

            outputStream.println("");
            outputStream.println("SPADE 2.0 Control Client for Android");
            outputStream.println("");
            while (true) {
                String line = commandReader.readLine();
                if (line.split("\\s")[0].equalsIgnoreCase("query")) {
                    SPADEControlIn.println("");
                } else if (line.equalsIgnoreCase("exit")) {
                    shutdown = true;
                    SPADEControlIn.println("exit");
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
        } catch (NumberFormatException | IOException exception) {
            exception.printStackTrace(errorStream);
        }
    }
}
