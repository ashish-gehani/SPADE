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
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.SocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import spade.filter.FinalCommitFilter;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.utility.HelperFunctions;
import spade.utility.HostInfo;
import spade.utility.LogManager;
import spade.utility.Result;

/**
 * The SPADE kernel containing the control client and
 * managing all the central activities.
 *
 * @author Dawood Tariq and Raza Ahmad
 */

public class Kernel
{
    /**
     * Public name for this host.
     */
    private static String HOST_NAME = null;
    /**
     * Set of reporters active on the local SPADE instance.
     */
    private static Set<AbstractReporter> reporters;
    /**
     * Set of analyzers active on the local SPADE instance.
     */
    public static Set<AbstractAnalyzer> analyzers;
    /**
     * Set of storages active on the local SPADE instance.
     */
    public static Set<AbstractStorage>storages;
    
	private static final Object defaultQueryStorageClassLock = new Object();
	private static Class<? extends AbstractStorage> defaultQueryStorageClass = null;
	private static final Object defaultQueryStorageLock = new Object();
	private static AbstractStorage defaultQueryStorage = null;

	public static AbstractStorage getDefaultQueryStorage(){
		synchronized(defaultQueryStorageLock){
			return defaultQueryStorage;
		}
	}

	public static void setDefaultQueryStorage(final AbstractStorage storage){
		synchronized(defaultQueryStorageLock){
			defaultQueryStorage = storage;
		}
	}

	public static Class<? extends AbstractStorage> getDefaultQueryStorageClass(){
		synchronized(defaultQueryStorageClassLock){
			return defaultQueryStorageClass;
		}
	}

	public static void setDefaultQueryStorageClass(final Class<? extends AbstractStorage> clazz){
		synchronized(defaultQueryStorageClassLock){
			defaultQueryStorageClass = clazz;
		}
	}
    
	public static AbstractStorage getStorage(String storageName){
		for(AbstractStorage storage : storages){
			// Search for the given storage in the storages set.
			if(storage.getClass().getSimpleName().equalsIgnoreCase(storageName)){
				return storage;
			}
		}

		return null;
	}
    
    public static boolean isStoragePresent(AbstractStorage storage){
    	for(AbstractStorage existingStorage : storages){
            // Search for the given storage in the storages set by instance.
            if(existingStorage == storage){
                return true;
            }
        }
        return false;
    }
    
	public static AbstractReporter findReporter(String reporterName){
		if(reporters != null){
			for(AbstractReporter reporter : reporters){
				// Search for the given storage in the storages set.
				if(reporter != null){
					if(reporter.getClass().getSimpleName().equalsIgnoreCase(reporterName)){
						return reporter;
					}
				}
			}
		}
		return null;
	}
    /**
     * Set of filters active on the local SPADE instance.
     */
    private static List<AbstractFilter> filters;
    /**
     * Set of transformers active on the local SPADE instance.
     */
    public static List<AbstractTransformer> transformers;
    /**
     * A map used to cache the remote sketches.
     */
    public static Map<String, AbstractSketch> remoteSketches;
    /**
     * Set of sketches active on the local SPADE instance.
     */
    public static Set<AbstractSketch> sketches;

    public static boolean isShutdown()
    {
        return shutdown;
    }

    public static void setShutdown()
    {
        Kernel.shutdown = true;
    }

    /**
     * Used to initiate shutdown. It is used by various threads to
     * determine whether to continue running.
     */
    private static volatile boolean shutdown;
    /**
     * Used to indicate whether the transactions need to be flushed by
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
    private static Logger logger;
    private static boolean ANDROID_PLATFORM = false;

    /**
     * Strings for control client
     */
    private static final String ADD_REPORTER_STORAGE_STRING = "add reporter|storage <class name> <initialization arguments>";
    private static final String ADD_FILTER_TRANSFORMER_STRING = "add filter|transformer <class name> position=<number> <initialization arguments>";
    private static final String ADD_ANALYZER_SKETCH_STRING = "add analyzer|sketch <class name>";
    private static final String SET_QUERY_STORAGE_STRING = "set storage <class name>";
    private static final String REMOVE_REPORTER_STORAGE_SKETCH_ANALYZER_STRING = "remove reporter|analyzer|storage|sketch <class name>";
    private static final String REMOVE_FILTER_TRANSFORMER_STRING = "remove filter|transformer <position number>";
    private static final String LIST_STRING = "list reporters|storages|analyzers|filters|sketches|transformers|all";
    private static final String CONFIG_STRING = "config load|save <filename>";
    public static final String EXIT_STRING = "exit";

    /**
     * Members for creating secure sockets
     */
    private static KeyStore clientKeyStorePublic;
    private static KeyStore clientKeyStorePrivate;
    private static KeyStore serverKeyStorePublic;
    private static KeyStore serverKeyStorePrivate;
    public static SSLSocketFactory sslSocketFactory;
    public static SSLServerSocketFactory sslServerSocketFactory;

    //time to timeout after when reading from the control client socket
    private final static int CONTROL_CLIENT_READ_TIMEOUT = 1000;

