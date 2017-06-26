package spade.analyzer;

import spade.core.AbstractAnalyzer;
import spade.core.AbstractQuery;
import spade.core.Graph;
import spade.core.Kernel;
import spade.resolver.Recursive;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * @author raza
 */
public class CommandLine extends AbstractAnalyzer
{
    // Strings for new query format
    public enum DigQueryCommands
    {
        QUERY_CONSTRAINT_KEY("constraint: "),
        QUERY_CONSTRAINT_VALUE("<constraint_name> = <key> <operator> <value>"),
        QUERY_ARITHMETIC_OPERATORS_KEY("arithmetic operators: "),
        QUERY_ARITHMETIC_OPERATORS_VALUE("=|>|<|>=|<="),
        QUERY_BOOLEAN_OPERATORS_KEY("boolean operators: "),
        QUERY_BOOLEAN_OPERATORS_VALUE("AND|OR"),
        QUERY_LIMIT_KEY("result limit: "),
        QUERY_LIMIT_VALUE("LIMIT n"),
        QUERY_FUNCTION_LIST_KEY("available functions:"),
        QUERY_FUNCTION_LIST_VALUE("GetVertex | GetEdge | GetChildren | GetParents | GetLineage | GetPaths"),
        QUERY_FUNCTION_ARGUMENTS_KEY("argument: "),
        QUERY_FUNCTION_ARGUMENTS_VALUE("<constraint_name> | (<constraint_name> <boolean_operator> <constraint_name>)+"),

        QUERY_FUNCTION_GET_VERTEX("GetVertex(<arguments, limit>)"),
        QUERY_FUNCTION_GET_EDGE("GetEdge(<arguments, limit>)"),
        QUERY_FUNCTION_GET_CHILDREN("GetVertex(<arguments, limit>)"),
        QUERY_FUNCTION_GET_PARENTS("GetParents(<arguments, limit>)"),
        QUERY_FUNCTION_GET_LINEAGE("GetLineage(<arguments, limit, direction, maxDepth>)"),
        QUERY_FUNCTION_GET_PATHS("GetPaths(<arguments, limit, direction, maxLength>)"),
        QUERY_LIST_CONSTRAINTS("list constraints"),
        QUERY_EXIT("exit");

        public String value;

        DigQueryCommands(String value)
        {
            this.value = value;
        }
    }

    public CommandLine()
    {
        QUERY_PORT = "commandline_query_port";
    }

