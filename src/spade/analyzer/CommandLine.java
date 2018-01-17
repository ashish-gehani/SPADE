package spade.analyzer;

import spade.client.QueryMetaData;
import spade.core.AbstractAnalyzer;
import spade.core.AbstractEdge;
import spade.core.AbstractQuery;
import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.core.Kernel;
import spade.resolver.Recursive;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static spade.core.AbstractStorage.USE_SCAFFOLD;
import static spade.core.AbstractStorage.scaffold;

/**
 * @author raza
 */
public class CommandLine extends AbstractAnalyzer
{
    // Strings for new query format
    public enum QueryCommands
    {
        QUERY_FUNCTION_LIST_KEY("available functions:"),
        QUERY_FUNCTION_GET_VERTEX("\t GetVertex(expression [, limit])"),
        QUERY_FUNCTION_GET_EDGE("\t GetEdge(expression [, limit])"),
        QUERY_FUNCTION_GET_CHILDREN("\t GetChildren(expression [, limit])"),
        QUERY_FUNCTION_GET_PARENTS("\t GetParents(expression [, limit])"),
        QUERY_FUNCTION_GET_LINEAGE("\t GetLineage(expression, maxDepth, direction)"),
        QUERY_FUNCTION_GET_PATHS("\t GetPaths(expression, maxLength)"),

        QUERY_FUNCTION_EXPRESSION_KEY("expression: "),
        QUERY_FUNCTION_EXPRESSION_VALUE("\t <constraint_name> [<boolean_operator> <constraint_name> ...]"),
        QUERY_CONSTRAINT_KEY("constraint creation: "),
        QUERY_CONSTRAINT_VALUE("\t <constraint_name>: <key> <comparison_operator> <value>"),
        QUERY_COMPARISON_OPERATORS_KEY("comparison operators: "),
        QUERY_COMPARISON_OPERATORS_VALUE("\t = | > | < | >= | <="),
        QUERY_BOOLEAN_OPERATORS_KEY("boolean operators: "),
        QUERY_BOOLEAN_OPERATORS_VALUE("\t AND | OR"),
        QUERY_DIRECTION_KEY("direction: "),
        QUERY_DIRECTION_VALUE("\t a[ncestors] | d[escendants]"),

        QUERY_FUNCTION_EXPORT("export > <path_to_file_for_next_query>"),
        QUERY_LIST_CONSTRAINTS("list constraints"),
        QUERY_EXIT("exit");

        public String value;

        QueryCommands(String value)
        {
            this.value = value;
        }
    }

    public CommandLine()
    {
        QUERY_PORT = "commandline_query_port";
    }
    private static final Logger logger = Logger.getLogger(CommandLine.class.getName());

