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
package spade.reporter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.jena.atlas.json.JSON;
import org.apache.jena.atlas.json.JsonArray;
import org.apache.jena.atlas.json.JsonObject;
import org.apache.jena.atlas.json.JsonValue;

import spade.core.AbstractEdge;
import spade.core.AbstractReporter;
import spade.core.AbstractVertex;
import spade.core.Settings;
import spade.edge.cdm.SimpleEdge;
import spade.utility.BerkeleyDB;
import spade.utility.CommonFunctions;
import spade.utility.ExternalMemoryMap;
import spade.utility.FileUtility;
import spade.utility.Hasher;
import spade.vertex.cdm.Event;
import spade.vertex.cdm.Principal;
import spade.vertex.cdm.Subject;

/**
 * CDM reporter that reads output of CDM json storage.
 *	
 * Assumes that all vertices are seen before the edges they are a part of.
 * If a vertex is not found then edge is not put into the buffer.
 *
 */
public class CDM extends AbstractReporter{
	
	// Special annotation keys for CDM
	public static final String CDM_KEY_OBJECT_TYPE = "cdm.object.type"; //refers to classname of the object
	public static final String CDM_KEY_TYPE = "cdm.type"; //refers to types defined in CDM
	// Added the ones above because we have a special key by the name 'type' already in all data model objects 
	
	// Keys used in config
	private static final String CONFIG_KEY_CACHE_DATABASE_PARENT_PATH = "cacheDatabasePath",
								CONFIG_KEY_CACHE_DATABASE_NAME = "verticesDatabaseName",
								CONFIG_KEY_CACHE_SIZE = "verticesCacheSize",
								CONFIG_KEY_BLOOMFILTER_FALSE_PROBABILITY = "verticesBloomfilterFalsePositiveProbability",
								CONFIG_KEY_BLOOMFILTER_EXPECTED_ELEMENTS = "verticesBloomFilterExpectedNumberOfElements";
	
	// Special keys in CDM avro schema
	private static final String CDM_KEY_UUID = "uuid",
								CDM_KEY_FROMUUID = "fromUuid",
								CDM_KEY_TOUUID = "toUuid",
								CDM_KEY_EDGE_OBJECT_TYPE = "com.bbn.tc.schema.avro.SimpleEdge",
								CDM_KEY_EVENT_OBJECT_TYPE = "com.bbn.tc.schema.avro.Event",
								CDM_KEY_SUBJECT_OBJECT_TYPE = "com.bbn.tc.schema.avro.Subject",
								CDM_KEY_PRINCIPAL_OBJECT_TYPE = "com.bbn.tc.schema.avro.Principal",
								CDM_KEY_FILE_OBJECT_TYPE = "com.bbn.tc.schema.avro.FileObject",
								CDM_KEY_NETFLOW_OBJECT_TYPE = "com.bbn.tc.schema.avro.NetFlowObject",
								CDM_KEY_SRCSINK_OBJECT_TYPE = "com.bbn.tc.schema.avro.SrcSinkObject",
								CDM_KEY_MEMORY_OBJECT_TYPE = "com.bbn.tc.schema.avro.MemoryObject";
	
	//Reporting variables
	private boolean reportingEnabled = false;
	private long reportEveryMs;
	private long lastReportedTime;
	private long linesRead = 0;

	private Logger logger = Logger.getLogger(this.getClass().getName());
	
	private BufferedReader fileReader;
	private volatile boolean shutdown = false;
	private final long THREAD_JOIN_WAIT = 1000; // One second
	private final long BUFFER_DRAIN_DELAY = 500;

	// External database path for the external memory map
	private String dbpath = null;
	
	// Using an external map because can grow arbitrarily
	private ExternalMemoryMap<String, AbstractVertex> uuidToVertexMap;
	
