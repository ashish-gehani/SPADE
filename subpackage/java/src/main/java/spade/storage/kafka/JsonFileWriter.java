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

import org.apache.avro.Schema;
import org.apache.avro.Schema.Parser;
import org.apache.avro.generic.GenericContainer;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.io.JsonEncoder;
import org.apache.avro.specific.SpecificDatumWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class JsonFileWriter implements DataWriter {

	private JsonEncoder jsonEncoder;
	private DatumWriter<Object> datumWriter;
	
	public JsonFileWriter(String schemaFile, String outputFilePath) throws Exception{
		
		File outputFile = new File(outputFilePath);
		if(outputFile == null || outputFile.getParentFile() == null || !outputFile.getParentFile().exists()){
			throw new Exception("Invalid file path: " + outputFilePath);
		}
		
		Parser parser = new Schema.Parser();
		Schema schema = parser.parse(new File(schemaFile));
		datumWriter = new SpecificDatumWriter<Object>(schema);
		OutputStream outputStream = new FileOutputStream(outputFile);
		jsonEncoder = EncoderFactory.get().jsonEncoder(schema, outputStream);
	}
	
	@Override
	public void writeRecord(GenericContainer genericContainer) throws Exception {
		datumWriter.write(genericContainer, jsonEncoder);
	}

	@Override
	public void close() throws Exception {
		jsonEncoder.flush();
	}

}
