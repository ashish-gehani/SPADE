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
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import jline.ArgumentCompletor;
import jline.ConsoleReader;
import jline.MultiCompletor;
import jline.NullCompletor;
import jline.SimpleCompletor;


import javax.swing.*;
import com.mxgraph.layout.hierarchical.*;
import com.mxgraph.swing.*;
import com.mxgraph.view.*;


public class Kernel {

    private static Set reporters;
    private static Set storages;
    private static Set removereporters;
    private static Set removestorages;
    private static List filters;
    private static Map<AbstractReporter, Buffer> buffers;
    private static volatile boolean shutdown;
    private static volatile boolean flushTransactions;
    private static final String historyFile = "spade.history";
    private static final String configFile = "spade.config";
    private static ArrayList<String> reporterStrings;
    private static ArrayList<String> storageStrings;
    private static SimpleCompletor reporterCompletor;
    private static SimpleCompletor storageCompletor;

//    private static TestViz testViz;

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

//        testViz = new TestViz();
//        testViz.setSize(800, 600);
        FinalCommitFilter commitfilter = new FinalCommitFilter();
//        commitfilter.testViz = testViz;
        commitfilter.setStorages(storages);
        filters.add(commitfilter);

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
                                System.out.println("done");
                                shutdown();
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
                    exception.printStackTrace(System.err);
                }
            }
        };
        new Thread(mainRunnable).start();

        Runnable consoleRunnable = new Runnable() {

            public void run() {
                try {
                    System.out.println("");
                    System.out.println("SPADE 2.0 Kernel");
                    System.out.println("");

                    configCommand("config load " + configFile);
                    System.out.println("");

                    showCommands();
                    ConsoleReader commandReader = new ConsoleReader();
                    commandReader.getHistory().setHistoryFile(new File(historyFile));

                    List argCompletor1 = new LinkedList();
                    argCompletor1.add(new SimpleCompletor(new String[]{"add"}));
                    argCompletor1.add(new SimpleCompletor(new String[]{"filter", "storage", "reporter"}));
                    argCompletor1.add(new NullCompletor());

                    List argCompletor2 = new LinkedList();
                    argCompletor2.add(new SimpleCompletor(new String[]{"remove"}));
                    argCompletor2.add(new SimpleCompletor(new String[]{"filter"}));
                    argCompletor2.add(new NullCompletor());

                    List argCompletor3 = new LinkedList();
                    argCompletor3.add(new SimpleCompletor(new String[]{"remove"}));
                    argCompletor3.add(new SimpleCompletor(new String[]{"storage"}));
                    argCompletor3.add(storageCompletor);
                    argCompletor3.add(new NullCompletor());

                    List argCompletor4 = new LinkedList();
                    argCompletor4.add(new SimpleCompletor(new String[]{"remove"}));
                    argCompletor4.add(new SimpleCompletor(new String[]{"reporter"}));
                    argCompletor4.add(reporterCompletor);
                    argCompletor4.add(new NullCompletor());

                    List argCompletor5 = new LinkedList();
                    argCompletor5.add(new SimpleCompletor(new String[]{"list"}));
                    argCompletor5.add(new SimpleCompletor(new String[]{"filters", "storages", "reporters", "all"}));
                    argCompletor5.add(new NullCompletor());

                    List argCompletor6 = new LinkedList();
                    argCompletor6.add(new SimpleCompletor(new String[]{"query"}));
                    argCompletor6.add(storageCompletor);
                    argCompletor6.add(new SimpleCompletor(new String[]{"vertices", "lineage"}));
                    argCompletor6.add(new NullCompletor());

                    List argCompletor7 = new LinkedList();
                    argCompletor7.add(new SimpleCompletor(new String[]{"config"}));
                    argCompletor7.add(new SimpleCompletor(new String[]{"load", "save"}));
                    argCompletor7.add(new NullCompletor());

                    List completors = new LinkedList();
                    completors.add(new ArgumentCompletor(argCompletor1));
                    completors.add(new ArgumentCompletor(argCompletor2));
                    completors.add(new ArgumentCompletor(argCompletor3));
                    completors.add(new ArgumentCompletor(argCompletor4));
                    completors.add(new ArgumentCompletor(argCompletor5));
                    completors.add(new ArgumentCompletor(argCompletor6));
                    completors.add(new ArgumentCompletor(argCompletor7));

                    commandReader.addCompletor(new MultiCompletor(completors));

                    while (true) {
                        System.out.println("");
                        String line = commandReader.readLine("-> ");
                        if (executeCommand(line) == false) {
                            break;
                        }
                    }
                } catch (Exception exception) {
                    exception.printStackTrace(System.err);
                }
            }
        };
        new Thread(consoleRunnable).start();

