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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.Vector;

public class Kernel {

    private static Set producers;
    private static Set storages;
    private static Set removeproducers;
    private static Set removestorages;
    private static List filters;
    private static Map<AbstractProducer, Buffer> buffers;
    private static volatile boolean shutdown;
    private static volatile boolean flushTransactions;

    public static void main(String args[]) {

        shutdown = false;
        flushTransactions = false;
        producers = Collections.synchronizedSet(new HashSet());
        storages = Collections.synchronizedSet(new HashSet());
        removeproducers = Collections.synchronizedSet(new HashSet());
        removestorages = Collections.synchronizedSet(new HashSet());
        filters = Collections.synchronizedList(new Vector());
        buffers = Collections.synchronizedMap(new HashMap<AbstractProducer, Buffer>());

        FinalCommitFilter commitfilter = new FinalCommitFilter();
        commitfilter.setStorages(storages);
        filters.add(commitfilter);

        Runnable mainRunnable = new Runnable() {

            public void run() {
                try {
                    while (true) {
                        if (shutdown) {
                            for (AbstractProducer producer : buffers.keySet()) {
                                if (((Buffer) buffers.get(producer)).isEmpty()) {
                                    buffers.remove(producer);
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
                            AbstractProducer producer = (AbstractProducer) iterator.next();
                            Object bufferelement = ((Buffer) buffers.get(producer)).getBufferElement();
                            if (bufferelement instanceof AbstractVertex) {
                                AbstractVertex tempVertex = (AbstractVertex) bufferelement;
                                tempVertex.addAnnotation("source_producer", producer.getClass().getName());
                                ((AbstractFilter) filters.get(0)).putVertex(tempVertex);
                            } else if (bufferelement instanceof AbstractEdge) {
                                AbstractEdge tempEdge = (AbstractEdge) bufferelement;
                                tempEdge.addAnnotation("source_producer", producer.getClass().getName());
                                ((AbstractFilter) filters.get(0)).putEdge((AbstractEdge) bufferelement);
                            } else if ((bufferelement == null) && (removeproducers.contains(producer))) {
                                producer.shutdown();
                                producers.remove(producer);
                                removeproducers.remove(producer);
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
                    showCommands();
                    Scanner commandReader = new Scanner(System.in);
                    while (true) {
                        System.out.println("");
                        System.out.print("-> ");
                        String line = commandReader.nextLine();
                        String command = line.split("\\s")[0];
                        if (command.equalsIgnoreCase("exit")) {
                            Iterator itp = producers.iterator();
                            System.out.print("Shutting down producers... ");
                            while (itp.hasNext()) {
                                AbstractProducer producer = (AbstractProducer) itp.next();
                                producer.shutdown();
                            }
                            System.out.println("done");
                            System.out.print("Flushing buffers... ");
                            shutdown = true;
                            break;
                        } else if (command.equalsIgnoreCase("add")) {
                            addCommand(line);
                        } else if (command.equalsIgnoreCase("list")) {
                            listCommand(line);
                        } else if (command.equalsIgnoreCase("remove")) {
                            removeCommand(line);
                        } else if (command.equalsIgnoreCase("query")) {
                            queryCommand(line);
                        } else {
                            showCommands();
                            continue;
                        }
                    }
                    commandReader.close();
                } catch (Exception exception) {
                    exception.printStackTrace(System.err);
                }
            }
        };
        new Thread(consoleRunnable).start();

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
                        for (int i=3; i<tokens.length; i++) {
                            queryExpression = queryExpression + tokens[i] + " ";
                        }
                        Set resultSet = null;
                        try {
                            resultSet = storage.getVertices(queryExpression.trim());
                        } catch (Exception badQuery) {
                            System.out.println("Error: Please check query expression");
                            return;
                        }
                        Iterator resultIterator = resultSet.iterator();
                        while (resultIterator.hasNext()) {
                            AbstractVertex tempVertex = (AbstractVertex) resultIterator.next();
                            System.out.println("[" + tempVertex.toString().replaceAll("\\|", "\t") + "]");
                        }
                    } else if (tokens[2].equalsIgnoreCase("lineage")) {
                        Lineage resultLineage = null;
                        String vertexId = tokens[3];
                        int depth = Integer.parseInt(tokens[4]);
                        String direction = tokens[5];
                        try {
                            resultLineage = storage.getLineage(vertexId, depth, direction);
                        } catch (Exception badQuery) {
                            System.out.println("Error: Please check query expression");
                            badQuery.printStackTrace();
                            return;
                        }
                        Lineage.exportDOT(resultLineage.getGraph(), tokens[6]);
                    } else {
                        throw new Exception();
                    }
                    System.out.println("Done");
                    return;
                }
            }
            throw new Exception();
        } catch (Exception exception) {
            System.out.println("Usage: query <class name> vertices <expression>");
            System.out.println("       query <class name> lineage <vertex id> <depth> ancestors|descendants <output file>");
        }
    }

    public static void showCommands() {
        System.out.println("Available commands:");
        System.out.println("       add producer|storage <class name> <initialization arguments>");
        System.out.println("       add filter <class name> <index>");
        System.out.println("       remove producer|storage <class name>");
        System.out.println("       remove filter <index>");
        System.out.println("       list producers|storages|filters");
        System.out.println("       query <class name> vertices <expression>");
        System.out.println("       query <class name> lineage <vertex id> <depth> ancestors|descendants <output file>");
        System.out.println("       exit");
    }

    public static void addCommand(String line) {
        String[] tokens = line.split("\\s", 4);
        try {
            if (tokens[1].equalsIgnoreCase("producer")) {
                addProducer(tokens[2], tokens[3]);
            } else if (tokens[1].equalsIgnoreCase("storage")) {
                addStorage(tokens[2], tokens[3]);
            } else if (tokens[1].equalsIgnoreCase("filter")) {
                addFilter(tokens[2], tokens[3]);
            } else {
                throw new Exception();
            }
        } catch (Exception addCommandException) {
            System.out.println("Usage: add producer|storage <class name> <initialization arguments>");
            System.out.println("       add filter <class name> <index>");
        }
    }

    public static void listCommand(String line) {
        String[] tokens = line.split("\\s");
        try {
            if (tokens[1].equalsIgnoreCase("producers")) {
                if (producers.isEmpty()) {
                    System.out.println("No producers added");
                    return;
                }
                System.out.println(producers.size() + " producer(s) added:");
                Iterator iterator = producers.iterator();
                int count = 1;
                while (iterator.hasNext()) {
                    System.out.println(count + ". " + iterator.next().getClass().getName().split("\\.")[2]);
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
                    System.out.println(count + ". " + iterator.next().getClass().getName().split("\\.")[2]);
                    count++;
                }
            } else if (tokens[1].equalsIgnoreCase("filters")) {
                if (filters.size() == 1) {
                    System.out.println("No filters added");
                    return;
                }
                System.out.println((filters.size() - 1) + " filter(s) added:");
                for (int i = 0; i < filters.size() - 1; i++) {
                    System.out.println(i + 1 + ". " + filters.get(i).getClass().getName().split("\\.")[2]);
                }
            } else {
                throw new Exception();
            }
        } catch (Exception listCommandException) {
            System.out.println("Usage: list producers|storages|filters");
        }
    }

    public static void removeCommand(String line) {
        String[] tokens = line.split("\\s");
        try {
            if (tokens[1].equalsIgnoreCase("producer")) {
                boolean found = false;
                Iterator iterator = producers.iterator();
                while (iterator.hasNext()) {
                    AbstractProducer producer = (AbstractProducer) iterator.next();
                    if (producer.getClass().getName().equals("spade.producer." + tokens[2])) {
                        removeproducers.add(producer);
                        found = true;
                        System.out.print("Shutting down producer " + tokens[2] + "... ");
                        while (removeproducers.contains(producer)) {
                            // wait for other thread to safely remove producer
                        }
                        System.out.println("done");
                    }
                }
                if (!found) {
                    System.out.println("Producer " + tokens[2] + " not found");
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
                System.out.print("Removing filter " + filterName + "... ");
                if (index > 1) {
                    ((AbstractFilter) filters.get(index - 2)).setNextFilter((AbstractFilter) filters.get(index));
                }
                filters.remove(index - 1);
                System.out.println("done");
            } else {
                throw new Exception();
            }
        } catch (Exception removeCommandException) {
            System.out.println("Usage: remove producer|storage <class name>");
            System.out.println("       remove filter <index>");
        }
    }

    public static void addProducer(String classname, String arguments) {
        try {
            AbstractProducer producer = (AbstractProducer) Class.forName("spade.producer." + classname).newInstance();
            System.out.print("Adding producer " + classname + "... ");
            Buffer buff = new Buffer();
            producer.setBuffer(buff);
            if (producer.launch(arguments)) {
                buffers.put(producer, buff);
                producers.add(producer);
                System.out.println("done");
            } else {
                System.out.println("failed");
            }
        } catch (Exception addProducerException) {
            System.out.println("Error: Unable to add producer " + classname + " - please check class name");
            addProducerException.printStackTrace(System.err);
        }
    }

    public static void addStorage(String classname, String arg) {
        try {
            AbstractStorage storage = (AbstractStorage) Class.forName("spade.storage." + classname).newInstance();
            System.out.print("Adding storage " + classname + "... ");
            if (storage.initialize(arg)) {
                storages.add(storage);
                System.out.println("done");
            } else {
                System.out.println("failed");
            }
        } catch (Exception addStorageException) {
            System.out.println("Error: Unable to add storage " + classname + " - please check class name and argument");
            addStorageException.printStackTrace(System.err);
        }
    }

    public static void addFilter(String classname, String arg) {
        try {
            AbstractFilter filter = (AbstractFilter) Class.forName("spade.filter." + classname).newInstance();
            int index = Integer.parseInt(arg);
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

    public void setStorages(Set mainStroageSet) {
        storages = mainStroageSet;
    }

    @Override
    public void putVertex(AbstractVertex incomingVertex) {
        incomingVertex.removeAnnotation("source_producer");
        Iterator iterator = storages.iterator();
        while (iterator.hasNext()) {
            ((AbstractStorage) iterator.next()).putVertex(incomingVertex);
        }
    }

    @Override
    public void putEdge(AbstractEdge incomingEdge) {
        incomingEdge.removeAnnotation("source_producer");
        Iterator iterator = storages.iterator();
        while (iterator.hasNext()) {
            ((AbstractStorage) iterator.next()).putEdge(incomingEdge);
        }
    }
}
