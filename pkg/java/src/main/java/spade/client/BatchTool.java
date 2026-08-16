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
import java.io.FileInputStream;
import java.io.IOException;
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

public class BatchTool {

    private static PrintStream outputStream;
    private static PrintStream SPADEQueryIn;
    private static BufferedReader SPADEQueryOut;
    private static final String nullString = "null";
    // Members for creating secure sockets
    private static KeyStore clientKeyStorePrivate;
    private static KeyStore serverKeyStorePublic;
    private static SSLSocketFactory sslSocketFactory;
    
    private static void setupKeyStores() throws Exception
    {
        String SERVER_PUBLIC_PATH = Settings.getServerPublicKeystorePath();
        String CLIENT_PRIVATE_PATH = Settings.getClientPrivateKeystorePath();

        serverKeyStorePublic = KeyStore.getInstance("JKS");
        serverKeyStorePublic.load(new FileInputStream(SERVER_PUBLIC_PATH), Settings.getPasswordPublicKeystoreAsCharArray());
        clientKeyStorePrivate = KeyStore.getInstance("JKS");
        clientKeyStorePrivate.load(new FileInputStream(CLIENT_PRIVATE_PATH), Settings.getPasswordPrivateKeystoreAsCharArray());
    }

    private static void setupClientSSLContext() throws Exception
    {
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextInt();

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(serverKeyStorePublic);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(clientKeyStorePrivate, Settings.getPasswordPrivateKeystoreAsCharArray());

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
            int port = Settings.getCommandLineQueryPort();
            SSLSocket remoteSocket = (SSLSocket) sslSocketFactory.createSocket(host, port);

            OutputStream outStream = remoteSocket.getOutputStream();
            InputStream inStream = remoteSocket.getInputStream();
            SPADEQueryOut = new BufferedReader(new InputStreamReader(inStream));
            SPADEQueryIn = new PrintStream(outStream);
        } catch (NumberFormatException | IOException exception) {
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
