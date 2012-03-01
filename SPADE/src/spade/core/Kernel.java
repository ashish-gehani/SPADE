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
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The SPADE core.
 *
 * @author Dawood
 */
public class Kernel {

    /**
     * A string representing the key for the source reporter annotation added to
     * all elements retrieved from buffers.
     */
    public static final String SOURCE_REPORTER = "source_reporter";
    /**
     * A map used to cache the remote sketches.
     */
    public static Map<String, AbstractSketch> remoteSketches;
    /**
     * Default port number for local control client.
     */
    public static int LOCAL_CONTROL_PORT = 19999;
    /**
     * Default port number for local query client.
     */
    public static int LOCAL_QUERY_PORT = 19998;
    /**
     * Default port number for remote queries.
     */
    public static int REMOTE_QUERY_PORT = 29999;
    /**
     * Default port number for remote sketch-related operations.
     */
    public static int REMOTE_SKETCH_PORT = 29998;
    /**
     * Default timeout interval.
     */
    public static int CONNECTION_TIMEOUT = 15000;
    /**
     * Path to configuration file for storing state of SPADE instance (includes
     * currently added modules).
     */
    public static final String configFile = "../../cfg/spade.config";
    /**
     * Path to configuration file for port numbers.
     */
    public static final String portsFile = "../../cfg/ports.config";
    /**
     * Path to log files including the prefix.
     */
    public static final String logPathAndPrefix = "../../log/SPADE_";
    /**
     * Date/time suffix pattern for log files.
     */
    public static final String logFilenamePattern = "MM.dd.yyyy-H.mm.ss";
    /**
     * Set of reporters active on the local SPADE instance.
     */
    public static Set<AbstractReporter> reporters;
    /**
     * Set of storages active on the local SPADE instance.
     */
    public static Set<AbstractStorage> storages;
    /**
     * Set of filters active on the local SPADE instance.
     */
    public static List<AbstractFilter> filters;
    /**
     * Set of transformers active on the local SPADE instance.
     */
    public static List<AbstractFilter> transformers;
    /**
     * Set of sketches active on the local SPADE instance.
     */
    public static Set<AbstractSketch> sketches;
    /**
     * Boolean used to initiate the clean shutdown procedure. This is used by
     * the different threads to determine if the shutdown procedure has been
     * called.
     */
    public static volatile boolean shutdown;
    /**
     * Boolean used to indicate whether the transactions need to be flushed by
     * the storages.
     */
    public static volatile boolean flushTransactions;
    private static Thread mainThread;
    private static List<ServerSocket> serverSockets;
    private static Set<AbstractReporter> removereporters;
    private static Set<AbstractStorage> removestorages;
    private static final int BATCH_BUFFER_ELEMENTS = 100;
    private static final int MAIN_THREAD_SLEEP_DELAY = 10;
    private static final int REMOVE_WAIT_DELAY = 100;
    private static final int FIRST_TRANSFORMER = 0;
    private static final int FIRST_FILTER = 0;
    private static final String ADD_REPORTER_STORAGE_STRING = "add reporter|storage <class name> <initialization arguments>";
    private static final String ADD_FILTER_TRANSFORMER_STRING = "add filter|transformer <class name> <index> <initialization arguments>";
    private static final String ADD_SKETCH_STRING = "add sketch <class name>";
    private static final String REMOVE_REPORTER_STORAGE_SKETCH_STRING = "remove reporter|storage|sketch <class name>";
    private static final String REMOVE_FILTER_TRANSFORMER_STRING = "remove filter|transformer <index>";
    private static final String LIST_STRING = "list reporters|storages|filters|transformers|sketches|all";
    private static final String CONFIG_STRING = "config load|save <filename>";
    private static final String EXIT_STRING = "exit";
    private static final String SHUTDOWN_STRING = "shutdown";

