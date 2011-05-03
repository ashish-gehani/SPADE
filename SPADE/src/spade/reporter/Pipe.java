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
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.LinkedHashMap;
import spade.core.AbstractReporter;
import spade.core.AbstractEdge;
import spade.opm.edge.Used;
import spade.opm.edge.WasTriggeredBy;
import spade.opm.edge.WasGeneratedBy;
import spade.opm.edge.WasDerivedFrom;
import spade.opm.edge.WasControlledBy;
import spade.core.AbstractVertex;
import spade.opm.vertex.Artifact;
import spade.opm.vertex.Process;
import spade.opm.vertex.Agent;

public class Pipe extends AbstractReporter {

    private String pipePath;
    private BufferedReader eventReader;
    private volatile boolean shutdown;
    private HashMap vertices;

    @Override
    public boolean launch(String arguments) {
        // The Pipe reporter creates a simple named pipe to which provenance events
        // can be written. The argument to the launch method is the location of the
        // pipe.
        vertices = new HashMap();
        pipePath = arguments;
        File checkpipe = new File(pipePath);
        if (checkpipe.exists()) {
            return false;
        } else {
            try {
                int exitValue = Runtime.getRuntime().exec("mkfifo " + pipePath).waitFor();
                if (exitValue != 0) {
                    System.err.println("Error creating pipe!");
                    return false;
                }
                Runnable eventThread = new Runnable() {

                    public void run() {
                        try {
                            eventReader = new BufferedReader(new FileReader(pipePath));
                            while (!shutdown) {
                                if (eventReader.ready()) {
                                    String line = eventReader.readLine();
                                    if (line != null) {
                                        parseEvent(line);
                                    }
                                }
                                Thread.sleep(5);
                            }
                            eventReader.close();
                        } catch (Exception exception) {
                            exception.printStackTrace(System.err);
                        }
                    }
                };
                new Thread(eventThread).start();
                return true;
            } catch (Exception exception) {
                exception.printStackTrace(System.err);
                return false;
            }
        }
    }

    private void parseEvent(String line) {
        try {
            // Tokens are split on spaces not preceded by a backslash using
            // a negative lookbehind in the regex.
            String[] tokens = line.split("(?<!\\\\) ");
            String id = null;
            String type = null;
            String from = null;
            String to = null;
            AbstractVertex vertex = null;
            AbstractEdge edge = null;
            // Create an empty HashMap for annotations. We use a LinkedHashMap
            // to preserve order of annotations.
            LinkedHashMap annotations = new LinkedHashMap();
            for (int i = 0; i < tokens.length; i++) {
                // Check if the key is one of the keywords, otherwise treat it as
                // an annotation.
                if (getKey(tokens[i]).equalsIgnoreCase("id")) {
                    id = getValue(tokens[i]);
                } else if (getKey(tokens[i]).equalsIgnoreCase("type")) {
                    type = getValue(tokens[i]);
                } else if (getKey(tokens[i]).equalsIgnoreCase("from")) {
                    from = getValue(tokens[i]);
                } else if (getKey(tokens[i]).equalsIgnoreCase("to")) {
                    to = getValue(tokens[i]);
                } else {
                    annotations.put(getKey(tokens[i]), getValue(tokens[i]));
                }
            }
            // Instantiate object based on the type and associate annotations to it.
            if (type.equalsIgnoreCase("process")) {
                vertex = new Process(annotations);
            } else if (type.equalsIgnoreCase("artifact")) {
                vertex = new Artifact(annotations);
            } else if (type.equalsIgnoreCase("agent")) {
                vertex = new Agent(annotations);
            // Create edges and also check if the 'from' and 'to' values are valid.
            } else if ((type.equalsIgnoreCase("used")) && (from != null) && (to != null)) {
                if ((vertices.get((from)) instanceof Process) && (vertices.get((to)) instanceof Artifact)) {
                    edge = new Used((Process) vertices.get(from), (Artifact) vertices.get(to), annotations);
                }
            } else if ((type.equalsIgnoreCase("wasgeneratedby")) && (from != null) && (to != null)) {
                if ((vertices.get((from)) instanceof Artifact) && (vertices.get((to)) instanceof Process)) {
                    edge = new WasGeneratedBy((Artifact) vertices.get(from), (Process) vertices.get(to), annotations);
                }
            } else if ((type.equalsIgnoreCase("wastriggeredby")) && (from != null) && (to != null)) {
                if ((vertices.get((from)) instanceof Process) && (vertices.get((to)) instanceof Process)) {
                    edge = new WasTriggeredBy((Process) vertices.get(from), (Process) vertices.get(to), annotations);
                }
            } else if ((type.equalsIgnoreCase("wascontrolledby")) && (from != null) && (to != null)) {
                if ((vertices.get((from)) instanceof Process) && (vertices.get((to)) instanceof Agent)) {
                    edge = new WasControlledBy((Process) vertices.get(from), (Agent) vertices.get(to), annotations);
                }
            } else if ((type.equalsIgnoreCase("wasderivedfrom")) && (from != null) && (to != null)) {
                if ((vertices.get((from)) instanceof Artifact) && (vertices.get((to)) instanceof Artifact)) {
                    edge = new WasDerivedFrom((Artifact) vertices.get(from), (Artifact) vertices.get(to), annotations);
                }
            }
            // Finally, pass vertex or edge to buffer.
            if ((id != null) && (vertex != null)) {
                vertices.put(id, vertex);
                putVertex(vertex);
            } else if (edge != null) {
                putEdge(edge);
            }
        } catch (Exception parseException) {
            parseException.printStackTrace(System.err);
        }
    }

    private String getKey(String token) {
        // Return the key after removing escaping backslashes. The backslashes
        // are detected using positive lookbehind.
        return token.split("(?<!\\\\):")[0].replaceAll("\\\\(?=[: ])", "");
    }

    private String getValue(String token) {
        // Return the value after removing escaping backslashes. The backslashes
        // are detected using positive lookbehind.
        return token.split("(?<!\\\\):")[1].replaceAll("\\\\(?=[: ])", "");
    }

    @Override
    public boolean shutdown() {
        shutdown = true;
        try {
            // Remove the pipe created at startup.
            Runtime.getRuntime().exec("rm -f " + pipePath).waitFor();
            return true;
        } catch (Exception exception) {
            exception.printStackTrace(System.err);
            return false;
        }
    }
}
