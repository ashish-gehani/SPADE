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
package spade.reporter;

import spade.core.AbstractReporter;
import spade.edge.opm.Used;
import spade.edge.opm.WasGeneratedBy;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetAddress;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;

public class Network extends AbstractReporter implements Runnable {

    PrintStream outputStream = System.out;
    PrintStream errorStream = System.err;
    java.lang.Process process;
    boolean shutdown = false;
    HashMap<String, HashSet<String>> currentPidConnectionMap = new HashMap<>();
    HashMap<String, HashSet<String>> nextPidConnectionMap = new HashMap<>();

    @Override
    public boolean launch(String arguments) {

        boolean started = false;

        try {
            new Thread(this).start();
            started = true;
        } catch (Exception exception) {
            exception.printStackTrace(errorStream);
        }
        return started;
    }

    @Override
    public void run() {

        BufferedReader lsofBufferedReader = initialize();

        if (lsofBufferedReader != null) {
            while (!shutdown) {
                scanList(lsofBufferedReader);
            }
            process.destroy();
        }
    }

    BufferedReader initialize() {

        BufferedReader lsofBufferedReader = null;

        try {
            process = Runtime.getRuntime().exec("lsof -F pn -i -r 1");
            InputStream inputStream = process.getInputStream();
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            lsofBufferedReader = new BufferedReader(inputStreamReader);
        } catch (Exception exception) {
            exception.printStackTrace(errorStream);
        }

        return lsofBufferedReader;
    }

    void scanList(BufferedReader lsofBufferedReader) {

        String pid;
        boolean done = false;

        do {
            pid = scanProcess(lsofBufferedReader);

            if (pid != null) {
                scanConnections(lsofBufferedReader, pid);
            }

            if (peekString(lsofBufferedReader, "m")) {

                currentPidConnectionMap = nextPidConnectionMap;
                nextPidConnectionMap = new HashMap<>();
                try {
                    lsofBufferedReader.readLine();
                } catch (Exception exception) {
                    exception.printStackTrace(errorStream);
                }
                done = true;
            }

        } while (!done);
    }

    String scanProcess(BufferedReader lsofBufferedReader) {

        String pid = null;

        try {
            HashSet<String> connectionSet;
            String line;

            line = lsofBufferedReader.readLine();

            if (line.startsWith("p")) {
                pid = line.substring(1);

                if (currentPidConnectionMap.containsKey(pid)) {
                    connectionSet = currentPidConnectionMap.get(pid);
                } else {
                    connectionSet = new HashSet<>();
                }
                nextPidConnectionMap.put(pid, connectionSet);
            } else {
                errorStream.println("Expected line to start with 'p':\n" + "\t" + line);
            }
        } catch (Exception exception) {
            exception.printStackTrace(errorStream);
        }

        return pid;
    }

    void scanConnections(BufferedReader lsofBufferedReader, String pid) {

        try {
            String line, connection;
            HashSet<String> connectionSet;
            boolean done = false;

            connectionSet = nextPidConnectionMap.get(pid);
            do {
                if (peekString(lsofBufferedReader, "n")) {

                    line = lsofBufferedReader.readLine();
                    connection = line.substring(1);

                    if (connection.matches(".*:.*->.*:.*")) {
                        if (!connectionSet.contains(connection)) {
                            connectionSet.add(connection);
                            emitOPM(pid, connection);
                        }
                    }
                } else {
                    done = true;
                }
            } while (!done);
        } catch (Exception exception) {
            exception.printStackTrace(errorStream);
        }
    }

    void emitString(String pid, String connection) {
        outputStream.println("PID: " + pid + "\t\tConnection: " + connection);
    }

    void emitOPM(String pid, String connection) {

        try {
            String source, destination, port;
            String[] endPoint, endPoints;
            LinkedHashMap<String, String> annotations;
            boolean endPointMatched = false;
            Date currentTime;

            spade.vertex.opm.Process processVertex;
            spade.vertex.opm.Artifact networkVertex;
            WasGeneratedBy wasGeneratedByEdge;
            Used usedEdge;

            // Create process vertex.
            annotations = new LinkedHashMap<>();
            annotations.put("pid", pid);
            processVertex = new spade.vertex.opm.Process();
            processVertex.addAnnotations(annotations);

            if (!putVertex(processVertex)) {
                errorStream.println("Buffer did not accept process artifact:" + "\n\t pid" + pid);
            }

            // Create network artifact.
            annotations = new LinkedHashMap<>();
            endPoints = connection.split("->");

            endPoint = endPoints[0].split(":");
            source = endPoint[0];
            annotations.put("source host", source);
            port = endPoint[1];
            annotations.put("source port", port);

            endPoint = endPoints[1].split(":");
            destination = endPoint[0];
            annotations.put("destination host", destination);
            port = endPoint[1];
            annotations.put("destination port", port);

            networkVertex = new spade.vertex.opm.Artifact();
            networkVertex.addAnnotations(annotations);

            if (!putVertex(networkVertex)) {
                errorStream.println("Buffer did not accept connection artifact:" + "\n\t " + connection);
            }

            // Create an outgoing edge.
            if (InetAddress.getByName(destination).isSiteLocalAddress()) {
                annotations = new LinkedHashMap<>();
                currentTime = new Date();
                annotations.put("time", currentTime.toString());
                usedEdge = new Used(processVertex, networkVertex);
                usedEdge.addAnnotations(annotations);
                if (!putEdge(usedEdge)) {
                    errorStream.println("Buffer did not accept outgoing "
                            + "connection edge:\n\t pid: " + pid
                            + "\n\t connection: " + connection
                            + "\n\t time: " + currentTime.toString());
                }
                endPointMatched = true;
            }

            // Create an incoming edge.
            if (InetAddress.getByName(source).isSiteLocalAddress()) {
                annotations = new LinkedHashMap<>();
                currentTime = new Date();
                annotations.put("time", currentTime.toString());
                wasGeneratedByEdge
                        = new WasGeneratedBy(networkVertex, processVertex);
                wasGeneratedByEdge.addAnnotations(annotations);
                if (!putEdge(wasGeneratedByEdge)) {
                    errorStream.println("Buffer did not accept incoming "
                            + "connection edge:\n\t pid: " + pid
                            + "\n\t connection: " + connection
                            + "\n\t time: " + currentTime.toString());
                }

                endPointMatched = true;
            }

            if (!endPointMatched) {
                errorStream.println("Neither endpoint is local:\n\t"
                        + connection);
            }
        } catch (Exception exception) {
            exception.printStackTrace(errorStream);
        }
    }

    boolean peekString(BufferedReader bufferedReader, String nextString) {

        boolean match = false;

        try {
            int charactersRead, nextStringLength = nextString.length();
            char[] peekBuffer = new char[nextStringLength];
            String peekString;

            bufferedReader.mark(nextStringLength);
            charactersRead = bufferedReader.read(peekBuffer, 0,
                    nextStringLength);
            bufferedReader.reset();

            if (charactersRead == nextStringLength) {
                peekString = new String(peekBuffer);
                if (peekString.equals(nextString)) {
                    match = true;
                }
            }
        } catch (Exception exception) {
            exception.printStackTrace(errorStream);
        }

        return match;
    }

    @Override
    public boolean shutdown() {

        shutdown = true;
        return true;
    }
}
