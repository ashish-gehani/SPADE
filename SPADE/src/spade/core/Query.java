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

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author dawood
 */
public class Query {

    private static final int WAIT_FOR_FLUSH = 10;

    /**
     * This method is used to call query methods on the desired storage. The
     * transactions are also flushed to ensure that the data in the storages is
     * consistent and updated with all the data received by SPADE up to this
     * point.
     *
     * @param line The query string.
     * @param resolveRemote A boolean used to indicate whether or not remote
     * edges need to be resolved.
     * @return The result represented by a Graph object.
     */
    public static Graph executeQuery(String line, boolean resolveRemote) {
        Graph resultGraph = null;
        Kernel.flushTransactions = true;

        long begintime = 0, endtime = 0;

        while (Kernel.flushTransactions) {
            try {
                // wait for other thread to flush transactions
                Thread.sleep(WAIT_FOR_FLUSH);
            } catch (Exception exception) {
                Logger.getLogger(Kernel.class.getName()).log(Level.SEVERE, null, exception);
            }
        }
        if ((line == null) || (Kernel.storages.isEmpty())) {
            return null;
        }
        String[] tokens = line.split("\\s+");
        for (AbstractStorage storage : Kernel.storages) {
            if (storage.getClass().getName().equals("spade.storage." + tokens[1])) {
                ////////////////////////////////////////////////////////////////
                System.out.println("Executing query line: " + line);
                ////////////////////////////////////////////////////////////////
                // Determine the type of query and call the corresponding method
                begintime = System.currentTimeMillis();
                if (tokens[2].equalsIgnoreCase("vertices")) {
                    String queryExpression = "";
                    for (int i = 3; i < tokens.length; i++) {
                        queryExpression = queryExpression + tokens[i] + " ";
                    }
                    try {
                        resultGraph = storage.getVertices(queryExpression.trim());
                    } catch (Exception badQuery) {
                        Logger.getLogger(Kernel.class.getName()).log(Level.SEVERE, null, badQuery);
                    }
                } else if (tokens[2].equalsIgnoreCase("remotevertices")) {
                    String host = tokens[3];
                    String queryExpression = "";
                    for (int i = 4; i < tokens.length; i++) {
                        queryExpression = queryExpression + tokens[i] + " ";
                    }
                    try {
                        // Connect to the specified host and query for vertices.
                        SocketAddress sockaddr = new InetSocketAddress(host, Kernel.REMOTE_QUERY_PORT);
                        Socket remoteSocket = new Socket();
                        remoteSocket.connect(sockaddr, Kernel.CONNECTION_TIMEOUT);
                        OutputStream outStream = remoteSocket.getOutputStream();
                        InputStream inStream = remoteSocket.getInputStream();
                        //ObjectOutputStream graphOutputStream = new ObjectOutputStream(outStream);
                        ObjectInputStream graphInputStream = new ObjectInputStream(inStream);
                        PrintWriter remoteSocketOut = new PrintWriter(outStream, true);

                        String srcExpression = "query Neo4j vertices " + queryExpression;
                        remoteSocketOut.println(srcExpression);
                        resultGraph = (Graph) graphInputStream.readObject();

                        remoteSocketOut.println("close");
                        graphInputStream.close();
                        //graphOutputStream.close();
                        remoteSocketOut.close();
                        inStream.close();
                        outStream.close();
                        remoteSocket.close();
                    } catch (Exception badQuery) {
                        Logger.getLogger(Kernel.class.getName()).log(Level.SEVERE, null, badQuery);
                    }
                } else if (tokens[2].equalsIgnoreCase("lineage")) {
                    String vertexId = tokens[3];
                    int depth = Integer.parseInt(tokens[4]);
                    String direction = tokens[5];
                    String terminatingExpression = "";
                    for (int i = 6; i < tokens.length - 1; i++) {
                        terminatingExpression = terminatingExpression + tokens[i] + " ";
                    }
                    try {
                        resultGraph = storage.getLineage(vertexId, depth, direction, terminatingExpression.trim());
                    } catch (Exception badQuery) {
                        Logger.getLogger(Kernel.class.getName()).log(Level.SEVERE, null, badQuery);
                    }
                    if (resolveRemote) {
                        // Perform the remote queries here. A temporary remoteGraph is
                        // created to store the results of the remote queries and then
                        // added to the final resultGraph
                        Graph remoteGraph = new Graph();
                        // Get the map of network vertexes of our current graph
                        Map<AbstractVertex, Integer> currentNetworkMap = resultGraph.networkMap();
                        // Perform remote queries until the network map is exhausted
                        while (!currentNetworkMap.isEmpty()) {
                            // Perform remote query on current network vertex and union
                            // the result with the remoteGraph. This also adds the network
                            // vertexes to the remoteGraph as well, so that deeper level
                            // network queries are resolved iteratively
                            for (Map.Entry currentEntry : currentNetworkMap.entrySet()) {
                                AbstractVertex networkVertex = (AbstractVertex) currentEntry.getKey();
                                int currentDepth = (Integer) currentEntry.getValue();
                                // Execute remote query
                                Graph tempRemoteGraph = queryNetworkVertex(networkVertex, depth - currentDepth, direction, terminatingExpression.trim());
                                // Update the depth values of all network artifacts in the
                                // remote network map to reflect current level of iteration
                                for (Map.Entry currentNetworkEntry : tempRemoteGraph.networkMap().entrySet()) {
                                    AbstractVertex tempNetworkVertex = (AbstractVertex) currentNetworkEntry.getKey();
                                    int updatedDepth = currentDepth + (Integer) currentNetworkEntry.getValue();
                                    tempRemoteGraph.putNetworkVertex(tempNetworkVertex, updatedDepth);
                                }
                                // Add the lineage of the current network node to the
                                // overall result
                                remoteGraph = Graph.union(remoteGraph, tempRemoteGraph);
                            }
                            currentNetworkMap.clear();
                            // Set the networkMap to network vertexes of the newly
                            // create remoteGraph
                            currentNetworkMap = remoteGraph.networkMap();
                        }
                        resultGraph = Graph.union(resultGraph, remoteGraph);
                    }
                } else if (tokens[2].equalsIgnoreCase("paths")) {
                    String srcVertexId = tokens[3];
                    String dstVertexId = tokens[4];
                    int maxLength = Integer.parseInt(tokens[5]);
                    try {
                        resultGraph = storage.getPaths(srcVertexId, dstVertexId, maxLength);
                    } catch (Exception badQuery) {
                        Logger.getLogger(Kernel.class.getName()).log(Level.SEVERE, null, badQuery);
                    }
                } else if (tokens[2].equalsIgnoreCase("serialize")) {
                    // Temporary method: Used to determine false positives in the sketches.
                    try {
                        ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream("sketches.out"));
                        Map<String, AbstractSketch> tempSketches = new HashMap<String, AbstractSketch>();
                        tempSketches.putAll(Kernel.remoteSketches);
                        tempSketches.put("localhost", Kernel.sketches.iterator().next());
                        out.writeObject(tempSketches);
                        out.flush();
                        out.close();
                    } catch (Exception ex) {
                        Logger.getLogger(Kernel.class.getName()).log(Level.SEVERE, null, ex);
                    }
                } else if (tokens[2].equalsIgnoreCase("sketchpaths")) {
                    resultGraph = getPathInSketch(tokens[3] + " " + tokens[4]);
                } else if (tokens[2].equalsIgnoreCase("rebuildsketches")) {
                    notifyRebuildSketches(Integer.parseInt(tokens[3]), Integer.parseInt(tokens[3]));
                    return null;
                } else if (tokens[2].equalsIgnoreCase("remotepaths")) {
                    String source = tokens[3];
                    String srcHost = source.split(":")[0];
                    String srcVertexId = source.split(":")[1];
                    String destination = tokens[4];
                    String dstHost = destination.split(":")[0];
                    String dstVertexId = destination.split(":")[1];
                    int maxLength = Integer.parseInt(tokens[5]);
                    try {
                        if (srcHost.equalsIgnoreCase("localhost") && dstHost.equalsIgnoreCase("localhost")) {
                            resultGraph = storage.getPaths(srcVertexId, dstVertexId, maxLength);
                        } else {
                            Graph srcGraph = null, dstGraph = null;

                            // Connect to source host and get upward lineage
                            SocketAddress sockaddr = new InetSocketAddress(srcHost, Kernel.REMOTE_QUERY_PORT);
                            Socket remoteSocket = new Socket();
                            remoteSocket.connect(sockaddr, Kernel.CONNECTION_TIMEOUT);
                            OutputStream outStream = remoteSocket.getOutputStream();
                            InputStream inStream = remoteSocket.getInputStream();
                            //ObjectOutputStream graphOutputStream = new ObjectOutputStream(outStream);
                            ObjectInputStream graphInputStream = new ObjectInputStream(inStream);
                            PrintWriter remoteSocketOut = new PrintWriter(outStream, true);

                            String srcExpression = "query Neo4j lineage " + srcVertexId + " " + maxLength + " a null tmp.dot";
                            remoteSocketOut.println(srcExpression);
                            srcGraph = (Graph) graphInputStream.readObject();

                            remoteSocketOut.println("close");
                            graphInputStream.close();
                            //graphOutputStream.close();
                            remoteSocketOut.close();
                            inStream.close();
                            outStream.close();
                            remoteSocket.close();

                            // Connect to destination host and get downward lineage
                            sockaddr = new InetSocketAddress(dstHost, Kernel.REMOTE_QUERY_PORT);
                            remoteSocket = new Socket();
                            remoteSocket.connect(sockaddr, Kernel.CONNECTION_TIMEOUT);
                            outStream = remoteSocket.getOutputStream();
                            inStream = remoteSocket.getInputStream();
                            //graphOutputStream = new ObjectOutputStream(outStream);
                            graphInputStream = new ObjectInputStream(inStream);
                            remoteSocketOut = new PrintWriter(outStream, true);

                            String dstExpression = "query Neo4j lineage " + dstVertexId + " " + maxLength + " d null tmp.dot";
                            remoteSocketOut.println(dstExpression);
                            dstGraph = (Graph) graphInputStream.readObject();

                            remoteSocketOut.println("close");
                            graphInputStream.close();
                            //graphOutputStream.close();
                            remoteSocketOut.close();
                            inStream.close();
                            outStream.close();
                            remoteSocket.close();

                            // The result path is the intersection of the two lineages
                            resultGraph = Graph.intersection(srcGraph, dstGraph);
                        }
                    } catch (Exception badQuery) {
                        Logger.getLogger(Kernel.class.getName()).log(Level.SEVERE, null, badQuery);
                    }
                } else {
                    return null;
                }
            }
        }
        transformNetworkBoundaries(resultGraph);
        endtime = System.currentTimeMillis();
        long elapsedtime = endtime - begintime;
        ////////////////////////////////////////////////////////////////
        System.out.println("Time taken for (" + line + "): " + elapsedtime);
        ////////////////////////////////////////////////////////////////
        // If the graph is incomplete, perform the necessary remote queries
        return resultGraph;
    }

    /**
     * Method to retrieve a terminal path fragment from the local SPADE instance
     * given a remote sketch as input.
     *
     * @param inputSketch The input sketch
     * @param end A string indicating whether this is the source terminal or
     * destination terminal fragment.
     * @return A path fragment represented by a Graph object.
     */
    public static Graph getEndPathFragment(AbstractSketch inputSketch, String end) {
        // Given a remote sketch, this method returns an ending path fragment from the
        // local graph database.

        Graph result = new Graph();

        String line = (String) inputSketch.objects.get("queryLine");
        String source = line.split("\\s")[0];
        String srcVertexId = source.split(":")[1];
        String destination = line.split("\\s")[1];
        String dstVertexId = destination.split(":")[1];

        ////////////////////////////////////////////////////////////////////
        System.out.println("endPathFragment - generating end path fragment");
        ////////////////////////////////////////////////////////////////////
        // First, store the local network vertices in a set because they will be
        // used later.
        //Graph myNetworkVertices = query("query Neo4j vertices type:Network", false);
        Graph myNetworkVertices = executeQuery("query Neo4j vertices network:true", false);
        MatrixFilter receivedMatrixFilter = inputSketch.matrixFilter;
        MatrixFilter myMatrixFilter = Kernel.sketches.iterator().next().matrixFilter;

        if (end.equals("src")) {
            // If a 'src' end is requested, then the following is done to generate the
            // path fragment:
            //
            // 1) For each 'source vertex' in the received sketch, use the matrix filter
            //    to get the bloom filter containing its ancestors.
            // 2) For each local network vertex, check if it is contained in this bloom
            //    filter. If yes, add it to the set of matching vertices.
            // 3) Finally, get all paths between the source vertex id and each vertex in
            //    the set of matching vertices.
            // 4) Each of these paths is union'd to generate the final resulting graph
            //    fragment.

            // Current host's network vertices that match downward
            Set<AbstractVertex> matchingVerticesUp = new HashSet<AbstractVertex>();
            ////////////////////////////////////////////////////////////////////
            System.out.println("endPathFragment - checking " + ((Set<AbstractVertex>) inputSketch.objects.get("srcVertices")).size() + " srcVertices");
            ////////////////////////////////////////////////////////////////////
            for (AbstractVertex sourceVertex : (Set<AbstractVertex>) inputSketch.objects.get("srcVertices")) {
                BloomFilter currentBloomFilter = receivedMatrixFilter.get(sourceVertex);
                for (AbstractVertex vertexToCheck : myNetworkVertices.vertexSet()) {
                    if (currentBloomFilter.contains(vertexToCheck)) {
                        matchingVerticesUp.add(vertexToCheck);
                    }
                }
            }

            // Get all paths between the matching network vertices and the required vertex id
            Object vertices[] = matchingVerticesUp.toArray();
            ////////////////////////////////////////////////////////////////////
            System.out.println("endPathFragment - generating up paths between " + vertices.length + " matched vertices");
            ////////////////////////////////////////////////////////////////////
            for (int i = 0; i < vertices.length; i++) {
                String vertexId = ((AbstractVertex) vertices[i]).getAnnotation("storageId");
                Graph path = executeQuery("query Neo4j paths " + srcVertexId + " " + vertexId + " 20", false);
                if (!path.edgeSet().isEmpty()) {
                    result = Graph.union(result, path);
                    ////////////////////////////////////////////////////////////////////
                    System.out.println("endPathFragment - added path to result fragment");
                    ////////////////////////////////////////////////////////////////////
                }
            }
        } else if (end.equals("dst")) {
            // If a 'dst' end is requested, then the following is done to generate the
            // path fragment:
            //
            // 1) For each local network vertex, use the local matrix filter to get its
            //    bloom filter.
            // 2) For each 'destination vertex' in the received sketch, check if it is
            //    contained in this bloom filter. If yes, add it to the set of matching
            //    vertices.
            // 3) Finally, get all paths between the destination vertex id and each vertex
            //    in the set of matching vertices.
            // 4) Each of these paths is union'd to generate the final resulting graph
            //    fragment.

            // Current host's network vertices that match upward
            Set<AbstractVertex> matchingVerticesDown = new HashSet<AbstractVertex>();
            ////////////////////////////////////////////////////////////////////
            System.out.println("endPathFragment - checking " + ((Set<AbstractVertex>) inputSketch.objects.get("dstVertices")).size() + " dstVertices");
            ////////////////////////////////////////////////////////////////////
            for (AbstractVertex vertexToCheck : myNetworkVertices.vertexSet()) {
                BloomFilter currentBloomFilter = myMatrixFilter.get(vertexToCheck);
                for (AbstractVertex destinationVertex : (Set<AbstractVertex>) inputSketch.objects.get("dstVertices")) {
                    if (currentBloomFilter.contains(destinationVertex)) {
                        matchingVerticesDown.add(vertexToCheck);
                    }
                }
            }

            // Get all paths between the matching network vertices and the required vertex id
            Object vertices[] = matchingVerticesDown.toArray();
            ////////////////////////////////////////////////////////////////////
            System.out.println("endPathFragment - generating down paths between " + vertices.length + " matched vertices");
            ////////////////////////////////////////////////////////////////////
            for (int i = 0; i < vertices.length; i++) {
                String vertexId = ((AbstractVertex) vertices[i]).getAnnotation("storageId");
                Graph path = executeQuery("query Neo4j paths " + vertexId + " " + dstVertexId + " 20", false);
                if (!path.edgeSet().isEmpty()) {
                    result = Graph.union(result, path);
                    ////////////////////////////////////////////////////////////////////
                    System.out.println("endPathFragment - added path to result fragment");
                    ////////////////////////////////////////////////////////////////////
                }
            }
        }

        ////////////////////////////////////////////////////////////////////
        System.out.println("endPathFragment - returning " + end + " end fragment");
        ////////////////////////////////////////////////////////////////////

        return result;
    }

    /**
     * Method to retrieve a non-terminal path fragment given a remote sketch as
     * input.
     *
     * @param inputSketch The input sketch.
     * @return A path fragment represented by a Graph object.
     */
    public static Graph getPathFragment(AbstractSketch inputSketch) {
        Graph result = new Graph();
        // Given a remote sketch, this method is used to generate the path fragment.
        // The following is done:
        //
        // 1) For each 'source vertex' in the received sketch, get its bloom filter.
        // 2) For each local network vertex, check if it is contained in this bloom
        //    filter. If yes, add it to the set of upward-matching vertices.
        // 3) For each local network vertex in the local sketch, get its bloom filter.
        // 4) For each 'destination vertex' in the received sketch, check if is
        //    contained in this bloom filter. If yes, add it to the set of
        //    downward-matching vertices.
        // 5) We are only interested in the vertices that lie in both the
        //    upward-matching and downward-matching since that is indicative of them
        //    being in the desired path. Therefore, get the intersection of these
        //    two sets as 'matching vertices'.
        // 6) Get paths between all pairs of vertices in the set 'matching vertices'
        //    and union all the paths to get the final path fragment for this host.

        ////////////////////////////////////////////////////////////////////
        System.out.println("pathFragment.a - generating path fragment");
        ////////////////////////////////////////////////////////////////////
        //Graph myNetworkVertices = query("query Neo4j vertices type:Network", false);
        Graph myNetworkVertices = executeQuery("query Neo4j vertices network:true", false);
        Set<AbstractVertex> matchingVerticesDown = new HashSet<AbstractVertex>();
        Set<AbstractVertex> matchingVerticesUp = new HashSet<AbstractVertex>();
        MatrixFilter receivedMatrixFilter = inputSketch.matrixFilter;
        MatrixFilter myMatrixFilter = Kernel.sketches.iterator().next().matrixFilter;

        // Current host's network vertices that match downward
        ////////////////////////////////////////////////////////////////////
        System.out.println("pathFragment.b - checking " + ((Set<AbstractVertex>) inputSketch.objects.get("srcVertices")).size() + " srcVertices");
        ////////////////////////////////////////////////////////////////////
        for (AbstractVertex sourceVertex : (Set<AbstractVertex>) inputSketch.objects.get("srcVertices")) {
            BloomFilter currentBloomFilter = receivedMatrixFilter.get(sourceVertex);
            for (AbstractVertex vertexToCheck : myNetworkVertices.vertexSet()) {
                if (currentBloomFilter.contains(vertexToCheck)) {
                    matchingVerticesUp.add(vertexToCheck);
                }
            }
        }
        ////////////////////////////////////////////////////////////////////
        System.out.println("pathFragment.c - added downward vertices");
        ////////////////////////////////////////////////////////////////////

        // Current host's network vertices that match upward
        ////////////////////////////////////////////////////////////////////
        System.out.println("pathFragment.d - checking " + ((Set<AbstractVertex>) inputSketch.objects.get("dstVertices")).size() + " dstVertices");
        ////////////////////////////////////////////////////////////////////
        for (AbstractVertex vertexToCheck : myNetworkVertices.vertexSet()) {
            BloomFilter currentBloomFilter = myMatrixFilter.get(vertexToCheck);
            for (AbstractVertex destinationVertex : (Set<AbstractVertex>) inputSketch.objects.get("dstVertices")) {
                if (currentBloomFilter.contains(destinationVertex)) {
                    matchingVerticesDown.add(vertexToCheck);
                }
            }
        }
        ////////////////////////////////////////////////////////////////////
        System.out.println("pathFragment.e - added upward vertices");
        ////////////////////////////////////////////////////////////////////

        // Network vertices that we're interested in
        ////////////////////////////////////////////////////////////////////
        System.out.println("pathFragment.f - " + matchingVerticesDown.size() + " in down vertices");
        System.out.println("pathFragment.g - " + matchingVerticesUp.size() + " in up vertices");
        ////////////////////////////////////////////////////////////////////
        Set<AbstractVertex> matchingVertices = new HashSet<AbstractVertex>();
        matchingVertices.addAll(matchingVerticesDown);
        matchingVertices.retainAll(matchingVerticesUp);
        ////////////////////////////////////////////////////////////////////
        System.out.println("pathFragment.h - " + matchingVertices.size() + " total matching vertices");
        ////////////////////////////////////////////////////////////////////

        // Get all paths between the matching network vertices
        Object vertices[] = matchingVertices.toArray();
        ////////////////////////////////////////////////////////////////////
        System.out.println("pathFragment.i - generating paths between " + vertices.length + " matched vertices");
        ////////////////////////////////////////////////////////////////////
        for (int i = 0; i < vertices.length; i++) {
            for (int j = 0; j < vertices.length; j++) {
                if (j == i) {
                    continue;
                }
                String srcId = ((AbstractVertex) vertices[i]).getAnnotation("storageId");
                String dstId = ((AbstractVertex) vertices[j]).getAnnotation("storageId");
                Graph path = executeQuery("query Neo4j paths " + srcId + " " + dstId + " 20", false);
                if (!path.edgeSet().isEmpty()) {
                    result = Graph.union(result, path);
                    ////////////////////////////////////////////////////////////////////
                    System.out.println("pathFragment.j - added path to result fragment");
                    ////////////////////////////////////////////////////////////////////
                }
            }
        }
        ////////////////////////////////////////////////////////////////////
        System.out.println("pathFragment.k - returning fragment");
        ////////////////////////////////////////////////////////////////////

        return result;
    }

    /**
     * Method used to rebuild the local sketch.
     *
     */
    public static void rebuildLocalSketch() {
        // This method is used to rebuild the local sketch. All the 'used' edges
        // are added to the sketch before the 'wasgeneratedby' edges. This is
        // because of the pull-based architecture of the sketch which requests
        // remote sketches on a 'used' edge. The updated bloom filters are then
        // reflected in subsequent 'wasgeneratedby' edges. The delay (Thread.sleep)
        // is used to ensure that the remote sketches have been updated on the
        // incoming 'used' edges.

        ////////////////////////////////////////////////////////////////
        System.out.println("rebuildLocalSketch - rebuilding local sketch");
        ////////////////////////////////////////////////////////////////
        executeQuery(null, false); // To flush transactions
        try {
            AbstractSketch mySketch = Kernel.sketches.iterator().next();
            //Set<AbstractEdge> usedEdges = storages.iterator().next().getEdges(null, "type:Network", "type:Used").edgeSet();
            Set<AbstractEdge> usedEdges = Kernel.storages.iterator().next().getEdges(null, "network:true", "type:Used").edgeSet();
            for (AbstractEdge currentEdge : usedEdges) {
                mySketch.putEdge(currentEdge);
                Thread.sleep(200);
            }
            Thread.sleep(2000);
            //Set<AbstractEdge> wgbEdges = storages.iterator().next().getEdges("type:Network", null, "type:WasGeneratedBy").edgeSet();
            Set<AbstractEdge> wgbEdges = Kernel.storages.iterator().next().getEdges("network:true", null, "type:WasGeneratedBy").edgeSet();
            for (AbstractEdge currentEdge : wgbEdges) {
                mySketch.putEdge(currentEdge);
                Thread.sleep(200);
            }
        } catch (Exception exception) {
            Logger.getLogger(Kernel.class.getName()).log(Level.SEVERE, null, exception);
        }
    }

    /**
     * Method used to propagate sketches across the network.
     *
     * @param currentLevel The current level of propagation.
     * @param maxLevel The maximum level at which to propagate.
     */
    public static void propagateSketches(int currentLevel, int maxLevel) {
        // First, rebuild the local sketch.
        rebuildLocalSketch();
        // If the maximum propagation level is reached, terminate propagation.
        if (currentLevel == maxLevel) {
            ////////////////////////////////////////////////////////////////
            System.out.println("propagateSketches - reached max level, terminating propagation");
            ////////////////////////////////////////////////////////////////
            return;
        }
        ////////////////////////////////////////////////////////////////
        System.out.println("propagateSketches - propagating sketches");
        ////////////////////////////////////////////////////////////////
        currentLevel++;
        executeQuery(null, false); // To flush transactions
        //Set<AbstractVertex> upVertices = storages.iterator().next().getEdges("type:Network", null, "type:WasGeneratedBy").vertexSet();
        Set<AbstractVertex> upVertices = Kernel.storages.iterator().next().getEdges("network:true", null, "type:WasGeneratedBy").vertexSet();
        // Get all outgoing network vertices.
        for (AbstractVertex currentVertex : upVertices) {
            //if (!currentVertex.type().equalsIgnoreCase("Network")) {
            if (!currentVertex.getAnnotation("network").equalsIgnoreCase("true")) {
                continue;
            }
            try {
                // Get the remote host of each outgoing network vertex and trigger
                // the propagateSketch command on that SPADE instance.
                String remoteHost = currentVertex.getAnnotation("destination host");
                PropagateSketch currentElement = new PropagateSketch(currentLevel, maxLevel, remoteHost);
                Thread propagateThread = new Thread(currentElement);
                propagateThread.start();
            } catch (Exception exception) {
                Logger.getLogger(Kernel.class.getName()).log(Level.SEVERE, null, exception);
            }
        }
        ////////////////////////////////////////////////////////////////
        System.out.println("propagateSketches - finished propagation");
        ////////////////////////////////////////////////////////////////
    }

    /**
     * Method used to indicate to remote hosts that sketches need to be rebuilt.
     *
     * @param currentLevel Current level of notification.
     * @param maxLevel Maximum level to which notifications are sent.
     */
    public static void notifyRebuildSketches(int currentLevel, int maxLevel) {
        // If the last level is reached, stop notifying and begin propagation.
        if (currentLevel == 0) {
            ////////////////////////////////////////////////////////////////
            System.out.println("notifyRebuildSketch - reached level zero, propagating");
            ////////////////////////////////////////////////////////////////
            propagateSketches(0, maxLevel);
            return;
        }
        ////////////////////////////////////////////////////////////////
        System.out.println("notifyRebuildSketch - sending rebuild notifications");
        ////////////////////////////////////////////////////////////////
        executeQuery(null, false); // To flush transactions
        //Set<AbstractVertex> upVertices = storages.iterator().next().getEdges(null, "type:Network", "type:Used").vertexSet();
        Set<AbstractVertex> upVertices = Kernel.storages.iterator().next().getEdges(null, "network:true", "type:Used").vertexSet();
        // If there are no incoming network vertices to send notifications to,
        // stop notifying and begin propagation.
        if (upVertices.isEmpty()) {
            ////////////////////////////////////////////////////////////////
            System.out.println("notifyRebuildSketch - no more notification to send, beginning propagation");
            ////////////////////////////////////////////////////////////////
            propagateSketches(currentLevel, maxLevel);
            return;
        }
        currentLevel--;
        for (AbstractVertex currentVertex : upVertices) {
            //if (!currentVertex.type().equalsIgnoreCase("Network")) {
            if (!currentVertex.getAnnotation("network").equalsIgnoreCase("true")) {
                continue;
            }
            try {
                // For each incoming network vertex, get the remote host and send
                // it the notify command.
                String remoteHost = currentVertex.getAnnotation("destination host");
                RebuildSketch currentElement = new RebuildSketch(currentLevel, maxLevel, remoteHost);
                Thread rebuildThread = new Thread(currentElement);
                rebuildThread.start();
            } catch (Exception exception) {
                Logger.getLogger(Kernel.class.getName()).log(Level.SEVERE, null, exception);
            }
        }
        ////////////////////////////////////////////////////////////////
        System.out.println("notifyRebuildSketch - finished sending rebuild notifications");
        ////////////////////////////////////////////////////////////////
    }

    /**
     * This method is called when a path query is executed using sketches.
     *
     * @param line A string containing the source and destination host and
     * vertex IDs. It has the format "sourceHost:vertexId
     * destinationHost:vertexId"
     * @return The result of this path query represented by a Graph object.
     */
    public static Graph getPathInSketch(String line) {
        Graph result = new Graph();

        String source = line.split("\\s")[0];
        String srcHost = source.split(":")[0];
        String srcVertexId = source.split(":")[1];

        String destination = line.split("\\s")[1];
        String dstHost = destination.split(":")[0];
        String dstVertexId = destination.split(":")[1];

        Set<AbstractVertex> sourceNetworkVertices = new HashSet<AbstractVertex>();
        Set<AbstractVertex> destinationNetworkVertices = new HashSet<AbstractVertex>();

        try {
            // Connect to destination host and get all the destination network vertices
            SocketAddress sockaddr = new InetSocketAddress(dstHost, Kernel.REMOTE_QUERY_PORT);
            Socket remoteSocket = new Socket();
            remoteSocket.connect(sockaddr, Kernel.CONNECTION_TIMEOUT);
            OutputStream outStream = remoteSocket.getOutputStream();
            InputStream inStream = remoteSocket.getInputStream();
            //ObjectOutputStream graphOutputStream = new ObjectOutputStream(outStream);
            ObjectInputStream graphInputStream = new ObjectInputStream(inStream);
            PrintWriter remoteSocketOut = new PrintWriter(outStream, true);

            //String expression = "query Neo4j vertices type:Network";
            String expression = "query Neo4j vertices network:true";
            remoteSocketOut.println(expression);
            // Check whether the remote query server returned a graph in response
            Graph tempResultGraph = (Graph) graphInputStream.readObject();
            // Add those network vertices to the destination set that have a path
            // to the specified vertex
            for (AbstractVertex currentVertex : tempResultGraph.vertexSet()) {
                expression = "query Neo4j paths " + currentVertex.getAnnotation("storageId") + " " + dstVertexId + " 20";
                remoteSocketOut.println(expression);
                Graph currentGraph = (Graph) graphInputStream.readObject();
                if (!currentGraph.edgeSet().isEmpty()) {
                    destinationNetworkVertices.add(currentVertex);
                    ////////////////////////////////////////////////////////////////////
                    System.out.println("sketchPaths.1 - added vertex " + currentVertex.getAnnotation("storageId") + "to dstSet");
                    ////////////////////////////////////////////////////////////////////
                }
            }

            remoteSocketOut.println("close");
            remoteSocketOut.close();
            graphInputStream.close();
            //graphOutputStream.close();
            inStream.close();
            outStream.close();
            remoteSocket.close();
            ////////////////////////////////////////////////////////////////////
            System.out.println("sketchPaths.1 - received data from " + dstHost);
            ////////////////////////////////////////////////////////////////////

            // Connect to the source host and get all source network vertices.
            sockaddr = new InetSocketAddress(srcHost, Kernel.REMOTE_QUERY_PORT);
            remoteSocket = new Socket();
            remoteSocket.connect(sockaddr, Kernel.CONNECTION_TIMEOUT);
            inStream = remoteSocket.getInputStream();
            outStream = remoteSocket.getOutputStream();
            //graphOutputStream = new ObjectOutputStream(outStream);
            graphInputStream = new ObjectInputStream(inStream);
            remoteSocketOut = new PrintWriter(outStream, true);

            //expression = "query Neo4j vertices type:Network";
            expression = "query Neo4j vertices network:true";
            remoteSocketOut.println(expression);
            // Check whether the remote query server returned a graph in response
            tempResultGraph = (Graph) graphInputStream.readObject();
            for (AbstractVertex currentVertex : tempResultGraph.vertexSet()) {
                expression = "query Neo4j paths " + srcVertexId + " " + currentVertex.getAnnotation("storageId") + " 20";
                remoteSocketOut.println(expression);
                Graph currentGraph = (Graph) graphInputStream.readObject();
                if (!currentGraph.edgeSet().isEmpty()) {
                    sourceNetworkVertices.add(currentVertex);
                    ////////////////////////////////////////////////////////////////////
                    System.out.println("sketchPaths.1 - added vertex " + currentVertex.getAnnotation("storageId") + "to srcSet");
                    ////////////////////////////////////////////////////////////////////
                }
            }

            remoteSocketOut.println("close");
            remoteSocketOut.close();
            graphInputStream.close();
            //graphOutputStream.close();
            inStream.close();
            outStream.close();
            remoteSocket.close();
            ////////////////////////////////////////////////////////////////////
            System.out.println("sketchPaths.2 - received data from " + srcHost);
            ////////////////////////////////////////////////////////////////////


            List<String> hostsToContact = new LinkedList<String>();

            // Determine which hosts need to be contacted for the path fragments:
            //
            // 1) For each remote sketch that this host has cached, get a single
            //    bloom filter containing all the ancestors in that matrix filter.
            // 2) Check if this bloom filter contains any of the destination
            //    network vertices. If yes, this host needs to be contacted.

            for (Map.Entry<String, AbstractSketch> currentEntry : Kernel.remoteSketches.entrySet()) {
                if (currentEntry.getKey().equals(srcHost) || currentEntry.getKey().equals(dstHost)) {
                    continue;
                }
                BloomFilter ancestorFilter = currentEntry.getValue().matrixFilter.getAllBloomFilters();
                for (AbstractVertex destinationVertex : destinationNetworkVertices) {
                    if (ancestorFilter.contains(destinationVertex)) {
                        // Send B's sketch to this host to get the path fragment
                        hostsToContact.add(currentEntry.getKey());
                        break;
                    }
                }
            }

            AbstractSketch mySketch = Kernel.sketches.iterator().next();
            mySketch.objects.put("srcVertices", sourceNetworkVertices);
            mySketch.objects.put("dstVertices", destinationNetworkVertices);
            mySketch.objects.put("queryLine", line);

            // Retrieving path ends
            // Retrieve source end
            List<Graph> graphResults = new Vector<Graph>();
            List<Thread> pathThreads = new LinkedList<Thread>();

            // Start threads to get all path fragments in a multi-threaded manner.

            PathFragment srcFragment = new PathFragment(srcHost, "pathFragment_src", graphResults);
            Thread srcFragmentThread = new Thread(srcFragment);
            pathThreads.add(srcFragmentThread);
            srcFragmentThread.start();

            // Retrieve destination end
            PathFragment dstFragment = new PathFragment(dstHost, "pathFragment_dst", graphResults);
            Thread dstFragmentThread = new Thread(dstFragment);
            pathThreads.add(dstFragmentThread);
            dstFragmentThread.start();

            ////////////////////////////////////////////////////////////////
            System.out.println("sketchPaths.3 - contacting " + hostsToContact.size() + " hosts");
            ////////////////////////////////////////////////////////////////
            for (int i = 0; i < hostsToContact.size(); i++) {
                // Connect to each host and send it B's sketch
                PathFragment midFragment = new PathFragment(hostsToContact.get(i), "pathFragment_mid", graphResults);
                Thread midFragmentThread = new Thread(midFragment);
                pathThreads.add(midFragmentThread);
                midFragmentThread.start();
            }

            // Wait for all threads to finish getting the results.
            for (int i = 0; i < pathThreads.size(); i++) {
                pathThreads.get(i).join();
            }

            // Union all the results to get the final resulting graph.
            for (int i = 0; i < graphResults.size(); i++) {
                result = Graph.union(result, graphResults.get(i));
            }

        } catch (Exception exception) {
            Logger.getLogger(Kernel.class.getName()).log(Level.SEVERE, null, exception);
        }

        ////////////////////////////////////////////////////////////////
        System.out.println("sketchPaths.4 - finished building path from fragments");
        ////////////////////////////////////////////////////////////////

        // Add edges between corresponding network vertices in the resulting graph.
        transformNetworkBoundaries(result);
        return result;
    }

    /**
     * Apply the network vertex transform on the graph since the network
     * vertices between a network boundary are symmetric but not identical.
     *
     * @param graph
     */
    public static void transformNetworkBoundaries(Graph graph) {
        try {
            if (graph.transformed) {
                return;
            } else {
                graph.transformed = true;
            }

            List<AbstractVertex> networkVertices = new LinkedList<AbstractVertex>();
            for (AbstractVertex currentVertex : graph.vertexSet()) {
                //if (currentVertex.type().equalsIgnoreCase("Network")) {
                if (currentVertex.getAnnotation("network").equalsIgnoreCase("true")) {
                    networkVertices.add(currentVertex);
                }
            }

            for (int i = 0; i < networkVertices.size(); i++) {
                AbstractVertex vertex1 = networkVertices.get(i);
                String source_host = vertex1.getAnnotation("source host");
                String source_port = vertex1.getAnnotation("source port");
                String destination_host = vertex1.getAnnotation("destination host");
                String destination_port = vertex1.getAnnotation("destination port");
                for (int j = 0; j < networkVertices.size(); j++) {
                    AbstractVertex vertex2 = networkVertices.get(j);
                    if ((vertex2.getAnnotation("source host").equals(destination_host))
                            && (vertex2.getAnnotation("source port").equals(destination_port))
                            && (vertex2.getAnnotation("destination host").equals(source_host))
                            && (vertex2.getAnnotation("destination port").equals(source_port))) {
                        Edge newEdge1 = new Edge((Vertex) vertex1, (Vertex) vertex2);
                        newEdge1.addAnnotation("type", "Network Boundary");
                        graph.putEdge(newEdge1);
                        Edge newEdge2 = new Edge((Vertex) vertex2, (Vertex) vertex1);
                        newEdge2.addAnnotation("type", "Network Boundary");
                        graph.putEdge(newEdge2);
                    }
                }
            }
        } catch (Exception exception) {
            Logger.getLogger(Query.class.getName()).log(Level.WARNING, null, exception);
        }
    }

    /**
     * Method used to get remote lineage of a network vertex.
     *
     * @param networkVertex The input network vertex.
     * @param depth Depth of lineage.
     * @param direction Direction of lineage.
     * @param terminatingExpression The terminating expression.
     * @return The result represented by a Graph object.
     */
    public static Graph queryNetworkVertex(AbstractVertex networkVertex, int depth, String direction, String terminatingExpression) {
        Graph resultGraph = null;

        try {
            // Establish a connection to the remote host
            SocketAddress sockaddr = new InetSocketAddress(networkVertex.getAnnotation("destination host"), Kernel.REMOTE_QUERY_PORT);
            Socket remoteSocket = new Socket();
            remoteSocket.connect(sockaddr, Kernel.CONNECTION_TIMEOUT);
            OutputStream outStream = remoteSocket.getOutputStream();
            InputStream inStream = remoteSocket.getInputStream();
            //ObjectOutputStream graphOutputStream = new ObjectOutputStream(outStream);
            ObjectInputStream graphInputStream = new ObjectInputStream(inStream);
            PrintWriter remoteSocketOut = new PrintWriter(outStream, true);

            // The first query is used to determine the vertex id of the network
            // vertex on the remote host. This is needed to execute the lineage
            // query
            String vertexQueryExpression = "query Neo4j vertices";
            vertexQueryExpression += " source\\ host:" + networkVertex.getAnnotation("destination host");
            vertexQueryExpression += " AND source\\ port:" + networkVertex.getAnnotation("destination port");
            vertexQueryExpression += " AND destination\\ host:" + networkVertex.getAnnotation("source host");
            vertexQueryExpression += " AND destination\\ port:" + networkVertex.getAnnotation("source port");

            // Execute remote query for vertices
            System.out.println("Sending query expression...");
            System.out.println(vertexQueryExpression);
            remoteSocketOut.println(vertexQueryExpression);
            // Check whether the remote query server returned a graph in response
            Graph vertexGraph = (Graph) graphInputStream.readObject();
            // The graph should only have one vertex which is the network vertex.
            // We use this to get the vertex id
            AbstractVertex targetVertex = vertexGraph.vertexSet().iterator().next();
            String targetVertexId = targetVertex.getAnnotation("storageId");
            int vertexId = Integer.parseInt(targetVertexId);

            // Build the expression for the remote lineage query
            String lineageQueryExpression = "query Neo4j lineage " + vertexId + " " + depth + " " + direction + " " + terminatingExpression + " tmp.dot";
            remoteSocketOut.println(lineageQueryExpression);

            // The graph object we get as a response is returned as the
            // result of this method
            resultGraph = (Graph) graphInputStream.readObject();

            remoteSocketOut.println("close");
            remoteSocketOut.close();
            graphInputStream.close();
            //graphOutputStream.close();
            inStream.close();
            outStream.close();
            remoteSocket.close();
        } catch (Exception exception) {
            Logger.getLogger(Kernel.class.getName()).log(Level.SEVERE, null, exception);
        }

        return resultGraph;
    }
}

