/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2014 SRI International

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
package spade.utility;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jline.ConsoleReader;
import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.filter.FinalCommitFilter;
import spade.query.quickgrail.instruction.ExportGraph;

@Deprecated
public class GraphUtility {

    private static PrintStream outputStream = System.out;
    private static final String COMMAND_PROMPT = "-> ";
    private static HashMap<String, Graph> graphObjects = new HashMap<String, Graph>();

    private static Pattern importPattern = Pattern.compile("([a-zA-Z0-9]+)\\s*=\\s*import\\((.+)\\)");
    private static Pattern exportPattern = Pattern.compile("([a-zA-Z0-9]+)\\.export\\((.+)\\)");
    private static Pattern vertexPattern = Pattern.compile("([a-zA-Z0-9]+)\\.showVertices\\((.*)\\)");
    private static Pattern pathPattern = Pattern.compile("([a-zA-Z0-9]+)\\s*=\\s*([a-zA-Z0-9]+)\\.getPaths\\((.+)\\)");
    private static Pattern lineagePattern = Pattern.compile("([a-zA-Z0-9]+)\\s*=\\s*([a-zA-Z0-9]+)\\.getLineage\\((.+)\\)");
    private static Pattern filterPattern = Pattern.compile("([a-zA-Z0-9]+)\\s*=\\s*([a-zA-Z0-9]+)\\.filter\\((.+)\\)");

