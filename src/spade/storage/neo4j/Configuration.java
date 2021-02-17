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
package spade.storage.neo4j;

import java.io.File;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.RelationshipType;

import spade.utility.FileUtility;
import spade.utility.HelperFunctions;
import spade.utility.Result;

public class Configuration{

	public enum HashKeyCollisionAction{ DISCARD, REMOVE };
	public enum IndexMode{ ALL, NONE };
	public enum VertexCacheMode{ ID, NODE };
	public enum EdgeCacheFindMode{ NONE, ITERATE, CYPHER };

	public static final String
		// Storage setup management
		keyDbHomeDirectoryPath = "dbms.directories.neo4j_home",
		keyDbDataDirectoryName = "database",
		keyDbName = "dbms.default_database",
		keyNeo4jConfigFilePath = "neo4jConfigFilePath",
		// Cache management
		keyVertexCacheMode = "vertexCacheMode",
		keyEdgeCacheFindMode = "edgeCacheFindMode",
		// Storage buffer management
		keyFlushBufferSize = "flushBufferSize", 
		keyFlushAfterSeconds = "flushAfterSeconds",
		keyBufferLimit = "bufferLimit",
		keyTransactionTimeoutInSeconds = "transactionTimeoutInSeconds",
		// Storage and database interaction management
		keyForceShutdown = "forceShutdown",
		keyReset = "reset",
		keyMaxRetries = "maxRetries",
		keyWaitForIndexSeconds = "waitForIndexSeconds",
		keyHashKeyCollisionAction = "hashKeyCollisionAction", 
		// Database schema management
		keyHashPropertyName = "hashPropertyName",
		keyEdgeSymbolsPropertyName = "edgeSymbolsPropertyName",
		keyNodePrimaryLabel = "nodePrimaryLabel", 
		keyEdgeRelationshipType = "edgeRelationshipType",
		keyQuerySymbolsNodeLabel = "querySymbolsNodeLabel",
		// Database indexing
		keyIndexVertex = "index.vertex", 
		keyIndexEdge = "index.edge",
		keyIndexNamePrefix = "indexNamePrefix",
		// Logging management
		keyReportingIntervalSeconds = "reportingIntervalSeconds",
		keyDebug = "debug", 
		keyTimeMe = "timeMe",
		// Thread management (i.e. responsivity)
		keySleepWaitMillis = "sleepWaitMillis", 
		keyMainThreadSleepWaitMillis = "mainThreadSleepWaitMillis",
		// Test management
		keyTest = "test",
		keyTestVertexTotal = "test.vertex.total",
		keyTestEdgeDegree = "test.edge.degree",
		keyTestVertexAnnotations = "test.vertex.annotations",
		keyTestEdgeAnnotations = "test.edge.annotations";

	// Storage setup management
	public final File dbHomeDirectoryFile;
	public final String dbDataDirectoryName;
	public final String dbName;
	public final File finalConstructedDbPath;
	public final File neo4jConfigFilePath;
	// Cache management
	public final VertexCacheMode vertexCacheMode;
	public final EdgeCacheFindMode edgeCacheFindMode;
	// Storage buffer management
	public final int flushBufferSize;
	public final int flushAfterSeconds;
	public final int bufferLimit;
	public final int transactionTimeoutInSeconds;
	// Storage and database interaction management
	public final boolean forceShutdown;
	public final boolean reset;
	public final int maxRetries;
	public final int waitForIndexSeconds;
	public final HashKeyCollisionAction hashKeyCollisionAction;
	// Database schema management
	public final String hashPropertyName;
	public final String edgeSymbolsPropertyName;
	public final String nodePrimaryLabelName;
	public final String edgeRelationshipTypeName;
	public final Label neo4jVertexLabel;
	public final RelationshipType neo4jEdgeRelationshipType;
	public final String querySymbolsNodeLabelName;
	public final Label neo4jQuerySymbolsNodeLabel;
	// Database indexing
	public final IndexMode indexVertexMode;
	public final IndexMode indexEdgeMode;
	public final String indexNamePrefix;
	// Logging management
	public final int reportingIntervalSeconds;
	public final boolean reportingEnabled;
	public final boolean debug;
	public final boolean timeMe;
	// Thread management (i.e. responsivity)
	public final long sleepWaitMillis;
	public final long mainThreadSleepWaitMillis;
	// Test management
	public final boolean test;
	public final long testVertexTotal;
	public final long testEdgeDegree;
	private final List<SimpleEntry<String, String>> testVertexAnnotationsList;
	private final List<SimpleEntry<String, String>> testEdgeAnnotationsList;
	// The stringified map that was left after removing all valid keys. Used as a warning in case the user uses illegal keys
	public final String extraKeysAndValues;

