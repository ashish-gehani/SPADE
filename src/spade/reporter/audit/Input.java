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

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;

import spade.core.Settings;
import spade.utility.FileUtility;
import spade.utility.HelperFunctions;
import spade.utility.Result;

public class Input{

	public static enum Mode{
		LIVE, FILE, DIRECTORY
	}

	private final static String keyInputLog = "inputLog",
			keyInputLogRotate = "rotate",
			keyInputDir = "inputDir",
			keyInputDirTime = "inputTime",
			keySPADEAuditBridge = "spadeAuditBridge",
			keyLinuxAuditSocket = "linuxAuditSocket",
			keyWaitForLog = "waitForLog";

	private final static String inputDirTimeFormat = "yyyy-MM-dd:HH:mm:ss";
	
	private String spadeAuditBridgePath;
	private String spadeAuditBridgeName;
	private Mode mode;
	private String linuxAuditSocketPath;
	private String inputLog;
	private boolean inputLogRotate;
	private List<String> inputLogList;
	private String inputLogListFile;
	private String inputDir;
	private String inputDirTime;
	private boolean waitForLog;
	
	private Input(String spadeAuditBridgePath, Mode mode, 
			String linuxAuditSocketPath,
			String inputLog, boolean inputLogRotate, List<String> inputLogList, String inputLogListFile, 
			String inputDir, String inputDirTime, boolean waitForLog){
		this.spadeAuditBridgePath = spadeAuditBridgePath;
		final String spadeAuditBridgePathTokens[] = spadeAuditBridgePath.split(File.separator);
		this.spadeAuditBridgeName = spadeAuditBridgePathTokens[spadeAuditBridgePathTokens.length - 1].trim();
		
		this.mode = mode;
		this.linuxAuditSocketPath = linuxAuditSocketPath;
		this.inputLog = inputLog;
		this.inputLogRotate = inputLogRotate;
		this.inputLogList = inputLogList;
		this.inputLogListFile = inputLogListFile;
		this.inputDir = inputDir;
		this.inputDirTime = inputDirTime;
		this.waitForLog = waitForLog;
	}

	public String getSPADEAuditBridgePath(){
		return spadeAuditBridgePath;
	}
	
	public String getSPADEAuditBridgeName(){
		return spadeAuditBridgeName;
	}
	
	public String getLinuxAuditSocketPath(){
		return linuxAuditSocketPath;
	}
	
	public Mode getMode(){
		return mode;
	}

	public String getInputLog(){
		return inputLog;
	}

	public boolean isInputLogRotate(){
		return inputLogRotate;
	}

	public List<String> getInputLogList(){
		return inputLogList;
	}

	public String getInputLogListFile(){
		return inputLogListFile;
	}
	
	public void deleteInputLogListFile() throws Exception{
		final String path = getInputLogListFile();
		if(path != null){
			try{
				final File file = new File(path);
				if(file.exists() && file.isFile()){
					file.delete();
				}
			}catch(Exception e){
				throw e;
			}
		}
	}

	public String getInputDir(){
		return inputDir;
	}

	public String getInputDirTime(){
		return inputDirTime;
	}
	
	public boolean isInputDirTimeSpecified(){
		return inputDirTime != null;
	}
	
	public boolean isLiveMode(){
		return mode == Mode.LIVE;
	}
	
	public boolean isWaitForLog(){
		return waitForLog;
	}

	@Override
	public String toString(){
		return "Input [spadeAuditBridgePath=" + spadeAuditBridgePath + ", spadeAuditBridgeName=" + spadeAuditBridgeName
				+ ", mode=" + mode + ", linuxAuditSocketPath=" + linuxAuditSocketPath
				+ ", inputLog=" + inputLog + ", inputLogRotate=" + inputLogRotate + ", inputLogList=" + inputLogList
				+ ", inputLogListFile=" + inputLogListFile + ", inputDir=" + inputDir + ", inputDirTime=" + inputDirTime
				+ ", waitForLog=" + waitForLog + "]";
	}

