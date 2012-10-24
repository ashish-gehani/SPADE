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

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jline.ConsoleReader;
import spade.core.Graph;
import spade.core.Kernel;
import spade.core.AuthSSLSocketFactory;

public class QueryClient {

    private static PrintStream outputStream;
    private static PrintStream SPADEQueryIn;
    private static ObjectInputStream SPADEQueryOut;
    private static final String historyFile = "../cfg/query.history";
    private static final String COMMAND_PROMPT = "-> ";
    private static HashMap<String, Graph> graphObjects;
    private static String QUERY_STORAGE = "Neo4j";

    public static void main(String args[]) {

        outputStream = System.out;
        graphObjects = new HashMap<String, Graph>();

        try {
            InetSocketAddress sockaddr = new InetSocketAddress("localhost", Kernel.LOCAL_QUERY_PORT);
            Socket remoteSocket = new Socket();
            remoteSocket.connect(sockaddr, Kernel.CONNECTION_TIMEOUT);
            remoteSocket = AuthSSLSocketFactory.getSocket(remoteSocket, sockaddr, "DAWOOD_READ_FROM_CONFIG");
            		
            OutputStream outStream = remoteSocket.getOutputStream();
            InputStream inStream = remoteSocket.getInputStream();
            SPADEQueryOut = new ObjectInputStream(inStream);
            SPADEQueryIn = new PrintStream(outStream);
        } catch (Exception exception) {
            outputStream.println("Error connecting to SPADE");
            System.exit(-1);
        }

        try {
            outputStream.println("");
            outputStream.println("SPADE 2.0 Query Client");
            outputStream.println("");

            // Set up command history and tab completion.

            ConsoleReader commandReader = new ConsoleReader();
            commandReader.getHistory().setHistoryFile(new File(historyFile));

            SPADEQueryIn.println("");
            String commands = (String) SPADEQueryOut.readObject();
            System.out.println(commands + "\n");

            while (true) {
                try {
                    outputStream.print(COMMAND_PROMPT);
                    String line = commandReader.readLine();
                    if (line.equals("exit")) {
                        SPADEQueryIn.println(line);
                        break;
                    } else if (line.equals("list")) {
                        System.out.println("-------------------------------------------------");
                        System.out.println("Graph\t\t | Expression");
                        System.out.println("-------------------------------------------------");
                        for (Map.Entry<String, Graph> currentEntry : graphObjects.entrySet()) {
                            String key = currentEntry.getKey();
                            Graph graph = currentEntry.getValue();
                            System.out.println(key + "\t\t | " + graph.graphInfo.get("expression"));
                        }
                        System.out.println("-------------------------------------------------");
                        System.out.println();
                    } else if (line.startsWith("export")) {
                        String[] tokens = line.split("\\s+");
                        graphObjects.get(tokens[1]).exportDOT(tokens[2]);
                    } else {
                        parseQuery(line);
                    }
                } catch (Exception exception) {
                    exception.printStackTrace();
                }
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private static void parseQuery(String input) {
        // Accepts input of the following form and generates the corresponding
        // query expression to pass to the Query class:
        //      function(arguments)
        // Examples:
        //      getVertices(expression)
        //      getPaths(src_id, dst_id)
        //      getLineage(id, depth, direction)
        //      getLineage(id, depth, direction, expression)
        Pattern vertexPattern = Pattern.compile("([a-zA-Z0-9]+)\\s+=\\s+([a-zA-Z0-9]+\\.)?getVertices\\((.+)\\)[;]?");
        Pattern pathPattern = Pattern.compile("([a-zA-Z0-9]+)\\s+=\\s+([a-zA-Z0-9]+\\.)?getPaths\\((\\d+),\\s*(\\d+),\\s*(\\d+)\\)[;]?");
        Pattern lineagePattern = Pattern.compile("([a-zA-Z0-9]+)\\s+=\\s+([a-zA-Z0-9]+\\.)?getLineage\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*([a-zA-Z]+)\\s*(,\\s*.+)?\\s*\\)[;]?");

        Matcher vertexMatcher = vertexPattern.matcher(input);
        Matcher pathMatcher = pathPattern.matcher(input);
        Matcher lineageMatcher = lineagePattern.matcher(input);

        String queryTarget = null, queryString = null, result = null;
        if (vertexMatcher.matches()) {
            result = vertexMatcher.group(1);
            queryTarget = vertexMatcher.group(2);
            String expression = vertexMatcher.group(3);
            queryString = "query " + QUERY_STORAGE + " vertices " + expression;
        } else if (pathMatcher.matches()) {
            result = pathMatcher.group(1);
            queryTarget = pathMatcher.group(2);
            String srcVertex = pathMatcher.group(3);
            String dstVertex = pathMatcher.group(4);
            String maxLength = pathMatcher.group(4);
            queryString = "query " + QUERY_STORAGE + " paths " + srcVertex + " " + dstVertex + " " + maxLength;
        } else if (lineageMatcher.matches()) {
            result = lineageMatcher.group(1);
            queryTarget = lineageMatcher.group(2);
            String vertexId = lineageMatcher.group(3);
            String depth = lineageMatcher.group(4);
            String direction = lineageMatcher.group(5);
            String terminatingExpression = (lineageMatcher.group(6) == null) ? "null" : lineageMatcher.group(6).substring(1).trim();
            queryString = "query " + QUERY_STORAGE + " lineage " + vertexId + " " + depth + " " + direction + " " + terminatingExpression;
        }

        try {
            if ((queryTarget == null) && (queryString != null)) {
                long begintime = 0, endtime = 0;
                begintime = System.currentTimeMillis();
                SPADEQueryIn.println(queryString);
                String resultString = (String) SPADEQueryOut.readObject();
                if (resultString.equals("graph")) {
                    Graph resultGraph = (Graph) SPADEQueryOut.readObject();
                    String queryExpression = input.split("\\s*=\\s*")[1];
                    resultGraph.graphInfo.put("expression", queryExpression);
                    graphObjects.put(result, resultGraph);
                } else {
                    outputStream.println(resultString + "\n");
                }
                endtime = System.currentTimeMillis();
                long elapsedtime = endtime - begintime;

                System.out.println("Time taken for query: " + elapsedtime);
            } else if (queryTarget != null) {
                queryTarget = queryTarget.substring(0, queryTarget.length() - 1);
                if (!graphObjects.containsKey(queryTarget)) {
                    System.out.println("Error: graph " + queryTarget + " does not exist");
                    return;
                }
                Graph resultGraph = null;
                if (vertexMatcher.matches()) {
                    String queryExpression = vertexMatcher.group(3);
                    resultGraph = graphObjects.get(queryTarget).getVertices(queryExpression);
                } else if (pathMatcher.matches()) {
                    String srcVertexId = pathMatcher.group(3);
                    String dstVertexId = pathMatcher.group(4);
                    int maxLength = Integer.parseInt(pathMatcher.group(5));
                    resultGraph = graphObjects.get(queryTarget).getPaths(srcVertexId, dstVertexId, maxLength);
                } else if (lineageMatcher.matches()) {
                    String vertexId = lineageMatcher.group(3);
                    int depth = Integer.parseInt(lineageMatcher.group(4));
                    String direction = lineageMatcher.group(5);
                    String terminatingExpression = (lineageMatcher.group(6) == null) ? "null" : lineageMatcher.group(6).substring(1).trim();
                    resultGraph = graphObjects.get(queryTarget).getLineage(vertexId, depth, direction, terminatingExpression);
                }
                if (resultGraph != null) {
                    graphObjects.put(result, resultGraph);
                    String queryExpression = input.split("\\s*=\\s*")[1];
                    resultGraph.graphInfo.put("expression", queryExpression);
                }
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }
}
