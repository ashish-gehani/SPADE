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
package spade.core;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import spade.utility.FileUtility;
import spade.utility.HelperFunctions;
import spade.utility.Result;

/**
 *
 * @author Dawood Tariq and Raza Ahmad
 */
public class Settings{
	
	private static final Logger logger = Logger.getLogger(Settings.class.getName());

	private static final String settingsFile = ("cfg" + File.separator + "spade.core.Kernel.config"); // Relative to the current working directory of the process
	
	private static final String keySPADERoot = "spade_root",
			keyLocalControlPort = "local_control_port",
			keyCommandLineQueryPort = "commandline_query_port",
			keyRemoteSketchPort = "remote_sketch_port",
			keyConnectionTimeout = "connection_timeout",
			keySourceReporter = "source_reporter",
			keyLoggerLevel = "logger_level",
			keyTemporaryDirectoryPath = "temp_directory",
			keyLogDirectoryPath = "log_directory",
			keyLogFileNamePattern = "log_filename_pattern",
			keyDatabaseDirectoryPath = "database_directory",
			keyConfigDirectoryPath = "config_directory",
			keyLibraryDirectoryPath = "library_directory",
			keyQueryHistoryFile = "query_history_file",
			keyControlHistoryFile = "control_history_file",
			keySPADEHostFile = "spade_host_file",
			keySPADEPidFile = "spade_pid_file",
			keyServerPublicKeystore = "server_public_keystore",
			keyServerPrivateKeystore = "server_private_keystore",
			keyClientPublicKeystore = "client_public_keystore",
			keyClientPrivateKeystore = "client_private_keystore",
			keyPasswordPublicKeystore = "password_public_keystore",
			keyPasswordPrivateKeystore = "password_private_keystore";

	private boolean loaded = false;
	private boolean loggingInitialized = false;
	private String spadeRoot;
	private int localControlPort;
	private int commandLineQueryPort;
	private int remoteSketchPort;
	private int connectionTimeoutMillis;
	private String sourceReporter;
	private Level loggerLevel;
	private String temporaryDirectory;
	private String logDirectory;
	private String logFileNamePattern;
	private String databaseDirectory;
	private String configDirectory;
	private String libraryDirectory;
	private String queryHistoryFile;
	private String controlHistoryFile;
	private String spadeHostFile;
	private String spadePidFile;
	private String serverPublicKeystore;
	private String serverPrivateKeystore;
	private String clientPublicKeystore;
	private String clientPrivateKeystore;
	private String passwordPublicKeystore;
	private String passwordPrivateKeystore;
	
	private static final Settings instance = new Settings();
	
	static{
		try{
			load(settingsFile);
		}catch(Exception e){
			logger.log(Level.SEVERE, "SHUTTING DOWN! Failed to load settings file at path: '" + settingsFile + "'", e);
			System.exit(-1);
		}
	}
	
