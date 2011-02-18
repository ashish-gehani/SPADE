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

package spade.producer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.LinkedHashMap;
import spade.core.AbstractProducer;
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

public class Pipe extends AbstractProducer {

    private String pipePath;
    private BufferedReader eventReader;
    private volatile boolean shutdown;
    private HashMap vertices;

    @Override
    public boolean launch(String arguments) {
        vertices = new HashMap();
        pipePath = arguments;
        File checkpipe = new File(pipePath);
        if (checkpipe.exists()) {
            return false;
        }
        try {
            Runtime.getRuntime().exec("mkfifo " + pipePath);
            Runnable eventThread = new Runnable() {

                public void run() {
                    try {
                        eventReader = new BufferedReader(new FileReader(pipePath));
                        String line = eventReader.readLine();
                        while (true) {
                            if (shutdown) {
                                eventReader.close();
                                return;
                            }
                            if (line != null) {
                                parseEvent(line);
                            }
                            line = eventReader.readLine();
                            Thread.sleep(1);
                        }
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

    private void parseEvent(String line) {
        try {
            String[] tokens = line.split("(?<!\\\\) ");
            String id = null;
            String type = null;
            String from = null;
            String to = null;
            AbstractVertex vertex = null;
            AbstractEdge edge = null;
            LinkedHashMap annotations = new LinkedHashMap();
            for (int i = 0; i < tokens.length; i++) {
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
            if (type.equalsIgnoreCase("process")) {
                vertex = new Process(annotations);
            } else if (type.equalsIgnoreCase("artifact")) {
                vertex = new Artifact(annotations);
            } else if (type.equalsIgnoreCase("agent")) {
                vertex = new Agent(annotations);
            } else if ((type.equalsIgnoreCase("used")) && (from != null) && (to != null)) {
                edge = new Used((Process) vertices.get(from), (Artifact) vertices.get(to), annotations);
            } else if ((type.equalsIgnoreCase("wasgeneratedby")) && (from != null) && (to != null)) {
                edge = new WasGeneratedBy((Artifact) vertices.get(from), (Process) vertices.get(to), annotations);
            } else if ((type.equalsIgnoreCase("wastriggeredby")) && (from != null) && (to != null)) {
                edge = new WasTriggeredBy((Process) vertices.get(from), (Process) vertices.get(to), annotations);
            } else if ((type.equalsIgnoreCase("wascontrolledby")) && (from != null) && (to != null)) {
                edge = new WasControlledBy((Process) vertices.get(from), (Agent) vertices.get(to), annotations);
            } else if ((type.equalsIgnoreCase("wasderivedfrom")) && (from != null) && (to != null)) {
                edge = new WasDerivedFrom((Artifact) vertices.get(from), (Artifact) vertices.get(to), annotations);
            }
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
        return token.split("(?<!\\\\):")[0].replaceAll("\\\\(?=[: ])", "");
    }

    private String getValue(String token) {
        return token.split("(?<!\\\\):")[1].replaceAll("\\\\(?=[: ])", "");
    }

    @Override
    public boolean shutdown() {
        shutdown = true;
        try {
            Runtime.getRuntime().exec("rm -f " + pipePath);
            return true;
        } catch (Exception exception) {
            exception.printStackTrace(System.err);
            return false;
        }
    }
}