	public Configuration(
			// Storage setup management
			final File dbHomeDirectoryFile, 
			final String dbDataDirectoryName, 
			final String dbName,
			final File finalConstructedDbPath, 
			final File neo4jConfigFilePath,
			// Cache management
			final VertexCacheMode vertexCacheMode,
			final EdgeCacheFindMode edgeCacheFindMode,
			// Storage buffer management
			final int flushBufferSize, 
			final int flushAfterSeconds, 
			final int bufferLimit, 
			final int transactionTimeoutInSeconds,
			// Storage and database interaction management
			final boolean forceShutdown,
			final boolean reset,
			final int maxRetries,
			final int waitForIndexSeconds,
			final HashKeyCollisionAction hashKeyCollisionAction,
			// Database schema management
			final String hashPropertyName,
			final String edgeSymbolsPropertyName,
			final String nodePrimaryLabelName,
			final String edgeRelationshipTypeName, 
			final Label neo4jVertexLabel, 
			final RelationshipType neo4jEdgeRelationshipType,
			final String querySymbolsNodeLabelName,
			final Label neo4jQuerySymbolsNodeLabel,
			// Database indexing
			final IndexMode indexVertexMode, 
			final IndexMode indexEdgeMode, 
			final String indexNamePrefix,
			// Logging management
			final int reportingIntervalSeconds, 
			final boolean reportingEnabled,
			final boolean debug,
			final boolean timeMe,
			// Thread management (i.e. responsivity)
			final long sleepWaitMillis,
			final long mainThreadSleepWaitMillis,
			// Test management
			final boolean test,
			final long testVertexTotal, 
			final long testEdgeDegree, 
			final List<SimpleEntry<String, String>> testVertexAnnotationsList,
			final List<SimpleEntry<String, String>> testEdgeAnnotationsList,
			// The stringified map that was left
			final String extraKeysAndValues
			){

		// Storage setup management
		this.dbHomeDirectoryFile = dbHomeDirectoryFile;
		this.dbDataDirectoryName = dbDataDirectoryName;
		this.dbName = dbName;
		this.finalConstructedDbPath = finalConstructedDbPath;
		this.neo4jConfigFilePath = neo4jConfigFilePath;
		// Cache management
		this.vertexCacheMode = vertexCacheMode;
		this.edgeCacheFindMode = edgeCacheFindMode;
		// Storage buffer management
		this.flushBufferSize = flushBufferSize;
		this.flushAfterSeconds = flushAfterSeconds;
		this.bufferLimit = bufferLimit;
		this.transactionTimeoutInSeconds = transactionTimeoutInSeconds;
		// Storage and database interaction management
		this.forceShutdown = forceShutdown;
		this.reset = reset;
		this.maxRetries = maxRetries;
		this.waitForIndexSeconds = waitForIndexSeconds;
		this.hashKeyCollisionAction = hashKeyCollisionAction;
		// Database schema management
		this.hashPropertyName = hashPropertyName;
		this.edgeSymbolsPropertyName = edgeSymbolsPropertyName;
		this.nodePrimaryLabelName = nodePrimaryLabelName;
		this.edgeRelationshipTypeName = edgeRelationshipTypeName;
		this.neo4jVertexLabel = neo4jVertexLabel;
		this.neo4jEdgeRelationshipType = neo4jEdgeRelationshipType;
		this.querySymbolsNodeLabelName = querySymbolsNodeLabelName;
		this.neo4jQuerySymbolsNodeLabel = neo4jQuerySymbolsNodeLabel;
		// Database indexing
		this.indexVertexMode = indexVertexMode;
		this.indexEdgeMode = indexEdgeMode;
		this.indexNamePrefix = indexNamePrefix;
		// Logging management
		this.reportingIntervalSeconds = reportingIntervalSeconds;
		this.reportingEnabled = reportingEnabled;
		this.debug = debug;
		this.timeMe = timeMe;
		// Thread management (i.e. responsivity)
		this.sleepWaitMillis = sleepWaitMillis;
		this.mainThreadSleepWaitMillis = mainThreadSleepWaitMillis;
		// Test management
		this.test = test;
		this.testVertexTotal = testVertexTotal;
		this.testEdgeDegree = testEdgeDegree;
		this.testVertexAnnotationsList = testVertexAnnotationsList;
		this.testEdgeAnnotationsList = testEdgeAnnotationsList;
		// The stringified map that was left
		this.extraKeysAndValues = extraKeysAndValues;
	}
	
	public final List<SimpleEntry<String, String>> getTestVertexAnnotationsListCopy(){
		return new ArrayList<SimpleEntry<String, String>>(testVertexAnnotationsList);
	}
	
	public final List<SimpleEntry<String, String>> getTestEdgeAnnotationsListCopy(){
		return new ArrayList<SimpleEntry<String, String>>(testEdgeAnnotationsList);
	}