	public static void load(final String settingsFile) throws Exception{
		synchronized(instance){
			if(instance.loaded){
				return;
			}

			final Result<HashMap<String, String>> result = FileUtility.parseKeysValuesInConfigFile(settingsFile);
			if(result.error){
				throw new Exception(result.toErrorString());
			}
			
			final HashMap<String, String> map = new HashMap<String, String>();
			map.putAll(result.result);
			
			final String spadeRoot;
			final String valueSpadeRoot = map.get(keySPADERoot);
			try{
				if(valueSpadeRoot != null && valueSpadeRoot.trim().isEmpty()){
					spadeRoot = new File(valueSpadeRoot).getAbsolutePath();
					FileUtility.pathMustBeAReadableWritableDirectory(spadeRoot);
				}else{
					FileUtility.pathMustBeAReadableWritableDirectory(valueSpadeRoot);
					spadeRoot = new File(valueSpadeRoot).getAbsolutePath();
				}
			}catch(Exception e){
				throw new Exception("Invalid value for key '"+ keySPADERoot +"'", e);
			}
			
			final String valueLocalControlPort = map.get(keyLocalControlPort);
			final Result<Long> resultLocalControlPort = HelperFunctions.parseLong(valueLocalControlPort, 10, 1, Integer.MAX_VALUE);
			if(resultLocalControlPort.error){
				throw new Exception("Invalid value for key '"+ keyLocalControlPort +"'. " + resultLocalControlPort.toErrorString());
			}
			
			final String valueCommandLineQueryPort = map.get(keyCommandLineQueryPort);
			final Result<Long> resultCommandLineQueryPort = HelperFunctions.parseLong(valueCommandLineQueryPort, 10, 1, Integer.MAX_VALUE);
			if(resultCommandLineQueryPort.error){
				throw new Exception("Invalid value for key '"+ keyCommandLineQueryPort +"'. " + resultCommandLineQueryPort.toErrorString());
			}
			
			final String valueRemoteSketchPort = map.get(keyRemoteSketchPort);
			final Result<Long> resultRemoteSketchPort = HelperFunctions.parseLong(valueRemoteSketchPort, 10, 1, Integer.MAX_VALUE);
			if(resultRemoteSketchPort.error){
				throw new Exception("Invalid value for key '"+ keyRemoteSketchPort +"'. " + resultRemoteSketchPort.toErrorString());
			}
			
			final String valueConnectionTimeout = map.get(keyConnectionTimeout);
			final Result<Long> resultConnectionTimeout = HelperFunctions.parseLong(valueConnectionTimeout, 10, 1, Long.MAX_VALUE);
			if(resultConnectionTimeout.error){
				throw new Exception("Invalid value for key '"+ keyConnectionTimeout +"'. " + resultConnectionTimeout.toErrorString());
			}
			
			final String sourceReporter = map.get(keySourceReporter);
			if(HelperFunctions.isNullOrEmpty(sourceReporter)){
				throw new Exception("NULL/Empty value for key '" + keySourceReporter + "'");
			}
			
			final String valueLoggerLevel = map.get(keyLoggerLevel);
			if(HelperFunctions.isNullOrEmpty(valueLoggerLevel)){
				throw new Exception("NULL/Empty value for key '" + keyLoggerLevel + "'");
			}
			final Level loggerLevel;
			try{
				loggerLevel = Level.parse(valueLoggerLevel);
			}catch(Exception e){
				throw new Exception("Invalid value for key '"+ keyLoggerLevel +"'", e);
			}
			
			final String temporaryDirectory;
			final String valueTemporaryDirectory = map.get(keyTemporaryDirectoryPath);
			try{
				temporaryDirectory = getConcatenatedPathWithSPADERootIfNotAbsolute(spadeRoot, valueTemporaryDirectory);
				final File temporaryDirectoryFile = new File(temporaryDirectory);
				if(!temporaryDirectoryFile.exists()){
					temporaryDirectoryFile.mkdirs();
	        	}else{ // exists
	        		FileUtility.pathMustBeAReadableWritableDirectory(temporaryDirectory);
	        	}
			}catch(Exception e){
				throw new Exception("Invalid value for key '"+ keyTemporaryDirectoryPath +"'", e);
			}
			
			final String logDirectoryPath;
			final String valueLogDirectoryPath = map.get(keyLogDirectoryPath);
			try{
				logDirectoryPath = getConcatenatedPathWithSPADERootIfNotAbsolute(spadeRoot, valueLogDirectoryPath);
				final File logDirectory = new File(logDirectoryPath);
				if(!logDirectory.exists()){
	        		logDirectory.mkdirs();
	        	}else{ // exists
	        		FileUtility.pathMustBeAReadableWritableDirectory(logDirectoryPath);
	        	}
			}catch(Exception e){
				throw new Exception("Invalid value for key '"+ keyLogDirectoryPath +"'", e);
			}
			
			final String databaseDirectoryPath;
			final String valueDatabaseDirectoryPath = map.get(keyDatabaseDirectoryPath);
			try{
				databaseDirectoryPath = getConcatenatedPathWithSPADERootIfNotAbsolute(spadeRoot, valueDatabaseDirectoryPath);
				final File databaseDirectory = new File(databaseDirectoryPath);
				if(!databaseDirectory.exists()){
					databaseDirectory.mkdirs();
	        	}else{ // exists
	        		FileUtility.pathMustBeAReadableWritableDirectory(databaseDirectoryPath);
	        	}
			}catch(Exception e){
				throw new Exception("Invalid value for key '"+ keyDatabaseDirectoryPath +"'", e);
			}
			
			final String configDirectoryPath;
			final String valueConfigDirectoryPath = map.get(keyConfigDirectoryPath);
			try{
				configDirectoryPath = getConcatenatedPathWithSPADERootIfNotAbsolute(spadeRoot, valueConfigDirectoryPath);
				final File configDirectory = new File(configDirectoryPath);
				if(!configDirectory.exists()){
					configDirectory.mkdirs();
	        	}else{ // exists
	        		FileUtility.pathMustBeAReadableWritableDirectory(configDirectoryPath);
	        	}
			}catch(Exception e){
				throw new Exception("Invalid value for key '"+ keyDatabaseDirectoryPath +"'", e);
			}
			
			final String libraryDirectoryPath;
			final String valueLibraryDirectoryPath = map.get(keyLibraryDirectoryPath);
			try{
				libraryDirectoryPath = getConcatenatedPathWithSPADERootIfNotAbsolute(spadeRoot, valueLibraryDirectoryPath);
				FileUtility.pathMustBeAReadableWritableDirectory(libraryDirectoryPath);
			}catch(Exception e){
				throw new Exception("Invalid value for key '"+ keyLibraryDirectoryPath +"'", e);
			}
			
			final String queryHistoryFile;
			final String valueQueryHistoryFile = map.get(keyQueryHistoryFile);
			try{
				queryHistoryFile = getConcatenatedPathWithSPADERootIfNotAbsolute(spadeRoot, valueQueryHistoryFile);
				FileUtility.pathMustBeAWritableFile(queryHistoryFile);
			}catch(Exception e){
				throw new Exception("Invalid value for key '"+ keyQueryHistoryFile +"'", e);
			}
			
			final String controlHistoryFile;
			final String valueControlHistoryFile = map.get(keyControlHistoryFile);
			try{
				controlHistoryFile = getConcatenatedPathWithSPADERootIfNotAbsolute(spadeRoot, valueControlHistoryFile);
				FileUtility.pathMustBeAWritableFile(controlHistoryFile);
			}catch(Exception e){
				throw new Exception("Invalid value for key '"+ keyControlHistoryFile +"'", e);
			}
			
			final String spadeHostFile;
			final String valueSpadeHostFile = map.get(keySPADEHostFile);
			try{
				spadeHostFile = getConcatenatedPathWithSPADERootIfNotAbsolute(spadeRoot, valueSpadeHostFile);
				FileUtility.pathMustBeAWritableFile(spadeHostFile);
			}catch(Exception e){
				throw new Exception("Invalid value for key '"+ keySPADEHostFile +"'", e);
			}
			
			final String spadePidFile;
			final String valueSpadePidFile = map.get(keySPADEPidFile);
			try{
				spadePidFile = getConcatenatedPathWithSPADERootIfNotAbsolute(spadeRoot, valueSpadePidFile);
				FileUtility.pathMustBeAWritableFile(spadePidFile);
			}catch(Exception e){
				throw new Exception("Invalid value for key '"+ keySPADEPidFile +"'", e);
			}
			
			final String serverPublicKeystore;
			final String valueServerPublicKeystore = map.get(keyServerPublicKeystore);
			try{
				serverPublicKeystore = getConcatenatedPathWithSPADERootIfNotAbsolute(spadeRoot, valueServerPublicKeystore);
				FileUtility.pathMustBeAReadableFile(serverPublicKeystore); FileUtility.pathMustBeAWritableFile(serverPublicKeystore);
			}catch(Exception e){
				throw new Exception("Invalid value for key '"+ keyServerPublicKeystore +"'", e);
			}
			
			final String serverPrivateKeystore;
			final String valueServerPrivateKeystore = map.get(keyServerPrivateKeystore);
			try{
				serverPrivateKeystore = getConcatenatedPathWithSPADERootIfNotAbsolute(spadeRoot, valueServerPrivateKeystore);
				FileUtility.pathMustBeAReadableFile(serverPrivateKeystore); FileUtility.pathMustBeAWritableFile(serverPrivateKeystore);
			}catch(Exception e){
				throw new Exception("Invalid value for key '"+ keyServerPrivateKeystore +"'", e);
			}
			
			final String clientPublicKeystore;
			final String valueClientPublicKeystore = map.get(keyClientPublicKeystore);
			try{
				clientPublicKeystore = getConcatenatedPathWithSPADERootIfNotAbsolute(spadeRoot, valueClientPublicKeystore);
				FileUtility.pathMustBeAReadableFile(clientPublicKeystore); FileUtility.pathMustBeAWritableFile(clientPublicKeystore);
			}catch(Exception e){
				throw new Exception("Invalid value for key '"+ keyClientPublicKeystore +"'", e);
			}
			
			final String clientPrivateKeystore;
			final String valueClientPrivateKeystore = map.get(keyClientPrivateKeystore);
			try{
				clientPrivateKeystore = getConcatenatedPathWithSPADERootIfNotAbsolute(spadeRoot, valueClientPrivateKeystore);
				FileUtility.pathMustBeAReadableFile(clientPrivateKeystore); FileUtility.pathMustBeAWritableFile(clientPrivateKeystore);
			}catch(Exception e){
				throw new Exception("Invalid value for key '"+ keyClientPrivateKeystore +"'", e);
			}
			
			final String passwordPublicKeystore = map.get(keyPasswordPublicKeystore);
			if(HelperFunctions.isNullOrEmpty(passwordPublicKeystore)){
				throw new Exception("Invalid value for key '"+ keyPasswordPublicKeystore +"'");
			}
			
			final String passwordPrivateKeystore = map.get(keyPasswordPrivateKeystore);
			if(HelperFunctions.isNullOrEmpty(passwordPrivateKeystore)){
				throw new Exception("Invalid value for key '"+ keyPasswordPrivateKeystore +"'");
			}
			
			final String logFileNamePattern = map.get(keyLogFileNamePattern);
			try{
        		new SimpleDateFormat(logFileNamePattern).format(new Date(System.currentTimeMillis()));
        	}catch(Exception e){
        		throw new Exception("Failed to format date/time in value of key '" + keyLogFileNamePattern + "'", e);
        	}
		
			instance.loaded = true;
			instance.spadeRoot = spadeRoot;
			instance.localControlPort = resultLocalControlPort.result.intValue();
			instance.commandLineQueryPort = resultCommandLineQueryPort.result.intValue();
			instance.remoteSketchPort = resultRemoteSketchPort.result.intValue();
			instance.connectionTimeoutMillis = resultConnectionTimeout.result.intValue();
			instance.sourceReporter = sourceReporter;
			instance.loggerLevel = loggerLevel;
			instance.temporaryDirectory = temporaryDirectory;
			instance.logDirectory = logDirectoryPath;
			instance.logFileNamePattern = logFileNamePattern;
			instance.databaseDirectory = databaseDirectoryPath;
			instance.configDirectory = configDirectoryPath;
			instance.libraryDirectory = libraryDirectoryPath;
			instance.queryHistoryFile = queryHistoryFile;
			instance.controlHistoryFile = controlHistoryFile;
			instance.spadeHostFile = spadeHostFile;
			instance.spadePidFile = spadePidFile;
			instance.serverPublicKeystore = serverPublicKeystore;
			instance.serverPrivateKeystore = serverPrivateKeystore;
			instance.clientPublicKeystore = clientPublicKeystore;
			instance.clientPrivateKeystore = clientPrivateKeystore;
			instance.passwordPublicKeystore = passwordPublicKeystore;
			instance.passwordPrivateKeystore = passwordPrivateKeystore;
		}
	}

