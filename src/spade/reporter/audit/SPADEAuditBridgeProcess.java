/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2021 SRI International

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
package spade.reporter.audit;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.function.BiConsumer;

import spade.utility.HelperFunctions;

public class SPADEAuditBridgeProcess{
	
	private static String buildArguments(
			final Input input,
			final boolean units, final Integer mergeUnit) throws Exception{
		final Input.Mode mode = input.getMode();
		
		String arguments = "";

		if(mode == Input.Mode.FILE){
			arguments += " -f " + input.getInputLogListFile();
		}else if(mode == Input.Mode.DIRECTORY){
			arguments += " -d " + input.getInputDir();
			if(input.isInputDirTimeSpecified()){
				arguments += " -t " + input.getInputDirTime();
			}
		}else if(mode == Input.Mode.LIVE){
			arguments += " -s " + input.getLinuxAuditSocketPath();
		}else{
			throw new Exception("Unexpected input mode: " + mode);
		}

		if(mode == Input.Mode.FILE || mode == Input.Mode.DIRECTORY){
			if(input.isWaitForLog()){
				arguments += " -w";
			}
		}

		if(units){
			arguments += " -u";
			if(mergeUnit != null && mergeUnit > 0){
				arguments += " -m " + mergeUnit;
			}
		}
		return arguments;
	}

	public static SPADEAuditBridgeProcess launch(final Input input, final boolean units, final Integer mergeUnit
			) throws Exception{

		final String arguments;
		try{
			arguments = buildArguments(input, units, mergeUnit);
		}catch(Exception e){
			throw new Exception("Failed to build SPADE audit bridge arguments", e);
		}

		String command = null;
		final SPADEAuditBridgeProcess process;
		try{
			command = input.getSPADEAuditBridgePath() + " " + arguments;
			final Process p = Runtime.getRuntime().exec(command);
			process = new SPADEAuditBridgeProcess(command, p);
		}catch(Exception e){
			throw new Exception("Failed to start SPADE audit bridge process using command: '" + command + "'", e);
		}

		try{
			process.initErrorControlStream();

			final String line = process.readStdErrLine(); // Read the first line which should always be the pid control message
			if(line != null && line.startsWith("#CONTROL_MSG#")){
				try{
					process.setPid(line.split("=")[1]);
				}catch(Exception e){
					throw new Exception(
							"Malformed control message received from SPADE audit bridge: '" + line + "'", e);
				}
			}else{
				throw new Exception("Unexpected control message (expected: '#CONTROL_MSG#pid=<num>') in STDERR of SPADE audit bridge: '" + line + "'");
			}
		}catch(Exception initStreamException){
			try{
				process.stop(true);
			}catch(Exception stopException){
				// ignore
			}
			throw new Exception("Failed to initialize SPADE audit bridge process error/control stream. Process killed if started.",
					initStreamException);
		}

		return process;
	}

	////////////////////////////////////////////////////////////////

	private String command;
	private Process process;
	private String pid;
	private BufferedReader stdErrReader;

	private SPADEAuditBridgeProcess(final String command, final Process process) throws Exception{
		if(process == null){
			throw new Exception("NULL SPADE audit bridge process");
		}
		this.command = command;
		this.process = process;
	}

	private void initErrorControlStream() throws Exception{
		try{
			this.stdErrReader = new BufferedReader(new InputStreamReader(this.process.getErrorStream()));
		}catch(Exception e){
			throw new Exception("Failed to read STDERR of SPADE audit bridge", e);
		}
	}

	private void setPid(String pid){
		if(HelperFunctions.isNullOrEmpty(pid)){
			this.pid = null;
		}else{
			this.pid = pid;
		}
	}

	public String getPid(){
		return this.pid;
	}

	public String getCommand(){
		return command;
	}

	public String readStdErrLine() throws Exception{
		if(this.stdErrReader != null){
			try{
				return this.stdErrReader.readLine();
			}catch(Exception e){
				throw new Exception("Failed to read STDERR of SPADE audit bridge", e);
			}
		}else{
			return null;
		}
	}
	
	public void consumeStdErr(final BiConsumer<String, Exception> consumer) throws Exception{
		try{
			final Thread t = new Thread(new Runnable(){
				@Override
				public void run(){
					while(true){
						try{
							String line = readStdErrLine();
							try{
								consumer.accept(line, null);
							}catch(Exception e){
								// ignore
							}
							if(line == null){ // End of stream
								break;
							}
						}catch(Exception e){
							try{
								consumer.accept("Failed to read STDERR line from SPADE audit bridge", e);
							}catch(Exception ignoreE){
								// ignore
							}
							try{
								consumer.accept(null, null); // End of stream
							}catch(Exception ignoreE){
								// ignore
							}
							break;
						}
					}
				}
			}, "SPADEAuditBridge-stderr-reader");
			t.start();
		}catch(Exception e){
			throw new Exception("Failed to start STDERR reader thread for SPADE audit bridge", e);
		}
	}

	public InputStream getStdOutStream() throws Exception{
		if(isRunning()){
			return this.process.getInputStream();	
		}else{
			throw new Exception("SPADE audit bridge process is not alive");
		}
		
	}

	public void close() throws Exception{
		try{
			if(this.stdErrReader != null){
				this.stdErrReader.close();
			}
			this.stdErrReader = null;
		}catch(Exception e){
			throw new Exception("Failed to close STDERR reader of SPADE audit bridge", e);
		}
	}

	public void stop(final boolean force) throws Exception{
		if(this.pid != null && isRunning()){
			final int signal = ((force) ? (9) : (2));
			try{
				HelperFunctions.nixSendSignalToPid(pid, signal);
				this.pid = null;
			}catch(Exception e){
				throw new Exception("Failed to stop " + (force == false ? "" : "(forcefully) ")
						+ "SPADE audit bridge with pid '" + this.pid + "' using command '" + command + "'", e);
			}
		}else{
			if(isRunning()){
				try{
					if(force == false){
						this.process.destroy();
					}else{
						this.process.destroyForcibly();
					}
				}catch(Exception e){
					throw new Exception("Failed to stop " + (force == false ? "" : "(forcefully) ")
							+ "SPADE audit bridge using JAVA API", e);
				}
			}
		}
	}

	public boolean isRunning() throws Exception{
		return this.process != null && this.process.isAlive();
	}
}