	// true on success and false on failure
	// logs the message
	private final static Result<ArrayList<SimpleEntry<String, String>>> initializeTestAnnotations(String value){
		if(HelperFunctions.isNullOrEmpty(value)){
			return Result.successful(new ArrayList<SimpleEntry<String, String>>());
		}else{
			value = value.trim();
			final ArrayList<SimpleEntry<String, String>> list = new ArrayList<SimpleEntry<String, String>>();
			final String[] valueTokens = value.split(",");
			for(final String valueToken : valueTokens){
				final String subKeySubValue[] = valueToken.split(":", 2);
				if(subKeySubValue.length != 2){
					return Result.failed("Invalid format for value. Must be in format: a:b(,c:d)*");
				}else{
					list.add(new SimpleEntry<String, String>(subKeySubValue[0].trim(), subKeySubValue[1].trim()));
				}
			}
			return Result.successful(list);
		}
	}
	
	private final static Result<File> parseOptionalReadableFile(final Map<String, String> map, final String key){
		final String pathString = map.remove(key);
		if(HelperFunctions.isNullOrEmpty(pathString)){
			return Result.successful(null);
		}else{
			final File file = new File(pathString);
			try{
				if(file.exists()){
					if(!file.isFile()){
						return Result.failed("The path for key '" + key + "' is not a file: '" + file.getAbsolutePath() + "'");
					}else{
						if(!file.canRead()){
							return Result.failed("The path for key '" + key + "' is not a readable file: '" + file.getAbsolutePath() + "'");
						}
					}
				}else{
					return Result.failed("The path for key '" + key + "' does not exist: '" + file.getAbsolutePath() + "'");
				}
			}catch(Exception e){
				return Result.failed("Failed to validate file path for key '" + key + "': '" + file.getAbsolutePath() + "'", e, null);
			}
			return Result.successful(file);
		}
	}

	private final static Result<File> parseDbHomeDirectoryFile(final Map<String, String> map, final String key){
		final File dbHomeDirectoryFile;
		final String dbHomeDirectoryPathString = map.remove(key);
		if(HelperFunctions.isNullOrEmpty(dbHomeDirectoryPathString)){
			dbHomeDirectoryFile = new File("");
		}else{
			dbHomeDirectoryFile = new File(dbHomeDirectoryPathString.trim());
		}
		
		try{
			if(dbHomeDirectoryFile.exists()){
				if(!dbHomeDirectoryFile.isDirectory()){
					return Result.failed("Path for key '" + keyDbHomeDirectoryPath + "' exists but is not a directory: '" + dbHomeDirectoryFile.getAbsolutePath() + "'");
				}else{
					if(!dbHomeDirectoryFile.canWrite()){
						return Result.failed("Path for key '" + keyDbHomeDirectoryPath + "' must be a writable directory: '" + dbHomeDirectoryFile.getAbsolutePath() + "'");
					}
					if(!dbHomeDirectoryFile.canRead()){
						return Result.failed("Path for key '" + keyDbHomeDirectoryPath + "' must be a readable directory: '" + dbHomeDirectoryFile.getAbsolutePath() + "'");
					}
				}
			}
		}catch(Exception e){
			return Result.failed("Failed to validate directory path for key '" + keyDbHomeDirectoryPath + "': '"+ dbHomeDirectoryFile.getAbsolutePath() + "'", e, null);
		}
		
		return Result.successful(dbHomeDirectoryFile);
	}