    public static void main(String[] args) {
        try {
            outputStream.println("");
            outputStream.println("Graph Query Utility");
            outputStream.println("");
            ConsoleReader commandReader = new ConsoleReader();

            while (true) {
                try {
                    outputStream.print(COMMAND_PROMPT);
                    String line = commandReader.readLine();
                    if (line.equals("exit")) {
                        break;
                    } else {
                        processQuery(line);
                    }
                } catch (Exception exception) {
                    exception.printStackTrace();
                }
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private static void importGraph(String path, String target) {
    	Graph graph = null;
    	try{
    		graph = Graph.importGraphFromDOTFile(path);
    	}catch(Exception e){
    		outputStream.println("Error importing graph! : " + e.getMessage());
            return;
    	}
        if (graph == null) {
            outputStream.println("Error importing graph!");
            return;
        }
        outputStream.println(String.format("Finished importing %s to graph %s", path, target));
        graphObjects.put(target, graph);
    }

    private static void exportGraph(String input, String path) {
        if (!graphObjects.containsKey(input)) {
            outputStream.println(String.format("Graph %s not found!", input));
            return;
        }
        Graph graph = graphObjects.get(input);
        try {
        	Graph.exportGraphToFile(ExportGraph.Format.kDot, path, graph);
        } catch (Exception exception) {
            outputStream.println("Error exporting graph!");
            return;
        }
        outputStream.println(String.format("Finished exporting graph %s to %s", input, path));
    }

    private static void showVertices(String input, String expression) {
        expression = (expression.equals("") || expression == null) ? "type:*" : expression;
        if (!graphObjects.containsKey(input)) {
            outputStream.println(String.format("Graph %s not found!", input));
            return;
        }
        Graph graph = graphObjects.get(input);
//        List<Integer> vertices = graph.listVertices(expression); TODO
//        outputStream.println(String.format("%d vertices matched expression:", vertices.size()));TODO
//        for (int id : vertices) {TODO
//            outputStream.println(String.format(" - %d\t\t%s", id, graph.getVertex(id))); TODO
//        }TODO
    }

    private static void getPaths(String target, String input, String expression) {
        if (!graphObjects.containsKey(input)) {
            outputStream.println(String.format("Graph %s not found!", input));
            return;
        }
        Graph graph = graphObjects.get(input);
        Graph result = null;
        String[] args = expression.split(",");
        try {
            int src = Integer.parseInt(args[0].trim());
            int dst = Integer.parseInt(args[1].trim());
//            result = graph.getPaths(src, dst); TODO
        } catch (Exception exception) {
            String src = args[0].trim();
            String dst = args[1].trim();
//            result = (graphObjects.containsKey(src) && graphObjects.containsKey(dst)) ? graph.getPaths(graphObjects.get(src), graphObjects.get(dst)) : graph.getPaths(src, dst); TODO
        }
        if (result != null) {
            graphObjects.put(target, result);
            outputStream.println(String.format("Result saved in graph %s", target));
        } else {
            outputStream.println(String.format("Error querying graph %s!", input));
        }
    }

    private static void getLineage(String target, String input, String expression) {
        if (!graphObjects.containsKey(input)) {
            outputStream.println(String.format("Graph %s not found!", input));
            return;
        }
        Graph graph = graphObjects.get(input);
        Graph result = null;
        String[] args = expression.split(",");
        String direction = args[1].trim();
        try {
            int src = Integer.parseInt(args[0].trim());
//            result = graph.getLineage(src, direction); TODO
        } catch (Exception exception) {
            String src = args[0].trim();
//            result = graphObjects.containsKey(src) ? graph.getLineage(graphObjects.get(src), direction) : graph.getLineage(src, direction); TODO
        }
        if (result != null) {
            graphObjects.put(target, result);
            outputStream.println(String.format("Result saved in graph %s", target));
        } else {
            outputStream.println(String.format("Error querying graph %s!", input));
        }
    }

    private static void filter(String target, String input, String filterName) {
        if (!graphObjects.containsKey(input)) {
            outputStream.println(String.format("Graph %s not found!", input));
            return;
        }
        Graph result = new Graph();
        AbstractFilter filter;
        try {
            filter = (AbstractFilter) Class.forName("spade.filter." + filterName).newInstance();
        } catch (Exception ex) {
            outputStream.println("Unable to find/load filter!");
            return;
        }
        FinalCommitFilter finalFilter = new FinalCommitFilter();
        //finalFilter.storages.add(result); TODO
        filter.setNextFilter(finalFilter);
        Graph graph = graphObjects.get(input);
        for (AbstractVertex v : graph.vertexSet()) {
            filter.putVertex(v);
        }
        for (AbstractEdge e : graph.edgeSet()) {
            filter.putEdge(e);
        }
        finalFilter.storages.remove(graph);
        graphObjects.put(target, result);
        outputStream.println(String.format("Result saved in graph %s", target));
    }

    private static void processQuery(String line) {
        Matcher importMatcher = importPattern.matcher(line);
        Matcher exportMatcher = exportPattern.matcher(line);
        Matcher vertexMatcher = vertexPattern.matcher(line);
        Matcher pathMatcher = pathPattern.matcher(line);
        Matcher lineageMatcher = lineagePattern.matcher(line);
        Matcher filterMatcher = filterPattern.matcher(line);

        if (importMatcher.matches()) {
            String target = importMatcher.group(1);
            String path = importMatcher.group(2).trim();
            importGraph(path, target);
        } else if (exportMatcher.matches()) {
            String input = exportMatcher.group(1);
            String path = exportMatcher.group(2).trim();
            exportGraph(input, path);
        } else if (vertexMatcher.matches()) {
            String input = vertexMatcher.group(1);
            String expression = vertexMatcher.group(2);
            showVertices(input, expression);
        } else if (pathMatcher.matches()) {
            String target = pathMatcher.group(1);
            String input = pathMatcher.group(2);
            String expression = pathMatcher.group(3).trim();
            getPaths(target, input, expression);
        } else if (lineageMatcher.matches()) {
            String target = lineageMatcher.group(1);
            String input = lineageMatcher.group(2);
            String expression = lineageMatcher.group(3).trim();
            getLineage(target, input, expression);
        } else if (filterMatcher.matches()) {
            String target = filterMatcher.group(1);
            String input = filterMatcher.group(2);
            String filterName = filterMatcher.group(3).trim();
            filter(target, input, filterName);
        } else {
            outputStream.println("Available commands:");
            outputStream.println("\t <var> = import(<path>)");
            outputStream.println("\t <var>.export(<path>)");
            outputStream.println("\t <var>.showVertices(<expression>)");
            outputStream.println("\t <var> = <var>.getPaths(<src id | expression>, <dst id | expression>)");
            outputStream.println("\t <var> = <var>.getLineage(<src id | expression>, <direction>)");
            outputStream.println("\t <var> = <var>.filter(<filter name>)");
            outputStream.println("\t exit");
        }
    }
}
