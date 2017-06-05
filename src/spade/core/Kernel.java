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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import java.security.KeyStore;
import java.security.SecureRandom;

import java.util.AbstractMap.SimpleEntry;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import spade.filter.FinalCommitFilter;

/**
 * The SPADE kernel containing the control client and
 * managing all the central activities.
 *
 * @author Dawood Tariq and Raza Ahmad
 */
public class Kernel
{

    private static final String SPADE_ROOT = Settings.getProperty("spade_root");
    /**
     * A map used to cache the remote sketches.
     */
    public static Map<String, AbstractSketch> remoteSketches;
    /**
     * Path to configuration file for storing state of SPADE instance (includes
     * currently added modules).
     */
    public static String configFile = SPADE_ROOT + "cfg/spade.config";
    /**
     * Path to log files including the prefix.
     */
    public static String pidFile = "spade.pid";
    /**
     * Path to log files.
     */
    public static String logPath = SPADE_ROOT + "log/";
    /**
     * Path to log files including the prefix.
     */
    public static String logPathAndPrefix = logPath + "/SPADE_";
    /**
     * Date/time suffix pattern for log files.
     */
    public static final String logFilenamePattern = "MM.dd.yyyy-H.mm.ss";
    /**
     * Set of reporters active on the local SPADE instance.
     */
    public static Set<AbstractReporter> reporters;
    /**
     * Set of analyzers active on the local SPADE instance.
     */
    public static Set<AbstractAnalyzer> analyzers;
    /**
     * Set of storages active on the local SPADE instance.
     */
    public static Set<AbstractStorage> storages;
    /**
     * Set of filters active on the local SPADE instance.
     */
    public static List<AbstractFilter> filters;
    /**
     * Set of transformers active on the local SPADE instance.
     */
    public static List<AbstractTransformer> transformers;
    /**
     * Set of sketches active on the local SPADE instance.
     */
    public static Set<AbstractSketch> sketches;
    /**
     * Boolean used to initiate the clean SHUTDOWN procedure. This is used by the
     * different threads to determine if the KERNEL_SHUTDOWN procedure has been called.
     */
    public static volatile boolean KERNEL_SHUTDOWN;
    /**
     * Boolean used to indicate whether the transactions need to be flushed by
     * the storages.
     */
    public static volatile boolean flushTransactions;

    /**
     * Miscellaneous members
     */
    private static List<ServerSocket> serverSockets;
    private static Set<AbstractReporter> removeReporters;
    private static Set<AbstractStorage> removeStorages;
    private static Set<AbstractAnalyzer> removeAnalyzers;
    private static final int BATCH_BUFFER_ELEMENTS = 1000000;
    private static final int MAIN_THREAD_SLEEP_DELAY = 10;
    private static final int REMOVE_WAIT_DELAY = 100;
    private static final int FIRST_FILTER = 0;
    private static final Logger logger = Logger.getLogger(Kernel.class.getName());
    private static boolean ANDROID_PLATFORM = false;

    /**
     * Strings for control client
     */
    private static final String ADD_REPORTER_STORAGE_STRING = "add reporter|storage <class name> <initialization arguments>";
    private static final String ADD_ANALYZER_STRING = "add analyzer <class name>";
    private static final String ADD_FILTER_TRANSFORMER_STRING = "add filter|transformer <class name> position=<number> <initialization arguments>";
    private static final String ADD_SKETCH_STRING = "add sketch <class name>";
    private static final String REMOVE_REPORTER_STORAGE_SKETCH_ANALYZER_STRING = "remove reporter|analyzer|storage|sketch <class name>";
    private static final String REMOVE_FILTER_TRANSFORMER_STRING = "remove filter|transformer <position number>";
    private static final String LIST_STRING = "list reporters|storages|analyzers|filters|sketches|transformers|all";
    private static final String CONFIG_STRING = "config load|save <filename>";
    private static final String EXIT_STRING = "exit";
    private static final String SHUTDOWN_STRING = "KERNEL_SHUTDOWN";

    /**
     * Members for creating secure sockets
     */
    private static KeyStore clientKeyStorePublic;
    private static KeyStore clientKeyStorePrivate;
    private static KeyStore serverKeyStorePublic;
    private static KeyStore serverKeyStorePrivate;
    public static SSLSocketFactory sslSocketFactory;
    public static SSLServerSocketFactory sslServerSocketFactory;

    /*
      Description of how the below locks and indicator variables are used to send acknowledgments for 'KERNEL_SHUTDOWN'
      and 'exit' commands back to the control clients. Irrespective of the control client who sent the KERNEL_SHUTDOWN
      command, the ACK is sent to all the connected control clients so that they know also that the kernel has been
      SHUTDOWN. 'remainingShutdownAcks' integer is incremented every time a control client connects to the kernel.
      It is decremented every time a control client exits or shuts down. 'allShutdownsAcknowledgedLock' is used for the
      kernel to wait on until all the ACKs have been sent to control clients and the last control client notifies the
      kernel that it is safe to proceed now. 'controlConnectionsLock' is used to safely increment/decrement it.
      'shutdownComplete' boolean is used to indicate by the kernel to all the control clients that KERNEL_SHUTDOWN has
      been completed. So, all clients wait for this to be set to true by waiting on 'shutdownCompleteLock'.
     */
    /**
     * Time to timeout after when reading from the control client socket
     */
    private final static int CONTROL_CLIENT_READ_TIMEOUT = 1000;
    /**
     * A lock to be able to safely modify remainingShutdownAcks
     */
    static Object controlConnectionsLock = new Object();
    /**
     * To keep track of how many control clients are connected and to how many
     * the KERNEL_SHUTDOWN acknowledgement needs to be sent still.
     */
    static volatile int remainingShutdownAcks = -1;
    /**
     * A lock for waiting and notifying when all KERNEL_SHUTDOWN
     * acknowledgments have been sent.
     */
    static Object allShutdownsAcknowledgedLock = new Object();
    /**
	 * A lock for waiting and notifying that the kernel has completed
     * SHUTDOWN and it is safe to send KERNEL_SHUTDOWN acknowledgments
	*/
	static Object shutdownCompleteLock = new Object();
    /**
	 * A boolean variable to indicate that SHUTDOWN has been
     * completed by the kernel
	 */
	static volatile boolean shutdownComplete = false;

