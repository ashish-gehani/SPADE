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
package spade.core;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLSocket;

/**
 *
 * @author Dawood Tariq
 */
public class Query {

    private static final int WAIT_FOR_FLUSH = 10;
    private static final Logger logger = Logger.getLogger(Query.class.getName());
    protected static final boolean DEBUG_OUTPUT = false;
    public static final String STORAGE_ID_STRING = "storageId";
    // String to match for when specifying direction for ancestors
    public static final String DIRECTION_ANCESTORS = "ancestors";
    // String to match for when specifying direction for descendants
    public static final String DIRECTION_DESCENDANTS = "descendants";
    // String to match for when specifying direction for both ancestors
    // and descendants
    public static final String DIRECTION_BOTH = "both";

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
                logger.log(Level.SEVERE, null, exception);
            }
        }
        if ((line == null) || (Kernel.storages.isEmpty())) {
            return null;
        }
        try {
            String[] tokens = line.split("\\s+", 4);
            for (AbstractStorage storage : Kernel.storages) {
                if (storage.getClass().getName().equals("spade.storage." + tokens[1])) {

                    if (DEBUG_OUTPUT) {
                        logger.log(Level.INFO, "Executing query line: {0}", line);
                    }

                    // Determine the type of query and call the corresponding method
                    begintime = System.currentTimeMillis();
                    if (tokens[2].equalsIgnoreCase("vertices")) {
                        resultGraph = queryVertices(tokens[3], storage);
                    } else if (tokens[2].equalsIgnoreCase("edges")) {
                        resultGraph = queryEdges(tokens[3], storage);
                    } else if (tokens[2].equalsIgnoreCase("remotevertices")) {
                        resultGraph = queryRemoteVertices(tokens[3], storage);
                    } else if (tokens[2].equalsIgnoreCase("lineage")) {
                        resultGraph = queryLineage(tokens[3], storage, resolveRemote);
                        if (resolveRemote) {
                            transformNetworkBoundaries(resultGraph);
                        }
                    } else if (tokens[2].equalsIgnoreCase("paths")) {
                        resultGraph = queryPaths(tokens[3], storage);
                    } else if (tokens[2].equalsIgnoreCase("sketchpaths")) {
                        resultGraph = getPathInSketch(tokens[3] + " " + tokens[4]);
                        transformNetworkBoundaries(resultGraph);
                    } else if (tokens[2].equalsIgnoreCase("rebuildsketches")) {
                        notifyRebuildSketches(Integer.parseInt(tokens[3]), Integer.parseInt(tokens[3]));
                        return null;
                    } else if (tokens[2].equalsIgnoreCase("remotepaths")) {
                        resultGraph = queryRemotePaths(tokens[3], storage);
                        transformNetworkBoundaries(resultGraph);
                    } else {
                        return null;
                    }
                }
            }
        } catch (Exception badQuery) {
            logger.log(Level.SEVERE, null, badQuery);
            return null;
        }
        endtime = System.currentTimeMillis();
        long elapsedtime = endtime - begintime;

        if (DEBUG_OUTPUT) {
            logger.log(Level.INFO, "Time taken for query \"({0})\": {1}", new Object[]{line, elapsedtime});
        }

        return resultGraph;
    }

    private static Graph queryEdges(String queryLine, AbstractStorage storage) {
        try {
            String[] expression = queryLine.split(",");
            Graph resultGraph;
            if (expression.length == 2) {
                resultGraph = storage.getEdges(expression[0].trim(), expression[1].trim());
            } else if (expression.length == 3) {
                resultGraph = storage.getEdges(expression[0].trim(), expression[1].trim(), expression[2].trim());
            } else {
                return null;
            }
            return resultGraph;
        } catch (Exception badQuery) {
            logger.log(Level.SEVERE, null, badQuery);
            return null;
        }
    }

    private static Graph queryVertices(String queryLine, AbstractStorage storage) {
        try {
            Graph resultGraph = storage.getVertices(queryLine);
            return resultGraph;
        } catch (Exception badQuery) {
            logger.log(Level.SEVERE, null, badQuery);
            return null;
        }
    }

    private static Graph queryRemoteVertices(String queryLine, AbstractStorage storage) {
        try {
            String[] tokens = queryLine.split("\\s+", 2);
            String host = tokens[0];
            String queryExpression = tokens[1];
            // Connect to the specified host and query for vertices.
            int port = Integer.parseInt(Settings.getProperty("remote_query_port"));
            SSLSocket remoteSocket = (SSLSocket) Kernel.sslSocketFactory.createSocket(host, port);

            OutputStream outStream = remoteSocket.getOutputStream();
            InputStream inStream = remoteSocket.getInputStream();
            //ObjectOutputStream graphOutputStream = new ObjectOutputStream(outStream);
            ObjectInputStream graphInputStream = new ObjectInputStream(inStream);
            PrintWriter remoteSocketOut = new PrintWriter(outStream, true);

            String srcExpression = "query Neo4j vertices " + queryExpression;
            remoteSocketOut.println(srcExpression);
            Graph resultGraph = (Graph) graphInputStream.readObject();

            remoteSocketOut.println("close");
            graphInputStream.close();
            //graphOutputStream.close();
            remoteSocketOut.close();
            inStream.close();
            outStream.close();
            remoteSocket.close();
            return resultGraph;
        } catch (Exception badQuery) {
            logger.log(Level.SEVERE, null, badQuery);
            return null;
        }
    }

    private static Graph queryLineage(String queryLine, AbstractStorage storage, boolean resolveRemote) {
        Graph resultGraph;
        try {
            String[] tokens = queryLine.split("\\s+", 4);
            String vertexId = tokens[0];
            int depth = Integer.parseInt(tokens[1]);
            String direction = tokens[2];
            String terminatingExpression = tokens[3];
            resultGraph = storage.getLineage(vertexId, depth, direction, terminatingExpression);
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
                        Graph tempRemoteGraph = queryNetworkVertex(networkVertex, depth - currentDepth, direction, terminatingExpression);
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
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
            return null;
        }
        return resultGraph;
    }

    private static Graph queryPaths(String queryLine, AbstractStorage storage) {
        try {
            String[] tokens = queryLine.split("\\s+");
            String srcVertexId = tokens[0];
            String dstVertexId = tokens[1];
            int maxLength = Integer.parseInt(tokens[2]);
            Graph resultGraph = storage.getPaths(srcVertexId, dstVertexId, maxLength);
            return resultGraph;
        } catch (Exception badQuery) {
            logger.log(Level.SEVERE, null, badQuery);
            return null;
        }
    }

    private static Graph queryRemotePaths(String queryLine, AbstractStorage storage) {
        try {
            String[] tokens = queryLine.split("\\s+");
            String source = tokens[0];
            String srcHost = source.split(":")[0];
            String srcVertexId = source.split(":")[1];
            String destination = tokens[1];
            String dstHost = destination.split(":")[0];
            String dstVertexId = destination.split(":")[1];
            int maxLength = Integer.parseInt(tokens[2]);
            if (srcHost.equalsIgnoreCase("localhost") && dstHost.equalsIgnoreCase("localhost")) {
                String newQueryLine = srcVertexId + dstVertexId + maxLength;
                return queryPaths(newQueryLine, storage);
            } else {
                Graph srcGraph, dstGraph;

                // Connect to source host and get upward lineage
                int port = Integer.parseInt(Settings.getProperty("remote_query_port"));
                SSLSocket remoteSocket = (SSLSocket) Kernel.sslSocketFactory.createSocket(srcHost, port);

                OutputStream outStream = remoteSocket.getOutputStream();
                InputStream inStream = remoteSocket.getInputStream();
                //ObjectOutputStream graphOutputStream = new ObjectOutputStream(outStream);
                ObjectInputStream graphInputStream = new ObjectInputStream(inStream);
                PrintWriter remoteSocketOut = new PrintWriter(outStream, true);

                String srcExpression = "query Neo4j lineage " + srcVertexId + " " + maxLength + " ancestors null";
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
                port = Integer.parseInt(Settings.getProperty("remote_query_port"));
                remoteSocket = (SSLSocket) Kernel.sslSocketFactory.createSocket(dstHost, port);

                outStream = remoteSocket.getOutputStream();
                inStream = remoteSocket.getInputStream();
                //graphOutputStream = new ObjectOutputStream(outStream);
                graphInputStream = new ObjectInputStream(inStream);
                remoteSocketOut = new PrintWriter(outStream, true);

                String dstExpression = "query Neo4j lineage " + dstVertexId + " " + maxLength + " descendants null";
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
                Graph resultGraph = Graph.intersection(srcGraph, dstGraph);
                return resultGraph;
            }
        } catch (Exception badQuery) {
            logger.log(Level.SEVERE, null, badQuery);
            return null;
        }
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

        if (DEBUG_OUTPUT) {
            logger.log(Level.INFO, "endPathFragment - generating end path fragment");
        }

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

            if (DEBUG_OUTPUT) {
                logger.log(Level.INFO, "endPathFragment - checking {0} srcVertices", ((Set<AbstractVertex>) inputSketch.objects.get("srcVertices")).size());
            }

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

            if (DEBUG_OUTPUT) {
                logger.log(Level.INFO, "endPathFragment - generating up paths between {0} matched vertices", vertices.length);
            }

            for (int i = 0; i < vertices.length; i++) {
                String vertexId = ((AbstractVertex) vertices[i]).getAnnotation(Query.STORAGE_ID_STRING);
                Graph path = executeQuery("query Neo4j paths " + srcVertexId + " " + vertexId + " 20", false);
                if (!path.edgeSet().isEmpty()) {
                    result = Graph.union(result, path);

                    if (DEBUG_OUTPUT) {
                        logger.log(Level.INFO, "endPathFragment - added path to result fragment");
                    }

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

            if (DEBUG_OUTPUT) {
                logger.log(Level.INFO, "endPathFragment - checking {0} dstVertices", ((Set<AbstractVertex>) inputSketch.objects.get("dstVertices")).size());
            }

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

            if (DEBUG_OUTPUT) {
                logger.log(Level.INFO, "endPathFragment - generating down paths between {0} matched vertices", vertices.length);
            }

            for (int i = 0; i < vertices.length; i++) {
                String vertexId = ((AbstractVertex) vertices[i]).getAnnotation(Query.STORAGE_ID_STRING);
                Graph path = executeQuery("query Neo4j paths " + vertexId + " " + dstVertexId + " 20", false);
                if (!path.edgeSet().isEmpty()) {
                    result = Graph.union(result, path);

                    if (DEBUG_OUTPUT) {
                        logger.log(Level.INFO, "endPathFragment - added path to result fragment");
                    }

                }
            }
        }

        if (DEBUG_OUTPUT) {
            logger.log(Level.INFO, "endPathFragment - returning {0} end fragment", end);
        }

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

        if (DEBUG_OUTPUT) {
            logger.log(Level.INFO, "pathFragment.a - generating path fragment");
        }

        //Graph myNetworkVertices = query("query Neo4j vertices type:Network", false);
        Graph myNetworkVertices = executeQuery("query Neo4j vertices network:true", false);
        Set<AbstractVertex> matchingVerticesDown = new HashSet<AbstractVertex>();
        Set<AbstractVertex> matchingVerticesUp = new HashSet<AbstractVertex>();
        MatrixFilter receivedMatrixFilter = inputSketch.matrixFilter;
        MatrixFilter myMatrixFilter = Kernel.sketches.iterator().next().matrixFilter;

        // Current host's network vertices that match downward

        if (DEBUG_OUTPUT) {
            logger.log(Level.INFO, "pathFragment.b - checking {0} srcVertices", ((Set<AbstractVertex>) inputSketch.objects.get("srcVertices")).size());
        }

        for (AbstractVertex sourceVertex : (Set<AbstractVertex>) inputSketch.objects.get("srcVertices")) {
            BloomFilter currentBloomFilter = receivedMatrixFilter.get(sourceVertex);
            for (AbstractVertex vertexToCheck : myNetworkVertices.vertexSet()) {
                if (currentBloomFilter.contains(vertexToCheck)) {
                    matchingVerticesUp.add(vertexToCheck);
                }
            }
        }

        if (DEBUG_OUTPUT) {
            logger.log(Level.INFO, "pathFragment.c - added downward vertices");
        }

        // Current host's network vertices that match upward

        if (DEBUG_OUTPUT) {
            logger.log(Level.INFO, "pathFragment.d - checking {0} dstVertices", ((Set<AbstractVertex>) inputSketch.objects.get("dstVertices")).size());
        }

        for (AbstractVertex vertexToCheck : myNetworkVertices.vertexSet()) {
            BloomFilter currentBloomFilter = myMatrixFilter.get(vertexToCheck);
            for (AbstractVertex destinationVertex : (Set<AbstractVertex>) inputSketch.objects.get("dstVertices")) {
                if (currentBloomFilter.contains(destinationVertex)) {
                    matchingVerticesDown.add(vertexToCheck);
                }
            }
        }

        if (DEBUG_OUTPUT) {
            logger.log(Level.INFO, "pathFragment.e - added upward vertices");
        }

        // Network vertices that we're interested in

        if (DEBUG_OUTPUT) {
            logger.log(Level.INFO, "pathFragment.f - {0} in down vertices", matchingVerticesDown.size());
        }
        if (DEBUG_OUTPUT) {
            logger.log(Level.INFO, "pathFragment.g - {0} in up vertices", matchingVerticesUp.size());
        }

        Set<AbstractVertex> matchingVertices = new HashSet<AbstractVertex>();
        matchingVertices.addAll(matchingVerticesDown);
        matchingVertices.retainAll(matchingVerticesUp);

        if (DEBUG_OUTPUT) {
            logger.log(Level.INFO, "pathFragment.h - {0} total matching vertices", matchingVertices.size());
        }

        // Get all paths between the matching network vertices
        Object vertices[] = matchingVertices.toArray();

        if (DEBUG_OUTPUT) {
            logger.log(Level.INFO, "pathFragment.i - generating paths between {0} matched vertices", vertices.length);
        }

        for (int i = 0; i < vertices.length; i++) {
            for (int j = 0; j < vertices.length; j++) {
                if (j == i) {
                    continue;
                }
                String srcId = ((AbstractVertex) vertices[i]).getAnnotation(Query.STORAGE_ID_STRING);
                String dstId = ((AbstractVertex) vertices[j]).getAnnotation(Query.STORAGE_ID_STRING);
                Graph path = executeQuery("query Neo4j paths " + srcId + " " + dstId + " 20", false);
                if (!path.edgeSet().isEmpty()) {
                    result = Graph.union(result, path);

                    if (DEBUG_OUTPUT) {
                        logger.log(Level.INFO, "pathFragment.j - added path to result fragment");
                    }

                }
            }
        }

        if (DEBUG_OUTPUT) {
            logger.log(Level.INFO, "pathFragment.k - returning fragment");
        }

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

        if (DEBUG_OUTPUT) {
            logger.log(Level.INFO, "rebuildLocalSketch - rebuilding local sketch");
        }

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
            logger.log(Level.SEVERE, null, exception);
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

            if (DEBUG_OUTPUT) {
                logger.log(Level.INFO, "propagateSketches - reached max level, terminating propagation");
            }

            return;
        }

        if (DEBUG_OUTPUT) {
            logger.log(Level.INFO, "propagateSketches - propagating sketches");
        }

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
                logger.log(Level.SEVERE, null, exception);
            }
        }

        if (DEBUG_OUTPUT) {
            logger.log(Level.INFO, "propagateSketches - finished propagation");
        }

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

            if (DEBUG_OUTPUT) {
                logger.log(Level.INFO, "notifyRebuildSketch - reached level zero, propagating");
            }

            propagateSketches(0, maxLevel);
            return;
        }

        if (DEBUG_OUTPUT) {
            logger.log(Level.INFO, "notifyRebuildSketch - sending rebuild notifications");
        }

        executeQuery(null, false); // To flush transactions
        //Set<AbstractVertex> upVertices = storages.iterator().next().getEdges(null, "type:Network", "type:Used").vertexSet();
        Set<AbstractVertex> upVertices = Kernel.storages.iterator().next().getEdges(null, "network:true", "type:Used").vertexSet();
        // If there are no incoming network vertices to send notifications to,
        // stop notifying and begin propagation.
        if (upVertices.isEmpty()) {

            if (DEBUG_OUTPUT) {
                logger.log(Level.INFO, "notifyRebuildSketch - no more notification to send, beginning propagation");
            }

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
                logger.log(Level.SEVERE, null, exception);
            }
        }

        if (DEBUG_OUTPUT) {
            logger.log(Level.INFO, "notifyRebuildSketch - finished sending rebuild notifications");
        }

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
            int port = Integer.parseInt(Settings.getProperty("remote_query_port"));
            SSLSocket remoteSocket = (SSLSocket) Kernel.sslSocketFactory.createSocket(dstHost, port);

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
                expression = "query Neo4j paths " + currentVertex.getAnnotation(Query.STORAGE_ID_STRING) + " " + dstVertexId + " 20";
                remoteSocketOut.println(expression);
                Graph currentGraph = (Graph) graphInputStream.readObject();
                if (!currentGraph.edgeSet().isEmpty()) {
                    destinationNetworkVertices.add(currentVertex);

                    if (DEBUG_OUTPUT) {
                        logger.log(Level.INFO, "sketchPaths.1 - added vertex {0} to dstSet", currentVertex.getAnnotation(Query.STORAGE_ID_STRING));
                    }

                }
            }

            remoteSocketOut.println("close");
            remoteSocketOut.close();
            graphInputStream.close();
            //graphOutputStream.close();
            inStream.close();
            outStream.close();
            remoteSocket.close();

            if (DEBUG_OUTPUT) {
                logger.log(Level.INFO, "sketchPaths.1 - received data from {0}", dstHost);
            }

            // Connect to the source host and get all source network vertices.
            port = Integer.parseInt(Settings.getProperty("remote_query_port"));
            remoteSocket = (SSLSocket) Kernel.sslSocketFactory.createSocket(srcHost, port);

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
                expression = "query Neo4j paths " + srcVertexId + " " + currentVertex.getAnnotation(Query.STORAGE_ID_STRING) + " 20";
                remoteSocketOut.println(expression);
                Graph currentGraph = (Graph) graphInputStream.readObject();
                if (!currentGraph.edgeSet().isEmpty()) {
                    sourceNetworkVertices.add(currentVertex);

                    if (DEBUG_OUTPUT) {
                        logger.log(Level.INFO, "sketchPaths.1 - added vertex {0} to srcSet", currentVertex.getAnnotation(Query.STORAGE_ID_STRING));
                    }

                }
            }

            remoteSocketOut.println("close");
            remoteSocketOut.close();
            graphInputStream.close();
            //graphOutputStream.close();
            inStream.close();
            outStream.close();
            remoteSocket.close();

            if (DEBUG_OUTPUT) {
                logger.log(Level.INFO, "sketchPaths.2 - received data from {0}", srcHost);
            }


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

            if (DEBUG_OUTPUT) {
                logger.log(Level.INFO, "sketchPaths.3 - contacting {0} hosts", hostsToContact.size());
            }

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
            logger.log(Level.SEVERE, null, exception);
        }

        if (DEBUG_OUTPUT) {
            logger.log(Level.INFO, "sketchPaths.4 - finished building path from fragments");
        }

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
    private static void transformNetworkBoundaries(Graph graph) {
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
    private static Graph queryNetworkVertex(AbstractVertex networkVertex, int depth, String direction, String terminatingExpression) {
        Graph resultGraph = null;

        try {
            // Establish a connection to the remote host
            String host = networkVertex.getAnnotation("destination host");
            int port = Integer.parseInt(Settings.getProperty("remote_query_port"));
            SSLSocket remoteSocket = (SSLSocket) Kernel.sslSocketFactory.createSocket(host, port);

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
            if (DEBUG_OUTPUT) {
                logger.log(Level.INFO, "Sending query expression: {0}", vertexQueryExpression);
            }
            remoteSocketOut.println(vertexQueryExpression);
            // Check whether the remote query server returned a graph in response
            Graph vertexGraph = (Graph) graphInputStream.readObject();
            // The graph should only have one vertex which is the network vertex.
            // We use this to get the vertex id
            AbstractVertex targetVertex = vertexGraph.vertexSet().iterator().next();
            String targetVertexId = targetVertex.getAnnotation(Query.STORAGE_ID_STRING);
            int vertexId = Integer.parseInt(targetVertexId);

            // Build the expression for the remote lineage query
            String lineageQueryExpression = "query Neo4j lineage " + vertexId + " " + depth + " " + direction + " " + terminatingExpression;
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
            logger.log(Level.SEVERE, null, exception);
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

            if (Query.DEBUG_OUTPUT) {
                Logger.getLogger(QueryConnection.class.getName()).log(Level.INFO, "Query socket opened");
            }

            OutputStream outStream = clientSocket.getOutputStream();
            InputStream inStream = clientSocket.getInputStream();
            ObjectOutputStream clientObjectOutputStream = new ObjectOutputStream(outStream);
            BufferedReader clientInputReader = new BufferedReader(new InputStreamReader(inStream));

            String queryLine = clientInputReader.readLine();
            while (!queryLine.equalsIgnoreCase("close")) {
                // Read lines from the querying client until 'close' is called

                if (Query.DEBUG_OUTPUT) {
                    Logger.getLogger(QueryConnection.class.getName()).log(Level.INFO, "Received query line: {0}", queryLine);
                }

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

            if (Query.DEBUG_OUTPUT) {
                Logger.getLogger(QueryConnection.class.getName()).log(Level.INFO, "Query socket closed");
            }

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

            if (Query.DEBUG_OUTPUT) {
                Logger.getLogger(SketchConnection.class.getName()).log(Level.INFO, "Sketch socket opened");
            }

            InputStream inStream = clientSocket.getInputStream();
            OutputStream outStream = clientSocket.getOutputStream();
            ObjectInputStream clientObjectInputStream = new ObjectInputStream(inStream);
            ObjectOutputStream clientObjectOutputStream = new ObjectOutputStream(outStream);

            String sketchLine = (String) clientObjectInputStream.readObject();
            while (!sketchLine.equalsIgnoreCase("close")) {
                // Process sketch commands issued by the client until 'close' is called.

                if (Query.DEBUG_OUTPUT) {
                    Logger.getLogger(SketchConnection.class.getName()).log(Level.INFO, "Received sketch line: {0}", sketchLine);
                }

                if (sketchLine.equals("giveSketch")) {
                    clientObjectOutputStream.writeObject(Kernel.sketches.iterator().next());
                    clientObjectOutputStream.flush();
                    clientObjectOutputStream.writeObject(Kernel.remoteSketches);
                    clientObjectOutputStream.flush();

                    Logger.getLogger(SketchConnection.class.getName()).log(Level.INFO, "Sent sketches");

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

            if (Query.DEBUG_OUTPUT) {
                Logger.getLogger(SketchConnection.class.getName()).log(Level.INFO, "Sketch socket closed");
            }

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
            int port = Integer.parseInt(Settings.getProperty("remote_sketch_port"));
            SSLSocket remoteSocket = (SSLSocket) Kernel.sslSocketFactory.createSocket(remoteHost, port);

            OutputStream outStream = remoteSocket.getOutputStream();
            InputStream inStream = remoteSocket.getInputStream();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(outStream);
            ObjectInputStream objectInputStream = new ObjectInputStream(inStream);

            if (Query.DEBUG_OUTPUT) {
                Logger.getLogger(RebuildSketch.class.getName()).log(Level.INFO, "notifyRebuildSketch - notifying {0}", remoteHost);
            }

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
            int port = Integer.parseInt(Settings.getProperty("remote_sketch_port"));
            SSLSocket remoteSocket = (SSLSocket) Kernel.sslSocketFactory.createSocket(remoteHost, port);

            OutputStream outStream = remoteSocket.getOutputStream();
            InputStream inStream = remoteSocket.getInputStream();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(outStream);
            ObjectInputStream objectInputStream = new ObjectInputStream(inStream);

            if (Query.DEBUG_OUTPUT) {
                Logger.getLogger(PropagateSketch.class.getName()).log(Level.INFO, "propagateSketches - propagating to {0}", remoteHost);
            }

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
            int port = Integer.parseInt(Settings.getProperty("remote_sketch_port"));
            SSLSocket remoteSocket = (SSLSocket) Kernel.sslSocketFactory.createSocket(remoteHost, port);

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

            if (Query.DEBUG_OUTPUT) {
                Logger.getLogger(PathFragment.class.getName()).log(Level.INFO, "PathFragment - received path fragment from {0}", remoteHost);
            }

            graphResults.add(tempResultGraph);
        } catch (Exception exception) {
            Logger.getLogger(PathFragment.class.getName()).log(Level.SEVERE, null, exception);
        }
    }
}