//        testViz.setVisible(true);

    }

    public static boolean executeCommand(String line) {
        String command = line.split("\\s")[0];
        if (command.equalsIgnoreCase("exit")) {
            configCommand("config save " + configFile);
            Iterator itp = reporters.iterator();
            System.out.print("Shutting down reporters... ");
            while (itp.hasNext()) {
                AbstractReporter reporter = (AbstractReporter) itp.next();
                reporter.shutdown();
            }
            System.out.println("done");
            System.out.print("Flushing buffers... ");
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
            queryCommand(line);
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
                System.out.println("Finished loading configuration file");
            } else if (tokens[1].equalsIgnoreCase("save")) {
                System.out.print("Saving configuration... ");
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
                System.out.println("done");
            } else {
                throw new Exception();
            }
        } catch (Exception configCommandException) {
            System.out.println("Usage: config load|save <filename>");
        }
    }

    public static void queryCommand(String line) {
        flushTransactions = true;
        while (flushTransactions) {
            // wait for other thread to flush transactions
        }
        try {
            String[] tokens = line.split("\\s");
            Iterator iterator = storages.iterator();
            if (storages.isEmpty()) {
                System.out.println("No storage(s) added");
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
                            System.out.println("Error: Please check query expression");
                            badQuery.printStackTrace(System.err);
                            return;
                        }
                        Iterator resultIterator = resultSet.iterator();
                        while (resultIterator.hasNext()) {
                            AbstractVertex tempVertex = (AbstractVertex) resultIterator.next();
                            System.out.println("[" + tempVertex.toString() + "]");
                        }
                    } else if (tokens[2].equalsIgnoreCase("lineage")) {
                        Lineage resultLineage = null;
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
                            System.out.println("Error: Please check query expression");
                            badQuery.printStackTrace(System.err);
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
            System.out.println("Usage: query <class name> vertices <expression>");
            System.out.println("       query <class name> lineage <vertex id> <depth> <direction> <terminating expression> <output file>");
        }
    }

    public static void showCommands() {
        System.out.println("Available commands:");
        System.out.println("       add reporter|storage <class name> <initialization arguments>");
        System.out.println("       add filter <class name> <index>");
        System.out.println("       remove reporter|storage <class name>");
        System.out.println("       remove filter <index>");
        System.out.println("       list reporters|storages|filters|all");
        System.out.println("       query <class name> vertices <expression>");
        System.out.println("       query <class name> lineage <vertex id> <depth> <direction> <terminating expression> <output file>");
        System.out.println("       config load|save <filename>");
        System.out.println("       exit");
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
            System.out.println("Usage: add reporter|storage <class name> <initialization arguments>");
            System.out.println("       add filter <class name> <index>");
        }
    }

    public static void listCommand(String line) {
        String[] tokens = line.split("\\s");
        try {
            if (tokens[1].equalsIgnoreCase("reporters")) {
                if (reporters.isEmpty()) {
                    System.out.println("No reporters added");
                    return;
                }
                System.out.println(reporters.size() + " reporter(s) added:");
                Iterator iterator = reporters.iterator();
                int count = 1;
                while (iterator.hasNext()) {
                    AbstractReporter reporter = (AbstractReporter) iterator.next();
                    String arguments = reporter.arguments;
                    System.out.println("\t" + count + ". " + reporter.getClass().getName().split("\\.")[2] + " (" + arguments + ")");
                    count++;
                }
            } else if (tokens[1].equalsIgnoreCase("storages")) {
                if (storages.isEmpty()) {
                    System.out.println("No storages added");
                    return;
                }
                System.out.println(storages.size() + " storage(s) added:");
                Iterator iterator = storages.iterator();
                int count = 1;
                while (iterator.hasNext()) {
                    AbstractStorage storage = (AbstractStorage) iterator.next();
                    String arguments = storage.arguments;
                    System.out.println("\t" + count + ". " + storage.getClass().getName().split("\\.")[2] + " (" + arguments + ")");
                    count++;
                }
            } else if (tokens[1].equalsIgnoreCase("filters")) {
                if (filters.size() == 1) {
                    System.out.println("No filters added");
                    return;
                }
                System.out.println((filters.size() - 1) + " filter(s) added:");
                for (int i = 0; i < filters.size() - 1; i++) {
                    System.out.println("\t" + (i + 1) + ". " + filters.get(i).getClass().getName().split("\\.")[2]);
                }
            } else if (tokens[1].equalsIgnoreCase("all")) {
                listCommand("list reporters");
                listCommand("list filters");
                listCommand("list storages");
            } else {
                throw new Exception();
            }
        } catch (Exception listCommandException) {
            System.out.println("Usage: list reporters|storages|filters|all");
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
                        System.out.print("Shutting down reporter " + tokens[2] + "... ");
                        while (removereporters.contains(reporter)) {
                            // wait for other thread to safely remove reporter
                        }
                        reporterStrings.remove(tokens[2]);
                        reporterCompletor.setCandidateStrings(new String[]{});
                        for (int i = 0; i < reporterStrings.size(); i++) {
                            reporterCompletor.addCandidateString((String) reporterStrings.get(i));
                        }
                        System.out.println("done");
                    }
                }
                if (!found) {
                    System.out.println("Reporter " + tokens[2] + " not found");
                }
            } else if (tokens[1].equalsIgnoreCase("storage")) {
                boolean found = false;
                Iterator iterator = storages.iterator();
                while (iterator.hasNext()) {
                    AbstractStorage storage = (AbstractStorage) iterator.next();
                    if (storage.getClass().getName().equals("spade.storage." + tokens[2])) {
                        removestorages.add(storage);
                        found = true;
                        System.out.print("Shutting down storage " + tokens[2] + "... ");
                        while (removestorages.contains(storage)) {
                            // wait for other thread to safely remove storage
                        }
                        storageStrings.remove(tokens[2]);
                        storageCompletor.setCandidateStrings(new String[]{});
                        for (int i = 0; i < storageStrings.size(); i++) {
                            storageCompletor.addCandidateString((String) storageStrings.get(i));
                        }
                        System.out.println("done");
                    }
                }
                if (!found) {
                    System.out.println("Storage " + tokens[2] + " not found");
                }
            } else if (tokens[1].equalsIgnoreCase("filter")) {
                int index = Integer.parseInt(tokens[2]);
                if ((index <= 0) || (index >= filters.size())) {
                    System.out.println("Error: Unable to remove filter - bad index");
                    return;
                }
                String filterName = filters.get(index - 1).getClass().getName();
                System.out.print("Removing filter " + filterName.split("\\.")[2] + "... ");
                if (index > 1) {
                    ((AbstractFilter) filters.get(index - 2)).setNextFilter((AbstractFilter) filters.get(index));
                }
                filters.remove(index - 1);
                System.out.println("done");
            } else {
                throw new Exception();
            }
        } catch (Exception removeCommandException) {
            System.out.println("Usage: remove reporter|storage <class name>");
            System.out.println("       remove filter <index>");
            removeCommandException.printStackTrace(System.err);
        }
    }

    public static void addReporter(String classname, String arguments) {
        try {
            AbstractReporter reporter = (AbstractReporter) Class.forName("spade.reporter." + classname).newInstance();
            System.out.print("Adding reporter " + classname + "... ");
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
                System.out.println("done");
            } else {
                System.out.println("failed");
            }
        } catch (Exception addReporterException) {
            System.out.println("Error: Unable to add reporter " + classname + " - please check class name");
            addReporterException.printStackTrace(System.err);
        }
    }

    public static void addStorage(String classname, String arguments) {
        try {
            AbstractStorage storage = (AbstractStorage) Class.forName("spade.storage." + classname).newInstance();
            System.out.print("Adding storage " + classname + "... ");
            if (storage.initialize(arguments)) {
                storage.arguments = arguments;
                storages.add(storage);
                storageStrings.add(classname);
                storageCompletor.setCandidateStrings(new String[]{});
                for (int i = 0; i < storageStrings.size(); i++) {
                    storageCompletor.addCandidateString((String) storageStrings.get(i));
                }
                System.out.println("done");
            } else {
                System.out.println("failed");
            }
        } catch (Exception addStorageException) {
            System.out.println("Error: Unable to add storage " + classname + " - please check class name and argument");
            addStorageException.printStackTrace(System.err);
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
            System.out.print("Adding filter " + classname + "... ");
            filters.add(index, filter);
            System.out.println("done");
        } catch (Exception addFilterException) {
            System.out.println("Error: Unable to add filter " + classname + " - please check class name and index");
            addFilterException.printStackTrace(System.err);
        }
    }

    public static void shutdown() {
        Iterator iterator = storages.iterator();
        System.out.print("Shutting down storages... ");
        while (iterator.hasNext()) {
            AbstractStorage storage = (AbstractStorage) iterator.next();
            storage.shutdown();
        }
        System.out.println("done");
        System.out.println("Terminating kernel...\n");
        System.exit(0);
    }
}

