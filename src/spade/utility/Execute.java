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

public class Execute {
	
	private static Logger logger = Logger.getLogger(Execute.class.getName());
	
	private static final String PREFIX_STDOUT = "[STDOUT]\t",
			PREFIX_STDERR = "[STDERR]\t";

	public static List<String> getOutput(final String command) throws Exception{
		
		Process process = Runtime.getRuntime().exec(command);
		
		final BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
		final BufferedReader stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
		final List<String> lines = new ArrayList<String>();
		
		Thread stdoutThread = new Thread(new Runnable(){
			public void run(){
				try{
					String line = null;
					while((line = stdoutReader.readLine()) != null){
						lines.add(PREFIX_STDOUT + line);
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
						lines.add(PREFIX_STDERR + line);
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
		
		return lines;
	}
	
	/**
	 * Used to tell if the output of a command gotten from Execute.getOutput function has errors or not
	 * 
	 * @param outputLines output lines received from Execute.getOutput
	 * @return true if errors exist, otherwise false
	 */
	public static boolean containsOutputFromStderr(List<String> lines){
		if(lines != null){
			for(String line : lines){
				if(line != null){
					if(line.startsWith(PREFIX_STDERR)){
						return true;
					}
				}
			}
		}
		return false;
	}
	
	/**
	 * Strips STDOUT and STDERR headers from the lines added by getOutput function
	 * 
	 * @param lines output of getOutput function
	 */
	public static void stripLineHeaders(List<String> lines){
		if(lines != null){
			int stderrPrefixLen = PREFIX_STDERR.length();
			int stdoutPrefixLen = PREFIX_STDOUT.length();
			List<String> strippedLines = new ArrayList<String>();
			for(String line : lines){
				if(line != null){
					if(line.startsWith(PREFIX_STDERR)){
						line = line.substring(stderrPrefixLen);
					}else if(line.startsWith(PREFIX_STDOUT)){
						line = line.substring(stdoutPrefixLen);
					}
				}
				strippedLines.add(line);
			}
			lines.clear();
			lines.addAll(strippedLines);
		}
	}

}
