/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2015 SRI International

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
package spade.reporter;

import spade.core.AbstractEdge;
import spade.core.AbstractReporter;
import spade.core.AbstractVertex;
import spade.edge.opm.Used;
import spade.edge.opm.WasGeneratedBy;
import spade.edge.opm.WasTriggeredBy;
import spade.vertex.opm.Artifact;
import spade.vertex.opm.Process;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LLVM extends AbstractReporter {

    public static volatile boolean shutdown;
    public static final String PID_PREFIX = "#Pid = ";
    public Map<String, Stack> functionStackMap; // Each Stack holds the function call stack for a thread.
    ServerSocket server;
    public static final int THREAD_SLEEP_DELAY = 400;
    public static LLVM reporter = null;
    public final int socketNumber = 5000;
    public static volatile ArrayList<BufferedReader> threadBufferReaders;
    boolean forcedRemoval = true;

    @Override
    public boolean launch(String arguments) {
        /*
        * argument can be 'forcedremoval=true' (default) or 'forcedremoval=false'
        * if forcedremoval is specified as false, on removal of the reporter, reporter won't
        * shutdown unless the socket buffer from where instrumented programs sends in
        * provenance data is empitited.
        * if forcedremoval is true, it will discard this buffer and proceed to shutdown
        */
        try{
            String[] pairs = arguments.split("\\s+");
            for (String pair : pairs) {
                String[] keyvalue = pair.split("=");
                String key = keyvalue[0];
                String value = keyvalue[1];

                if (key.equals("forcedremoval") && value.equals("false")) {
                    forcedRemoval = false;
                }

            }
            } catch (NullPointerException e) {
            } catch (ArrayIndexOutOfBoundsException e) {
        }

        threadBufferReaders = new ArrayList<BufferedReader>();
        reporter = this;
        try {
            server = new ServerSocket(socketNumber);
            server.setReuseAddress(true);
            shutdown = false;
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return false;
        }
        //
        functionStackMap = Collections.synchronizedMap(new HashMap<String, Stack>());
        try {
            // Create connectionThread to listen on the socket and create EventHandlers
            Runnable connectionThread = new Runnable() {

                public void run() {
                    while (!shutdown) {
                        try {
                            Socket socket = server.accept();
                            EventHandler eventHandler = new EventHandler(socket);
                            new Thread(eventHandler).start();
                            Thread.sleep(THREAD_SLEEP_DELAY);
                        } catch (Exception exception) {
                            // exception.printStackTrace(System.err);
                        }
                    }
                }
            };
            new Thread(connectionThread).start();
            return true;
        } catch (Exception exception) {
            exception.printStackTrace(System.err);
            return false;
        }
    }

    @Override
    public boolean shutdown() {
        shutdown = true;
        if (forcedRemoval==false) {
            try {
                for (BufferedReader br : threadBufferReaders) {
                    while (br.ready()) {
                        Thread.sleep(LLVM.THREAD_SLEEP_DELAY);
                    }
                }
            } catch (Exception exception) {}
        }
        try {
            if (server != null) {
                server.close();
            }
        } catch (Exception exception) {

        } finally {
        }
        return true;
    }
}

class EventHandler implements Runnable {

    Socket threadSocket;
    int FunctionId = 0;
    BufferedReader inFromClient;

    EventHandler(Socket socket) {
        threadSocket = socket;
    }

    @Override
    public void run() {
        try {
            inFromClient = new BufferedReader(new InputStreamReader(threadSocket.getInputStream()));
            LLVM.threadBufferReaders.add(inFromClient);
            int adaptitive_pause_step = 10;
            int adaptitive_pause = 10;
            while (!LLVM.shutdown || inFromClient.ready() ) {
                String line = inFromClient.readLine();
                if (line != null) {
                    parseEvent(line);
                    adaptitive_pause=0;
                } else {
                    Thread.sleep(adaptitive_pause);
                    if (adaptitive_pause < LLVM.THREAD_SLEEP_DELAY) {
                        adaptitive_pause += adaptitive_pause_step;
                    }
                }
            }
            LLVM.threadBufferReaders.add(inFromClient);
            inFromClient.close();
            threadSocket.close();
        } catch (Exception exception) {
            exception.printStackTrace(System.err);
        }
    }

    private String extractThreadId(String line) {
        if (line.startsWith(LLVM.PID_PREFIX)) {
            String[] parts = line.split("\\s+"); // Split by spaces
            if (parts.length > 2) {
                return parts[2]; // The thread ID is the third part
            }
        } else {
            int index = line.indexOf(' ');
            return line.substring(0, index);
        }
        return null;
    }