class FinalCommitFilter extends AbstractFilter {

    private Set storages;
//    public TestViz testViz;

    public void setStorages(Set mainStorageSet) {
        storages = mainStorageSet;
    }

    @Override
    public void putVertex(AbstractVertex incomingVertex) {
//        incomingVertex.removeAnnotation("source_reporter");
        Iterator iterator = storages.iterator();
        while (iterator.hasNext()) {
            ((AbstractStorage) iterator.next()).putVertex(incomingVertex);
        }
//        testViz.putVertex(incomingVertex);
    }

    @Override
    public void putEdge(AbstractEdge incomingEdge) {
//        incomingEdge.removeAnnotation("source_reporter");
        Iterator iterator = storages.iterator();
        while (iterator.hasNext()) {
            ((AbstractStorage) iterator.next()).putEdge(incomingEdge);
        }
//        testViz.putEdge(incomingEdge);
    }
}

class TestViz extends JFrame {

    private mxGraph graph;
    private mxHierarchicalLayout layout;
    private HashMap vertices;
    private Object parent;

    public TestViz() {
        vertices = new HashMap();
        graph = new mxGraph();
        graph.setCellsEditable(false);
        graph.setCellsMovable(false);
        graph.setCellsResizable(false);
        graph.setCellsDeletable(false);
        graph.setCellsCloneable(false);
        graph.setCellsBendable(false);
        graph.setCellsDisconnectable(false);
        graph.setAllowDanglingEdges(false);
        graph.setConnectableEdges(false);
        parent = graph.getDefaultParent();

        layout = new mxHierarchicalLayout(graph);
        mxGraphComponent graphComponent = new mxGraphComponent(graph);
        getContentPane().add(graphComponent);
        Runnable refreshLayout = new Runnable() {

            public void run() {
                while (true) {
                    layout.execute(graph.getDefaultParent());
                    try {
                        Thread.sleep(4000);
                    } catch (Exception exception) {
                        exception.printStackTrace(System.err);
                    }
                }
            }
        };
        new Thread(refreshLayout).start();
    }
    