	public static void initializeLogging() throws Exception{
		synchronized(instance){
			if(instance.loaded == false){
				throw new Exception("Must load the Settings before initializing logging!");
			}
			if(instance.loggingInitialized){
				return;
			}

			System.setProperty("java.util.logging.manager", spade.utility.LogManager.class.getName());
			System.setProperty("java.util.logging.SimpleFormatter.format",
					"%1$tb %1$td, %1$tY %1$tl:%1$tM:%1$tS %1$Tp %2$s %4$s: %5$s%6$s%n");

			final String logFilePath;
			final String logFilename;
			try{
				final Date currentDate = new Date(System.currentTimeMillis());
				logFilename = new SimpleDateFormat(instance.logFileNamePattern).format(currentDate);
				logFilePath = concatenatePaths(getLogDirectoryPath(), logFilename);
			}catch(Exception e){
				throw new Exception("Failed to format date/time in value of key '" + keyLogFileNamePattern + "'", e);
			}

			try{
				final Handler logFileHandler = new FileHandler(logFilePath);
				logFileHandler.setFormatter(new SimpleFormatter());
				logFileHandler.setLevel(getLoggerLevel());
				Logger.getLogger("").addHandler(logFileHandler);
			}catch(Exception e){
				throw new Exception("Failed to initialize SPADE log handler", e);
			}

			try{
				Path target = Paths.get("", logFilePath);
				String linkPath = getCurrentLogLinkPath();
				Path link = Paths.get("", linkPath);
				if(Files.exists(link)){
					Files.delete(link);
				}
				Files.createSymbolicLink(link, target);
			}catch(Exception e){
				// Ignore if failed to do this
			}

			instance.loggingInitialized = true;
		}
	}

