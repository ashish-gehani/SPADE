package spade.core;

import spade.client.QueryMetaData;
import spade.utility.InconsistencyDetector;

import javax.net.ssl.SSLServerSocket;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static spade.core.Kernel.sslServerSocketFactory;

/**
 * @author raza
 */
public abstract class AbstractAnalyzer
{
    public String QUERY_PORT;
    protected AbstractResolver remoteResolver;
    protected volatile boolean SHUTDOWN = false;
    protected boolean USE_TRANSFORMER = Boolean.parseBoolean(Settings.getProperty("use_transformer"));
    private static Map<String, List<String>> functionToClassMap;
    protected static boolean EXPORT_RESULT = false;
    public static final String COMPARISON_OPERATORS = "=|>|<|>=|<=";
    public static final String BOOLEAN_OPERATORS = "AND|OR";
    protected static final InconsistencyDetector inconsistencyDetector = new InconsistencyDetector();

    /**
     * remoteResolutionRequired is used by query module to signal the Analyzer
     * to resolve any outstanding remote parts of result graph.
     */
    private static boolean remoteResolutionRequired = false;

    public static void setRemoteResolutionRequired()
    {
        remoteResolutionRequired = true;
    }

    public static void clearRemoteResolutionRequired()
    {
        remoteResolutionRequired = false;
    }

    public static boolean isRemoteResolutionRequired()
    {
        return remoteResolutionRequired;
    }

    public abstract boolean initialize();

    static
    {
        // load functionToClassMap here
        String file_name = "cfg/functionToClassMap";
        File file = new File(file_name);
        try
        {
            FileInputStream fis = new FileInputStream(file);
            ObjectInputStream ois = new ObjectInputStream(fis);
            functionToClassMap = (Map<String, List<String>>) ois.readObject();
            ois.close();
            fis.close();
        }
        catch(IOException | ClassNotFoundException ex)
        {
            functionToClassMap = new HashMap<>();
            Logger.getLogger(AbstractAnalyzer.class.getName()).log(Level.WARNING, "Unable to read functionToClassMap from file!", ex);
        }
    }

    public void shutdown()
    {
        // store functionToClassMap here
        String file_name = "cfg/functionToClassMap";
        File file = new File(file_name);
        try
        {
            FileOutputStream fos = new FileOutputStream(file);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(functionToClassMap);
            oos.flush();
            oos.close();
            fos.close();
        }
        catch(IOException ex)
        {
            Logger.getLogger(AbstractAnalyzer.class.getName()).log(Level.WARNING, "Unable to write functionToClassMap to file!", ex);
        }
        // signal to analyzer
        SHUTDOWN = true;
    }

    public static void registerFunction(String func_name, String class_name, String ret_type)
    {
        functionToClassMap.put(func_name, Arrays.asList(class_name, ret_type));
    }

    public String getFunctionClassName(String functionName)
    {
        // key -> values
        // function name -> function class name, function return type
        List<String> className = functionToClassMap.get(functionName);
        if(className == null)
        {
            //TODO: create all query classes with abstractanalyzer beforehand
            if(functionName.equals("GetLineage") || functionName.equals("GetPaths"))
                className = Arrays.asList("spade.query.common." + functionName, "spade.core.Graph");
            else
            {
                String storageName = AbstractQuery.getCurrentStorage().getClass().getSimpleName().toLowerCase();
                className = Arrays.asList("spade.query." + storageName + "." + functionName, "java.lang.Object");
            }
            functionToClassMap.put(functionName, className);
        }
        return className.get(0);
    }

    public String getReturnType(String functionName)
    {
        List<String> values = functionToClassMap.get(functionName);
        if(values == null)
        {
            values = Arrays.asList("spade.query.postgresql." + functionName, "Object");
        }

        return values.get(1);
    }

    public static ServerSocket getServerSocket(String socketName)
    {
        ServerSocket serverSocket;
        Integer port = null;
        try
        {
            port = Integer.parseInt(Settings.getProperty(socketName));
            serverSocket = sslServerSocketFactory.createServerSocket(port);
            ((SSLServerSocket) serverSocket).setNeedClientAuth(true);
            Kernel.addServerSocket(serverSocket);
        }
        catch(IOException ex)
        {
            String message = "Socket " + socketName + " creation unsuccessful at port # " + port;
            Logger.getLogger(AbstractAnalyzer.class.getName()).log(Level.SEVERE, message, ex);
            return null;
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

        protected Graph iterateTransformers(Graph graph, QueryMetaData queryMetaData)
        {
            synchronized (Kernel.transformers)
            {
                for(int i = 0; i < Kernel.transformers.size(); i++)
                {
                    AbstractTransformer transformer = Kernel.transformers.get(i);
                    if(graph != null)
                    {
                        try
                        {
                            graph = transformer.putGraph(graph, queryMetaData);
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
