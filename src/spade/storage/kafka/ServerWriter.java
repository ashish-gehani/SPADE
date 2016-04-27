package spade.storage.kafka;

import java.util.Properties;

import org.apache.avro.generic.GenericContainer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import spade.storage.Kafka;

public class ServerWriter implements DataWriter{
				
	private KafkaProducer<String, GenericContainer> serverWriter;
	private String kafkaTopic;
	
	public ServerWriter(Properties properties){
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