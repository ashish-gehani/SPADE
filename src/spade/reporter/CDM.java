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

import org.apache.commons.io.FileUtils;
import org.apache.jena.atlas.json.JSON;
import org.apache.jena.atlas.json.JsonArray;
import org.apache.jena.atlas.json.JsonObject;
import org.apache.jena.atlas.json.JsonValue;
import spade.core.AbstractReporter;
import spade.core.AbstractVertex;
import spade.core.Settings;
import spade.edge.cdm.SimpleEdge;
import spade.utility.BerkeleyDB;
import spade.utility.CommonFunctions;
import spade.utility.ExternalMemoryMap;
import spade.utility.FileUtility;
import spade.utility.Hasher;
import spade.vertex.cdm.Principal;
import spade.vertex.cdm.Subject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * CDM reporter that reads output of CDM json storage.
 *	
 * Assumes that all vertices are seen before the edges they are a part of.
 * If a vertex is not found then edge is not put into the buffer.
 *
 */
public class CDM extends AbstractReporter{
	
	private static final String AVRO_PACKAGE = "com.bbn.tc.schema.avro";
	private static final String AVRO_PACKAGE_UUID = AVRO_PACKAGE+".UUID";
	private static final String BASEOBJECT = "baseObject";
	private static final String PROPERTIES_MAP = "properties.map";
	private static final String BASEOBJECT_PROPERTIES_MAP = BASEOBJECT + "." + PROPERTIES_MAP;
	
	// Special annotation keys for CDM
	private static final String CDM_TYPE_KEY = "cdm.type"; //refers to types defined in CDM
	
	// Keys used in config
	private static final String CONFIG_KEY_CACHE_DATABASE_PARENT_PATH = "cacheDatabasePath",
								CONFIG_KEY_CACHE_DATABASE_NAME = "verticesDatabaseName",
								CONFIG_KEY_CACHE_SIZE = "verticesCacheSize",
								CONFIG_KEY_BLOOMFILTER_FALSE_PROBABILITY = "verticesBloomfilterFalsePositiveProbability",
								CONFIG_KEY_BLOOMFILTER_EXPECTED_ELEMENTS = "verticesBloomFilterExpectedNumberOfElements";
	
	// Special keys in CDM avro schema
	private static final String UUID_KEY = "uuid",
								EVENT_KEY_SUBJECT_UUID = "subject",
								KEY_TYPE = "type",
								EVENT_KEY_PREDICATE_OBJECT1_UUID = "predicateObject."+AVRO_PACKAGE_UUID,
								EVENT_KEY_PREDICATE_OBJECT2_UUID = "predicateObject2."+AVRO_PACKAGE_UUID,
								
								UNIT_DEPENDENCY_KEY_UNIT_UUID = "unit",
								UNIT_DEPENDENCY_KEY_DEPENDENT_UNIT_UUID = "dependentUnit",
								SUBJECT_KEY_LOCAL_PRINCIPAL = "localPrincipal",
								
								OBJECT_TYPE_EVENT = AVRO_PACKAGE+".Event",
								OBJECT_TYPE_UNIT_DEPENDENCY = AVRO_PACKAGE+".UnitDependency",
								OBJECT_TYPE_SUBJECT = AVRO_PACKAGE+".Subject",
								OBJECT_TYPE_PRINCIPAL = AVRO_PACKAGE+".Principal",
								OBJECT_TYPE_FILE_OBJECT = AVRO_PACKAGE+".FileObject",
								OBJECT_TYPE_NETFLOW_OBJECT = AVRO_PACKAGE+".NetFlowObject",
								OBJECT_TYPE_SRCSINK_OBJECT = AVRO_PACKAGE+".SrcSinkObject",
								OBJECT_TYPE_MEMORY_OBJECT = AVRO_PACKAGE+".MemoryObject",
								OBJECT_TYPE_UNNAMEDPIPE_OBJECT = AVRO_PACKAGE+".UnnamedPipeObject";
	
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
	
