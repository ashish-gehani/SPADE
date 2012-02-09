
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
package spade.reporter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import spade.core.AbstractEdge;
import spade.core.AbstractReporter;
import spade.core.AbstractVertex;
import spade.edge.opm.Used;
import spade.edge.opm.WasGeneratedBy;
import spade.edge.opm.WasTriggeredBy;
import spade.vertex.opm.Artifact;
import spade.vertex.opm.Process;


public class LLVMReporter extends AbstractReporter {

    public static volatile boolean shutdown;
    public Map<String, Stack> functionStackMap; // Each Stack holds the function call stack for a thread.
    ServerSocket Server;
    public static final int THREAD_SLEEP_DELAY = 500;
    public static LLVMReporter Reporter = null;
    public final int SocketNumber = 5000;
    
    @Override
    public boolean launch(String arguments) {
        Reporter = this;
        try {
            Server = new ServerSocket(SocketNumber);
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
                    while(!shutdown) {
                        try {
                            Socket connected = Server.accept();
                            EventHandler eventHandler = new EventHandler(connected);
                            new Thread(eventHandler).start();
                            Thread.sleep(THREAD_SLEEP_DELAY);
                        }
                        catch (Exception exception){
                            exception.printStackTrace(System.err);
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
        return true;
    }
}


class EventHandler implements Runnable{
    Socket threadSocket;
    int FunctionId = 0;

    EventHandler(Socket socket) {
        threadSocket = socket;
    }
    
    @Override
    public void run()
    {
        try {
            BufferedReader inFromClient = new BufferedReader(new InputStreamReader(threadSocket.getInputStream()));
            while (!LLVMReporter.shutdown) {
                if (inFromClient.ready()) {
                    String line = inFromClient.readLine();
                    if (line != null) {
                        parseEvent(line);
                    }
                }
                Thread.sleep(LLVMReporter.THREAD_SLEEP_DELAY); // Reduce busy waiting load
            }
            inFromClient.close();
        } catch (Exception exception) {
            exception.printStackTrace(System.err);
        }
    }
    
    //trace contains thread id, function entry or exit, function name and arguments or return value.
    //trace looks like "123 E: $foo Arg #0: i32 %a =123". 
    private void parseEvent(String line) {
        try {
            AbstractVertex function = null;
            AbstractVertex argument = null;
            AbstractEdge edge = null;
            LinkedHashMap<String, String> annotations = new LinkedHashMap<String, String>();
            if (line.length() > 0) {
				// get thread id
                String tid = "";
                
                int index = line.indexOf(' ');
                tid = line.substring(0, index);
                
                // if the functionStackMap does not contain a stack for that thread, create a new stack.
                if (!LLVMReporter.Reporter.functionStackMap.containsKey(tid))
                {
                    LLVMReporter.Reporter.functionStackMap.put(tid, new Stack());
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
                    function.addAnnotation("pid", functionName + "." + FunctionId + "." + tid);
                    function.addAnnotation("name", functionName);
                    function.addAnnotation("tid", tid);
                    
                    LLVMReporter.Reporter.putVertex(function);
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
                        
                        LLVMReporter.Reporter.putVertex(argument);
                        
                        if (!LLVMReporter.Reporter.functionStackMap.get(tid).empty()) {
                            edge = new WasGeneratedBy((Artifact) argument, (Process) LLVMReporter.Reporter.functionStackMap.get(tid).peek());
                            LLVMReporter.Reporter.putEdge(edge);
                        }
                        edge = new Used((Process) function, (Artifact) argument);
                        LLVMReporter.Reporter.putEdge(edge);
                    }
                    if (!LLVMReporter.Reporter.functionStackMap.get(tid).empty()) {
                        edge = new WasTriggeredBy((Process) function, (Process) LLVMReporter.Reporter.functionStackMap.get(tid).peek());
                        LLVMReporter.Reporter.putEdge(edge);
                    }
                    LLVMReporter.Reporter.functionStackMap.get(tid).push(function);
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
                        
                        LLVMReporter.Reporter.putVertex(argument);
                        edge = new WasGeneratedBy((Artifact) argument, (Process) LLVMReporter.Reporter.functionStackMap.get(tid).peek());
                        LLVMReporter.Reporter.putEdge(edge);
                        LLVMReporter.Reporter.functionStackMap.get(tid).pop();
                    }
                }

            }

        } catch (Exception exception) {
            exception.printStackTrace(System.err);
        }
    }

}
