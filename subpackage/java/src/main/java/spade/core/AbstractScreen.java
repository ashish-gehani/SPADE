/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2020 SRI International

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
import java.lang.reflect.Constructor;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.utility.FileUtility;
import spade.utility.HelperFunctions;
import spade.utility.Result;

public abstract class AbstractScreen{

	private final static Logger logger = Logger.getLogger(AbstractScreen.class.getName());
	public final static String keyScreenArgument = "screen";

	public static final Result<ArrayList<String>> parseScreensInOrder(
			final String userArgumentsString, final Class<? extends AbstractStorage> storageClass){
		final String userArguments = userArgumentsString == null ? "" : userArgumentsString.trim();
		if(storageClass == null){
			return Result.failed("Failed to construct screen arguments. NULL storage class");
		}
		
		final LinkedList<SimpleEntry<String, String>> allEntries = new LinkedList<SimpleEntry<String, String>>();
		
		final Result<ArrayList<SimpleEntry<String, String>>> argumentKeyValueEntriesListResult = 
				HelperFunctions.parseKeyValueEntriesInString(userArguments);
		if(argumentKeyValueEntriesListResult.error){
			return Result.failed("Failed to construct screen arguments from user arguments", null, argumentKeyValueEntriesListResult);
		}
		
		allEntries.addAll(argumentKeyValueEntriesListResult.result);

		final boolean storageConfigFileExists;
		final String storageConfigFilePath = Settings.getDefaultConfigFilePath(storageClass);
		
		try{
			final File file = new File(storageConfigFilePath);
			if(file.exists()){
				if(file.isDirectory()){
					throw new Exception("Must be a file but is a directory");
				}else{
					if(!file.canRead()){
						throw new Exception("Must be a readable file but is not readable");	
					}else{
						storageConfigFileExists = true;
					}
				}
			}else{
				storageConfigFileExists = false;
			}
		}catch(Exception e){
			return Result.failed("Invalid storage config file: '"+storageConfigFilePath+"'", e, null);
		}
		
		if(storageConfigFileExists){
			final Result<ArrayList<SimpleEntry<String, String>>> storageKeyValueEntriesListResult = 
					FileUtility.parseKeyValueEntriesInConfigFile(storageConfigFilePath, "=", true);
			if(storageKeyValueEntriesListResult.error){
				return Result.failed("Failed to construct screen arguments from storage config file", null, storageKeyValueEntriesListResult);
			}
			
			allEntries.addAll(storageKeyValueEntriesListResult.result);
		}
		
		final boolean abstractStorageConfigFileExists;
		final String abstractStorageConfigFilePath = Settings.getDefaultConfigFilePath(AbstractStorage.class);
		
		try{
			final File file = new File(abstractStorageConfigFilePath);
			if(file.exists()){
				if(file.isDirectory()){
					throw new Exception("Must be a file but is a directory");
				}else{
					if(!file.canRead()){
						throw new Exception("Must be a readable file but is not readable");	
					}else{
						abstractStorageConfigFileExists = true;
					}
				}
			}else{
				abstractStorageConfigFileExists = false;
			}
		}catch(Exception e){
			return Result.failed("Invalid abstract storage config file: '"+abstractStorageConfigFilePath+"'", e, null);
		}
		
		if(abstractStorageConfigFileExists){
			final Result<ArrayList<SimpleEntry<String, String>>> abstractStorageKeyValueEntriesListResult = 
					FileUtility.parseKeyValueEntriesInConfigFile(abstractStorageConfigFilePath, "=", true);
			if(abstractStorageKeyValueEntriesListResult.error){
				return Result.failed("Failed to construct screen arguments from abstract storage config file", null, 
						abstractStorageKeyValueEntriesListResult);
			}
			
			allEntries.addAll(abstractStorageKeyValueEntriesListResult.result);
		}
		
		final ArrayList<SimpleEntry<String, String>> filteredEntries = HelperFunctions.filterEntriesByKey(allEntries, keyScreenArgument);
		
		// consolidate same screen specified in different places while retaining the order
		
		final Map<String, String> screenNameToConsolidatedArguments = new HashMap<String, String>();

		for(final SimpleEntry<String, String> screenEntry : filteredEntries){
			if(screenEntry != null && screenEntry.getValue() != null){
				final String[] tokens = screenEntry.getValue().split("\\s+", 2);
				final String screenName = tokens[0];
				final String screenArguments;
				if(tokens.length == 2){
					screenArguments = tokens[1];
				}else{
					screenArguments = "";
				}
				
				String existingScreenArguments = screenNameToConsolidatedArguments.get(screenName);
				if(existingScreenArguments == null){
					existingScreenArguments = "";
				}
				existingScreenArguments = existingScreenArguments.trim() + " " + screenArguments.trim();
				existingScreenArguments = existingScreenArguments.trim();
				screenNameToConsolidatedArguments.put(screenName, existingScreenArguments);
			}
		}
		
		final Set<String> screenNamesAddedToList = new HashSet<String>();
		
		final ArrayList<String> screensInOrder = new ArrayList<String>();
		for(final SimpleEntry<String, String> screenEntry : filteredEntries){
			if(screenEntry != null && screenEntry.getValue() != null){
				final String[] tokens = screenEntry.getValue().split("\\s+", 2);
				final String screenName = tokens[0];
				if(screenNamesAddedToList.contains(screenName)){
					continue;
				}else{
					screenNamesAddedToList.add(screenName);
				}
				String existingScreenArguments = screenNameToConsolidatedArguments.get(screenName);
				if(HelperFunctions.isNullOrEmpty(existingScreenArguments)){
					screensInOrder.add(screenName);
				}else{
					existingScreenArguments = existingScreenArguments.trim();
					screensInOrder.add(screenName + " " + existingScreenArguments);
				}
			}
		}
		
		return Result.successful(screensInOrder);
	}
	
