/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2012 SRI International

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
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.LinkedList;
import java.util.List;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import jline.ArgumentCompletor;
import jline.Completor;
import jline.ConsoleReader;
import jline.MultiCompletor;
import jline.NullCompletor;
import jline.SimpleCompletor;
import spade.core.Settings;

public class ControlClient {

    private static PrintStream outputStream;
    private static PrintStream errorStream;
    private static PrintStream SPADEControlIn;
    private static BufferedReader SPADEControlOut;
    private static volatile boolean shutdown;
    private static final String SPADE_ROOT = "../";
    private static final String historyFile = SPADE_ROOT + "cfg/control.history";
    private static final String COMMAND_PROMPT = "-> ";
    private static final int THREAD_SLEEP_DELAY = 10;
    // Members for creating secure sockets
    private static KeyStore clientKeyStorePrivate;
    private static KeyStore serverKeyStorePublic;
    private static SSLSocketFactory sslSocketFactory;

    private static void setupKeyStores() throws Exception {
        String serverPublicPath = SPADE_ROOT + "ssl/server.public";
        String clientPrivatePath = SPADE_ROOT + "ssl/client.private";

        serverKeyStorePublic = KeyStore.getInstance("JKS");
        serverKeyStorePublic.load(new FileInputStream(serverPublicPath), "public".toCharArray());
        clientKeyStorePrivate = KeyStore.getInstance("JKS");
        clientKeyStorePrivate.load(new FileInputStream(clientPrivatePath), "private".toCharArray());
    }

    private static void setupClientSSLContext() throws Exception {
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextInt();

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(serverKeyStorePublic);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(clientKeyStorePrivate, "private".toCharArray());

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), secureRandom);
        sslSocketFactory = sslContext.getSocketFactory();
    }

    public static void main(String args[]) {
        // Set up context for secure connections
        try {
            setupKeyStores();
            setupClientSSLContext();
        } catch (Exception exception) {
            exception.printStackTrace();
        }

        outputStream = System.out;
        errorStream = System.err;

        shutdown = false;

        Runnable outputReader = new Runnable() {
            public void run() {
                try {
                    String host = "localhost";
                    int port = Integer.parseInt(Settings.getProperty("local_control_port"));
                    SSLSocket remoteSocket = (SSLSocket) sslSocketFactory.createSocket(host, port);

                    OutputStream outStream = remoteSocket.getOutputStream();
                    InputStream inStream = remoteSocket.getInputStream();
                    SPADEControlOut = new BufferedReader(new InputStreamReader(inStream));
                    SPADEControlIn = new PrintStream(outStream);

                    while (!shutdown) {
                        // This thread keeps reading from the output pipe and
                        // printing to the current output stream.
                        String outputLine = SPADEControlOut.readLine();
                        if (outputLine != null) {
                            outputStream.println(outputLine);
                        }
                        if (outputLine.equals("")) {
                            outputStream.print(COMMAND_PROMPT);
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
            Thread.sleep(2000);

            outputStream.println("");
            outputStream.println("SPADE 2.0 Control Client");
            outputStream.println("");

            // Set up command history and tab completion.

            ConsoleReader commandReader = new ConsoleReader();
            try {
                commandReader.getHistory().setHistoryFile(new File(historyFile));
            } catch (Exception ex) {
                // Ignore
            }

            List<Completor> addArguments = new LinkedList<Completor>();
            addArguments.add(new SimpleCompletor(new String[]{"add"}));
            addArguments.add(new SimpleCompletor(new String[]{"filter", "storage", "reporter"}));
            addArguments.add(new NullCompletor());

            List<Completor> removeArguments = new LinkedList<Completor>();
            removeArguments.add(new SimpleCompletor(new String[]{"remove"}));
            removeArguments.add(new SimpleCompletor(new String[]{"filter", "storage", "reporter"}));
            removeArguments.add(new NullCompletor());

            List<Completor> listArguments = new LinkedList<Completor>();
            listArguments.add(new SimpleCompletor(new String[]{"list"}));
            listArguments.add(new SimpleCompletor(new String[]{"filters", "storages", "reporters", "all"}));
            listArguments.add(new NullCompletor());

            List<Completor> configArguments = new LinkedList<Completor>();
            configArguments.add(new SimpleCompletor(new String[]{"config"}));
            configArguments.add(new SimpleCompletor(new String[]{"load", "save"}));
            configArguments.add(new NullCompletor());

            List<Completor> completors = new LinkedList<Completor>();
            completors.add(new ArgumentCompletor(addArguments));
            completors.add(new ArgumentCompletor(removeArguments));
            completors.add(new ArgumentCompletor(listArguments));
            completors.add(new ArgumentCompletor(configArguments));

            commandReader.addCompletor(new MultiCompletor(completors));

            SPADEControlIn.println("");
            while (true) {
                String line = commandReader.readLine();
                if (line.equalsIgnoreCase("exit")) {
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
        } catch (Exception exception) {
            exception.printStackTrace(errorStream);
        }
    }
}