    private int getIndex(String line) {
        if (line.startsWith(LLVM.PID_PREFIX)) {
            int index = line.indexOf(':');
            if (index > 0) {
                index = index - 2; // Return the index of 'L' or 'E'
            }
            return index;
        } else {
            int index = line.indexOf(' ');
            return index;
        }
    }
    //trace contains thread id, function entry or exit, function name and arguments or return value.
    //trace looks like "123 E: $foo Arg #0: i32 %a =123".
    // trace looks like "#Pid = 2273022730 L: @main  R:  i32 %6 =0"
    private void parseEvent(String line) {
        try {
            AbstractVertex function;
            AbstractVertex argument;
            AbstractEdge edge;
            line = line.trim();
            if (line.length() > 0) {
                String tid = extractThreadId(line);
                int index = getIndex(line);

                // if the functionStackMap does not contain a stack for that thread, create a new stack.
                if (!LLVM.reporter.functionStackMap.containsKey(tid)) {
                    LLVM.reporter.functionStackMap.put(tid, new Stack());
                }
                //Gets EventType - Entry or Exit
                line = line.substring(index + 1);
                char EventType = line.charAt(0); //EventType indicates entering or returning of a function
                line = line.substring(4); // The rest of the line is function name and arguments/return value.

                //Get the function Name
                String functionName;
                index = line.indexOf(' ');
                if (index >= 0) { // if there are arguments or a return value
                    functionName = line.substring(0, index);
                } else { // if there are no arguments and no return value
                    functionName = line;
                }

                Pattern pattern;
                if (EventType == 'E') {
                    // Expecting Argument number, arg type, arg name and arg value. eg: Arg #0: i32 %a =123
                    pattern = Pattern.compile("Arg #([0-9]+): ([^ ]+) %([^ ]+) =([^ ]+)");
                    Matcher items = pattern.matcher(line);

                    function = new Process();
                    // process id is a combination of functionName, functionId, and thread ID
                    function.addAnnotation("FunctionID", functionName + "." + FunctionId + "." + tid);
                    function.addAnnotation("FunctionName", functionName);
                    function.addAnnotation("ThreadID", tid);

                    LLVM.reporter.putVertex(function);
                    while (items.find()) {
                        argument = new Artifact();

                        String ArgNo = items.group(1);
                        // id is a combination of functionName, functionId and Argument Number
                        argument.addAnnotation("ID", functionName + "." + FunctionId + "-" + ArgNo);

                        String ArgType = items.group(2);
                        argument.addAnnotation("ArgType", ArgType);

                        String ArgName = items.group(3);
                        argument.addAnnotation("ArgName", ArgName);

                        String ArgVal = items.group(4);
                        argument.addAnnotation("ArgVal", ArgVal);

                        LLVM.reporter.putVertex(argument);

                        if (!LLVM.reporter.functionStackMap.get(tid).empty()) {
                            edge = new WasGeneratedBy((Artifact) argument, (Process) LLVM.reporter.functionStackMap.get(tid).peek());
                            LLVM.reporter.putEdge(edge);
                        }
                        edge = new Used((Process) function, (Artifact) argument);
                        LLVM.reporter.putEdge(edge);
                    }
                    if (!LLVM.reporter.functionStackMap.get(tid).empty()) {
                        edge = new WasTriggeredBy((Process) function, (Process) LLVM.reporter.functionStackMap.get(tid).peek());
                        LLVM.reporter.putEdge(edge);
                    }
                    LLVM.reporter.functionStackMap.get(tid).push(function);
                    FunctionId++;
                } else // in case of EventType being Return
                {
                    // Expecting ret type, ret name and ret value. "R:  i32 %ret =2". Ret name is ignored
                    pattern = Pattern.compile("R:  ([^ ]+) %([^ ]+) =(.+)");
                    Matcher items = pattern.matcher(line);
                    if (items.find()) {
                        argument = new Artifact();
                        String RetType = items.group(1);
                        argument.addAnnotation("ReturnType", RetType);

                        String RetVal = items.group(3);
                        argument.addAnnotation("ReturnVal", RetVal);

                        LLVM.reporter.putVertex(argument);
                        edge = new WasGeneratedBy((Artifact) argument, (Process) LLVM.reporter.functionStackMap.get(tid).peek());
                        LLVM.reporter.putEdge(edge);
                    }
                    LLVM.reporter.functionStackMap.get(tid).pop();
                }

            }

        } catch (Exception exception) {
            exception.printStackTrace(System.err);
        }
    }
}