	public static String concatenatePaths(String ...paths){
		if(paths == null || paths.length == 0){
			return null;
		}
		String finalPath = paths[0];
		for(int i = 1; i < paths.length; i++){
			String path = paths[i];
			if(path == null){
				return null;
			}
			finalPath = finalPath + File.separatorChar + path;
		}
		return finalPath;
	}
	
	private static String getConcatenatedPathWithSPADERootIfNotAbsolute(String spadeRoot, String path) throws Exception{
		if(isAbsolutePath(path)){
			return path;
		}
		return concatenatePaths(spadeRoot, path);
	}
	
	public static String getPathRelativeToSPADERootIfNotAbsolute(final String path){
		if(isAbsolutePath(path)){
			return path;
		}
		return concatenatePaths(join(getSPADERoot(), new String[]{path}));
	}
	
	public static boolean isAbsolutePath(final String path){
		return path != null ? path.startsWith(File.separator) : false;
	}

	public static String getSPADERoot(){
		synchronized(instance){
			return instance.spadeRoot;
		}
	}
	
	public static int getLocalControlPort(){
		synchronized(instance){
			return instance.localControlPort;
		}
	}
	
	public static int getCommandLineQueryPort(){
		synchronized(instance){
			return instance.commandLineQueryPort;
		}
	}
	
