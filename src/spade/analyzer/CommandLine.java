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

import java.io.BufferedReader;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * @author raza
 */
public class CommandLine extends AbstractAnalyzer
{
    private static HashMap<String, String> constraints = new HashMap<>();

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
                    long start_time = System.currentTimeMillis();
                    if(line.equalsIgnoreCase(QueryCommands.QUERY_EXIT.value))
                    {
                        break;
                    }
                    else if(line.toLowerCase().startsWith("set"))
                    {
                        // set storage for querying
                        String output = parseSetStorage(line);
                        queryOutputStream.writeObject(output);
                    }
                    else if(AbstractQuery.getCurrentStorage() == null)
                    {
                        String message = "No storage set for querying. " +
                                "Use command: 'set storage <storage_name>'";
                        queryOutputStream.writeObject(message);
                    }
                    else if(line.equals(QueryCommands.QUERY_LIST_CONSTRAINTS.value))
                    {
                        StringBuilder output = new StringBuilder(100);
                        output.append("Constraint Name\t\t | Constraint Expression\n");
                        output.append("-------------------------------------------------\n");
                        for(Map.Entry<String, String> currentEntry : constraints.entrySet())
                        {
                            String key = currentEntry.getKey();
                            String value = currentEntry.getValue();
                            output.append(key).append("\t\t\t | ").append(value).append("\n");
                        }
                        output.append("-------------------------------------------------\n");
                        queryOutputStream.writeObject(output.toString());
                    }
                    else if(line.contains(":"))
                    {
                        // hoping its a constraint
                        String output = createConstraint(line);
                        queryOutputStream.writeObject(output);
                    }
                    else
                    {
                        line = replaceConstraintNames(line.trim());
                        try
                        {
                            boolean success = parseQuery(line);
                            if(!success)
                            {
                                String message = "Function name not valid! Make sure you follow the guidelines";
                                throw new Exception(message);
                            }
                            AbstractQuery queryClass;
                            Class<?> returnType;
                            Object result;
                            String functionClassName = getFunctionClassName(functionName);
                            if(functionClassName == null)
                            {
                                String message = "Required query class not available!";
                                throw new Exception(message);
                            }
                            queryClass = (AbstractQuery) Class.forName(functionClassName).newInstance();
                            returnType = Class.forName(getReturnType(functionName));
                            result = queryClass.execute(functionArguments);
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
                                        discrepancyDetector.setResponseGraph(finalGraphSet);
                                        int discrepancyCount = discrepancyDetector.findDiscrepancy();
                                        logger.log(Level.WARNING, "discrepancyCount: " + discrepancyCount);
                                        if(discrepancyCount == 0)
                                        {
                                            discrepancyDetector.update();
                                        }
                                        for(Graph graph : finalGraphSet)
                                        {
                                            result = Graph.union((Graph) result, graph);
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
                                }
                                // if result output is to be converted into dot file format
                                if(EXPORT_RESULT)
                                {
                                    Graph temp_result = new Graph();
                                    if(functionName.equalsIgnoreCase("GetEdge"))
                                    {
                                        temp_result.edgeSet().addAll((Set<AbstractEdge>) result);
                                        result = temp_result;
                                    }
                                    else if(functionName.equalsIgnoreCase("GetVertex"))
                                    {
                                        temp_result.vertexSet().addAll((Set<AbstractVertex>) result);
                                        result = temp_result;
                                    }
                                    result = ((Graph) result).exportGraph();
                                    EXPORT_RESULT = false;
                                }
                            }
                            else
                            {
                                logger.log(Level.SEVERE, "Return type null or mismatch!");
                            }
                            long elapsed_time = System.currentTimeMillis() - start_time;
                            logger.log(Level.INFO, "Time taken for query: " + elapsed_time + " ms");
                            if(result != null)
                            {
                                queryOutputStream.writeObject(result.toString());
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

        public boolean parseQuery(String query_line)
        {
            functionName = null;
            if(query_line.startsWith("export"))
            {
                query_line = query_line.substring(query_line.indexOf("export") + "export".length());
                EXPORT_RESULT = true;
            }
            try
            {
                // get the function name
                Pattern token_pattern = Pattern.compile("\\(");
                String[] tokens = token_pattern.split(query_line);
                functionName = tokens[0].trim();
                String arguments_string = tokens[1].substring(0, tokens[1].length() - 1);
                functionArguments = replaceConstraintNames(arguments_string);

                return true;
            }
            catch(Exception ex)
            {
                Logger.getLogger(CommandLine.QueryConnection.class.getName()).log(Level.SEVERE, "Error in parsing query: \n" + query_line, ex);
            }
            return false;
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

    private static String createConstraint(String line)
    {
        String output;
        try
        {
            String[] tokens = line.split(":");
            String constraint_name = tokens[0].trim();
            String constraint_expression = tokens[1].trim();
            constraints.put(constraint_name, constraint_expression);
            output = "Constraint '" + constraint_name + "' created.";
        }
        catch(Exception ex)
        {
            output = "Unable to create constraint!";
            logger.log(Level.SEVERE, output, ex);
        }

        return output;
    }

    private static String replaceConstraintNames(String arguments_string)
    {
        String arguments = arguments_string.trim();
        for(Map.Entry<String, String> constraint : constraints.entrySet())
        {
            String constraint_name = constraint.getKey();
            String constraint_expression = constraint.getValue();
            if(arguments.contains(constraint_name))
            {
                arguments = arguments.replaceAll("\\b" + Pattern.quote(constraint_name) + "\\b", constraint_expression);
            }
        }

        return arguments;
    }

    private static String parseSetStorage(String line)
    {
        String output = null;
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
                    output = "Storage '" + storageName + "' successfully set for querying.";
                }
                else
                {
                    output = "Storage '" + tokens[2] + "' not found";
                }
            }
        }
        catch(Exception ex)
        {
            output = "Error setting storage!";
            logger.log(Level.SEVERE, output, ex);
        }

        return output;
    }
}