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