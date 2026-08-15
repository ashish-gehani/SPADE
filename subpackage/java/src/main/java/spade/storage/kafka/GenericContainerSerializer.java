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

import java.io.ByteArrayOutputStream;
import java.util.Map;

import org.apache.avro.generic.GenericContainer;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.io.JsonEncoder;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;

/**
 * Kafka producer value serializer for spade's own Avro-generated records (e.g. GraphElement).
 * Encodes using each record's own embedded schema, so no external schema file is needed.
 */
public class GenericContainerSerializer implements Serializer<GenericContainer>{

	@Override
	public void configure(Map<String, ?> configs, boolean isKey){
		//no configuration needed
	}

	@Override
	public byte[] serialize(String topic, GenericContainer data){
		if(data == null){
			return null;
		}
		try{
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			JsonEncoder encoder = EncoderFactory.get().jsonEncoder(data.getSchema(), outputStream);
			DatumWriter<Object> datumWriter = new SpecificDatumWriter<Object>(data.getSchema());
			datumWriter.write(data, encoder);
			encoder.flush();
			return outputStream.toByteArray();
		}catch(Exception exception){
			throw new SerializationException("Failed to serialize record: " + data, exception);
		}
	}

	@Override
	public void close(){
		//nothing to close
	}

}
