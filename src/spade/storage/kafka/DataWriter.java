package spade.storage.kafka;

import org.apache.avro.generic.GenericContainer;

public interface DataWriter {
	
	public abstract void writeRecord(GenericContainer genericContainer) throws Exception;
	public abstract void close() throws Exception;
	
}