     /**
     * The main initialization function.
     *
     * @param args arguments to the main function, used for signalling Android usage for now.
     */
    public static void main(String args[])
    {
        if (args.length == 1 && args[0].equals("android"))
        {
            ANDROID_PLATFORM = true;
        }

        // Set up context for secure connections
        if (!ANDROID_PLATFORM)
        {
            try
            {
                setupKeyStores();
                setupClientSSLContext();
                setupServerSSLContext();
            }
            catch (Exception ex)
            {
                logger.log(Level.SEVERE, null, ex);
            }
        }

        try
        {
            new File(logPath).mkdirs();
            // Configuring the global exception logger
            String logFilename = new java.text.SimpleDateFormat(logFilenamePattern).format(new java.util.Date(System.currentTimeMillis()));
            Handler logFileHandler = new FileHandler(logPathAndPrefix + logFilename + ".log");
            logFileHandler.setFormatter(new SimpleFormatter());
            Logger.getLogger("").addHandler(logFileHandler);
        }
        catch (IOException | SecurityException ex)
        {
            System.err.println("Error initializing exception logger");
        }

        registerShutdownThread();

        initializeObjects();

        registerMainThread();

        registerControlThread();

        // Load the SPADE configuration from the default config file.
        configCommand("config load " + configFile, NullStream.out);
    }

    private static void setupKeyStores() throws Exception
    {
        String serverPublicPath = Settings.getProperty("spade_root") + "cfg/ssl/server.public";
        String serverPrivatePath = Settings.getProperty("spade_root") + "cfg/ssl/server.private";
        String clientPublicPath = Settings.getProperty("spade_root") + "cfg/ssl/client.public";
        String clientPrivatePath = Settings.getProperty("spade_root") + "cfg/ssl/client.private";

        serverKeyStorePublic = KeyStore.getInstance("JKS");
        serverKeyStorePublic.load(new FileInputStream(serverPublicPath), "public".toCharArray());
        serverKeyStorePrivate = KeyStore.getInstance("JKS");
        serverKeyStorePrivate.load(new FileInputStream(serverPrivatePath), "private".toCharArray());
        clientKeyStorePublic = KeyStore.getInstance("JKS");
        clientKeyStorePublic.load(new FileInputStream(clientPublicPath), "public".toCharArray());
        clientKeyStorePrivate = KeyStore.getInstance("JKS");
        clientKeyStorePrivate.load(new FileInputStream(clientPrivatePath), "private".toCharArray());
    }

