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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.avro.generic.GenericContainer;
import org.apache.kafka.clients.producer.ProducerConfig;

import spade.core.AbstractEdge;
import spade.core.AbstractStorage;
import spade.core.AbstractVertex;
import spade.core.Settings;
import spade.storage.kafka.DataWriter;
import spade.storage.kafka.Edge;
import spade.storage.kafka.FileWriter;
import spade.storage.kafka.GraphElement;
import spade.storage.kafka.ServerWriter;
import spade.storage.kafka.Vertex;
import spade.utility.CommonFunctions;
import spade.utility.FileUtility;

public class Kafka extends AbstractStorage{

	//NOTE: child classes must override "getDefaultKafkaProducerProperties" function if properties are different
	
	public static final String 	OUTPUT_FILE_KEY = "OutputFile",
			SCHEMA_FILE_KEY = "SchemaFilename",
			KAFKA_SERVER_KEY = "KafkaServer",
			KAFKA_TOPIC_KEY = "KafkaTopic",
			KAFKA_PRODUCER_ID_KEY = "KafkaProducerID";
	
	private static final Logger logger = Logger.getLogger(Kafka.class.getName());

	protected final String SPADE_ROOT = Settings.getProperty("spade_root");
    
    private DataWriter dataWriter = null;
    
    private String defaultConfigFilePath = Settings.getDefaultConfigFilePath(this.getClass()); //depending on the instance get the correct config file
  	
	@Override
	public boolean initialize(String arguments) {
		try {

			Properties properties = null;
			
            arguments = arguments == null ? "" : arguments.trim();
           
            Map<String, String> passedArguments = CommonFunctions.parseKeyValPairs(arguments);
            
            //if output file key exists then handle as file otherwise always set up kafka server writer i.e. kafka producer
            if(passedArguments.get(OUTPUT_FILE_KEY) != null){  
            	
            	String schemaFilename = passedArguments.get(SCHEMA_FILE_KEY);
            	schemaFilename = schemaFilename == null ? "" : schemaFilename.trim();
            	
            	if(schemaFilename.isEmpty()){
            		Map<String, String> defaultArguments = FileUtility.readConfigFileAsKeyValueMap(defaultConfigFilePath, "=");
            		schemaFilename = defaultArguments.get(SCHEMA_FILE_KEY);
              	}
            	
            	properties = new Properties();
            	properties.put(SCHEMA_FILE_KEY, schemaFilename);
            	properties.put(OUTPUT_FILE_KEY, passedArguments.get(OUTPUT_FILE_KEY));
            	
            } else {
           
	            String kafkaServer = null, kafkaProducerID = null, schemaFilename = null, kafkaTopic = null;
	            
	            if (passedArguments.containsKey("KafkaServer") && !passedArguments.get("KafkaServer").isEmpty() && passedArguments.get("KafkaServer") != null) {
	                kafkaServer = passedArguments.get("KafkaServer");
	            }
	            if (passedArguments.containsKey("KafkaTopic") && !passedArguments.get("KafkaTopic").isEmpty() && passedArguments.get("KafkaTopic") != null) {
	            	kafkaTopic = passedArguments.get("KafkaTopic");
	            }
	            if (passedArguments.containsKey("KafkaProducerID") && !passedArguments.get("KafkaProducerID").isEmpty() && passedArguments.get("KafkaProducerID") != null) {
	                kafkaProducerID = passedArguments.get("KafkaProducerID");
	            }
	            if (passedArguments.containsKey("SchemaFilename") && !passedArguments.get("SchemaFilename").isEmpty() && passedArguments.get("SchemaFilename") != null) {
	                schemaFilename = passedArguments.get("SchemaFilename");
	            }
	            
	            //if any of the values not gotten from user then get them from the default location
	            Map<String, String> defaultArguments = null;
	            if(kafkaServer == null || kafkaProducerID == null || kafkaTopic == null || schemaFilename == null){ 
	            	defaultArguments = FileUtility.readConfigFileAsKeyValueMap(defaultConfigFilePath, "=");
	            }
	            
	            if(kafkaServer == null){
	            	kafkaServer = defaultArguments.get("KafkaServer");
	            }
	            
	            if(kafkaProducerID == null){
	            	kafkaProducerID = defaultArguments.get("KafkaProducerID");
	            }
	            
	        	if(kafkaTopic == null){
	        		kafkaTopic = defaultArguments.get("KafkaTopic");
	        	}
	        	
	        	if(schemaFilename == null){
	        		schemaFilename = defaultArguments.get("SchemaFilename");
	        	}
	        	            
	            logger.log(Level.INFO,
	                    "Params: KafkaServer={0} KafkaTopic={1} KafkaProducerID={2} SchemaFilename={3}",
	                    new Object[] {kafkaServer, kafkaTopic, kafkaProducerID, schemaFilename});

            	properties = getDefaultKafkaProducerProperties(kafkaServer, kafkaServer, kafkaProducerID, schemaFilename); //depending on the instance of the class
            	
            	//add the kafka topic in the properties. To be used in the construction of ServerWriter class
            	properties.put(KAFKA_TOPIC_KEY, kafkaTopic);
            }
            
            dataWriter = getDataWriter(properties);
            
            if(dataWriter == null){
            	logger.log(Level.SEVERE, "Invalid arguments. Writer object not initialized");
            	return false;
            }
            
            return true;
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
            return false;
        }
	}
	
	public static DataWriter getDataWriter(Properties properties) throws Exception{
		if(properties.get(Kafka.OUTPUT_FILE_KEY) != null){
			return new FileWriter(properties.getProperty(Kafka.SCHEMA_FILE_KEY), properties.getProperty(Kafka.OUTPUT_FILE_KEY));
		}else if(properties.getProperty(Kafka.KAFKA_SERVER_KEY) != null){
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
	public boolean putEdge(AbstractEdge edge){
		try{
			List<GenericContainer> recordsToPublish = new ArrayList<GenericContainer>();
			Edge.Builder edgeBuilder = Edge.newBuilder();
			edgeBuilder.setAnnotations(edge.getAnnotations());
			edgeBuilder.setSourceVertexHash(String.valueOf(edge.getSourceVertex().hashCode()));
			edgeBuilder.setDestinationVertexHash(String.valueOf(edge.getDestinationVertex().hashCode()));
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
        for (GenericContainer genericContainer : genericContainers) {
            logger.log(Level.INFO,
                    "Attempting to publish record {0}", genericContainer.toString());
            try {
                dataWriter.writeRecord(genericContainer);
                recordCount += 1;
                logger.log(Level.INFO, "Sent record: ({0})", recordCount);
            } catch (Exception exception) {
                logger.log(Level.WARNING, "{0}", exception);
            } 
        }
        return recordCount;
    }
	
	public boolean shutdown(){
		try{
			dataWriter.close();
			return true;
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to close data writer", e);
			return false;
		}
	}
		
}
