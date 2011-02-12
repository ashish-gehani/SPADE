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
    private static Set consumers;
    private static Set removeproducers;
    private static Set removeconsumers;
    private static List filters;
    private static Map<ProducerInterface, Buffer> buffers;
    private static boolean shutdown;

    public static void main(String args[]) {
        shutdown = false;
        producers = Collections.synchronizedSet(new HashSet());
        consumers = Collections.synchronizedSet(new HashSet());
        removeproducers = Collections.synchronizedSet(new HashSet());
        removeconsumers = Collections.synchronizedSet(new HashSet());
        filters = Collections.synchronizedList(new Vector());
        buffers = Collections.synchronizedMap(new HashMap<ProducerInterface, Buffer>());

        FinalCommitFilter commitfilter = new FinalCommitFilter();
        commitfilter.setConsumers(consumers);
        filters.add(commitfilter);

        Runnable r1 = new Runnable() {

            public void run() {
                try {
                    while (true) {
                        if (shutdown) {
                            for (ProducerInterface p : buffers.keySet()) {
                                if (((Buffer) buffers.get(p)).isEmpty()) {
                                    buffers.remove(p);
                                }
                            }
                            if (buffers.isEmpty()) {
                                System.out.println("done");
                                shutdown();
                            }
                        }
                        if (!removeconsumers.isEmpty()) {
                            Iterator it = removeconsumers.iterator();
                            ConsumerInterface s = (ConsumerInterface) it.next();
                            s.shutdown();
                            consumers.remove(s);
                            it.remove();
                        }
                        for (Iterator it = buffers.keySet().iterator(); it.hasNext();) {
                            ProducerInterface p = (ProducerInterface) it.next();
                            Object bufferelement = ((Buffer) buffers.get(p)).getBufferElement();
                            if (bufferelement instanceof Vertex) {
                                Vertex tempVertex = (Vertex) bufferelement;
                                tempVertex.addAnnotation("source_producer", p.getClass().getName());
                                ((FilterInterface) filters.get(0)).putVertex(tempVertex);
                            } else if (bufferelement instanceof Edge) {
                                Edge tempEdge = (Edge) bufferelement;
                                tempEdge.addAnnotation("source_producer", p.getClass().getName());
                                ((FilterInterface) filters.get(0)).putEdge((Edge) bufferelement);
                            } else if ((bufferelement == null) && (removeproducers.contains(p))) {
                                p.shutdown();
                                producers.remove(p);
                                removeproducers.remove(p);
                                it.remove();
                            }
                        }
                    }
                } catch (Exception iex) {
                    iex.printStackTrace();
                }
            }
        };
        new Thread(r1).start();

        Runnable r2 = new Runnable() {

            public void run() {
                try {
                    System.out.println("");
                    System.out.println("SPADE 2.0 Kernel");
                    System.out.println("");
                    showCommands();
                    Scanner in = new Scanner(System.in);
                    while (true) {
                        System.out.println("");
                        System.out.print("-> ");
                        String line = in.nextLine();
                        String command = line.split("\\s")[0];
                        if (command.equals("exit")) {
                            Iterator itp = producers.iterator();
                            System.out.print("Shutting down producers... ");
                            while (itp.hasNext()) {
                                ProducerInterface p = (ProducerInterface) itp.next();
                                p.shutdown();
                            }
                            System.out.println("done");
                            System.out.print("Flushing buffers... ");
                            shutdown = true;
                            break;
                        } else if (command.equals("add")) {
                            addCommand(line);
                        } else if (command.equals("list")) {
                            listCommand(line);
                        } else if (command.equals("remove")) {
                            removeCommand(line);
                        } else {
                            showCommands();
                            continue;
                        }
                    }
                    in.close();
                } catch (Exception iex) {
                    iex.printStackTrace();
                }
            }
        };
        new Thread(r2).start();

    }

    public static void showCommands() {
        System.out.println("Available commands:");
        System.out.println("       add producer <class name>");
        System.out.println("       add consumer <class name> <initialization argument>");
        System.out.println("       add filter <class name> <index>");
        System.out.println("       remove <producer|consumer> <class name>");
        System.out.println("       remove <filter> <index>");
        System.out.println("       list <producers|consumers|filters>");
        System.out.println("       exit");
    }

    public static void addCommand(String line) {
        String[] tokens = line.split("\\s");
        for (int i = 0; i < tokens.length; i++) {
            tokens[i] = tokens[i].trim();
        }
        try {
            if (tokens[1].equals("producer")) {
                addProducer(tokens[2]);
            } else if (tokens[1].equals("consumer")) {
                addConsumer(tokens[2], tokens[3]);
            } else if (tokens[1].equals("filter")) {
                addFilter(tokens[2], tokens[3]);
            } else {
                throw new Exception();
            }
        } catch (Exception e) {
            System.out.println("Usage: add producer <class name>");
            System.out.println("       add consumer <class name> <initialization argument>");
            System.out.println("       add filter <class name> <index>");
        }
    }

    public static void listCommand(String line) {
        String[] tokens = line.split("\\s");
        for (int i = 0; i < tokens.length; i++) {
            tokens[i] = tokens[i].trim();
        }
        try {
            if (tokens[1].equals("producers")) {
                if (producers.isEmpty()) {
                    System.out.println("No producers added");
                    return;
                }
                System.out.println(producers.size() + " producer(s) added:");
                Iterator itp = producers.iterator();
                int count = 1;
                while (itp.hasNext()) {
                    System.out.println(count + ") " + itp.next().getClass().getName());
                    count++;
                }
            } else if (tokens[1].equals("consumers")) {
                if (consumers.isEmpty()) {
                    System.out.println("No consumers added");
                    return;
                }
                System.out.println(consumers.size() + " consumer(s) added:");
                Iterator itc = consumers.iterator();
                int count = 1;
                while (itc.hasNext()) {
                    System.out.println(count + ") " + itc.next().getClass().getName());
                    count++;
                }
            } else if (tokens[1].equals("filters")) {
                if (filters.size() == 1) {
                    System.out.println("No filters added");
                    return;
                }
                System.out.println((filters.size() - 1) + " filter(s) added:");
                for (int i = 0; i < filters.size() - 1; i++) {
                    System.out.println(i + 1 + ") " + filters.get(i).getClass().getName());
                }
            } else {
                throw new Exception();
            }
        } catch (Exception e) {
            System.out.println("Usage: list <producers|consumers|filters>");
        }
    }

    public static void removeCommand(String line) {
        String[] tokens = line.split("\\s");
        for (int i = 0; i < tokens.length; i++) {
            tokens[i] = tokens[i].trim();
        }
        try {
            if (tokens[1].equals("producer")) {
                boolean found = false;
                Iterator itp = producers.iterator();
                while (itp.hasNext()) {
                    ProducerInterface p = (ProducerInterface) itp.next();
                    if (p.getClass().getName().equals(tokens[2])) {
                        removeproducers.add(p);
                        found = true;
                        System.out.print("Shutting down producer " + tokens[2] + "... ");
                        while (removeproducers.contains(p)) {
                        }
                        System.out.println("done");
                    }
                }
                if (!found) {
                    System.out.println("Producer " + tokens[2] + " not found");
                }
            } else if (tokens[1].equals("consumer")) {
                boolean found = false;
                Iterator itc = consumers.iterator();
                while (itc.hasNext()) {
                    ConsumerInterface s = (ConsumerInterface) itc.next();
                    if (s.getClass().getName().equals(tokens[2])) {
                        removeconsumers.add(s);
                        found = true;
                        System.out.print("Shutting down consumer " + tokens[2] + "... ");
                        while (removeconsumers.contains(s)) {
                        }
                        System.out.println("done");
                    }
                }
                if (!found) {
                    System.out.println("Consumer " + tokens[2] + " not found");
                }
            } else if (tokens[1].equals("filter")) {
                boolean found = false;
                int index = Integer.parseInt(tokens[2]);
                if ((index <= 0) || (index >= filters.size())) {
                    System.out.println("Error: Unable to remove filter - bad index");
                    return;
                }
                String name = filters.get(index - 1).getClass().getName();
                System.out.print("Removing filter " + name + "... ");
                if (index > 1) {
                    ((FilterInterface) filters.get(index - 2)).setNextFilter((FilterInterface) filters.get(index));
                }
                filters.remove(index - 1);
                System.out.println("done");
            } else {
                throw new Exception();
            }
        } catch (Exception e) {
            System.out.println("Usage: remove <producer|consumer> <class name>");
            System.out.println("       remove <filter> <index>");
        }
    }

    public static void addProducer(String classname) {
        try {
            ProducerInterface p = (ProducerInterface) Class.forName(classname).newInstance();
            System.out.print("Adding producer " + classname + "... ");
            Buffer buff = new Buffer();
            if (p.initialize(buff, null)) {
                buffers.put(p, buff);
                producers.add(p);
                System.out.println("done");
            } else {
                System.out.println("failed");
            }
        } catch (Exception e) {
            System.out.println("Error: Unable to add producer " + classname + " - please check class name");
        }
    }

    public static void addConsumer(String classname, String arg) {
        try {
            ConsumerInterface c = (ConsumerInterface) Class.forName(classname).newInstance();
            System.out.print("Adding consumer " + classname + "... ");
            if (c.initialize(arg)) {
                consumers.add(c);
                System.out.println("done");
            } else {
                System.out.println("failed");
            }
        } catch (Exception e) {
            System.out.println("Error: Unable to add consumer " + classname + " - please check class name and argument");
        }
    }

    public static void addFilter(String classname, String arg) {
        try {
            FilterInterface f = (FilterInterface) Class.forName(classname).newInstance();
            int index = Integer.parseInt(arg);
            if (index >= filters.size()) {
                throw new Exception();
            }
            f.setNextFilter((FilterInterface) filters.get(index));
            if (index > 0) {
                ((FilterInterface) filters.get(index - 1)).setNextFilter(f);
            }
            System.out.print("Adding filter " + classname + "... ");
            filters.add(index, f);
            System.out.println("done");
        } catch (Exception e) {
            System.out.println("Error: Unable to add filter " + classname + " - please check class name and index");
        }
    }

    public static void putVertex(Vertex v) {
        Iterator it = consumers.iterator();
        while (it.hasNext()) {
            ((ConsumerInterface) it.next()).putVertex(v);
        }
    }

    public static void putEdge(Edge e) {
        Iterator it = consumers.iterator();
        while (it.hasNext()) {
            ((ConsumerInterface) it.next()).putEdge(e);
        }
    }

    public static void shutdown() {
        Iterator itc = consumers.iterator();
        System.out.print("Shutting down consumers... ");
        while (itc.hasNext()) {
            ConsumerInterface s = (ConsumerInterface) itc.next();
            s.shutdown();
        }
        System.out.println("done");
        System.out.println("Terminating kernel...\n");
        try {
            Thread.sleep(500L);
        } catch (Exception e) {
        }
        System.exit(0);
    }
}
