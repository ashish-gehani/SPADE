package spade.analyzer;

import spade.core.AbstractAnalyzer;
import spade.core.AbstractQuery;
import spade.core.Graph;
import spade.resolver.Naive;

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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static spade.core.Kernel.KERNEL_SHUTDOWN;

/**
 * @author raza
 */
public class Dig extends AbstractAnalyzer
{
    // Strings for new query format
    public enum DigQueryCommands
    {
        QUERY_CONSTRAINT("constraints -> <key> <operator> <value>"),
        QUERY_ARITHMETIC_OPERATORS("arithmetic operators -> = | > | < | >= | <="),
        QUERY_BOOLEAN_OPERATORS("boolean operators ->AND | OR"),
        QUERY_LIMIT("result limit -> LIMIT n"),
        QUERY_FUNCTION_LIST("available functions -> GetVertex | GetEdge | GetChildren | GetParents |GetLineage | GetPaths"),
        QUERY_FUNCTION_ARGUMENTS("arguments -> <constraint> | (<constraint> <boolean_operator> <constraint>)+"),
        QUERY_FUNCTION_GET_VERTEX("GetVertex(<arguments, limit>)"),
        QUERY_FUNCTION_GET_EDGE("GetEdge(<arguments, limit>)"),
        QUERY_FUNCTION_GET_CHILDREN("GetVertex(<arguments, limit>)"),
        QUERY_FUNCTION_GET_PARENTS("GetParents(<arguments, limit>)"),
        QUERY_FUNCTION_GET_LINEAGE("GetLineage(<arguments, limit, direction, maxDepth>)"),
        QUERY_FUNCTION_GET_PATHS("GetPaths(<arguments, limit, direction, maxLength>)"),
        QUERY_EXIT("exit");

        String value;
        DigQueryCommands(String value)
        {
            this.value = value;
        }
    }

    public Dig()
    {
        QUERY_PORT = "dig_query_port";
    }

    @Override
    public void init()
    {
        Runnable queryRunnable = new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    ServerSocket serverSocket = getServerSocket(QUERY_PORT);
                    while(!KERNEL_SHUTDOWN)
                    {
                        Socket querySocket = serverSocket.accept();
                        QueryConnection thisConnection = new QueryConnection(querySocket);
                        Thread connectionThread = new Thread(thisConnection);
                        connectionThread.start();
                    }
                }
                catch(SocketException exception)
                {
                    //TODO: how?
                    // Do nothing... this is triggered on KERNEL_SHUTDOWN.
                }
                catch(NumberFormatException | IOException ex)
                {
                    Logger.getLogger(AbstractAnalyzer.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        };
        Thread queryThread = new Thread(queryRunnable, "querySocket-Thread");
        queryThread.start();
    }

    /**
     * Method to display query commands to the given output stream.
     *
     * @return
     */
    public static String getQueryCommands()
    {
        StringBuilder query = new StringBuilder(200);
        query.append("Available Commands: \n");
        for(DigQueryCommands command: DigQueryCommands.values())
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
                    if(line.equalsIgnoreCase(ANALYZER_EXIT))
                    {
                        break;
                    }
                    else
                    {
                        parseQuery(line);
                        AbstractQuery queryClass = (AbstractQuery) Class.forName(getFunctionClassName(functionName)).newInstance();
                        Class<?> returnType = Class.forName(getReturnType(functionName));
                        Object result = queryClass.execute(queryParameters, resultLimit);
                        if(result.getClass().isAssignableFrom(returnType))
                        {
                            if(result != null)
                            {
                                if(isSetRemoteFlag())
                                {
                                    //TODO: Could use a factory pattern here to get remote resolver
                                    remoteResolver = new Naive((Graph)result, functionName, 0, null);
                                    Thread remoteResolverThread = new Thread(remoteResolver, "Naive-RemoteResolver");
                                    remoteResolverThread.start();
                                    // wait for thread to complete to get the final graph
                                    remoteResolverThread.join();
                                    result = Graph.union((Graph) result, remoteResolver.getFinalGraph());
                                    clearRemoteFlag();
                                }
                                if(result instanceof Graph)
                                {
                                    result = iterateTransformers((Graph) result, line);
                                }
                                queryOutputStream.writeObject(result);
                            }
                            else
                            {
                                //TODO: Change the need for this too
                                queryOutputStream.writeObject(getQueryCommands());
                            }
                        }
                        else
                        {
                            Logger.getLogger(Dig.QueryConnection.class.getName()).log(Level.SEVERE, "Return type mismatch!");
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
                Logger.getLogger(Dig.QueryConnection.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        @Override
        public void parseQuery(String query_line)
        {
            // Step1: get the function name
            String[] tokens = query_line.split("\\(");
            functionName = tokens[0];
            String argument_string = tokens[1].substring(0, tokens[1].length() - 1);
            String[] arguments = argument_string.split(",");
            String constraints = arguments[0];
            resultLimit = 1;
            if(arguments.length == 2)
                resultLimit = Integer.parseInt(arguments[1]);
            else if(arguments.length == 3)
                direction = arguments[2];
            else if(arguments.length == 4)
                maxLength = arguments[3];

            // Step2: get the argument expression(s), split by the boolean operators
            // The format for one argument is:
            // <key> ARITHMETIC_OPERATOR <value> [BOOLEAN_OPERATOR]
            String[] expressions = constraints.split("((?<=(" + DigQueryCommands.QUERY_BOOLEAN_OPERATORS.value + "))|" +
                    "(?=(" + DigQueryCommands.QUERY_BOOLEAN_OPERATORS.value + ")))");

            // extract the key value pairs
            int i = 0;
            while(i < expressions.length)
            {
                String expression = expressions[i];
                String[] operands = expression.split("((?<=("+ DigQueryCommands.QUERY_ARITHMETIC_OPERATORS.value +"))|" +
                        "(?=(" + DigQueryCommands.QUERY_ARITHMETIC_OPERATORS.value + ")))");
                String key = operands[0];
                String operator = operands[1];
                String value = operands[2];

                List<String> values = new ArrayList<>();
                values.add(operator);
                values.add(value);
                i++;
                // if boolean operator is present
                if(i < expressions.length && DigQueryCommands.QUERY_BOOLEAN_OPERATORS.value.contains(expressions[i]))
                {
                    String bool_operator = expressions[i];
                    values.add(bool_operator);
                    i++;
                }
                else
                    values.add(null);

                queryParameters.put(key, values);
            }
            if(functionName.equals("GetLineage"))
            {
                queryParameters.put("direction", Collections.singletonList(direction));
                queryParameters.put("maxDepth", Collections.singletonList(maxLength));
            }
            else if (functionName.equals("GetPaths"))
            {
                queryParameters.put("direction", Collections.singletonList(direction));
                queryParameters.put("maxLength", Collections.singletonList(maxLength));
            }
        }
    }

}











