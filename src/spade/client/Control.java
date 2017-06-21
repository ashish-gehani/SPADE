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

import jline.ArgumentCompletor;
import jline.Completor;
import jline.ConsoleReader;
import jline.MultiCompletor;
import jline.NullCompletor;
import jline.SimpleCompletor;
import spade.core.Settings;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.LinkedList;
import java.util.List;

public class Control {

    private static PrintStream outputStream;
    private static PrintStream errorStream;
    private volatile static PrintStream SPADEControlIn;
    private static BufferedReader SPADEControlOut;
    private static volatile boolean shutdown;
    private static final String SPADE_ROOT = Settings.getProperty("spade_root");
    private static final String historyFile = SPADE_ROOT + "cfg/control.history";
    private static final String COMMAND_PROMPT = "-> ";
    private static final int THREAD_SLEEP_DELAY = 10;
    // Members for creating secure sockets
    private static KeyStore clientKeyStorePrivate;
    private static KeyStore serverKeyStorePublic;
    private static SSLSocketFactory sslSocketFactory;
    
    private static final Object SPADEControlInLock = new Object(); //an object to synchronize on and to wait until SPADEControlIn has been initialized

    private static void setupKeyStores() throws Exception
    {
        String serverPublicPath = SPADE_ROOT + "cfg/ssl/server.public";
        String clientPrivatePath = SPADE_ROOT + "cfg/ssl/client.private";

        serverKeyStorePublic = KeyStore.getInstance("JKS");
        serverKeyStorePublic.load(new FileInputStream(serverPublicPath), "public".toCharArray());
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

    public static void main(String args[])
    {
        // Set up context for secure connections
        try
        {
            setupKeyStores();
            setupClientSSLContext();
        }
        catch (Exception exception)
        {
            exception.printStackTrace();
        }

        outputStream = System.out;
        errorStream = System.err;

        shutdown = false;

        Runnable outputReader = new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    String host = "localhost";
                    int port = Integer.parseInt(Settings.getProperty("local_control_port"));
                    SSLSocket remoteSocket = (SSLSocket) sslSocketFactory.createSocket(host, port);

                    OutputStream outStream = remoteSocket.getOutputStream();
                    InputStream inStream = remoteSocket.getInputStream();
                    SPADEControlOut = new BufferedReader(new InputStreamReader(inStream));
                    SPADEControlIn = new PrintStream(outStream);
                    
                    synchronized (SPADEControlInLock)
                    {
                    	SPADEControlInLock.notify(); //notify the main thread that it is safe to use spadeControlIn now.
        		    }

                    while (!shutdown)
                    {
                        // This thread keeps reading from the output pipe and
                        // printing to the current output stream.
                        String outputLine = SPADEControlOut.readLine();
                        
                        if(shutdown)
                        {
                        	break;
                        }
                        
                        if (outputLine == null)
                        {
                            System.err.println("Error connecting to SPADE Kernel");
                            shutdown = true;
                        }

                        //ACK[exit] is only received here when sent by this client only.
//                        if ("ACK[exit]".equals(outputLine)){
//                            shutdown = true;
//                            break;
//                        }                        

                        if (outputLine != null)
                        {
                            outputStream.println(outputLine);
                        }
                        
                        if ("".equals(outputLine))
                        {
                            outputStream.print(COMMAND_PROMPT);
                        }
                        
                        Thread.sleep(THREAD_SLEEP_DELAY);
                    }
                    SPADEControlOut.close();
                    SPADEControlIn.close();
                }
                catch (NumberFormatException | IOException | InterruptedException exception)
                {
                    if (!shutdown)
                    {
                        System.err.println(Control.class.getName() + " Exception when communicating with SPADE Kernel! " + exception);
                    }
                    System.exit(-1);
                }
            }
        };
        new Thread(outputReader).start();

        try
        {
    
        	//wait for the spadeControlIn object to be initialized in the other thread
        	synchronized (SPADEControlInLock)
            {
        		while(SPADEControlIn == null)
                {
            		try
                    {
            			SPADEControlInLock.wait();
            		}
            		catch(Exception exception)
                    {
                        System.err.println(Control.class.getName() + " Error waiting for spadeControlIn object! " + exception);
            		}
            	}
			}        	
        	
        	outputStream.println("");
            outputStream.println("SPADE 3.0 Control Client");
            outputStream.println("");
            
            SPADEControlIn.println(""); 
        	
            // Set up command history and tab completion.
            ConsoleReader commandReader = new ConsoleReader();
            
            try
            {
                commandReader.getHistory().setHistoryFile(new File(historyFile));
            }
            catch (Exception ex)
            {
                // Ignore
            }

            List<Completor> addArguments = new LinkedList<>();
            addArguments.add(new SimpleCompletor(new String[]{"add"}));
            addArguments.add(new SimpleCompletor(new String[]{"filter", "storage", "reporter",  "transformer"}));
            addArguments.add(new NullCompletor());

            List<Completor> removeArguments = new LinkedList<>();
            removeArguments.add(new SimpleCompletor(new String[]{"remove"}));
            removeArguments.add(new SimpleCompletor(new String[]{"filter", "storage", "reporter",  "transformer"}));
            removeArguments.add(new NullCompletor());

            List<Completor> listArguments = new LinkedList<>();
            listArguments.add(new SimpleCompletor(new String[]{"list"}));
            listArguments.add(new SimpleCompletor(new String[]{"filters", "storages", "reporters", "all",  "transformers"}));
            listArguments.add(new NullCompletor());

            List<Completor> configArguments = new LinkedList<>();
            configArguments.add(new SimpleCompletor(new String[]{"config"}));
            configArguments.add(new SimpleCompletor(new String[]{"load", "save"}));
            configArguments.add(new NullCompletor());

            List<Completor> completors = new LinkedList<>();
            completors.add(new ArgumentCompletor(addArguments));
            completors.add(new ArgumentCompletor(removeArguments));
            completors.add(new ArgumentCompletor(listArguments));
            completors.add(new ArgumentCompletor(configArguments));

            commandReader.addCompletor(new MultiCompletor(completors));
            
            while (true)
            {
                String line = commandReader.readLine();
                if (line == null || line.equalsIgnoreCase("exit"))
                {
                    SPADEControlIn.println("exit");
                    shutdown = true;
                    break;
                }
                else
                    {
                    SPADEControlIn.println(line);
                }
            }   
        }
        catch (Exception exception)
        {
            System.err.println(Control.class.getName() + " Error connecting to SPADE client! " + exception);
        }
    }
}
