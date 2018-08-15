package spade.client;

import jline.ConsoleReader;
import spade.core.Settings;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static spade.analyzer.CommandLine.QueryCommands;
import static spade.analyzer.CommandLine.getQueryCommands;

/**
 * @author raza
 */
public class CommandLine
{
    private static PrintStream clientOutputStream;
    private static ObjectInputStream clientInputStream;
    private static final String SPADE_ROOT = Settings.getProperty("spade_root");
    private static final String historyFile = SPADE_ROOT + "cfg/query.history";
    private static final String COMMAND_PROMPT = "-> ";
    private static HashMap<String, String> constraints = new HashMap<>();
    private static String RESULT_EXPORT_PATH = null;

    // Members for creating secure sockets
    private static KeyStore clientKeyStorePrivate;
    private static KeyStore serverKeyStorePublic;
    private static SSLSocketFactory sslSocketFactory;


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
        catch (Exception ex)
        {
            System.err.println(CommandLine.class.getName() + " Error setting up context for secure connection. " + ex);
        }

        try
        {
            String host = "localhost";
            int port = Integer.parseInt(Settings.getProperty("commandline_query_port"));
            SSLSocket remoteSocket = (SSLSocket) sslSocketFactory.createSocket(host, port);

            OutputStream outStream = remoteSocket.getOutputStream();
            InputStream inStream = remoteSocket.getInputStream();
            clientInputStream = new ObjectInputStream(inStream);
            clientOutputStream = new PrintStream(outStream);
        }
        catch (NumberFormatException | IOException ex)
        {
            System.err.println(CommandLine.class.getName() + " Error connecting to SPADE! " + ex);
            System.err.println("Make sure that the CommandLine analyzer is running.");
            System.exit(-1);
        }

        try
        {
            System.out.println("SPADE 3.0 Query Client");

            // Set up command history and tab completion.
            ConsoleReader commandReader = new ConsoleReader();
            try
            {
                commandReader.getHistory().setHistoryFile(new File(historyFile));
            }
            catch (Exception ex)
            {
                System.err.println(CommandLine.class.getName() + " Command history not set up! " + ex);
            }

            while (true)
            {
                try
                {
                    System.out.println(getQueryCommands());
                    System.out.print(COMMAND_PROMPT);
                    String line = commandReader.readLine();

                    if(line.equals(QueryCommands.QUERY_EXIT.value))
                    {
                        clientOutputStream.println(line);
                        break;
                    }
                    else if(line.equals(QueryCommands.QUERY_LIST_CONSTRAINTS.value))
                    {
                        System.out.println("-------------------------------------------------");
                        System.out.println("Constraint Name\t\t | Constraint Expression");
                        System.out.println("-------------------------------------------------");
                        for (Map.Entry<String, String> currentEntry : constraints.entrySet())
                        {
                            String key = currentEntry.getKey();
                            String value = currentEntry.getValue();
                            System.out.println(key + "\t\t\t | " + value);
                        }
                        System.out.println("-------------------------------------------------");
                        System.out.println();
                    }
                    else if(line.contains(":"))
                    {
                        // probably a constraint
                        createConstraint(line);
                    }
                    else if(line.contains("("))
                    {
                        // send line to analyzer
                        String query = parseQuery(line);
                        if(RESULT_EXPORT_PATH != null)
                        {
                            query = "export " + query;
                        }
                        long start_time = System.currentTimeMillis();
                        clientOutputStream.println(query);
                        String returnTypeName = (String) clientInputStream.readObject();
                        if(returnTypeName.equalsIgnoreCase("error"))
                        {
                            System.out.println("Error executing query request!");
                            continue;
                        }
                        Class<?> returnType = Class.forName(returnTypeName);
                        Object result = clientInputStream.readObject();
                        String resultString = "";
                        if(returnType.isAssignableFrom(result.getClass()))
                        {
                            resultString = result.toString();
                        }
                        long elapsed_time = System.currentTimeMillis() - start_time;
                        System.out.println("Time taken for query: " + elapsed_time + " ms");
                        if(RESULT_EXPORT_PATH != null)
                        {
                            FileWriter writer = new FileWriter(RESULT_EXPORT_PATH, false);
                            writer.write(resultString);
                            writer.flush();
                            writer.close();
                            System.out.println("Output exported to file: " + RESULT_EXPORT_PATH);
                            RESULT_EXPORT_PATH = null;
                        }
                        else
                        {
                            System.out.println();
                            System.out.println("Result:");
                            System.out.println("Return type: " + returnTypeName);
                            System.out.println("Result value: " + resultString);
                            System.out.println("------------------");
                        }
                    }
                    else if(line.toLowerCase().startsWith("export"))
                    {
                        // save export path for next answer's dot file
                        parseExport(line);
                    }
                    else
                    {
                        System.out.println("Invalid input!");
                    }
                }
                catch (Exception ex)
                {
                    System.err.println(CommandLine.class.getName() + " Error talking to the client! " + ex);
                }
            }
        }
        catch (IOException ex)
        {
            System.err.println(CommandLine.class.getName() + " Error in CommandLine Client! " + ex);
        }
    }

    private static void parseExport(String line)
    {
        try
        {
            String[] tokens = line.split("\\s+");
            String command = tokens[0].toLowerCase().trim();
            String operator = tokens[1].trim();
            String path = tokens[2].trim();
            if(command.equalsIgnoreCase("export") && operator.equals(">"))
            {
                RESULT_EXPORT_PATH = path;
                System.out.println("Output export path set to '" + path + "' for next query.");
            }
        }
        catch(Exception ex)
        {
            System.err.println(CommandLine.class.getName() + " Insufficient arguments!");
        }
    }

    private static void createConstraint(String line)
    {
        String[] tokens = line.split(":");
        String constraint_name = tokens[0].trim();
        String constraint_expression = tokens[1].trim();
        constraints.put(constraint_name, constraint_expression);
        System.out.println("Constraint '" + constraint_name + "' created.");
    }

    private static String parseQuery(String line)
    {
        int start_index = line.indexOf('(') + 1;
        int end_index = line.indexOf(')');
        String query = line.substring(start_index, end_index);
        for(Map.Entry<String, String> constraint: constraints.entrySet())
        {
            String constraint_name = constraint.getKey();
            String constraint_expression = constraint.getValue();
            if(query.contains(constraint_name))
            {
                query = query.replaceAll("\\b" + Pattern.quote(constraint_name) + "\\b", constraint_expression);
            }
        }

        return line.replace(line.substring(start_index, end_index), query);
    }
}