    /**
     * The main initialization function.
     *
     * @param args
     */
    public static void main(String args[]) {

        checkPorts();

        try {
            // Configuring the global exception logger
            String logFilename = new java.text.SimpleDateFormat(logFilenamePattern).format(new java.util.Date(System.currentTimeMillis()));
            Handler logFileHandler = new FileHandler(logPathAndPrefix + logFilename + ".log");
            Logger.getLogger("").addHandler(logFileHandler);
        } catch (Exception exception) {
            System.out.println("Error initializing exception logger");
        }

        // Register a shutdown hook to terminate gracefully
        Runtime.getRuntime().addShutdownHook(new Thread() {

            @Override
            public void run() {
                if (!shutdown) {
                    // Shut down server sockets.
                    for (ServerSocket socket : serverSockets) {
                        try {
                            socket.close();
                        } catch (IOException ex) {
                            Logger.getLogger(Kernel.class.getName()).log(Level.WARNING, null, ex);
                        }
                    }
                    // Save current configuration.
                    configCommand("config save " + configFile, NullStream.out);
                    // Shut down all reporters.
                    for (AbstractReporter reporter : reporters) {
                        reporter.shutdown();
                    }
                    // Wait for main thread to consume all provenance data.
                    while (true) {
                        for (Iterator reporterIterator = reporters.iterator(); reporterIterator.hasNext();) {
                            AbstractReporter currentReporter = (AbstractReporter) reporterIterator.next();
                            Buffer currentBuffer = currentReporter.getBuffer();
                            if (currentBuffer.isEmpty()) {
                                reporterIterator.remove();
                            }
                        }
                        if (reporters.isEmpty()) {
                            break;
                        }
                        try {
                            Thread.sleep(MAIN_THREAD_SLEEP_DELAY);
                        } catch (InterruptedException ex) {
                            Logger.getLogger(Kernel.class.getName()).log(Level.WARNING, null, ex);
                        }
                    }
                    // Shut down filters.
                    for (int i = 0; i < filters.size() - 1; i++) {
                        filters.get(i).shutdown();
                    }
                    // Shut down storages.
                    for (AbstractStorage storage : storages) {
                        storage.shutdown();
                    }
                }
                // Terminate SPADE
            }
        });

        // Basic initialization
        reporters = Collections.synchronizedSet(new HashSet<AbstractReporter>());
        storages = Collections.synchronizedSet(new HashSet<AbstractStorage>());
        removereporters = Collections.synchronizedSet(new HashSet<AbstractReporter>());
        removestorages = Collections.synchronizedSet(new HashSet<AbstractStorage>());
        transformers = Collections.synchronizedList(new LinkedList<AbstractFilter>());
        filters = Collections.synchronizedList(new LinkedList<AbstractFilter>());
        sketches = Collections.synchronizedSet(new HashSet<AbstractSketch>());
        remoteSketches = Collections.synchronizedMap(new HashMap<String, AbstractSketch>());
        serverSockets = Collections.synchronizedList(new LinkedList<ServerSocket>());

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
        Runnable mainRunnable = new Runnable() {

            public void run() {
                try {
                    while (true) {
                        if (shutdown) {
                            // The shutdown process is also partially handled by this thread. On
                            // shutdown, all reporters are marked for removal so that their buffers
                            // are cleanly flushed and no data is lost. When a buffer becomes empty,
                            // it is removed along with its corresponding reporter. When all buffers
                            // become empty, this thread terminates.
                            for (Iterator reporterIterator = reporters.iterator(); reporterIterator.hasNext();) {
                                AbstractReporter currentReporter = (AbstractReporter) reporterIterator.next();
                                Buffer currentBuffer = currentReporter.getBuffer();
                                if (currentBuffer.isEmpty()) {
                                    reporterIterator.remove();
                                }
                            }
                            if (reporters.isEmpty()) {
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
                        for (AbstractReporter reporter : reporters) {
                            // This loop performs the actual task of committing provenance data to
                            // the storages. Each reporter is selected and the nested loop is used to
                            // extract buffer elements in a batch manner for increased efficiency.
                            // The elements are then passed to the filter list.
                            Buffer buffer = reporter.getBuffer();
                            for (int i = 0; i < BATCH_BUFFER_ELEMENTS; i++) {
                                Object bufferelement = buffer.getBufferElement();
                                if (bufferelement instanceof AbstractVertex) {
                                    AbstractVertex tempVertex = (AbstractVertex) bufferelement;
                                    tempVertex.addAnnotation(SOURCE_REPORTER, reporter.getClass().getName().split("\\.")[2]);
                                    filters.get(FIRST_FILTER).putVertex(tempVertex);
                                } else if (bufferelement instanceof AbstractEdge) {
                                    AbstractEdge tempEdge = (AbstractEdge) bufferelement;
                                    tempEdge.addAnnotation(SOURCE_REPORTER, reporter.getClass().getName().split("\\.")[2]);
                                    filters.get(FIRST_FILTER).putEdge(tempEdge);
                                } else if (bufferelement == null) {
                                    if (removereporters.contains(reporter)) {
                                        removereporters.remove(reporter);
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
        mainThread = new Thread(mainRunnable, "mainSPADE-Thread");
        mainThread.start();


        // This thread creates the input and output pipes used for control (and also used
        // by the control client). The exit value is used to determine if the pipes were
        // successfully created. The input pipe (to which commands are issued) is read in
        // a loop and the commands are processed.
        Runnable controlRunnable = new Runnable() {

            public void run() {
                try {
                    ServerSocket serverSocket = new ServerSocket(LOCAL_CONTROL_PORT);
                    serverSockets.add(serverSocket);
                    while (!shutdown) {
                        Socket controlSocket = serverSocket.accept();
                        LocalControlConnection thisConnection = new LocalControlConnection(controlSocket);
                        Thread connectionThread = new Thread(thisConnection);
                        connectionThread.start();
                    }
                } catch (SocketException exception) {
                    // Do nothing... this is triggered on shutdown.
                } catch (Exception exception) {
                    Logger.getLogger(Kernel.class.getName()).log(Level.SEVERE, null, exception);
                }
            }
        };
        Thread controlThread = new Thread(controlRunnable, "controlSocket-Thread");
        controlThread.start();


        // Construct the query pipe. The exit value is used to determine if the
        // query pipe was successfully created.
        Runnable queryRunnable = new Runnable() {

            public void run() {
                try {
                    ServerSocket serverSocket = new ServerSocket(LOCAL_QUERY_PORT);
                    serverSockets.add(serverSocket);
                    while (!shutdown) {
                        Socket querySocket = serverSocket.accept();
                        LocalQueryConnection thisConnection = new LocalQueryConnection(querySocket);
                        Thread connectionThread = new Thread(thisConnection);
                        connectionThread.start();
                    }
                } catch (SocketException exception) {
                    // Do nothing... this is triggered on shutdown.
                } catch (Exception exception) {
                    Logger.getLogger(Kernel.class.getName()).log(Level.SEVERE, null, exception);
                }
            }
        };
        Thread queryThread = new Thread(queryRunnable, "querySocket-Thread");
        queryThread.start();


        // This thread creates a server socket for remote querying. When a query connection
        // is established, another new thread is created for that connection object. The
        // remote query server is therefore a multithreaded server.
        Runnable remoteRunnable = new Runnable() {

            public void run() {
                try {
                    ServerSocket serverSocket = new ServerSocket(REMOTE_QUERY_PORT);
                    serverSockets.add(serverSocket);
                    while (!shutdown) {
                        Socket clientSocket = serverSocket.accept();
                        QueryConnection thisConnection = new QueryConnection(clientSocket);
                        Thread connectionThread = new Thread(thisConnection);
                        connectionThread.start();
                    }
                } catch (SocketException exception) {
                    // Do nothing... this is triggered on shutdown.
                } catch (Exception exception) {
                    Logger.getLogger(Kernel.class.getName()).log(Level.SEVERE, null, exception);
                }
            }
        };
        Thread remoteThread = new Thread(remoteRunnable, "remoteQuery-Thread");
        remoteThread.start();


        // This thread creates a server socket for remote sketches. When a sketch connection
        // is established, another new thread is created for that connection object. The
        // remote sketch server is therefore a multithreaded server.
        Runnable sketchRunnable = new Runnable() {

            public void run() {
                try {
                    ServerSocket serverSocket = new ServerSocket(REMOTE_SKETCH_PORT);
                    serverSockets.add(serverSocket);
                    while (!shutdown) {
                        Socket clientSocket = serverSocket.accept();
                        SketchConnection thisConnection = new SketchConnection(clientSocket);
                        Thread connectionThread = new Thread(thisConnection);
                        connectionThread.start();
                    }
                } catch (SocketException exception) {
                    // Do nothing... this is triggered on shutdown.
                } catch (Exception exception) {
                    Logger.getLogger(Kernel.class.getName()).log(Level.SEVERE, null, exception);
                }
            }
        };
        Thread sketchThread = new Thread(sketchRunnable, "remoteSketch-Thread");
        sketchThread.start();

        // Load the SPADE configuration from the default config file.
        configCommand("config load " + configFile, NullStream.out);
    }

    // Check the port configuration file to assign port numbers
    /**
     * Checks the port configuration file to assign port numbers.
     */
    public static void checkPorts() {
        try {
            // Check if the port configuration file exists. If not, use the
            // default port numbers
            File checkPortsFile = new File(portsFile);
            if (!checkPortsFile.exists()) {
                return;
            }

            // Create HashMap to store key/value pairs from the configuration files
            HashMap<String, Integer> configValues = new HashMap<String, Integer>();
            BufferedReader fileReader = new BufferedReader(new FileReader(portsFile));
            String line;
            while ((line = fileReader.readLine()) != null) {
                String[] tokens = line.split("=");
                String key = tokens[0].trim().toUpperCase();
                Integer value = Integer.parseInt(tokens[1].trim());
                configValues.put(key, value);
            }

            // Assign port numbers from the configuration file
            LOCAL_CONTROL_PORT = configValues.get("LOCAL_CONTROL_PORT");
            LOCAL_QUERY_PORT = configValues.get("LOCAL_QUERY_PORT");
            REMOTE_QUERY_PORT = configValues.get("REMOTE_QUERY_PORT");
            REMOTE_SKETCH_PORT = configValues.get("REMOTE_SKETCH_PORT");
            CONNECTION_TIMEOUT = configValues.get("CONNECTION_TIMEOUT");
        } catch (Exception ex) {
            Logger.getLogger(Kernel.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    // The following two methods are called by the Graph object when adding vertices
    // and edges to the result graph. Transformers are technically the same as filters
    // and are used to modify/transform data as it is entered into a Graph object.
    /**
     * Method called by a Graph object to send vertices to transformers before
     * they are finally added to the result.
     *
     * @param vertex The vertex to be transformed.
     */
    public static void sendToTransformers(AbstractVertex vertex) {
        ((AbstractFilter) transformers.get(FIRST_TRANSFORMER)).putVertex(vertex);
    }

    /**
     * Method called by a Graph object to send edges to transformers before they
     * are finally added to the result.
     *
     * @param edge The edge to be transformed.
     */
    public static void sendToTransformers(AbstractEdge edge) {
        ((AbstractFilter) transformers.get(FIRST_TRANSFORMER)).putEdge(edge);
    }

    /**
     * All command strings are passed to this function which subsequently calls
     * the correct method based on the command. Each command is determined by
     * the first token in the string.
     *
     * @param line The command string.
     * @param outputStream The output stream on which to print the result or any
     * output.
     */
    public static void executeCommand(String line, PrintStream outputStream) {
        String command = line.split("\\s+")[0];
        if (command.equalsIgnoreCase("shutdown")) {
            // On shutdown, save the current configuration in the default configuration
            // file.
            configCommand("config save " + configFile, NullStream.out);
            for (AbstractReporter reporter : reporters) {
                // Shut down all reporters. After
                // this, their buffers are flushed and then the storages are shut down.
                reporter.shutdown();
            }
            shutdown = true;
        } else if (command.equalsIgnoreCase("add")) {
            addCommand(line, outputStream);
        } else if (command.equalsIgnoreCase("list")) {
            listCommand(line, outputStream);
        } else if (command.equalsIgnoreCase("remove")) {
            removeCommand(line, outputStream);
        } else if (command.equalsIgnoreCase("query")) {
            queryCommand(line, outputStream);
        } else if (command.equalsIgnoreCase("config")) {
            configCommand(line, outputStream);
        } else {
            displayControlCommands(outputStream);
        }
    }

    /**
     * The configCommand is used to load or save the current SPADE configuration
     * from/to a file.
     *
     * @param line The configuration command to execute.
     * @param outputStream The output stream on which to print the result or any
     * output.
     */
    public static void configCommand(String line, PrintStream outputStream) {
        String[] tokens = line.split("\\s+");
        if (tokens.length < 3) {
            outputStream.println("Usage:");
            outputStream.println("\t" + CONFIG_STRING);
            return;
        }
        // Determine whether the command is a load or a save.
        if (tokens[1].equalsIgnoreCase("load")) {
            outputStream.print("Loading configuration... ");
            try {
                BufferedReader configReader = new BufferedReader(new FileReader(tokens[2]));
                String configLine;
                while ((configLine = configReader.readLine()) != null) {
                    addCommand("add " + configLine, outputStream);
                }
            } catch (Exception exception) {
                outputStream.println("error! Unable to open configuration file for reading");
                Logger.getLogger(Kernel.class.getName()).log(Level.SEVERE, null, exception);
                return;
            }
            outputStream.println("done");
        } else if (tokens[1].equalsIgnoreCase("save")) {
            // If the command is save, then write the current configuration.
            outputStream.print("Saving configuration... ");
            try {
                FileWriter configWriter = new FileWriter(tokens[2], false);
                for (int i = 0; i < filters.size() - 1; i++) {
                    String arguments = filters.get(i).arguments;
                    configWriter.write("filter " + filters.get(i).getClass().getName().split("\\.")[2] + " " + i);
                    if (arguments != null) {
                        configWriter.write(" " + arguments);
                    }
                    configWriter.write("\n");
                }
                for (AbstractSketch sketch : sketches) {
                    configWriter.write("sketch " + sketch.getClass().getName().split("\\.")[2] + "\n");
                }
                for (AbstractStorage storage : storages) {
                    String arguments = storage.arguments;
                    configWriter.write("storage " + storage.getClass().getName().split("\\.")[2]);
                    if (arguments != null) {
                        configWriter.write(" " + arguments);
                    }
                    configWriter.write("\n");
                }
                for (AbstractReporter reporter : reporters) {
                    String arguments = reporter.arguments;
                    configWriter.write("reporter " + reporter.getClass().getName().split("\\.")[2]);
                    if (arguments != null) {
                        configWriter.write(" " + arguments);
                    }
                    configWriter.write("\n");
                }
                configWriter.close();
            } catch (Exception exception) {
                outputStream.println("error! Unable to open configuration file for writing");
                Logger.getLogger(Kernel.class.getName()).log(Level.SEVERE, null, exception);
                return;
            }
            outputStream.println("done");
        } else {
            outputStream.println("Usage:");
            outputStream.println("\t" + CONFIG_STRING);
        }
    }

    /**
     * This method is triggered by the query client and calls the main query
     * method to retrieve the result before exporting it to the desired path.
     *
     * @param line The query expression.
     * @param outputStream The output stream on which to print the result or any
     * output.
     */
    public static void queryCommand(String line, PrintStream outputStream) {
        Graph resultGraph = Query.executeQuery(line, false);
        if (resultGraph != null) {
            String[] tokens = line.split("\\s+");
            String outputFile = tokens[tokens.length - 1];
            if ((tokens[2].equalsIgnoreCase("vertices")) || (tokens[2].equalsIgnoreCase("remotevertices"))) {
                for (AbstractVertex tempVertex : resultGraph.vertexSet()) {
                    outputStream.println("[" + tempVertex.toString() + "]");
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

    /**
     * Method to display control commands to the output stream. The control and
     * query commands are displayed using separate methods since these commands
     * are issued from different clients.
     *
     * @param outputStream
     */
    public static void displayControlCommands(PrintStream outputStream) {
        outputStream.println("Available commands:");
        outputStream.println("\t" + ADD_REPORTER_STORAGE_STRING);
        outputStream.println("\t" + ADD_FILTER_TRANSFORMER_STRING);
        outputStream.println("\t" + ADD_SKETCH_STRING);
        outputStream.println("\t" + REMOVE_REPORTER_STORAGE_SKETCH_STRING);
        outputStream.println("\t" + REMOVE_FILTER_TRANSFORMER_STRING);
        outputStream.println("\t" + LIST_STRING);
        outputStream.println("\t" + CONFIG_STRING);
        outputStream.println("\t" + EXIT_STRING);
        outputStream.println("\t" + SHUTDOWN_STRING);
    }

    /**
     * Method to display query commands to the given output stream.
     *
     * @param outputStream The target output stream.
     */
    public static void displayQueryCommands(PrintStream outputStream) {
        outputStream.println("Available commands:");
        outputStream.println("       query <class name> vertices <expression>");
        outputStream.println("       query <class name> lineage <vertex id> <depth> <direction> <terminating expression> <output file>");
        outputStream.println("       query <class name> paths <source vertex id> <destination vertex id> <max length> <output file>");
//        outputStream.println("       query <class name> remotepaths <source host:vertex id> <destination host:vertex id> <max length> <output file>");
        outputStream.println("       exit");
    }

    /**
     * Method to add extensions.
     *
     * @param line The add command issued using the control client.
     * @param outputStream The output stream on which to print the results or
     * any output.
     */
    public static void addCommand(String line, PrintStream outputStream) {
        String[] tokens = line.split("\\s+", 4);
        if (tokens.length < 2) {
            outputStream.println("Usage:");
            outputStream.println("\t" + ADD_REPORTER_STORAGE_STRING);
            outputStream.println("\t" + ADD_FILTER_TRANSFORMER_STRING);
            outputStream.println("\t" + ADD_SKETCH_STRING);
            return;
        }
        if (tokens[1].equalsIgnoreCase("reporter")) {
            if (tokens.length < 3) {
                outputStream.println("Usage:");
                outputStream.println("\t" + ADD_REPORTER_STORAGE_STRING);
                return;
            }
            String classname = tokens[2];
            String arguments = (tokens.length == 3) ? null : tokens[3];
            // Get the reporter by classname and create a new instance.
            outputStream.print("Adding reporter " + classname + "... ");
            AbstractReporter reporter;
            try {
                reporter = (AbstractReporter) Class.forName("spade.reporter." + classname).newInstance();
            } catch (Exception ex) {
                outputStream.println("error: Unable to find/load class");
                Logger.getLogger(Kernel.class.getName()).log(Level.SEVERE, null, ex);
                return;
            }
            // Create a new buffer and allocate it to this reporter.
            Buffer buffer = new Buffer();
            reporter.setBuffer(buffer);
            if (reporter.launch(arguments)) {
                // The launch() method must return true to indicate a successful launch.
                // On true, the reporter is added to the reporters set and the buffer
                // is put into a HashMap keyed by the reporter (this is used by the main
                // SPADE thread to extract buffer elements).
                reporter.arguments = arguments;
                reporters.add(reporter);
                outputStream.println("done");
            } else {
                outputStream.println("failed");
            }
        } else if (tokens[1].equalsIgnoreCase("storage")) {
            if (tokens.length < 3) {
                outputStream.println("Usage:");
                outputStream.println("\t" + ADD_REPORTER_STORAGE_STRING);
                return;
            }
            String classname = tokens[2];
            String arguments = (tokens.length == 3) ? null : tokens[3];
            // Get the storage by classname and create a new instance.
            outputStream.print("Adding storage " + classname + "... ");
            AbstractStorage storage;
            try {
                storage = (AbstractStorage) Class.forName("spade.storage." + classname).newInstance();
            } catch (Exception ex) {
                outputStream.println("error: Unable to find/load class");
                Logger.getLogger(Kernel.class.getName()).log(Level.SEVERE, null, ex);
                return;
            }
            if (storage.initialize(arguments)) {
                // The initialize() method must return true to indicate successful startup.
                // On true, the storage is added to the storages set.
                storage.arguments = arguments;
                storage.vertexCount = 0;
                storage.edgeCount = 0;
                storages.add(storage);
                outputStream.println("done");
            } else {
                outputStream.println("failed");
            }
        } else if (tokens[1].equalsIgnoreCase("filter")) {
            if (tokens.length < 4) {
                outputStream.println("Usage:");
                outputStream.println("\t" + ADD_FILTER_TRANSFORMER_STRING);
                return;
            }
            String classname = tokens[2];
            String[] parameters = tokens[3].split("\\s+", 2);
            outputStream.print("Adding filter " + classname + "... ");
            int index = 0;
            try {
                index = Integer.parseInt(parameters[0]);
            } catch (NumberFormatException numberFormatException) {
                outputStream.println("error: Index must be a number!");
                return;
            }
            String arguments = (parameters.length == 1) ? null : parameters[1];
            // Get the filter by classname and create a new instance.
            AbstractFilter filter;
            try {
                filter = (AbstractFilter) Class.forName("spade.filter." + classname).newInstance();
            } catch (Exception ex) {
                outputStream.println("error: Unable to find/load class");
                Logger.getLogger(Kernel.class.getName()).log(Level.SEVERE, null, ex);
                return;
            }
            // Initialize filter if arguments are provided
            filter.initialize(arguments);
            filter.arguments = arguments;
            // The argument is the index at which the filter is to be inserted.
            if (index >= filters.size()) {
                outputStream.println("error: Invalid index!");
                return;
            }
            // Set the next filter of this newly added filter.
            filter.setNextFilter((AbstractFilter) filters.get(index));
            if (index > 0) {
                // If the newly added filter is not the first in the list, then
                // then configure the previous filter in the list to point to this
                // newly added filter as its next.
                ((AbstractFilter) filters.get(index - 1)).setNextFilter(filter);
            }
            // Add filter to the list.
            filters.add(index, filter);
            outputStream.println("done");
        } else if (tokens[1].equalsIgnoreCase("transformer")) {
            if (tokens.length < 4) {
                outputStream.println("Usage:");
                outputStream.println("\t" + ADD_FILTER_TRANSFORMER_STRING);
                return;
            }
            String classname = tokens[2];
            String[] parameters = tokens[3].split("\\s+", 2);
            outputStream.print("Adding transformer " + classname + "... ");
            int index = 0;
            try {
                index = Integer.parseInt(parameters[0]);
            } catch (NumberFormatException numberFormatException) {
                outputStream.println("error: Index must be a number!");
                return;
            }
            String arguments = (parameters.length == 1) ? null : parameters[1];
            // Get the transformer by classname and create a new instance.
            AbstractFilter filter;
            try {
                filter = (AbstractFilter) Class.forName("spade.filter." + classname).newInstance();
            } catch (Exception ex) {
                outputStream.println("error: Unable to find/load class");
                Logger.getLogger(Kernel.class.getName()).log(Level.SEVERE, null, ex);
                return;
            }
            // Initialize filter if arguments are provided
            filter.initialize(arguments);
            filter.arguments = arguments;
            // The argument is the index at which the transformer is to be inserted.
            if (index >= transformers.size()) {
                outputStream.println("error: Invalid index!");
                return;
            }
            // Set the next transformer of this newly added transformer.
            filter.setNextFilter((AbstractFilter) transformers.get(index));
            if (index > 0) {
                // If the newly added transformer is not the first in the list, then
                // then configure the previous transformer in the list to point to this
                // newly added transformer as its next.
                ((AbstractFilter) transformers.get(index - 1)).setNextFilter(filter);
            }
            // Add transformer to the list of transformers.
            transformers.add(index, filter);
            outputStream.println("done");
        } else if (tokens[1].equalsIgnoreCase("sketch")) {
            if (tokens.length < 3) {
                outputStream.println("Usage:");
                outputStream.println("\t" + ADD_SKETCH_STRING);
                return;
            }
            String classname = tokens[2];
            // Get the sketch by classname and create a new instance.
            outputStream.print("Adding sketch " + classname + "... ");
            AbstractSketch sketch;
            try {
                sketch = (AbstractSketch) Class.forName("spade.sketch." + classname).newInstance();
            } catch (Exception ex) {
                outputStream.println("error: Unable to find/load class");
                Logger.getLogger(Kernel.class.getName()).log(Level.SEVERE, null, ex);
                return;
            }
            sketches.add(sketch);
            outputStream.println("done");
        } else {
            outputStream.println("Usage:");
            outputStream.println("\t" + ADD_REPORTER_STORAGE_STRING);
            outputStream.println("\t" + ADD_FILTER_TRANSFORMER_STRING);
            outputStream.println("\t" + ADD_SKETCH_STRING);
        }
    }

    /**
     * Method to list extensions.
     *
     * @param line The list command issued using the control client.
     * @param outputStream The output stream on which to print the results or
     * any output.
     */
    public static void listCommand(String line, PrintStream outputStream) {
        String[] tokens = line.split("\\s+");
        if (tokens.length < 2) {
            outputStream.println("Usage:");
            outputStream.println("\t" + LIST_STRING);
            return;
        }
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
                outputStream.print("\t" + count + ". " + reporter.getClass().getName().split("\\.")[2]);
                if (arguments != null) {
                    outputStream.print(" (" + arguments + ")");
                }
                outputStream.println();
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
                outputStream.print("\t" + count + ". " + storage.getClass().getName().split("\\.")[2]);
                if (arguments != null) {
                    outputStream.print(" (" + arguments + ")");
                }
                outputStream.println();
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
                String arguments = filters.get(i).arguments;
                outputStream.print("\t" + (i + 1) + ". " + filters.get(i).getClass().getName().split("\\.")[2]);
                if (arguments != null) {
                    outputStream.print(" (" + arguments + ")");
                }
                outputStream.println();
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
                String arguments = transformers.get(i).arguments;
                outputStream.print("\t" + (i + 1) + ". " + transformers.get(i).getClass().getName().split("\\.")[2]);
                if (arguments != null) {
                    outputStream.print(" (" + arguments + ")");
                }
                outputStream.println();
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
            outputStream.println("Usage:");
            outputStream.println("\t" + LIST_STRING);
        }
    }

    /**
     * Method to remove extensions.
     *
     * @param line The remove command issued using the control client.
     * @param outputStream The output stream on which to print the results or
     * any output.
     */
    public static void removeCommand(String line, PrintStream outputStream) {
        String[] tokens = line.split("\\s+");
        if (tokens.length < 3) {
            outputStream.println("Usage:");
            outputStream.println("\t" + REMOVE_REPORTER_STORAGE_SKETCH_STRING);
            outputStream.println("\t" + REMOVE_FILTER_TRANSFORMER_STRING);
            return;
        }
        try {
            if (tokens[1].equalsIgnoreCase("reporter")) {
                boolean found = false;
                for (Iterator reporterIterator = reporters.iterator(); reporterIterator.hasNext();) {
                    AbstractReporter reporter = (AbstractReporter) reporterIterator.next();
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
                        reporterIterator.remove();
                        outputStream.println("done");
                        break;
                    }
                }
                if (!found) {
                    outputStream.println("Reporter " + tokens[2] + " not found");
                }
            } else if (tokens[1].equalsIgnoreCase("storage")) {
                boolean found = false;
                for (Iterator storageIterator = storages.iterator(); storageIterator.hasNext();) {
                    AbstractStorage storage = (AbstractStorage) storageIterator.next();
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
                        storageIterator.remove();
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
                filters.get(index - 1).shutdown();
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
                transformers.get(index - 1).shutdown();
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
                for (Iterator sketchIterator = sketches.iterator(); sketchIterator.hasNext();) {
                    AbstractSketch sketch = (AbstractSketch) sketchIterator.next();
                    // Search for the given sketch in the sketches set.
                    if (sketch.getClass().getName().equals("spade.sketch." + tokens[2])) {
                        found = true;
                        outputStream.print("Removing sketch " + tokens[2] + "... ");
                        sketchIterator.remove();
                        outputStream.println("done");
                        break;
                    }
                }
                if (!found) {
                    outputStream.println("Sketch " + tokens[2] + " not found");
                }
            } else {
                outputStream.println("Usage:");
                outputStream.println("\t" + REMOVE_REPORTER_STORAGE_SKETCH_STRING);
                outputStream.println("\t" + REMOVE_FILTER_TRANSFORMER_STRING);
            }
        } catch (Exception removeCommandException) {
            outputStream.println("Usage:");
            outputStream.println("\t" + REMOVE_REPORTER_STORAGE_SKETCH_STRING);
            outputStream.println("\t" + REMOVE_FILTER_TRANSFORMER_STRING);
            Logger.getLogger(Kernel.class.getName()).log(Level.WARNING, null, removeCommandException);
        }
    }

    /**
     * Method to shut down SPADE completely.
     */
    public static void shutdown() {
        // Shut down filters.
        for (int i = 0; i < filters.size() - 1; i++) {
            filters.get(i).shutdown();
        }
        // Shut down storages.
        for (AbstractStorage storage : storages) {
            storage.shutdown();
        }
        // Shut down server sockets.
        for (ServerSocket socket : serverSockets) {
            try {
                socket.close();
            } catch (IOException ex) {
                Logger.getLogger(Kernel.class.getName()).log(Level.SEVERE, null, ex);
            }
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
            while (!Kernel.shutdown) {
                // Commands read from the input stream and executed.
                if (controlInputStream.ready()) {
                    String line = controlInputStream.readLine();
                    if ((line == null) || line.equalsIgnoreCase("exit")) {
                        break;
                    }
                    Kernel.executeCommand(line, controlOutputStream);
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
            while (!Kernel.shutdown) {
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
                                Kernel.displayQueryCommands(queryOutputStream);
                            } else if (queryTokens[1].startsWith("query ")) {
                                Kernel.queryCommand(queryTokens[1], queryOutputStream);
                            } else if (queryTokens[1].equalsIgnoreCase("exit")) {
                                queryOutputStream.println("");
                                break;
                            } else {
                                Kernel.displayQueryCommands(queryOutputStream);
                            }
                        } catch (Exception exception) {
                            Logger.getLogger(LocalQueryConnection.class.getName()).log(Level.SEVERE, null, exception);
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
