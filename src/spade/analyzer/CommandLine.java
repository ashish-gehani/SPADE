/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2017 SRI International
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
package spade.analyzer;

import spade.client.QueryMetaData;
import spade.core.AbstractAnalyzer;
import spade.core.AbstractEdge;
import spade.core.AbstractQuery;
import spade.core.AbstractStorage;
import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.core.Kernel;
import spade.resolver.Recursive;
import spade.utility.Query;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
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
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static spade.core.AbstractStorage.BUILD_SCAFFOLD;
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
                            logger.log(Level.INFO, "Closed server socket for analyzer");
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
                ObjectInputStream queryInputStream = new ObjectInputStream(inStream);
                while(!SHUTDOWN)
                {
                    // Commands read from the input stream and executed.
                    Query query = (Query) queryInputStream.readObject();
                    String line = query.getQueryString();
                    String nonce = query.getQueryTime();
                    if(line.equalsIgnoreCase(QueryCommands.QUERY_EXIT.value))
                    {
                        break;
                    }
                    else if (line.toLowerCase().startsWith("set"))
                    {
                        boolean success = parseSetStorage(line);
                        if(success)
                        {
                            queryOutputStream.writeObject("success");
                        }
                        else
                        {
                            queryOutputStream.writeObject("failure");
                        }
                    }
                    else
                    {
                        try
                        {
                            if(AbstractQuery.getCurrentStorage() == null)
                            {
                                String message = "No storage available to query!";
                                throw new Exception(message);
                            }
                            logger.log(Level.INFO, "Executing query: " + line.trim());
                            boolean parse_successful = parseQuery(line.trim());
                            if(!parse_successful)
                            {
                                String message = "Querying parsing not successful! Make sure you follow the guidelines";
                                throw new Exception(message);
                            }
                            AbstractQuery queryClass;
                            Object localResult;
                            Class<?> resultType;
                            Object finalResult = null;
                            Class<?> finalResultType;
                            if( (functionName.equalsIgnoreCase("GetLineage") ||
                                    functionName.equalsIgnoreCase("GetPaths") )
                                    && USE_SCAFFOLD && BUILD_SCAFFOLD)
                            {
                                localResult = scaffold.queryManager(queryParameters);
                                resultType = Graph.class;
                                finalResultType = Set.class;
                            }
                            else
                            {
                                String functionClassName = getFunctionClassName(functionName);
                                if(functionClassName == null)
                                {
                                    String message = "Required query class not available!";
                                    throw new Exception(message);
                                }
                                queryClass = (AbstractQuery) Class.forName(functionClassName).newInstance();
                                resultType = Class.forName(getReturnType(functionName));
                                finalResultType = resultType;
                                localResult = queryClass.execute(queryParameters, resultLimit);
                                finalResult = localResult;
                            }
                            //TODO: Do something in case when no result found
                            if(localResult != null && resultType.isAssignableFrom(localResult.getClass()))
                            {
                                if(localResult instanceof Graph)
                                {
                                    ((Graph) localResult).setQueryString(queryString);
                                    ((Graph) localResult).setMaxDepth(Integer.parseInt(maxLength));
                                    logger.log(Level.INFO, "queryString: " + ((Graph) localResult).getQueryString());
                                    logger.log(Level.INFO, "maxDepth: " + ((Graph) localResult).getMaxDepth());
                                    ((Graph) localResult).setHostName(Kernel.HOST_NAME);
                                    Properties props = new Properties();
                                    props.load(new FileInputStream("find_inconsistency.txt"));
                                    boolean find_inconsistency = Boolean.parseBoolean(props.getProperty("find_inconsistency"));
                                    if(isRemoteResolutionRequired())
                                    {
                                        logger.log(Level.INFO, "Performing remote resolution.");
                                        //TODO: Could use a factory pattern here to get remote resolver
                                        remoteResolver = new Recursive((Graph) localResult, functionName,
                                                Integer.parseInt(maxLength), direction, nonce);
                                        Thread remoteResolverThread = new Thread(remoteResolver, "Recursive-Resolver");
                                        remoteResolverThread.start();
                                        // wait for thread to complete to get the final graph
                                        remoteResolverThread.join();
                                        // returns a set of un-stitched graphs
                                        finalResult = remoteResolver.getCombinedResultSet();
                                        finalResultType = Set.class;
                                        clearRemoteResolutionRequired();
                                        if(find_inconsistency)
                                        {
                                            discrepancyDetector.setQueryDirection(direction);
                                            discrepancyDetector.setResponseGraph((Set<Graph>) finalResult);
                                            int discrepancyCount = discrepancyDetector.findDiscrepancy();
                                            logger.log(Level.WARNING, "discrepancyCount: " + discrepancyCount);
                                            if(discrepancyCount == 0)
                                            {
                                                discrepancyDetector.update();
                                            }
                                        }
                                        AbstractAnalyzer.addSignature((Graph) localResult, nonce);
                                        ((Set<Graph>) finalResult).add((Graph) localResult);
                                        int vertex_count = ((Graph) localResult).vertexSet().size();
                                        int edge_count = ((Graph) localResult).edgeSet().size();
                                        int total = vertex_count + edge_count;
                                        String stats = "Local result stats. Total: " + total + " Vertices: " + vertex_count + ", Edges: " + edge_count;
                                        logger.log(Level.INFO, stats);
                                        logger.log(Level.INFO, "Remote resolution completed.");
                                    } else
                                    {
                                        AbstractAnalyzer.addSignature((Graph) localResult, nonce);
                                        int vertex_count = ((Graph) localResult).vertexSet().size();
                                        int edge_count = ((Graph) localResult).edgeSet().size();
                                        int total = vertex_count + edge_count;
                                        String stats = "Result graph stats. Total: " + total + " Vertices: " + vertex_count + ", Edges: " + edge_count;
                                        logger.log(Level.INFO, stats);

                                        if(find_inconsistency)
                                        {
                                            logger.log(Level.INFO, "find_inconsistency: true");
                                            discrepancyDetector.setQueryDirection(direction);
                                            modifyResult((Graph) localResult);
                                            logger.log(Level.INFO, "result vertex set size: " + ((Graph) localResult).vertexSet().size());
                                            logger.log(Level.INFO, "result edge set size: " + ((Graph) localResult).edgeSet().size());
                                            finalResult = new HashSet<>();
                                            ((Set<Graph>) finalResult).add((Graph) localResult);
                                            discrepancyDetector.setResponseGraph((Set<Graph>) finalResult);
                                            int discrepancyCount = discrepancyDetector.findDiscrepancy();
                                            logger.log(Level.INFO, "discrepancyCount:" + discrepancyCount);
                                            if(discrepancyCount == 0)
                                                discrepancyDetector.update();
                                        } else
                                        {
                                            finalResult = new HashSet<>();
                                            ((Set<Graph>) finalResult).add((Graph) localResult);
                                        }
                                        finalResultType = Set.class;
                                    }
                                    if(USE_TRANSFORMER)
                                    {
                                        localResult = new Graph();
                                        for(Graph graph : (Set<Graph>) finalResult)
                                        {
                                            ((Graph) localResult).union(graph);
                                        }
                                        logger.log(Level.INFO, "Applying transformers on the final result.");
                                        Map<String, Object> queryMetaDataMap = getQueryMetaData((Graph) localResult);
                                        QueryMetaData queryMetaData = new QueryMetaData(queryMetaDataMap);
                                        localResult = iterateTransformers((Graph) localResult, queryMetaData);
                                        finalResult = localResult;
                                        logger.log(Level.INFO, "Transformers applied successfully.");
                                    }
                                }
                                // if result output is to be converted into dot file format
                                if(EXPORT_RESULT)
                                {
                                    if(localResult instanceof Graph)
                                    {
                                        for(Graph graph : (Set<Graph>) finalResult)
                                        {
                                            ((Graph) localResult).union(graph);
                                        }
                                    } else
                                    {
                                        Graph temp_result = new Graph();
                                        if(functionName.equalsIgnoreCase("GetEdge"))
                                        {
                                            temp_result.edgeSet().addAll((Set<AbstractEdge>) localResult);
                                            localResult = temp_result;
                                        } else if(functionName.equalsIgnoreCase("GetVertex"))
                                        {
                                            temp_result.vertexSet().addAll((Set<AbstractVertex>) localResult);
                                            localResult = temp_result;
                                        }
                                    }

                                    finalResultType = String.class;
                                    finalResult = ((Graph) localResult).exportGraph();
                                    logger.log(Level.INFO, "Exporting the result to file");
                                    EXPORT_RESULT = false;
                                }
                            }
                            else
                            {
                                logger.log(Level.SEVERE, "Return type null or mismatch!");
                            }
                            queryOutputStream.writeObject(finalResultType.getName());
                            if(finalResult != null)
                            {
                                queryOutputStream.writeObject(finalResult);
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
                List<AbstractVertex> vertexSet = new ArrayList<>(result.vertexSet());
                props = new Properties();
                props.load(new FileInputStream("random_seed.txt"));
                int random_seed = Integer.parseInt(props.getProperty("random_seed"));
                Collections.shuffle(vertexSet, new Random(random_seed));
                for(AbstractVertex vertex: vertexSet)
                {
                    if(removedVerticesCount >= vertices)
                    {
                        break;
                    }
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
                List<AbstractEdge> edgeSet = new ArrayList<>(result.edgeSet());
                Collections.shuffle(edgeSet, new Random(random_seed));
                for(AbstractEdge edge: edgeSet)
                {
                    if(removedEdgesCount >= edges)
                    {
                        break;
                    }
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

        private boolean parseSetStorage(String line)
        {
            try
            {
                String[] tokens = line.split("\\s+");
                String setCommand = tokens[0].toLowerCase().trim();
                String storageCommand = tokens[1].toLowerCase().trim();
                String storageName = tokens[2].toLowerCase().trim();
                if(setCommand.equals("set") && storageCommand.equals("storage"))
                {
                    AbstractStorage storage = Kernel.getStorage(storageName);
                    if(storage != null)
                    {
                        AbstractQuery.setCurrentStorage(storage);
                        logger.log(Level.INFO, "Storage '" + storageName + "' successfully set for querying.");
                        return true;
                    }
                    else
                    {
                        logger.log(Level.SEVERE, "Storage '" + tokens[2] + "' not found");
                    }
                }
            }
            catch(Exception ex)
            {
                logger.log(Level.SEVERE, " Error setting storages! " + ex);
            }

            return false;
        }


        @Override
        public boolean parseQuery(String query_line)
        {
            if(query_line.startsWith("export"))
            {
                query_line = query_line.substring(query_line.indexOf("export") + "export".length());
                EXPORT_RESULT = true;
            }
            queryString = query_line;
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
                        direction = arguments[2].trim().toLowerCase();
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
                return false;
            }
            return true;
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