    // reads name of the host
	public synchronized static String getHostName(){
		if(HOST_NAME == null){
			try{
				HOST_NAME = HostInfo.getHostName();
			}catch(Exception e){
				logger.log(Level.WARNING, "Failed to retrieve host name. Using empty string!", e);
				HOST_NAME = "";
			}
			final String spadeHostFilePath = Settings.getSPADEHostFilePath();
			try(PrintWriter out = new PrintWriter(spadeHostFilePath)){
				out.println(HOST_NAME);
			}catch(Exception e){
				logger.log(Level.WARNING, "Failed to write host name to file: " + spadeHostFilePath, e);
			}
			logger.log(Level.INFO, "SPADE host name: '" + HOST_NAME + "'");
		}
		return HOST_NAME;
	}
    
    public static SocketFactory getClientSocketFactory(){
    	return sslSocketFactory;
    }

    /**
     * The main initialization function.
     *
     * @param args
     */
    public static void main(String args[]) {
        if (args.length == 1 && args[0].equals("android")) {
            ANDROID_PLATFORM = true;
        }

		try{
			Settings.initializeLogging();
		}catch(Exception e){
			Logger.getLogger(Kernel.class.getName()).log(Level.WARNING, "Failed to initialize SPADE logging. Falling back to JAVA default.", e);
		}finally{
			logger = Logger.getLogger(Kernel.class.getName());
		}

        // Set up context for secure connections
        if (!ANDROID_PLATFORM) {
            try {
                setupKeyStores();
                setupClientSSLContext();
                setupServerSSLContext();
            } catch (Exception exception) {
                logger.log(Level.SEVERE, null, exception);
            }
        }

        // Initialize host name
        getHostName();

        registerShutdownThread();

        initializeObjects();

        registerMainThread();

        registerControlThread();

        // Load the SPADE configuration from the default config file.
        final String controlClientConfigFilePath = Settings.getDefaultConfigFilePath(spade.client.Control.class);
        configCommand("config load " + controlClientConfigFilePath, NullStream.out);
    }

    private static void setupKeyStores() throws Exception
    {
        String SERVER_PUBLIC_PATH = Settings.getServerPublicKeystorePath();
        String SERVER_PRIVATE_PATH = Settings.getServerPrivateKeystorePath();
        String CLIENT_PUBLIC_PATH = Settings.getClientPublicKeystorePath();
        String CLIENT_PRIVATE_PATH = Settings.getClientPrivateKeystorePath();

        serverKeyStorePublic = KeyStore.getInstance("JKS");
        serverKeyStorePublic.load(new FileInputStream(SERVER_PUBLIC_PATH), Settings.getPasswordPublicKeystoreAsCharArray());
        serverKeyStorePrivate = KeyStore.getInstance("JKS");
        serverKeyStorePrivate.load(new FileInputStream(SERVER_PRIVATE_PATH), Settings.getPasswordPrivateKeystoreAsCharArray());
        clientKeyStorePublic = KeyStore.getInstance("JKS");
        clientKeyStorePublic.load(new FileInputStream(CLIENT_PUBLIC_PATH), Settings.getPasswordPublicKeystoreAsCharArray());
        clientKeyStorePrivate = KeyStore.getInstance("JKS");
        clientKeyStorePrivate.load(new FileInputStream(CLIENT_PRIVATE_PATH), Settings.getPasswordPrivateKeystoreAsCharArray());
    }

