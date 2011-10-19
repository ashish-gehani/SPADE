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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedList;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Iterator;
import java.util.Vector;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Kernel {

    public static final String SOURCE_REPORTER = "source_reporter";
    public static Map<String, AbstractSketch> remoteSketches;
    public static final int LOCAL_CONTROL_PORT = 19999;
    public static final int LOCAL_QUERY_PORT = 19998;
    public static final int REMOTE_QUERY_PORT = 29999;
    public static final int REMOTE_SKETCH_PORT = 29998;
    public static final int TIMEOUT = 15000;
    private static final String configFile = "../cfg/spade.config";
    private static final String queryPipeInputPath = "../dev/queryPipeIn";
    private static final String controlPipeInputPath = "../dev/controlPipeIn";
    private static final String controlPipeOutputPath = "../dev/controlPipeOut";
    private static final String logFilenamePattern = "MM.dd.yyyy-H.mm.ss";
    private static final String NO_ARGUMENTS = "no arguments";
    private static final int BATCH_BUFFER_ELEMENTS = 100;
    private static final int MAIN_THREAD_SLEEP_DELAY = 3;
    private static final int COMMAND_THREAD_SLEEP_DELAY = 200;
    private static final int REMOVE_WAIT_DELAY = 300;
    private static final int FIRST_TRANSFORMER = 0;
    private static final int FIRST_FILTER = 0;
    private static Set<AbstractReporter> reporters;
    private static Set<AbstractStorage> storages;
    private static Set<AbstractReporter> removereporters;
    private static Set<AbstractStorage> removestorages;
    private static List<AbstractFilter> filters;
    private static List<AbstractFilter> transformers;
    private static Map<AbstractReporter, Buffer> buffers;
    public static Set<AbstractSketch> sketches;
    private static volatile boolean shutdown;
    private static volatile boolean flushTransactions;

    public static void main(String args[]) {

        try {
            // Configuring the global exception logger
            String logFilename = new java.text.SimpleDateFormat(logFilenamePattern).format(new java.util.Date(System.currentTimeMillis()));
            Handler logFileHandler = new FileHandler("../log/" + logFilename + ".log");
            Logger.getLogger("").addHandler(logFileHandler);
        } catch (Exception exception) {
            System.out.println("Error initializing exception logger");
        }

        // Basic initialization
        reporters = Collections.synchronizedSet(new HashSet<AbstractReporter>());
        storages = Collections.synchronizedSet(new HashSet<AbstractStorage>());
        removereporters = Collections.synchronizedSet(new HashSet<AbstractReporter>());
        removestorages = Collections.synchronizedSet(new HashSet<AbstractStorage>());
        transformers = Collections.synchronizedList(new LinkedList<AbstractFilter>());
        filters = Collections.synchronizedList(new LinkedList<AbstractFilter>());
        buffers = Collections.synchronizedMap(new HashMap<AbstractReporter, Buffer>());
        sketches = Collections.synchronizedSet(new HashSet<AbstractSketch>());
        remoteSketches = Collections.synchronizedMap(new HashMap<String, AbstractSketch>());
        shutdown = false;
        flushTransactions = false;

        // Initialize the SketchManager and the final commit filter.
        // The FinalCommitFilter acts as a terminator for the filter list
        // and also maintains a pointer to the list of active storages to which
        // the provenance data is finally passed. It also has a reference to
        // the SketchManager and triggers its putVertex() and putEdge() methods
        FinalCommitFilter commitFilter = new FinalCommitFilter();
        commitFilter.storages = storages;
        commitFilter.sketches = sketches;
        filters.add(commitFilter);

        // The final transformer is used to send vertex and edge objects to
        // their corresponding result Graph.
        FinalTransformer finalTransformer = new FinalTransformer();
        transformers.add(finalTransformer);


        // Initialize the main thread. This thread performs critical provenance-related
        // work inside SPADE. It extracts provenance objects (vertices, edges) from the
        // buffers, adds the source_reporter annotation to each object which is class name
        // of the reporter, and then sends these objects to the filter list.
        // This thread is also used for cleanly removing reporters and storages (through
        // the control commands and also when shutting down). This is done by ensuring that
        // once a reporter is marked for removal, the provenance objects from its buffer are
        // completely flushed.
        Runnable mainThread = new Runnable() {

            public void run() {
                try {
                    while (true) {
                        if (shutdown) {
                            // The shutdown process is also partially handled by this thread. On
                            // shutdown, all reporters are marked for removal so that their buffers
                            // are cleanly flushed and no data is lost. When a buffer becomes empty,
                            // it is removed along with its corresponding reporter. When all buffers
                            // become empty, this thread terminates.
                            for (Iterator iterator = buffers.entrySet().iterator(); iterator.hasNext();) {
                                Map.Entry currentEntry = (Map.Entry) iterator.next();
                                Buffer tempBuffer = (Buffer) currentEntry.getValue();
                                if (tempBuffer.isEmpty()) {
                                    iterator.remove();
                                }
                            }
                            if (buffers.isEmpty()) {
                                shutdown();
                                break;
                            }
                        }
                        if (flushTransactions) {
                            // Flushing of transactions is also handled by this thread to ensure that
                            // there are no errors/problems when using storages that are sensitive to
                            // thread-context for their transactions. For example, this is true for
                            // the embedded neo4j graph database.
                            for (AbstractStorage currentStorage : storages) {
                                currentStorage.flushTransactions();
                            }
                            flushTransactions = false;
                        }
                        if (!removestorages.isEmpty()) {
                            // Check if a storage is marked for removal. If it is, shut it down and
                            // remove it from the list.
                            for (Iterator iterator = removestorages.iterator(); iterator.hasNext();) {
                                AbstractStorage currentStorage = (AbstractStorage) iterator.next();
                                currentStorage.shutdown();
                                iterator.remove();
                            }
                        }
                        for (Iterator iterator = buffers.entrySet().iterator(); iterator.hasNext();) {
                            // This loop performs the actual task of committing provenance data to
                            // the storages. Each reporter is selected and the nested loop is used to
                            // extract buffer elements in a batch manner for increased efficiency.
                            // The elements are then passed to the filter list.
                            Map.Entry currentEntry = (Map.Entry) iterator.next();
                            AbstractReporter reporter = (AbstractReporter) currentEntry.getKey();
                            for (int i = 0; i < BATCH_BUFFER_ELEMENTS; i++) {
                                Object bufferelement = ((Buffer) currentEntry.getValue()).getBufferElement();
                                if (bufferelement instanceof AbstractVertex) {
                                    AbstractVertex tempVertex = (AbstractVertex) bufferelement;
                                    tempVertex.addAnnotation(SOURCE_REPORTER, reporter.getClass().getName().split("\\.")[2]);
                                    ((AbstractFilter) filters.get(FIRST_FILTER)).putVertex(tempVertex);
                                } else if (bufferelement instanceof AbstractEdge) {
                                    AbstractEdge tempEdge = (AbstractEdge) bufferelement;
                                    tempEdge.addAnnotation(SOURCE_REPORTER, reporter.getClass().getName().split("\\.")[2]);
                                    ((AbstractFilter) filters.get(FIRST_FILTER)).putEdge((AbstractEdge) bufferelement);
                                } else if (bufferelement == null) {
                                    if (removereporters.contains(reporter)) {
                                        removereporters.remove(reporter);
                                        iterator.remove();
                                    }
                                    break;
                                }
                            }
                        }
                        Thread.sleep(MAIN_THREAD_SLEEP_DELAY);
                    }
                } catch (Exception exception) {
                    Logger.getLogger(Kernel.class.getName()).log(Level.SEVERE, null, exception);
                }
            }
        };
        new Thread(mainThread, "mainThread").start();


        // This thread creates the input and output pipes used for control (and also used
        // by the control client). The exit value is used to determine if the pipes were
        // successfully created. The input pipe (to which commands are issued) is read in
        // a loop and the commands are processed.
        Runnable controlThread = new Runnable() {

            public void run() {
                try {
                    configCommand("config load " + configFile, NullStream.out);
                    ServerSocket serverSocket = new ServerSocket(LOCAL_CONTROL_PORT);
                    while (!shutdown) {
                        Socket controlSocket = serverSocket.accept();
                        LocalControlConnection thisConnection = new LocalControlConnection(controlSocket);
                        Thread connectionThread = new Thread(thisConnection);
                        connectionThread.start();
                    }
                } catch (Exception exception) {
                    Logger.getLogger(Kernel.class.getName()).log(Level.SEVERE, null, exception);
                }
            }
        };
        new Thread(controlThread, "controlThread").start();


        // Construct the query pipe. The exit value is used to determine if the
        // query pipe was successfully created.
        Runnable queryThread = new Runnable() {

            public void run() {
                try {
                    ServerSocket serverSocket = new ServerSocket(LOCAL_QUERY_PORT);
                    while (!shutdown) {
                        Socket querySocket = serverSocket.accept();
                        LocalQueryConnection thisConnection = new LocalQueryConnection(querySocket);
                        Thread connectionThread = new Thread(thisConnection);
                        connectionThread.start();
                    }
                } catch (Exception exception) {
                    Logger.getLogger(Kernel.class.getName()).log(Level.SEVERE, null, exception);
                }
            }
        };
        new Thread(queryThread, "queryThread").start();

        // This thread creates a server socket for remote querying. When a query connection
        // is established, another new thread is created for that connection object. The
        // remote query server is therefore a multithreaded server.
        Runnable remoteThread = new Runnable() {

            public void run() {
                try {
                    ServerSocket serverSocket = new ServerSocket(REMOTE_QUERY_PORT);
                    while (!shutdown) {
                        Socket clientSocket = serverSocket.accept();
                        QueryConnection thisConnection = new QueryConnection(clientSocket);
                        Thread connectionThread = new Thread(thisConnection);
                        connectionThread.start();
                    }
                } catch (Exception exception) {
                    Logger.getLogger(Kernel.class.getName()).log(Level.SEVERE, null, exception);
                }
            }
        };
        new Thread(remoteThread, "remoteThread").start();

        // This thread creates a server socket for remote sketches. When a sketch connection
        // is established, another new thread is created for that connection object. The
        // remote sketch server is therefore a multithreaded server.
        Runnable sketchThread = new Runnable() {

            public void run() {
                try {
                    ServerSocket serverSocket = new ServerSocket(REMOTE_SKETCH_PORT);
                    while (!shutdown) {
                        Socket clientSocket = serverSocket.accept();
                        SketchConnection thisConnection = new SketchConnection(clientSocket);
                        Thread connectionThread = new Thread(thisConnection);
                        connectionThread.start();
                    }
                } catch (Exception exception) {
                    Logger.getLogger(Kernel.class.getName()).log(Level.SEVERE, null, exception);
                }
            }
        };
        new Thread(sketchThread, "sketchThread").start();

    }

    // The following two methods are called by the Graph object when adding vertices
    // and edges to the result graph. Transformers are technically the same as filters
    // and are used to modify/transform data as it is entered into a Graph object.    
    public static void sendToTransformers(AbstractVertex vertex) {
        ((AbstractFilter) transformers.get(FIRST_TRANSFORMER)).putVertex(vertex);
    }

    public static void sendToTransformers(AbstractEdge edge) {
        ((AbstractFilter) transformers.get(FIRST_TRANSFORMER)).putEdge(edge);
    }

    // All command strings are passed to this function which subsequently calls the
    // correct method based on the command. Each command is determined by the first
    // token in the string.
    public static boolean executeCommand(String line, PrintStream outputStream) {
        String command = line.split("\\s+")[0];
        if (command.equalsIgnoreCase("shutdown")) {
            // On shutdown, save the current configuration in the default configuration
            // file.
            configCommand("config save " + configFile, outputStream);
            for (AbstractReporter reporter : reporters) {
                // Shut down all reporters. After
                // this, their buffers are flushed and then the storages are shut down.
                reporter.shutdown();
            }
            shutdown = true;
            return false;
        } else if (command.equalsIgnoreCase("add")) {
            addCommand(line, outputStream);
            return true;
        } else if (command.equalsIgnoreCase("list")) {
            listCommand(line, outputStream);
            return true;
        } else if (command.equalsIgnoreCase("remove")) {
            removeCommand(line, outputStream);
            return true;
        } else if (command.equalsIgnoreCase("query")) {
            queryCommand(line, outputStream);
            return true;
        } else if (command.equalsIgnoreCase("config")) {
            configCommand(line, outputStream);
            return true;
        } else {
            showCommands(outputStream);
            return true;
        }
    }

    // The configCommand is used to load or save the current SPADE configuration
    // from/to a file.
    public static void configCommand(String line, PrintStream outputStream) {
        String[] tokens = line.split("\\s+");
        try {
            // Determine whether the command is a load or a save.
            if (tokens[1].equalsIgnoreCase("load")) {
                BufferedReader configReader = new BufferedReader(new FileReader(tokens[2]));
                String configLine;
                while ((configLine = configReader.readLine()) != null) {
                    addCommand("add " + configLine, outputStream);
                }
                outputStream.println("Finished loading configuration file");
            } else if (tokens[1].equalsIgnoreCase("save")) {
                // If the command is save, then write the current configuration.
                outputStream.print("Saving configuration... ");
                FileWriter configWriter = new FileWriter(tokens[2], false);
                for (int i = 0; i < filters.size() - 1; i++) {
                    configWriter.write("filter " + filters.get(i).getClass().getName().split("\\.")[2] + " " + i + "\n");
                }
                for (AbstractSketch sketch : sketches) {
                    configWriter.write("sketch " + sketch.getClass().getName().split("\\.")[2] + "\n");
                }
                for (AbstractStorage storage : storages) {
                    String arguments = storage.arguments;
                    configWriter.write("storage " + storage.getClass().getName().split("\\.")[2] + " " + arguments + "\n");
                }
                for (AbstractReporter reporter : reporters) {
                    String arguments = reporter.arguments;
                    configWriter.write("reporter " + reporter.getClass().getName().split("\\.")[2] + " " + arguments + "\n");
                }
                configWriter.close();
                outputStream.println("done");
            } else {
                throw new Exception();
            }
        } catch (Exception exception) {
            outputStream.println("Usage: config load|save <filename>");
        }
    }

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
        Graph myNetworkVertices = query("query Neo4j vertices type:Network", false);
        MatrixFilter receivedMatrixFilter = inputSketch.matrixFilter;
        MatrixFilter myMatrixFilter = sketches.iterator().next().matrixFilter;

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
                Graph path = query("query Neo4j paths " + srcVertexId + " " + vertexId + " 20", false);
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
                Graph path = query("query Neo4j paths " + vertexId + " " + dstVertexId + " 20", false);
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
        Graph myNetworkVertices = query("query Neo4j vertices type:Network", false);
        Set<AbstractVertex> matchingVerticesDown = new HashSet<AbstractVertex>();
        Set<AbstractVertex> matchingVerticesUp = new HashSet<AbstractVertex>();
        MatrixFilter receivedMatrixFilter = inputSketch.matrixFilter;
        MatrixFilter myMatrixFilter = sketches.iterator().next().matrixFilter;

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
                Graph path = query("query Neo4j paths " + srcId + " " + dstId + " 20", false);
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
        query(null, false); // To flush transactions
        try {
            AbstractSketch mySketch = sketches.iterator().next();
            Set<AbstractEdge> usedEdges = storages.iterator().next().getEdges(null, "type:Network", "type:Used").edgeSet();
            for (AbstractEdge currentEdge : usedEdges) {
                mySketch.putEdge(currentEdge);
                Thread.sleep(200);
            }
            Thread.sleep(2000);
            Set<AbstractEdge> wgbEdges = storages.iterator().next().getEdges("type:Network", null, "type:WasGeneratedBy").edgeSet();
            for (AbstractEdge currentEdge : wgbEdges) {
                mySketch.putEdge(currentEdge);
                Thread.sleep(200);
            }
        } catch (Exception exception) {
            Logger.getLogger(Kernel.class.getName()).log(Level.SEVERE, null, exception);
        }
    }

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
        query(null, false); // To flush transactions
        Set<AbstractVertex> upVertices = storages.iterator().next().getEdges("type:Network", null, "type:WasGeneratedBy").vertexSet();
        // Get all outgoing network vertices.
        for (AbstractVertex currentVertex : upVertices) {
            if (!currentVertex.type().equalsIgnoreCase("Network")) {
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
        query(null, false); // To flush transactions
        Set<AbstractVertex> upVertices = storages.iterator().next().getEdges(null, "type:Network", "type:Used").vertexSet();
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
            if (!currentVertex.type().equalsIgnoreCase("Network")) {
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

    public static Graph getPathInSketch(String line) {
        // This method is called when a path query is executed using sketches.
        Graph result = new Graph();

        // line has format "sourceHost:vertexId destinationHost:vertexId"
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
            remoteSocket.connect(sockaddr, Kernel.TIMEOUT);
            OutputStream outStream = remoteSocket.getOutputStream();
            InputStream inStream = remoteSocket.getInputStream();
            //ObjectOutputStream graphOutputStream = new ObjectOutputStream(outStream);
            ObjectInputStream graphInputStream = new ObjectInputStream(inStream);
            PrintWriter remoteSocketOut = new PrintWriter(outStream, true);

            String expression = "query Neo4j vertices type:Network";
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
            remoteSocket.connect(sockaddr, Kernel.TIMEOUT);
            inStream = remoteSocket.getInputStream();
            outStream = remoteSocket.getOutputStream();
            //graphOutputStream = new ObjectOutputStream(outStream);
            graphInputStream = new ObjectInputStream(inStream);
            remoteSocketOut = new PrintWriter(outStream, true);

            expression = "query Neo4j vertices type:Network";
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

            for (Map.Entry<String, AbstractSketch> currentEntry : remoteSketches.entrySet()) {
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

            AbstractSketch mySketch = sketches.iterator().next();
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
            for (int i=0; i<pathThreads.size(); i++) {
                pathThreads.get(i).join();
            }
            
            // Union all the results to get the final resulting graph.
            for (int i=0; i<graphResults.size(); i++) {
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

    // This method is used to call query methods on the desired storage. The
    // transactions are also flushed to ensure that the data in the storages is
    // consistent and updated with all the data received by SPADE up to this point.
    public static Graph query(String line, boolean resolveRemote) {
        Graph resultGraph = null;
        flushTransactions = true;
        
        long begintime = 0, endtime = 0;
        
        while (flushTransactions) {
            try {
                // wait for other thread to flush transactions
                Thread.sleep(MAIN_THREAD_SLEEP_DELAY);
            } catch (Exception exception) {
                Logger.getLogger(Kernel.class.getName()).log(Level.SEVERE, null, exception);
            }
        }
        if ((line == null) || (storages.isEmpty())) {
            return null;
        }
        String[] tokens = line.split("\\s+");
        for (AbstractStorage storage : storages) {
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
                        remoteSocket.connect(sockaddr, Kernel.TIMEOUT);
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
                        tempSketches.putAll(remoteSketches);
                        tempSketches.put("localhost", sketches.iterator().next());
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
                            remoteSocket.connect(sockaddr, Kernel.TIMEOUT);
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
                            remoteSocket.connect(sockaddr, Kernel.TIMEOUT);
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

    public static void transformNetworkBoundaries(Graph graph) {
        // Apply the network vertex transform on the graph since
        // the network vertices between a network boundary are
        // symmetric but not identical.
        
        if (graph.transformed) {
            return;
        } else {
            graph.transformed = true;
        }

        List<AbstractVertex> networkVertices = new LinkedList<AbstractVertex>();
        for (AbstractVertex currentVertex : graph.vertexSet()) {
            if (currentVertex.type().equalsIgnoreCase("Network")) {
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
    }

    public static Graph queryNetworkVertex(AbstractVertex networkVertex, int depth, String direction, String terminatingExpression) {
        Graph resultGraph = null;

        try {
            // Establish a connection to the remote host
            SocketAddress sockaddr = new InetSocketAddress(networkVertex.getAnnotation("destination host"), Kernel.REMOTE_QUERY_PORT);
            Socket remoteSocket = new Socket();
            remoteSocket.connect(sockaddr, Kernel.TIMEOUT);
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

    public static String convertVertexToString(AbstractVertex vertex) {
        String vertexString = "";
        for (Map.Entry currentEntry : vertex.getAnnotations().entrySet()) {
            String key = (String) currentEntry.getKey();
            String value = (String) currentEntry.getValue();
            vertexString = vertexString + key + ":" + value + "|";
        }
        vertexString = vertexString.substring(0, vertexString.length() - 1);
        return vertexString;
    }

    // Call the main query method.
    public static void queryCommand(String line, PrintStream outputStream) {
        Graph resultGraph = query(line, false);
        if (resultGraph != null) {
            String[] tokens = line.split("\\s+");
            String outputFile = tokens[tokens.length - 1];
            if ((tokens[2].equalsIgnoreCase("vertices")) || (tokens[2].equalsIgnoreCase("remotevertices"))) {
                for (AbstractVertex tempVertex : resultGraph.vertexSet()) {
                    outputStream.println("[" + convertVertexToString(tempVertex) + "]");
                }
                outputStream.println(resultGraph.vertexSet().size() + " vertices found");
            } else if (tokens[2].equalsIgnoreCase("lineage")) {
                resultGraph.exportDOT(outputFile);
                outputStream.println("Exported graph to " + outputFile);
            } else if (tokens[2].equalsIgnoreCase("paths")) {
                resultGraph.exportDOT(outputFile);
                outputStream.println("Exported graph to " + outputFile);
            } else if (tokens[2].equalsIgnoreCase("remotepaths")) {
                resultGraph.exportDOT(outputFile);
                outputStream.println("Exported graph to " + outputFile);
            } else if (tokens[2].equalsIgnoreCase("sketchpaths")) {
                resultGraph.exportDOT(outputFile);
                outputStream.println("Exported graph to " + outputFile);
            }
        } else {
            outputStream.println("Error: Please check query expression");
        }
    }

    // Method to display control commands to the output stream. The control and query
    // commands are displayed using separate methods since these commands are issued
    // from different shells.
    public static void showCommands(PrintStream outputStream) {
        outputStream.println("Available commands:");
        outputStream.println("       add reporter|storage <class name> <initialization arguments>");
        outputStream.println("       add filter <class name> <index>");
        outputStream.println("       add sketch <class name> <storage class name>");
        outputStream.println("       remove reporter|storage|sketch <class name>");
        outputStream.println("       remove filter <index>");
        outputStream.println("       list reporters|storages|filters|sketches|all");
        outputStream.println("       config load|save <filename>");
        outputStream.println("       exit");
        outputStream.println("       shutdown");
    }

    // Method to display query commands to the given output stream.
    public static void showQueryCommands(PrintStream outputStream) {
        outputStream.println("Available commands:");
        outputStream.println("       query <class name> vertices <expression>");
        outputStream.println("       query <class name> lineage <vertex id> <depth> <direction> <terminating expression> <output file>");
        outputStream.println("       query <class name> paths <source vertex id> <destination vertex id> <max length> <output file>");
        outputStream.println("       exit");
    }

    public static void addCommand(String line, PrintStream outputStream) {
        String[] tokens = line.split("\\s+", 4);
        try {
            if (tokens[1].equalsIgnoreCase("reporter")) {
                String classname = tokens[2];
                String arguments = (tokens.length == 3) ? null : tokens[3];
                try {
                    // Get the reporter by classname and create a new instance.
                    AbstractReporter reporter = (AbstractReporter) Class.forName("spade.reporter." + classname).newInstance();
                    outputStream.print("Adding reporter " + classname + "... ");
                    // Create a new buffer and allocate it to this reporter.
                    Buffer buffer = new Buffer();
                    reporter.setBuffer(buffer);
                    if (reporter.launch(arguments)) {
                        // The launch() method must return true to indicate a successful launch.
                        // On true, the reporter is added to the reporters set and the buffer
                        // is put into a HashMap keyed by the reporter (this is used by the main
                        // SPADE thread to extract buffer elements).
                        reporter.arguments = (arguments == null) ? NO_ARGUMENTS : arguments;
                        buffers.put(reporter, buffer);
                        reporters.add(reporter);
                        outputStream.println("done");
                    } else {
                        outputStream.println("failed");
                    }
                } catch (Exception addReporterException) {
                    outputStream.println("Error: Unable to add reporter " + classname + " - please check class name and arguments");
                    return;
                }
            } else if (tokens[1].equalsIgnoreCase("storage")) {
                String classname = tokens[2];
                String arguments = (tokens.length == 3) ? null : tokens[3];
                try {
                    // Get the storage by classname and create a new instance.
                    AbstractStorage storage = (AbstractStorage) Class.forName("spade.storage." + classname).newInstance();
                    outputStream.print("Adding storage " + classname + "... ");
                    if (storage.initialize(arguments)) {
                        // The initialize() method must return true to indicate successful startup.
                        // On true, the storage is added to the storages set.
                        storage.arguments = (arguments == null) ? NO_ARGUMENTS : arguments;
                        storage.vertexCount = 0;
                        storage.edgeCount = 0;
                        storages.add(storage);
                        outputStream.println("done");
                    } else {
                        outputStream.println("failed");
                    }
                } catch (Exception addStorageException) {
                    outputStream.println("Error: Unable to add storage " + classname + " - please check class name and arguments");
                    return;
                }
            } else if (tokens[1].equalsIgnoreCase("filter")) {
                String classname = tokens[2];
                String arguments = tokens[3];
                try {
                    // Get the filter by classname and create a new instance.
                    AbstractFilter filter = (AbstractFilter) Class.forName("spade.filter." + classname).newInstance();
                    // The argument is the index at which the filter is to be inserted.
                    int index = Integer.parseInt(arguments);
                    if (index >= filters.size()) {
                        throw new Exception();
                    }
                    // Set the next filter of this newly added filter.
                    filter.setNextFilter((AbstractFilter) filters.get(index));
                    if (index > 0) {
                        // If the newly added filter is not the first in the list, then
                        // then configure the previous filter in the list to point to this
                        // newly added filter as its next.
                        ((AbstractFilter) filters.get(index - 1)).setNextFilter(filter);
                    }
                    outputStream.print("Adding filter " + classname + "... ");
                    // Add filter to the list.
                    filters.add(index, filter);
                    outputStream.println("done");
                } catch (Exception addFilterException) {
                    outputStream.println("Error: Unable to add filter " + classname + " - please check class name and index");
                    return;
                }
            } else if (tokens[1].equalsIgnoreCase("transformer")) {
                String classname = tokens[2];
                String arguments = tokens[3];
                try {
                    // Get the transformer by classname and create a new instance.
                    AbstractFilter filter = (AbstractFilter) Class.forName("spade.filter." + classname).newInstance();
                    // The argument is the index at which the transformer is to be inserted.
                    int index = Integer.parseInt(arguments);
                    if (index >= transformers.size()) {
                        throw new Exception();
                    }
                    // Set the next transformer of this newly added transformer.
                    filter.setNextFilter((AbstractFilter) transformers.get(index));
                    if (index > 0) {
                        // If the newly added transformer is not the first in the list, then
                        // then configure the previous transformer in the list to point to this
                        // newly added transformer as its next.
                        ((AbstractFilter) transformers.get(index - 1)).setNextFilter(filter);
                    }
                    outputStream.print("Adding transformer " + classname + "... ");
                    // Add transformer to the list of transformers.
                    transformers.add(index, filter);
                    outputStream.println("done");
                } catch (Exception addFilterException) {
                    outputStream.println("Error: Unable to add transformer " + classname + " - please check class name and index");
                    return;
                }
            } else if (tokens[1].equalsIgnoreCase("sketch")) {
                String classname = tokens[2];
                try {
                    // Get the sketch by classname and create a new instance.
                    AbstractSketch sketch = (AbstractSketch) Class.forName("spade.sketch." + classname).newInstance();
                    outputStream.print("Adding sketch " + classname + "... ");
                    sketches.add(sketch);
                    outputStream.println("done");
                } catch (Exception addSketchException) {
                    outputStream.println("Error: Unable to add sketch " + classname + " - please check class name and storage name");
                    return;
                }
            } else {
                throw new Exception();
            }
        } catch (Exception addCommandException) {
            outputStream.println("Usage: add reporter|storage <class name> <initialization arguments>");
            outputStream.println("       add filter|transformer <class name> <index>");
            outputStream.println("       add sketch <class name>");
        }
    }

    public static void listCommand(String line, PrintStream outputStream) {
        String[] tokens = line.split("\\s+");
        try {
            if (tokens[1].equalsIgnoreCase("reporters")) {
                if (reporters.isEmpty()) {
                    // Nothing to list if the set of reporters is empty.
                    outputStream.println("No reporters added");
                    return;
                }
                outputStream.println(reporters.size() + " reporter(s) added:");
                int count = 1;
                for (AbstractReporter reporter : reporters) {
                    // Print the names and arguments of all reporters.
                    String arguments = reporter.arguments;
                    outputStream.println("\t" + count + ". " + reporter.getClass().getName().split("\\.")[2] + " (" + arguments + ")");
                    count++;
                }
            } else if (tokens[1].equalsIgnoreCase("storages")) {
                if (storages.isEmpty()) {
                    // Nothing to list if the set of storages is empty.
                    outputStream.println("No storages added");
                    return;
                }
                outputStream.println(storages.size() + " storage(s) added:");
                int count = 1;
                for (AbstractStorage storage : storages) {
                    // Print the names and arguments of all storages.
                    String arguments = storage.arguments;
                    outputStream.println("\t" + count + ". " + storage.getClass().getName().split("\\.")[2] + " (" + arguments + ")");
                    count++;
                }
            } else if (tokens[1].equalsIgnoreCase("filters")) {
                if (filters.size() == 1) {
                    // The size of the filters list will always be at least 1 because
                    // of the FinalCommitFilter. The user is not made aware of the
                    // presence of this filter and it is only used for committing
                    // provenance data to the storages. Therefore, there is nothing
                    // to list if the size of the filters list is 1.
                    outputStream.println("No filters added");
                    return;
                }
                outputStream.println((filters.size() - 1) + " filter(s) added:");
                for (int i = 0; i < filters.size() - 1; i++) {
                    // Loop through the filters list, printing their names (except
                    // for the last FinalCommitFilter).
                    outputStream.println("\t" + (i + 1) + ". " + filters.get(i).getClass().getName().split("\\.")[2]);
                }
            } else if (tokens[1].equalsIgnoreCase("transformers")) {
                if (transformers.size() == 1) {
                    // The size of the transformers list will always be at least 1 because
                    // of the FinalTransformer. The user is not made aware of the
                    // presence of this filter and it is only used for committing
                    // provenance data to the result Graph. Therefore, there is nothing
                    // to list if the size of the filters list is 1.
                    outputStream.println("No transformers added");
                    return;
                }
                outputStream.println((transformers.size() - 1) + " transformer(s) added:");
                for (int i = 0; i < transformers.size() - 1; i++) {
                    // Loop through the transformers list, printing their names (except
                    // for the last FinalTransformer).
                    outputStream.println("\t" + (i + 1) + ". " + transformers.get(i).getClass().getName().split("\\.")[2]);
                }
            } else if (tokens[1].equalsIgnoreCase("sketches")) {
                if (sketches.isEmpty()) {
                    // Nothing to list if the set of sketches is empty.
                    outputStream.println("No sketches added");
                    return;
                }
                outputStream.println(sketches.size() + " sketch(es) added:");
                int count = 1;
                for (AbstractSketch sketch : sketches) {
                    // Print the names of all sketches.
                    outputStream.println("\t" + count + ". " + sketch.getClass().getName().split("\\.")[2]);
                    count++;
                }
            } else if (tokens[1].equalsIgnoreCase("all")) {
                listCommand("list reporters", outputStream);
                listCommand("list storages", outputStream);
                listCommand("list filters", outputStream);
                listCommand("list transformers", outputStream);
                listCommand("list sketches", outputStream);
            } else {
                throw new Exception();
            }
        } catch (Exception listCommandException) {
            outputStream.println("Usage: list reporters|storages|filters|transformers|sketches|all");
        }
    }

    public static void removeCommand(String line, PrintStream outputStream) {
        String[] tokens = line.split("\\s+");
        try {
            if (tokens[1].equalsIgnoreCase("reporter")) {
                boolean found = false;
                for (Iterator iterator = reporters.iterator(); iterator.hasNext();) {
                    AbstractReporter reporter = (AbstractReporter) iterator.next();
                    // Search for the given reporter in the set of reporters.
                    if (reporter.getClass().getName().equals("spade.reporter." + tokens[2])) {
                        // Mark the reporter for removal by adding it to the removereporters set.
                        // This will enable the main SPADE thread to cleanly flush the reporter
                        // buffer and remove it.
                        reporter.shutdown();
                        removereporters.add(reporter);
                        found = true;
                        outputStream.print("Shutting down reporter " + tokens[2] + "... ");
                        while (removereporters.contains(reporter)) {
                            // Wait for other thread to safely remove reporter
                            Thread.sleep(REMOVE_WAIT_DELAY);
                        }
                        iterator.remove();
                        outputStream.println("done");
                        break;
                    }
                }
                if (!found) {
                    outputStream.println("Reporter " + tokens[2] + " not found");
                }
            } else if (tokens[1].equalsIgnoreCase("storage")) {
                boolean found = false;
                for (Iterator iterator = storages.iterator(); iterator.hasNext();) {
                    AbstractStorage storage = (AbstractStorage) iterator.next();
                    // Search for the given storage in the storages set.
                    if (storage.getClass().getName().equals("spade.storage." + tokens[2])) {
                        // Mark the storage for removal by adding it to the removestorages set.
                        // This will enable the main SPADE thread to safely commit any transactions
                        // and then remove the storage.
                        long vertexCount = storage.vertexCount;
                        long edgeCount = storage.edgeCount;
                        removestorages.add(storage);
                        found = true;
                        outputStream.print("Shutting down storage " + tokens[2] + "... ");
                        while (removestorages.contains(storage)) {
                            // Wait for other thread to safely remove storage
                            Thread.sleep(REMOVE_WAIT_DELAY);
                        }
                        iterator.remove();
                        outputStream.println("done (" + vertexCount + " vertices and " + edgeCount + " edges added)");
                        break;
                    }
                }
                if (!found) {
                    outputStream.println("Storage " + tokens[2] + " not found");
                }
            } else if (tokens[1].equalsIgnoreCase("filter")) {
                // Filter removal is done by the index number (beginning from 1).
                int index = Integer.parseInt(tokens[2]);
                if ((index <= 0) || (index >= filters.size())) {
                    outputStream.println("Error: Unable to remove filter - bad index");
                    return;
                }
                String filterName = filters.get(index - 1).getClass().getName();
                outputStream.print("Removing filter " + filterName.split("\\.")[2] + "... ");
                if (index > 1) {
                    // Update the internal links between filters by calling the setNextFilter
                    // method on the filter just before the one being removed. The (index-1)
                    // check is used because this method is not to be called on the first filter.
                    ((AbstractFilter) filters.get(index - 2)).setNextFilter((AbstractFilter) filters.get(index));
                }
                filters.remove(index - 1);
                outputStream.println("done");
            } else if (tokens[1].equalsIgnoreCase("transformer")) {
                // Transformer removal is done by the index number (beginning from 1).
                int index = Integer.parseInt(tokens[2]);
                if ((index <= 0) || (index >= transformers.size())) {
                    outputStream.println("Error: Unable to remove transformer - bad index");
                    return;
                }
                String filterName = transformers.get(index - 1).getClass().getName();
                outputStream.print("Removing transformer " + filterName.split("\\.")[2] + "... ");
                if (index > 1) {
                    // Update the internal links between transformers by calling the setNextFilter
                    // method on the transformer just before the one being removed. The (index-1)
                    // check is used because this method is not to be called on the first transformer.
                    ((AbstractFilter) transformers.get(index - 2)).setNextFilter((AbstractFilter) transformers.get(index));
                }
                transformers.remove(index - 1);
                outputStream.println("done");
            } else if (tokens[1].equalsIgnoreCase("sketch")) {
                boolean found = false;
                for (Iterator iterator = sketches.iterator(); iterator.hasNext();) {
                    AbstractSketch sketch = (AbstractSketch) iterator.next();
                    // Search for the given sketch in the sketches set.
                    if (sketch.getClass().getName().equals("spade.sketch." + tokens[2])) {
                        found = true;
                        outputStream.print("Removing sketch " + tokens[2] + "... ");
                        iterator.remove();
                        outputStream.println("done");
                        break;
                    }
                }
                if (!found) {
                    outputStream.println("Sketch " + tokens[2] + " not found");
                }
            } else {
                throw new Exception();
            }
        } catch (Exception removeCommandException) {
            outputStream.println("Usage: remove reporter|storage|sketch <class name>");
            outputStream.println("       remove filter|transformer <index>");
        }
    }

    public static void shutdown() {
        for (AbstractStorage storage : storages) {
            // Shut down all storages.
            storage.shutdown();
        }
        try {
            // Remove the control and query pipes.
            Runtime.getRuntime().exec("rm -f " + controlPipeInputPath + " " + controlPipeOutputPath + " " + queryPipeInputPath).waitFor();
        } catch (Exception exception) {
            Logger.getLogger(Kernel.class.getName()).log(Level.SEVERE, null, exception);
        }
        System.exit(0);
    }
}

class FinalCommitFilter extends AbstractFilter {

    // Reference to the set of storages maintained by the Kernel.
    public Set<AbstractStorage> storages;
    public Set<AbstractSketch> sketches;

    // This filter is the last filter in the list so any vertices or edges
    // received by it need to be passed to the storages. On receiving any
    // provenance elements, it is passed to all storages.
    @Override
    public void putVertex(AbstractVertex incomingVertex) {
        for (AbstractStorage storage : storages) {
            if (storage.putVertex(incomingVertex)) {
                storage.vertexCount++;
            }
        }
        for (AbstractSketch sketch : sketches) {
            sketch.putVertex(incomingVertex);
        }
    }

    @Override
    public void putEdge(AbstractEdge incomingEdge) {
        for (AbstractStorage storage : storages) {
            if (storage.putEdge(incomingEdge)) {
                storage.edgeCount++;
            }
        }
        for (AbstractSketch sketch : sketches) {
            sketch.putEdge(incomingEdge);
        }
    }
}

class FinalTransformer extends AbstractFilter {

    // This transformer is the last one in the list so any vertices or edges
    // received by it need to be passed to the correct graph.
    @Override
    public void putVertex(AbstractVertex incomingVertex) {
        incomingVertex.resultGraph.commitVertex(incomingVertex);
    }

    @Override
    public void putEdge(AbstractEdge incomingEdge) {
        incomingEdge.resultGraph.commitEdge(incomingEdge);
    }
}

final class NullStream {

    public final static PrintStream out = new PrintStream(new OutputStream() {

        @Override
        public void close() {
        }

        @Override
        public void flush() {
        }

        @Override
        public void write(byte[] b) {
        }

        @Override
        public void write(byte[] b, int off, int len) {
        }

        public void write(int b) {
        }
    });
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
                Graph resultGraph = Kernel.query(queryLine, true);
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
                    clientObjectOutputStream.writeObject(Kernel.getPathFragment(remoteSketch));
                    clientObjectOutputStream.flush();
                } else if (sketchLine.equals("pathFragment_src")) {
                    // Get the source end path fragment
                    AbstractSketch remoteSketch = (AbstractSketch) clientObjectInputStream.readObject();
                    clientObjectOutputStream.writeObject(Kernel.getEndPathFragment(remoteSketch, "src"));
                    clientObjectOutputStream.flush();
                } else if (sketchLine.equals("pathFragment_dst")) {
                    // Get the destination end path fragment
                    AbstractSketch remoteSketch = (AbstractSketch) clientObjectInputStream.readObject();
                    clientObjectOutputStream.writeObject(Kernel.getEndPathFragment(remoteSketch, "dst"));
                    clientObjectOutputStream.flush();
                } else if (sketchLine.startsWith("notifyRebuildSketches")) {
                    String tokens[] = sketchLine.split("\\s+");
                    int currentLevel = Integer.parseInt(tokens[1]);
                    int maxLevel = Integer.parseInt(tokens[2]);
                    Kernel.notifyRebuildSketches(currentLevel, maxLevel);
                } else if (sketchLine.startsWith("propagateSketches")) {
                    String tokens[] = sketchLine.split("\\s+");
                    int currentLevel = Integer.parseInt(tokens[1]);
                    int maxLevel = Integer.parseInt(tokens[2]);
                    Kernel.propagateSketches(currentLevel, maxLevel);
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
            remoteSocket.connect(sockaddr, Kernel.TIMEOUT);
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
            remoteSocket.connect(sockaddr, Kernel.TIMEOUT);
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
            remoteSocket.connect(sockaddr, Kernel.TIMEOUT);
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

class LocalControlConnection implements Runnable {

    Socket controlSocket;

    LocalControlConnection(Socket socket) {
        controlSocket = socket;
    }

    public void run() {
        try {
            OutputStream outStream = controlSocket.getOutputStream();
            InputStream inStream = controlSocket.getInputStream();

            BufferedReader controlInputStream = new BufferedReader(new InputStreamReader(inStream));
            PrintStream controlOutputStream = new PrintStream(outStream);
            while (true) {
                // Commands read from the input stream and executed.
                if (controlInputStream.ready()) {
                    String line = controlInputStream.readLine();
                    if ((line != null) && (Kernel.executeCommand(line, controlOutputStream) == false)) {
                        break;
                    }
                    // An empty line is printed to let the client know that the
                    // command output is complete.
                    controlOutputStream.println("");
                }
            }
            controlInputStream.close();
            controlOutputStream.close();
                    
            inStream.close();
            outStream.close();
            controlSocket.close();
        } catch (Exception ex) {
            Logger.getLogger(LocalControlConnection.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}

class LocalQueryConnection implements Runnable {

    Socket querySocket;

    LocalQueryConnection(Socket socket) {
        querySocket = socket;
    }

    public void run() {
        try {
            OutputStream outStream = querySocket.getOutputStream();
            InputStream inStream = querySocket.getInputStream();

            BufferedReader queryInputStream = new BufferedReader(new InputStreamReader(inStream));
            PrintStream queryOutputStream = new PrintStream(outStream);
            while (true) {
                // Commands read from the input stream and executed.
                if (queryInputStream.ready()) {
                    String line = queryInputStream.readLine();
                    if (line != null) {
                        try {
                            String[] queryTokens = line.split("\\s+", 2);
                            // Only accept query commands from this pipe
                            // The second argument in the query command is used to specify the
                            // output for this query (i.e., a file or a pipe). This argument is
                            // stripped from the query string and is passed as a separate argument
                            // to the queryCommand() as the output stream.
                            if (queryTokens.length == 1) {
                                Kernel.showQueryCommands(queryOutputStream);
                            } else if (queryTokens[1].startsWith("query ")) {
                                Kernel.queryCommand(queryTokens[1], queryOutputStream);
                            } else {
                                Kernel.showQueryCommands(queryOutputStream);
                            }
                            // An empty line is printed to let the client know that the query
                            // output is complete.
                            queryOutputStream.println("");
                            break;
                        } catch (Exception exception) {
                        }
                    }
                    // An empty line is printed to let the client know that the
                    // command output is complete.
                    queryOutputStream.println("");
                }
            }
            queryInputStream.close();
            queryOutputStream.close();
                    
            inStream.close();
            outStream.close();
            querySocket.close();
        } catch (Exception ex) {
            Logger.getLogger(LocalQueryConnection.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}

