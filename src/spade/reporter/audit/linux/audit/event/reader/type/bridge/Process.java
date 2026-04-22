/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2026 SRI International

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
package spade.reporter.audit.linux.audit.event.reader.type.bridge;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.utility.HelperFunctions;

public class Process{

	private static final Logger logger = Logger.getLogger(Process.class.getName());

	private final ProcessConfig processConfig;

	private String command;
	private java.lang.Process process;
	private String pid;
	private BufferedReader stdErrReader;

	public Process(final ProcessConfig processConfig){
		if(processConfig == null){
			throw new IllegalArgumentException("ProcessConfig cannot be NULL");
		}
		this.processConfig = processConfig;
	}

	public void start() throws Exception{
		final String argStr;
		try{
			argStr = processConfig.getArgAsStr();
		}catch(Exception e){
			throw new Exception("Failed to build SPADE audit bridge arguments", e);
		}

		command = processConfig.getBridgePath() + " " + argStr;
		try{
			process = Runtime.getRuntime().exec(command);
		}catch(Exception e){
			throw new Exception("Failed to start SPADE audit bridge process using command: '" + command + "'", e);
		}

		try{
			stdErrReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
			final String line = readStdErrLine(); // First line is always the pid control message
			if(line != null && line.startsWith("#CONTROL_MSG#")){
				try{
					setPid(line.split("=")[1]);
				}catch(Exception e){
					throw new Exception(
							"Malformed control message received from SPADE audit bridge: '" + line + "'", e);
				}
			}else{
				throw new Exception("Unexpected control message (expected: '#CONTROL_MSG#pid=<num>') in STDERR of SPADE audit bridge: '" + line + "'");
			}
			startStdErrLogger();
		}catch(Exception e){
			kill();
			throw new Exception("Failed to initialize SPADE audit bridge process error/control stream. Process killed if started.", e);
		}
	}

	private void startStdErrLogger(){
		final Thread t = new Thread(new Runnable(){
			@Override
			public void run(){
				while(isRunning()){
					final String msg;
					try{
						msg = readStdErrLine();
					}catch(Exception e){
						logger.log(Level.SEVERE, "[SPADE audit bridge] [ERROR] " + "Unexpected error", e);
						logger.log(Level.INFO, "[SPADE audit bridge] [OUTPUT] " + "Exiting error thread");
						break;
					}
					if(msg != null){
						logger.log(Level.INFO, "[SPADE audit bridge] [OUTPUT] " + msg);
					}else{
						logger.log(Level.INFO, "[SPADE audit bridge] [OUTPUT] " + "Exiting error thread");
						break;
					}
				}
			}
		}, "SPADEAuditBridge-stderr-logger");
		t.setDaemon(true);
		t.start();
	}

	private void setPid(final String pid){
		if(HelperFunctions.isNullOrEmpty(pid)){
			this.pid = null;
		}else{
			this.pid = pid;
		}
	}

	public String getPid(){
		return pid;
	}

	public String getCommand(){
		return command;
	}

	public InputStream getStdOutStream() throws Exception{
		if(!isRunning()){
			throw new Exception("SPADE audit bridge process is not alive");
		}
		return process.getInputStream();
	}

	private String readStdErrLine() throws Exception{
		if (stdErrReader == null) {
			return null;
		}
		try {
			return stdErrReader.readLine();
		} catch (Exception e) {
			throw new Exception("Failed to read STDERR of SPADE audit bridge", e);
		}
	}

	public boolean isRunning(){
		return process != null && process.isAlive();
	}

	public void stop() throws Exception{
		if(pid != null && isRunning()){
			try{
				HelperFunctions.nixSendSignalToPid(pid, 2);
				pid = null;
			}catch(Exception e){
				throw new Exception("Failed to stop SPADE audit bridge with pid '" + pid + "' using command '" + command + "'", e);
			}
		}else if(isRunning()){
			try{
				process.destroy();
			}catch(Exception e){
				throw new Exception("Failed to stop SPADE audit bridge using JAVA API", e);
			}
		}
	}

	public void kill() throws Exception{
		if(pid != null && isRunning()){
			try{
				HelperFunctions.nixSendSignalToPid(pid, 9);
				pid = null;
			}catch(Exception e){
				throw new Exception("Failed to kill SPADE audit bridge with pid '" + pid + "' using command '" + command + "'", e);
			}
		}else if(isRunning()){
			try{
				process.destroyForcibly();
			}catch(Exception e){
				throw new Exception("Failed to kill SPADE audit bridge using JAVA API", e);
			}
		}
	}

	public void close() throws Exception{
		try{
			if(stdErrReader != null){
				stdErrReader.close();
			}
		}catch(Exception e){
			throw new Exception("Failed to close STDERR reader of SPADE audit bridge", e);
		}finally{
			stdErrReader = null;
		}
	}
}