    public void putEdge(AbstractEdge incomingEdge) {
        String label = incomingEdge.toString();
        String style = "";
        if (incomingEdge instanceof spade.opm.edge.Used) {
            style = "strokeColor=#00cd00";
        } else if (incomingEdge instanceof spade.opm.edge.WasGeneratedBy) {
            style = "strokeColor=#ff0000";
        } else if (incomingEdge instanceof spade.opm.edge.WasTriggeredBy) {
            style = "strokeColor=#0000ff";
        } else if (incomingEdge instanceof spade.opm.edge.WasControlledBy) {
            style = "strokeColor=#d050ff";
        } else if (incomingEdge instanceof spade.opm.edge.WasDerivedFrom) {
            style = "strokeColor=#ee9a00";
        }

        graph.getModel().beginUpdate();
        graph.insertEdge(graph.getDefaultParent(), null, label,
                vertices.get(incomingEdge.getSrcVertex()), vertices.get(incomingEdge.getDstVertex()), "fontSize=12;fontColor=black;" + style);
        graph.getModel().endUpdate();
    }

    public void putVertex(AbstractVertex incomingVertex) {
        String label = incomingVertex.toString().replaceAll("\\|", "\n");
        String style = "";
        if (incomingVertex instanceof spade.opm.vertex.Process) {
            style = "fillColor=#cae1ff";
        } else if (incomingVertex instanceof spade.opm.vertex.Artifact) {
            style = "fillColor=#fff68f";
        } else {
            style = "fillColor=#ffc1c1";
        }
        graph.getModel().beginUpdate();
        Object tmpObject = graph.insertVertex(graph.getDefaultParent(), null, label, 0, 0, 0, 0, "shadow=1;fontSize=12;fontColor=black;strokeColor=black;spacing=5;" + style);
        graph.updateCellSize(tmpObject);
        graph.getModel().endUpdate();
        vertices.put(incomingVertex, tmpObject);
    }
}