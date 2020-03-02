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

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import org.apache.commons.lang3.StringUtils;

import jline.ConsoleReader;
import spade.core.SPADEQuery;
import spade.core.Settings;

/**
 * @author raza
 */
public class CommandLine{
	
	private static final String SPADE_ROOT = Settings.getProperty("spade_root");
	private static final String historyFile = SPADE_ROOT + "cfg/query.history";
	private static final String COMMAND_PROMPT = "-> ";
	private static String RESULT_EXPORT_PATH = null;

	// Members for creating secure sockets
	private static KeyStore clientKeyStorePrivate;
	private static KeyStore serverKeyStorePublic;
	private static SSLSocketFactory sslSocketFactory;

	private static void setupKeyStores() throws Exception{
		String serverPublicPath = SPADE_ROOT + "cfg/ssl/server.public";
		String clientPrivatePath = SPADE_ROOT + "cfg/ssl/client.private";

		serverKeyStorePublic = KeyStore.getInstance("JKS");
		serverKeyStorePublic.load(new FileInputStream(serverPublicPath), "public".toCharArray());
		clientKeyStorePrivate = KeyStore.getInstance("JKS");
		clientKeyStorePrivate.load(new FileInputStream(clientPrivatePath), "private".toCharArray());
	}

	private static void setupClientSSLContext() throws Exception{
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

	public static void main(String args[]){
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
			String host = "localhost";
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
			System.out.println("SPADE 3.0 Query Client");
			// Set up command history and tab completion.
			ConsoleReader commandReader = new ConsoleReader();
			try{
				commandReader.getHistory().setHistoryFile(new File(historyFile));
			}catch(Exception ex){
				System.err.println(CommandLine.class.getName() + " Command history not set up! " + ex);
			}

			while(true){
				try{
					System.out.flush();
					System.out.print(COMMAND_PROMPT);
					String line = commandReader.readLine();
					if(StringUtils.isBlank(line)){
						continue;
					}
					if(line.trim().toLowerCase().equals("exit") || line.trim().toLowerCase().equals("quit")){
						SPADEQuery spadeQuery = new SPADEQuery("localname", "remotename", line);
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
						
						SPADEQuery spadeQuery = new SPADEQuery("localname", "remotename", line);
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
											resultAsString = graph.exportGraph();
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
}