	/**
	 * @param arguments The arguments string for the storage
	 * @param configFilePath The config file path of the storage
	 * @return The result object containing the successful object or an error object
	 */
	public final static Result<Configuration> initialize(final String arguments, final String configFilePath){
		// NOTE: Remove keys from map because at the end a warning is printed if the map
		// wasn't empty

		final Map<String, String> map = new HashMap<String, String>();
		try{
			final Map<String, String> configMap = FileUtility.readConfigFileAsKeyValueMap(configFilePath, "=");
			map.putAll(configMap);
		}catch(Exception e){
			return Result.failed("Failed to read config file: " + configFilePath, e, null);
		}

		if(!HelperFunctions.isNullOrEmpty(arguments)){
			final Result<HashMap<String, String>> argumentsParseResult = HelperFunctions
					.parseKeysValuesInString(arguments);
			if(argumentsParseResult.error){
				return Result.failed("Failed to parse arguments", null, argumentsParseResult);
			}
			map.putAll(argumentsParseResult.result);
		}

		// Initialized map to get key values from

		// Start - Storage setup management
		final Result<File> dbHomeDirectoryFileResult = parseDbHomeDirectoryFile(map, keyDbHomeDirectoryPath);
		if(dbHomeDirectoryFileResult.error){
			return Result.failed(dbHomeDirectoryFileResult.errorMessage, dbHomeDirectoryFileResult.exception, dbHomeDirectoryFileResult.cause);
		}
		final File dbHomeDirectoryFile = dbHomeDirectoryFileResult.result;

		final String dbNameString = map.remove(keyDbName);
		if(HelperFunctions.isNullOrEmpty(dbNameString)){
			return Result.failed("NULL/Empty value for '" + keyDbName + "': '" + dbNameString + "'");
		}
		final String dbName = dbNameString.trim();

		final String dbDataDirectoryName;
		final String dbDataDirectoryNameString = map.remove(keyDbDataDirectoryName);
		// TODO Never allow empty. If yes then verify that 'reset' is not dangerous
		if(HelperFunctions.isNullOrEmpty(dbDataDirectoryNameString)){
			dbDataDirectoryName = GraphDatabaseSettings.DEFAULT_DATA_DIR_NAME;
		}else{
			dbDataDirectoryName = dbDataDirectoryNameString.trim();
		}

		final Result<File> neo4jConfigFilePathResult = parseOptionalReadableFile(map, keyNeo4jConfigFilePath);
		if(neo4jConfigFilePathResult.error){
			return Result.failed(neo4jConfigFilePathResult.errorMessage, neo4jConfigFilePathResult.exception, neo4jConfigFilePathResult.cause);
		}
		final File neo4jConfigFilePath = neo4jConfigFilePathResult.result;

		final File finalConstructedDbPath;
		try{
			finalConstructedDbPath = new File(
					dbHomeDirectoryFile.getAbsolutePath()
					+ File.separatorChar
					+ dbDataDirectoryName
					+ File.separatorChar
					+ "databases"
					+ File.separatorChar
					+ dbName);
		}catch(Exception e){
			return Result.failed("Failed to construct database path using values of '"
					+keyDbHomeDirectoryPath+"'("+dbHomeDirectoryFile+"), '"
					+keyDbDataDirectoryName+"'("+dbDataDirectoryName+"), and '"
					+keyDbName+"'("+dbName+")", e, null);
		}
		// End - Storage setup management

		// Start - Cache management
		final String vertexCacheModeString = map.remove(keyVertexCacheMode);
		final Result<VertexCacheMode> vertexCacheModeResult = HelperFunctions.parseEnumValue(VertexCacheMode.class, vertexCacheModeString, true);
		if(vertexCacheModeResult.error){
			return Result.failed("Invalid value for '" + keyVertexCacheMode + "': '"+vertexCacheModeString+"'", null, vertexCacheModeResult);
		}
		final VertexCacheMode vertexCacheMode = vertexCacheModeResult.result;
		
		final String edgeCacheFindModeString = map.remove(keyEdgeCacheFindMode);
		final Result<EdgeCacheFindMode> edgeCacheFindModeResult = HelperFunctions.parseEnumValue(EdgeCacheFindMode.class, edgeCacheFindModeString, true);
		if(edgeCacheFindModeResult.error){
			return Result.failed("Invalid value for '" + keyEdgeCacheFindMode + "': '"+edgeCacheFindModeString+"'", null, edgeCacheFindModeResult);
		}
		final EdgeCacheFindMode edgeCacheFindMode = edgeCacheFindModeResult.result;
		// End - Cache management

		// Start - Storage buffer management
		final String flushBufferSizeString = map.remove(keyFlushBufferSize);
		final Result<Long> flushBufferSizeResult = HelperFunctions.parseLong(flushBufferSizeString, 10, 0, Integer.MAX_VALUE);
		if(flushBufferSizeResult.error){
			return Result.failed("Invalid value for '" + keyFlushBufferSize + "': '"+flushBufferSizeString+"'", null, flushBufferSizeResult);
		}
		final int flushBufferSize = flushBufferSizeResult.result.intValue();

		final String flushAfterSecondsString = map.remove(keyFlushAfterSeconds);
		final Result<Long> flushAfterSecondsResult = HelperFunctions.parseLong(flushAfterSecondsString, 10, 0, Integer.MAX_VALUE);
		if(flushAfterSecondsResult.error){
			return Result.failed("Invalid value for '" + keyFlushAfterSeconds + "': '"+flushAfterSecondsString+"'", null, flushAfterSecondsResult);
		}
		final int flushAfterSeconds = flushAfterSecondsResult.result.intValue();

		final String bufferLimitString = map.remove(keyBufferLimit);
		final Result<Long> bufferLimitResult = HelperFunctions.parseLong(bufferLimitString, 10, Integer.MIN_VALUE, Integer.MAX_VALUE);
		if(bufferLimitResult.error){
			return Result.failed("Invalid value for '" + keyBufferLimit + "': '"+bufferLimitString+"'", null, bufferLimitResult);
		}
		final int bufferLimit = bufferLimitResult.result.intValue();
		
		final String transactionTimeoutInSecondsString = map.remove(keyTransactionTimeoutInSeconds);
		final Result<Long> transactionTimeoutInSecondsResult = HelperFunctions.parseLong(transactionTimeoutInSecondsString, 10, Integer.MIN_VALUE, Integer.MAX_VALUE);
		if(transactionTimeoutInSecondsResult.error){
			return Result.failed("Invalid value for '" + keyTransactionTimeoutInSeconds + "': '"+transactionTimeoutInSecondsString+"'", null, transactionTimeoutInSecondsResult);
		}
		final int transactionTimeoutInSeconds = transactionTimeoutInSecondsResult.result.intValue();
		// End - Storage buffer management

		// Start - Storage and database interaction management
		final String forceShutdownString = map.remove(keyForceShutdown);
		final Result<Boolean> forceShutdownResult = HelperFunctions.parseBoolean(forceShutdownString);
		if(forceShutdownResult.error){
			return Result.failed("Invalid value for '" + keyForceShutdown + "': '"+forceShutdownString+"'", null, forceShutdownResult);
		}
		final boolean forceShutdown = forceShutdownResult.result;

		final String resetString = map.remove(keyReset);
		final Result<Boolean> resetResult = HelperFunctions.parseBoolean(resetString);
		if(resetResult.error){
			return Result.failed("Invalid value for '" + keyReset + "': '" + resetString + "'", null, resetResult);
		}
		final boolean reset = resetResult.result;

		final String maxRetriesString = map.remove(keyMaxRetries);
		final Result<Long> maxRetriesResult = HelperFunctions.parseLong(maxRetriesString, 10, 0, Integer.MAX_VALUE);
		if(maxRetriesResult.error){
			return Result.failed("Invalid value for '" + keyMaxRetries + "': '"+maxRetriesString+"'", null, maxRetriesResult);
		}
		final int maxRetries = maxRetriesResult.result.intValue();

		final String waitForIndexSecondsString = map.remove(keyWaitForIndexSeconds);
		final Result<Long> waitForIndexSecondsResult = HelperFunctions.parseLong(waitForIndexSecondsString, 10, 0, Integer.MAX_VALUE);
		if(waitForIndexSecondsResult.error){
			return Result.failed("Invalid value for '" + keyWaitForIndexSeconds + "': '"+waitForIndexSecondsString+"'", null, waitForIndexSecondsResult);
		}
		final int waitForIndexSeconds = waitForIndexSecondsResult.result.intValue();

		final String hashKeyCollisionActionString = map.remove(keyHashKeyCollisionAction);
		final Result<HashKeyCollisionAction> hashKeyCollisionActionResult = HelperFunctions.parseEnumValue(HashKeyCollisionAction.class, hashKeyCollisionActionString, true);
		if(hashKeyCollisionActionResult.error){
			return Result.failed("Invalid value for '" + keyHashKeyCollisionAction + "': '"+hashKeyCollisionActionString+"'", null, hashKeyCollisionActionResult);
		}
		final HashKeyCollisionAction hashKeyCollisionAction = hashKeyCollisionActionResult.result;
		// End - Storage and database interaction management

		// Start - Database schema management		
		final String hashPropertyNameString = map.remove(keyHashPropertyName);
		if(HelperFunctions.isNullOrEmpty(hashPropertyNameString)){
			return Result.failed("NULL/Empty value for '" + keyHashPropertyName + "': '"+hashPropertyNameString+"'");
		}
		final String hashPropertyName = hashPropertyNameString.trim();

		final String edgeSymbolsPropertyNameString = map.remove(keyEdgeSymbolsPropertyName);
		if(HelperFunctions.isNullOrEmpty(edgeSymbolsPropertyNameString)){
			return Result.failed("NULL/Empty value for '" + keyEdgeSymbolsPropertyName + "': '"+edgeSymbolsPropertyNameString+"'");
		}
		final String edgeSymbolsPropertyName = edgeSymbolsPropertyNameString.trim();

		final String nodePrimaryLabelNameString = map.remove(keyNodePrimaryLabel);
		if(HelperFunctions.isNullOrEmpty(nodePrimaryLabelNameString)){
			return Result.failed("NULL/Empty value for '" + keyNodePrimaryLabel + "': '" + nodePrimaryLabelNameString + "'");
		}
		final String nodePrimaryLabelName = nodePrimaryLabelNameString.trim();
		final Label neo4jVertexLabel = Label.label(nodePrimaryLabelName); // TODO label with spaces or illegal

		final String edgeRelationshipTypeString = map.remove(keyEdgeRelationshipType);
		if(HelperFunctions.isNullOrEmpty(edgeRelationshipTypeString)){
			return Result.failed("NULL/Empty value for '" + keyEdgeRelationshipType + "': '"+edgeRelationshipTypeString+"'");
		}
		final String edgeRelationshipTypeName = edgeRelationshipTypeString.trim();
		final RelationshipType neo4jEdgeRelationshipType = RelationshipType.withName(edgeRelationshipTypeName);
		
		final String querySymbolsNodeLabelNameString = map.remove(keyQuerySymbolsNodeLabel);
		if(HelperFunctions.isNullOrEmpty(querySymbolsNodeLabelNameString)){
			return Result.failed("NULL/Empty value for '" + keyQuerySymbolsNodeLabel + "': '" + querySymbolsNodeLabelNameString + "'");
		}
		final String querySymbolsNodeLabelName = querySymbolsNodeLabelNameString.trim();
		final Label neo4jQuerySymbolsNodeLabel = Label.label(querySymbolsNodeLabelName);
		
		if(nodePrimaryLabelName.contains(" ")){
			return Result.failed("Invalid value for '"+keyNodePrimaryLabel+"': '"+nodePrimaryLabelName+"'. Must not contain <space>.");
		}
		if(edgeRelationshipTypeName.contains(" ")){
			return Result.failed("Invalid value for '"+keyEdgeRelationshipType+"': '"+edgeRelationshipTypeName+"'. Must not contain <space>.");
		}
		if(querySymbolsNodeLabelName.contains(" ")){
			return Result.failed("Invalid value for '"+keyQuerySymbolsNodeLabel+"': '"+querySymbolsNodeLabelName+"'. Must not contain <space>.");
		}
		
		if(nodePrimaryLabelName.equalsIgnoreCase(querySymbolsNodeLabelName)){
			return Result.failed("Values for '"+keyNodePrimaryLabel+"' ('"+nodePrimaryLabelName+"') and '"
					+keyQuerySymbolsNodeLabel+"' ('"+querySymbolsNodeLabelName+"') must not be same (case insensitive)");
		}
		
		if(hashPropertyName.equalsIgnoreCase(edgeSymbolsPropertyName)){
			return Result.failed("Values for '"+keyHashPropertyName+"' ('"+hashPropertyName+"') and '"
					+keyEdgeSymbolsPropertyName+"' ('"+edgeSymbolsPropertyName+"') must not be same (case insensitive comparison)");
		}
		// End - Database schema management

		// Start - Database indexing
		final String indexVertexModeString = map.remove(keyIndexVertex);
		final Result<IndexMode> indexVertexModeResult = HelperFunctions.parseEnumValue(IndexMode.class, indexVertexModeString, true);
		if(indexVertexModeResult.error){
			return Result.failed("Invalid value for '" + keyIndexVertex + "': '" + indexVertexModeString + "'", null, indexVertexModeResult);
		}
		final IndexMode indexVertexMode = indexVertexModeResult.result;

		final String indexEdgeModeString = map.remove(keyIndexEdge);
		final Result<IndexMode> indexEdgeModeResult = HelperFunctions.parseEnumValue(IndexMode.class, indexEdgeModeString, true);
		if(indexEdgeModeResult.error){
			return Result.failed("Invalid value for '" + keyIndexEdge + "': '"+indexEdgeModeString+"'", null, indexEdgeModeResult);
		}
		final IndexMode indexEdgeMode = indexEdgeModeResult.result;
		
		final String indexNamePrefixString = map.remove(keyIndexNamePrefix);
		if(HelperFunctions.isNullOrEmpty(indexNamePrefixString)){
			return Result.failed("NULL/Empty value for '" + keyIndexNamePrefix + "': '" + indexNamePrefixString + "'");
		}
		final String indexNamePrefix = indexNamePrefixString;
		
		if(indexNamePrefix.contains(" ")){
			return Result.failed("Invalid value for '"+keyIndexNamePrefix+"': '"+indexNamePrefix+"'. Must not contain <space>.");
		}
		// End - Database indexing

		// Start - Logging management
		final String reportingIntervalSecondsString = map.remove(keyReportingIntervalSeconds);
		final Result<Long> reportingIntervalSecondsResult = HelperFunctions.parseLong(reportingIntervalSecondsString, 10, Integer.MIN_VALUE, Integer.MAX_VALUE);
		if(reportingIntervalSecondsResult.error){
			return Result.failed("Invalid value for '" + keyReportingIntervalSeconds + "': '"+reportingIntervalSecondsString+"'", null, reportingIntervalSecondsResult);
		}
		final int reportingIntervalSeconds = reportingIntervalSecondsResult.result.intValue();
		final boolean reportingEnabled = reportingIntervalSeconds > 0 ? true : false;

		final String debugString = map.remove(keyDebug);
		final Result<Boolean> debugResult = HelperFunctions.parseBoolean(debugString);
		if(debugResult.error){
			return Result.failed("Invalid value for '" + keyDebug + "': '"+debugString+"'", null, debugResult);
		}
		final boolean debug = debugResult.result;

		final String timeMeString = map.remove(keyTimeMe);
		final Result<Boolean> timeMeResult = HelperFunctions.parseBoolean(timeMeString);
		if(timeMeResult.error){
			return Result.failed("Invalid value for '" + keyTimeMe + "': '" + timeMeString + "'", null, timeMeResult);
		}
		final boolean timeMe = timeMeResult.result;
		// End - Logging management
		
		// Start - Thread management (i.e. responsivity)
		final String sleepWaitMillisString = map.remove(keySleepWaitMillis);
		final Result<Long> sleepWaitMillisResult = HelperFunctions.parseLong(sleepWaitMillisString, 10, 1, Long.MAX_VALUE);
		if(sleepWaitMillisResult.error){
			return Result.failed("Invalid value for '" + keySleepWaitMillis + "': '"+sleepWaitMillisString+"'", null, sleepWaitMillisResult);
		}
		final long sleepWaitMillis = sleepWaitMillisResult.result;

		final String mainThreadSleepWaitMillisString = map.remove(keyMainThreadSleepWaitMillis);
		final Result<Long> mainThreadSleepWaitMillisResult = HelperFunctions.parseLong(mainThreadSleepWaitMillisString, 10, 0, Long.MAX_VALUE);
		if(mainThreadSleepWaitMillisResult.error){
			return Result.failed("Invalid value for '" + keyMainThreadSleepWaitMillis + "': '"+mainThreadSleepWaitMillisString+"'", null, mainThreadSleepWaitMillisResult);
		}
		final long mainThreadSleepWaitMillis = mainThreadSleepWaitMillisResult.result;
		// End - Thread management (i.e. responsivity)

		// Start - Test management
		final String testString = map.remove(keyTest);
		final Result<Boolean> testResult = HelperFunctions.parseBoolean(testString);
		if(testResult.error){
			return Result.failed("Invalid value for '" + keyTest + "': '" + testString + "'", null, testResult);
		}
		final boolean test = testResult.result;

		final String testVertexTotalString = map.remove(keyTestVertexTotal);
		final String testEdgeDegreeString = map.remove(keyTestEdgeDegree);
		final String testVertexAnnotationsString = map.remove(keyTestVertexAnnotations);
		final String testEdgeAnnotationsString = map.remove(keyTestEdgeAnnotations);

		final long testVertexTotal;
		final long testEdgeDegree;
		final ArrayList<SimpleEntry<String, String>> testVertexAnnotationsList;
		final ArrayList<SimpleEntry<String, String>> testEdgeAnnotationsList;
		if(test){
			final Result<Long> testVertexTotalResult = HelperFunctions.parseLong(testVertexTotalString, 10, 0, Long.MAX_VALUE);
			if(testVertexTotalResult.error){
				return Result.failed("Invalid value for '" + keyTestVertexTotal + "': '" + testVertexTotalString + "'", null, testVertexTotalResult);
			}
			testVertexTotal = testVertexTotalResult.result;

			final Result<Long> testEdgeDegreeResult = HelperFunctions.parseLong(testEdgeDegreeString, 10, 0, Long.MAX_VALUE);
			if(testEdgeDegreeResult.error){
				return Result.failed("Invalid value for '" + keyTestEdgeDegree + "': '" + testEdgeDegreeString + "'", null, testEdgeDegreeResult);
			}
			testEdgeDegree = testEdgeDegreeResult.result;

			if(testVertexTotal == 0 && testEdgeDegree > 0){
				return Result.failed("Invalid config. Cannot create edges greater than 0 (specified using key '"+keyTestEdgeDegree+"') with 0 vertices. "
						+ "Specify value of '" + keyTestVertexTotal + "' that is greater than 0");
			}

			final Result<ArrayList<SimpleEntry<String, String>>> testVertexAnnotationsResult = initializeTestAnnotations(testVertexAnnotationsString);
			if(testVertexAnnotationsResult.error){
				return Result.failed("Invalid value for '"+keyTestVertexAnnotations+"': '"+testVertexAnnotationsString+"'", null, testVertexAnnotationsResult);
			}
			testVertexAnnotationsList = testVertexAnnotationsResult.result; 
			
			final Result<ArrayList<SimpleEntry<String, String>>> testEdgeAnnotationsResult = initializeTestAnnotations(testEdgeAnnotationsString);
			if(testEdgeAnnotationsResult.error){
				return Result.failed("Invalid value for '"+keyTestEdgeAnnotations+"': '"+testEdgeAnnotationsString+"'", null, testEdgeAnnotationsResult);
			}
			testEdgeAnnotationsList = testEdgeAnnotationsResult.result;
		}else{
			testVertexTotal = 0;
			testEdgeDegree = 0;
			testVertexAnnotationsList = new ArrayList<SimpleEntry<String, String>>();
			testEdgeAnnotationsList = new ArrayList<SimpleEntry<String, String>>();
		}
		// End - Test management

		// The stringified map that was left
		final String extraKeysAndValues = map.toString();

		return Result.successful(
				new Configuration(
						// Storage setup management
						dbHomeDirectoryFile, 
						dbDataDirectoryName, 
						dbName, 
						finalConstructedDbPath, 
						neo4jConfigFilePath,
						// Cache management
						vertexCacheMode, 
						edgeCacheFindMode,
						// Storage buffer management
						flushBufferSize, 
						flushAfterSeconds, 
						bufferLimit,
						transactionTimeoutInSeconds,
						// Storage and database interaction management
						forceShutdown, 
						reset, 
						maxRetries, 
						waitForIndexSeconds, 
						hashKeyCollisionAction,
						// Database schema management
						hashPropertyName, 
						edgeSymbolsPropertyName,
						nodePrimaryLabelName, 
						edgeRelationshipTypeName, 
						neo4jVertexLabel, 
						neo4jEdgeRelationshipType,
						querySymbolsNodeLabelName,
						neo4jQuerySymbolsNodeLabel,
						// Database indexing
						indexVertexMode, 
						indexEdgeMode, 
						indexNamePrefix,
						// Logging management
						reportingIntervalSeconds, 
						reportingEnabled,
						debug, 
						timeMe, 
						// Thread management (i.e. responsivity)
						sleepWaitMillis, 
						mainThreadSleepWaitMillis, 
						// Test management
						test, 
						testVertexTotal, 
						testEdgeDegree, 
						testVertexAnnotationsList, 
						testEdgeAnnotationsList,
						// Extra key values in the map
						extraKeysAndValues
						));
	}
	