	private Map<String, String> rewriteKeys(Map<String, String> map){
		Map<String, String> rewritten = new HashMap<String, String>();
		for(Map.Entry<String, String> entry : map.entrySet()){
			String rewrittenKey = getRewrittenKey(entry.getKey());
			if(rewrittenKey != null && !rewrittenKey.equals(KEY_TYPE)){
				rewritten.put(rewrittenKey, entry.getValue());
			}
		}
		return rewritten;
	}
	
	private String getRewrittenKey(String key){
		switch (key) {
			// Object extra annotations in the properties map
			case BASEOBJECT_PROPERTIES_MAP+".path": return "path";
			case BASEOBJECT_PROPERTIES_MAP+".version": return "version";
			case BASEOBJECT_PROPERTIES_MAP+".pid": return "pid";
			case BASEOBJECT_PROPERTIES_MAP+".tgid": return "tgid";
			// Object baseObject values
			case BASEOBJECT+".epoch.int": return "epoch";
			case "fileDescriptor.int": return "fileDescriptor";
			case "ipProtocol.int": return "ipProtocol";
			// Event annotations
			case EVENT_KEY_PREDICATE_OBJECT1_UUID: return "predicateObject";
			case EVENT_KEY_PREDICATE_OBJECT2_UUID: return "predicateObject2";
			case "predicateObjectPath.string": return "predicateObjectPath";
			case "predicateObject2Path.string": return "predicateObject2Path";
			case "size.long": return "size";
			case "location.long": return "location";
			// Subject, Principal, Event extra annotations in the properties map
			case PROPERTIES_MAP+".mode": return "mode";
			case PROPERTIES_MAP+".protection": return "protection";
			case PROPERTIES_MAP+".pid": return "pid";
			// Subject annotations
			case PROPERTIES_MAP+".name": return "name";
			case PROPERTIES_MAP+".cwd": return "cwd";
			case PROPERTIES_MAP+".ppid": return "ppid";
			case "cid": return "pid";
			case "parentSubject."+AVRO_PACKAGE_UUID: return "parentSubject";
			case "unitId.int": return "unitId";
			case "iteration.int": return "iteration";
			case "count.int": return "count";
			case "cmdLine.string": return "cdmLine";
			// Principal annotations
			case PROPERTIES_MAP+".euid": return "euid";
			case PROPERTIES_MAP+".suid": return "suid";
			case PROPERTIES_MAP+".fsuid": return "fsuid";
			case "groupIds[0]": return "gid";
			case "groupIds[1]": return "egid";
			case "groupIds[2]": return "sgid";
			case "groupIds[3]": return "fsgid";
			case "groupIds[size]": return null;
			
			// Special case for edge
			case "properties.map.type": return null;
			
			default: return key;
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
					if(typeKey.equals(OBJECT_TYPE_UNIT_DEPENDENCY)){
						
						String sourceUuid = flattened.get(UNIT_DEPENDENCY_KEY_UNIT_UUID);
						String destinationUuid = flattened.get(UNIT_DEPENDENCY_KEY_DEPENDENT_UNIT_UUID);
						
						AbstractVertex source = uuidToVertexMap.get(sourceUuid);
						AbstractVertex destination = uuidToVertexMap.get(destinationUuid);
						
						SimpleEdge dependencyEdge = new SimpleEdge(source, destination);
						dependencyEdge.addAnnotation(CDM_TYPE_KEY, typeKey.substring(typeKey.lastIndexOf('.') + 1));
						dependencyEdge.addAnnotation(UNIT_DEPENDENCY_KEY_UNIT_UUID, sourceUuid);
						dependencyEdge.addAnnotation(UNIT_DEPENDENCY_KEY_DEPENDENT_UNIT_UUID, destinationUuid);
						putEdge(dependencyEdge);
						
					}else if(typeKey.equals(OBJECT_TYPE_EVENT)){
						String eventType = flattened.get(KEY_TYPE);
						String sourceUuid = flattened.get(EVENT_KEY_SUBJECT_UUID);
						String destination1Uuid = flattened.get(EVENT_KEY_PREDICATE_OBJECT1_UUID);
						String destination2Uuid = flattened.get(EVENT_KEY_PREDICATE_OBJECT2_UUID);
						AbstractVertex source = uuidToVertexMap.get(sourceUuid);
						AbstractVertex destination1 = uuidToVertexMap.get(destination1Uuid);
						AbstractVertex destination2 = null;
						if(destination2Uuid != null){
							destination2 = uuidToVertexMap.get(destination2Uuid);
						}
						if(source == null || destination1 == null){
							logger.log(Level.WARNING, "Failed to put edge with null source or destination: " + flattened);
						}else{

							Map<String, String> edgeAnnotations = new HashMap<String, String>();
							edgeAnnotations.put(CDM_TYPE_KEY, eventType); // Event type as cdm.key
							
							// Rewrite keys in the map here
							flattened = rewriteKeys(flattened);
							// Remove keys with null values
							flattened = removeKeysWithNullValues(flattened);
							// Add to edge annotations
							edgeAnnotations.putAll(flattened);
							
							SimpleEdge toDst1 = new SimpleEdge(source, destination1);
							// Add edge annotations
							toDst1.addAnnotations(edgeAnnotations);
							putEdge(toDst1);
							
							if(destination2 != null){
								SimpleEdge toDst2 = new SimpleEdge(source, destination2);
								toDst2.addAnnotations(edgeAnnotations);
								putEdge(toDst2);
							}
						}
					}else{
						String localPrincipalUUID = null;
						String cdmTypeKeyValue = null;
						AbstractVertex vertex = null;
						if(typeKey.equals(OBJECT_TYPE_SRCSINK_OBJECT) &&
								(flattened.get(BASEOBJECT_PROPERTIES_MAP+".start time") != null 
								|| flattened.get(BASEOBJECT_PROPERTIES_MAP+".end time") != null)){
							return; // stream marker objects
						}else if(typeKey.equals(OBJECT_TYPE_FILE_OBJECT)){
							vertex = new spade.vertex.cdm.Object();
							cdmTypeKeyValue = flattened.get(KEY_TYPE);
						}else if(typeKey.equals(OBJECT_TYPE_MEMORY_OBJECT)
								|| typeKey.equals(OBJECT_TYPE_NETFLOW_OBJECT)
								|| typeKey.equals(OBJECT_TYPE_SRCSINK_OBJECT)
								|| typeKey.equals(OBJECT_TYPE_UNNAMEDPIPE_OBJECT)){
							vertex = new spade.vertex.cdm.Object();
							cdmTypeKeyValue = typeKey.substring(typeKey.lastIndexOf('.') + 1);
						}else if(typeKey.equals(OBJECT_TYPE_PRINCIPAL)){
							vertex = new Principal();
							cdmTypeKeyValue = flattened.get(KEY_TYPE);
						}else if(typeKey.equals(OBJECT_TYPE_SUBJECT)){
							vertex = new Subject();
							cdmTypeKeyValue = flattened.get(KEY_TYPE);
							
							localPrincipalUUID = flattened.get(SUBJECT_KEY_LOCAL_PRINCIPAL);
							
						}else{
							logger.log(Level.WARNING, "Unexpected type: " + typeKey);
						}
						if(vertex != null){
							String uuid = flattened.get(UUID_KEY);
							vertex.addAnnotation(CDM_TYPE_KEY, cdmTypeKeyValue);
							flattened = rewriteKeys(flattened);
							flattened = removeKeysWithNullValues(flattened);
							vertex.addAnnotations(flattened);
							putVertex(vertex);
							uuidToVertexMap.put(uuid, vertex);
							
							if(localPrincipalUUID != null){
								AbstractVertex principalVertex = uuidToVertexMap.get(localPrincipalUUID);
								if(principalVertex != null){
									SimpleEdge localPrincipalEdge = new SimpleEdge(vertex, principalVertex);
									putEdge(localPrincipalEdge);
								}
							}
						}
					}
				}
			}
		}
	}
	
	private Map<String, String> removeKeysWithNullValues(Map<String, String> map){
		Map<String, String> removedNullsMap = new HashMap<String, String>();
		for(Map.Entry<String, String> entry : map.entrySet()){
			if(!"null".equals(entry.getValue().trim())){
				removedNullsMap.put(entry.getKey(), entry.getValue());
			}
		}
		return removedNullsMap;
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
