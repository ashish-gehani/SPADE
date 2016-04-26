package spade.storage.kafka;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.avro.Schema;
import org.apache.avro.Schema.Parser;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericContainer;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.specific.SpecificDatumWriter;

public class FileWriter implements DataWriter{
		
	private final int TRANSACTION_LIMIT = 1000;
    private int transaction_count;	
			
	private DataFileWriter<Object> fileWriter;
	
	public FileWriter(String schemaFile, String outputFile) throws IOException{
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