	@Override
	public final String toString(){
		final String newLine = "";//System.lineSeparator();
		final String configString = 
				"Configuration: "  + newLine
				// Storage setup management
				+ 		keyDbHomeDirectoryPath + "=" + dbHomeDirectoryFile.getAbsolutePath() + newLine
				+ ", " + keyDbDataDirectoryName + "=" + dbDataDirectoryName + newLine
				+ ", " + keyDbName + "=" + dbName + newLine
				+ ", " + "constructedDatabasePath" + "=" + finalConstructedDbPath.getAbsolutePath() + newLine
				+ ", " + keyNeo4jConfigFilePath + "=" + (neo4jConfigFilePath == null ? "null" : neo4jConfigFilePath.getAbsolutePath()) + newLine
				// Cache management
				+ ", " + keyVertexCacheMode + "=" + vertexCacheMode + newLine
				+ ", " + keyEdgeCacheFindMode + "=" + edgeCacheFindMode + newLine
				// Storage buffer management
				+ ", " + keyFlushBufferSize + "=" + flushBufferSize + newLine
				+ ", " + keyFlushAfterSeconds + "=" + flushAfterSeconds + newLine
				+ ", " + keyBufferLimit + "=" + bufferLimit + " (buffering:" + ((bufferLimit < 0) ? ("disabled") : ("enabled") )+ ")" + newLine
				+ ", " + keyTransactionTimeoutInSeconds + "=" + transactionTimeoutInSeconds + " (limited:" + ((transactionTimeoutInSeconds < 0) ? ("no") : ("yes") )+ ")" + newLine
				// Storage and database interaction management
				+ ", " + keyForceShutdown + "=" + forceShutdown + newLine
				+ ", " + keyReset + "=" + reset + newLine
				+ ", " + keyMaxRetries + "=" + maxRetries + newLine
				+ ", " + keyWaitForIndexSeconds + "=" + waitForIndexSeconds + newLine
				+ ", " + keyHashKeyCollisionAction + "=" + hashKeyCollisionAction + newLine
				// Database schema management
				+ ", " + keyHashPropertyName + "=" + hashPropertyName + newLine
				+ ", " + keyEdgeSymbolsPropertyName + "=" + edgeSymbolsPropertyName + newLine
				+ ", " + keyNodePrimaryLabel + "=" + nodePrimaryLabelName + " ("+neo4jVertexLabel+")" + newLine
				+ ", " + keyEdgeRelationshipType + "=" + edgeRelationshipTypeName + " ("+neo4jEdgeRelationshipType+")" + newLine
				+ ", " + keyQuerySymbolsNodeLabel + "=" + querySymbolsNodeLabelName + " ("+neo4jQuerySymbolsNodeLabel+")" + newLine
				// Database indexing
				+ ", " + keyIndexVertex + "=" + indexVertexMode + newLine
				+ ", " + keyIndexEdge + "=" + indexEdgeMode + newLine
				+ ", " + keyIndexNamePrefix + "=" + indexNamePrefix + newLine
				// Logging management
				+ ", " + keyReportingIntervalSeconds + "=" + reportingIntervalSeconds + " (" +  ( reportingEnabled ? "enabled" : "disabled" )+ ")" + newLine
				+ ", " + keyDebug + "=" + debug + newLine
				+ ", " + keyTimeMe + "=" + timeMe + newLine
				// Thread management (i.e. responsivity)
				+ ", " + keySleepWaitMillis + "=" + sleepWaitMillis + newLine
				+ ", " + keyMainThreadSleepWaitMillis + "=" + mainThreadSleepWaitMillis + newLine
				// Test management
				+ ", " + keyTest + "=" + test + newLine
				+ ", " + keyTestVertexTotal + "=" + testVertexTotal + newLine
				+ ", " + keyTestEdgeDegree + "=" + testEdgeDegree + newLine
				+ ", " + keyTestVertexAnnotations + "=" + testVertexAnnotationsList + newLine
				+ ", " + keyTestEdgeAnnotations + "=" + testEdgeAnnotationsList + newLine
				// Extra keys and values in the map
				+ ", " + "Ignored unexpected argument(s)" + "=" + extraKeysAndValues  + newLine
				;
		return configString;
	}

	public static void main(final String [] args) throws Exception{
		Result<Configuration> result = Configuration.initialize(
				null, 
				"cfg/spade.storage.Neo4j.config");
		if(result.error){
			System.out.println(result.toErrorString());
		}else{
			System.out.println(result.result);
		}
	}
}