	public static final Result<ArrayList<AbstractScreen>> initializeScreensInOrder(final List<String> storageScreensSpecs){
		if(storageScreensSpecs == null){
			return Result.failed("NULL list for screens");
		}
		
		Result<AbstractScreen> screenCreationFailureResult = null;
		boolean failed = false;
		
		final ArrayList<AbstractScreen> screens = new ArrayList<AbstractScreen>();
		for(final String storageScreenSpec : storageScreensSpecs){
			if(storageScreenSpec != null){
				final String storageScreenSpecTokens[] = storageScreenSpec.split("\\s+", 2);
				final String screenArguments;
				if(storageScreenSpecTokens.length == 2){
					screenArguments = storageScreenSpecTokens[1];
				}else{
					screenArguments = "";
				}
				final String screenClassName = storageScreenSpecTokens[0];
				final Result<AbstractScreen> screenResult = initializeScreen(screenClassName, screenArguments);
				if(screenResult.error){
					screenCreationFailureResult = screenResult;
					failed = true;
					break;
				}else{
					screens.add(screenResult.result);
				}
			}
		}
		
		if(failed){
			// shutdown all and return the error
			for(final AbstractScreen screen : screens){
				shutdownScreen(screen);
			}
			return Result.failed("Failed to initialize screen", null, screenCreationFailureResult);
		}else{
			return Result.successful(screens);
		}
	}
	
	@SuppressWarnings("unchecked")
	public static final Result<AbstractScreen> initializeScreen(final String screenClassName, final String screenArguments){
		if(HelperFunctions.isNullOrEmpty(screenClassName)){
			return Result.failed("NULL/Empty class name for screen");
		}
		
		final String qualifiedScreenClassName = "spade.screen." + screenClassName;
		
		final Class<AbstractScreen> clazz;
		try{
			clazz = (Class<AbstractScreen>)Class.forName(qualifiedScreenClassName);
		}catch(Exception e){
			return Result.failed("Failed to find/load class: '"+qualifiedScreenClassName+"'", e, null);
		}
		
		final Constructor<AbstractScreen> constructor;
		try{
			constructor = clazz.getDeclaredConstructor();
		}catch(Exception e){
			return Result.failed("Failed to reflect on class. Illegal implementation for '"+qualifiedScreenClassName+"'."
					+ " Must have an empty public constructor", e, null);
		}
		
		final AbstractScreen screen;
		try{
			screen = constructor.newInstance();
		}catch(Exception e){
			return Result.failed("Failed to instantiate screen class using the empty constructor: " + clazz, e, null);
		}
		
		final boolean isInitialized;
		try{
			isInitialized = screen.initialize(screenArguments);
		}catch(Exception e){
			return Result.failed("Failed to initialize screen: " + qualifiedScreenClassName, e, null);
		}
		
		if(isInitialized){
			screen.setArguments(screenArguments);
			logger.log(Level.INFO, "Successfully initialized screen [name:"+qualifiedScreenClassName+", arguments:"+screenArguments+"]");
			return Result.successful(screen);			
		}else{
			final String msg = "Failed to initialize screen [name:"+qualifiedScreenClassName+", arguments:"+screenArguments+"]";
			logger.log(Level.SEVERE, msg);
			return Result.failed(msg);
		}
	}
	
	public static final void shutdownScreens(final List<AbstractScreen> screens){
		if(screens != null){
			for(final AbstractScreen screen : screens){
				shutdownScreen(screen);
			}
		}
	}
	
	public static final void shutdownScreen(final AbstractScreen screen){
		if(screen != null){
			try{
				screen.shutdown();
				logger.log(Level.INFO, "Successfull screen shutdown for '"+screen.getClass().getSimpleName()+"'");
			}catch(Exception e){
				logger.log(Level.INFO, "Failed screen shutdown for '"+screen.getClass().getSimpleName()+"'", e);
			}
		}
	}
	
	////////////////////////
	
	private String arguments = "";
	public final String getArguments(){
		return arguments;
	}
	private final void setArguments(final String arguments){
		if(arguments == null){
			this.arguments = "";
		}else{
			this.arguments = arguments;
		}
	}
	
	public abstract boolean initialize(final String arguments);
	public abstract boolean blockVertex(final AbstractVertex vertex);
	public abstract boolean blockEdge(final AbstractEdge edge);
	public abstract boolean shutdown();
}