    @Override
    public boolean initialize()
    {
        try
        {
            Runnable queryRunnable = new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        ServerSocket serverSocket = AbstractAnalyzer.getServerSocket(QUERY_PORT);
                        while(!Kernel.isShutdown() && !SHUTDOWN)
                        {
                            Socket querySocket = serverSocket.accept();
                            QueryConnection thisConnection = new QueryConnection(querySocket);
                            Thread connectionThread = new Thread(thisConnection);
                            connectionThread.start();
                        }
                    }
                    catch(NumberFormatException | IOException ex)
                    {
                        Logger.getLogger(CommandLine.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            };
            Thread queryThread = new Thread(queryRunnable, "querySocket-Thread");
            queryThread.start();
            return true;
        }
        catch(Exception ex)
        {
            Logger.getLogger(CommandLine.class.getName()).log(Level.SEVERE, null, ex);
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
        for(DigQueryCommands command : DigQueryCommands.values())
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
                    if(line.equalsIgnoreCase(DigQueryCommands.QUERY_EXIT.value))
                    {
                        break;
                    }
                    else
                    {
                        parseQuery(line);
                        AbstractQuery queryClass = (AbstractQuery) Class.forName(getFunctionClassName(functionName)).newInstance();
                        Class<?> returnType = Class.forName(getReturnType(functionName));
                        Object result = queryClass.execute(queryParameters, resultLimit);
                        if(result != null && result.getClass().isAssignableFrom(returnType))
                        {
                            if(result instanceof Graph)
                            {
                                if(isRemoteResolutionRequired())
                                {
                                    //TODO: Could use a factory pattern here to get remote resolver
                                    remoteResolver = new Recursive((Graph) result, functionName, 0, null);
                                    Thread remoteResolverThread = new Thread(remoteResolver, "Recursive-AbstractResolver");
                                    remoteResolverThread.start();
                                    // wait for thread to complete to get the final graph
                                    remoteResolverThread.join();
                                    // final graph is a set of unstitched graphs
                                    result = remoteResolver.getFinalGraph();
                                    clearRemoteResolutionRequired();
                                    // TODO: perform consistency check here - Carol
                                }
                                if(USE_TRANSFORMER)
                                    result = iterateTransformers((Graph) result, line);
                            }
                            queryOutputStream.writeObject(returnType);
                            queryOutputStream.writeObject(result.toString());
                        }
                        else
                        {
                            Logger.getLogger(CommandLine.QueryConnection.class.getName()).log(Level.SEVERE, "Return type null or mismatch!");
                        }
                    }

                }
                queryInputStream.close();
                queryOutputStream.close();

                inStream.close();
                outStream.close();
                querySocket.close();
            } catch(Exception ex)
            {
                Logger.getLogger(CommandLine.QueryConnection.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        @Override
        public void parseQuery(String query_line)
        {
            try
            {
                // Step1: get the function name
                Pattern token_pattern = Pattern.compile("\\(");
                String[] tokens = token_pattern.split(query_line);
                functionName = tokens[0];
                String argument_string = tokens[1].substring(0, tokens[1].length() - 1);
                Pattern argument_pattern = Pattern.compile(",");
                String[] arguments = argument_pattern.split(argument_string);
                String constraints = arguments[0];
                resultLimit = 1;
                if(arguments.length >= 2)
                    resultLimit = Integer.parseInt(arguments[1]);
                if(arguments.length > 2)
                {
                    direction = arguments[2];
                    maxLength = arguments[3];
                }

                // Step2: get the argument expression(s), split by the boolean operators
                // The format for one argument is:
                // <key> ARITHMETIC_OPERATOR <value> [BOOLEAN_OPERATOR]
                Pattern constraints_pattern = Pattern.compile("((?i)(?<=(" + DigQueryCommands.QUERY_BOOLEAN_OPERATORS_VALUE.value + "))|" +
                        "((?i)?=(" + DigQueryCommands.QUERY_BOOLEAN_OPERATORS_VALUE.value + ")))");
                String[] expressions = constraints_pattern.split(constraints);

                // extract the key value pairs
                int i = 0;
                while(i < expressions.length)
                {
                    String expression = expressions[i];
                    Pattern expression_pattern = Pattern.compile("((?<=(" + DigQueryCommands.QUERY_ARITHMETIC_OPERATORS_VALUE.value + "))|" +
                            "(?=(" + DigQueryCommands.QUERY_ARITHMETIC_OPERATORS_VALUE.value + ")))");
                    String[] operands = expression_pattern.split(expression);
                    String key = operands[0];
                    String operator = operands[1];
                    String value = operands[2];

                    List<String> values = new ArrayList<>();
                    values.add(operator);
                    values.add(value);
                    i++;
                    // if boolean operator is present
                    if(i < expressions.length &&
                            DigQueryCommands.QUERY_BOOLEAN_OPERATORS_VALUE.value.toLowerCase().contains(expressions[i].toLowerCase()))
                    {
                        String bool_operator = expressions[i];
                        values.add(bool_operator);
                        i++;
                    } else
                        values.add(null);

                    queryParameters.put(key, values);
                }
                if(functionName.equals("GetLineage"))
                {
                    queryParameters.put("direction", Collections.singletonList(direction));
                    queryParameters.put("maxDepth", Collections.singletonList(maxLength));
                } else if(functionName.equals("GetPaths"))
                {
                    queryParameters.put("direction", Collections.singletonList(direction));
                    queryParameters.put("maxLength", Collections.singletonList(maxLength));
                }
            }
            catch(Exception ex)
            {
                Logger.getLogger(CommandLine.QueryConnection.class.getName()).log(Level.SEVERE, "Error in parsing query", ex);
            }
        }
    }
}