    private static void setupClientSSLContext() throws Exception
    {
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextInt();

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(serverKeyStorePublic);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(clientKeyStorePrivate, "private".toCharArray());

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), secureRandom);
        sslSocketFactory = sslContext.getSocketFactory();
    }

    private static void setupServerSSLContext() throws Exception
    {
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextInt();

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(clientKeyStorePublic);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(serverKeyStorePrivate, "private".toCharArray());

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), secureRandom);
        sslServerSocketFactory = sslContext.getServerSocketFactory();
    }

    public static void addServerSocket(ServerSocket socket)
    {
        serverSockets.add(socket);
    }

    /**
     * Initialize all basic components and moving parts of SPADE
     */
    private static void initializeObjects()
    {
        reporters = Collections.synchronizedSet(new HashSet<AbstractReporter>());
        analyzers = Collections.synchronizedSet(new HashSet<AbstractAnalyzer>());
        storages = Collections.synchronizedSet(new HashSet<AbstractStorage>());
        transformers = Collections.synchronizedList(new LinkedList<AbstractTransformer>());
        filters = Collections.synchronizedList(new LinkedList<AbstractFilter>());
        sketches = Collections.synchronizedSet(new HashSet<AbstractSketch>());
        remoteSketches = Collections.synchronizedMap(new HashMap<String, AbstractSketch>());
        serverSockets = Collections.synchronizedList(new LinkedList<ServerSocket>());

        removeReporters = Collections.synchronizedSet(new HashSet<AbstractReporter>());
        removeStorages = Collections.synchronizedSet(new HashSet<AbstractStorage>());
        removeAnalyzers = Collections.synchronizedSet(new HashSet<AbstractAnalyzer>());

        KERNEL_SHUTDOWN = false;
        flushTransactions = true;

        // The FinalCommitFilter acts as a terminator for the filter list
        // and also maintains a pointer to the list of active storages to which
        // the provenance data is finally passed. It also has a reference to
        // the SketchManager and triggers its putVertex() and putEdge() methods
        FinalCommitFilter commitFilter = new FinalCommitFilter();
        commitFilter.storages = storages;
        commitFilter.sketches = sketches;
        filters.add(commitFilter);

        // The final transformer is used to send vertex and edge objects to
        // their corresponding result Graph.
        // FinalTransformer finalTransformer = new FinalTransformer();
        // transformers.add(finalTransformer);
    }

    /**
     * Initialize the main thread. This thread performs critical
     * provenance-related work inside SPADE.
     * It extracts provenance objects (vertices, edges) from the
     * buffers, adds the source_reporter annotation to each object which is
     * class name of the reporter, and then sends these objects to the filter list.
     * This thread is also used for cleanly removing reporters and storages
     * through the control commands and also when shutting down. This is done by
     * ensuring that once a reporter is marked for removal, the provenance objects from
     * its buffer are completely flushed.
     */
    private static void registerMainThread()
    {
        Runnable mainRunnable = new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    while (true)
                    {
                        if (KERNEL_SHUTDOWN)
                        {
                            // The KERNEL_SHUTDOWN process is also partially handled by
                            // this thread. On KERNEL_SHUTDOWN, all reporters are marked for removal so
                            // that their buffers are cleanly flushed and no data is lost.
                            // When a buffer becomes empty, it is removed along with its corresponding reporter.
                            // When all buffers become empty, this thread terminates.
                            Iterator<AbstractReporter> reporterIterator = reporters.iterator();
                            while(reporterIterator.hasNext())
                            {
                                AbstractReporter currentReporter = reporterIterator.next();
                                Buffer currentBuffer = currentReporter.getBuffer();
                                if (currentBuffer.isEmpty())
                                {
                                    reporterIterator.remove();
                                }
                            }
                            if (reporters.isEmpty())
                            {
                                shutdown();
                                break;
                            }
                        }
                        if (flushTransactions)
                        {
                            // Flushing of transactions is also handled by this thread to ensure that
                            // there are no errors/problems when using storages that are sensitive to
                            // thread-context for their transactions.
                            // For example, this is true for the embedded neo4j graph database.
                            for (AbstractStorage currentStorage : storages)
                            {
                                currentStorage.flushTransactions();
                            }
                            flushTransactions = false;
                        }

                        if (!removeStorages.isEmpty())
                        {
                            // Check if a storage is marked for removal.
                            // If it is, shut it down and remove it from the list.
                            Iterator<AbstractStorage> iterator = removeStorages.iterator();
                            while(iterator.hasNext())
                            {
                                AbstractStorage currentStorage = iterator.next();
                                currentStorage.shutdown();
                                iterator.remove();
                            }
                        }
                        if (!removeAnalyzers.isEmpty())
                        {
                            // Check if an analyzer is marked for removal.
                            // If it is, shut it down and remove it from the list.
                            Iterator<AbstractAnalyzer> iterator = removeAnalyzers.iterator();
                            while(iterator.hasNext())
                            {
                                AbstractAnalyzer currentAnalyzer = iterator.next();
                                currentAnalyzer.shutdown();
                                iterator.remove();
                            }
                        }

                        for (AbstractReporter reporter : reporters)
                        {
                            // This loop performs the actual task of committing provenance data to
                            // the storages. Each reporter is selected and the nested loop is used to
                            // extract buffer elements in a batch manner for increased efficiency.
                            // The elements are then passed to the filter list.
                            Buffer buffer = reporter.getBuffer();
                            for (int i = 0; i < BATCH_BUFFER_ELEMENTS; i++)
                            {
                                Object bufferElement = buffer.getBufferElement();
                                if (bufferElement instanceof AbstractVertex)
                                {
                                    AbstractVertex tempVertex = (AbstractVertex) bufferElement;
                                    filters.get(FIRST_FILTER).putVertex(tempVertex);
                                }
                                else if (bufferElement instanceof AbstractEdge)
                                {
                                    AbstractEdge tempEdge = (AbstractEdge) bufferElement;
                                    filters.get(FIRST_FILTER).putEdge(tempEdge);
                                }
                                else if (bufferElement == null)
                                {
                                    if (removeReporters.contains(reporter))
                                    {
                                        removeReporters.remove(reporter);
                                    }
                                    break;
                                }
                            }
                        }
                        Thread.sleep(MAIN_THREAD_SLEEP_DELAY);
                    }
                }
                catch (Exception exception)
                {
                    logger.log(Level.SEVERE, "Error registering Main Thread", exception);
                }
            }
        };
        Thread mainThread = new Thread(mainRunnable, "mainSPADE-Thread");
        mainThread.start();
    }

    /**
     * Register a SHUTDOWN hook to terminate gracefully
     */
    private static void registerShutdownThread()
    {
        Runtime.getRuntime().addShutdownHook(new Thread()
        {
            @Override
            public void run()
            {
                if (!KERNEL_SHUTDOWN)
                {
                    // Save current configuration.
                    configCommand("config save " + configFile, NullStream.out);
                    // Shut down all reporters.
                    for (AbstractReporter reporter : reporters)
                    {
                        reporter.shutdown();
                    }
                    // Wait for main thread to consume all provenance data.
                    while (!reporters.isEmpty())
                    {
                        Iterator<AbstractReporter> reporterIterator = reporters.iterator();
                        while(reporterIterator.hasNext())
                        {
                            AbstractReporter currentReporter = reporterIterator.next();
                            Buffer currentBuffer = currentReporter.getBuffer();
                            if (currentBuffer.isEmpty())
                            {
                                reporterIterator.remove();
                            }
                        }
                        try
                        {
                            Thread.sleep(MAIN_THREAD_SLEEP_DELAY);
                        }
                        catch (InterruptedException ex)
                        {
                            logger.log(Level.WARNING, null, ex);
                        }
                    }
                    // Shut down filters.
                    for (int i = 0; i < filters.size() - 1; i++)
                    {
                        filters.get(i).shutdown();
                    }
                    // Shut down analyzers.
                    for(AbstractAnalyzer analyzer: analyzers)
                    {
                        analyzer.shutdown();
                    }
                    // Shut down storages.
                    for (AbstractStorage storage : storages)
                    {
                        storage.shutdown();
                    }
                    // Shut down server sockets.
                    for (ServerSocket socket : serverSockets)
                    {
                        try
                        {
                            socket.close();
                        }
                        catch (IOException ex)
                        {
                            logger.log(Level.WARNING, "Error closing down server socket!", ex);
                        }
                    }
                }
                // Terminate SPADE
            }
        });
    }

    /**
     * This thread creates the input and output pipes used for control
     * (and also used by the control client). The exit value is used to
     * determine if the pipes were successfully created.
     * The input pipe to which commands are issued is read in
     * a loop and the commands are processed.
     */
    private static void registerControlThread()
    {
        Runnable controlRunnable = new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    ServerSocket serverSocket = null;
                    int port = Integer.parseInt(Settings.getProperty("local_control_port"));
                    if (ANDROID_PLATFORM)
                    {
                        serverSocket = new ServerSocket(port);
                    }
                    else
                    {
                        serverSocket = sslServerSocketFactory.createServerSocket(port);
                        ((SSLServerSocket) serverSocket).setNeedClientAuth(true);
                    }
                    serverSockets.add(serverSocket);
                    while (!KERNEL_SHUTDOWN)
                    {
                        Socket controlSocket = serverSocket.accept();
                        //added time to timeout after reading from the socket
                        // to be able to check if the kernel has been SHUTDOWN
                        // or not and send SHUTDOWN ack to the control clients.
                        controlSocket.setSoTimeout(CONTROL_CLIENT_READ_TIMEOUT);
                        LocalControlConnection thisConnection = new LocalControlConnection(controlSocket);
                        Thread connectionThread = new Thread(thisConnection);
                        connectionThread.start();
                    }
                }
                catch (SocketException exception)
                {
                    // Do nothing... this is triggered on KERNEL_SHUTDOWN.
                }
                catch (NumberFormatException | IOException exception)
                {
                    logger.log(Level.SEVERE, "control thread not able to start!", exception);
                }
            }
        };
        Thread controlThread = new Thread(controlRunnable, "controlSocket-Thread");
        controlThread.start();
    }

    /**
     * All command strings are passed to this function which subsequently calls
     * the correct method based on the command. Each command is determined by
     * the first token in the string.
     *
     * @param line The command string.
     * @param outputStream The output stream on which to print the result or any
     * output.
     */
    public static void executeCommand(String line, PrintStream outputStream)
    {
        //TODO: change occurred here. Check for correctness.
        String commandPrefix = line.split(" ", 2)[0].toLowerCase();
        switch(commandPrefix)
        {
            case "shutdown":
                // save the current configuration in the config file.
                configCommand("config save " + configFile, NullStream.out);
                for (AbstractReporter reporter : reporters)
                {
                    // Shut down all reporters, flush buffers
                    // and then shut down storages.
                    reporter.shutdown();
                }
                KERNEL_SHUTDOWN = true;
                break;

            case "add":
                addCommand(line, outputStream);
                break;

            case "list":
                listCommand(line, outputStream);
                break;

            case "remove":
                removeCommand(line, outputStream);
                break;

            case "config":
                configCommand(line, outputStream);
                break;

            default:
                outputStream.println(getControlCommands());
        }
    }

    /**
     * The configCommand is used to load or save the current SPADE configuration
     * from/to a file.
     *
     * @param line The configuration command to execute.
     * @param outputStream The output stream on which to print the result or any
     * output.
     */
    public static void configCommand(String line, PrintStream outputStream)
    {
        //TODO: change occurred here. Check for correctness.
        String[] tokens = line.split("\\s+");
        if (tokens.length < 3)
        {
            outputStream.println("Usage:");
            outputStream.println("\t" + CONFIG_STRING);
            return;
        }
        String command = tokens[1].toLowerCase();
        String fileName = tokens[2];

        switch(command)
        {
            // load the saved configuration
            case "load":
                outputStream.print("Loading configuration... ");
                BufferedReader configReader = null;
                try
                {
                    configReader = new BufferedReader(new FileReader(fileName));
                    String configLine;
                    while ((configLine = configReader.readLine()) != null)
                    {
                        addCommand("add " + configLine, outputStream);
                    }
                }
                catch (Exception exception)
                {
                    outputStream.println("error! Unable to open configuration file for reading");
                    logger.log(Level.SEVERE, "error! Unable to open configuration file for reading", exception);
                    return;
                }
                finally
                {
                    try
                    {
                        if(configReader != null)
                        {
                            configReader.close();
                        }
                    }
                    catch(Exception e)
                    {
                        logger.log(Level.WARNING, "Failed to close config reader!", e);
                    }
                }
                outputStream.println("done");
                break;

            // write the current configuration.
            case "save":
                outputStream.print("Saving configuration... ");
                try
                {
                    FileWriter configWriter = new FileWriter(fileName, false);
                    for (int i = 0; i < filters.size() - 1; i++)
                    {
                        String arguments = filters.get(i).arguments;
                        configWriter.write("filter " + filters.get(i).getClass().getName().split("\\.")[2] + " " + i);
                        if (arguments != null)
                        {
                            configWriter.write(" " + arguments);
                        }
                        configWriter.write("\n");
                    }
                    for (AbstractSketch sketch : sketches)
                    {
                        configWriter.write("sketch " + sketch.getClass().getName().split("\\.")[2] + "\n");
                    }
                    for (AbstractStorage storage : storages)
                    {
                        String arguments = storage.arguments;
                        configWriter.write("storage " + storage.getClass().getName().split("\\.")[2]);
                        if (arguments != null)
                        {
                            configWriter.write(" " + arguments);
                        }
                        configWriter.write("\n");
                    }
                    for(AbstractAnalyzer analyzer: analyzers)
                    {
                        configWriter.write("analyzer " + analyzer.getClass().getName().split("\\.")[2] + "\n");
                    }
                    for (AbstractReporter reporter : reporters)
                    {
                        String arguments = reporter.arguments;
                        configWriter.write("reporter " + reporter.getClass().getName().split("\\.")[2]);
                        if (arguments != null)
                        {
                            configWriter.write(" " + arguments);
                        }
                        configWriter.write("\n");
                    }
                    synchronized (transformers)
                    {
                        for(int i = 0; i < transformers.size(); i++)
                        {
                            String arguments = transformers.get(i).arguments;
                            configWriter.write("transformer " + transformers.get(i).getClass().getName().split("\\.")[2] + " " + (i + 1));
                            if(arguments != null)
                            {
                                configWriter.write(" " + arguments);
                            }
                            configWriter.write("\n");
                        }
                    }
                    configWriter.close();
                }
                catch (Exception exception)
                {
                    outputStream.println("error! Unable to open configuration file for writing");
                    logger.log(Level.SEVERE, "error! Unable to open configuration file for writing", exception);
                    return;
                }
                outputStream.println("done");
                break;

            default:
                outputStream.println("Usage:");
                outputStream.println("\t" + CONFIG_STRING);
                logger.log(Level.INFO, "Usage not appropriate");
        }
    }

    /**
     * Method to display control commands to the output stream. The control and
     * query commands are displayed using separate methods since these commands
     * are issued from different clients.
     *
     * @return control commands' string
     */
    public static String getControlCommands()
    {
        StringBuilder string = new StringBuilder();
        string.append("Available commands:\n");
        string.append("\t" + ADD_REPORTER_STORAGE_STRING + "\n");
        string.append("\t" + ADD_ANALYZER_STRING + "\n");
        string.append("\t" + ADD_FILTER_TRANSFORMER_STRING + "\n");
        string.append("\t" + ADD_SKETCH_STRING + "\n");
        string.append("\t" + REMOVE_REPORTER_STORAGE_SKETCH_ANALYZER_STRING + "\n");
        string.append("\t" + REMOVE_FILTER_TRANSFORMER_STRING + "\n");
        string.append("\t" + LIST_STRING + "\n");
        string.append("\t" + CONFIG_STRING + "\n");
        string.append("\t" + EXIT_STRING + "\n");
        string.append("\t" + SHUTDOWN_STRING);
        return string.toString();
    }

    private static SimpleEntry<String, String> getPositionAndArguments(String partOfCommand)
    {
    	try
        {
	    	int indexOfPosition = partOfCommand.startsWith("position") ? 0 : !partOfCommand.contains(" position") ? -1 : partOfCommand.indexOf(" position") + 1;
	    	String positionSubstring = partOfCommand.substring(indexOfPosition);
	    	int indexOfEquals = positionSubstring.indexOf('=');
	    	String positionValue = "";
	    	int i = indexOfEquals + 1;
	    	for(; i < positionSubstring.length(); i++)
	    	{
	    		if(positionValue.isEmpty())
	    		{
	    			if(positionSubstring.charAt(i) != ' ')
	    			{
	    				positionValue += positionSubstring.charAt(i);
	    			}
	    		}
	    		else
                { //not empty
	    			if(positionSubstring.charAt(i) != ' ')
	    			{
	    				positionValue += positionSubstring.charAt(i);
	    			}
	    			else
                    {
	    				break;
	    			}
	    		}
	    	}
	    	i = i < positionSubstring.length() ? i++ : i;
	    	String arguments = partOfCommand.replace(partOfCommand.substring(indexOfPosition, indexOfPosition+i), "");
	    	return new SimpleEntry<String, String>(positionValue, arguments);
		}
		catch(Exception e)
        {
    		logger.log(Level.SEVERE, null, e);
    		return null;
    	}
    }

    /**
     * Method to add modules.
     *
     * @param line The add command issued using the control client.
     * @param outputStream The output stream on which to print the results or
     * any output.
     */
    public static void addCommand(String line, PrintStream outputStream)
    {
        //TODO: change occurred here. Check for correctness.
        String[] tokens = line.split("\\s+", 4);
        if (tokens.length < 2)
        {
            outputStream.println("Usage:");
            outputStream.println("\t" + ADD_REPORTER_STORAGE_STRING);
            outputStream.println("\t" + ADD_FILTER_TRANSFORMER_STRING);
            outputStream.println("\t" + ADD_SKETCH_STRING);
            return;
        }
        String moduleName = tokens[1].toLowerCase();
        String className = tokens[2];
        String arguments = null;
        String position = null;
        SimpleEntry<String, String> positionArgumentsEntry;
        int index;

        switch (moduleName)
        {
            case "reporter":
                if (tokens.length < 3)
                {
                    outputStream.println("Usage:");
                    outputStream.println("\t" + ADD_REPORTER_STORAGE_STRING);
                    return;
                }
                arguments = (tokens.length == 3) ? null : tokens[3];
                logger.log(Level.INFO, "Adding reporter: {0}", className);
                outputStream.print("Adding reporter " + className + "... ");
                AbstractReporter reporter;
                try
                {
                    reporter = (AbstractReporter) Class.forName("spade.reporter." + className).newInstance();
                }
                catch (ClassNotFoundException | InstantiationException | IllegalAccessException ex)
                {
                    outputStream.println("error: Unable to find/load class");
                    logger.log(Level.SEVERE, null, ex);
                    return;
                }
                // Create a new buffer and allocate it to this reporter.
                Buffer buffer = new Buffer();
                reporter.setBuffer(buffer);
                if (reporter.launch(arguments))
                {
                    // The launch() method must return true to indicate a successful launch.
                    // On true, the reporter is added to the reporters set and the buffer
                    // is put into a HashMap keyed by the reporter. This is used by the main
                    // SPADE thread to extract buffer elements.
                    reporter.arguments = arguments;
                    reporters.add(reporter);
                    logger.log(Level.INFO, "Reporter added: {0}", className);
                    outputStream.println("done");
                }
                else
                {
                    outputStream.println("failed");
                }

                break;

            case "analyzer":
                if (tokens.length < 3)
                {
                    outputStream.println("Usage:");
                    outputStream.println("\t" + ADD_ANALYZER_STRING);
                    return;
                }
                logger.log(Level.INFO, "Adding analyzer: {0}", className);
                outputStream.print("Adding analyzer " + className + "... ");
                AbstractAnalyzer analyzer;
                try
                {
                    analyzer = (AbstractAnalyzer) Class.forName("spade.analyzer." + className).newInstance();
                    analyzer.init();
                    analyzers.add(analyzer);
                    logger.log(Level.INFO, "Analyzer added: {0}", className);
                    outputStream.println("done");
                }
                catch(ClassNotFoundException | InstantiationException | IllegalAccessException ex)
                {
                    outputStream.println("error: Unable to find/load class");
                    logger.log(Level.SEVERE, null, ex);
                    return;
                }

                break;

            case "storage":
                if (tokens.length < 3)
                {
                    outputStream.println("Usage:");
                    outputStream.println("\t" + ADD_REPORTER_STORAGE_STRING);
                    return;
                }
                arguments = (tokens.length == 3) ? null : tokens[3];
                logger.log(Level.INFO, "Adding storage: {0}", className);
                outputStream.print("Adding storage " + className + "... ");
                AbstractStorage storage;
                try
                {
                    storage = (AbstractStorage) Class.forName("spade.storage." + className).newInstance();
                }
                catch (ClassNotFoundException | InstantiationException | IllegalAccessException ex)
                {
                    outputStream.println("error: Unable to find/load class");
                    logger.log(Level.SEVERE, null, ex);
                    return;
                }
                if (storage.initialize(arguments))
                {
                    // The initialize() method must return true to indicate
                    // successful startup.
                    storage.arguments = arguments;
                    storage.vertexCount = 0;
                    storage.edgeCount = 0;
                    storages.add(storage);
                    logger.log(Level.INFO, "Storage added: {0}", className);
                    outputStream.println("done");
                }
                else
                {
                    outputStream.println("failed");
                }

                break;

            case "filter":
                if (tokens.length < 4)
                {
                    outputStream.println("Usage:");
                    outputStream.println("\t" + ADD_FILTER_TRANSFORMER_STRING);
                    return;
                }
                positionArgumentsEntry = getPositionAndArguments(tokens[3]);
                if(positionArgumentsEntry != null)
                {
                    position = positionArgumentsEntry.getKey();
                    arguments = positionArgumentsEntry.getValue();
                }
                logger.log(Level.INFO, "Adding filter: {0}", className);
                outputStream.print("Adding filter " + className + "... ");

                try
                {
                    index = Integer.parseInt(position) - 1;
                }
                catch (NumberFormatException numberFormatException)
                {
                    outputStream.println("error: Position must be specified and must be a number");
                    return;
                }

                AbstractFilter filter;
                try
                {
                    filter = (AbstractFilter) Class.forName("spade.filter." + className).newInstance();
                }
                catch (ClassNotFoundException | InstantiationException | IllegalAccessException ex)
                {
                    outputStream.println("error: Unable to find/load class");
                    logger.log(Level.SEVERE, null, ex);
                    return;
                }

                filter.initialize(arguments);
                filter.arguments = arguments;
                // The argument is the index at which the filter is to be inserted.
                if (index >= filters.size())
                {
                    outputStream.println("error: Invalid position");
                    return;
                }
                // Set the next filter of this newly added filter.
                filter.setNextFilter((AbstractFilter) filters.get(index));
                if (index > 0)
                {
                    // If the newly added filter is not the first in the list, then
                    // then configure the previous filter in the list to point to
                    // this
                    // newly added filter as its next.
                    ((AbstractFilter) filters.get(index - 1)).setNextFilter(filter);
                }

                filters.add(index, filter);
                logger.log(Level.INFO, "Filter added: {0}", className);
                outputStream.println("done");

                break;

            case "transformer":
                if (tokens.length < 4)
                {
                    outputStream.println("Usage:");
                    outputStream.println("\t" + ADD_FILTER_TRANSFORMER_STRING);
                    return;
                }
                positionArgumentsEntry = getPositionAndArguments(tokens[3]);
                if(positionArgumentsEntry != null)
                {
                    position = positionArgumentsEntry.getKey();
                    arguments = positionArgumentsEntry.getValue();
                }
                logger.log(Level.INFO, "Adding transformer: {0}", className);
                outputStream.print("Adding transformer " + className + "... ");
                try
                {
                    index = Integer.parseInt(position) - 1;
                } catch (NumberFormatException numberFormatException)
                {
                    outputStream.println("error: Position must be specified and must be a number");
                    return;
                }
                // Get the transformer by classname and create a new instance.
                AbstractTransformer transformer;
                try
                {
                    transformer = (AbstractTransformer) Class.forName("spade.transformer." + className).newInstance();
                }
                catch (ClassNotFoundException | InstantiationException | IllegalAccessException ex)
                {
                    outputStream.println("error: Unable to find/load class");
                    logger.log(Level.SEVERE, null, ex);
                    return;
                }

                if(transformer.initialize(arguments))
                {
                    transformer.arguments = arguments;
                    // The argument is the index at which the transformer is to be
                    // inserted.
                    if (index > transformers.size() || index < 0)
                    {
                        outputStream.println("error: Invalid position");
                        return;
                    }

                    synchronized (transformers)
                    {
                        transformers.add(index, transformer);
                    }

                    logger.log(Level.INFO, "Transformer added: {0}", className);
                    outputStream.println("done");
                }
                else
                {
                    outputStream.println("failed");
                }

                break;

            case "sketch":
                if (tokens.length < 3)
                {
                    outputStream.println("Usage:");
                    outputStream.println("\t" + ADD_SKETCH_STRING);
                    return;
                }
                logger.log(Level.INFO, "Adding sketch: {0}", className);
                outputStream.print("Adding sketch " + className + "... ");

                AbstractSketch sketch;
                try
                {
                    sketch = (AbstractSketch) Class.forName("spade.sketch." + className).newInstance();
                }
                catch (ClassNotFoundException | InstantiationException | IllegalAccessException ex)
                {
                    outputStream.println("error: Unable to find/load class");
                    logger.log(Level.SEVERE, null, ex);
                    return;
                }
                sketches.add(sketch);
                logger.log(Level.INFO, "Sketch added: {0}", className);
                outputStream.println("done");

                break;

            default:
                outputStream.println("Usage:");
                outputStream.println("\t" + ADD_REPORTER_STORAGE_STRING);
                outputStream.println("\t" + ADD_FILTER_TRANSFORMER_STRING);
                outputStream.println("\t" + ADD_SKETCH_STRING);
        }
    }

    /**
     * Method to list modules.
     *
     * @param line The list command issued using the control client.
     * @param outputStream The output stream on which to print the results or
     * any output.
     */
    public static void listCommand(String line, PrintStream outputStream)
    {
        //TODO: change occurred here. Check for correctness.
        String[] tokens = line.split("\\s+");
        String verbose_token = "";
        if (tokens.length < 2)
        {
            outputStream.println("Usage:");
            outputStream.println("\t" + LIST_STRING);
            return;
        }
        String moduleName = tokens[1].toLowerCase();
        int count;
        switch(moduleName)
        {
            case "reporters":
                if (reporters.isEmpty())
                {
                    outputStream.println("No reporters added");
                    return;
                }
                outputStream.println(reporters.size() + " reporter(s) added:");
                count = 1;
                for (AbstractReporter reporter : reporters)
                {
                    String arguments = reporter.arguments;
                    outputStream.print("\t" + count + ". " + reporter.getClass().getName().split("\\.")[2]);
                    if (arguments != null)
                    {
                        outputStream.print(" (" + arguments + ")");
                    }
                    outputStream.println();
                    count++;
                }

                break;

            case "analyzers":
                if(analyzers.isEmpty())
                {
                    outputStream.println("No analyzers added");
                    return;
                }
                outputStream.println(analyzers.size() + " analyzer(s) added: ");
                count = 1;
                for (AbstractAnalyzer analyzer : analyzers)
                {
                    outputStream.println("\t" + count + ". " + analyzer.getClass().getName().split("\\.")[2]);
                    count++;
                }

                break;

            case "storages":
                if (storages.isEmpty())
                {
                    outputStream.println("No storages added");
                    return;
                }
                outputStream.println(storages.size() + " storage(s) added:");
                count = 1;
                for (AbstractStorage storage : storages)
                {
                    String arguments = storage.arguments;
                    outputStream.print("\t" + count + ". " + storage.getClass().getName().split("\\.")[2]);
                    if (arguments != null)
                    {
                        outputStream.print(" (" + arguments + ")");
                    }
                    outputStream.println();
                    count++;
                }

                break;

            case "filter":
                if (filters.size() == 1)
                {
                    // The size of the filters list will always be at least 1 because
                    // of the FinalCommitFilter. The user is not made aware of the
                    // presence of this filter and it is only used for committing
                    // provenance data to the storages. Therefore, there is nothing
                    // to list if the size of the filters list is 1.
                    outputStream.println("No filters added");
                    return;
                }
                outputStream.println((filters.size() - 1) + " filter(s) added:");
                for (int i = 0; i < filters.size() - 1; i++)
                {
                    // Print filter names except for the FinalCommitFilter.
                    String arguments = filters.get(i).arguments;
                    outputStream.print("\t" + (i + 1) + ". " + filters.get(i).getClass().getName().split("\\.")[2]);
                    if (arguments != null)
                    {
                        outputStream.print(" (" + arguments + ")");
                    }
                    outputStream.println();
                }

                break;

            case "transformer":
                if (transformers.size() == 0)
                {
                    outputStream.println("No transformers added");
                    return;
                }
                outputStream.println((transformers.size()) + " transformer(s) added:");
                StringBuffer transformersListString = new StringBuffer();
                synchronized (transformers)
                {
                    for (int i = 0; i < transformers.size(); i++)
                    {
                        // Print transformer names except for the FinalTransformer.
                        transformersListString.append("\t").append((i + 1)).append(". ");
                        transformersListString.append(transformers.get(i).getClass().getName().split("\\.")[2]);
                        if (transformers.get(i).arguments != null)
                        {
                            transformersListString.append(" (");
                            transformersListString.append(transformers.get(i).arguments);
                            transformersListString.append(")");
                        }
                        transformersListString.append("\n");
                    }
                }
                outputStream.print(transformersListString);

                break;

            case "sketch":
                if (sketches.isEmpty())
                {
                    outputStream.println("No sketches added");
                    return;
                }
                outputStream.println(sketches.size() + " sketch(es) added:");
                count = 1;
                for (AbstractSketch sketch : sketches)
                {
                    outputStream.println("\t" + count + ". " + sketch.getClass().getName().split("\\.")[2]);
                    count++;
                }

                break;

            case "all":
                listCommand("list reporters " + verbose_token, outputStream);
                listCommand("list analyzers " + verbose_token, outputStream);
                listCommand("list storages " + verbose_token, outputStream);
                listCommand("list filters " + verbose_token, outputStream);
                listCommand("list transformers " + verbose_token, outputStream);
                listCommand("list sketches " + verbose_token, outputStream);
                break;

            default:
                outputStream.println("Usage:");
                outputStream.println("\t" + LIST_STRING);
        }
    }

    /**
     * Method to remove modules.
     *
     * @param line The remove command issued using the control client.
     * @param outputStream The output stream on which to print the results or
     * any output.
     */
    public static void removeCommand(String line, PrintStream outputStream)
    {
        //TODO: change occurred here. Check for correctness.
        String[] tokens = line.split("\\s+");
        if(tokens.length < 3)
        {
            outputStream.println("Usage:");
            outputStream.println("\t" + REMOVE_REPORTER_STORAGE_SKETCH_ANALYZER_STRING);
            outputStream.println("\t" + REMOVE_FILTER_TRANSFORMER_STRING);
            return;
        }
        String moduleName = tokens[1].toLowerCase();
        String className = tokens[2];
        boolean found;
        int index;
        try
        {
            switch(moduleName)
            {
                case "reporter":
                    found = false;
                    for (Iterator<AbstractReporter> reporterIterator = reporters.iterator(); reporterIterator.hasNext();)
                    {
                        AbstractReporter reporter = reporterIterator.next();
                        // Search for the given reporter in the set of reporters.
                        if (reporter.getClass().getName().equals("spade.reporter." + className))
                        {
                            // Mark the reporter for removal by adding it to the removeReporters set.
                            // This will enable the main SPADE thread to cleanly flush the reporter
                            // buffer and remove it.
                            reporter.shutdown();
                            removeReporters.add(reporter);
                            found = true;
                            logger.log(Level.INFO, "Shutting down reporter: {0}", className);
                            outputStream.print("Shutting down reporter " + className + "... ");
                            while (removeReporters.contains(reporter))
                            {
                                // Wait for other thread to safely remove reporter
                                Thread.sleep(REMOVE_WAIT_DELAY);
                            }
                            reporterIterator.remove();
                            logger.log(Level.INFO, "Reporter shut down: {0}", className);
                            outputStream.println("done");
                            break;
                        }
                    }
                    if (!found)
                    {
                        logger.log(Level.WARNING, "Reporter not found (for shutting down): {0}", className);
                        outputStream.println("Reporter " + className + " not found");
                    }

                    break;

                case "analyzer":
                    found = false;
                    for (Iterator<AbstractAnalyzer> analyzerIterator = analyzers.iterator(); analyzerIterator.hasNext();)
                    {
                        AbstractAnalyzer analyzer = analyzerIterator.next();
                        if (analyzer.getClass().getName().equals("spade.analyzer." + className))
                        {
                            // Mark the analyzer for removal by adding it to the removeanalyzer set.
                            // This will enable the main SPADE thread to safely commit any transactions
                            // and then remove the analyzer.
                            removeAnalyzers.add(analyzer);
                            found = true;
                            logger.log(Level.INFO, "Shutting down analyzer: {0}", className);
                            outputStream.print("Shutting down analyzer " + className + "... ");
                            while (removeAnalyzers.contains(analyzer))
                            {
                                // Wait for other thread to safely remove analyzer
                                Thread.sleep(REMOVE_WAIT_DELAY);
                            }
                            analyzerIterator.remove();
                            logger.log(Level.INFO, "Analyzer shut down: {0})", className);
                            break;
                        }
                    }
                    if (!found)
                    {
                        logger.log(Level.WARNING, "Analyzer not found (for shutting down): {0}", className);
                        outputStream.println("Analyzer " + className + " not found");
                    }

                    break;

                case "storage":
                    found = false;
                    for (Iterator<AbstractStorage> storageIterator = storages.iterator(); storageIterator.hasNext();)
                    {
                        AbstractStorage storage = storageIterator.next();
                        // Search for the given storage in the storages set.
                        if (storage.getClass().getName().equals("spade.storage." + className))
                        {
                            // Mark the storage for removal by adding it to the removeStorages set.
                            // This will enable the main SPADE thread to safely commit any transactions
                            // and then remove the storage.
                            long vertexCount = storage.vertexCount;
                            long edgeCount = storage.edgeCount;
                            removeStorages.add(storage);
                            found = true;
                            logger.log(Level.INFO, "Shutting down storage: {0}", className);
                            outputStream.print("Shutting down storage " + className + "... ");

                            while (removeStorages.contains(storage))
                            {
                                // Wait for other thread to safely remove storage
                                Thread.sleep(REMOVE_WAIT_DELAY);
                            }
                            storageIterator.remove();
                            logger.log(Level.INFO, "Storage shut down: {0} ({1} vertices and {2} edges were added)",
                                                                        new Object[]{className, vertexCount, edgeCount});
                            outputStream.println("done (" + vertexCount + " vertices and " + edgeCount + " edges added)");
                            break;
                        }
                    }
                    if (!found)
                    {
                        logger.log(Level.WARNING, "Storage not found (for shutting down): {0}", className);
                        outputStream.println("Storage " + className + " not found");
                    }

                    break;

                case "filter":
                    // Filter removal is done by the index number beginning from 1.
                    index = Integer.parseInt(tokens[2]);
                    if ((index <= 0) || (index >= filters.size()))
                    {
                        logger.log(Level.WARNING, "Error: Unable to remove filter - bad index");
                        outputStream.println("Error: Unable to remove filter - bad index");
                        return;
                    }

                    className = filters.get(index - 1).getClass().getName();
                    logger.log(Level.INFO, "Removing filter {0}", className.split("\\.")[2]);
                    outputStream.print("Removing filter " + className.split("\\.")[2] + "... ");
                    filters.get(index - 1).shutdown();
                    if (index > 1)
                    {
                        // Update the internal links between filters by calling the
                        // setNextFilter
                        // method on the filter just before the one being removed.
                        // The (index-1)
                        // check is used because this method is not to be called on
                        // the first filter.
                        ((AbstractFilter) filters.get(index - 2)).setNextFilter((AbstractFilter) filters.get(index));
                    }
                    filters.remove(index - 1);
                    logger.log(Level.INFO, "Filter Removed: {0}", className.split("\\.")[2]);
                    outputStream.println("done");

                    break;

                case "transformer":
                    try
                    {
                        index = Integer.parseInt(tokens[2]) - 1;
                    }
                    catch(Exception e)
                    {
                        logger.log(Level.WARNING, "Error: Invalid index (Not a number)");
                        outputStream.println("Error: Invalid index (Not a number)");
                        return;
                    }
                    if ((index < 0) || (index >= transformers.size()))
                    {
                        logger.log(Level.WARNING, "Error: Unable to remove transformer - bad index");
                        outputStream.println("Error: Unable to remove transformer - bad index");
                        return;
                    }

                    className = transformers.get(index).getClass().getName().split("\\.")[2];
                    logger.log(Level.INFO, "Removing transformer {0}", className);
                    outputStream.print("Removing transformer " + className + "... ");
                    AbstractTransformer removed = null;
                    synchronized (transformers)
                    {
                        removed = transformers.remove(index);
                    }
                    if(removed != null)
                    {
                        removed.shutdown();
                    }
                    logger.log(Level.INFO, "Transformer removed: {0}", className);
                    outputStream.println("done");

                    break;

                case "sketch":
                    found = false;
                    for (Iterator<AbstractSketch> sketchIterator = sketches.iterator(); sketchIterator.hasNext();)
                    {
                        AbstractSketch sketch = sketchIterator.next();
                        // Search for the given sketch in the sketches set.
                        if (sketch.getClass().getName().equals("spade.sketch." + className))
                        {
                            found = true;
                            logger.log(Level.INFO, "Removing sketch {0}", className);
                            outputStream.print("Removing sketch: " + className + "... ");
                            sketchIterator.remove();
                            logger.log(Level.INFO, "Sketch removed: {0}", className);
                            outputStream.println("done");
                            break;
                        }
                    }
                    if (!found)
                    {
                        logger.log(Level.WARNING, "Sketch not found: {0}", className);
                        outputStream.println("Sketch " + className + " not found");
                    }

                    break;

                default:
                    outputStream.println("Usage:");
                    outputStream.println("\t" + REMOVE_REPORTER_STORAGE_SKETCH_ANALYZER_STRING);
                    outputStream.println("\t" + REMOVE_FILTER_TRANSFORMER_STRING);
            }
        }
        catch (InterruptedException | NumberFormatException removeCommandException)
        {
            outputStream.println("Usage:");
            outputStream.println("\t" + REMOVE_REPORTER_STORAGE_SKETCH_ANALYZER_STRING);
            outputStream.println("\t" + REMOVE_FILTER_TRANSFORMER_STRING);
            logger.log(Level.WARNING, null, removeCommandException);
        }
    }

    /**
     * Method to shut down SPADE completely.
     */
    public static void shutdown()
    {
        logger.log(Level.INFO, "Shutting down SPADE....");
        // Shut down filters.
        for (int i = 0; i < filters.size() - 1; i++)
        {
            filters.get(i).shutdown();
        }
        // Shut down storages.
        for (AbstractStorage storage : storages)
        {
            storage.shutdown();
        }

        //before closing the sockets notify that the KERNEL_SHUTDOWN is complete
        synchronized (shutdownCompleteLock)
        {
        	shutdownComplete = true;
			shutdownCompleteLock.notifyAll();
		}

        //wait for the KERNEL_SHUTDOWN acknowledgement to be sent
        synchronized (allShutdownsAcknowledgedLock)
        {
        	while(remainingShutdownAcks != 0)
            {
				try
                {
					allShutdownsAcknowledgedLock.wait();
				}
				catch(Exception e)
                {
					logger.log(Level.SEVERE, null, e);
				}
        	}
		}

        // Shut down server sockets.
        for (ServerSocket socket : serverSockets)
        {
            try
            {
                socket.close();
            }
            catch (IOException ex)
            {
                logger.log(Level.SEVERE, null, ex);
            }
        }
        logger.log(Level.INFO, "SPADE turned off.");
        System.exit(0);
    }
}


