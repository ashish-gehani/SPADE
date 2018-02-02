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

package spade.storage;

import org.apache.avro.generic.GenericContainer;
import org.apache.kafka.clients.producer.ProducerConfig;
import spade.core.AbstractEdge;
import spade.core.AbstractStorage;
import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.core.Settings;
import spade.storage.kafka.DataWriter;
import spade.storage.kafka.Edge;
import spade.storage.kafka.FileWriter;
import spade.storage.kafka.GraphElement;
import spade.storage.kafka.JsonFileWriter;
import spade.storage.kafka.ServerWriter;
import spade.storage.kafka.Vertex;
import spade.utility.CommonFunctions;
import spade.utility.FileUtility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Kafka extends AbstractStorage{

	//NOTE: child classes must override "getDefaultKafkaProducerProperties" function if properties are different
	
	//change the keys in the config files of Kafka and CDM too if changed here 
	public static final String 	OUTPUT_FILE_KEY = "output",
			SCHEMA_FILE_KEY = "schema",
			SERVER_KEY = "kafkaserver",
			TOPIC_KEY = "kafkatopic",
			PRODUCER_ID_KEY = "kafkaproducerid";
	
	private static final Logger logger = Logger.getLogger(Kafka.class.getName());

	protected final String SPADE_ROOT = Settings.getProperty("spade_root");
    
    private List<DataWriter> dataWriters = new ArrayList<DataWriter>();
    
    private String defaultConfigFilePath = Settings.getDefaultConfigFilePath(this.getClass()); //depending on the instance get the correct config file
  	
    public boolean writeDataToServer(Map<String, String> args){
    	String kafkaServer = args.get(SERVER_KEY),
        		kafkaProducerID = args.get(PRODUCER_ID_KEY),
        		kafkaTopic = args.get(TOPIC_KEY);
    	return (kafkaServer != null || kafkaProducerID != null || kafkaTopic != null) || args.get(OUTPUT_FILE_KEY) == null;
    }
    
	@Override
	public boolean initialize(String arguments) {
		/*
		 * if file argument passed only then file output only
		 * if server argument passed only then server output only
		 * if file and server arguments both are passed then both outputs
		 * if no arguments passed then server output only (from default config file)		 * 
		 */
		
		try {
            arguments = arguments == null ? "" : arguments.trim();
           
            Map<String, String> passedArguments = CommonFunctions.makeKeysLowerCase(CommonFunctions.parseKeyValPairs(arguments));
            
            //if output file key exists then handle as file 
            if(passedArguments.get(OUTPUT_FILE_KEY) != null){  
            	
            	String schemaFilename = passedArguments.get(SCHEMA_FILE_KEY);
            	schemaFilename = schemaFilename == null ? "" : schemaFilename.trim();
            	
            	if(schemaFilename.isEmpty()){
            		Map<String, String> defaultArguments = CommonFunctions.makeKeysLowerCase(FileUtility.readConfigFileAsKeyValueMap(defaultConfigFilePath, "="));
            		schemaFilename = defaultArguments.get(SCHEMA_FILE_KEY);
            		if(schemaFilename == null || schemaFilename.trim().isEmpty()){
            			logger.log(Level.WARNING, "Failed to initialize storage. Missing schema file path.");
            			return false;
            		}
              	}
            	
            	Properties properties = new Properties();
            	properties.put(SCHEMA_FILE_KEY, schemaFilename);
            	properties.put(OUTPUT_FILE_KEY, passedArguments.get(OUTPUT_FILE_KEY));
            	
            	DataWriter dataWriter = getDataWriter(properties);
            	if(dataWriter == null){
            		logger.log(Level.SEVERE, "Failed to create file writer");
            		return false;
            	}else{
            		dataWriters.add(dataWriter);
            	}
            	
            } 
            
            //either when server info passed or when server info not passed and output file info not passed either
            if(writeDataToServer(passedArguments)) {
           	    
            	String kafkaServer = passedArguments.get(SERVER_KEY),
                		kafkaProducerID = passedArguments.get(PRODUCER_ID_KEY),
                		schemaFilename = passedArguments.get(SCHEMA_FILE_KEY),
                		kafkaTopic = passedArguments.get(TOPIC_KEY);
            	
	            kafkaServer = kafkaServer == null ? kafkaServer : kafkaServer.trim().isEmpty() ? null : kafkaServer;
	            kafkaProducerID = kafkaProducerID == null ? kafkaProducerID : kafkaProducerID.trim().isEmpty() ? null : kafkaProducerID;
	            schemaFilename = schemaFilename == null ? schemaFilename : schemaFilename.trim().isEmpty() ? null : schemaFilename;
	            kafkaTopic = kafkaTopic == null ? kafkaTopic : kafkaTopic.trim().isEmpty() ? null : kafkaTopic;
	            
	            //if any of the values not gotten from user then get them from the default location
	            Map<String, String> defaultArguments = null;
	            if(kafkaServer == null || kafkaProducerID == null || kafkaTopic == null || schemaFilename == null){ 
	            	defaultArguments = CommonFunctions.makeKeysLowerCase(FileUtility.readConfigFileAsKeyValueMap(defaultConfigFilePath, "="));
	            }
	            
	            if(kafkaServer == null){
	            	kafkaServer = defaultArguments.get(SERVER_KEY);
	            	if(kafkaServer == null || kafkaServer.trim().isEmpty()){
            			logger.log(Level.WARNING, "Failed to initialize storage. Missing kafka server address.");
            			return false;
            		}
	            }
	            
	            if(kafkaProducerID == null){
	            	kafkaProducerID = defaultArguments.get(PRODUCER_ID_KEY);
	            	if(kafkaProducerID == null || kafkaProducerID.trim().isEmpty()){
            			logger.log(Level.WARNING, "Failed to initialize storage. Missing kafka producer id.");
            			return false;
            		}
	            }
	            
	        	if(kafkaTopic == null){
	        		kafkaTopic = defaultArguments.get(TOPIC_KEY);
	        		if(kafkaTopic == null || kafkaTopic.trim().isEmpty()){
            			logger.log(Level.WARNING, "Failed to initialize storage. Missing kafka topic name.");
            			return false;
            		}
	        	}
	        	
	        	if(schemaFilename == null){
	        		schemaFilename = defaultArguments.get(SCHEMA_FILE_KEY);
	        		if(schemaFilename == null || schemaFilename.trim().isEmpty()){
            			logger.log(Level.WARNING, "Failed to initialize storage. Missing schema file path.");
            			return false;
            		}
	        	}
	        	
	            logger.log(Level.INFO,
	                    "Params: KafkaServer={0} KafkaTopic={1} KafkaProducerID={2} SchemaFilename={3}",
	                    new Object[] {kafkaServer, kafkaTopic, kafkaProducerID, schemaFilename});

	        	Properties properties = getDefaultKafkaProducerProperties(kafkaServer, kafkaTopic, kafkaProducerID, schemaFilename); //depending on the instance of the class
	        	
	        	//add the kafka topic and server in the properties. To be used in the construction of ServerWriter class
	        	properties.put(TOPIC_KEY, kafkaTopic);
	        	properties.put(SERVER_KEY, kafkaServer);
	        	
	        	DataWriter dataWriter = getDataWriter(properties);
	            
	            if(dataWriter == null){
	            	logger.log(Level.SEVERE, "Failed to create server writer");
	            	return false;
	            }
	            
	            dataWriters.add(dataWriter);
            }      
            
            return true;
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
            return false;
        }
	}
	
	public static DataWriter getDataWriter(Properties properties) throws Exception{
		if(properties.get(Kafka.OUTPUT_FILE_KEY) != null){
			if(String.valueOf(properties.get(Kafka.OUTPUT_FILE_KEY)).endsWith(".json")){
				return new JsonFileWriter(properties.getProperty(Kafka.SCHEMA_FILE_KEY), properties.getProperty(Kafka.OUTPUT_FILE_KEY));
			}else{
				return new FileWriter(properties.getProperty(Kafka.SCHEMA_FILE_KEY), properties.getProperty(Kafka.OUTPUT_FILE_KEY));
			}
		}else if(properties.getProperty(Kafka.SERVER_KEY) != null){
			return new ServerWriter(properties);
		}
		return null;
	}	
	
	protected Properties getDefaultKafkaProducerProperties(String kafkaServer, String kafkaTopic, String kafkaProducerID, String schemaFilename){
		Properties properties = new Properties();
		properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaServer);
		properties.put(ProducerConfig.CLIENT_ID_CONFIG, kafkaProducerID);
		properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringSerializer");
		properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
				"org.apache.kafka.common.serialization.StringSerializer");
		return properties;
	}
	
	@Override
	public boolean putVertex(AbstractVertex vertex){
		try{
			List<GenericContainer> recordsToPublish = new ArrayList<GenericContainer>();
			Vertex.Builder vertexBuilder = Vertex.newBuilder();
			vertexBuilder.setAnnotations(vertex.getAnnotations());
			vertexBuilder.setHash(String.valueOf(vertex.hashCode()));
			Vertex kafkaVertex = vertexBuilder.build();
			recordsToPublish.add(GraphElement.newBuilder().setElement(kafkaVertex).build());
			return publishRecords(recordsToPublish) > 0;
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to publish vertex : " + vertex);
			return false;
		}
	}

	@Override
	public Object executeQuery(String query)
	{
		return null;
	}

	@Override
	public boolean putEdge(AbstractEdge edge){
		try{
			List<GenericContainer> recordsToPublish = new ArrayList<GenericContainer>();
			Edge.Builder edgeBuilder = Edge.newBuilder();
			edgeBuilder.setAnnotations(edge.getAnnotations());
			edgeBuilder.setChildVertexHash(String.valueOf(edge.getChildVertex().hashCode()));
			edgeBuilder.setParentVertexHash(String.valueOf(edge.getParentVertex().hashCode()));
			edgeBuilder.setHash(String.valueOf(edge.hashCode()));
			Edge kafkaEdge = edgeBuilder.build();
			recordsToPublish.add(GraphElement.newBuilder().setElement(kafkaEdge).build());
			return publishRecords(recordsToPublish) > 0;	
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to publish edge : " + edge);
			return false;
		}
	}
	
	//function to call to save data to the storage
	protected int publishRecords(List<GenericContainer> genericContainers) {
		int recordCount = 0;
		if(genericContainers != null){
			for (GenericContainer genericContainer : genericContainers) {
				//            logger.log(Level.INFO, "Attempting to publish record {0}", genericContainer.toString());
				for(DataWriter dataWriter : dataWriters){
					try {
						dataWriter.writeRecord(genericContainer);
						recordCount += 1;
						//                    logger.log(Level.INFO, "Sent record: ({0})", recordCount);
					} catch (Exception exception) {
						logger.log(Level.INFO, "Failed to publish record {0}", genericContainer.toString());
						logger.log(Level.WARNING, "{0}", exception);
					} 
				}                
			}
		}
		return (recordCount / dataWriters.size());
	}
	
	public boolean shutdown(){
		boolean success = true;
		for(DataWriter dataWriter : dataWriters){
			try{
				dataWriter.close();
			}catch(Exception e){
				logger.log(Level.SEVERE, "Failed to close data writer", e);
				success = false;
			}
		}
		return success;
	}

	/**
	 * This function queries the underlying storage and retrieves the edge
	 * matching the given criteria.
	 *
	 * @param childVertexHash  hash of the source vertex.
	 * @param parentVertexHash hash of the destination vertex.
	 * @return returns edge object matching the given vertices OR NULL.
	 */
	@Override
	public AbstractEdge getEdge(String childVertexHash, String parentVertexHash)
	{
		return null;
	}

	/**
	 * This function queries the underlying storage and retrieves the vertex
	 * matching the given criteria.
	 *
	 * @param vertexHash hash of the vertex to find.
	 * @return returns vertex object matching the given hash OR NULL.
	 */
	@Override
	public AbstractVertex getVertex(String vertexHash)
	{
		return null;
	}

	/**
	 * This function finds the children of a given vertex.
	 * A child is defined as a vertex which is the source of a
	 * direct edge between itself and the given vertex.
	 *
	 * @param parentHash hash of the given vertex
	 * @return returns graph object containing children of the given vertex OR NULL.
	 */
	@Override
	public Graph getChildren(String parentHash)
	{
		return null;
	}

	/**
	 * This function finds the parents of a given vertex.
	 * A parent is defined as a vertex which is the destination of a
	 * direct edge between itself and the given vertex.
	 *
	 * @param childVertexHash hash of the given vertex
	 * @return returns graph object containing parents of the given vertex OR NULL.
	 */
	@Override
	public Graph getParents(String childVertexHash)
	{
		return null;
	}

}