	// The main thread that processes the file
	private Thread jsonProcessorThread = new Thread(new Runnable() {
		@Override
		public void run() {
			while(!shutdown){
				String line = null;
				try{
					
					if(fileReader != null){
						line = fileReader.readLine();
						
						if(line == null){ //EOF
							break;
						}else{
							processJsonObject(getJsonObject(line));
							
							if(reportingEnabled){
								long currentTime = System.currentTimeMillis();
								if((currentTime - lastReportedTime) >= reportEveryMs){
									printStats();
									lastReportedTime = currentTime;
								}
							}
						}
						
					}else{
						logger.log(Level.SEVERE, "Invalid state. File reader not initialized. Reporter exiting.");
						shutdown = true;
						break;
					}
					
				}catch(Exception e){
					logger.log(Level.SEVERE, "Error in reading/processing line: " + line, e);
				}
			}
			
			while(getBuffer().size() > 0){
				if(shutdown){
					break;
				}
				try{
					Thread.sleep(BUFFER_DRAIN_DELAY);
				}catch(Exception e){
					//No need to log this exception
				}
				
				if(reportingEnabled){
					long currentTime = System.currentTimeMillis();
					if((currentTime - lastReportedTime) >= reportEveryMs){
						printStats();
						lastReportedTime = currentTime;
					}
				}
				
			}
			
			if(getBuffer().size() > 0){
				logger.log(Level.INFO, "File processing partially succeeded");
			}else{
				logger.log(Level.INFO, "File processing successfully succeeded");
			}
			
			try{
				if(fileReader != null){
					fileReader.close();
				}
			}catch(Exception e){
				logger.log(Level.WARNING, "Failed to close file reader", e);
			}
			
			try{
				// Close and delete the external map
				if(uuidToVertexMap != null){
					uuidToVertexMap.close();
				}
				FileUtils.forceDelete(new File(dbpath));
			}catch(Exception e){
				logger.log(Level.WARNING, "Failed to close/delete external vertex database at path '"+dbpath+"'", e);
			}
			
		}
	}, "CDM-Reporter");
		
