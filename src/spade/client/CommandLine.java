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
package spade.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.mutable.MutableBoolean;

import jline.ConsoleReader;
import spade.core.Graph;
import spade.core.Query;
import spade.core.Settings;
import spade.query.quickgrail.core.AbstractQueryEnvironment;
import spade.query.quickgrail.instruction.ExportGraph;
import spade.utility.FileUtility;
import spade.utility.HostInfo;

/**
 * @author raza
 */
public class CommandLine{

	private static final int maxQueriesFromFilesSize = 1000;

	private static final String historyFile = Settings.getQueryHistoryFilePath();
	private static final File configFile = new File(Settings.getDefaultConfigFilePath(CommandLine.class));
	private static final String COMMAND_PROMPT = "-> ";
	private static String RESULT_EXPORT_PATH = null;
	
	private static boolean batchMode = false;

	// Members for creating secure sockets
	private static KeyStore clientKeyStorePrivate;
	private static KeyStore serverKeyStorePublic;
	private static SSLSocketFactory sslSocketFactory;

	private static void setupKeyStores() throws Exception{
		String SERVER_PUBLIC_PATH = Settings.getServerPublicKeystorePath();
		String CLIENT_PRIVATE_PATH = Settings.getClientPrivateKeystorePath();

		serverKeyStorePublic = KeyStore.getInstance("JKS");
		serverKeyStorePublic.load(new FileInputStream(SERVER_PUBLIC_PATH), Settings.getPasswordPublicKeystoreAsCharArray());
		clientKeyStorePrivate = KeyStore.getInstance("JKS");
		clientKeyStorePrivate.load(new FileInputStream(CLIENT_PRIVATE_PATH), Settings.getPasswordPrivateKeystoreAsCharArray());
	}

