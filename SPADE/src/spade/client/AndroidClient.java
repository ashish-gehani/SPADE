/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2011 SRI International

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

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import spade.core.Kernel;
import spade.reporter.AndroidAudit;
import spade.vertex.opm.Process;

public class AndroidClient {

    private static PrintStream outputStream;
    private static PrintStream errorStream;
    private static PrintStream SPADEControlIn;
    private static BufferedReader SPADEControlOut;
    private static BufferedReader commandReader = new BufferedReader(new InputStreamReader(System.in));
    private static volatile boolean shutdown;
    private static final String COMMAND_PROMPT = "-> ";
    private static final int THREAD_SLEEP_DELAY = 10;
    private final String simpleDatePattern = "EEE MMM d H:mm:ss yyyy";

    public static void main(String args[]) {

        outputStream = System.out;
        errorStream = System.err;

        shutdown = false;

        Runnable outputReader = new Runnable() {

            public void run() {
                try {
                    while (!shutdown) {
                        if (SPADEControlOut.ready()) {
                            // This thread keeps reading from the output pipe and
                            // printing to the current output stream.
                            String outputLine = SPADEControlOut.readLine();
                            if (outputLine != null) {
                                outputStream.println(outputLine);
                            }
                            if (outputLine.equals("")) {
                                outputStream.print(COMMAND_PROMPT);
                            }
                        }
                        Thread.sleep(THREAD_SLEEP_DELAY);
                    }
                    SPADEControlOut.close();
                } catch (Exception exception) {
                    System.out.println("Error connecting to SPADE");
                    exception.printStackTrace(errorStream);
                    System.exit(-1);
                }
            }
        };

        try {
            SocketAddress sockaddr = new InetSocketAddress("localhost", Kernel.LOCAL_CONTROL_PORT);
            Socket remoteSocket = new Socket();
            remoteSocket.connect(sockaddr, Kernel.CONNECTION_TIMEOUT);
            OutputStream outStream = remoteSocket.getOutputStream();
            InputStream inStream = remoteSocket.getInputStream();
            SPADEControlOut = new BufferedReader(new InputStreamReader(inStream));
            SPADEControlIn = new PrintStream(outStream);
            new Thread(outputReader).start();

            outputStream.println("");
            outputStream.println("SPADE 2.0 Control Client for Android");
            outputStream.println("");

            SPADEControlIn.println("");
            while (true) {
                String line = commandReader.readLine();
                if (line.split("\\s")[0].equalsIgnoreCase("query")) {
                    // Do not allow query commands from this control shell.
                    SPADEControlIn.println("");
                } else if (line.equalsIgnoreCase("exit")) {
                    shutdown = true;
                    SPADEControlIn.println("exit");
                    SPADEControlIn.close();
                    break;
                } else if (line.equalsIgnoreCase("shutdown")) {
                    shutdown = true;
                    SPADEControlIn.println("shutdown");
                    SPADEControlIn.close();
                    break;
                } else {
                    SPADEControlIn.println(line);
                }
            }
        } catch (Exception exception) {
            exception.printStackTrace(errorStream);
        }
    }

    protected Process createProgramVertex(String pid) {
        // The process vertex is created using the proc filesystem.
        Process resultVertex = new Process();
        try {
            BufferedReader procReader = new BufferedReader(new FileReader("/proc/" + pid + "/status"));
            String nameline = procReader.readLine();
            procReader.readLine();
            String tgidline = procReader.readLine();
            procReader.readLine();
            String ppidline = procReader.readLine();
            String tracerpidline = procReader.readLine();
            String uidline = procReader.readLine();
            String gidline = procReader.readLine();
            procReader.close();

            BufferedReader statReader = new BufferedReader(new FileReader("/proc/" + pid + "/stat"));
            String statline = statReader.readLine();
            statReader.close();

            BufferedReader cmdlineReader = new BufferedReader(new FileReader("/proc/" + pid + "/cmdline"));
            String cmdline = cmdlineReader.readLine();
            cmdlineReader.close();
            if (cmdline == null) {
                cmdline = "";
            } else {
                cmdline = cmdline.replace("\0", " ");
                cmdline = cmdline.replace("\"", "'");
            }

            String stats[] = statline.split("\\s+");
            long elapsedtime = Long.parseLong(stats[21]) * 10;
            long starttime = 0 + elapsedtime;
            String stime_readable = new java.text.SimpleDateFormat(simpleDatePattern).format(new java.util.Date(starttime));
            String stime = Long.toString(starttime);

            StringTokenizer st1 = new StringTokenizer(nameline);
            st1.nextToken();
            String name = st1.nextToken();

            StringTokenizer st3 = new StringTokenizer(ppidline);
            st3.nextToken();
            String ppid = st3.nextToken("").trim();

            StringTokenizer st5 = new StringTokenizer(uidline);
            st5.nextToken();
            String uid = st5.nextToken().trim();

            StringTokenizer st6 = new StringTokenizer(gidline);
            st6.nextToken();
            String gid = st6.nextToken().trim();

            resultVertex.addAnnotation("pidname", name);
            resultVertex.addAnnotation("pid", pid);
            resultVertex.addAnnotation("ppid", ppid);
            resultVertex.addAnnotation("uid", uid);
            resultVertex.addAnnotation("gid", gid);
            resultVertex.addAnnotation("starttime_unix", stime);
            resultVertex.addAnnotation("starttime_simple", stime_readable);
            resultVertex.addAnnotation("group", stats[4]);
            resultVertex.addAnnotation("sessionid", stats[5]);
            resultVertex.addAnnotation("commandline", cmdline);
        } catch (Exception exception) {
            Logger.getLogger(AndroidAudit.class.getName()).log(Level.SEVERE, null, exception);
            return null;
        }

//        try {
//            BufferedReader environReader = new BufferedReader(new FileReader("/proc/" + pid + "/environ"));
//            String environ = environReader.readLine();
//            environReader.close();
//            if (environ != null) {
//                environ = environ.replace("\0", ", ");
//                environ = environ.replace("\"", "'");
//                resultVertex.addAnnotation("environment", environ);
//            }
//        } catch (Exception exception) {
//            // Unable to access the environment variables
//        }
        return resultVertex;
    }
}