	private static List<String> getListOfRotatedAuditLogs(final String inputLog, final boolean rotate) throws Exception{
		// Build a list of audit log files to be read
		final LinkedList<String> paths = new LinkedList<String>();
		paths.addFirst(inputLog); //add the file in the argument
		// If rotate is true then add the rest too based on the decided convention
		if(rotate){
			/*
			 * Convention: name format of files to be processed -> name.1, name.2 and so on, where
			 * 		the name is the name of the file passed in the argument and can only process 99 logs.
			 */
			for(int logCount = 1; logCount<=99; logCount++){
				final String logPath = inputLog + "." + logCount;
				try{
					final File logFile = new File(logPath);
					if(logFile.exists() && logFile.isFile()){
						if(!logFile.canRead()){
							throw new Exception("Exists but not readable");
						}
						paths.addFirst(logPath);
					}
				}catch(Exception e){
					throw new Exception("Failed to check if rotated input log is a readable file: " + logPath, e);
				}
			}
		}
		return paths;
	}
	
	/**
	 * Creates a temporary file which contains the list of input log files
	 * 
	 * Returns the path of the temporary file is that is created successfully
	 * 
	 * @param inputLogList list of input log files (in the defined order)
	 * @return path of the temporary file which contains the list of audit logs
	 */
	private static String createLogListFile(final List<String> inputLogList) throws Exception{
		final String tempFileName = "audit." + System.nanoTime();
		final String tempFilePath = Settings.getPathRelativeToTemporaryDirectory(tempFileName);
		try{
			final File tempFile = new File(tempFilePath);
			if(!tempFile.createNewFile()){
				throw new Exception("Already exists. Failed to overwrite.");
			}else{
				FileUtils.writeLines(tempFile, inputLogList);
				return tempFilePath;
			}
		}catch(Exception e){
			throw new Exception("Failed to create input file-list file at path: '" + tempFilePath + "'", e);
		}
	}
	
