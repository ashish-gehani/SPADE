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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedList;
import jline.SimpleCompletor;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;


public class Kernel {

    private static final String configFile = "spade.config";
    private static final String queryPipeInputPath = "queryPipeIn";
    private static final String controlPipeInputPath = "controlPipeIn";
    private static final String controlPipeOutputPath = "controlPipeOut";
    private static final int REMOTE_QUERY_PORT = 9999;
    private static final int BATCH_BUFFER_ELEMENTS = 200;

    private static Set<AbstractReporter> reporters;
    private static Set<AbstractStorage> storages;
    private static Set<AbstractReporter> removereporters;
    private static Set<AbstractStorage> removestorages;
    private static List<AbstractFilter> filters;
    private static Map<AbstractReporter, Buffer> buffers;
    private static Set<AbstractSketch> sketches;

    private static volatile boolean shutdown;
    private static volatile boolean flushTransactions;
    private static List<String> reporterStrings;
    private static List<String> storageStrings;
    private static SimpleCompletor reporterCompletor;
    private static SimpleCompletor storageCompletor;

    private static PrintStream outputStream = System.out;
    private static PrintStream errorStream = System.err;

    public static void main(String args[]) {

        // Basic initialization
        shutdown = false;
        flushTransactions = false;
        reporters = Collections.synchronizedSet(new HashSet<AbstractReporter>());
        storages = Collections.synchronizedSet(new HashSet<AbstractStorage>());
        removereporters = Collections.synchronizedSet(new HashSet<AbstractReporter>());
        removestorages = Collections.synchronizedSet(new HashSet<AbstractStorage>());
        filters = Collections.synchronizedList(new LinkedList<AbstractFilter>());
        buffers = Collections.synchronizedMap(new HashMap<AbstractReporter, Buffer>());
        sketches = Collections.synchronizedSet(new HashSet<AbstractSketch>());

        // Data structures used for tab completion
        reporterStrings = new ArrayList<String>();
        storageStrings = new ArrayList<String>();
        reporterCompletor = new SimpleCompletor("");
        storageCompletor = new SimpleCompletor("");

        // Initialize the SketchManager and the final commit filter.
        // The FinalCommitFilter acts as a terminator for the filter list
        // and also maintains a pointer to the list of active storages to which
        // the provenance data is finally passed. It also has a reference to
        // the SketchManager and triggers its putVertex() and putEdge() methods
        FinalCommitFilter commitFilter = new FinalCommitFilter();
        commitFilter.storages = storages;
        commitFilter.sketches = sketches;
        filters.add(commitFilter);


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
                            // it is removed along with its corresponding reporter.
                            Iterator iterator = buffers.entrySet().iterator();
                            while (iterator.hasNext()) {
                                if (((Buffer) ((Map.Entry) iterator.next()).getValue()).isEmpty()) {
                                    iterator.remove();
                                }
                            }
                            if (buffers.isEmpty()) {
                                outputStream.println("done");
                                shutdown();
                                break;
                            }
                        }
                        if (flushTransactions) {
                            // Flushing of transactions is also handled by this thread to ensure that
                            // there are no errors/problems when using storages that are sensitive to
                            // thread-context for their transactions. For example, this is true for
                            // the embedded neo4j graph database.
                            Iterator iterator = storages.iterator();
                            while (iterator.hasNext()) {
                                ((AbstractStorage) iterator.next()).flushTransactions();
                            }
                            flushTransactions = false;
                        }
                        if (!removestorages.isEmpty()) {
                            Iterator iterator = removestorages.iterator();
                            AbstractStorage storage = (AbstractStorage) iterator.next();
                            storage.shutdown();
                            iterator.remove();
                        }
                        for (Iterator iterator = buffers.keySet().iterator(); iterator.hasNext();) {
                            AbstractReporter reporter = (AbstractReporter) iterator.next();
                            for (int i=0; i<BATCH_BUFFER_ELEMENTS; i++) {
                                Object bufferelement = ((Buffer) buffers.get(reporter)).getBufferElement();
                                if (bufferelement instanceof AbstractVertex) {
                                    AbstractVertex tempVertex = (AbstractVertex) bufferelement;
                                    tempVertex.addAnnotation("source_reporter", reporter.getClass().getName());
                                    ((AbstractFilter) filters.get(0)).putVertex(tempVertex);
                                } else if (bufferelement instanceof AbstractEdge) {
                                    AbstractEdge tempEdge = (AbstractEdge) bufferelement;
                                    tempEdge.addAnnotation("source_reporter", reporter.getClass().getName());
                                    ((AbstractFilter) filters.get(0)).putEdge((AbstractEdge) bufferelement);
                                } else if (bufferelement == null) {
                                    if (removereporters.contains(reporter)) {
                                        removereporters.remove(reporter);
                                        iterator.remove();
                                    }
                                    break;
                                }
                            }
                        }
                        Thread.sleep(3);
                    }
                } catch (Exception exception) {
                    exception.printStackTrace(errorStream);
                }
            }
        };
        new Thread(mainThread, "mainThread").start();


        // Construct the query pipe. The exit value is used to determine if the
        // query pipe was successfully created.
        try {
            int exitValue1 = Runtime.getRuntime().exec("mkfifo " + queryPipeInputPath).waitFor();
            if (exitValue1 != 0) {
                errorStream.println("Error creating query pipes!");
            } else {
                Runnable queryThread = new Runnable() {

                    public void run() {
                        try {
                            BufferedReader queryInputStream = new BufferedReader(new FileReader(queryPipeInputPath));
                            while (!shutdown) {
                                if (queryInputStream.ready()) {
                                    String line = queryInputStream.readLine();
                                    if (line != null) {
                                        try {
                                            String[] queryTokens = line.split("\\s", 2);
                                            // Only accept query commands from this pipe
                                            // The second argument in the query command is used to specify the
                                            // output for this query (i.e., a file or a pipe). This argument is
                                            // stripped from the query string and is passed as a separate argument
                                            // to the queryCommand() as the output stream.
                                            PrintStream queryOutputStream = new PrintStream(new FileOutputStream(queryTokens[0]));
                                            if (queryTokens.length == 1) {
                                                showQueryCommands(queryOutputStream);
                                            } else if (queryTokens[1].startsWith("query ")) {
                                                queryCommand(queryTokens[1], queryOutputStream);
                                            } else {
                                                showQueryCommands(queryOutputStream);
                                            }
                                            queryOutputStream.close();
                                        } catch (Exception exception) {
                                        }
                                    }
                                }
                                Thread.sleep(200);
                            }
                        } catch (Exception exception) {
                            exception.printStackTrace(errorStream);
                        }
                    }
                };
                new Thread(queryThread, "queryThread").start();
            }
        } catch (Exception ex) {
            ex.printStackTrace(errorStream);
        }


        // This thread creates the input and output pipes used for control (and also used
        // by the control client). The exit value is used to determine if the pipes were
        // successfully created. The input pipe (to which commands are issued) is read in
        // a loop and the commands are processed.
        Runnable controlThread = new Runnable() {

            public void run() {
                try {
                    int exitValue1 = Runtime.getRuntime().exec("mkfifo " + controlPipeInputPath).waitFor();
                    int exitValue2 = Runtime.getRuntime().exec("mkfifo " + controlPipeOutputPath).waitFor();
                    if (exitValue1 != 0 && exitValue2 != 0) {
                        errorStream.println("Error creating control pipes!");
                    } else {
                        outputStream.println("");
                        outputStream.println("SPADE 2.0 Kernel");

                        configCommand("config load " + configFile);

                        BufferedReader controlInputStream = new BufferedReader(new FileReader(controlPipeInputPath));
                        PrintStream controlOutputStream = new PrintStream(new FileOutputStream(controlPipeOutputPath));
                        outputStream = controlOutputStream;
                        errorStream = controlOutputStream;
                        while (true) {
                            if (controlInputStream.ready()) {
                                String line = controlInputStream.readLine();
                                if ((line != null) && (executeCommand(line) == false)) {
                                    outputStream.println("Shutting down...");
                                    break;
                                }
                            }
                            Thread.sleep(100);
                        }
                    }
                } catch (Exception exception) {
                    exception.printStackTrace(errorStream);
                }
            }
        };
        new Thread(controlThread, "controlThread").start();

        
        // This thread creates the input and output pipes used for control (and also used
        // by the control client). The exit value is used to determine if the pipes were
        // successfully created. The input pipe (to which commands are issued) is read in
        // a loop and the commands are processed.
        Runnable remoteThread = new Runnable() {

            public void run() {
                try {
                    ServerSocket serverSocket = new ServerSocket(REMOTE_QUERY_PORT);
                    Socket clientSocket = serverSocket.accept();
                    BufferedReader clientInputReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    PrintStream clientPrintStream = new PrintStream(clientSocket.getOutputStream());
                    String queryLine = clientInputReader.readLine();
                    Graph resultGraph = query(queryLine);
                    if (resultGraph == null) {
                        clientPrintStream.println("null");
                    } else {
                        clientPrintStream.println("result");
                        ObjectOutputStream clientObjectOutputStream = new ObjectOutputStream(clientSocket.getOutputStream());
                        clientObjectOutputStream.writeObject(resultGraph);
                        clientObjectOutputStream.close();
                    }
                    clientPrintStream.close();
                    clientInputReader.close();
                } catch (Exception exception) {
                    exception.printStackTrace(errorStream);
                }
            }
        };
        // new Thread(remoteThread, "remoteThread").start();

    }

    // All command strings are passed to this function which subsequently calls the
    // correct method based on the command. Each command is determined by the first
    // token in the string.
    public static boolean executeCommand(String line) {
        String command = line.split("\\s")[0];
        if (command.equalsIgnoreCase("shutdown")) {
            // On shutdown, save the current configuration in the default configuration
            // file.
            configCommand("config save " + configFile);
            Iterator itp = reporters.iterator();
            outputStream.print("Shutting down reporters... ");
            while (itp.hasNext()) {
                // Iterate through the set of reporters and shut them all down. After
                // this, their buffers are flushed and then the storages are shut down.
                AbstractReporter reporter = (AbstractReporter) itp.next();
                reporter.shutdown();
            }
            outputStream.println("done");
            outputStream.print("Flushing buffers... ");
            shutdown = true;
            return false;
        } else if (command.equalsIgnoreCase("add")) {
            addCommand(line);
            return true;
        } else if (command.equalsIgnoreCase("list")) {
            listCommand(line);
            return true;
        } else if (command.equalsIgnoreCase("remove")) {
            removeCommand(line);
            return true;
        } else if (command.equalsIgnoreCase("query")) {
            queryCommand(line, outputStream);
            return true;
        } else if (command.equalsIgnoreCase("config")) {
            configCommand(line);
            return true;
        } else {
            showCommands();
            return true;
        }
    }

    // The configCommand is used to load or save the current SPADE configuration
    // from/to a file.
    public static void configCommand(String line) {
        String[] tokens = line.split("\\s");
        try {
            if (tokens[1].equalsIgnoreCase("load")) {
                BufferedReader configReader = new BufferedReader(new FileReader(tokens[2]));
                String configLine;
                while ((configLine = configReader.readLine()) != null) {
                    addCommand("add " + configLine);
                }
                outputStream.println("Finished loading configuration file");
            } else if (tokens[1].equalsIgnoreCase("save")) {
                outputStream.print("Saving configuration... ");
                FileWriter configWriter = new FileWriter(tokens[2], false);
                for (int i = 0; i < filters.size() - 1; i++) {
                    configWriter.write("filter " + filters.get(i).getClass().getName().split("\\.")[2] + " " + i + "\n");
                }
                Iterator storageIterator = storages.iterator();
                while (storageIterator.hasNext()) {
                    AbstractStorage storage = (AbstractStorage) storageIterator.next();
                    String arguments = storage.arguments;
                    configWriter.write("storage " + storage.getClass().getName().split("\\.")[2] + " " + arguments + "\n");
                }
                Iterator reporterIterator = reporters.iterator();
                while (reporterIterator.hasNext()) {
                    AbstractReporter reporter = (AbstractReporter) reporterIterator.next();
                    String arguments = reporter.arguments;
                    configWriter.write("reporter " + reporter.getClass().getName().split("\\.")[2] + " " + arguments + "\n");
                }
                configWriter.close();
                outputStream.println("done");
            } else {
                throw new Exception();
            }
        } catch (Exception configCommandException) {
            // outputStream.println("Usage: config load|save <filename>");
        }
    }

    // This method is used to call query methods on the desired storage. The
    // transactions are also flushed to ensure that the data in the storages is
    // consistent and updated with all the data received by SPADE up to this point.
    public static Graph query(String line) {
        Graph resultGraph = null;
        flushTransactions = true;
        while (flushTransactions) {
            // wait for other thread to flush transactions
        }
        if (storages.isEmpty()) {
            return null;
        }
        String[] tokens = line.split("\\s");
        Iterator iterator = storages.iterator();
        while (iterator.hasNext()) {
            AbstractStorage storage = (AbstractStorage) iterator.next();
            if (storage.getClass().getName().equals("spade.storage." + tokens[1])) {
                if (tokens[2].equalsIgnoreCase("vertices")) {
                    String queryExpression = "";
                    for (int i = 3; i < tokens.length; i++) {
                        queryExpression = queryExpression + tokens[i] + " ";
                    }
                    try {
                        resultGraph = storage.getVertices(queryExpression.trim());
                    } catch (Exception badQuery) {
                        return null;
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
                        resultGraph = storage.getLineage(vertexId, depth, direction, terminatingExpression);
                    } catch (Exception badQuery) {
                        return null;
                    }
                } else if (tokens[2].equalsIgnoreCase("paths")) {
                    String srcVertexId = tokens[3];
                    String dstVertexId = tokens[4];
                    int maxLength = Integer.parseInt(tokens[5]);
                    try {
                        resultGraph = storage.getPaths(srcVertexId, dstVertexId, maxLength);
                    } catch (Exception badQuery) {
                        return null;
                    }
                } else {
                    return null;
                }
            }
        }
        return resultGraph;
    }

    // Call the main query method.
    public static void queryCommand(String line, PrintStream output) {
        Graph resultGraph = query(line);
        if (resultGraph != null) {
            String[] tokens = line.split("\\s");
            String outputFile = tokens[tokens.length - 1];
            if (tokens[2].equalsIgnoreCase("vertices")) {
                Iterator resultIterator = resultGraph.vertexSet().iterator();
                while (resultIterator.hasNext()) {
                    AbstractVertex tempVertex = (AbstractVertex) resultIterator.next();
                    output.println("[" + tempVertex.toString() + "]");
                }
            } else if (tokens[2].equalsIgnoreCase("lineage")) {
                resultGraph.exportDOT(outputFile);
                output.println("Exported graph to " + outputFile);
            } else if (tokens[2].equalsIgnoreCase("paths")) {
                resultGraph.exportDOT(outputFile);
                output.println("Exported graph to " + outputFile);
            }
        } else {
            output.println("Error: Please check query expression");
        }
    }

    // Method to display control commands to the output stream. The control and query
    // commands are displayed using separate methods since these commands are issued
    // from different shells.
    public static void showCommands() {
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

    public static void addCommand(String line) {
        String[] tokens = line.split("\\s", 4);
        try {
            if (tokens[1].equalsIgnoreCase("reporter")) {
                addReporter(tokens[2], tokens[3]);
            } else if (tokens[1].equalsIgnoreCase("storage")) {
                addStorage(tokens[2], tokens[3]);
            } else if (tokens[1].equalsIgnoreCase("filter")) {
                addFilter(tokens[2], tokens[3]);
            } else if (tokens[1].equalsIgnoreCase("sketch")) {
                addSketch(tokens[2], tokens[3]);
            } else {
                throw new Exception();
            }
        } catch (Exception addCommandException) {
            outputStream.println("Usage: add reporter|storage <class name> <initialization arguments>");
            outputStream.println("       add filter <class name> <index>");
            outputStream.println("       add sketch <class name> <storage class name>");
        }
    }

    public static void listCommand(String line) {
        String[] tokens = line.split("\\s");
        try {
            if (tokens[1].equalsIgnoreCase("reporters")) {
                if (reporters.isEmpty()) {
                    // Nothing to list if the set of reporters is empty.
                    outputStream.println("No reporters added");
                    return;
                }
                outputStream.println(reporters.size() + " reporter(s) added:");
                Iterator iterator = reporters.iterator();
                int count = 1;
                while (iterator.hasNext()) {
                    // Iterate through the set of reporters, printing their names and arguments.
                    AbstractReporter reporter = (AbstractReporter) iterator.next();
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
                Iterator iterator = storages.iterator();
                int count = 1;
                while (iterator.hasNext()) {
                    // Iterate through the set of storages, printing their names and arguments.
                    AbstractStorage storage = (AbstractStorage) iterator.next();
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
            } else if (tokens[1].equalsIgnoreCase("sketches")) {
                if (sketches.isEmpty()) {
                    // Nothing to list if the set of sketches is empty.
                    outputStream.println("No sketches added");
                    return;
                }
                outputStream.println(sketches.size() + " sketch(es) added:");
                Iterator iterator = sketches.iterator();
                int count = 1;
                while (iterator.hasNext()) {
                    // Iterate through the set of sketches, printing their names.
                    AbstractSketch sketch = (AbstractSketch) iterator.next();
                    outputStream.println("\t" + count + ". " + sketch.getClass().getName().split("\\.")[2]);
                    count++;
                }
            } else if (tokens[1].equalsIgnoreCase("all")) {
                listCommand("list reporters");
                listCommand("list filters");
                listCommand("list storages");
                listCommand("list sketches");
            } else {
                throw new Exception();
            }
        } catch (Exception listCommandException) {
            outputStream.println("Usage: list reporters|storages|filters|sketches|all");
        }
    }

    public static void removeCommand(String line) {
        String[] tokens = line.split("\\s");
        try {
            if (tokens[1].equalsIgnoreCase("reporter")) {
                boolean found = false;
                Iterator iterator = reporters.iterator();
                while (iterator.hasNext()) {
                    // Iterate through the set of reporters until one is found which
                    // matches the given argument by its name.
                    AbstractReporter reporter = (AbstractReporter) iterator.next();
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
                            Thread.sleep(200);
                        }
                        iterator.remove();
                        // reporterStrings and reporterCompletor are only used for tab completion
                        // in the command terminal.
                        reporterStrings.remove(tokens[2]);
                        reporterCompletor.setCandidateStrings(new String[]{});
                        for (int i = 0; i < reporterStrings.size(); i++) {
                            reporterCompletor.addCandidateString((String) reporterStrings.get(i));
                        }
                        outputStream.println("done");
                        break;
                    }
                }
                if (!found) {
                    outputStream.println("Reporter " + tokens[2] + " not found");
                }
            } else if (tokens[1].equalsIgnoreCase("storage")) {
                boolean found = false;
                Iterator iterator = storages.iterator();
                while (iterator.hasNext()) {
                    // Iterate through the set of storages until one is found which
                    // matches the given argument by its name.
                    AbstractStorage storage = (AbstractStorage) iterator.next();
                    if (storage.getClass().getName().equals("spade.storage." + tokens[2])) {
                        // Mark the storage for removal by adding it to the removestorages set.
                        // This will enable the main SPADE thread to safely commit any transactions
                        // and then remove the storage.
                        removestorages.add(storage);
                        found = true;
                        outputStream.print("Shutting down storage " + tokens[2] + "... ");
                        while (removestorages.contains(storage)) {
                            // Wait for other thread to safely remove storage
                            Thread.sleep(200);
                        }
                        iterator.remove();
                        // storageStrings and storageCompletor are only used for tab completion
                        // in the command terminal.
                        storageStrings.remove(tokens[2]);
                        storageCompletor.setCandidateStrings(new String[]{});
                        for (int i = 0; i < storageStrings.size(); i++) {
                            storageCompletor.addCandidateString((String) storageStrings.get(i));
                        }
                        outputStream.println("done");
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
            } else if (tokens[1].equalsIgnoreCase("sketch")) {
                boolean found = false;
                Iterator iterator = sketches.iterator();
                while (iterator.hasNext()) {
                    // Iterate through the set of sketches until one is found which
                    // matches the given argument by its name.
                    AbstractSketch sketch = (AbstractSketch) iterator.next();
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
            outputStream.println("       remove filter <index>");
            removeCommandException.printStackTrace(errorStream);
        }
    }

    public static void addReporter(String classname, String arguments) {
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
                reporter.arguments = arguments;
                buffers.put(reporter, buffer);
                reporters.add(reporter);
                reporterStrings.add(classname);
                reporterCompletor.setCandidateStrings(new String[]{});
                for (int i = 0; i < reporterStrings.size(); i++) {
                    reporterCompletor.addCandidateString((String) reporterStrings.get(i));
                }
                outputStream.println("done");
            } else {
                outputStream.println("failed");
            }
        } catch (Exception addReporterException) {
            outputStream.println("Error: Unable to add reporter " + classname + " - please check class name");
            addReporterException.printStackTrace(errorStream);
        }
    }

    public static void addStorage(String classname, String arguments) {
        try {
            // Get the storage by classname and create a new instance.
            AbstractStorage storage = (AbstractStorage) Class.forName("spade.storage." + classname).newInstance();
            outputStream.print("Adding storage " + classname + "... ");
            if (storage.initialize(arguments)) {
                // The initialize() method must return true to indicate successful startup.
                // On true, the storage is added to the storages set.
                storage.arguments = arguments;
                storages.add(storage);
                storageStrings.add(classname);
                storageCompletor.setCandidateStrings(new String[]{});
                for (int i = 0; i < storageStrings.size(); i++) {
                    storageCompletor.addCandidateString((String) storageStrings.get(i));
                }
                outputStream.println("done");
            } else {
                outputStream.println("failed");
            }
        } catch (Exception addStorageException) {
            outputStream.println("Error: Unable to add storage " + classname + " - please check class name and argument");
            addStorageException.printStackTrace(errorStream);
        }
    }

    public static void addSketch(String classname, String storagename) {
        try {
            // Get the sketch by classname and create a new instance.
            AbstractSketch sketch = (AbstractSketch) Class.forName("spade.sketch." + classname).newInstance();
            // The argument is the storage class to which this sketch must refernce.
            boolean found = false;
            Iterator iterator = storages.iterator();
            while (iterator.hasNext()) {
                // Iterate through the set of storages until one is found which
                // matches the given argument by its name.
                AbstractStorage storage = (AbstractStorage) iterator.next();
                if (storage.getClass().getName().equals("spade.storage." + storagename)) {
                    sketch.storage = storage;
                    found = true;
                }
            }
            if (!found) {
                throw new Exception();
            }
            outputStream.print("Adding sketch " + classname + "... ");
            sketches.add(sketch);
            outputStream.println("done");
        } catch (Exception addFilterException) {
            outputStream.println("Error: Unable to add sketch " + classname + " - please check class name and storage name");
            addFilterException.printStackTrace(errorStream);
        }
    }

    public static void addFilter(String classname, String arguments) {
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
            addFilterException.printStackTrace(errorStream);
        }
    }

    public static void shutdown() {
        Iterator iterator = storages.iterator();
        outputStream.print("Shutting down storages... ");
        while (iterator.hasNext()) {
            // Shut down all storages.
            AbstractStorage storage = (AbstractStorage) iterator.next();
            storage.shutdown();
        }
        outputStream.println("done");
        outputStream.println("Terminating kernel...\n");
        try {
            // Remove the control and query pipes.
            Runtime.getRuntime().exec("rm -f " + controlPipeInputPath + " " + controlPipeOutputPath + " " + queryPipeInputPath).waitFor();
        } catch (Exception exception) {
            exception.printStackTrace(errorStream);
        }
        System.exit(0);
    }
}

class FinalCommitFilter extends AbstractFilter {

    // Reference to the set of storages maintained by the Kernel.
    public Set storages;
    public Set sketches;

    // This filter is the last filter in the list so any vertices or edges
    // received by it need to be passed to the storages. On receiving any
    // provenance elements, iterate through the set of storages and pass
    // the element to each storage.

    @Override
    public void putVertex(AbstractVertex incomingVertex) {
        Iterator storageIterator = storages.iterator();
        while (storageIterator.hasNext()) {
            ((AbstractStorage) storageIterator.next()).putVertex(incomingVertex);
        }
        Iterator sketchIterator = sketches.iterator();
        while (sketchIterator.hasNext()) {
            ((AbstractSketch) sketchIterator.next()).putVertex(incomingVertex);
        }
    }

    @Override
    public void putEdge(AbstractEdge incomingEdge) {
        Iterator storageIterator = storages.iterator();
        while (storageIterator.hasNext()) {
            ((AbstractStorage) storageIterator.next()).putEdge(incomingEdge);
        }
        Iterator sketchIterator = sketches.iterator();
        while (sketchIterator.hasNext()) {
            ((AbstractSketch) sketchIterator.next()).putEdge(incomingEdge);
        }
    }
}
