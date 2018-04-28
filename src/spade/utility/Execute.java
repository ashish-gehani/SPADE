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

package spade.utility;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Convenience class for executing a given command.
 */
public class Execute{
	
	private static Logger logger = Logger.getLogger(Execute.class.getName());
	
	public static Output getOutput(final String command) throws Exception{
		
		final Output output = new Output(command);
		
		Process process = Runtime.getRuntime().exec(command);
		
		final BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
		final BufferedReader stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
		
		Thread stdoutThread = new Thread(new Runnable(){
			public void run(){
				try{
					String line = null;
					while((line = stdoutReader.readLine()) != null){
						output.stdOut.add(line);
					}
				}catch(Exception e){
					logger.log(Level.WARNING, "Error reading STDOUT for command: " +command, e);
				}
			}
		});
		
		Thread stderrThread = new Thread(new Runnable(){
			public void run(){
				try{
					String line = null;
					while((line = stderrReader.readLine()) != null){
						output.stdErr.add(line);
					}
				}catch(Exception e){
					logger.log(Level.WARNING, "Error reading STDERR for command: " +command, e);
				}
			}
		});
		
		stdoutThread.start();
		stderrThread.start();
		
		process.waitFor();
		
		stdoutThread.join();
		stderrThread.join();
		
		stderrReader.close();
		stdoutReader.close();
		
		output.exitValue = process.exitValue();
		
		return output;
	}
	
	/**
	 * Class that contains the output of the executed command.
	 */
	public static class Output{
		/**
		 * Execute command
		 */
		private String command;
		/**
		 * Exit value of the process as returned by the JVM Process class.
		 */
		private int exitValue;
		/**
		 * Standard out and standard error lines
		 */
		private List<String> stdOut = new ArrayList<String>(), 
				stdErr = new ArrayList<String>();
		
		private Output(String command){
			this.command = command;
		}
		/**
		 * Not necessary that a negative value indicates error.
		 * Needs to be checked for every command that the following condition is true.
		 * 
		 * Note: Can't also use size of standard error because it can contain warnings
		 * @return true (if exitValue >= 0) else false
		 */
		public boolean hasError(){
			return exitValue < 0 || !stdErr.isEmpty();
		}
		public List<String> getStdOut(){
			return stdOut;
		}
		public List<String> getStdErr(){
			return stdErr;
		}
		public void log(){
			if(hasError()){
				logger.log(Level.SEVERE, "Command \"{0}\" failed with error: {1}.", new Object[]{
						command, stdErr});
			}else{
				logger.log(Level.INFO, "Command \"{0}\" succeeded with output: {1}.", new Object[]{
						command, stdOut});
			}
		}
	}
}
