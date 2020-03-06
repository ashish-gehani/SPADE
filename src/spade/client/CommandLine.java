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

import static spade.core.Kernel.CONFIG_PATH;
import static spade.core.Kernel.FILE_SEPARATOR;
import static spade.core.Kernel.KEYSTORE_FOLDER;
import static spade.core.Kernel.KEYS_FOLDER;
import static spade.core.Kernel.PASSWORD_PRIVATE_KEYSTORE;
import static spade.core.Kernel.PASSWORD_PUBLIC_KEYSTORE;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import jline.ConsoleReader;
import spade.core.SPADEQuery;
import spade.core.Settings;
import spade.utility.HelperFunctions;
import spade.utility.HostInfo;

/**
 * @author raza
 */
public class CommandLine{
	
	private static final String SPADE_ROOT = Settings.getProperty("spade_root");
	private static final String historyFile = SPADE_ROOT + "cfg/query.history";
	private static final String COMMAND_PROMPT = "-> ";
	private static String RESULT_EXPORT_PATH = null;
	
	private static final String hostNameFilePath = SPADE_ROOT + File.separator + "hostname.txt";

	// Members for creating secure sockets
	private static KeyStore clientKeyStorePrivate;
	private static KeyStore serverKeyStorePublic;
	private static SSLSocketFactory sslSocketFactory;
	
	private static void setupKeyStores() throws Exception
    {
        String KEYS_PATH = CONFIG_PATH + FILE_SEPARATOR + KEYS_FOLDER;
        String KEYSTORE_PATH = KEYS_PATH + FILE_SEPARATOR + KEYSTORE_FOLDER;
        String SERVER_PUBLIC_PATH = KEYSTORE_PATH + FILE_SEPARATOR + "serverpublic.keystore";
        String CLIENT_PRIVATE_PATH = KEYSTORE_PATH + FILE_SEPARATOR + "clientprivate.keystore";

        serverKeyStorePublic = KeyStore.getInstance("JKS");
        serverKeyStorePublic.load(new FileInputStream(SERVER_PUBLIC_PATH), PASSWORD_PUBLIC_KEYSTORE.toCharArray());
        clientKeyStorePrivate = KeyStore.getInstance("JKS");
        clientKeyStorePrivate.load(new FileInputStream(CLIENT_PRIVATE_PATH), PASSWORD_PRIVATE_KEYSTORE.toCharArray());
    }

