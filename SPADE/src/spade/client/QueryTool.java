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
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import spade.core.Settings;

public class QueryTool {

    private static PrintStream outputStream;
    private static PrintStream SPADEQueryIn;
    private static BufferedReader SPADEQueryOut;
    private static final String nullString = "null";
    private static final String SPADE_ROOT = "../";
    // Members for creating secure sockets
    private static KeyStore clientKeyStorePrivate;
    private static KeyStore serverKeyStorePublic;
    private static SSLSocketFactory sslSocketFactory;

    private static void setupKeyStores() throws Exception {
        String serverPublicPath = SPADE_ROOT + "conf/ssl/server.public";
        String clientPrivatePath = SPADE_ROOT + "conf/ssl/client.private";

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

        try {
            String host = "localhost";
            int port = Integer.parseInt(Settings.getProperty("local_query_port"));
            SSLSocket remoteSocket = (SSLSocket) sslSocketFactory.createSocket(host, port);

            OutputStream outStream = remoteSocket.getOutputStream();
            InputStream inStream = remoteSocket.getInputStream();
            SPADEQueryOut = new BufferedReader(new InputStreamReader(inStream));
            SPADEQueryIn = new PrintStream(outStream);
        } catch (Exception exception) {
            System.out.println("Error connecting to SPADE");
            System.exit(-1);
        }

        // Build the query expression from the argument tokens.
        String line = "";
        for (int i = 2; i < args.length; i++) {
            line += args[i] + " ";
        }
        line = line.trim();

        if (!line.equalsIgnoreCase("exit")) {
            // The output path is embedded in each query sent to SPADE
            // as the first token of the query. This is to allow multiple
            // query clients to work simultaneously with SPADE.
            SPADEQueryIn.println(nullString + " " + line);
        }

        String outputLine;
        try {
            while ((outputLine = SPADEQueryOut.readLine()) != null) {
                outputStream.println(outputLine);
            }
        } catch (Exception ex) {
            System.out.println("Error retrieving results from SPADE");
            System.exit(-1);
        }
    }
}