    private static void setupClientSSLContext() throws Exception
    {
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextInt();

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(serverKeyStorePublic);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(clientKeyStorePrivate, Settings.getPasswordPrivateKeystoreAsCharArray());

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
        kmf.init(serverKeyStorePrivate, Settings.getPasswordPrivateKeystoreAsCharArray());

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), secureRandom);
        sslServerSocketFactory = sslContext.getServerSocketFactory();
    }
    
    public static ServerSocket createServerSocket(int listeningPort) throws Exception{
        ServerSocket serverSocket = sslServerSocketFactory.createServerSocket(listeningPort);
        ((SSLServerSocket) serverSocket).setNeedClientAuth(true);
        addServerSocket(serverSocket);
        return serverSocket;
    }

    public static PrivateKey getServerPrivateKey(String alias)
    {
        try
        {
            KeyStore.ProtectionParameter protectionParameter =
                    new KeyStore.PasswordProtection(Settings.getPasswordPrivateKeystoreAsCharArray());
            KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry) serverKeyStorePrivate.getEntry(alias, protectionParameter);
            return privateKeyEntry.getPrivateKey();
        } catch(Exception ex)
        {
            logger.log(Level.SEVERE, "Error getting server private key", ex);
            return null;
        }
    }

    public static PublicKey getServerPublicKey(String alias)
    {
        try
        {
            Certificate certificate = serverKeyStorePublic.getCertificate(alias);
            return certificate.getPublicKey();
        } catch(Exception ex)
        {
            logger.log(Level.SEVERE, "Error getting server public key", ex);
            return null;
        }
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

        shutdown = false;
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
//                        if (flushTransactions)
//                        {
//                            // Flushing of transactions is also handled by this thread to ensure that
//                            // there are no errors/problems when using storages that are sensitive to
//                            // thread-context for their transactions.
//                            // For example, this is true for the embedded neo4j graph database.
//                            for (AbstractStorage currentStorage : storages)
//                            {
//                                currentStorage.flushTransactions(false);
//                            }
//                            flushTransactions = false;
//                        }

                        if (!removeStorages.isEmpty())
                        {
                            // Check if a storage is marked for removal.
                            // If it is, shut it down and remove it from the list.
                            Iterator<AbstractStorage> iterator = removeStorages.iterator();
                            while(iterator.hasNext())
                            {
                                AbstractStorage currentStorage = iterator.next();
                                AbstractScreen.shutdownScreens(currentStorage.getScreens());
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
        // Register a shutdown hook to terminate gracefully
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                if (!shutdown) {
                    shutdown = true;
                    shutdown();
                }
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
                    int port = Settings.getLocalControlPort();
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
                    while (!shutdown)
                    {
                        Socket controlSocket = serverSocket.accept();
                        LocalControlConnection thisConnection = new LocalControlConnection(controlSocket);
                        Thread connectionThread = new Thread(thisConnection);
                        connectionThread.start();
                    }
                }
                catch (SocketException exception)
                {
                    // Do nothing... this is triggered on shutdown.
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
        String commandPrefix = line.split("\\s+", 2)[0].toLowerCase();
        switch(commandPrefix)
        {
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
                
            case "set":
            	String tokens[] = line.split("\\s+", 3);
            	if(tokens.length > 2){ // at least 3
            		String storageToken = tokens[1].toLowerCase();
            		if(storageToken.equalsIgnoreCase("storage")){
            			setQueryStorageCommand(line, outputStream);
                    	break;
            		}
                	// Fall down to default. Invalid query because the second word must be storage.
            	}
            	// Fall down to default because 'set' must be followed by an extra word. If it doesn't then it in an invalid query.
            default:
                outputStream.print(getControlCommands()); 
                // Don't do println because new line already added to the control commands list
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
                        executeCommand(configLine, outputStream);
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
                    // Write out the default query storage first
                    if(Kernel.getDefaultQueryStorageClass() != null){
                    	configWriter.write("set storage " + Kernel.getDefaultQueryStorageClass().getSimpleName() + "\n");
                    }
                    for (int filter = 0; filter < filters.size() - 1; filter++)
                    {
                        String arguments = filters.get(filter).arguments;
                        configWriter.write("add filter " + filters.get(filter).getClass().getName().split("\\.")[2] + " position=" + (filter+1));
                        if (arguments != null)
                        {
                            configWriter.write(" " + arguments.trim());
                        }
                        configWriter.write("\n");
                    }
                    for (AbstractSketch sketch : sketches)
                    {
                        configWriter.write("add sketch " + sketch.getClass().getName().split("\\.")[2] + "\n");
                    }
                    for (AbstractStorage storage : storages)
                    {
                        String arguments = storage.arguments;
                        configWriter.write("add storage " + storage.getClass().getName().split("\\.")[2]);
                        if (arguments != null)
                        {
                            configWriter.write(" " + arguments.trim());
                        }
                        configWriter.write("\n");
                    }
                    for(AbstractAnalyzer analyzer: analyzers)
                    {
                        configWriter.write("add analyzer " + analyzer.getClass().getName().split("\\.")[2] + "\n");
                    }
                    for (AbstractReporter reporter : reporters)
                    {
                        String arguments = reporter.arguments;
                        configWriter.write("add reporter " + reporter.getClass().getName().split("\\.")[2]);
                        if (arguments != null)
                        {
                            configWriter.write(" " + arguments.trim());
                        }
                        configWriter.write("\n");
                    }
                    synchronized (transformers)
                    {
                        for(int transformer = 0; transformer < transformers.size(); transformer++)
                        {
                            String arguments = transformers.get(transformer).arguments;
                            configWriter.write("add transformer " + transformers.get(transformer).getClass().getName().split("\\.")[2] + " position=" + (transformer + 1));
                            if(arguments != null)
                            {
                                configWriter.write(" " + arguments.trim());
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
            
            case "get":
            case "set":
            case "unset":{
            	configGetSetCommand(outputStream, line);
            }
            break;
                
            default:
            	printConfigCommandUsage(outputStream);
                break;
        }
    }
    
    private static enum ConfigPropertyOperation{ GET, SET, UNSET };
    private final static void configGetSetCommand(final PrintStream outputStream, final String line){
    	final String tokens[] = line.split("\\s+");
    	final int tokensLength = tokens.length;
    	if(tokensLength > 0){
    		if(tokens[0].equalsIgnoreCase("config")){
    			if(tokensLength > 1){
    				Result<ConfigPropertyOperation> propertyOperationResult = 
    						HelperFunctions.parseEnumValue(ConfigPropertyOperation.class, tokens[1], true);
    				if(!propertyOperationResult.error){
    					if(tokensLength > 2){
    						final String moduleType = tokens[2];
    						if(moduleType.equalsIgnoreCase("reporter")){
    							if(tokensLength > 3){
    								final String reporterName = tokens[3];
    								final AbstractReporter reporter = findReporter(reporterName);
    								if(reporter != null){
    									if(tokensLength > 4){
    										final String propertyName = tokens[4];
    										if(propertyOperationResult.result.equals(ConfigPropertyOperation.GET)){
    											try{
    												final Object value = reporter.getProperty(propertyName);
    												final String msg = 
    														(value == null)
    														? ("'" + propertyName + "' is NULL")
    														: ("'" + propertyName + "' = '" + value + "'");
    												outputStream.println(msg);
    							                    logger.log(Level.INFO, "[GET] '" + reporterName + "' " + moduleType + ". " + msg);
    							                    return; // return here!!!
    											}catch(final Exception e){
    												logger.log(Level.WARNING, 
    														"Failed to get '" + reporterName + "' "+ moduleType + " property '"+propertyName+"'", e);
    											}
											}else if(propertyOperationResult.result.equals(ConfigPropertyOperation.SET)){
												if(tokensLength > 5){
													final String[] limitedTokens = line.split("\\s+", 6);
													final String propertyValue = limitedTokens[5];
													try{
														reporter.setProperty(propertyName, propertyValue);
														final String msg = "'" + propertyName + "' = '" + propertyValue + "'";
	    												outputStream.println("OK");
	    							                    logger.log(Level.INFO, "[SET] '" + reporterName + "' " + moduleType + ". " + msg);
	    							                    return; // return here!!!
													}catch(Exception e){
														logger.log(Level.WARNING, 
	    														"Failed to set '" + reporterName + "' "+ moduleType + " property '"+propertyName+"' value '"+propertyValue+"'", e);
													}
												}
											}else if(propertyOperationResult.result.equals(ConfigPropertyOperation.UNSET)){
												try{
													reporter.unsetProperty(propertyName);
    												final String msg = "'" + propertyName + "' = '";
    												outputStream.println("OK");
    							                    logger.log(Level.INFO, "[UNSET] '" + reporterName + "' " + moduleType + ". " + msg);
    							                    return; // return here!!!
    											}catch(final Exception e){
    												logger.log(Level.WARNING, 
    														"Failed to unset '" + reporterName + "' "+ moduleType + " property '"+propertyName+"'", e);
    											}
											}
    									}
    								}
    							}
    						}
    					}
    				}
    			}
    		}
    	}
    	// here only case of unknown command
    	printConfigCommandUsage(outputStream);
    }

    private final static void printConfigCommandUsage(final PrintStream outputStream){
    	outputStream.println("Usage:");
        outputStream.println("\t" + CONFIG_STRING);
        logger.log(Level.INFO, "Usage not appropriate");
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
        string.append("\t" + ADD_ANALYZER_SKETCH_STRING + "\n");
        string.append("\t" + ADD_FILTER_TRANSFORMER_STRING + "\n");
        string.append("\t" + SET_QUERY_STORAGE_STRING + "\n");
        string.append("\t" + REMOVE_REPORTER_STORAGE_SKETCH_ANALYZER_STRING + "\n");
        string.append("\t" + REMOVE_FILTER_TRANSFORMER_STRING + "\n");
        string.append("\t" + LIST_STRING + "\n");
        string.append("\t" + CONFIG_STRING + "\n");
        string.append("\t" + EXIT_STRING + "\n");
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
            i = i < positionSubstring.length() ? ++i : i;
            String arguments = partOfCommand.replace(partOfCommand.substring(indexOfPosition, indexOfPosition+i), "");
            return new SimpleEntry<>(positionValue, arguments);
        }
        catch(Exception e)
        {
            logger.log(Level.SEVERE, null, e);
            return null;
        }
    }

	private static void addReporterCommand(final PrintStream outputStream, final String classNameString,
			final String argumentsString){
		if(outputStream == null){
			logger.log(Level.SEVERE, "NULL output stream for client");
			return;
		}
		if(HelperFunctions.isNullOrEmpty(classNameString)){
			logger.log(Level.SEVERE, "NULL/Empty reporter class name: '" + classNameString + "'");
			return;
		}
		final String arguments = argumentsString == null ? "" : argumentsString;

		////

		logger.log(Level.INFO, "Adding reporter: {0}", classNameString);
		outputStream.print("Adding reporter " + classNameString + "... ");

		final Class<AbstractReporter> classObject;
		try{
			classObject = (Class<AbstractReporter>)Class.forName("spade.reporter." + classNameString);
		}catch(Throwable t){
			outputStream.println("error: Unable to find/load class");
			logger.log(Level.SEVERE, "error: Unable to find/load class: " + classNameString, t);
			return;
		}

		final Constructor<AbstractReporter> constructor;
		try{
			constructor = classObject.getDeclaredConstructor();
		}catch(Throwable t){
			outputStream.println("error: Unable to reflect on class. Illegal implementation");
			logger.log(Level.SEVERE, "error: Unable to reflect on class. Illegal implementation for '" + classObject
					+ "'. Must have an empty public constructor", t);
			return;
		}

		////
		
		final Map<String, SimpleEntry<String, String>> configMapWithSources;
		try{
			configMapWithSources = 
					HelperFunctions.parseKeyValuePairsFromAndGetSources(
							arguments, 
							Settings.getDefaultConfigFilePath(classObject), 
							Settings.getDefaultConfigFilePath(AbstractReporter.class)
							);
		}catch(Throwable t){
			outputStream.println("error: Failed to construct reporter arguments. " + t.getMessage());
			logger.log(Level.SEVERE, "error: Failed to construct reporter arguments", t);
			return;
		}

		final Buffer buffer;
		final String bufferConfigKey = BlockingBuffer.keyWorkableFreeMemoryPercentageForBuffer;
		final SimpleEntry<String, String> bufferConfigValueEntry = configMapWithSources.get(bufferConfigKey);
		
		if(bufferConfigValueEntry == null){
			buffer = new Buffer();
			logger.log(Level.INFO, "Default (unlimited) buffer used for reporter '"+classNameString+"'");
		}else{
			final String bufferValue = bufferConfigValueEntry.getKey();
			final String bufferValueSource = bufferConfigValueEntry.getValue();
			try{
				buffer = new BlockingBuffer(bufferValue, classObject);
				logger.log(Level.INFO, "Memory usage limited buffer used for reporter '" + classNameString + "' with "
						+ "'" + bufferConfigKey + "'='" + bufferValue + "' from '" + bufferValueSource + "'");
			}catch(Throwable t){
				outputStream.println("error: Unable to create buffer using '" + bufferConfigKey + "' = "
						+ "'" + bufferValue + "' from '" + bufferValueSource + "'. " + t.getMessage());
				logger.log(Level.SEVERE, "error: Unable to create buffer using '" + bufferConfigKey + "' = "
						+ "'" + bufferValue + "' from '" + bufferValueSource + "'. " + classObject, t);
				return;
			}
		}

		final AbstractReporter reporter;
		try{
			reporter = constructor.newInstance();
		}catch(Throwable t){
			outputStream.println("error: Unable to instantiate class");
			logger.log(Level.SEVERE, "error: Unable to instantiate class using the empty constructor: " + classObject, t);
			return;
		}

		reporter.setBuffer(buffer);

		final boolean launchResult;

		try{
			launchResult = reporter.launch(arguments);
		}catch(Throwable t){
			logger.log(Level.SEVERE, "Unable to launch reporter", t);
			outputStream.println("failed. " + t.getMessage());
			return;
		}

		if(launchResult){
			// The launch() method must return true to indicate a successful launch.
			// On true, the reporter is added to the reporters set and the buffer
			// is put into a HashMap keyed by the reporter. This is used by the main
			// SPADE thread to extract buffer elements.
			reporter.arguments = arguments;
			reporters.add(reporter);
			logger.log(Level.INFO, "Reporter added: {0}", classNameString + " " + arguments);
			outputStream.println("done");
			return;
		}else{
			logger.log(Level.SEVERE, "Unable to launch reporter");
			outputStream.println("failed");
			return;
		}
	}

	private static void addStorageCommand(final PrintStream outputStream, final String classNameString,
			final String argumentsString){
		if(outputStream == null){
			logger.log(Level.SEVERE, "NULL output stream for client");
			return;
		}
		if(HelperFunctions.isNullOrEmpty(classNameString)){
			logger.log(Level.SEVERE, "NULL/Empty storage class name: '" + classNameString + "'");
			return;
		}
		final String arguments = argumentsString == null ? "" : argumentsString;

		////

		logger.log(Level.INFO, "Adding storage: {0}", classNameString);
		outputStream.print("Adding storage " + classNameString + "... ");

		final Class<AbstractStorage> classObject;
		try{
			classObject = (Class<AbstractStorage>)Class.forName("spade.storage." + classNameString);
		}catch(Throwable t){
			outputStream.println("error: Unable to find/load class");
			logger.log(Level.SEVERE, "error: Unable to find/load class: " + classNameString, t);
			return;
		}

		final Constructor<AbstractStorage> constructor;
		try{
			constructor = classObject.getDeclaredConstructor();
		}catch(Throwable t){
			outputStream.println("error: Unable to reflect on class. Illegal implementation");
			logger.log(Level.SEVERE, "error: Unable to reflect on class. Illegal implementation for '" + classObject
					+ "'. Must have an empty public constructor", t);
			return;
		}
		
		final AbstractStorage storage;
		try{
			storage = constructor.newInstance();
		}catch(Throwable t){
			outputStream.println("error: Unable to instantiate class");
			logger.log(Level.SEVERE, "error: Unable to instantiate class using the empty constructor: " + classObject, t);
			return;
		}

		////
		
		final Result<ArrayList<String>> screensArgumentsInOrderResult = AbstractScreen.parseScreensInOrder(arguments, classObject);
		if(screensArgumentsInOrderResult.error){
			outputStream.println("error: Failed to parse screen arguments for the storage");
			logger.log(Level.SEVERE, "error: Failed to parse screen arguments for the storage: " + screensArgumentsInOrderResult.toErrorString());
			return;
		}
		
		final Result<ArrayList<AbstractScreen>> screensResult = AbstractScreen.initializeScreensInOrder(screensArgumentsInOrderResult.result);
		if(screensResult.error){
			outputStream.println("error: Failed to initialize screen(s) for the storage");
			logger.log(Level.SEVERE, "error: Failed to initialize screen(s) for the storage: " + screensResult.toErrorString());
			return;
		}

		storage.addScreens(screensResult.result);

		try{
			if(!storage.initialize(arguments)){
				throw new Exception("Failed to initialize storage");
			}
		}catch(Exception | Error ex){
			logger.log(Level.SEVERE, "Unable to initialize storage!", ex);
			outputStream.println("failed");
			storage.clearScreens();
			AbstractScreen.shutdownScreens(screensResult.result);
			return;
		}

		boolean setAsDefaultQuery = false;
		// If the storage classes match and the storage instance is not set for querying
		// only then do the following.
		if(storage.getClass().equals(Kernel.getDefaultQueryStorageClass()) && Kernel.getDefaultQueryStorage() == null){
			try{
				final QueryInstructionExecutor instructionExecutor = storage.getQueryInstructionExecutor();
				if(instructionExecutor == null){
					throw new RuntimeException("NULL query executor");
				}else{
					Kernel.setDefaultQueryStorage(storage);
					setAsDefaultQuery = true;
					logger.log(Level.INFO, "Storage '" + storage.getClass().getSimpleName()
							+ "' successfully set as default" + " for querying.");
				}
			}catch(Throwable t){
				logger.log(Level.WARNING,
						"Query storage not set for '" + Kernel.getDefaultQueryStorageClass().getSimpleName() + "'.", t);
			}
		}
		// The initialize() method must return true to indicate
		// successful startup.
		storage.arguments = arguments;
		storage.vertexCount = 0;
		storage.edgeCount = 0;
		
		storages.add(storage);
		
		final List<String> screenNames = HelperFunctions.getListOfClassNames(screensResult.result);
		
		logger.log(Level.INFO, "Storage added: " + storage.getClass() + " " + arguments + ". Using screens: " + screenNames);
		outputStream.println("done." 
				+ (!setAsDefaultQuery ? "" : " [ Querying default ]")
				+ (" Screens:" + (screenNames.isEmpty() ? "NONE" : screenNames.toString()))
				);
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
        String[] tokens = line.split("\\s+", 4);
        if (tokens.length < 3)
        {
            outputStream.println("Usage:");
            outputStream.println("\t" + ADD_REPORTER_STORAGE_STRING);
            outputStream.println("\t" + ADD_FILTER_TRANSFORMER_STRING);
            outputStream.println("\t" + ADD_ANALYZER_SKETCH_STRING);
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
            	arguments = (tokens.length == 3) ? null : tokens[3];
            	addReporterCommand(outputStream, className, arguments);
            	break;
            case "analyzer":
            	arguments = (tokens.length == 3) ? null : tokens[3];
            	logger.log(Level.INFO, "Adding analyzer: {0}", className);
            	outputStream.print("Adding analyzer " + className + "... ");
            	AbstractAnalyzer analyzer;
            	try
            	{
            		Class<? extends AbstractAnalyzer> clazz = 
            				(Class<? extends AbstractAnalyzer>)Class.forName("spade.analyzer." + className);
            		Constructor<? extends AbstractAnalyzer> constructor = clazz.getDeclaredConstructor();
            		analyzer = constructor.newInstance();
            		if(analyzer.initialize(arguments))
            		{
            			analyzers.add(analyzer);
            			logger.log(Level.INFO, "Analyzer added: {0}", className);
            			outputStream.println("done");
            		}
            		else
            			outputStream.println("failed");
            	}catch(Throwable t){
            		outputStream.println("error: Unable to find/load/initialize class");
            		outputStream.flush();
            		logger.log(Level.SEVERE, null, t);
            		return;
            	}


                break;

            case "storage":
                arguments = (tokens.length == 3) ? null : tokens[3];
                addStorageCommand(outputStream, className, arguments);
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

                if(filter.initialize(arguments)){
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
	                logger.log(Level.INFO, "Filter added: {0}", className + " " + arguments);
	                outputStream.println("done");
                }else{
                	outputStream.println("failed");
                }

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
                }
                catch (NumberFormatException numberFormatException)
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

                    logger.log(Level.INFO, "Transformer added: {0}", className + " " + arguments);
                    outputStream.println("done");
                }
                else
                {
                    outputStream.println("failed");
                }

                break;

            case "sketch":
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
                outputStream.println("\t" + ADD_ANALYZER_SKETCH_STRING);
                outputStream.println("\t" + ADD_REPORTER_STORAGE_STRING);
                outputStream.println("\t" + ADD_FILTER_TRANSFORMER_STRING);
        }
    }

	@SuppressWarnings("unchecked")
	private static void setQueryStorageCommand(String line, PrintStream outputStream){
		final String tokens[] = line.split("\\s+", 3);
		if(tokens.length < 3){
			outputStream.println("Usage:");
			outputStream.println("\t" + SET_QUERY_STORAGE_STRING);
			return;
		}

		final String storageClassName = tokens[2];
		
		logger.log(Level.INFO, "Setting default query storage: {0}", storageClassName);
		outputStream.print("Setting default query storage " + storageClassName + "... ");
		
		if(Kernel.getDefaultQueryStorageClass() == null){
			try{
    			Class<? extends AbstractStorage> newQueryStorageClass = 
    					(Class<? extends AbstractStorage>)Class.forName("spade.storage."+storageClassName);
    			Kernel.setDefaultQueryStorageClass(newQueryStorageClass);
    			if(Kernel.getDefaultQueryStorage() != null){
    				// remove this since the class changed
    				logger.log(Level.INFO, "Removed existing default query storage: '"+Kernel.getDefaultQueryStorage().getClass().getSimpleName()+"'");
    				Kernel.setDefaultQueryStorage(null);
    			}
    			
    			AbstractStorage matchingStorage = null;
    			for(AbstractStorage storage : storages){
    				if(storage.getClass().getSimpleName().equalsIgnoreCase(storageClassName)){ // Just get the first one (in no order) TODO
    					matchingStorage = storage;
    					break;
    				}
    			}
    			
    			if(matchingStorage != null){
    				try{
    	        		QueryInstructionExecutor instructionExecutor = matchingStorage.getQueryInstructionExecutor();
    	        		if(instructionExecutor == null){
    	        			throw new RuntimeException("NULL query executor");
    	        		}else{
    	        			Kernel.setDefaultQueryStorage(matchingStorage);
    	        			outputStream.println("done");
	            			logger.log(Level.INFO, "Storage '"+matchingStorage.getClass().getSimpleName()+"' successfully set as default"
	            					+ " for querying.");
	            			return;
    	        		}
    	        	}catch(Throwable t){
    	        		outputStream.println("done but failed to select instance: " + t.getMessage());
    	        		logger.log(Level.WARNING, "Query storage not set for '"
    	        				+matchingStorage.getClass().getSimpleName()+"' but the class is set successfully.", t);
	    				return;
    	        	}
    			}else{ // no matching found
    				outputStream.println("done");
    				logger.log(Level.INFO, "No matching query storage found and set the class to '"+storageClassName+"'");
    				return;
    			}
    		}catch(Throwable t){
    			outputStream.println("Unable to find/load class");
                logger.log(Level.SEVERE, "Invalid class name: '"+storageClassName+"'. ", t);
                return;
    		}
		}else{
			// There is an existing class
			if(Kernel.getDefaultQueryStorageClass().getSimpleName().equalsIgnoreCase(storageClassName)){
				logger.log(Level.INFO, "Class is already the default query storage: " + storageClassName);
		        outputStream.println("Already set");
				return;
				// Don't need to do anything more
			}else{
				// If it is different
				try{
	    			Class<? extends AbstractStorage> newQueryStorageClass = 
	    					(Class<? extends AbstractStorage>)Class.forName("spade.storage."+storageClassName);
	    			Kernel.setDefaultQueryStorageClass(newQueryStorageClass);
	    			if(Kernel.getDefaultQueryStorage() != null){
	    				// remove this since the class changed
	    				logger.log(Level.INFO, "Removed existing default query storage: '"+Kernel.getDefaultQueryStorage().getClass().getSimpleName()+"'");
	    				Kernel.setDefaultQueryStorage(null);
	    			}
	    			
	    			AbstractStorage matchingStorage = null;
	    			for(AbstractStorage storage : storages){
	    				if(storage.getClass().getSimpleName().equalsIgnoreCase(storageClassName)){ // Just get the first one (in no order) TODO
	    					matchingStorage = storage;
	    					break;
	    				}
	    			}
	    			
	    			if(matchingStorage != null){
	    				try{
	    	        		QueryInstructionExecutor instructionExecutor = matchingStorage.getQueryInstructionExecutor();
	    	        		if(instructionExecutor == null){
	    	        			throw new RuntimeException("NULL query executor");
	    	        		}else{
	    	        			Kernel.setDefaultQueryStorage(matchingStorage);
	    	        			outputStream.println("done");
    	            			logger.log(Level.INFO, "Storage '"+matchingStorage.getClass().getSimpleName()+"' successfully set as default"
    	            					+ " for querying.");
    	            			return;
	    	        		}
	    	        	}catch(Throwable t){
	    	        		outputStream.println("done but failed to select instance: " + t.getMessage());
	    	        		logger.log(Level.WARNING, "Query storage not set for '"
	    	        				+matchingStorage.getClass().getSimpleName()+"' but the class is set successfully.", t);
		    				return;
	    	        	}
	    			}else{ // no matching found
	    				outputStream.println("done");
	    				logger.log(Level.INFO, "No matching query storage found and set the class to '"+storageClassName+"'");
	    				return;
	    			}
	    		}catch(Throwable t){
	    			outputStream.println("Unable to find/load class. Kept existing: '"+Kernel.getDefaultQueryStorageClass().getSimpleName()+"'");
	                logger.log(Level.SEVERE, "Invalid class name: '"+storageClassName+"'. Kept existing: '"
	                		+Kernel.getDefaultQueryStorageClass().getSimpleName()+"'", t);
	                return;
	    		}
			}
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
        String[] tokens = line.split("\\s+");
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
                    if(storage == Kernel.getDefaultQueryStorage()){
                    	outputStream.print(" [ Querying default ]");
                    }
                    final List<String> screenNames = HelperFunctions.getListOfClassNames(storage.getScreens()); 
                    outputStream.print((" (Screens:" + (screenNames.isEmpty() ? "NONE" : screenNames.toString()) + ")"));
                    outputStream.println();
                    count++;
                }

                break;

            case "filters":
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

            case "transformers":
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

            case "sketches":
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
                listCommand("list reporters ", outputStream);
                listCommand("list analyzers " , outputStream);
                listCommand("list storages " , outputStream);
                listCommand("list filters " , outputStream);
                listCommand("list transformers " , outputStream);
                listCommand("list sketches " , outputStream);
                break;

            default:
                outputStream.println("Usage:");
                outputStream.println("\t" + LIST_STRING);
        }
    }

    private static void removeStorageCommand(final String className, final PrintStream outputStream){
		boolean found = false;
		for(Iterator<AbstractStorage> storageIterator = storages.iterator(); storageIterator.hasNext();){
			final AbstractStorage storage = storageIterator.next();
			if(storage != null){
				// Search for the given storage in the storages set.
				if(storage.getClass().getSimpleName().equals(className)){
					// Mark the storage for removal by adding it to the removeStorages set.
					// This will enable the main SPADE thread to safely commit any transactions
					// and then remove the storage.
					long vertexCount = storage.vertexCount;
					long edgeCount = storage.edgeCount;
					removeStorages.add(storage);
					found = true;
					logger.log(Level.INFO, "Shutting down storage: {0}", className);
					outputStream.print("Shutting down storage " + className + "... ");

					while(removeStorages.contains(storage)){
						// Wait for other thread to safely remove storage
						HelperFunctions.sleepSafe(REMOVE_WAIT_DELAY);
					}
					storageIterator.remove();

					if(Kernel.getDefaultQueryStorage() == storage){ // Equality by instance
						// Find the next one with the same class and set to it (if any)
						AbstractStorage newQueryStorage = null;
						for(AbstractStorage checkStorage : storages){
							if(checkStorage.getClass().equals(Kernel.getDefaultQueryStorageClass())){
								newQueryStorage = checkStorage;
								break;
							}
						}
						if(newQueryStorage == null){
							logger.log(Level.WARNING, "Storage '" + storage.getClass().getSimpleName() + "'"
									+ " removed as the default storage for querying. No default storage for querying.");
							Kernel.setDefaultQueryStorage(null);
						}else{
							try{
								final QueryInstructionExecutor instructionExecutor = newQueryStorage.getQueryInstructionExecutor();
								if(instructionExecutor == null){
									throw new RuntimeException("NULL query executor");
								}else{
									logger.log(Level.INFO, "Storage '" + newQueryStorage.getClass().getSimpleName()
											+ "' successfully set as default" + " for querying.");
									Kernel.setDefaultQueryStorage(newQueryStorage);
								}
							}catch(Throwable t){
								logger.log(Level.WARNING,
										"Query storage not set for '" + newQueryStorage.getClass().getSimpleName() + "'.", t);
								Kernel.setDefaultQueryStorage(null);
							}
						}
					}

					logger.log(Level.INFO, "Storage shut down: {0} ({1} vertices and {2} edges were added)",
							new Object[]{className, vertexCount, edgeCount});
					outputStream.println("done (" + vertexCount + " vertices and " + edgeCount + " edges added)");
					break;
				}
			}
		}
		if(!found){
			logger.log(Level.WARNING, "Storage not found (for shutting down): {0}", className);
			outputStream.println("Storage " + className + " not found");
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
                        if (reporter.getClass().getSimpleName().equals(className))
                        {
                            // Mark the reporter for removal by adding it to the removeReporters set.
                            // This will enable the main SPADE thread to cleanly flush the reporter
                            // buffer and remove it.
                        	reporter.getBuffer().shutdown();
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
                        if (analyzer.getClass().getSimpleName().equals(className))
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
                            logger.log(Level.INFO, "Analyzer shut down: {0}", className);
                            outputStream.println("done");
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
                    removeStorageCommand(className, outputStream);
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
                        (filters.get(index - 2)).setNextFilter(filters.get(index));
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
                        if (sketch.getClass().getSimpleName().equals(className))
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

        // Save current configuration.
        final String controlClientConfigFilePath = Settings.getDefaultConfigFilePath(spade.client.Control.class);
        configCommand("config save " + controlClientConfigFilePath, NullStream.out);
        // Shut down all reporters.
        for (AbstractReporter reporter : reporters) {
        	reporter.getBuffer().shutdown();
            reporter.shutdown();
        }
        // Wait for main thread to consume all provenance data.
        while (!reporters.isEmpty()) {
            for (Iterator<AbstractReporter> reporterIterator = reporters.iterator(); reporterIterator.hasNext();) {
                AbstractReporter currentReporter = reporterIterator.next();
                Buffer currentBuffer = currentReporter.getBuffer();
                if (currentBuffer.isEmpty()) {
                    reporterIterator.remove();
                }
            }
            try {
                Thread.sleep(MAIN_THREAD_SLEEP_DELAY);
            } catch (InterruptedException ex) {
                logger.log(Level.WARNING, null, ex);
            }
        }

        // Shut down filters.
        for (int i = 0; i < filters.size() - 1; i++)
        {
            filters.get(i).shutdown();
        }
        // Shut down transformers.
        for (int i = 0; i < transformers.size(); i++)
        {
        	transformers.get(i).shutdown();
        }
        // Shut down storages.
        for (AbstractStorage storage : storages)
        {
        	long vertexCount = storage.vertexCount;
            long edgeCount = storage.edgeCount;
            AbstractScreen.shutdownScreens(storage.getScreens());
            storage.shutdown();
            logger.log(Level.INFO, "Storage shut down: {0} ({1} vertices and {2} edges were added)",
                    new Object[]{storage.getClass().getSimpleName(), vertexCount, edgeCount});
        }
        // Shut down analzers.
        for(AbstractAnalyzer analyzer: analyzers)
        {
            analyzer.shutdown();
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
        logger.log(Level.INFO, "SPADE stopped.");

        try {

            Files.deleteIfExists(Paths.get(Settings.getSPADEProcessIdFilePath()));
        } catch (Exception exception) {
            logger.log(Level.WARNING, "Could not delete PID file.");
        }

        // Allow LogManager to complete its response to the shutdown
        LogManager.shutdownReset();

		try{
			Files.deleteIfExists(Paths.get("", Settings.getCurrentLogLinkPath()));
		}catch(Exception e){
			// ignore
		}
    }

    private static class LocalControlConnection implements Runnable
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
                OutputStream outStream = controlSocket.getOutputStream();
                InputStream inStream = controlSocket.getInputStream();

                BufferedReader controlInputStream = new BufferedReader(new InputStreamReader(inStream));
                PrintStream controlOutputStream = new PrintStream(outStream);
                try
                {
                    while (!Kernel.isShutdown())
                    {
                        // Commands read from the input stream and executed.
                        try
                        {
                            String line = controlInputStream.readLine();
                            if (line == null || line.equalsIgnoreCase(EXIT_STRING) || line.equalsIgnoreCase("quit"))
                            {
                                break;
                            }

                            Kernel.executeCommand(line, controlOutputStream);

                            // An empty line is printed to let the client know that the command output is complete.
                            controlOutputStream.println("");

                        }
                        catch(SocketTimeoutException exception)
                        {
                            logger.log(Level.SEVERE, null, exception);
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
