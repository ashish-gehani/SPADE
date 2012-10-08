/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2012 SRI International

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
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import spade.core.AbstractEdge;
import spade.core.AbstractReporter;
import spade.core.AbstractVertex;
import spade.edge.opm.*;
import spade.vertex.opm.Agent;
import spade.vertex.opm.Artifact;
import spade.vertex.opm.Process;

/**
 *
 * @author Dawood Tariq
 */
public class DOT extends AbstractReporter {

    private BufferedReader eventReader;
    protected volatile boolean shutdown = false;
    private Map<String, AbstractVertex> vertexMap = new HashMap<String, AbstractVertex>();
    static final Logger logger = Logger.getLogger(Audit.class.getName());
    // Pattern to match nodes
    private static Pattern nodePattern = Pattern.compile("\"(.*)\" \\[label=\"(.*)\" shape=\"(.*)\" fillcolor=\"(.*)\"\\];");
    // Pattern to match edges
    private static Pattern edgePattern = Pattern.compile("\"(.*)\" -> \"(.*)\" \\[label=\"(.*)\" color=\"(.*)\"\\];");

    @Override
    public boolean launch(String arguments) {
        if (arguments == null) {
            return false;
        }
        try {
        	logger.info("Opening file '" + arguments + "' for reading");
            eventReader = new BufferedReader(new FileReader(arguments));
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
            return false;
        }
        Runnable lineProcessor = new Runnable() {

            public void run() {
                try {
                    String line;
                    while (!shutdown) {
                        line = eventReader.readLine();
                        if (line == null) {
                            break;
                        }
                        processLine(line);
                    }
                    eventReader.close();
                } catch (Exception exception) {
                    logger.log(Level.SEVERE, null, exception);
                }
            }
        };
        new Thread(lineProcessor, "DOT-Thread").start();
        return true;
    }
    
    @Override
    public boolean shutdown() {
        shutdown = true;
        return true;
    }

    private void processLine(String line) {
        Matcher nodeMatcher = nodePattern.matcher(line);
        Matcher edgeMatcher = edgePattern.matcher(line);
        if (nodeMatcher.find()) {
            String key = nodeMatcher.group(1);
            String label = nodeMatcher.group(2);
            String shape = nodeMatcher.group(3);
            AbstractVertex vertex;
            if (shape.equals("box")) {
                vertex = new Process();
            } else if (shape.equals("ellipse")) {
                vertex = new Artifact();
            } else {
                vertex = new Agent();
            }
            String[] pairs = label.split("\\\\n");
            for (String pair : pairs) {
                String key_value[] = pair.split(":", 2);
                if (key_value.length == 2) {
                    vertex.addAnnotation(key_value[0], key_value[1]);
                }
            }
            putVertex(vertex);
            vertexMap.put(key, vertex);
        } else if (edgeMatcher.find()) {
            String srckey = edgeMatcher.group(1);
            String dstkey = edgeMatcher.group(2);
            String label = edgeMatcher.group(3);
            String color = edgeMatcher.group(4);
            AbstractEdge edge;
            AbstractVertex srcVertex = vertexMap.get(srckey);
            AbstractVertex dstVertex = vertexMap.get(dstkey);
            if (color.equals("green")) {
                edge = new Used((Process) srcVertex, (Artifact) dstVertex);
            } else if (color.equals("red")) {
                edge = new WasGeneratedBy((Artifact) srcVertex, (Process) dstVertex);
            } else if (color.equals("blue")) {
                edge = new WasTriggeredBy((Process) srcVertex, (Process) dstVertex);
            } else if (color.equals("purple")) {
                edge = new WasControlledBy((Process) srcVertex, (Agent) dstVertex);
            } else {
                edge = new WasDerivedFrom((Artifact) srcVertex, (Artifact) dstVertex);
            }
            if ((label != null) && (label.length() > 2)) {
                label = label.substring(1, label.length() - 1);
                String[] pairs = label.split("\\\\n");
                for (String pair : pairs) {
                    String key_value[] = pair.split(":", 2);
                    if (key_value.length == 2) {
                        edge.addAnnotation(key_value[0], key_value[1]);
                    }
                }
            }
            putEdge(edge);
        } else {
            logger.log(Level.WARNING, "unable to match line {0}", line);
        }
    }
}
