/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2012 SRI International

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

 /* This works across *nix and runs the SPADE kernel as a daemon.
  * 'bin/spade' is a bash script that invokes this daemon.
  */

package spade.utility;

import com.sun.akuma.CLibrary;
import com.sun.akuma.Daemon;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Daemonizer {
    
    private static final int SHUTDOWN_POLLING_DELAY = 1000; // Milliseconds to wait before checking if process has terminated
    
    private String pidFile = spade.core.Kernel.getPidFileName();

    public Daemonizer() {
    }

    public boolean pidFileExists() {
        File file = new File(pidFile);
        return file.exists() && !file.isDirectory() ;
    }

    public int getPidFromFile() throws IOException{
        if (pidFileExists()) { 
            String pidfromfile = "";
            pidfromfile = new String(Files.readAllBytes(Paths.get(pidFile))); 
            return Integer.parseInt(pidfromfile);
        }

        throw new IOException("PID file is missing");
    }

    public boolean isProcessRunning(int pid) throws IOException {
	String pidStr = Integer.toString (pid);
        String line = "";
        Process process = Runtime.getRuntime().exec("ps -ef"); // portable across *nix
        InputStream stream = process.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        while ((line = reader.readLine()) != null) {
            line = line.trim().replaceAll("\\s+", " ");
            if (pidStr.equals (line.split(" ")[1])) {
                return true;                
            }
        }
        return false;
    }

    public void startKernel() throws Exception{

        Daemon daemon = new Daemon.WithoutChdir();
        if(daemon.isDaemonized()) {
            System.out.println("Starting SPADE as a daemon with PID: " + CLibrary.LIBC.getpid() );
            daemon.init(pidFile);
        } else {
            daemon.daemonize();
            System.exit(0);
        }
        spade.core.Kernel.main(new String[0]);
    }

    public void start() {
        int pidfromfile = 0;
        boolean processRunning = false;

        if (pidFileExists()) {
            
            try {
                pidfromfile = getPidFromFile();
            } catch (IOException exception) {
                System.err.println("PID file exists, but is not readable.");
                System.exit(1);
            }

            try {
                processRunning = isProcessRunning(pidfromfile);
            } catch (IOException exception) {
                System.err.println("Can not determine if SPADE is running.");
                System.exit(1);
            }

            if (processRunning) {
                System.err.println("SPADE is already running with PID " + pidfromfile);
                System.exit(1);
            } else {
                System.err.println("SPADE is not running, but PID file exists. Deleting it.");
            }
        }

        try{
            startKernel();
        } catch (Exception exception) {
            System.err.println("Error daemonizing SPADE: " + exception.getMessage());
            System.exit(1);            
        }

    }

    public void stop(int signum) {
        int pidFromFile = 0;
        boolean processRunning = false;

        if (pidFileExists()) {
            
            try {
                pidFromFile = getPidFromFile();
            } catch (IOException exception) {
                System.err.println("PID file exists, but is not readable.");
                System.exit(1);
            }

            try {
                processRunning = isProcessRunning(pidFromFile);
            } catch (IOException exception) {
                System.err.println("Can not determine if SPADE is running.");
                System.exit(1);
            }

            if (processRunning) {
                // System.err.println("Sending SPADE (Process ID: " + pidfromfile + ") " + ((signum == 2)?"SIGINT":((signum == 9)?"SIGKILL":"SIGNUM "+signum)) );
                if (signum == 15) {
                    System.err.println("SPADE daemon will stop after buffers clear.");
                } else if (signum == 9) {
                    System.err.println("Stopping SPADE daemon immediately");
                }
                if (CLibrary.LIBC.kill(pidFromFile, signum) != 0 ) {
                    System.err.println("SPADE process could not be killed.");
                }
            } else {
                System.err.println("SPADE is not running, but PID file exists.");
            }

            try {
                while (isProcessRunning(pidFromFile)) {
                    Thread.sleep(SHUTDOWN_POLLING_DELAY);
                }
            } catch (Exception exception) {

            }

        } else {
            System.err.println("PID file does not exist.");
            System.exit(1);
        }
    }


    public static void main(String[] arguments) {
        Daemonizer daemonizer = new Daemonizer();
        if (arguments.length == 0) {
            System.out.println("args: java-start | java-stop | java-kill | -h");
        }
        if (arguments.length == 1) {
            if (arguments[0].equals("java-start")) {
                daemonizer.start();
            } 
            if(arguments[0].equals("java-stop")) {
                daemonizer.stop(15); // SIGTERM
            }
            if(arguments[0].equals("java-kill")) {
                daemonizer.stop(9); // SIGKILL
            }
            if (arguments[0].equals("-h")) {
                System.out.println("args: start | stop | -h");
            }
        }
    }
}