	public static int getRemoteSketchPort(){
		synchronized(instance){
			return instance.remoteSketchPort;
		}
	}
	
	public static int getConnectionTimeoutMillis(){
		synchronized(instance){
			return instance.connectionTimeoutMillis;
		}
	}
	
	public static String getSourceReporter(){
		synchronized(instance){
			return instance.sourceReporter;
		}
	}
	
	private static Level getLoggerLevel(){
		synchronized(instance){
			return instance.loggerLevel;
		}
	}
	
	public static String getTemporaryDirectory(){
		synchronized(instance){
			return instance.temporaryDirectory;
		}
	}
	
	private static String getLogDirectoryPath(){
		synchronized(instance){
			return instance.logDirectory;
		}
	}
	
	public static String getDatabaseDirectoryPath(){
		synchronized(instance){
			return instance.databaseDirectory;
		}
	}
	
	public static String getConfigDirectoryPath(){
		synchronized(instance){
			return instance.configDirectory;
		}
	}
	
	public static String getLibraryDirectoryPath(){
		synchronized(instance){
			return instance.libraryDirectory;
		}
	}
	
	public static String getQueryHistoryFilePath(){
		synchronized(instance){
			return instance.queryHistoryFile;
		}
	}
	
	public static String getControlHistoryFilePath(){
		synchronized(instance){
			return instance.controlHistoryFile;
		}
	}
	
	public static String getSPADEHostFilePath(){
		synchronized(instance){
			return instance.spadeHostFile;
		}
	}
	
	public static String getSPADEProcessIdFilePath(){
		synchronized(instance){
			return instance.spadePidFile;
		}
	}
	
	public static String getServerPublicKeystorePath(){
		synchronized(instance){
			return instance.serverPublicKeystore;
		}
	}
	
	public static String getServerPrivateKeystorePath(){
		synchronized(instance){
			return instance.serverPrivateKeystore;
		}
	}
	
	public static String getClientPublicKeystorePath(){
		synchronized(instance){
			return instance.clientPublicKeystore;
		}
	}
	
	public static String getClientPrivateKeystorePath(){
		synchronized(instance){
			return instance.clientPrivateKeystore;
		}
	}
	
	public static char[] getPasswordPublicKeystoreAsCharArray(){
		synchronized(instance){
			return instance.passwordPublicKeystore.toCharArray();
		}
	}
	
	public static char[] getPasswordPrivateKeystoreAsCharArray(){
		synchronized(instance){
			return instance.passwordPrivateKeystore.toCharArray();
		}
	}
	
	private static String[] join(String first, String[] rest){
		if(rest == null){
			return null;
		}
		String result[] = new String[rest.length + 1];
		result[0] = first;
		for(int i = 0; i < rest.length; i++){
			result[i+1] = rest[i];
		}
		return result;
	}
	
	public static String getPathRelativeToSPADERoot(String ... paths){
		return concatenatePaths(join(getSPADERoot(), paths));
	}
	
	public static String getPathRelativeToTemporaryDirectory(String ... paths){
		return concatenatePaths(join(getTemporaryDirectory(), paths));
	}
	
	public static String getPathRelativeToConfigDirectory(String ... paths){
		return concatenatePaths(join(getConfigDirectoryPath(), paths));
	}
	
	public static String getPathRelativeToLibraryDirectory(String ... paths){
		return concatenatePaths(join(getLibraryDirectoryPath(), paths));
	}
	
	public static String getDefaultConfigFilePath(Class<?> forClass){
		return getPathRelativeToConfigDirectory(forClass.getName() + ".config");
	}

	public static String getDefaultOutputFilePath(Class<?> forClass){
		return getPathRelativeToConfigDirectory(forClass.getName() + ".out");
	}
	
	public static String getCurrentLogLinkPath(){
		return concatenatePaths(getLogDirectoryPath(), "current.log");
	}
}
