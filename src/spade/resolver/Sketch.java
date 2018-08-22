/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2017 SRI International
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
package spade.resolver;

import spade.core.AbstractEdge;
import spade.core.AbstractResolver;
import spade.core.AbstractSketch;
import spade.core.AbstractVertex;
import spade.core.BloomFilter;
import spade.core.Edge;
import spade.core.Graph;
import spade.core.Kernel;
import spade.core.MatrixFilter;
import spade.core.Settings;
import spade.core.Vertex;
import spade.query.common.GetPaths;
import spade.query.postgresql.GetVertex;

import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static spade.core.AbstractQuery.OPERATORS;
import static spade.core.AbstractStorage.CHILD_VERTEX_KEY;
import static spade.core.AbstractStorage.DIRECTION_ANCESTORS;
import static spade.core.AbstractStorage.PARENT_VERTEX_KEY;
import static spade.core.AbstractStorage.PRIMARY_KEY;

/**
 * @author raza
 */
public class Sketch extends AbstractResolver
{
    private static final Logger logger = Logger.getLogger(Sketch.class.getName());
    Socket clientSocket;

    public Sketch(Graph graph, String func, int d, String dir, Socket s)
    {
        super(graph, func, d, dir);
        clientSocket = s;
    }

    @Override
    public void run()
    {
        try
        {
            InputStream inStream = clientSocket.getInputStream();
            OutputStream outStream = clientSocket.getOutputStream();
            ObjectInputStream clientObjectInputStream = new ObjectInputStream(inStream);
            ObjectOutputStream clientObjectOutputStream = new ObjectOutputStream(outStream);

            String sketchLine = (String) clientObjectInputStream.readObject();
            while (!sketchLine.equalsIgnoreCase("close"))
            {
                // Process sketch commands issued by the client until 'close' is called.
                if (sketchLine.equals("giveSketch"))
                {
                    clientObjectOutputStream.writeObject(Kernel.sketches.iterator().next());
                    clientObjectOutputStream.flush();
                    clientObjectOutputStream.writeObject(Kernel.remoteSketches);
                    clientObjectOutputStream.flush();

                    Logger.getLogger(Sketch.class.getName()).log(Level.INFO, "Sent sketches");
                }
                else if (sketchLine.equals("pathFragment_mid"))
                {
                    // Get a non-terminal path fragment
                    AbstractSketch remoteSketch = (AbstractSketch) clientObjectInputStream.readObject();
                    clientObjectOutputStream.writeObject(getPathFragment(remoteSketch));
                    clientObjectOutputStream.flush();
                }
                else if (sketchLine.equals("pathFragment_src"))
                {
                    // Get the source end path fragment
                    AbstractSketch remoteSketch = (AbstractSketch) clientObjectInputStream.readObject();
                    clientObjectOutputStream.writeObject(getEndPathFragment(remoteSketch, "src"));
                    clientObjectOutputStream.flush();
                }
                else if (sketchLine.equals("pathFragment_dst"))
                {
                    // Get the destination end path fragment
                    AbstractSketch remoteSketch = (AbstractSketch) clientObjectInputStream.readObject();
                    clientObjectOutputStream.writeObject(getEndPathFragment(remoteSketch, "dst"));
                    clientObjectOutputStream.flush();
                }
                else if (sketchLine.startsWith("notifyRebuildSketches"))
                {
                    String tokens[] = sketchLine.split("\\s+");
                    int currentLevel = Integer.parseInt(tokens[1]);
                    int maxLevel = Integer.parseInt(tokens[2]);
                    notifyRebuildSketches(currentLevel, maxLevel);
                }
                else if (sketchLine.startsWith("propagateSketches"))
                {
                    String tokens[] = sketchLine.split("\\s+");
                    int currentLevel = Integer.parseInt(tokens[1]);
                    int maxLevel = Integer.parseInt(tokens[2]);
                    propagateSketches(currentLevel, maxLevel);
                }
                sketchLine = (String) clientObjectInputStream.readObject();
            }

            clientObjectInputStream.close();
            clientObjectOutputStream.close();
            inStream.close();
            outStream.close();
            clientSocket.close();

        }
        catch (IOException | ClassNotFoundException | NumberFormatException ex)
        {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Method to retrieve a non-terminal path fragment given a remote sketch as
     * input.
     *
     * @param inputSketch The input sketch.
     * @return A path fragment represented by a Graph object.
     */
    public static Graph getPathFragment(AbstractSketch inputSketch)
    {
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

        logger.log(Level.INFO, "pathFragment.a - generating path fragment");

        Map<String, List<String>> vertexParams = new HashMap<>();
        vertexParams.put("subtype", Collections.singletonList("network"));
        GetVertex getVertex = new GetVertex();
        Set<AbstractVertex> myNetworkVertices = getVertex.execute(vertexParams, 100);

        Set<AbstractVertex> matchingVerticesDown = new HashSet<>();
        Set<AbstractVertex> matchingVerticesUp = new HashSet<>();
        MatrixFilter receivedMatrixFilter = inputSketch.matrixFilter;
        MatrixFilter myMatrixFilter = Kernel.sketches.iterator().next().matrixFilter;

        // Current host's network vertices that match downward
        logger.log(Level.INFO, "pathFragment.b - checking {0} srcVertices", ((Set<AbstractVertex>) inputSketch.objects.get("srcVertices")).size());

        for (AbstractVertex childVertex : (Set<AbstractVertex>) inputSketch.objects.get("srcVertices"))
        {
            BloomFilter currentBloomFilter = receivedMatrixFilter.get(childVertex);
            for (AbstractVertex vertexToCheck : myNetworkVertices)
            {
                if (currentBloomFilter.contains(vertexToCheck))
                {
                    matchingVerticesUp.add(vertexToCheck);
                }
            }
        }

        logger.log(Level.INFO, "pathFragment.c - added downward vertices");

        // Current host's network vertices that match upward
        logger.log(Level.INFO, "pathFragment.d - checking {0} dstVertices", ((Set<AbstractVertex>) inputSketch.objects.get("dstVertices")).size());

        for (AbstractVertex vertexToCheck : myNetworkVertices)
        {
            BloomFilter currentBloomFilter = myMatrixFilter.get(vertexToCheck);
            for (AbstractVertex parentVertex : (Set<AbstractVertex>) inputSketch.objects.get("dstVertices"))
            {
                if (currentBloomFilter.contains(parentVertex))
                {
                    matchingVerticesDown.add(vertexToCheck);
                }
            }
        }

        logger.log(Level.INFO, "pathFragment.e - added upward vertices");

        // Network vertices that we're interested in
        logger.log(Level.INFO, "pathFragment.f - {0} in down vertices", matchingVerticesDown.size());
        logger.log(Level.INFO, "pathFragment.g - {0} in up vertices", matchingVerticesUp.size());

        Set<AbstractVertex> matchingVertices = new HashSet<>();
        matchingVertices.addAll(matchingVerticesDown);
        matchingVertices.retainAll(matchingVerticesUp);

        logger.log(Level.INFO, "pathFragment.h - {0} total matching vertices", matchingVertices.size());

        // Get all paths between the matching network vertices
        Object vertices[] = matchingVertices.toArray();

        logger.log(Level.INFO, "pathFragment.i - generating paths between {0} matched vertices", vertices.length);

        for (int i = 0; i < vertices.length; i++)
        {
            for (int j = 0; j < vertices.length; j++)
            {
                if (j == i)
                {
                    continue;
                }
                String srcId = ((AbstractVertex) vertices[i]).getAnnotation(PRIMARY_KEY);
                String dstId = ((AbstractVertex) vertices[j]).getAnnotation(PRIMARY_KEY);
                GetPaths getPaths = new GetPaths();
                Map<String, List<String>> pathParams = new HashMap<>();
                pathParams.put(CHILD_VERTEX_KEY, Arrays.asList(OPERATORS.EQUALS, srcId));
                pathParams.put(PARENT_VERTEX_KEY, Arrays.asList(OPERATORS.EQUALS, dstId));
                pathParams.put("direction", Collections.singletonList(DIRECTION_ANCESTORS));
                pathParams.put("maxLength", Collections.singletonList("100"));
                Graph path = getPaths.execute(pathParams, 100);
                if (!path.edgeSet().isEmpty())
                {
                    result = Graph.union(result, path);
                    logger.log(Level.INFO, "pathFragment.j - added path to result fragment");
                }
            }
        }
        logger.log(Level.INFO, "pathFragment.k - returning fragment");

        return result;
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
    public static Graph getEndPathFragment(AbstractSketch inputSketch, String end)
    {
        // Given a remote sketch, this method returns an ending path fragment from the
        // local graph database.

        Graph result = new Graph();

        String line = (String) inputSketch.objects.get("queryLine");
        String source = line.split("\\s")[0];
        String childVertexId = source.split(":")[1];
        String destination = line.split("\\s")[1];
        String parentVertexId = destination.split(":")[1];

        logger.log(Level.INFO, "endPathFragment - generating end path fragment");

        // Store the local network vertices in a set for later use.
        Map<String, List<String>> vertexParams = new HashMap<>();
        vertexParams.put("subtype", Collections.singletonList("network"));
        GetVertex getVertex = new GetVertex();
        Set<AbstractVertex> myNetworkVertices = getVertex.execute(vertexParams, 100);
        MatrixFilter receivedMatrixFilter = inputSketch.matrixFilter;
        MatrixFilter myMatrixFilter = Kernel.sketches.iterator().next().matrixFilter;

        if (end.equals("src"))
        {
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
            Set<AbstractVertex> matchingVerticesUp = new HashSet<>();

            logger.log(Level.INFO, "endPathFragment - checking {0} srcVertices", ((Set<AbstractVertex>) inputSketch.objects.get("srcVertices")).size());

            for (AbstractVertex childVertex : (Set<AbstractVertex>) inputSketch.objects.get("srcVertices"))
            {
                BloomFilter currentBloomFilter = receivedMatrixFilter.get(childVertex);
                for (AbstractVertex vertexToCheck : myNetworkVertices)
                {
                    if (currentBloomFilter.contains(vertexToCheck))
                    {
                        matchingVerticesUp.add(vertexToCheck);
                    }
                }
            }

            // Get all paths between the matching network vertices and the required vertex id
            Object vertices[] = matchingVerticesUp.toArray();

            logger.log(Level.INFO, "endPathFragment - generating up paths between {0} matched vertices", vertices.length);

            for (int i = 0; i < vertices.length; i++)
            {
                String vertexId = ((AbstractVertex) vertices[i]).getAnnotation(PRIMARY_KEY);
                GetPaths getPaths = new GetPaths();
                Map<String, List<String>> pathParams = new HashMap<>();
                pathParams.put(CHILD_VERTEX_KEY, Arrays.asList(OPERATORS.EQUALS, childVertexId));
                pathParams.put(PARENT_VERTEX_KEY, Arrays.asList(OPERATORS.EQUALS, vertexId));
                pathParams.put("direction", Collections.singletonList(DIRECTION_ANCESTORS));
                pathParams.put("maxLength", Collections.singletonList("100"));
                Graph path = getPaths.execute(pathParams, 100);
                if (!path.edgeSet().isEmpty())
                {
                    result = Graph.union(result, path);

                    logger.log(Level.INFO, "endPathFragment - added path to result fragment");
                }
            }
        }
        else if (end.equals("dst"))
        {
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
            Set<AbstractVertex> matchingVerticesDown = new HashSet<>();

            logger.log(Level.INFO, "endPathFragment - checking {0} dstVertices", ((Set<AbstractVertex>) inputSketch.objects.get("dstVertices")).size());

            for (AbstractVertex vertexToCheck : myNetworkVertices)
            {
                BloomFilter currentBloomFilter = myMatrixFilter.get(vertexToCheck);
                for (AbstractVertex parentVertex : (Set<AbstractVertex>) inputSketch.objects.get("dstVertices"))
                {
                    if (currentBloomFilter.contains(parentVertex))
                    {
                        matchingVerticesDown.add(vertexToCheck);
                    }
                }
            }

            // Get all paths between the matching network vertices and the required vertex id
            Object vertices[] = matchingVerticesDown.toArray();

            logger.log(Level.INFO, "endPathFragment - generating down paths between {0} matched vertices", vertices.length);

            for (int i = 0; i < vertices.length; i++)
            {
                String vertexId = ((AbstractVertex) vertices[i]).getAnnotation(PRIMARY_KEY);
                GetPaths getPaths = new GetPaths();
                Map<String, List<String>> pathParams = new HashMap<>();
                pathParams.put(CHILD_VERTEX_KEY, Arrays.asList(OPERATORS.EQUALS, vertexId));
                pathParams.put(PARENT_VERTEX_KEY, Arrays.asList(OPERATORS.EQUALS, parentVertexId));
                pathParams.put("direction", Collections.singletonList(DIRECTION_ANCESTORS));
                pathParams.put("maxLength", Collections.singletonList("100"));
                Graph path = getPaths.execute(pathParams, 100);
                if (!path.edgeSet().isEmpty())
                {
                    result = Graph.union(result, path);

                    logger.log(Level.INFO, "endPathFragment - added path to result fragment");

                }
            }
        }

        logger.log(Level.INFO, "endPathFragment - returning {0} end fragment", end);

        return result;
    }


    /**
     * Method used to indicate to remote hosts that sketches need to be rebuilt.
     *
     * @param currentLevel Current level of notification.
     * @param maxLevel Maximum level to which notifications are sent.
     */
    public static void notifyRebuildSketches(int currentLevel, int maxLevel)
    {
        // If the last level is reached, stop notifying and begin propagation.
        if (currentLevel == 0)
        {
            logger.log(Level.INFO, "notifyRebuildSketch - reached level zero, propagating");
            propagateSketches(0, maxLevel);
            return;
        }

        logger.log(Level.INFO, "notifyRebuildSketch - sending rebuild notifications");

        //TODO: flush transactions somehow
        Set<AbstractVertex> upVertices = null;
        //TODO: lacking functionality support here
//        Set<AbstractVertex> upVertices = Kernel.storages.iterator().next().getEdges(null, "network:true", "type:Used").vertexSet();
        // If there are no incoming network vertices to send notifications to,
        // stop notifying and begin propagation.
        if (upVertices.isEmpty())
        {
            logger.log(Level.INFO, "notifyRebuildSketch - no more notification to send, beginning propagation");

            propagateSketches(currentLevel, maxLevel);
            return;
        }
        currentLevel--;
        for (AbstractVertex currentVertex : upVertices)
        {
            if (!currentVertex.getAnnotation("network").equalsIgnoreCase("true"))
            {
                continue;
            }
            try
            {
                // For each incoming network vertex, get the remote host and send
                // it the notify command.
                String remoteHost = currentVertex.getAnnotation("destination host");
                RebuildSketch currentElement = new RebuildSketch(currentLevel, maxLevel, remoteHost);
                Thread rebuildThread = new Thread(currentElement);
                rebuildThread.start();
            }
            catch (Exception exception)
            {
                logger.log(Level.SEVERE, null, exception);
            }
        }

        logger.log(Level.INFO, "notifyRebuildSketch - finished sending rebuild notifications");
    }


    /**
     * Method used to propagate sketches across the network.
     *
     * @param currentLevel The current level of propagation.
     * @param maxLevel The maximum level at which to propagate.
     */
    public  static void propagateSketches(int currentLevel, int maxLevel)
    {
        // First, rebuild the local sketch.
        rebuildLocalSketch();
        // If the maximum propagation level is reached, terminate propagation.
        if (currentLevel == maxLevel)
        {
            logger.log(Level.INFO, "propagateSketches - reached max level, terminating propagation");
            return;
        }

        logger.log(Level.INFO, "propagateSketches - propagating sketches");
        currentLevel++;
        //TODO: flush transactions somehow
        //TODO: lacking functionality support here
        Set<AbstractVertex> upVertices = null;
//        Set<AbstractVertex> upVertices = Kernel.storages.iterator().next().getEdges("network:true", null, "type:WasGeneratedBy").vertexSet();
        // Get all outgoing network vertices.
        for (AbstractVertex currentVertex : upVertices)
        {
            if (!currentVertex.getAnnotation("network").equalsIgnoreCase("true"))
            {
                continue;
            }
            try
            {
                // Get the remote host of each outgoing network vertex and trigger
                // the propagateSketch command on that SPADE instance.
                String remoteHost = currentVertex.getAnnotation("destination host");
                PropagateSketch currentElement = new PropagateSketch(currentLevel, maxLevel, remoteHost);
                Thread propagateThread = new Thread(currentElement);
                propagateThread.start();
            }
            catch (Exception exception)
            {
                logger.log(Level.SEVERE, null, exception);
            }
        }

        logger.log(Level.INFO, "propagateSketches - finished propagation");
    }

    /**
     * Method used to rebuild the local sketch.
     *
     */
    public static void rebuildLocalSketch()
    {
        // This method is used to rebuild the local sketch. All the 'used' edges
        // are added to the sketch before the 'wasgeneratedby' edges. This is
        // because of the pull-based architecture of the sketch which requests
        // remote sketches on a 'used' edge. The updated bloom filters are then
        // reflected in subsequent 'wasgeneratedby' edges. The delay (Thread.sleep)
        // is used to ensure that the remote sketches have been updated on the
        // incoming 'used' edges.

        logger.log(Level.INFO, "rebuildLocalSketch - rebuilding local sketch");

        //TODO: flush transactions somehow
        try
        {
            AbstractSketch mySketch = Kernel.sketches.iterator().next();
            //TODO: lacking functionality support here
            Set<AbstractEdge> usedEdges = null;
//            Set<AbstractEdge> usedEdges = Kernel.storages.iterator().next().getEdges(null, "network:true", "type:Used").edgeSet();
            for (AbstractEdge currentEdge : usedEdges)
            {
                mySketch.putEdge(currentEdge);
                Thread.sleep(200);
            }
            Thread.sleep(2000);
            //TODO: lacking functionality support here
            Set<AbstractEdge> wgbEdges = null;
//            Set<AbstractEdge> wgbEdges = Kernel.storages.iterator().next().getEdges("network:true", null, "type:WasGeneratedBy").edgeSet();
            for (AbstractEdge currentEdge : wgbEdges)
            {
                mySketch.putEdge(currentEdge);
                Thread.sleep(200);
            }
        }
        catch (Exception exception)
        {
            logger.log(Level.SEVERE, null, exception);
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
    public static Graph getPathInSketch(String line)
    {
        Graph result = new Graph();

        String source = line.split("\\s")[0];
        String srcHost = source.split(":")[0];
        String childVertexId = source.split(":")[1];

        String destination = line.split("\\s")[1];
        String dstHost = destination.split(":")[0];
        String parentVertexId = destination.split(":")[1];

        Set<AbstractVertex> sourceNetworkVertices = new HashSet<>();
        Set<AbstractVertex> destinationNetworkVertices = new HashSet<>();

        try
        {
            // Connect to destination host and get all the destination network vertices
            int port = Integer.parseInt(Settings.getProperty("remote_query_port"));
            SSLSocket remoteSocket = (SSLSocket) Kernel.sslSocketFactory.createSocket(dstHost, port);

            OutputStream outStream = remoteSocket.getOutputStream();
            InputStream inStream = remoteSocket.getInputStream();
            ObjectInputStream graphInputStream = new ObjectInputStream(inStream);
            PrintWriter remoteSocketOut = new PrintWriter(outStream, true);

            String expression = "query Neo4j vertices network:true";
            remoteSocketOut.println(expression);
            // Check whether the remote query server returned a graph in response
            Graph tempResultGraph = (Graph) graphInputStream.readObject();
            // Add those network vertices to the destination set that have a path
            // to the specified vertex
            for (AbstractVertex currentVertex : tempResultGraph.vertexSet())
            {
                expression = "query Neo4j paths " + currentVertex.getAnnotation(PRIMARY_KEY) + " " + parentVertexId + " 20";
                remoteSocketOut.println(expression);
                Graph currentGraph = (Graph) graphInputStream.readObject();
                if (!currentGraph.edgeSet().isEmpty()) {
                    destinationNetworkVertices.add(currentVertex);

                    logger.log(Level.INFO, "sketchPaths.1 - added vertex {0} to dstSet", currentVertex.getAnnotation(PRIMARY_KEY));
                }
            }

            remoteSocketOut.println("close");
            remoteSocketOut.close();
            graphInputStream.close();
            inStream.close();
            outStream.close();
            remoteSocket.close();

            logger.log(Level.INFO, "sketchPaths.1 - received data from {0}", dstHost);

            // Connect to the source host and get all source network vertices.
            port = Integer.parseInt(Settings.getProperty("remote_query_port"));
            remoteSocket = (SSLSocket) Kernel.sslSocketFactory.createSocket(srcHost, port);

            inStream = remoteSocket.getInputStream();
            outStream = remoteSocket.getOutputStream();
            graphInputStream = new ObjectInputStream(inStream);
            remoteSocketOut = new PrintWriter(outStream, true);

            expression = "query Neo4j vertices network:true";
            remoteSocketOut.println(expression);
            // Check whether the remote query server returned a graph in response
            tempResultGraph = (Graph) graphInputStream.readObject();
            for (AbstractVertex currentVertex : tempResultGraph.vertexSet())
            {
                expression = "query Neo4j paths " + childVertexId + " " + currentVertex.getAnnotation(PRIMARY_KEY) + " 20";
                remoteSocketOut.println(expression);
                Graph currentGraph = (Graph) graphInputStream.readObject();
                if (!currentGraph.edgeSet().isEmpty())
                {
                    sourceNetworkVertices.add(currentVertex);
                    logger.log(Level.INFO, "sketchPaths.1 - added vertex {0} to srcSet", currentVertex.getAnnotation(PRIMARY_KEY));
                }
            }

            remoteSocketOut.println("close");
            remoteSocketOut.close();
            graphInputStream.close();
            inStream.close();
            outStream.close();
            remoteSocket.close();

            logger.log(Level.INFO, "sketchPaths.2 - received data from {0}", srcHost);

            List<String> hostsToContact = new LinkedList<>();

            // Determine which hosts need to be contacted for the path fragments:
            //
            // 1) For each remote sketch that this host has cached, get a single
            //    bloom filter containing all the ancestors in that matrix filter.
            // 2) Check if this bloom filter contains any of the destination
            //    network vertices. If yes, this host needs to be contacted.
            for (Map.Entry<String, AbstractSketch> currentEntry : Kernel.remoteSketches.entrySet())
            {
                if (currentEntry.getKey().equals(srcHost) || currentEntry.getKey().equals(dstHost))
                {
                    continue;
                }
                BloomFilter ancestorFilter = currentEntry.getValue().matrixFilter.getAllBloomFilters();
                for (AbstractVertex parentVertex : destinationNetworkVertices)
                {
                    if (ancestorFilter.contains(parentVertex))
                    {
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
            List<Graph> graphResults = new LinkedList<>();
            List<Thread> pathThreads = new LinkedList<>();

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

            logger.log(Level.INFO, "sketchPaths.3 - contacting {0} hosts", hostsToContact.size());

            for (int i = 0; i < hostsToContact.size(); i++)
            {
                // Connect to each host and send it B's sketch
                PathFragment midFragment = new PathFragment(hostsToContact.get(i), "pathFragment_mid", graphResults);
                Thread midFragmentThread = new Thread(midFragment);
                pathThreads.add(midFragmentThread);
                midFragmentThread.start();
            }

            // Wait for all threads to finish getting the results.
            for (int i = 0; i < pathThreads.size(); i++)
            {
                pathThreads.get(i).join();
            }

            // Union all the results to get the final resulting graph.
            for (int i = 0; i < graphResults.size(); i++)
            {
                result = Graph.union(result, graphResults.get(i));
            }

        }
        catch (NumberFormatException | IOException | ClassNotFoundException | InterruptedException exception)
        {
            logger.log(Level.SEVERE, null, exception);
        }

        logger.log(Level.INFO, "sketchPaths.4 - finished building path from fragments");

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
    private static void transformNetworkBoundaries(Graph graph)
    {
        try
        {
            if (graph.transformed)
            {
                return;
            }
            else
            {
                graph.transformed = true;
            }

            List<AbstractVertex> networkVertices = new LinkedList<>();
            for (AbstractVertex currentVertex : graph.vertexSet())
            {
                //if (currentVertex.type().equalsIgnoreCase("Network")) {
                if (currentVertex.getAnnotation("network").equalsIgnoreCase("true"))
                {
                    networkVertices.add(currentVertex);
                }
            }

            for (int i = 0; i < networkVertices.size(); i++)
            {
                AbstractVertex vertex1 = networkVertices.get(i);
                String source_host = vertex1.getAnnotation("source host");
                String source_port = vertex1.getAnnotation("source port");
                String destination_host = vertex1.getAnnotation("destination host");
                String destination_port = vertex1.getAnnotation("destination port");
                for (int j = 0; j < networkVertices.size(); j++)
                {
                    AbstractVertex vertex2 = networkVertices.get(j);
                    if ((vertex2.getAnnotation("source host").equals(destination_host))
                            && (vertex2.getAnnotation("source port").equals(destination_port))
                            && (vertex2.getAnnotation("destination host").equals(source_host))
                            && (vertex2.getAnnotation("destination port").equals(source_port)))
                    {
                        Edge newEdge1 = new Edge((Vertex) vertex1, (Vertex) vertex2);
                        newEdge1.addAnnotation("type", "Network Boundary");
                        graph.putEdge(newEdge1);
                        Edge newEdge2 = new Edge((Vertex) vertex2, (Vertex) vertex1);
                        newEdge2.addAnnotation("type", "Network Boundary");
                        graph.putEdge(newEdge2);
                    }
                }
            }
        }
        catch (Exception exception)
        {
            logger.log(Level.WARNING, null, exception);
        }
    }


    static class RebuildSketch implements Runnable
    {

        // An object of this class is instantiated when the rebuildSketches method is
        // called. This is done so as to allow the rebuild sketch notifications to be
        // sent in a multi-threaded manner.
        int currentLevel;
        int maxLevel;
        String remoteHost;

        // The constructor is used to store the configuration for this method for
        // the current invocation.
        RebuildSketch(int current, int max, String host)
        {
            currentLevel = current;
            maxLevel = max;
            remoteHost = host;
        }

        @Override
        public void run()
        {
            try
            {
                int port = Integer.parseInt(Settings.getProperty("remote_sketch_port"));
                SSLSocket remoteSocket = (SSLSocket) Kernel.sslSocketFactory.createSocket(remoteHost, port);

                OutputStream outStream = remoteSocket.getOutputStream();
                InputStream inStream = remoteSocket.getInputStream();
                ObjectOutputStream objectOutputStream = new ObjectOutputStream(outStream);
                ObjectInputStream objectInputStream = new ObjectInputStream(inStream);


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
            }
            catch (NumberFormatException | IOException exception)
            {
                Logger.getLogger(RebuildSketch.class.getName()).log(Level.SEVERE, null, exception);
            }
        }
    }

    static class PropagateSketch implements Runnable
    {

        // An object of this class is instantiated when the propagateSketches method is
        // called. This is done so as to allow the sketch propagation to be performed
        // in a multi-threaded manner.
        int currentLevel;
        int maxLevel;
        String remoteHost;

        // The constructor is used to store the configuration for this method for
        // the current invocation.
        PropagateSketch(int current, int max, String host)
        {
            currentLevel = current;
            maxLevel = max;
            remoteHost = host;
        }

        @Override
        public void run()
        {
            try
            {
                int port = Integer.parseInt(Settings.getProperty("remote_sketch_port"));
                SSLSocket remoteSocket = (SSLSocket) Kernel.sslSocketFactory.createSocket(remoteHost, port);

                OutputStream outStream = remoteSocket.getOutputStream();
                InputStream inStream = remoteSocket.getInputStream();
                ObjectOutputStream objectOutputStream = new ObjectOutputStream(outStream);
                ObjectInputStream objectInputStream = new ObjectInputStream(inStream);


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
            }
            catch (NumberFormatException | IOException exception)
            {
                Logger.getLogger(PropagateSketch.class.getName()).log(Level.SEVERE, null, exception);
            }
        }
    }

    static class PathFragment implements Runnable
    {

        // An object of this class in instantiated when a path fragment is to be
        // fetched from a remote SPADE instance. This is done so as to allow all
        // the path fragments to be fetched in a multi-threaded manner.
        String remoteHost;
        String pathFragment;
        List<Graph> graphResults;

        PathFragment(String host, String fragment, List<Graph> results)
        {
            remoteHost = host;
            pathFragment = fragment;
            graphResults = results;
        }

        @Override
        public void run()
        {
            try
            {
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


                graphResults.add(tempResultGraph);
            }
            catch (NumberFormatException | IOException | ClassNotFoundException exception)
            {
                Logger.getLogger(PathFragment.class.getName()).log(Level.SEVERE, null, exception);
            }
        }
    }
}