	private static void setupClientSSLContext() throws Exception{
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

	private static String getHostName(String args[]){
		if(args.length > 1){ // 0 is query
			// Host name read from arguments
			return args[1];
		}
		try{
			final String spadeHostFilePath = Settings.getSPADEHostFilePath();
			File hostNameFile = new File(spadeHostFilePath);
			try{
				if(hostNameFile.exists()){
					try{
						if(!hostNameFile.isFile() || !hostNameFile.canRead()){
							System.err.println(
									"Invalid configuration to read host name from file: Path is not readable or file '"
											+ spadeHostFilePath + "'");
							return null;
						}else{
							try{
								List<String> lines = FileUtils.readLines(hostNameFile);
								if(!lines.isEmpty()){
									String hostName = lines.get(0);
									if(!hostName.isEmpty()){
										return hostName;
									}
								}
							}catch(Exception e){
								System.err.println(
										"Invalid configuration to read host name from a file: " + e.getMessage());
								return null;
							}
						}
					}catch(Exception e){
						System.err.println("Invalid configuration to read host name from a file: " + e.getMessage());
						return null;
					}
				}
			}catch(Exception e){
				System.err.println("Invalid configuration to read host name from a file: " + e.getMessage());
				return null;
			}
		}catch(Exception e){
			System.err.println("Invalid configuration to read host name from a file: " + e.getMessage());
			return null;
		}
		try{
			return HostInfo.getHostName();
		}catch(Exception e){
			System.err.println("Failed to get host name from system: " + e.getMessage());
			return null;
		}
	}
	
	private static final String addQueriesFromFileToList(
			final LinkedList<SimpleEntry<String, LinkedList<String>>> masterList, final String filePath){
		try{
			final File file = new File(filePath);
			boolean thisFileAlreadyExists = false;
			for(final SimpleEntry<String, LinkedList<String>> entry : masterList){
				if(entry.getKey().equals(file.getCanonicalPath())){
					thisFileAlreadyExists = true;
					break;
				}
			}
			if(thisFileAlreadyExists){
				throw new Exception("Already executing queries in file");
			}
			if(file.exists()){
				if(file.isFile()){
					if(file.canRead()){
						try(final BufferedReader reader = new BufferedReader(new FileReader(file))){
							
							int currentMasterListQueriesSize = 0;
							for(final SimpleEntry<String, LinkedList<String>> entry : masterList){
								currentMasterListQueriesSize += entry.getValue().size();
							}
							
							int linesRead = 0;
							final List<String> queriesFromFile = new ArrayList<String>();
							String line = null;
							while((line = reader.readLine()) != null){
								linesRead++;
								if(!(line.trim().isEmpty() || line.trim().startsWith("#"))){
									if((queriesFromFile.size() + currentMasterListQueriesSize) > maxQueriesFromFilesSize){
										throw new Exception("Too many queries in file. Max queries limit '" + maxQueriesFromFilesSize
												+ "' reached at line '" + linesRead + "'");
									}
									queriesFromFile.add(line);
								}
							}
							if(queriesFromFile.size() == 0){
								return ("No queries in file '" + file.getCanonicalPath() + "'");
							}else{
								final SimpleEntry<String, LinkedList<String>> newSublist = 
										new SimpleEntry<String, LinkedList<String>>(
												file.getCanonicalPath(), new LinkedList<String>(queriesFromFile));
								masterList.addFirst(newSublist);
								return ("Executing queries from file '" + file.getCanonicalPath() + "'");
							}
						}
					}else{
						throw new Exception("Not a readable file");
					}
				}else{
					throw new Exception("Not a file");
				}
			}else{
				throw new Exception("File does not exist");
			}
		}catch(Exception e){
			return ("Error: Failed to read queries from file '" + filePath + "'. " + e.getMessage() + ".");
		}
	}
	
	public static void main(String args[]){
		final List<String> argsList = new ArrayList<String>();
		for(final String arg : args){
			argsList.add(arg);
		}
		
		if(argsList.contains("-b") || argsList.contains("-B")){
			batchMode = true;
			argsList.remove("-b");
			argsList.remove("-B");
		}
		
		final String localHostName = getHostName(argsList.toArray(new String[]{}));
		if(localHostName == null){
			// Error printed by the function getHostName
			return;
		}

		// Set up context for secure connections
		try{
			setupKeyStores();
			setupClientSSLContext();
		}catch(Exception ex){
			System.err.println(CommandLine.class.getName() + " Error setting up context for secure connection. " + ex);
		}

		ObjectOutputStream clientOutputWriter = null;
		ObjectInputStream clientInputReader = null;
		SSLSocket remoteSocket = null;
		try{
			final String host = "localhost"; // Have you changed this to allow remote? If yes, then have you changed the
												// nonce in the query?
			int port = Settings.getCommandLineQueryPort();
			remoteSocket = (SSLSocket)sslSocketFactory.createSocket(host, port);
			OutputStream outStream = remoteSocket.getOutputStream();
			InputStream inStream = remoteSocket.getInputStream();
			clientInputReader = new ObjectInputStream(inStream);
			clientOutputWriter = new ObjectOutputStream(outStream);
		}catch(NumberFormatException | IOException ex){
			System.err.println(CommandLine.class.getName() + " Error connecting to SPADE! " + ex);
			System.err.println("Make sure that the CommandLine analyzer is running.");
			System.exit(-1);
		}
		try{
			/*
			 * Current protocol: 1) Read query from user 2) Create SPADEQuery object with
			 * nonce = null 3) Send SPADEQuery object to the server 4) Wait for and receive
			 * SPADEQuery object from server 5) Ignore nonce since local query 6) Go to (1)
			 */

			writeToUser("", true);
			writeToUser("Host '" + localHostName + "': SPADE Query Client", true);

			// Set up command history and tab completion.
			ConsoleReader commandReader = new ConsoleReader();
			try{
				commandReader.getHistory().setHistoryFile(new File(historyFile));
			}catch(Exception ex){
				System.err.println(CommandLine.class.getName() + " Command history not set up! " + ex);
				System.err.println();
			}

			setupShutdownThread(remoteSocket, clientOutputWriter, clientInputReader, localHostName);
			
			// A list of entries where each entry's key is the name of the source and the value is the list of queries.
			final LinkedList<SimpleEntry<String, LinkedList<String>>> listOfFilePathsAndFileLines = 
					new LinkedList<SimpleEntry<String, LinkedList<String>>>();

			writeToUser("", true);
			writeToUser(addQueriesFromFileToList(listOfFilePathsAndFileLines, configFile.getCanonicalPath()), true);

			final MutableBoolean queryError = new MutableBoolean(false);
			
			while(true){
				queryError.setValue(false);
				try{
					String line = null;

					flushToUser();

					if(listOfFilePathsAndFileLines.size() > 0){
						final SimpleEntry<String, LinkedList<String>> entry = listOfFilePathsAndFileLines.getFirst();
						if(entry.getValue().isEmpty()){
							// file finished
							final String fileFinished = entry.getKey();
							writeToUser("", true);
							writeToUser("Finished executing queries in file '" + fileFinished + "'. ", true);
							listOfFilePathsAndFileLines.removeFirst(); // remove the entry which is exhausted
							if(listOfFilePathsAndFileLines.size() > 0){
								writeToUser("Continuing executing queries in file '"+listOfFilePathsAndFileLines.getFirst().getKey()+"'", true);
							}
							continue;
						}else{
							line = entry.getValue().removeFirst();
							writeToUser("", true);
							writeToUser(COMMAND_PROMPT, false);
							writeToUser(line, true);
						}
					}else{
						writeToUser("", true);
						writeToUser(COMMAND_PROMPT, false);
						line = commandReader.readLine();
					}

					// If end of stream then set the command to exit
					if(line == null){ // End of input
						line = "exit";
					}

					line = line.trim();

					if(line.isEmpty()){
						continue;
					}

					String result = null;

					if(line.toLowerCase().startsWith("load ")){
						final String[] loadTokens = line.split("\\s+", 2);
						if(loadTokens.length == 2){
							final String filePath = loadTokens[1];
							result = addQueriesFromFileToList(listOfFilePathsAndFileLines, filePath);
						}else{
							result = "Invalid 'load' command format. Allowed: 'load <filepath>'";
						}
					}else{
						result = query(clientOutputWriter, clientInputReader, localHostName, line, queryError);
					}

					if(result == null){
						// EOF
						break;
					}
					
					if(result != null && result.isEmpty()){
						result = ("No Result!");
					}

					writeToUser(result, true);

					if(queryError.isTrue()){
						discardAndPrintQueriesInList(listOfFilePathsAndFileLines, true);
					}
					
				}catch(Exception ex){
					System.err.println(CommandLine.class.getName() + " Error talking to the client! " + ex);
				}
			}
			
			// Exited the main loop. Just tell the user in case there were queries still to be executed.
			discardAndPrintQueriesInList(listOfFilePathsAndFileLines, false);
			
		}catch(Exception ex){
			System.err.println(CommandLine.class.getName() + " Error in CommandLine Client! " + ex);
		}
	}
	
	private final static void discardAndPrintQueriesInList(final LinkedList<SimpleEntry<String, LinkedList<String>>> list,
			final boolean sawAnError){
		if(!list.isEmpty()){
			boolean preamblePrinted = false;
			while(!list.isEmpty()){
				SimpleEntry<String, LinkedList<String>> entry = list.removeFirst();
				final int size = entry.getValue().size();
				if(size > 0){
					if(!preamblePrinted){
						final String errorMsg = ((sawAnError) ? " (because of the error above)" : "");
						writeToUser("", true);
						writeToUser("Discarded queries" + errorMsg + " in file(s):", true);
						preamblePrinted = true;
					}
					final String queryWord = ((size == 1) ? "query" : "queries");
					writeToUser(entry.getKey() + " (" + entry.getValue().size() + " "+queryWord+")", true);
				}
			}
		}
	}

	private static void setupShutdownThread(final SSLSocket remoteSocket, final ObjectOutputStream clientOutputWriter,
			final ObjectInputStream clientInputReader, final String localHostName){
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable(){
			@Override
			public void run(){
				final List<String> lines = new ArrayList<String>();

				final MutableBoolean queryError = new MutableBoolean(false);
				
				try{
					final String storageName = query(clientOutputWriter, clientInputReader, localHostName,
							"print storage", queryError);

					if(queryError.isFalse() && storageName != null && !storageName.trim().equalsIgnoreCase("No current storage set")){
						lines.add("set storage " + storageName);
						
						queryError.setValue(false);
						
						final String maxDepthValue = query(clientOutputWriter, clientInputReader, localHostName,
								"env print maxdepth", queryError);
						
						if(queryError.isFalse() && !(maxDepthValue == null || maxDepthValue
								.equalsIgnoreCase(AbstractQueryEnvironment.environmentVariableValueUNSET))){
							lines.add("env set maxdepth " + maxDepthValue);
						}
						
						queryError.setValue(false);
						
						final String limitValue = query(clientOutputWriter, clientInputReader, localHostName,
								"env print limit", queryError);
						
						if(queryError.isFalse() && !(limitValue == null || limitValue
								.equalsIgnoreCase(AbstractQueryEnvironment.environmentVariableValueUNSET))){
							lines.add("env set limit " + limitValue);
						}
						
						queryError.setValue(false);
					}
				}catch(Exception e){
					System.err.println("Failed to get query environment state: " + e.getMessage());
					System.err.println();
				}

				try{
					FileUtility.writeLines(configFile.getCanonicalPath(), lines);
					writeToUser("", true);
					writeToUser("Current query environment saved to file: " + configFile, true);
				}catch(Exception e){
					System.err.println("Failed to save queries " + lines + " to file: " + configFile);
					System.err.println();
				}

				try{
					queryError.setValue(false);
					query(clientOutputWriter, clientInputReader, localHostName, "exit", queryError);
				}catch(Exception e){

				}

				if(remoteSocket != null){
					try{
						remoteSocket.close();
					}catch(Exception e){
						// Ignore
					}
				}
			}
		}));
	}

	// returns true only if it was export
	private static String parseExport(final String line){
		if(line == null){
			return null;
		}
		String[] tokens = line.split("\\s+", 3);
		if(tokens.length != 3){
			return null;
		}
		String command = tokens[0];
		String operator = tokens[1].trim();
		String path = tokens[2].trim();
		if(command.equalsIgnoreCase("export") && operator.equals(">")){
			RESULT_EXPORT_PATH = path;
			return "Output export path set to '" + path + "' for next query.";
		}else{
			return null;
		}
	}

	private static String query(final ObjectOutputStream clientOutputWriter, final ObjectInputStream clientInputReader,
			final String localHostName, final String line, final MutableBoolean error) throws Exception{
		boolean isExport = false;
		try{
			if(line.toLowerCase().startsWith("export ")){
				// save export path for next answer's dot file
				final String parseExportResult = parseExport(line);
				if(parseExportResult != null){
					isExport = true;
					return parseExportResult;
				}else{
					isExport = false;
					// Continue below on trying to execute the query
				}
			}

			// if query was exit/quit
			if("exit".equalsIgnoreCase(line) || "quit".equalsIgnoreCase(line)){
				return null; // EOF
				// Don't tell the server that the connection will be closed.
				// We need to query to get the environment.
			}

			final String queryNonce = null; // Keep the nonce null to indicate that the query is local
			Query spadeQuery = new Query(localHostName, localHostName, line, queryNonce);

			clientOutputWriter.writeObject(spadeQuery);
			clientOutputWriter.flush();

			final Object resultObject = clientInputReader.readObject();
			if(resultObject == null){ // EOF
				throw new Exception("Connection closed by the server!");
			}else{
				spadeQuery = (Query)resultObject; // overwrite
				if(spadeQuery.wasQuerySuccessful()){
					if(spadeQuery.getResult() == null){
						return ""; // Empty result
					}else{
						Object spadeResult = spadeQuery.getResult();
						if(spadeResult.getClass().equals(String.class)){
							return String.valueOf(spadeResult);
						}else{ // Other types
							if(spadeResult instanceof spade.core.Graph){
								spade.core.Graph graph = (spade.core.Graph)spadeResult;
								if(RESULT_EXPORT_PATH != null){
									if(RESULT_EXPORT_PATH.toLowerCase().endsWith(".json")){
										Graph.exportGraphToFile(ExportGraph.Format.kJson, RESULT_EXPORT_PATH, graph);
									}else{
										Graph.exportGraphToFile(ExportGraph.Format.kDot, RESULT_EXPORT_PATH, graph);
									}
									return "Output exported to file: " + RESULT_EXPORT_PATH;
								}else{
									writeToUser(graph);
									return "Output exported to STDOUT";
								}
							}else{
								return String.valueOf(spadeResult);
							}
						}
					}
				}else{
					error.setValue(true);
					if(spadeQuery.getError() == null){
						return ""; // Empty result
					}else{
						Object errorObject = spadeQuery.getError();
						if(errorObject instanceof Throwable){
							return "Error: " + ((Throwable)errorObject).getMessage();
						}else{
							return "Error: " + errorObject;
						}
					}
				}
			}
		}catch(Exception e){
			error.setValue(true);
			throw new Exception("Failed to execute query '" + line + "'. " + e.getMessage(), e);
		}finally{
			if(!isExport){
				RESULT_EXPORT_PATH = null;
			}
		}
	}

	private final static void writeToUser(final Graph graph){
		if(graph != null){
			try{
				boolean closeSystemOut = false;
				Graph.exportGraphUsingWriter(ExportGraph.Format.kJson, 
						new BufferedWriter(new OutputStreamWriter(System.out)), graph, closeSystemOut);
			}catch(Exception e){
				System.err.println("Failed to export graph as JSON: " + e.getMessage());
				e.printStackTrace(System.err);
			}
		}
	}
	
	private final static void writeToUser(final String str, final boolean newLine){
		if(!batchMode){
			if(str != null){
				if(newLine){
					System.out.println(str);
				}else{
					System.out.print(str);
				}
			}
		}
	}
	
	private final static void flushToUser(){
		System.out.flush();
	}
	
}
