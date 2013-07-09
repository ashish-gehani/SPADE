package spade.utility;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import jline.ConsoleReader;
import spade.core.Graph;

public class GraphUtility {

	private static PrintStream outputStream = System.out;
	private static final String COMMAND_PROMPT = "-> ";
	private static HashMap<String, Graph> graphObjects = new HashMap<String, Graph>();

	private static Pattern importPattern = Pattern.compile("([a-zA-Z0-9]+)\\s+=\\s+importGraph\\((.+)\\)");
	private static Pattern exportPattern = Pattern.compile("([a-zA-Z0-9]+)\\.exportGraph\\((.+)\\)");
	private static Pattern vertexPattern = Pattern.compile("([a-zA-Z0-9]+)\\.showVertices\\((.+)\\)");
	private static Pattern pathPattern = Pattern.compile("([a-zA-Z0-9]+)\\s+=\\s+([a-zA-Z0-9]+)\\.getPaths\\((.+)\\)");
	private static Pattern lineagePattern = Pattern.compile("([a-zA-Z0-9]+)\\s+=\\s+([a-zA-Z0-9]+)\\.getLineage\\((.+)\\)");

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

	private static void processQuery(String line) {
		Matcher importMatcher = importPattern.matcher(line);
		Matcher exportMatcher = exportPattern.matcher(line);
		Matcher vertexMatcher = vertexPattern.matcher(line);
		Matcher pathMatcher = pathPattern.matcher(line);
		Matcher lineageMatcher = lineagePattern.matcher(line);

		if (importMatcher.matches()) {
			String target = importMatcher.group(1);
			String inputFile = importMatcher.group(2).trim();
			Graph graph = null;
			try {
				graph = Graph.importGraph(inputFile);
			} catch (Exception exception) {
				outputStream.println("Error importing graph!");
				return;
			}
			outputStream.println(String.format("Finished importing %s to graph %s", inputFile, target));
			graphObjects.put(target, graph);
		} else if (exportMatcher.matches()) {
			String input = exportMatcher.group(1);
			String outputFile = exportMatcher.group(2).trim();
			if (!graphObjects.containsKey(input)) {
				outputStream.println(String.format("Graph %s not found!", input));
				return;
			}
			Graph graph = graphObjects.get(input);
			try {
				graph.exportGraph(outputFile);
			} catch (Exception exception) {
				outputStream.println("Error exporting graph!");
				return;
			}
			outputStream.println(String.format("Finished exporting graph %s to %s", input, outputFile));
		} else if (vertexMatcher.matches()) {
			String input = vertexMatcher.group(1);
			String expression = vertexMatcher.group(2).trim();
			if (!graphObjects.containsKey(input)) {
				outputStream.println(String.format("Graph %s not found!", input));
				return;
			}
			Graph graph = graphObjects.get(input);
			List<Integer> vertices = graph.listVertices(expression);
			outputStream.println(String.format("%d vertices matched expression:", vertices.size()));
			for (int id : vertices) {
				outputStream.println(String.format(" - %d\t\t%s", id, graph.getVertex(id)));
			}
		} else if (pathMatcher.matches()) {
			String target = pathMatcher.group(1);
			String input = pathMatcher.group(2);
			String expression = pathMatcher.group(3).trim();
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
				result = graph.getPaths(src, dst);
			} catch (Exception exception) {
				String src = args[0].trim();
				String dst = args[1].trim();
				result = graph.getPaths(src, dst);
			}
			if (result != null) {
				graphObjects.put(target, result);
				outputStream.println(String.format("Result saved in graph %s", target));
			} else {
				outputStream.println(String.format("Error querying graph %s!", input));
			}
		} else if (lineageMatcher.matches()) {
			String target = lineageMatcher.group(1);
			String input = lineageMatcher.group(2);
			String expression = lineageMatcher.group(3).trim();
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
				result = graph.getLineage(src, direction);
			} catch (Exception exception) {
				String src = args[0].trim();
				result = graph.getLineage(src, direction);
			}
			if (result != null) {
				graphObjects.put(target, result);
				outputStream.println(String.format("Result saved in graph %s", target));
			} else {
				outputStream.println(String.format("Error querying graph %s!", input));
			}
		} else {
			outputStream.println("Available commands:");
			outputStream.println("\t <var> = importGraph(<path>)");
			outputStream.println("\t <var>.exportGraph(<path>)");
			outputStream.println("\t <var>.showVertices(<expression>)");
			outputStream.println("\t <var> = <var>.getPaths(<src id | expression>, <dst id | expression>)");
			outputStream.println("\t <var> = <var>.getLineage(<src id | expression>, <direction>)");
			outputStream.println("\t exit");
		}
	}
}