class QueryConnection implements Runnable {

    // An object of this class is instantiated when a query connection is made.
    Socket clientSocket;

    QueryConnection(Socket socket) {
        clientSocket = socket;
    }

    public void run() {
        try {
            ////////////////////////////////////////////////////////////////
            System.out.println("Query socket opened");
            ////////////////////////////////////////////////////////////////
            OutputStream outStream = clientSocket.getOutputStream();
            InputStream inStream = clientSocket.getInputStream();
            ObjectOutputStream clientObjectOutputStream = new ObjectOutputStream(outStream);
            BufferedReader clientInputReader = new BufferedReader(new InputStreamReader(inStream));

            String queryLine = clientInputReader.readLine();
            while (!queryLine.equalsIgnoreCase("close")) {
                // Read lines from the querying client until 'close' is called
                ////////////////////////////////////////////////////////////////
                System.out.println("Received query line: " + queryLine);
                ////////////////////////////////////////////////////////////////
                Graph resultGraph = Query.executeQuery(queryLine, true);
                if (resultGraph == null) {
                    resultGraph = new Graph();
                }
                clientObjectOutputStream.writeObject(resultGraph);
                clientObjectOutputStream.flush();
                queryLine = clientInputReader.readLine();
            }

            clientObjectOutputStream.close();
            clientInputReader.close();
            inStream.close();
            outStream.close();
            clientSocket.close();
            ////////////////////////////////////////////////////////////////
            System.out.println("Query socket closed");
            ////////////////////////////////////////////////////////////////
        } catch (Exception ex) {
            Logger.getLogger(QueryConnection.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}

class SketchConnection implements Runnable {

    // An object of this class is instantiated when a sketch connection is made.
    Socket clientSocket;

    SketchConnection(Socket socket) {
        clientSocket = socket;
    }

    public void run() {
        try {
            ////////////////////////////////////////////////////////////////
            System.out.println("Sketch socket opened");
            ////////////////////////////////////////////////////////////////
            InputStream inStream = clientSocket.getInputStream();
            OutputStream outStream = clientSocket.getOutputStream();
            ObjectInputStream clientObjectInputStream = new ObjectInputStream(inStream);
            ObjectOutputStream clientObjectOutputStream = new ObjectOutputStream(outStream);

            String sketchLine = (String) clientObjectInputStream.readObject();
            while (!sketchLine.equalsIgnoreCase("close")) {
                // Process sketch commands issued by the client until 'close' is called.
                ////////////////////////////////////////////////////////////////
                System.out.println("Received sketch line: " + sketchLine);
                ////////////////////////////////////////////////////////////////
                if (sketchLine.equals("giveSketch")) {
                    clientObjectOutputStream.writeObject(Kernel.sketches.iterator().next());
                    clientObjectOutputStream.flush();
                    clientObjectOutputStream.writeObject(Kernel.remoteSketches);
                    clientObjectOutputStream.flush();
                    ////////////////////////////////////////////////////////////////
                    System.out.println("Sent sketches");
                    ////////////////////////////////////////////////////////////////
                } else if (sketchLine.equals("pathFragment_mid")) {
                    // Get a non-terminal path fragment
                    AbstractSketch remoteSketch = (AbstractSketch) clientObjectInputStream.readObject();
                    clientObjectOutputStream.writeObject(Query.getPathFragment(remoteSketch));
                    clientObjectOutputStream.flush();
                } else if (sketchLine.equals("pathFragment_src")) {
                    // Get the source end path fragment
                    AbstractSketch remoteSketch = (AbstractSketch) clientObjectInputStream.readObject();
                    clientObjectOutputStream.writeObject(Query.getEndPathFragment(remoteSketch, "src"));
                    clientObjectOutputStream.flush();
                } else if (sketchLine.equals("pathFragment_dst")) {
                    // Get the destination end path fragment
                    AbstractSketch remoteSketch = (AbstractSketch) clientObjectInputStream.readObject();
                    clientObjectOutputStream.writeObject(Query.getEndPathFragment(remoteSketch, "dst"));
                    clientObjectOutputStream.flush();
                } else if (sketchLine.startsWith("notifyRebuildSketches")) {
                    String tokens[] = sketchLine.split("\\s+");
                    int currentLevel = Integer.parseInt(tokens[1]);
                    int maxLevel = Integer.parseInt(tokens[2]);
                    Query.notifyRebuildSketches(currentLevel, maxLevel);
                } else if (sketchLine.startsWith("propagateSketches")) {
                    String tokens[] = sketchLine.split("\\s+");
                    int currentLevel = Integer.parseInt(tokens[1]);
                    int maxLevel = Integer.parseInt(tokens[2]);
                    Query.propagateSketches(currentLevel, maxLevel);
                }
                sketchLine = (String) clientObjectInputStream.readObject();
            }

            clientObjectInputStream.close();
            clientObjectOutputStream.close();
            inStream.close();
            outStream.close();
            clientSocket.close();
            ////////////////////////////////////////////////////////////////
            System.out.println("Sketch socket closed");
            ////////////////////////////////////////////////////////////////
        } catch (Exception ex) {
            Logger.getLogger(QueryConnection.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}

class RebuildSketch implements Runnable {

    // An object of this class is instantiated when the rebuildSketches method is
    // called. This is done so as to allow the rebuild sketch notifications to be
    // sent in a multi-threaded manner.
    int currentLevel;
    int maxLevel;
    String remoteHost;

    // The constructor is used to store the configuration for this method for
    // the current invocation.
    RebuildSketch(int current, int max, String host) {
        currentLevel = current;
        maxLevel = max;
        remoteHost = host;
    }

    public void run() {
        try {
            SocketAddress sockaddr = new InetSocketAddress(remoteHost, Kernel.REMOTE_SKETCH_PORT);
            Socket remoteSocket = new Socket();
            remoteSocket.connect(sockaddr, Kernel.CONNECTION_TIMEOUT);
            OutputStream outStream = remoteSocket.getOutputStream();
            InputStream inStream = remoteSocket.getInputStream();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(outStream);
            ObjectInputStream objectInputStream = new ObjectInputStream(inStream);

            ////////////////////////////////////////////////////////////////
            System.out.println("notifyRebuildSketch - notifying " + remoteHost);
            ////////////////////////////////////////////////////////////////
            String expression = "notifyRebuildSketches " + currentLevel + " " + maxLevel;
            objectOutputStream.writeObject(expression);
            objectOutputStream.flush();
            objectOutputStream.writeObject("close");
            objectOutputStream.flush();

            objectOutputStream.close();
            objectInputStream.close();
            outStream.close();
            inStream.close();
            remoteSocket.close();
        } catch (Exception exception) {
            Logger.getLogger(RebuildSketch.class.getName()).log(Level.SEVERE, null, exception);
        }
    }
}

class PropagateSketch implements Runnable {

    // An object of this class is instantiated when the propagateSketches method is
    // called. This is done so as to allow the sketch propagation to be performed
    // in a multi-threaded manner.
    int currentLevel;
    int maxLevel;
    String remoteHost;

    // The constructor is used to store the configuration for this method for
    // the current invocation.
    PropagateSketch(int current, int max, String host) {
        currentLevel = current;
        maxLevel = max;
        remoteHost = host;
    }

    public void run() {
        try {
            SocketAddress sockaddr = new InetSocketAddress(remoteHost, Kernel.REMOTE_SKETCH_PORT);
            Socket remoteSocket = new Socket();
            remoteSocket.connect(sockaddr, Kernel.CONNECTION_TIMEOUT);
            OutputStream outStream = remoteSocket.getOutputStream();
            InputStream inStream = remoteSocket.getInputStream();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(outStream);
            ObjectInputStream objectInputStream = new ObjectInputStream(inStream);

            ////////////////////////////////////////////////////////////////
            System.out.println("propagateSketches - propagating to " + remoteHost);
            ////////////////////////////////////////////////////////////////
            String expression = "propagateSketches " + currentLevel + " " + maxLevel;
            objectOutputStream.writeObject(expression);
            objectOutputStream.flush();
            objectOutputStream.writeObject("close");
            objectOutputStream.flush();

            objectOutputStream.close();
            objectInputStream.close();
            inStream.close();
            outStream.close();
            remoteSocket.close();
        } catch (Exception exception) {
            Logger.getLogger(PropagateSketch.class.getName()).log(Level.SEVERE, null, exception);
        }
    }
}

class PathFragment implements Runnable {

    // An object of this class in instantiated when a path fragment is to be
    // fetched from a remote SPADE instance. This is done so as to allow all
    // the path fragments to be fetched in a multi-threaded manner.
    String remoteHost;
    String pathFragment;
    List<Graph> graphResults;

    PathFragment(String host, String fragment, List<Graph> results) {
        remoteHost = host;
        pathFragment = fragment;
        graphResults = results;
    }

    public void run() {
        try {
            SocketAddress sockaddr = new InetSocketAddress(remoteHost, Kernel.REMOTE_SKETCH_PORT);
            Socket remoteSocket = new Socket();
            remoteSocket.connect(sockaddr, Kernel.CONNECTION_TIMEOUT);
            OutputStream outStream = remoteSocket.getOutputStream();
            InputStream inStream = remoteSocket.getInputStream();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(outStream);
            ObjectInputStream objectInputStream = new ObjectInputStream(inStream);
            // Send the sketch
            objectOutputStream.writeObject(pathFragment);
            objectOutputStream.flush();
            objectOutputStream.writeObject(Kernel.sketches.iterator().next());
            objectOutputStream.flush();
            // Receive the graph fragment
            Graph tempResultGraph = (Graph) objectInputStream.readObject();
            objectOutputStream.writeObject("close");
            objectOutputStream.flush();
            objectOutputStream.close();
            objectInputStream.close();
            inStream.close();
            outStream.close();
            remoteSocket.close();
            ////////////////////////////////////////////////////////////////
            System.out.println("PathFragment - received path fragment from " + remoteHost);
            ////////////////////////////////////////////////////////////////
            graphResults.add(tempResultGraph);
        } catch (Exception exception) {
            Logger.getLogger(PathFragment.class.getName()).log(Level.SEVERE, null, exception);
        }
    }
}
