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
import spade.edge.cdm.SimpleEdge;
import spade.edge.opm.Used;
import spade.edge.opm.WasControlledBy;
import spade.edge.opm.WasDerivedFrom;
import spade.edge.opm.WasGeneratedBy;
import spade.edge.opm.WasTriggeredBy;
import spade.edge.prov.WasAssociatedWith;
import spade.edge.prov.WasInformedBy;
import spade.vertex.cdm.Event;
import spade.vertex.cdm.Principal;
import spade.vertex.opm.Agent;
import spade.vertex.opm.Artifact;
import spade.vertex.opm.Process;
import spade.vertex.prov.Activity;
import spade.vertex.prov.Entity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pipe reporter for Linux.
 *
 * @author Dawood Tariq
 */
public class DSL extends AbstractReporter {

    private String pipePath;
    private BufferedReader eventReader;
    private volatile boolean shutdown;
    private HashMap<String, AbstractVertex> vertices;
    private final int THREAD_SLEEP_DELAY = 5;
    private Logger logger = Logger.getLogger(DSL.class.getName());

    @Override
    public boolean launch(String arguments) {
        if (arguments == null) {
            return false;
        }
        // The Pipe reporter creates a simple named pipe to which provenance events
        // can be written. The argument to the launch method is the location of the
        // pipe.
        vertices = new HashMap<String, AbstractVertex>();
        pipePath = arguments;
        File checkpipe = new File(pipePath);
        if (checkpipe.exists()) {
            return false;
        } else {
            try {
                int exitValue = Runtime.getRuntime().exec("mkfifo " + pipePath).waitFor();
                if (exitValue != 0) {
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
                                Thread.sleep(THREAD_SLEEP_DELAY);
                            }
                            eventReader.close();
                        } catch (Exception exception) {
                            Logger.getLogger(DSL.class.getName()).log(Level.SEVERE, null, exception);
                        }
                    }
                };
                new Thread(eventThread, "PipeReporter-Thread").start();
                return true;
            } catch (Exception exception) {
                Logger.getLogger(DSL.class.getName()).log(Level.SEVERE, null, exception);
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
            LinkedHashMap<String, String> annotations = new LinkedHashMap<String, String>();
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
            
            if(type.equalsIgnoreCase("event")){
            	vertex = new Event();
            }else if (type.equalsIgnoreCase("principal")) {
            	vertex = new Principal();
            }else if (type.equalsIgnoreCase("activity")) {
            	vertex = new Activity();
            } else if(type.equalsIgnoreCase("entity")) {
            	vertex = new Entity();
            } else if (type.equalsIgnoreCase("process")) {
                vertex = new Process();
            } else if (type.equalsIgnoreCase("artifact")) {
                vertex = new Artifact();
            } else if (type.equalsIgnoreCase("agent")) {
                vertex = new Agent();
                // Create edges and also check if the 'from' and 'to' values are valid.
            } else if ((type.equalsIgnoreCase("used")) && (from != null) && (to != null)) {
                if ((vertices.get(from) instanceof Process) && (vertices.get(to) instanceof Artifact)) {
                    edge = new Used((Process) vertices.get(from), (Artifact) vertices.get(to));
                } else if(vertices.get(from) instanceof Activity && vertices.get(to) instanceof Entity){
                	edge = new spade.edge.prov.Used((Activity) vertices.get(from), (Entity) vertices.get(to));
                }else {
                    logger.log(Level.WARNING, "Used edge must be from a Process/Activity to an Artifact/Entity");
                }
            } else if ((type.equalsIgnoreCase("wasgeneratedby")) && (from != null) && (to != null)) {
                if ((vertices.get(from) instanceof Artifact) && (vertices.get(to) instanceof Process)) {
                    edge = new WasGeneratedBy((Artifact) vertices.get(from), (Process) vertices.get(to));
                }else if(vertices.get(from) instanceof Entity && vertices.get(to) instanceof Activity){
                	edge = new spade.edge.prov.WasGeneratedBy((Entity) vertices.get(from), (Activity) vertices.get(to));
                }else{
                	logger.log(Level.WARNING, "WasGeneratedBy edge must be from a Artifact/Entity to an Process/Activity");
                }
            } else if ((type.equalsIgnoreCase("wastriggeredby")) && (from != null) && (to != null)) {
                if ((vertices.get((from)) instanceof Process) && (vertices.get((to)) instanceof Process)) {
                    edge = new WasTriggeredBy((Process) vertices.get(from), (Process) vertices.get(to));
                } else {
                    logger.log(Level.WARNING, "WasTriggeredBy edge must be from a Process to a Process");
                }
            } else if ((type.equalsIgnoreCase("wasinformedby")) && (from != null) && (to != null)) {
                if ((vertices.get((from)) instanceof Activity) && (vertices.get((to)) instanceof Activity)) {
                    edge = new WasInformedBy((Activity) vertices.get(from), (Activity) vertices.get(to));
                } else {
                    logger.log(Level.WARNING, "WasInformedBy edge must be from an Activity to a Activity");
                }
            } else if ((type.equalsIgnoreCase("wascontrolledby")) && (from != null) && (to != null)) {
                if ((vertices.get((from)) instanceof Process) && (vertices.get((to)) instanceof Agent)) {
                    edge = new WasControlledBy((Process) vertices.get(from), (Agent) vertices.get(to));
                } else {
                    logger.log(Level.WARNING, "WasControlledBy edge must be from a Process to an Agent");
                }
            } else if ((type.equalsIgnoreCase("wasassociatedwith")) && (from != null) && (to != null)) {
                if ((vertices.get((from)) instanceof Activity) && (vertices.get((to)) instanceof Agent)) {
                	spade.vertex.prov.Agent agent = new spade.vertex.prov.Agent();
                	agent.addAnnotations(((Agent) vertices.get(to)).getAnnotations());
                    edge = new WasAssociatedWith((Activity) vertices.get(from), agent);
                } else {
                    logger.log(Level.WARNING, "WasAssociatedWith edge must be from an Acivity to an Agent");
                }
            } else if ((type.equalsIgnoreCase("wasderivedfrom")) && (from != null) && (to != null)) {
                if ((vertices.get((from)) instanceof Artifact) && (vertices.get((to)) instanceof Artifact)) {
                    edge = new WasDerivedFrom((Artifact) vertices.get(from), (Artifact) vertices.get(to));
                } else if((vertices.get((from)) instanceof Entity) && (vertices.get((to)) instanceof Entity)){
                	edge = new spade.edge.prov.WasDerivedFrom((Entity) vertices.get(from), (Entity) vertices.get(to));
                }else {
                    logger.log(Level.WARNING, "WasDerivedFrom edge must be from an Artifact/Entity to an Artifact/Entity");
                }
            } else if (type.equalsIgnoreCase("simpleedge") && (from != null) && (to != null)) {
            	edge = new SimpleEdge(vertices.get(from), vertices.get(to));
            }
            // Finally, pass vertex or edge to buffer.
            if ((id != null) && (vertex != null)) {
                vertex.getAnnotations().putAll(annotations);
                vertices.put(id, vertex);
                putVertex(vertex);
            } else if (edge != null) {
                edge.getAnnotations().putAll(annotations);
                putEdge(edge);
            }
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
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
        return token.split("(?<!\\\\):", 2)[1].replaceAll("\\\\(?=[: ])", "");
    }

    @Override
    public boolean shutdown() {
        shutdown = true;
        try {
            // Remove the pipe created at startup.
            Runtime.getRuntime().exec("rm -f " + pipePath).waitFor();
            return true;
        } catch (Exception exception) {
            Logger.getLogger(DSL.class.getName()).log(Level.SEVERE, null, exception);
            return false;
        }
    }
}