	@Override
	public boolean launch(String arguments) {
		String filepath = arguments;
		
		if(filepath == null || filepath.trim().isEmpty()){
			logger.log(Level.SEVERE, "No filepath in arguments");
		}else{
			if(filepath.endsWith(".json")){
				File file = new File(filepath);
				if(!file.exists()){
					logger.log(Level.SEVERE, "'" + filepath + "' not found");
				}else{
					
					Map<String, String> config = null;
					
					try{
						config = FileUtility.readConfigFileAsKeyValueMap(
								Settings.getDefaultConfigFilePath(this.getClass()), "=");
					}catch(Exception e){
						logger.log(Level.SEVERE, 
								"Failed to read config file '"+Settings.getDefaultConfigFilePath(this.getClass())+"'", e);
						return false;
					}
					
					try{
						String cacheDatabaseParentDirectory = config.get(CONFIG_KEY_CACHE_DATABASE_PARENT_PATH);
						String verticesDatabaseName = config.get(CONFIG_KEY_CACHE_DATABASE_NAME);
						String verticesCacheSizeString = config.get(CONFIG_KEY_CACHE_SIZE);
						String verticesBloomFilterFalsePositiveProbabilityString = 
								config.get(CONFIG_KEY_BLOOMFILTER_FALSE_PROBABILITY);
						String verticesBloomFilterExpectedNumberOfElementsString = 
								config.get(CONFIG_KEY_BLOOMFILTER_EXPECTED_ELEMENTS);
						
						logger.log(Level.INFO, "[CDM reporter config]. "+CONFIG_KEY_CACHE_DATABASE_PARENT_PATH+": {0}, "
								+ CONFIG_KEY_CACHE_DATABASE_NAME + ": {1}, "+CONFIG_KEY_CACHE_SIZE+": {2}, "
								+ CONFIG_KEY_BLOOMFILTER_FALSE_PROBABILITY + ": {3}, "
								+ CONFIG_KEY_BLOOMFILTER_EXPECTED_ELEMENTS + ": {4}", 
								new Object[]{cacheDatabaseParentDirectory, verticesDatabaseName, verticesCacheSizeString, verticesBloomFilterFalsePositiveProbabilityString
										, verticesBloomFilterExpectedNumberOfElementsString});
						
						// Config values checks
						
						// Checking if the directory which would contain the external cache is create-able.
						try{
							File cacheDatabaseParentFile = new File(cacheDatabaseParentDirectory);
							if(!cacheDatabaseParentFile.exists()){
								FileUtils.forceMkdir(cacheDatabaseParentFile);
							}
						}catch(Exception e){
							logger.log(Level.SEVERE, "Failed to create directory '"+cacheDatabaseParentDirectory+"'", e);
							return false;
						}
						
						// Checking if the database name key existed
						if(verticesDatabaseName == null || verticesDatabaseName.trim().isEmpty()){
							logger.log(Level.SEVERE, "Must specify a non-empty value for key '"+CONFIG_KEY_CACHE_DATABASE_NAME+"' in config");
							return false;
						}
						
						// Checking if the cache size is valid
						Integer verticesCacheSize = CommonFunctions.parseInt(verticesCacheSizeString, null);
						if(verticesCacheSize == null || verticesCacheSize < 1){
							logger.log(Level.SEVERE, "'"+CONFIG_KEY_CACHE_SIZE+"' must be greater than 0");
							return false;
						}
						
						// Checking if the bloom filter false positive probability is valid
						Double verticesBloomFilterFalsePositiveProbability = CommonFunctions.parseDouble(verticesBloomFilterFalsePositiveProbabilityString, null);
						if(verticesBloomFilterFalsePositiveProbability == null || verticesBloomFilterFalsePositiveProbability < 0 || verticesBloomFilterFalsePositiveProbability > 1){
							logger.log(Level.SEVERE, "'"+CONFIG_KEY_BLOOMFILTER_FALSE_PROBABILITY+"' must be in the range [0-1]");
							return false;
						}
						
						// Checking if the bloom filter expected number of elements is valid
						Integer verticesBloomFilterExpectedNumberOfElements = CommonFunctions.parseInt(verticesBloomFilterExpectedNumberOfElementsString, null);
						if(verticesBloomFilterExpectedNumberOfElements == null || verticesBloomFilterExpectedNumberOfElements < 1){
							logger.log(Level.SEVERE, "'"+CONFIG_KEY_BLOOMFILTER_EXPECTED_ELEMENTS+"' must be greater than 0");
							return false;
						}
						
						String timestampedDBName = verticesDatabaseName + "_" + System.currentTimeMillis();
						dbpath = cacheDatabaseParentDirectory + File.separatorChar + timestampedDBName;
						try{
							FileUtils.forceMkdir(new File(dbpath));
						}catch(Exception e){
							logger.log(Level.INFO, "Failed to create directory for external store at '"+dbpath+"'", e);
							return false;
						}
						
						uuidToVertexMap = 
								new ExternalMemoryMap<String, AbstractVertex>(verticesCacheSize, 
										new BerkeleyDB<AbstractVertex>(dbpath, timestampedDBName), 
										verticesBloomFilterFalsePositiveProbability, verticesBloomFilterExpectedNumberOfElements);
						
						// Setting hash to be used as the key because saving vertices by CDM hashes
						uuidToVertexMap.setKeyHashFunction(new Hasher<String>() {
							@Override
							public String getHash(String t) {
								return t;
							}
						});
					}catch(Exception e){
						logger.log(Level.SEVERE, "Failed to create external map", e);
						return false;
					}
					
					try{
						fileReader = new BufferedReader(new FileReader(file));
						// Read the first line to make sure that it is a json file and process that object 
						JsonObject jsonObject = getJsonObject(fileReader.readLine());
						processJsonObject(jsonObject);
						
						String reportingIntervalSecondsConfig = config.get("reportingIntervalSeconds");
						if(reportingIntervalSecondsConfig != null){
							Integer reportingIntervalSeconds = CommonFunctions.parseInt(reportingIntervalSecondsConfig.trim(), null);
							if(reportingIntervalSeconds != null){
								reportingEnabled = true;
								reportEveryMs = reportingIntervalSeconds * 1000;
								lastReportedTime = System.currentTimeMillis();
							}else{
								logger.log(Level.WARNING, "Invalid reporting interval. Reporting disabled.");
							}
						}
						
						jsonProcessorThread.start();
						
						return true;
					}catch(Exception e){
						logger.log(Level.SEVERE, "Failed to read file '" + filepath + "'", e);
						return false;
					}
				}
			}else{
				logger.log(Level.SEVERE, "Non-JSON file. Must be a .json file");
			}
		}
		
		return false;
	}
	