final class NullStream
{

    public final static PrintStream out = new PrintStream(new OutputStream()
    {
        @Override
        public void close() {}

        @Override
        public void flush() {}

        @Override
        public void write(byte[] b) {}

        @Override
        public void write(byte[] b, int off, int len) {}

        @Override
        public void write(int b) {}
    });
}

class LocalControlConnection implements Runnable
{

	private final Logger logger = Logger.getLogger(LocalControlConnection.class.getName());
    private Socket controlSocket;

    LocalControlConnection(Socket socket)
    {
        controlSocket = socket;
    }

    @Override
    public void run()
    {
        try
        {
        	synchronized (Kernel.controlConnectionsLock)
            {
        		if(Kernel.remainingShutdownAcks == -1)
        		{
            		Kernel.remainingShutdownAcks = 1;
            	}
            	else
                {
            		Kernel.remainingShutdownAcks++;
            	}
			}
            OutputStream outStream = controlSocket.getOutputStream();
            InputStream inStream = controlSocket.getInputStream();

            BufferedReader controlInputStream = new BufferedReader(new InputStreamReader(inStream));
            PrintStream controlOutputStream = new PrintStream(outStream);
            try
            {
                while (!Kernel.KERNEL_SHUTDOWN)
                {
                    // Commands read from the input stream and executed.
                	try
                    {
	                    String line = controlInputStream.readLine();
	                    if (line == null || line.equalsIgnoreCase("exit"))
	                    {
	                    	controlOutputStream.println("ACK[exit]");
	                    	synchronized (Kernel.controlConnectionsLock)
                            {
	                    		Kernel.remainingShutdownAcks--;
	                    	}
	                        break;
	                    }

	                    Kernel.executeCommand(line, controlOutputStream);

	                    if(line.equals("KERNEL_SHUTDOWN"))
	                    {
	                    	break;
	                    }
	                    else
                        {
	                    	// An empty line is printed to let the client know that the
	                        // command output is complete.
	                    	controlOutputStream.println("");
	                    }
                	}
                	catch(SocketTimeoutException exception)
                    {
                		//logger.log(Level.SEVERE, null, exception);
                		//normal exception. no need to log it. timeout added to be able to
                        // SHUTDOWN when KERNEL_SHUTDOWN set to true by another client
                	}
                }
                //to differentiate between exit and KERNEL_SHUTDOWN
                if(Kernel.KERNEL_SHUTDOWN)
                {
                	//wait for Kernel to SHUTDOWN everything before replying with the
                    // SHUTDOWN ack.
                    // Will be sent to all control clients irrespective of who called KERNEL_SHUTDOWN
                	synchronized (Kernel.shutdownCompleteLock)
                    {
                		while(!Kernel.shutdownComplete)
                        {
							try
                            {
								Kernel.shutdownCompleteLock.wait();
							}
							catch(Exception e)
                            {
								logger.log(Level.SEVERE, null, e);
							}
                		}
					}
                    //doing this in a try catch to make sure the lock code (below) is always executed.
                	try
                    {
                		controlOutputStream.println("ACK[KERNEL_SHUTDOWN]");
                		controlOutputStream.flush();
                	}
                	catch(Exception e)
                    {
                		logger.log(Level.SEVERE, null, e);
                	}

                	synchronized (Kernel.allShutdownsAcknowledgedLock)
                    {
                		synchronized (Kernel.controlConnectionsLock)
                        {
                			Kernel.remainingShutdownAcks--;
                            //all acks sent. safe to let the kernel proceed
                			if(Kernel.remainingShutdownAcks == 0)
	                		{
	                			Kernel.allShutdownsAcknowledgedLock.notifyAll();
	                		}
                		}
					}
                }
            }
            catch (IOException e)
            {
            	logger.log(Level.SEVERE, null, e);
                // Connection broken?
            }
            finally
            {
                controlInputStream.close();
                controlOutputStream.close();

                inStream.close();
                outStream.close();
                if (controlSocket.isConnected())
                {
                    controlSocket.close();
                }
            }
        }
        catch (Exception ex)
        {
            logger.log(Level.SEVERE, null, ex);
        }
    }
}

