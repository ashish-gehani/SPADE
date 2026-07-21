/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2015 SRI International

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
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.LinkedList;
import java.util.List;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import jline.ArgumentCompletor;
import jline.Completor;
import jline.ConsoleReader;
import jline.MultiCompletor;
import jline.NullCompletor;
import jline.SimpleCompletor;
import spade.core.Settings;

public class Control {

	private static ConsoleReader commandReader;
    private static PrintStream outputStream;
    private static PrintStream errorStream;
    private volatile static PrintStream SPADEControlIn;
    private static BufferedReader SPADEControlOut;
    private static final String historyFile = Settings.getControlHistoryFilePath();
    private static final String COMMAND_PROMPT = "-> ";
    private static final long THREAD_SLEEP_TIME = 10;
    // Members for creating secure sockets
    private static KeyStore clientKeyStorePrivate;
    private static KeyStore serverKeyStorePublic;
    private static SSLSocketFactory sslSocketFactory;
    
    private static void setupKeyStores() throws Exception
    {
        String SERVER_PUBLIC_PATH = Settings.getServerPublicKeystorePath();
        String CLIENT_PRIVATE_PATH = Settings.getClientPrivateKeystorePath();

        serverKeyStorePublic = KeyStore.getInstance("JKS");
        serverKeyStorePublic.load(new FileInputStream(SERVER_PUBLIC_PATH), Settings.getPasswordPublicKeystoreAsCharArray());
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
    
    private static boolean secureConnectionSetup(){
    	try{
            setupKeyStores();
            setupClientSSLContext();
            return true;
        }catch (Exception exception){
            System.err.println("Unable to set up secure communication context! " + exception);
            return false;
        }
    }

    private static boolean setupStreams(){
    	if(!setupLocalStreams()){
			return false;
		}else{
			return setupRemoteStreams();
		}
    }
    
    private static boolean setupRemoteStreams(){
    	try{
            String host = "localhost";
            int port = Settings.getLocalControlPort();
            SSLSocket remoteSocket = (SSLSocket) sslSocketFactory.createSocket(host, port);
            OutputStream outStream = remoteSocket.getOutputStream();
            InputStream inStream = remoteSocket.getInputStream();
            SPADEControlOut = new BufferedReader(new InputStreamReader(inStream));
            SPADEControlIn = new PrintStream(outStream);
            return true;
    	}catch(Exception e){
    		System.err.println("Unable to set up remote secure communication streams! " + e);
    		return false;
    	}
    }
    
    private static boolean setupLocalStreams(){
    	try{
			outputStream = System.out;
	        errorStream = System.err;
	        
	        commandReader = new ConsoleReader();
	        
	        try{ commandReader.getHistory().setHistoryFile(new File(historyFile)); }catch(Exception e){ /* ignore */ }
	        
	        List<Completor> addArguments = new LinkedList<>();
            addArguments.add(new SimpleCompletor(new String[]{"add"}));
            addArguments.add(new SimpleCompletor(new String[]{"filter", "storage", "reporter",  "transformer", "analyzer"}));
            addArguments.add(new NullCompletor());

            List<Completor> removeArguments = new LinkedList<>();
            removeArguments.add(new SimpleCompletor(new String[]{"remove"}));
            removeArguments.add(new SimpleCompletor(new String[]{"filter", "storage", "reporter",  "transformer", "analyzer"}));
            removeArguments.add(new NullCompletor());

            List<Completor> listArguments = new LinkedList<>();
            listArguments.add(new SimpleCompletor(new String[]{"list"}));
            listArguments.add(new SimpleCompletor(new String[]{"filters", "storages", "reporters", "all",  "transformers", "analyzers"}));
            listArguments.add(new NullCompletor());

            List<Completor> configArguments = new LinkedList<>();
            configArguments.add(new SimpleCompletor(new String[]{"config"}));
            configArguments.add(new SimpleCompletor(new String[]{"load", "save"}));
            configArguments.add(new NullCompletor());
            
            List<Completor> setArguments = new LinkedList<>();
            setArguments.add(new SimpleCompletor(new String[]{"set"}));
            setArguments.add(new SimpleCompletor(new String[]{"storage"}));
            setArguments.add(new NullCompletor());

            List<Completor> completors = new LinkedList<>();
            completors.add(new ArgumentCompletor(addArguments));
            completors.add(new ArgumentCompletor(removeArguments));
            completors.add(new ArgumentCompletor(listArguments));
            completors.add(new ArgumentCompletor(configArguments));
            completors.add(new ArgumentCompletor(setArguments));

            commandReader.addCompletor(new MultiCompletor(completors));
	        return true;
    	}catch(Exception e){
    		System.err.println("Unable to set up local communication streams! " + e);
    		return false;
    	}
    }
    
    private static void closeLocalStreams(){
    	// Nothing to close
    }
    
    private static void closeRemoteStreams(){
    	try{
    		SPADEControlOut.close();
    	}catch(Exception e){
    		System.err.println("Failed to close remote output stream! " + e);
    	}
    	try{
    		SPADEControlIn.close();
    	}catch(Exception e){
    		System.err.println("Failed to close remote input stream! " + e);
    	}
    }
    
    private static void closeStreams(){
    	closeLocalStreams();
    	closeRemoteStreams();
    }
    
    private static boolean printHeaderToUser(){
    	try{
	    	outputStream.println("");
	        outputStream.println("SPADE 3.0 Control Client");
	        outputStream.println("");
	        return true;
    	}catch(Exception e){
    		System.err.println("Failed to write to user output stream! " + e);
    		return false;
    	}
    }
    
    private static void start(){
    	/* 
    	 * First command is empty string. Sending empty string to remote so that it can 
    	 * send back the commands available.
    	 */
    	String command = "";
    	do{
    		// Send the command to the remote stream
    		try{
    			SPADEControlIn.println(command);
    		}catch(Exception e){
    			errorStream.println("Failed to send command '"+command+"' to remote stream! " + e);
    			break;
    		}
    		
    		if("exit".equalsIgnoreCase(command) || "quit".equalsIgnoreCase(command)){
    			// On exit, the kernel doesn't send back any response
    			break;
    		}else{
    			// Wait for response
    			while(true){
    	            // This thread keeps reading from the output pipe and
    	            // printing to the current output stream.
    	            String outputLine = null;
    	            try{
    	            	outputLine = SPADEControlOut.readLine();
    	            	if(outputLine == null){
    	            		// connection closed
    	            		break;
    	            	}else{
    	            		outputStream.println(outputLine);
    	            		if("".equals(outputLine)){
    	            			// End of response
    	            			outputStream.print(COMMAND_PROMPT);
    	            			break;
    	            		}
    	            	}
    	            }
//    	            catch(SocketTimeoutException ste){
//    	            	// Timeout exception. Go back to reading until end of response '' received
//    	            	try{ Thread.sleep(THREAD_SLEEP_TIME); }catch(Exception e){}
//    	            }
    	            catch(Exception e){
    	            	errorStream.println("Error connecting to SPADE Kernel! " + e);
    	            	break;
    	            }
    	        }
    		}
    		// Read the next command from the input stream
    		try{
    			command = commandReader.readLine();
    			// If end of stream then set the command to exit
        		if(command == null){ // End of input
        			command = "exit";
        		}
    		}catch(Exception e){
    			errorStream.println("Failed to read command '"+command+"' from user! " + e);
    			break;
    		}
    	}while(true);
    	
    }
    
    public static void main(String args[]){
		// Set up context for secure connections
		if(secureConnectionSetup() && setupStreams()){
			if(printHeaderToUser()){
				start();
				// Back from the blocking call
				// Print extra new line because of extra '->' printed
				outputStream.println();
				// Cleanup
				closeStreams();
			}
		}
    }
}
