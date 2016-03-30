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

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericContainer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;

import com.bbn.tc.schema.serialization.AvroConfig;
import com.bbn.tc.schema.serialization.kafka.KafkaAvroGenericSerializer;

import spade.core.AbstractEdge;
import spade.core.AbstractStorage;
import spade.core.AbstractVertex;
import spade.core.Settings;
import spade.storage.kafka.SpadeObject;
import spade.utility.CommonFunctions;

public class Kafka extends AbstractStorage{
	
	private static final Logger logger = Logger.getLogger(Kafka.class.getName());

	protected final String SPADE_ROOT = Settings.getProperty("spade_root");

    private Schema schema;
    private KafkaAvroGenericSerializer serializer;
    private KafkaProducer<String, GenericContainer> producer;

    // default parameter values
    private String kafkaServer = "localhost:9092";
    private String kafkaTopic = "trace-topic";
    private String kafkaProducerID = "trace-producer";
    private String schemaFilename = SPADE_ROOT + "cfg/spade.storage.Kafka.avsc";
	
	@Override
	public boolean initialize(String arguments) {
		try {

            arguments = arguments == null ? "" : arguments;
            Map<String, String> args = CommonFunctions.parseKeyValPairs(arguments);

            if (args.containsKey("kafkaServer") && !args.get("kafkaServer").isEmpty()) {
                kafkaServer = args.get("kafkaServer");
            }
            if (args.containsKey("KafkaTopic") && !args.get("KafkaTopic").isEmpty()) {
                kafkaTopic = args.get("KafkaTopic");
            }
            if (args.containsKey("KafkaProducerID") && !args.get("KafkaProducerID").isEmpty()) {
                kafkaProducerID = args.get("KafkaProducerID");
            }
            if (args.containsKey("SchemaFilename") && !args.get("SchemaFilename").isEmpty()) {
                schemaFilename = args.get("SchemaFilename");
            }
            logger.log(Level.INFO,
                    "Params: KafkaServer={0} KafkaTopic={1} KafkaProducerID={2} SchemaFilename={3}",
                    new Object[] {kafkaServer, kafkaTopic, kafkaProducerID, schemaFilename});

            Properties props = new Properties();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaServer);
            props.put(ProducerConfig.CLIENT_ID_CONFIG, kafkaProducerID);
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                    "org.apache.kafka.common.serialization.StringSerializer");
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                    com.bbn.tc.schema.serialization.kafka.KafkaAvroGenericSerializer.class);
            props.put(AvroConfig.SCHEMA_WRITER_FILE, this.schemaFilename);
            props.put(AvroConfig.SCHEMA_SERDE_IS_SPECIFIC, true);
            producer = new KafkaProducer<>(props);

            return true;
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
            return false;
        }
	}
	@Override
	public boolean putVertex(AbstractVertex vertex){
		try{
			List<GenericContainer> data = new ArrayList<GenericContainer>();
			SpadeObject o = new SpadeObject();
			o.setAnnotations(vertex.getAnnotations());
			o.setHash(String.valueOf(vertex.hashCode()));
			data.add(o);
			return publishRecords(data) > 0;
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to publish vertex : " + vertex);
			return false;
		}
	}
	
	@Override
	public boolean putEdge(AbstractEdge edge){
		try{
			List<GenericContainer> data = new ArrayList<GenericContainer>();
			SpadeObject o = new SpadeObject();
			o.setAnnotations(edge.getAnnotations());
			o.setSourceVertexHash(String.valueOf(edge.getSourceVertex().hashCode()));
			o.setDestinationVertexHash(String.valueOf(edge.getDestinationVertex().hashCode()));
			o.setHash(String.valueOf(edge.hashCode()));
			data.add(o);
			return publishRecords(data) > 0;	
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to publish edge : " + edge);
			return false;
		}
	}
	
	//function to call to save data to the storage
	protected int publishRecords(List<GenericContainer> genericContainers) {
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
		return true;
	}
	
}
