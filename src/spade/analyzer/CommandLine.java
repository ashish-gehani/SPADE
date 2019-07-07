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

import spade.core.AbstractAnalyzer;
import spade.core.AbstractQuery;
import spade.core.AbstractStorage;
import spade.core.Graph;
import spade.core.Kernel;
import spade.query.graph.kernel.Environment;
import spade.query.postgresql.kernel.Program;
import spade.query.postgresql.kernel.Resolver;
import spade.query.postgresql.parser.DSLParserWrapper;
import spade.query.postgresql.parser.ParseProgram;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

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
                InputStream inStream = querySocket.getInputStream();
                OutputStream outStream = querySocket.getOutputStream();
                BufferedReader queryInputStream = new BufferedReader(new InputStreamReader(inStream));
                ObjectOutputStream responseOutputStream = new ObjectOutputStream(outStream);

                boolean quitting = false;
                while(!quitting && !SHUTDOWN)
                {
                    quitting = processRequest(queryInputStream, responseOutputStream);
                }

                queryInputStream.close();
                responseOutputStream.close();
                inStream.close();
                outStream.close();
            }
            catch(Exception e)
            {
                logger.log(Level.SEVERE, null, e);
            }
            finally
            {
                try
                {
                    querySocket.close();
                }
                catch(Exception e)
                {
                    logger.log(Level.SEVERE, "Unable to close query socket", e);
                }
            }
        }

        @Override
        protected boolean parseQuery(String line)
        {
            return true;
        }

        private boolean processRequest(BufferedReader inputStream, ObjectOutputStream outputStream)
        {
            try
            {
                String query = inputStream.readLine();
                if(query != null && query.toLowerCase().startsWith("export"))
                {
                    query = query.substring(6);
                }
                if(query == null || query.trim().equalsIgnoreCase("exit"))
                {
                    return true;
                }

                outputStream.writeObject(execute(query));
            }
            catch(IOException ex)
            {
                logger.log(Level.SEVERE, "Unable to process query request!", ex);
            }
            return false;
        }

        private String execute(String query)
        {
            ArrayList<Object> responses;
            try
            {
                DSLParserWrapper parserWrapper = new DSLParserWrapper();
                ParseProgram parseProgram = parserWrapper.fromText(query);

                logger.log(Level.INFO, "Parse tree:\n" + parseProgram.toString());

                Environment env = new Environment();

                Resolver resolver = new Resolver();
                Program program = resolver.resolveProgram(parseProgram, env);

                logger.log(Level.INFO, "Execution plan:\n" + program.toString());

                try
                {
                    responses = program.execute();
                }
                finally
                {
                    env.gc();
                }
            }
            catch(Exception ex)
            {
                responses = new ArrayList<>();
                StringWriter stackTrace = new StringWriter();
                PrintWriter pw = new PrintWriter(stackTrace);
                pw.println("Error evaluating QuickGrail command:");
                pw.println("------------------------------------------------------------");
                // e.printStackTrace(pw);
                pw.println(ex.getMessage());
                pw.println("------------------------------------------------------------");
                responses.add(stackTrace.toString());
            }

            if(responses == null || responses.isEmpty())
            {
                return "OK";
            }
            else
            {
                // Currently only return the last response.
                Object response = responses.get(responses.size() - 1);
                return response == null ? "" : response.toString();
            }
        }

        private Map<String, Object> getQueryMetaData(Graph result)
        {
            Map<String, Object> queryMetaData = new HashMap<>();
            queryMetaData.put("storage", AbstractQuery.getCurrentStorage().getClass().getSimpleName());
            queryMetaData.put("operation", functionName);
            queryMetaData.put("rootVertex", result.getRootVertexSet());
            queryMetaData.put("rootVertexHash", result.getRootVertexSet().bigHashCode());
            queryMetaData.put("childVertex", result.getRootVertexSet());
            queryMetaData.put("childVertexHash", result.getRootVertexSet().bigHashCode());
            queryMetaData.put("parentVertex", result.getDestinationVertex());
            queryMetaData.put("parentVertexHash", result.getDestinationVertex().bigHashCode());
            queryMetaData.put("maxLength", maxLength);
            queryMetaData.put("direction", direction);

            return queryMetaData;
        }
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