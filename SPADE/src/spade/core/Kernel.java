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
import java.util.Vector;
import jline.SimpleCompletor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;


public class Kernel {

    private static Set reporters;
    private static Set storages;
    private static Set removereporters;
    private static Set removestorages;
    private static List filters;
    private static Map<AbstractReporter, Buffer> buffers;
    private static volatile boolean shutdown;
    private static volatile boolean flushTransactions;
    private static final String configFile = "spade.config";
    private static ArrayList<String> reporterStrings;
    private static ArrayList<String> storageStrings;
    private static SimpleCompletor reporterCompletor;
    private static SimpleCompletor storageCompletor;

    private static PrintStream outputStream = System.out;
    private static PrintStream errorStream = System.err;
    private static String queryPipeInputPath = "queryPipeIn";
    private static String controlPipeInputPath = "controlPipeIn";
    private static String controlPipeOutputPath = "controlPipeOut";

    private static ServerSocket queryServerSocket;
    private static SketchManager sketchManager;

    public static void main(String args[]) {

        shutdown = false;
        flushTransactions = false;
        reporters = Collections.synchronizedSet(new HashSet());
        storages = Collections.synchronizedSet(new HashSet());
        removereporters = Collections.synchronizedSet(new HashSet());
        removestorages = Collections.synchronizedSet(new HashSet());
        filters = Collections.synchronizedList(new Vector());
        buffers = Collections.synchronizedMap(new HashMap<AbstractReporter, Buffer>());

        reporterStrings = new ArrayList<String>();
        storageStrings = new ArrayList<String>();
        reporterCompletor = new SimpleCompletor("");
        storageCompletor = new SimpleCompletor("");

        sketchManager = new SketchManager();

        FinalCommitFilter commitFilter = new FinalCommitFilter();
        commitFilter.sketchManager = sketchManager;
        commitFilter.setStorages(storages);
        filters.add(commitFilter);

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
                                String line = queryInputStream.readLine();
                                if (line != null) {
                                    String[] queryTokens = line.split("\\s", 3);
                                    if (queryTokens[0].equalsIgnoreCase("query")) {
                                        PrintStream queryOutputStream = new PrintStream(new FileOutputStream(queryTokens[1]));
                                        queryCommand("query " + queryTokens[2], queryOutputStream);
                                        queryOutputStream.close();
                                    }
                                }
                                Thread.sleep(10);
                            }
                            queryInputStream.close();
                        } catch (Exception exception) {
                            exception.printStackTrace(errorStream);
                        }
                    }
                };
                new Thread(queryThread).start();
            }
        } catch (Exception ex) {
            ex.printStackTrace(errorStream);
        }

        Runnable mainRunnable = new Runnable() {

            public void run() {
                try {
                    while (true) {
                        if (shutdown) {
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
                            storages.remove(storage);
                            iterator.remove();
                        }
                        for (Iterator iterator = buffers.keySet().iterator(); iterator.hasNext();) {
                            AbstractReporter reporter = (AbstractReporter) iterator.next();
                            Object bufferelement = ((Buffer) buffers.get(reporter)).getBufferElement();
                            if (bufferelement instanceof AbstractVertex) {
                                AbstractVertex tempVertex = (AbstractVertex) bufferelement;
                                tempVertex.addAnnotation("source_reporter", reporter.getClass().getName());
                                ((AbstractFilter) filters.get(0)).putVertex(tempVertex);
                            } else if (bufferelement instanceof AbstractEdge) {
                                AbstractEdge tempEdge = (AbstractEdge) bufferelement;
                                tempEdge.addAnnotation("source_reporter", reporter.getClass().getName());
                                ((AbstractFilter) filters.get(0)).putEdge((AbstractEdge) bufferelement);
                            } else if ((bufferelement == null) && (removereporters.contains(reporter))) {
                                reporter.shutdown();
                                reporters.remove(reporter);
                                removereporters.remove(reporter);
                                iterator.remove();
                            }
                        }
                        Thread.sleep(5);
                    }
                } catch (Exception exception) {
                    exception.printStackTrace(errorStream);
                }
            }
        };
        new Thread(mainRunnable).start();


        Runnable daemonRunnable = new Runnable() {

            public void run() {
                try {
                    int exitValue1 = Runtime.getRuntime().exec("mkfifo " + controlPipeInputPath).waitFor();
                    int exitValue2 = Runtime.getRuntime().exec("mkfifo " + controlPipeOutputPath).waitFor();
                    if (exitValue1 != 0 && exitValue2 != 0) {
                        errorStream.println("Error creating control pipes!");
                    } else {
                        outputStream.println("");
                        outputStream.println("SPADE 2.0 Kernel");
                        outputStream.println("");

                        configCommand("config load " + configFile);
                        outputStream.println("");

                        BufferedReader controlInputStream = new BufferedReader(new FileReader(controlPipeInputPath));
                        PrintStream controlOutputStream = new PrintStream(new FileOutputStream(controlPipeOutputPath));
                        outputStream = controlOutputStream;
                        errorStream = controlOutputStream;
                        while (true) {
                            // String line = commandReader.readLine("-> ");
                            String line = controlInputStream.readLine();
                            if (executeCommand(line) == false) {
                                break;
                            }
                        }
                    }
                } catch (Exception exception) {
                    exception.printStackTrace(errorStream);
                }
            }
        };
        new Thread(daemonRunnable).start();
    }

    public static boolean executeCommand(String line) {
        String command = line.split("\\s")[0];
        if (command.equalsIgnoreCase("exit")) {
            configCommand("config save " + configFile);
            Iterator itp = reporters.iterator();
            outputStream.print("Shutting down reporters... ");
            while (itp.hasNext()) {
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
            outputStream.println("Usage: config load|save <filename>");
        }
    }

    public static void queryCommand(String line, PrintStream output) {
        flushTransactions = true;
        while (flushTransactions) {
            // wait for other thread to flush transactions
        }
        try {
            String[] tokens = line.split("\\s");
            Iterator iterator = storages.iterator();
            if (storages.isEmpty()) {
                output.println("No storage(s) added");
                return;
            }
            while (iterator.hasNext()) {
                AbstractStorage storage = (AbstractStorage) iterator.next();
                if (storage.getClass().getName().equals("spade.storage." + tokens[1])) {
                    if (tokens[2].equalsIgnoreCase("vertices")) {
                        String queryExpression = "";
                        for (int i = 3; i < tokens.length; i++) {
                            queryExpression = queryExpression + tokens[i] + " ";
                        }
                        Set resultSet = null;
                        try {
                            resultSet = storage.getVertices(queryExpression.trim());
                        } catch (Exception badQuery) {
                            outputStream.println("Error: Please check query expression");
                            badQuery.printStackTrace(errorStream);
                            return;
                        }
                        Iterator resultIterator = resultSet.iterator();
                        while (resultIterator.hasNext()) {
                            AbstractVertex tempVertex = (AbstractVertex) resultIterator.next();
                            output.println("[" + tempVertex.toString() + "]");
                        }
                    } else if (tokens[2].equalsIgnoreCase("lineage")) {
                        Graph resultLineage = null;
                        String vertexId = tokens[3];
                        int depth = Integer.parseInt(tokens[4]);
                        String direction = tokens[5];
                        String terminatingExpression = "";
                        for (int i = 6; i < tokens.length - 1; i++) {
                            terminatingExpression = terminatingExpression + tokens[i] + " ";
                        }
                        try {
                            resultLineage = storage.getLineage(vertexId, depth, direction, terminatingExpression);
                            resultLineage.exportDOT(tokens[tokens.length - 1]);
                        } catch (Exception badQuery) {
                            outputStream.println("Error: Please check query expression");
                            badQuery.printStackTrace(errorStream);
                            return;
                        }
                    } else if (tokens[2].equalsIgnoreCase("paths")) {
                        Graph resultGraph = null;
                        String srcVertexId = tokens[3];
                        String dstVertexId = tokens[4];
                        int maxLength = Integer.parseInt(tokens[5]);
                        try {
                            resultGraph = storage.getPaths(srcVertexId, dstVertexId, maxLength);
                            resultGraph.exportDOT(tokens[6]);
                        } catch (Exception badQuery) {
                            outputStream.println("Error: Please check query expression");
                            badQuery.printStackTrace(errorStream);
                            return;
                        }
                    } else {
                        throw new Exception();
                    }
                    return;
                }
            }
            throw new Exception();
        } catch (Exception exception) {
        }
    }

    public static void showCommands() {
        outputStream.println("Available commands:");
        outputStream.println("       add reporter|storage <class name> <initialization arguments>");
        outputStream.println("       add filter <class name> <index>");
        outputStream.println("       remove reporter|storage <class name>");
        outputStream.println("       remove filter <index>");
        outputStream.println("       list reporters|storages|filters|all");
        outputStream.println("       config load|save <filename>");
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
            } else {
                throw new Exception();
            }
        } catch (Exception addCommandException) {
            outputStream.println("Usage: add reporter|storage <class name> <initialization arguments>");
            outputStream.println("       add filter <class name> <index>");
        }
    }

    public static void listCommand(String line) {
        String[] tokens = line.split("\\s");
        try {
            if (tokens[1].equalsIgnoreCase("reporters")) {
                if (reporters.isEmpty()) {
                    outputStream.println("No reporters added");
                    return;
                }
                outputStream.println(reporters.size() + " reporter(s) added:");
                Iterator iterator = reporters.iterator();
                int count = 1;
                while (iterator.hasNext()) {
                    AbstractReporter reporter = (AbstractReporter) iterator.next();
                    String arguments = reporter.arguments;
                    outputStream.println("\t" + count + ". " + reporter.getClass().getName().split("\\.")[2] + " (" + arguments + ")");
                    count++;
                }
            } else if (tokens[1].equalsIgnoreCase("storages")) {
                if (storages.isEmpty()) {
                    outputStream.println("No storages added");
                    return;
                }
                outputStream.println(storages.size() + " storage(s) added:");
                Iterator iterator = storages.iterator();
                int count = 1;
                while (iterator.hasNext()) {
                    AbstractStorage storage = (AbstractStorage) iterator.next();
                    String arguments = storage.arguments;
                    outputStream.println("\t" + count + ". " + storage.getClass().getName().split("\\.")[2] + " (" + arguments + ")");
                    count++;
                }
            } else if (tokens[1].equalsIgnoreCase("filters")) {
                if (filters.size() == 1) {
                    outputStream.println("No filters added");
                    return;
                }
                outputStream.println((filters.size() - 1) + " filter(s) added:");
                for (int i = 0; i < filters.size() - 1; i++) {
                    outputStream.println("\t" + (i + 1) + ". " + filters.get(i).getClass().getName().split("\\.")[2]);
                }
            } else if (tokens[1].equalsIgnoreCase("all")) {
                listCommand("list reporters");
                listCommand("list filters");
                listCommand("list storages");
            } else {
                throw new Exception();
            }
        } catch (Exception listCommandException) {
            outputStream.println("Usage: list reporters|storages|filters|all");
        }
    }

    public static void removeCommand(String line) {
        String[] tokens = line.split("\\s");
        try {
            if (tokens[1].equalsIgnoreCase("reporter")) {
                boolean found = false;
                Iterator iterator = reporters.iterator();
                while (iterator.hasNext()) {
                    AbstractReporter reporter = (AbstractReporter) iterator.next();
                    if (reporter.getClass().getName().equals("spade.reporter." + tokens[2])) {
                        removereporters.add(reporter);
                        found = true;
                        outputStream.print("Shutting down reporter " + tokens[2] + "... ");
                        while (removereporters.contains(reporter)) {
                            // wait for other thread to safely remove reporter
                        }
                        reporterStrings.remove(tokens[2]);
                        reporterCompletor.setCandidateStrings(new String[]{});
                        for (int i = 0; i < reporterStrings.size(); i++) {
                            reporterCompletor.addCandidateString((String) reporterStrings.get(i));
                        }
                        outputStream.println("done");
                    }
                }
                if (!found) {
                    outputStream.println("Reporter " + tokens[2] + " not found");
                }
            } else if (tokens[1].equalsIgnoreCase("storage")) {
                boolean found = false;
                Iterator iterator = storages.iterator();
                while (iterator.hasNext()) {
                    AbstractStorage storage = (AbstractStorage) iterator.next();
                    if (storage.getClass().getName().equals("spade.storage." + tokens[2])) {
                        removestorages.add(storage);
                        found = true;
                        outputStream.print("Shutting down storage " + tokens[2] + "... ");
                        while (removestorages.contains(storage)) {
                            // wait for other thread to safely remove storage
                        }
                        storageStrings.remove(tokens[2]);
                        storageCompletor.setCandidateStrings(new String[]{});
                        for (int i = 0; i < storageStrings.size(); i++) {
                            storageCompletor.addCandidateString((String) storageStrings.get(i));
                        }
                        outputStream.println("done");
                    }
                }
                if (!found) {
                    outputStream.println("Storage " + tokens[2] + " not found");
                }
            } else if (tokens[1].equalsIgnoreCase("filter")) {
                int index = Integer.parseInt(tokens[2]);
                if ((index <= 0) || (index >= filters.size())) {
                    outputStream.println("Error: Unable to remove filter - bad index");
                    return;
                }
                String filterName = filters.get(index - 1).getClass().getName();
                outputStream.print("Removing filter " + filterName.split("\\.")[2] + "... ");
                if (index > 1) {
                    ((AbstractFilter) filters.get(index - 2)).setNextFilter((AbstractFilter) filters.get(index));
                }
                filters.remove(index - 1);
                outputStream.println("done");
            } else {
                throw new Exception();
            }
        } catch (Exception removeCommandException) {
            outputStream.println("Usage: remove reporter|storage <class name>");
            outputStream.println("       remove filter <index>");
            removeCommandException.printStackTrace(errorStream);
        }
    }

    public static void addReporter(String classname, String arguments) {
        try {
            AbstractReporter reporter = (AbstractReporter) Class.forName("spade.reporter." + classname).newInstance();
            outputStream.print("Adding reporter " + classname + "... ");
            Buffer buffer = new Buffer();
            reporter.setBuffer(buffer);
            if (reporter.launch(arguments)) {
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
            AbstractStorage storage = (AbstractStorage) Class.forName("spade.storage." + classname).newInstance();
            outputStream.print("Adding storage " + classname + "... ");
            if (storage.initialize(arguments)) {
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

    public static void addFilter(String classname, String arguments) {
        try {
            AbstractFilter filter = (AbstractFilter) Class.forName("spade.filter." + classname).newInstance();
            int index = Integer.parseInt(arguments);
            if (index >= filters.size()) {
                throw new Exception();
            }
            filter.setNextFilter((AbstractFilter) filters.get(index));
            if (index > 0) {
                ((AbstractFilter) filters.get(index - 1)).setNextFilter(filter);
            }
            outputStream.print("Adding filter " + classname + "... ");
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
            AbstractStorage storage = (AbstractStorage) iterator.next();
            storage.shutdown();
        }
        outputStream.println("done");
        outputStream.println("Terminating kernel...\n");
        try {
            Runtime.getRuntime().exec("rm -f " + queryPipeInputPath).waitFor();
            Runtime.getRuntime().exec("rm -f " + controlPipeInputPath + " " + controlPipeOutputPath).waitFor();
        } catch (Exception exception) {
            exception.printStackTrace(errorStream);
        }
        System.exit(0);
    }
}

class FinalCommitFilter extends AbstractFilter {

    private Set storages;
    public SketchManager sketchManager;

    public void setStorages(Set mainStorageSet) {
        storages = mainStorageSet;
    }

    @Override
    public void putVertex(AbstractVertex incomingVertex) {
        Iterator iterator = storages.iterator();
        while (iterator.hasNext()) {
            ((AbstractStorage) iterator.next()).putVertex(incomingVertex);
        }
        sketchManager.processVertex(incomingVertex);
    }

    @Override
    public void putEdge(AbstractEdge incomingEdge) {
        Iterator iterator = storages.iterator();
        while (iterator.hasNext()) {
            ((AbstractStorage) iterator.next()).putEdge(incomingEdge);
        }
        sketchManager.processEdge(incomingEdge);
    }
}