    @Override
    public boolean initialize()
    {
        ServerSocket serverSocket = AbstractAnalyzer.getServerSocket(QUERY_PORT);
        if(serverSocket != null)
        {
            Runnable queryRunnable = new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        while(!Kernel.isShutdown() && !SHUTDOWN)
                        {
                            Socket querySocket = serverSocket.accept();
                            QueryConnection thisConnection = new QueryConnection(querySocket);
                            Thread connectionThread = new Thread(thisConnection);
                            connectionThread.start();
                        }
                    }
                    catch(SocketException ex)
                    {
                        // Do nothing. This is triggered on shutdown
                    }
                    catch(Exception ex)
                    {
                        logger.log(Level.SEVERE, null, ex);
                    }
                    finally
                    {
                        try
                        {
                            serverSocket.close();
                        }
                        catch(Exception ex)
                        {
                            logger.log(Level.SEVERE, "Unable to close server socket", ex);
                        }
                    }
                }
            };
            Thread queryThread = new Thread(queryRunnable, "querySocket-Thread");
            queryThread.start();
            return true;
        }
        else
        {
            logger.log(Level.SEVERE, "Server Socket not initialized");
            return false;
        }
    }

    /**
     * Method to display query commands to the given output stream.
     *
     * @return All query commands for the client in a single string
     */
    public static String getQueryCommands()
    {
        StringBuilder query = new StringBuilder(500);
        query.append("Available Commands: \n");
        for(QueryCommands command : QueryCommands.values())
        {
            query.append("\t");
            query.append(command.value);
            query.append("\n");
        }

        return query.toString();
    }

    public class QueryConnection extends AbstractAnalyzer.QueryConnection
    {
        QueryConnection(Socket socket)
        {
            super(socket);
        }

        @Override
        public void run()
        {
            try
            {
                OutputStream outStream = querySocket.getOutputStream();
                InputStream inStream = querySocket.getInputStream();
                ObjectOutputStream queryOutputStream = new ObjectOutputStream(outStream);
                BufferedReader queryInputStream = new BufferedReader(new InputStreamReader(inStream));

                while(!SHUTDOWN)
                {
                    // Commands read from the input stream and executed.
                    String line = queryInputStream.readLine();
                    if(line.equalsIgnoreCase(QueryCommands.QUERY_EXIT.value))
                    {
                        break;
                    }
                    else
                    {
                        try
                        {
                            parseQuery(line.trim());
                            AbstractQuery queryClass;
                            Class<?> returnType;
                            Object result;
                            if(USE_SCAFFOLD)
                            {
                                result = scaffold.queryManager(queryParameters);
                                returnType = Graph.class;
                            }
                            else
                            {
                                queryClass = (AbstractQuery) Class.forName(getFunctionClassName(functionName)).newInstance();
                                returnType = Class.forName(getReturnType(functionName));
                                result = queryClass.execute(queryParameters, resultLimit);
                            }
                            if(result != null && returnType.isAssignableFrom(result.getClass()))
                            {
                                if(result instanceof Graph)
                                {
                                    if(isRemoteResolutionRequired())
                                    {
                                        logger.log(Level.INFO, "Performing remote resolution.");
                                        //TODO: Could use a factory pattern here to get remote resolver
                                        remoteResolver = new Recursive((Graph) result, functionName,
                                                        Integer.parseInt(maxLength), direction);
                                        Thread remoteResolverThread = new Thread(remoteResolver, "Recursive-AbstractResolver");
                                        remoteResolverThread.start();
                                        // wait for thread to complete to get the final graph
                                        remoteResolverThread.join();
                                        // final graph is a set of un-stitched graphs
                                        Set<Graph> finalGraphSet = remoteResolver.getFinalGraph();
                                        clearRemoteResolutionRequired();
                                        // TODO: perform consistency check here - Carol
                                        inconsistencyDetector.setResponseGraph(finalGraphSet);
                                        int inconsistencyCount = inconsistencyDetector.findInconsistency();
                                        logger.log(Level.WARNING, "inconsistencyCount: " + inconsistencyCount);
                                        if(inconsistencyCount == 0)
                                        {
                                            inconsistencyDetector.update();
                                        }
                                        // TODO: return the stitched graphs
                                        for(Graph graph: finalGraphSet)
                                        {
                                            result = Graph.union((Graph)result, graph);
                                        }
                                        logger.log(Level.INFO, "Remote resolution completed.");
                                    }
                                    if(USE_TRANSFORMER)
                                    {
                                        logger.log(Level.INFO, "Applying transformers on the final result.");
                                        Map<String, Object> queryMetaDataMap = getQueryMetaData((Graph) result);
                                        QueryMetaData queryMetaData = new QueryMetaData(queryMetaDataMap);
                                        result = iterateTransformers((Graph) result, queryMetaData);
                                        logger.log(Level.INFO, "Transformers applied successfully.");
                                    }
                                    // if result output is to be converted into dot file format
                                    if(EXPORT_RESULT)
                                    {
                                        returnType = String.class;
                                        result = ((Graph) result).exportGraph();
                                        EXPORT_RESULT = false;
                                    }
                                }
                            }
                            else
                            {
                                logger.log(Level.SEVERE, "Return type null or mismatch!");
                            }
                            queryOutputStream.writeObject(returnType.getName());
                            if(result != null)
                            {
//                                String string = "Vertices: " + ((Graph) result).vertexSet().size();
//                                string += ". Edges: " + ((Graph) result).edgeSet().size() + ".\n";
//                                logger.log(Level.INFO, string);
//                                string += result.toString();
//                                logger.log(Level.INFO, string);
//                                queryOutputStream.writeObject(string);
                                if(functionName.equals("GetLineage"))
                                    modifyResult((Graph) result);
                                queryOutputStream.writeObject(result);
                            }
                            else
                            {
                                queryOutputStream.writeObject("Result Empty");
                            }

                        }
                        catch(Exception ex)
                        {
                            logger.log(Level.SEVERE, "Error executing query request!", ex);
                            queryOutputStream.writeObject("Error");
                        }
                    }

                }
                queryInputStream.close();
                queryOutputStream.close();

                inStream.close();
                outStream.close();
                querySocket.close();
            }
            catch(Exception ex)
            {
                Logger.getLogger(CommandLine.QueryConnection.class.getName()).log(Level.SEVERE, null, ex);
            }
            finally
            {
                try
                {
                    querySocket.close();
                }
                catch(Exception ex)
                {
                    Logger.getLogger(CommandLine.QueryConnection.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        private void modifyResult(Graph result)
        {
            try
            {
                Properties props = new Properties();
                props.load(new FileInputStream("modifications.txt"));
                int vertices = Integer.parseInt(props.getProperty("vertices"));
                int edges = Integer.parseInt(props.getProperty("edges"));

                int removedVerticesCount = 0;
                String removedVertices = "";
                List<AbstractVertex> verticesToRemove = new ArrayList<>();
                for(AbstractVertex vertex: result.vertexSet())
                {
                    if(removedVerticesCount >= vertices)
                        break;
                    removedVertices =  removedVertices + vertex.bigHashCode() + ", ";
                    verticesToRemove.add(vertex);
                    removedVerticesCount++;
                }
                logger.log(Level.INFO, "removedVertices: " + removedVertices);
                for(int i = 0; i < verticesToRemove.size(); i++)
                {
                    result.vertexSet().remove(verticesToRemove.get(i));
                }

                int removedEdgesCount = 0;
                String removedEdges = "";
                List<AbstractEdge> edgesToRemove = new ArrayList<>();
                for(AbstractEdge edge: result.edgeSet())
                {
                    if(removedEdgesCount >= edges)
                        break;
                    removedEdges = removedEdges + edge.bigHashCode() + ", ";
                    edgesToRemove.add(edge);
                    removedEdgesCount++;
                }
                logger.log(Level.INFO, "removedEdges: " + removedEdges);
                for(int i = 0; i < edgesToRemove.size(); i++)
                {
                    result.edgeSet().remove(edgesToRemove.get(i));
                }

            }
            catch(Exception ex)
            {
                logger.log(Level.SEVERE, "Error modifying graph result!", ex);
            }
        }

        @Override
        public void parseQuery(String query_line)
        {
            if(query_line.startsWith("export"))
            {
                query_line = query_line.substring(query_line.indexOf("export") + "export".length());
                EXPORT_RESULT = true;
            }
            queryParameters = new LinkedHashMap<>();
            try
            {
                // Step1: get the function name
                Pattern token_pattern = Pattern.compile("\\(");
                String[] tokens = token_pattern.split(query_line);
                functionName = tokens[0].trim();
                String argument_string = tokens[1].substring(0, tokens[1].length() - 1);
                Pattern argument_pattern = Pattern.compile(",");
                String[] arguments = argument_pattern.split(argument_string);
                String constraints = arguments[0].trim();
                switch(functionName)
                {
                    case "GetLineage":
                        maxLength = arguments[1].trim();
                        direction = arguments[2].trim();
                        break;
                    case "GetPaths":
                        maxLength = arguments[1].trim();
                        break;
                    default:
                        if(arguments.length > 1)
                            resultLimit = Integer.parseInt(arguments[1].trim());
                        break;
                }

                // Step2: get the argument expression(s), split by the boolean operators
                // The format for one argument is:
                // <key> COMPARISON_OPERATOR <value> [BOOLEAN_OPERATOR]
                Pattern constraints_pattern = Pattern.compile("((?i)(?<=(\\b" + BOOLEAN_OPERATORS + "\\b))|" +
                        "((?=(\\b" + BOOLEAN_OPERATORS + "\\b))))");
                String[] expressions = constraints_pattern.split(constraints);

                // extract the key value pairs
                int i = 0;
                while(i < expressions.length)
                {
                    String expression = expressions[i];
                    Pattern expression_pattern = Pattern.compile("((?<=(" + COMPARISON_OPERATORS + "))|" +
                            "(?=(" + COMPARISON_OPERATORS + ")))");
                    String[] operands = expression_pattern.split(expression);
                    String key = operands[0].trim();
                    String operator = operands[1].trim();
                    String value = operands[2].trim();

                    List<String> values = new ArrayList<>();
                    values.add(operator);
                    values.add(value);
                    i++;
                    // if boolean operator is present
                    if(i < expressions.length &&
                            BOOLEAN_OPERATORS.toLowerCase().contains(expressions[i].toLowerCase()))
                    {
                        String bool_operator = expressions[i].trim();
                        values.add(bool_operator);
                        i++;
                    }
                    else
                    {
                        values.add(null);
                    }

                    queryParameters.put(key, values);
                }
                if(functionName.equals("GetLineage"))
                {
                    queryParameters.put("direction", Collections.singletonList(direction));
                    queryParameters.put("maxDepth", Collections.singletonList(maxLength));
                }
                else if(functionName.equals("GetPaths"))
                {
                    queryParameters.put("direction", Collections.singletonList(direction));
                    queryParameters.put("maxLength", Collections.singletonList(maxLength));
                }
            }
            catch(Exception ex)
            {
                Logger.getLogger(CommandLine.QueryConnection.class.getName()).log(Level.SEVERE, "Error in parsing query: \n" + query_line, ex);
            }
        }

        private Map<String, Object> getQueryMetaData(Graph result)
        {
            Map<String, Object> queryMetaData = new HashMap<>();
            queryMetaData.put("storage", AbstractQuery.getCurrentStorage().getClass().getSimpleName());
            queryMetaData.put("operation", functionName);
            queryMetaData.put("rootVertex", result.getRootVertex());
            queryMetaData.put("rootVertexHash", result.getRootVertex().bigHashCode());
            queryMetaData.put("childVertex", result.getRootVertex());
            queryMetaData.put("childVertexHash", result.getRootVertex().bigHashCode());
            queryMetaData.put("parentVertex", result.getDestinationVertex());
            queryMetaData.put("parentVertexHash", result.getDestinationVertex().bigHashCode());
            queryMetaData.put("maxLength", maxLength);
            queryMetaData.put("direction", direction);

            return queryMetaData;
        }
    }
}








