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
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.avro.generic.GenericContainer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;

import spade.core.AbstractEdge;
import spade.core.AbstractStorage;
import spade.core.AbstractVertex;
import spade.core.Settings;
import spade.storage.kafka.Edge;
import spade.storage.kafka.GraphElement;
import spade.storage.kafka.Vertex;
import spade.utility.CommonFunctions;
import spade.utility.FileUtility;

public class Kafka extends AbstractStorage{

	//NOTE: child classes must override "getDefaultKafkaProducerProperties" function if properties are different
	
	private static final Logger logger = Logger.getLogger(Kafka.class.getName());

	protected final String SPADE_ROOT = Settings.getProperty("spade_root");

    private KafkaProducer<String, GenericContainer> producer;
    
    private String kafkaTopic = null;
    
    private String defaultConfigFilePath = Settings.getDefaultConfigFilePath(this.getClass()); //depending on the instance get the correct config file
  	
	@Override
	public boolean initialize(String arguments) {
		try {

            arguments = arguments == null ? "" : arguments.trim();
           
            Map<String, String> passedArguments = CommonFunctions.parseKeyValPairs(arguments);
           
            String kafkaServer = null, kafkaProducerID = null, schemaFilename = null;
            
            if (passedArguments.containsKey("KafkaServer") && !passedArguments.get("KafkaServer").isEmpty() && passedArguments.get("KafkaServer") != null) {
                kafkaServer = passedArguments.get("KafkaServer");
            }
            if (passedArguments.containsKey("KafkaTopic") && !passedArguments.get("KafkaTopic").isEmpty() && passedArguments.get("KafkaTopic") != null) {
                setKafkaTopic(passedArguments.get("KafkaTopic"));
            }
            if (passedArguments.containsKey("KafkaProducerID") && !passedArguments.get("KafkaProducerID").isEmpty() && passedArguments.get("KafkaProducerID") != null) {
                kafkaProducerID = passedArguments.get("KafkaProducerID");
            }
            if (passedArguments.containsKey("SchemaFilename") && !passedArguments.get("SchemaFilename").isEmpty() && passedArguments.get("SchemaFilename") != null) {
                schemaFilename = passedArguments.get("SchemaFilename");
            }
            
            //if any of the values not gotten from user then get them from the default location
            Map<String, String> defaultArguments = null;
            if(kafkaServer == null || kafkaProducerID == null || getKafkaTopic() == null || schemaFilename == null){ 
            	defaultArguments = FileUtility.readConfigFileAsKeyValueMap(defaultConfigFilePath, "=");
            }
            
            if(kafkaServer == null){
            	kafkaServer = defaultArguments.get("KafkaServer");
            }
            
            if(kafkaProducerID == null){
            	kafkaProducerID = defaultArguments.get("KafkaProducerID");
            }
            
        	if(getKafkaTopic() == null){
        		setKafkaTopic(defaultArguments.get("KafkaTopic"));
        	}
        	
        	if(schemaFilename == null){
        		schemaFilename = defaultArguments.get("SchemaFilename");
        	}
        	            
            logger.log(Level.INFO,
                    "Params: KafkaServer={0} KafkaTopic={1} KafkaProducerID={2} SchemaFilename={3}",
                    new Object[] {kafkaServer, getKafkaTopic(), kafkaProducerID, schemaFilename});

            Properties properties = getDefaultKafkaProducerProperties(kafkaServer, kafkaServer, kafkaProducerID, schemaFilename); //depending on the instance of the class
            producer = new KafkaProducer<>(properties);

            return true;
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
            return false;
        }
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
			return publishRecords(getKafkaTopic(), recordsToPublish) > 0;
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
			return publishRecords(getKafkaTopic(), recordsToPublish) > 0;	
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to publish edge : " + edge);
			return false;
		}
	}
	
	//function to call to save data to the storage
	protected int publishRecords(String kafkaTopic, List<GenericContainer> genericContainers) {
        /**
         * Publish the records in Kafka. Note how the serialization framework doesn't care about
         * the record type (any type from the union schema may be sent)
         */
		int recordCount = 0;
        for (GenericContainer genericContainer : genericContainers) {
            String key = Long.toString(System.currentTimeMillis());
            ProducerRecord<String, GenericContainer> record
                    = new ProducerRecord<>(kafkaTopic, key, genericContainer);
            logger.log(Level.INFO,
                    "Attempting to publish record {0}", genericContainer.toString());
            try {
                producer.send(record).get(); // synchronous send
                recordCount += 1;
                logger.log(Level.INFO, "Sent record: ({0})", recordCount);
            } catch (InterruptedException exception) {
                logger.log(Level.WARNING, "{0}", exception);
            } catch (ExecutionException exception) {
                logger.log(Level.WARNING, "{0}", exception);
            }
        }
        return recordCount;
    }
	
	public boolean shutdown(){
		producer.close();
		return true;
	}
	
	private void setKafkaTopic(String kafkaTopic){
		this.kafkaTopic = kafkaTopic;
	}
	
	protected String getKafkaTopic(){
		return kafkaTopic;
	}
	
}
