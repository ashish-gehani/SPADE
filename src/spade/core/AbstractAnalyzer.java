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
package spade.core;

import spade.client.QueryMetaData;
import spade.utility.DiscrepancyDetector;

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
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import static spade.core.Kernel.CONFIG_PATH;
import static spade.core.Kernel.FILE_SEPARATOR;
import static spade.core.Kernel.sslServerSocketFactory;

/**
 * @author raza
 */
public abstract class AbstractAnalyzer
{
    public String QUERY_PORT;
    protected AbstractResolver remoteResolver;
    protected volatile boolean SHUTDOWN = false;
    private static Map<String, List<String>> functionToClassMap;
    protected static boolean EXPORT_RESULT = false;
    public static final String COMPARISON_OPERATORS = "=|>|<|>=|<=";
    public static final String BOOLEAN_OPERATORS = "AND|OR";
    protected static final DiscrepancyDetector discrepancyDetector = new DiscrepancyDetector();
    protected static Properties databaseConfigs = new Properties();
    private static String configFile = CONFIG_PATH + FILE_SEPARATOR + "spade.core.AbstractAnalyzer.config";
    public static boolean USE_SCAFFOLD;
    public static boolean USE_TRANSFORMER;
    static
    {
        try
        {
            databaseConfigs.load(new FileInputStream(configFile));
            USE_SCAFFOLD = Boolean.parseBoolean(databaseConfigs.getProperty("use_scaffold"));
            USE_TRANSFORMER = Boolean.parseBoolean(Settings.getProperty("use_transformer"));
        }
        catch(Exception ex)
        {
            // default settings
            USE_SCAFFOLD = false;
            USE_TRANSFORMER = false;
            Logger.getLogger(AbstractAnalyzer.class.getName()).log(Level.WARNING,
                    "Loading configurations from the file unsuccessful! Falling back to default settings" , ex);
        }
    }

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
            Logger.getLogger(AbstractAnalyzer.class.getName()).log(Level.WARNING, "Unable to read functionToClassMap from file!");
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
        List<String> classInfo = functionToClassMap.get(functionName);
        if(classInfo == null)
        {
            String className;
            //TODO: create all query classes with abstractanalyzer beforehand
            if(functionName.equals("GetLineage") || functionName.equals("GetPaths"))
            {
                className = "spade.query.common." + functionName;
                classInfo = Arrays.asList(className, "spade.core.Graph");
            }
            else
            {
                String storageName = AbstractQuery.getCurrentStorage().getClass().getSimpleName().toLowerCase();
                className = "spade.query." + storageName + "." + functionName;
                classInfo = Arrays.asList(className, "java.lang.Object");
            }
            try
            {
                Class.forName(className);
                functionToClassMap.put(functionName, classInfo);
            }
            catch(ClassNotFoundException ex)
            {
                Logger.getLogger(AbstractAnalyzer.class.getName()).log(Level.SEVERE, "Unable to find/load query class!", ex);
                return null;
            }
        }

        return classInfo.get(0);
    }

    public String getReturnType(String functionName)
    {
        List<String> values = functionToClassMap.get(functionName);
        if(values == null)
        {
            String storageName = AbstractQuery.getCurrentStorage().getClass().getSimpleName().toLowerCase();
            values = Arrays.asList("spade.query." + storageName + "." + functionName, "Object");
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

        protected abstract boolean parseQuery(String line);

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
