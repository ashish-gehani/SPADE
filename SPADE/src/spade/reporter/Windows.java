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
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import spade.core.AbstractReporter;
import spade.edge.opm.Used;
import spade.edge.opm.WasGeneratedBy;
import spade.edge.opm.WasTriggeredBy;
import spade.vertex.opm.Artifact;
import spade.vertex.opm.Process;

public class Windows extends AbstractReporter {

    private BufferedReader eventReader;
    private java.lang.Process nativeProcess;
    private volatile boolean shutdown;
    private HashMap<String, Process> processes;

    private final int THREAD_SLEEP_DELAY = 5;
    private final String SPADEConsolePath = "C:\\SPADEWMI.exe";
    
    @Override
    public boolean launch(String arguments) {
        shutdown = false;
        processes = new HashMap<String, Process>();

        try {
            nativeProcess = Runtime.getRuntime().exec(SPADEConsolePath);
            eventReader = new BufferedReader(new InputStreamReader(nativeProcess.getInputStream()));
            
            Runnable eventProcessor = new Runnable() {

                public void run() {
                    try {
                        while (!shutdown) {
                            String line = eventReader.readLine();
                            if (line.equals("BEGIN PROCESS")) {
                                String pid = eventReader.readLine().trim();
                                String ppid = eventReader.readLine().trim();
                                if (!processes.containsKey(pid)) {
                                    Process process = new Process();
                                    process.addAnnotation("pid", pid);
                                    process.addAnnotation("ppid", ppid);
                                    process.addAnnotation("pidname", eventReader.readLine().trim());
                                    process.addAnnotation("owner", eventReader.readLine().trim());
                                    process.addAnnotation("commandline", eventReader.readLine().trim());
                                    process.addAnnotation("executablepath", eventReader.readLine().trim());
                                    process.addAnnotation("starttime", eventReader.readLine().trim());
                                    eventReader.readLine();
                                    putVertex(process);
                                    processes.put(pid, process);
                                    if (processes.containsKey(ppid)) {
                                        WasTriggeredBy edge = new WasTriggeredBy(process, processes.get(ppid));
                                        putEdge(edge);
                                    }
                                }
                            } else if (line.equals("BEGIN WRITE")) {
                                Artifact file = new Artifact();
                                Process process = processes.get(eventReader.readLine().trim());
                                file.addAnnotation("filename", eventReader.readLine().trim());
                                file.addAnnotation("path", eventReader.readLine().trim());
                                file.addAnnotation("size", eventReader.readLine().trim());
                                file.addAnnotation("lastmodified", eventReader.readLine().trim());
                                eventReader.readLine();
                                if (process != null) {
                                    WasGeneratedBy edge = new WasGeneratedBy(file, process);
                                    putVertex(file);
                                    putEdge(edge);
                                }
                            } else if (line.equals("BEGIN READ")) {
                                Artifact file = new Artifact();
                                Process process = processes.get(eventReader.readLine().trim());
                                file.addAnnotation("filename", eventReader.readLine().trim());
                                file.addAnnotation("path", eventReader.readLine().trim());
                                file.addAnnotation("size", eventReader.readLine().trim());
                                file.addAnnotation("lastmodified", eventReader.readLine().trim());
                                eventReader.readLine();
                                if (process != null) {
                                    Used edge = new Used(process, file);
                                    putVertex(file);
                                    putEdge(edge);
                                }
                            }
                            Thread.sleep(THREAD_SLEEP_DELAY);
                        }
                        nativeProcess.destroy();
                    } catch (Exception exception) {
                        Logger.getLogger(Windows.class.getName()).log(Level.SEVERE, null, exception);
                    }
                }
            };
            new Thread(eventProcessor, "WindowsEventProcessor").start();

            return true;
        } catch (Exception exception) {
            Logger.getLogger(Windows.class.getName()).log(Level.SEVERE, null, exception);
            return false;
        }
    }

    @Override
    public boolean shutdown() {
        shutdown = true;
        return true;
    }
}
