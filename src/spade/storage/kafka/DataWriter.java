package spade.storage.kafka;

import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.avro.Schema;
import org.apache.avro.Schema.Parser;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericContainer;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import spade.storage.Kafka;

public interface DataWriter {
	
	public void writeRecord(GenericContainer genericContainer) throws Exception;
	public void close() throws Exception;
	
	public static DataWriter getDataWriter(Properties properties) throws Exception{
		if(properties.get(Kafka.OUTPUT_FILE_KEY) != null){
			return new FileWriter(properties.getProperty(Kafka.SCHEMA_FILE_KEY), properties.getProperty(Kafka.OUTPUT_FILE_KEY));
		}else if(properties.getProperty(Kafka.KAFKA_SERVER_KEY) != null){
			return new ServerWriter(properties);
		}
		return null;
	}
	
	static class FileWriter implements DataWriter{
		
		private final int TRANSACTION_LIMIT = 1000;
	    private int transaction_count;	
				
		private DataFileWriter<Object> fileWriter;
		
		private FileWriter(String schemaFile, String outputFile) throws IOException{
			Parser parser = new Schema.Parser();
    		Schema schema = parser.parse(new File(schemaFile));
            DatumWriter<Object> datumWriter = new SpecificDatumWriter<Object>(schema);
			fileWriter = new DataFileWriter<>(datumWriter);
			fileWriter.create(schema, new File(outputFile));
		}
		
		public void writeRecord(GenericContainer genericContainer) throws Exception{
			fileWriter.append(genericContainer);
			checkTransactions();
		}
				
		public void close() throws Exception{
			fileWriter.close();
		}
		
		private void checkTransactions() {
	        transaction_count++;
	        if (transaction_count == TRANSACTION_LIMIT) {
	            try {
	                fileWriter.flush();
	                transaction_count = 0;
	            } catch (Exception exception) {
	                Logger.getLogger(FileWriter.class.getName()).log(Level.SEVERE, "Failed to flush data stream", exception);
	            }
	        }
	    }
	}
	
	static class ServerWriter implements DataWriter{
				
		private KafkaProducer<String, GenericContainer> serverWriter;
		private String kafkaTopic;
		
		private ServerWriter(Properties properties){
			this.kafkaTopic = properties.getProperty(Kafka.KAFKA_TOPIC_KEY);
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
	
}
