package spade.core;


import spade.client.QueryParameters;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author raza
 */
public abstract class AbstractAnalyzer
{
    public static final String ANALYZER_EXIT = "exit";

    protected static String QUERY_PORT;
    protected RemoteResolver remoteResolver;
    protected volatile boolean SHUTDOWN = false;

    private static SSLServerSocketFactory sslServerSocketFactory;
    private static Map<String, List<String>> functionToClassMap;

    /**
     * The following members relate to remoteFlag.
     * remoteFlag is used by query module to signal the Analyzer
     * to resolve any outstanding remote parts of result graph.
     */
    private static boolean remoteFlag = false;

    public abstract void init();

    public static void setRemoteFlag()
    {
        remoteFlag = true;
    }

    public static void clearRemoteFlag()
    {
        remoteFlag = false;
    }

    public static boolean isSetRemoteFlag()
    {
        return remoteFlag;
    }


    public void shutdown()
    {
        SHUTDOWN = true;
    }


    public static void registerFunction(String func, String class_, String ret)
    {
        functionToClassMap.put(func, Arrays.asList(class_, ret));
    }

    public String getFunctionClassName(String functionName)
    {
        // key -> values
        // function name -> function class name, function return type
        List<String> values = functionToClassMap.get(functionName);
        if(values == null)
        {
            // some policy here
        }

        return values.get(0);
    }

    public String getReturnType(String functionName)
    {
        List<String> values = functionToClassMap.get(functionName);
        if(values == null)
        {
            // some policy here
        }

        return values.get(1);
    }

    public ServerSocket getServerSocket(String socketName)
    {
        ServerSocket serverSocket = null;
        Integer port = null;
        try
        {
            port = Integer.parseInt(Settings.getProperty(socketName));
            if(port == null)
                return null;
            serverSocket = sslServerSocketFactory.createServerSocket(port);
            ((SSLServerSocket) serverSocket).setNeedClientAuth(true);
            Kernel.addServerSocket(serverSocket);
        }
        catch(IOException ex)
        {
            String message = "Socket " + socketName + " creation unsuccessful at port # " + port;
            Logger.getLogger(AbstractAnalyzer.class.getName()).log(Level.SEVERE, message, ex);
        }

        return serverSocket;
    }

    protected abstract class QueryConnection implements Runnable
    {
        protected Socket querySocket;
        protected Map<String, List<String>> queryParameters;
        protected String queryConstraints;
        protected String functionName;
        protected String queryStorage;
        protected Integer resultLimit = null;
        protected String direction = null;
        protected String maxLength = null;

        public QueryConnection(Socket socket)
        {
            querySocket = socket;
        }

        @Override
        public abstract void run();

        protected abstract void parseQuery(String line);

        protected Graph iterateTransformers(Graph graph, String query)
        {
            synchronized (Kernel.transformers)
            {
                QueryParameters digQueryParams = QueryParameters.parseQuery(query);
                for(int i = 0; i < Kernel.transformers.size(); i++)
                {
                    AbstractTransformer transformer = Kernel.transformers.get(i);
                    if(graph != null)
                    {
                        try
                        {
                            graph = transformer.putGraph(graph, digQueryParams);
                            if(graph != null)
                            {
                                //commit after every transformer to enable reading without error
                                graph.commitIndex();
                            }
                        }
                        catch(Exception ex)
                        {
                            Logger.getLogger(QueryConnection.class.getName()).log(Level.SEVERE, "Error in applying transformer!", ex);
                        }
                    }
                    else
                    {
                        break;
                    }
                }
            }
            return graph;
        }
    }
}
