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

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import spade.core.Kernel;

public class QueryTool {

    private static PrintStream outputStream;
    private static PrintStream SPADEQueryIn;
    private static BufferedReader SPADEQueryOut;
    private static final String nullString = "null";

    public static void main(String args[]) {

        outputStream = System.out;

        try {
            SocketAddress sockaddr = new InetSocketAddress("localhost", Kernel.LOCAL_QUERY_PORT);
            Socket remoteSocket = new Socket();
            remoteSocket.connect(sockaddr, Kernel.CONNECTION_TIMEOUT);
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
        } catch (IOException ex) {
            System.out.println("Error retrieving results from SPADE");
            System.exit(-1);
        }
    }
}