	public static Input instance(final Map<String, String> map) throws Exception{
		final String spadeAuditBridgePath;
		final Mode mode;
		final String linuxAuditSocketPath;
		final String inputLog;
		final boolean inputLogRotate;
		final List<String> inputLogList;
		final String inputLogListFile;
		final String inputDir;
		final String inputDirTime;
		final boolean waitForLog;
		
		final String valueSpadeAuditBridgePath = map.get(keySPADEAuditBridge);
		if(HelperFunctions.isNullOrEmpty(valueSpadeAuditBridgePath)){
			throw new Exception("NULL/Empty value for SPADE audit bridge path specified by '" + keySPADEAuditBridge + "'");
		}
		final String resolvedSpadeAuditBridgePath = Settings.getPathRelativeToSPADERootIfNotAbsolute(valueSpadeAuditBridgePath);
		try{
			FileUtility.pathMustBeAReadableExecutableFile(resolvedSpadeAuditBridgePath);
		}catch(Exception e){
			throw new Exception("Invalid path for SPADE audit bridge specified by '" + keySPADEAuditBridge + "': '" + resolvedSpadeAuditBridgePath + "'", e);
		}
		spadeAuditBridgePath = resolvedSpadeAuditBridgePath;
		
		final String valueInputLog = map.get(keyInputLog);
		if(valueInputLog != null){
			final String resolvedInputLog = Settings.getPathRelativeToSPADERootIfNotAbsolute(valueInputLog);
			try{
				FileUtility.pathMustBeAReadableFile(resolvedInputLog);
			}catch(Exception e){
				throw new Exception("Invalid input log path specified by '" + keyInputLog + "': '" + resolvedInputLog + "'", e);
			}
			final String valueInputLogRotate = map.get(keyInputLogRotate);
			final Result<Boolean> resultInputLogRotate = HelperFunctions.parseBoolean(valueInputLogRotate);
			if(resultInputLogRotate.error){
				throw new Exception("Invalid input log rotate specified by '" + keyInputLogRotate + "'. Error: " + resultInputLogRotate.toErrorString());
			}

			mode = Mode.FILE;
			linuxAuditSocketPath = null;
			inputLog = resolvedInputLog;
			inputLogRotate = resultInputLogRotate.result;
			inputLogList = getListOfRotatedAuditLogs(resolvedInputLog, resultInputLogRotate.result);
			inputLogListFile = createLogListFile(inputLogList);
			inputDir = null;
			inputDirTime = null;
		}else{
			final String valueInputDir = map.get(keyInputDir);
			if(valueInputDir != null){
				final String resolvedInputDir = Settings.getPathRelativeToSPADERootIfNotAbsolute(valueInputDir);
				try{
					FileUtility.pathMustBeAReadableDirectory(resolvedInputDir);
				}catch(Exception e){
					throw new Exception("Invalid input log directory specified by '" + keyInputDir + "': '" + resolvedInputDir + "'", e);
				}
				
				final String valueInputDirTime = map.get(keyInputDirTime);
				if(valueInputDirTime != null){
					try{
						final SimpleDateFormat dateFormat = new SimpleDateFormat(inputDirTimeFormat);
						dateFormat.parse(valueInputDirTime);
						// parsed successfully
					}catch(Exception e){
						throw new Exception(
								"Invalid input dir time value specified by '" + keyInputDirTime + "'"
										+ ". Must be in format '" + inputDirTimeFormat + "' but is '" + valueInputDirTime + "'", e);
					}
				}
				
				mode = Mode.DIRECTORY;
				linuxAuditSocketPath = null;
				inputLog = null;
				inputLogRotate = false;
				inputLogList = null;
				inputLogListFile = null;
				inputDir = resolvedInputDir;
				inputDirTime = valueInputDirTime;
			}else{
				final String valueLinuxAuditSocketPath = map.get(keyLinuxAuditSocket);
				if(HelperFunctions.isNullOrEmpty(valueLinuxAuditSocketPath)){
					throw new Exception("NULL/Empty value for Linux audit socket path specified by '" + keyLinuxAuditSocket + "'");
				}
				final String resolvedLinuxAuditSocketPath = Settings.getPathRelativeToSPADERootIfNotAbsolute(valueLinuxAuditSocketPath);
				try{
					final File resolvedLinuxAuditSocketFile = new File(resolvedLinuxAuditSocketPath);
					if(!resolvedLinuxAuditSocketFile.exists()){
						throw new Exception("Does not exist");
					}
				}catch(Exception e){
					throw new Exception("Invalid path for Linux audit socket path specified by '" + keyLinuxAuditSocket + "': '" + resolvedLinuxAuditSocketPath + "'", e);
				}
				
				mode = Mode.LIVE;
				linuxAuditSocketPath = resolvedLinuxAuditSocketPath;
				inputLog = null;
				inputLogRotate = false;
				inputLogList = null;
				inputLogListFile = null;
				inputDir = null;
				inputDirTime = null;
			}
		}
		
		if(mode == Mode.FILE || mode == Mode.DIRECTORY){
			final String valueWaitForLog = map.get(keyWaitForLog);
			final Result<Boolean> resultWaitForLog = HelperFunctions.parseBoolean(valueWaitForLog);
			if(resultWaitForLog.error){
				throw new Exception("Invalid wait for log value specified by '" + keyWaitForLog + "'. Error: " + resultWaitForLog.toErrorString());
			}
			waitForLog = resultWaitForLog.result;
		}else{
			waitForLog = false;
		}
		
		return new Input(spadeAuditBridgePath, mode, 
				linuxAuditSocketPath, 
				inputLog, inputLogRotate, inputLogList, inputLogListFile, inputDir, inputDirTime, waitForLog);
	}
}