	private void printStats(){
		Runtime runtime = Runtime.getRuntime();
		long usedMemoryMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024*1024);   	
		long internalBufferSize = getBuffer().size();
		logger.log(Level.INFO, "Lines read: {0}, Internal buffer size: {1}, JVM memory in use: {2}MB", new Object[]{linesRead, internalBufferSize, usedMemoryMB});
	}
		
	/**
	 * Strips start and end quotes only
	 * 
	 * @param string string to process
	 * @return stripped string
	 */
	private String stripStartEndQuotes(String string){
		if(string == null){
			return "";
		}else{
			string = string.trim();
			if(string.startsWith("\"")){
				string = string.substring(1);
			}
			if(string.endsWith("\"")){
				string = string.substring(0, string.length()-1);
			}
			return string;
		}
	}
	
	/**
	 * Flattens json object into a key value map
	 * 
	 * In case of arrays following is done:
	 * 
	 * {a:["hello","world"]} -> {a[0] = "hello", a[1] = "world", a[size] = 2}
	 *  
	 * @param outerObject apache jena json object
	 * @return key value map
	 */
	private Map<String, String> flattenJson(JsonObject outerObject){
		Map<String, String> flattened = new HashMap<String, String>();
				
		Set<Entry<String, JsonValue>> entrySet = outerObject.entrySet();
		for(Entry<String, JsonValue> entry : entrySet){
			String key = entry.getKey();
			JsonValue value = entry.getValue();
			if(value.isPrimitive()){
				String mapValue = stripStartEndQuotes(value.toString());
				if(mapValue != null && !mapValue.trim().isEmpty()){
					flattened.put(key, mapValue);
				}
			}else{
				if(value.isObject()){
					Map<String, String> subFlattened = flattenJson(value.getAsObject());
					for(String subkey : subFlattened.keySet()){
						String subvalue = subFlattened.get(subkey);
						String mapValue = stripStartEndQuotes(subvalue);
						if(mapValue != null && !mapValue.trim().isEmpty()){
							flattened.put(key+"."+subkey, mapValue);
						}
					}
				}else if(value.isArray()){
					JsonArray array = value.getAsArray();
					for(int a = 0; a<array.size(); a++){
						JsonValue arrayValue = array.get(a);
						JsonObject arrayObject = new JsonObject();
						arrayObject.put("["+a+"]", arrayValue);
						Map<String, String> subFlattened = flattenJson(arrayObject);
						int addCount = 0;
						for(String subkey : subFlattened.keySet()){
							String subvalue = subFlattened.get(subkey);
							String mapValue = stripStartEndQuotes(subvalue);
							if(mapValue != null && !mapValue.trim().isEmpty()){
								addCount++;
								flattened.put(key+subkey, mapValue);
							}
						}
						if(addCount > 0){
							flattened.put(key+"[size]", String.valueOf(addCount));
						}
					}
				}
			}
		}
		return flattened;
	}
	
	/**
	 * Parses json line using apache jena JSON library
	 * 
	 * Incrementing reporting variable 'linesRead' too if reporting enabled
	 * 
	 * @param line json object as string
	 * @return returns apache jena json object
	 * @throws Exception exception as returned by apache jena if malformed json
	 */
	private JsonObject getJsonObject(String line) throws Exception{
		if(reportingEnabled){
			linesRead++;
		}
		JsonObject jsonObject = JSON.parse(line);
		return jsonObject;
	}
	
	/**
	 * Puts vertices and edges to reporter's buffer after flattening them
	 * 
	 * Following 'cfg/spade.storage.CDM.avsc' schema
	 * 
	 * @param jsonObject Apache jena json object
	 * @throws Exception an unforseen exception
	 */
	private void processJsonObject(JsonObject jsonObject) throws Exception{
		JsonValue datumValue = jsonObject.get("datum");
		if(datumValue != null &&
				datumValue.isObject()){
			JsonObject datumValueObject = datumValue.getAsObject();
			if(datumValueObject.values() != null &&
					datumValueObject.values().size() > 0){
				String typeKey = datumValueObject.keys().iterator().next();
				JsonValue typeValue = datumValueObject.get(typeKey);
				if(typeKey != null && typeValue != null){
					typeKey = stripStartEndQuotes(typeKey);
					JsonObject typeValueObject = typeValue.getAsObject();
					Map<String, String> flattened = flattenJson(typeValueObject);
					if(typeKey.equals(CDM_KEY_EDGE_OBJECT_TYPE)){
						String sourceUuid = flattened.get(CDM_KEY_FROMUUID);
						String destinationUuid = flattened.get(CDM_KEY_TOUUID);
						AbstractVertex source = uuidToVertexMap.get(sourceUuid);
						AbstractVertex destination = uuidToVertexMap.get(destinationUuid);
						if(source == null || destination == null){
							logger.log(Level.WARNING, "Failed to put edge with null source or destination: " + jsonObject);
						}else{
							AbstractEdge edge = new SimpleEdge(source, destination);
							flattened.remove(CDM_KEY_FROMUUID);
							flattened.remove(CDM_KEY_TOUUID);
							flattened.put(CDM_KEY_OBJECT_TYPE, typeKey);
							if(flattened.get("type") != null){
								flattened.put(CDM_KEY_TYPE, flattened.remove("type"));
							}
							edge.addAnnotations(flattened);
							putEdge(edge);
						}
					}else{
						AbstractVertex vertex = null;
						if(typeKey.equals(CDM_KEY_EVENT_OBJECT_TYPE)){
							vertex = new Event();
						}else if(typeKey.equals(CDM_KEY_FILE_OBJECT_TYPE)
								|| typeKey.equals(CDM_KEY_MEMORY_OBJECT_TYPE)
								|| typeKey.equals(CDM_KEY_NETFLOW_OBJECT_TYPE)
								|| typeKey.equals(CDM_KEY_SRCSINK_OBJECT_TYPE)){
							vertex = new spade.vertex.cdm.Object();
						}else if(typeKey.equals(CDM_KEY_PRINCIPAL_OBJECT_TYPE)){
							vertex = new Principal();
						}else if(typeKey.equals(CDM_KEY_SUBJECT_OBJECT_TYPE)){
							vertex = new Subject();
						}else{
							logger.log(Level.WARNING, "Unexpected type: " + typeKey);
						}
						if(vertex != null){
							String uuid = flattened.remove(CDM_KEY_UUID);
							flattened.put(CDM_KEY_OBJECT_TYPE, typeKey);
							if(flattened.get("type") != null){
								flattened.put(CDM_KEY_TYPE, flattened.remove("type"));
							}
							vertex.addAnnotations(flattened);
							putVertex(vertex);
							uuidToVertexMap.put(uuid, vertex);
						}
					}
				}
			}
		}
	}
	
	@Override
	public boolean shutdown() {
		shutdown = true;
		
		try{
			
			if(jsonProcessorThread != null){
				jsonProcessorThread.join(THREAD_JOIN_WAIT);
			}
			
			return true;
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to close file reader", e);
			return false;
		}
	}
	

}
