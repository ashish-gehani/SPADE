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
package spade.storage.kafka;

import java.io.File;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.avro.generic.GenericContainer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import spade.core.Settings;
import spade.storage.Kafka;
import spade.utility.FileUtility;

public class ServerWriter implements DataWriter{
	
	private Logger logger = Logger.getLogger(ServerWriter.class.getName());
				
	private KafkaProducer<String, GenericContainer> serverWriter;
	private String kafkaTopic;
	
	private String defaultConfigFilePath = Settings.getDefaultConfigFilePath(this.getClass());
	
	public ServerWriter(Properties properties) throws Exception{
		try{
			if(new File(defaultConfigFilePath).exists()){
				Map<String, String> additionalProperties = FileUtility.readConfigFileAsKeyValueMap(defaultConfigFilePath, "=");
				if(additionalProperties != null && additionalProperties.size() > 0){
					properties.putAll(additionalProperties);
				}
			}
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to create KafkaProducer. Failed to read config file '"+defaultConfigFilePath+"'");
			throw e;
		}
		this.kafkaTopic = properties.getProperty(Kafka.TOPIC_KEY);
		serverWriter = new KafkaProducer<>(properties);
	}
	
	public void writeRecord(GenericContainer genericContainer) throws Exception{
		/**
         * Publish the records in Kafka. Note how the serialization framework doesn't care about
         * the record type (any type from the union schema may be sent)
         */
		String key = Long.toString(System.currentTimeMillis());
        ProducerRecord<String, GenericContainer> record = new ProducerRecord<>(kafkaTopic, key, genericContainer);
        serverWriter.send(record).get(); // synchronous send
    }
	
	public void close() throws Exception{
		serverWriter.close();
	}
}