    private static void setupClientSSLContext() throws Exception
    {
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextInt();

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(serverKeyStorePublic);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(clientKeyStorePrivate, PASSWORD_PRIVATE_KEYSTORE.toCharArray());

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), secureRandom);
        sslSocketFactory = sslContext.getSocketFactory();
    }

    private static String getHostName(String args[]){
    	if(args.length > 0){
    		// Host name read from arguments
    		return args[0];
    	}
    	try{
    		File hostNameFile = new File(hostNameFilePath);
    		try{
    			if(hostNameFile.exists()){
    				try{
	    				if(!hostNameFile.isFile() || !hostNameFile.canRead()){
	    					System.err.println("Invalid configuration to read host name from file: Path is not readable or file '"+hostNameFilePath+"'");
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
	    						System.err.println("Invalid configuration to read host name from a file: "+ e.getMessage());
	        	        		return null;
	    					}
	    				}
    				}catch(Exception e){
    					System.err.println("Invalid configuration to read host name from a file: "+ e.getMessage());
    	        		return null;
    				}
    			}
    		}catch(Exception e){
    			System.err.println("Invalid configuration to read host name from a file: "+ e.getMessage());
        		return null;
    		}
    	}catch(Exception e){
    		System.err.println("Invalid configuration to read host name from a file: "+ e.getMessage());
    		return null;
    	}
    	String hostNameFromSystem = HostInfo.getHostName();
    	if(hostNameFromSystem == null){
    		System.err.println("Failed to get host name from system");
    		return null;
    	}else{
    		return hostNameFromSystem;
    	}
    }
    
	public static void main(String args[]){
		final StringBuffer nameOfSetStorage = new StringBuffer();
		boolean setInitialStorageFromConfig = readStorageNameFromConfig(nameOfSetStorage);
		
		final String localHostName = getHostName(args);
		if(localHostName == null){
			// Error printed by the function getHostName
			return;
		}
		
		ObjectOutputStream clientOutputWriter = null;
		ObjectInputStream clientInputReader = null;
		SSLSocket remoteSocket = null;
		// Set up context for secure connections
		try{
			setupKeyStores();
			setupClientSSLContext();
		}catch(Exception ex){
			System.err.println(CommandLine.class.getName() + " Error setting up context for secure connection. " + ex);
		}
		try{
			final String host = "localhost"; // Have you changed this to allow remote? If yes, then have you changed the nonce in the query?
			int port = Integer.parseInt(Settings.getProperty("commandline_query_port"));
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
			 * Current protocol:
			 * 1) Read query from user
			 * 2) Create SPADEQuery object with nonce = null
			 * 3) Send SPADEQuery object to the server
			 * 4) Wait for and receive SPADEQuery object from server
			 * 5) Ignore nonce since local query
			 * 6) Go to (1)
			 */
			
			System.out.println("Host '"+localHostName+"': SPADE 3.0 Query Client");
			System.out.println();
			// Set up command history and tab completion.
			ConsoleReader commandReader = new ConsoleReader();
			try{
				commandReader.getHistory().setHistoryFile(new File(historyFile));
			}catch(Exception ex){
				System.err.println(CommandLine.class.getName() + " Command history not set up! " + ex);
			}

			while(true){
				try{
					String line = null;
					
					System.out.flush();
					
					if(setInitialStorageFromConfig){
						line = "set storage " + nameOfSetStorage.toString();
						setInitialStorageFromConfig = false;
					}else{
						System.out.print(COMMAND_PROMPT);
						line = commandReader.readLine();
					}
					
					if(StringUtils.isBlank(line)){
						continue;
					}
					if(line.trim().toLowerCase().equals("exit") || line.trim().toLowerCase().equals("quit")){
						final String queryNonce = null; // Keep the nonce null to indicate that the query is local
						SPADEQuery spadeQuery = new SPADEQuery(localHostName, localHostName, line, queryNonce);
						spadeQuery.setQuerySentByClientAtMillis();
						
						clientOutputWriter.writeObject(spadeQuery);
						clientOutputWriter.flush();
						break;
					}else if(line.toLowerCase().startsWith("export ")){
						// save export path for next answer's dot file
						parseExport(line);
					}else{
						if(RESULT_EXPORT_PATH != null){
							//line = "export " + line;
						}
						
						boolean isSetStorage = checkAndGetSetStorageCommand(line, nameOfSetStorage);
						
						final String queryNonce = null; // Keep the nonce null to indicate that the query is local
						SPADEQuery spadeQuery = new SPADEQuery(localHostName, localHostName, line, queryNonce);
						spadeQuery.setQuerySentByClientAtMillis();
						
						clientOutputWriter.writeObject(spadeQuery);
						clientOutputWriter.flush();
						
						final Object resultObject = clientInputReader.readObject();
						if(resultObject == null){ // EOF
							System.out.println("Connection closed by the server!");
							break;
						}else{
							spadeQuery = (SPADEQuery)resultObject; // overwrite
							spadeQuery.setQueryReceivedBackByClientAtMillis();

							if(spadeQuery.wasQuerySuccessful()){
								
								if(isSetStorage){
									writeStorageNameToConfig(nameOfSetStorage);
								}
								
								if(spadeQuery.getResult() == null){
									System.out.println("No result!");
								}else{
									boolean alreadyPrinted = false;
									String resultAsString = null;
									
									Object spadeResult = spadeQuery.getResult();
									if(spadeResult.getClass().equals(String.class)){ // Main type 
										if(String.valueOf(spadeResult).isEmpty()){
											System.out.println("No result!");
											alreadyPrinted = true;
										}else{
											resultAsString = String.valueOf(spadeResult);
											alreadyPrinted = false;
										}
									}else{ // Other types
										if(spadeResult.getClass().equals(spade.core.Graph.class)){
											spade.core.Graph graph = (spade.core.Graph)spadeResult;
											if(RESULT_EXPORT_PATH != null){
												resultAsString = graph.exportGraph();
											}else{
												resultAsString = graph.prettyPrint();
											}
											alreadyPrinted = false;
										}else{
											// default!
											resultAsString = String.valueOf(spadeResult);
											alreadyPrinted = false;
										}
									}
									
									if(alreadyPrinted == false){
										if(RESULT_EXPORT_PATH != null){
											FileWriter writer = new FileWriter(RESULT_EXPORT_PATH, false);
											writer.write(resultAsString);
											writer.flush();
											writer.close();
											System.out.println("Output exported to file: " + RESULT_EXPORT_PATH);
											RESULT_EXPORT_PATH = null;
										}else{
											System.out.println(resultAsString);
											System.out.println();
										}
									}
								}
							}else{
								
								if(isSetStorage){
									resetStorageNameInConfig();
								}
								
								if(spadeQuery.getError() == null){
									System.out.println("No result!");
								}else{
									Object errorObject = spadeQuery.getError();
									if(errorObject instanceof Throwable){
										System.out.println("Error: " + ((Throwable)errorObject).getMessage());
									}else{
										System.out.println("Error: " + errorObject);
									}
								}
							}
							
							// Reset irrespective of success or failure
							RESULT_EXPORT_PATH = null;
						}
					}
				}catch(Exception ex){
					System.err.println(CommandLine.class.getName() + " Error talking to the client! " + ex);
				}
			}
		}catch(IOException ex){
			System.err.println(CommandLine.class.getName() + " Error in CommandLine Client! " + ex);
		}
		if(remoteSocket != null){
			try{
				remoteSocket.close();
			}catch(Exception e){
			}
		}
	}

	private static void parseExport(String line){
		try{
			String[] tokens = line.split("\\s+", 3);
			String command = tokens[0].toLowerCase().trim();
			String operator = tokens[1].trim();
			String path = tokens[2].trim();
			if(command.equalsIgnoreCase("export") && operator.equals(">")){
				RESULT_EXPORT_PATH = path;
				System.out.println("Output export path set to '" + path + "' for next query.");
			}
		}catch(Exception ex){
			System.err.println(CommandLine.class.getName() + " Insufficient arguments!");
		}
	}
	
	private static boolean checkAndGetSetStorageCommand(String query, StringBuffer storageName){
		if(storageName != null){
			storageName.setLength(0);
			if(query != null){
				String tokens[] = query.split("\\s+", 3);
				if(tokens.length == 3){
					if(tokens[0].equals("set") && tokens[1].equals("storage")){
						storageName.append(tokens[2]);
						return true;
					}
				}
			}
		}
		return false;
	}
	
	private static void writeStorageNameToConfig(StringBuffer buffer){
		if(buffer != null && !buffer.toString().trim().isEmpty()){
			try{
				String configFile = Settings.getDefaultConfigFilePath(CommandLine.class);
				List<String> lines = new ArrayList<String>();
				lines.add(buffer.toString());
				FileUtils.writeLines(new File(configFile), lines);
			}catch(Exception e){
				// TODO error message?
			}
		}
	}
	
	private static boolean readStorageNameFromConfig(StringBuffer buffer){
		if(buffer != null){
			buffer.setLength(0);
			try{
				String configFile = Settings.getDefaultConfigFilePath(CommandLine.class);
				List<String> lines = FileUtils.readLines(new File(configFile));
				if(!lines.isEmpty()){
					String first = lines.get(0);
					if(!HelperFunctions.isNullOrEmpty(first)){
						buffer.append(first.trim());
						return true;
					}
				}
			}catch(Exception e){
				// TODO error message?
			}
		}
		return false;
	}
	
	private static boolean resetStorageNameInConfig(){
		try{
			String configFile = Settings.getDefaultConfigFilePath(CommandLine.class);
			FileUtils.writeStringToFile(new File(configFile), "");
			return true;
		}catch(Exception e){
			return false;
		}
	}
}
