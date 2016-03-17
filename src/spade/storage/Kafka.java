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

import com.bbn.tc.schema.avro.*;

import com.bbn.tc.schema.serialization.AvroConfig;
import com.bbn.tc.schema.serialization.kafka.KafkaAvroGenericSerializer;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericContainer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import spade.core.AbstractEdge;
import spade.core.AbstractStorage;
import spade.core.AbstractVertex;
import spade.core.Settings;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A storage implementation that serializes and sends to kafka.
 *
 * We assume the elements (vertices and edges) received are in the OPM syntax, SPADE's native format. The TC Common
 * Data Model (CDM) includes a properties field in all model elements for including aribitrary key-value
 * pairs for SPADE element annotations that do not directly map to the CDM. NOTE: For these items, we use the Prov
 * element annotation key as the CDM properties' key, even though the input from SPADE's reporter is in OPM syntax.
 *
 * We also assume that when an OPM/PROV edge is received, we can map it to an event and edges to other
 * entity records that have already been serialized and published to Kafka.
 *
 * @author Armando Caro
 */
public class Kafka extends AbstractStorage {

    // for volume stats
    private long startTime, endTime;
    private long recordCount;

    private final String SPADE_ROOT = Settings.getProperty("spade_root");

    private Schema schema;
    private KafkaAvroGenericSerializer serializer;
    private KafkaProducer<String, GenericContainer> producer;

    // default parameter values
    private String kafkaServer = "localhost:9092";
    private String kafkaTopic = "trace-topic";
    private String kafkaProducerID = "trace-producer";
    private String schemaFilename = SPADE_ROOT + "cfg/TCCDMDatum.avsc";

    @Override
    public boolean initialize(String arguments) {
        try {
            /* Note: This is not an accurate start time because we really want the first reported event,
             * but fine for now
             */
            startTime = System.currentTimeMillis();
            endTime = 0;
            recordCount = 0;

            arguments = arguments == null ? "" : arguments;
            Map<String, String> args = parseKeyValPairs(arguments);

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
            Logger.getLogger(Kafka.class.getName()).log(Level.INFO,
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
            Logger.getLogger(Kafka.class.getName()).log(Level.SEVERE, null, exception);
            return false;
        }
    }

    @Override
    public boolean putVertex(AbstractVertex vertex) {
        try {
            List<TCCDMDatum> tccdmDatums;

            String vertexType = vertex.type();
            if (vertexType.equals("Process")) {
                tccdmDatums = mapProcess(vertex);
            } else if (vertexType.equals("Artifact")) {
                tccdmDatums = mapArtifact(vertex);
            } else {
                Logger.getLogger(Kafka.class.getName()).log(Level.WARNING, "Unexpected vertex type: {0}", vertexType);
                return false;
            }

            // Now we publish the records in Kafka.
            publishRecords(tccdmDatums);
            return true;
        } catch (Exception exception) {
            Logger.getLogger(Kafka.class.getName()).log(Level.SEVERE, null, exception);
            return false;
        }
    }

    @Override
    public boolean putEdge(AbstractEdge edge) {
        try {
            List<TCCDMDatum> tccdmDatums = new LinkedList<>();
            EdgeType affectsEdgeType;

            /* Generate the Event record */
            Event.Builder eventBuilder = Event.newBuilder();
            eventBuilder.setUid(edge.hashCode());
            String time = edge.getAnnotation("time");
            if (time != null) { // XXX CDM requires timestamp
                // XXX CDM expects time as Long, SPADE wiki says it reports in ISO 8601 Z, but we see floats.
                eventBuilder.setTimestampMicros(0);
            } else {
                eventBuilder.setTimestampMicros(0);
            }
            eventBuilder.setSequence(0); // XXX not provided, but CDM requires it
            eventBuilder.setSource(InstrumentationSource.SOURCE_LINUX_AUDIT_TRACE); // XXX Is this right?
            Map<String, String> properties = new HashMap<>();
            properties.put("eventId", edge.getAnnotation("event id"));
            String edgeType = edge.type();
            String operation = edge.getAnnotation("operation");
            if (edgeType.equals("WasTriggeredBy")) {
                if (operation == null) {
                    Logger.getLogger(Kafka.class.getName()).log(Level.WARNING,
                            "NULL WasTriggeredBy/WasInformedBy operation!");
                    return false;
                } else if (operation.equals("fork")) {
                    eventBuilder.setType(EventType.EVENT_FORK);
                    affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_SUBJECT;
                } else if (operation.equals("clone")) {                             // XXX CDM doesn't support this
                    Logger.getLogger(Kafka.class.getName()).log(Level.WARNING,
                            "TC CDM does not support WasTriggeredBy/WasInformed operation: {0}", operation);
                    return false;
                } else if (operation.equals("execve")) {
                    eventBuilder.setType(EventType.EVENT_EXECUTE);
                    affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_SUBJECT;
                } else if (operation.equals("setuid")) {
                    eventBuilder.setType(EventType.EVENT_CHANGE_PRINCIPAL);
                    affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_SUBJECT;
                    /* XXX How do we capture the UID the Subject was set to?
                     * Perhaps a new HasLocalPrincipal edge? But that doesn't seem right.
                     */
                } else if (operation.equals("unit")) {                              // XXX CDM doesn't support this
                    Logger.getLogger(Kafka.class.getName()).log(Level.WARNING,
                            "TC CDM does not support WasTriggeredBy/WasInformed operation: {0}", operation);
                    return false;
                } else {
                    Logger.getLogger(Kafka.class.getName()).log(Level.WARNING,
                            "Unexpected WasTriggeredBy/WasInformedBy operation: {0}", operation);
                    return false;
                }
            } else if (edgeType.equals("WasGeneratedBy")) {
                if (operation == null) {
                    Logger.getLogger(Kafka.class.getName()).log(Level.WARNING,
                            "NULL WasGeneratedBy operation!");
                    return false;
                } else if (operation.equals("write")) {
                    eventBuilder.setType(EventType.EVENT_WRITE);
                    String size = edge.getAnnotation("size");
                    if (size != null) {
                        eventBuilder.setSize(Long.parseLong(size));
                    }
                    affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_FILE;
                } else if (operation.equals("send") || operation.equals("sendto")) {
                    // XXX CDM currently doesn't support send/sendto even type, so mapping to write.
                    eventBuilder.setType(EventType.EVENT_WRITE);
                    String size = edge.getAnnotation("size");
                    if (size != null) {
                        eventBuilder.setSize(Long.parseLong(size));
                    }
                    affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_NETFLOW;
                } else if (operation.equals("connect")) {
                    eventBuilder.setType(EventType.EVENT_CONNECT);
                    affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_NETFLOW;
                } else if (operation.equals("truncate") || operation.equals("ftruncate")) {
                    eventBuilder.setType(EventType.EVENT_TRUNCATE);
                    affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_FILE;
                } else if (operation.equals("chmod")) {
                    eventBuilder.setType(EventType.EVENT_MODIFY_FILE_ATTRIBUTES);
                    properties.put("permissions", edge.getAnnotation("permissions"));
                    affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_FILE;
                } else if (operation.equals("rename_write")) {                      // XXX CDM doesn't support this
                    Logger.getLogger(Kafka.class.getName()).log(Level.WARNING,
                            "TC CDM does not support WasGeneratedBy operation: {0}", operation);
                    return false;
                } else if (operation.equals("link_write")) {                        // XXX CDM doesn't support this
                    Logger.getLogger(Kafka.class.getName()).log(Level.WARNING,
                            "TC CDM does not support WasGeneratedBy operation: {0}", operation);
                    return false;
                } else {
                    Logger.getLogger(Kafka.class.getName()).log(Level.WARNING,
                            "Unexpected WasGeneratedBy operation: {0}", operation);
                    return false;
                }
            } else if (edgeType.equals("Used")) {
                if (operation == null) {
                    Logger.getLogger(Kafka.class.getName()).log(Level.WARNING,
                            "NULL Used operation!");
                    return false;
                } else if (operation.equals("read")) {
                    eventBuilder.setType(EventType.EVENT_READ);
                    String size = edge.getAnnotation("size");
                    if (size != null) {
                        eventBuilder.setSize(Long.parseLong(size));
                    }
                    affectsEdgeType = EdgeType.EDGE_VALUE_AFFECTS_EVENT; // XXX should be EDGE_FILE_AFFECTS_EVENT but not in CDM
                } else if (operation.equals("recv") || operation.equals("recvfrom")) { // XXX CDM doesn't support this
                    eventBuilder.setType(EventType.EVENT_READ);
                    String size = edge.getAnnotation("size");
                    if (size != null) {
                        eventBuilder.setSize(Long.parseLong(size));
                    }
                    affectsEdgeType = EdgeType.EDGE_VALUE_AFFECTS_EVENT; // XXX should be EDGE_NETFLOW_AFFECTS_EVENT but not in CDM
                } else if (operation.equals("accept")) {
                    eventBuilder.setType(EventType.EVENT_ACCEPT);
                    affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_NETFLOW; // XXX should be EDGE_NETFLOW_AFFECTS_EVENT but not in CDM
                } else if (operation.equals("rename_read")) {                       // XXX CDM doesn't support this
                    Logger.getLogger(Kafka.class.getName()).log(Level.WARNING,
                            "TC CDM does not support Used operation: {0}", operation);
                    return false;
                } else if (operation.equals("link_read")) {                         // XXX CDM doesn't support this
                    Logger.getLogger(Kafka.class.getName()).log(Level.WARNING,
                            "TC CDM does not support Used operation: {0}", operation);
                    return false;
                } else {
                    Logger.getLogger(Kafka.class.getName()).log(Level.WARNING,
                            "Unexpected Used operation: {0}", operation);
                    return false;
                }
            } else if (edgeType.equals("WasDerivedFrom")) {
                // XXX No Subject provided for EVENT_ISGENERATEDBY_SUBJECT edge
                if (operation == null) {
                    Logger.getLogger(Kafka.class.getName()).log(Level.WARNING,
                            "NULL WasDerivedBy operation!");
                    return false;
                } else if (operation.equals("update")) {                            // XXX CDM doesn't support this
                    Logger.getLogger(Kafka.class.getName()).log(Level.WARNING,
                            "TC CDM does not support WasDerivedFrom operation: {0}", operation);
                    return false;
                } else if (operation.equals("rename")) {                            // XXX CDM doesn't support this
                    Logger.getLogger(Kafka.class.getName()).log(Level.WARNING,
                            "TC CDM does not support WasDerivedFrom operation: {0}", operation);
                    return false;
                } else if (operation.equals("link")) {
                    eventBuilder.setType(EventType.EVENT_LINK);
                    affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_FILE;
                } else {
                    Logger.getLogger(Kafka.class.getName()).log(Level.WARNING,
                            "Unexpected WasDerivedFrom operation: {0}", operation);
                    return false;
                }
            } else {
                Logger.getLogger(Kafka.class.getName()).log(Level.WARNING, "Unexpected edge type: {0}", edgeType);
                return false;
            }
            eventBuilder.setProperties(properties);
            Event event = eventBuilder.build();
            tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(event).build());

            /* Generate the _*_AFFECTS_* edge record */
            SimpleEdge.Builder affectsEdgeBuilder = SimpleEdge.newBuilder();
            affectsEdgeBuilder.setFromUid(edge.hashCode());  // Event record's UID
            affectsEdgeBuilder.setToUid(edge.getSourceVertex().hashCode()); // UID of Subject/Object being affected
            affectsEdgeBuilder.setType(affectsEdgeType);
            if (time != null) { // XXX CDM requires timestamp
                // XXX CDM expects time as Long, SPADE wiki says it reports in ISO 8601 Z, but we see floats.
                affectsEdgeBuilder.setTimestamp(0);
            } else {
                affectsEdgeBuilder.setTimestamp(0);
            }
            SimpleEdge affectsEdge = affectsEdgeBuilder.build();
            tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(affectsEdge).build());

            if (edgeType.equals("WasDerivedFrom")) {
                /* Generate another _*_AFFECTS_* edge in the reverse direction */
                affectsEdgeBuilder.setFromUid(edge.getDestinationVertex().hashCode()); // UID of Object being affecting
                affectsEdgeBuilder.setToUid(edge.hashCode()); // Event record's UID
                affectsEdgeBuilder.setType(EdgeType.EDGE_VALUE_AFFECTS_EVENT); // XXX should be EDGE_FILE_AFFECTS_EVENT but not in CDM
                affectsEdge = affectsEdgeBuilder.build();
                tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(affectsEdge).build());
            } else {
                /* Generate the EVENT_ISGENERATEDBY_SUBJECT edge record */
                SimpleEdge.Builder generatedByEdgeBuilder = SimpleEdge.newBuilder();
                generatedByEdgeBuilder.setFromUid(edge.hashCode()); // Event record's UID
                generatedByEdgeBuilder.setToUid(edge.getDestinationVertex().hashCode()); //UID of Subject generating event
                generatedByEdgeBuilder.setType(EdgeType.EDGE_EVENT_ISGENERATEDBY_SUBJECT);
                if (time != null) { // XXX CDM requires timestamp
                    // XXX CDM expects time as Long, SPADE wiki says it reports in ISO 8601 Z, but we see floats.
                    generatedByEdgeBuilder.setTimestamp(0);
                } else {
                    generatedByEdgeBuilder.setTimestamp(0);
                }
                SimpleEdge generatedByEdge = generatedByEdgeBuilder.build();
                tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(generatedByEdge).build());
            }

            // Now we publish the records in Kafka.
            publishRecords(tccdmDatums);
            return true;
        } catch (Exception exception) {
            Logger.getLogger(Kafka.class.getName()).log(Level.SEVERE, null, exception);
            return false;
        }
    }

    @Override
    public boolean shutdown() {
        try {
            Logger.getLogger(Kafka.class.getName()).log(Level.INFO, "{0} records", recordCount);
            /* Note: end time is not accurate, because reporter may have ended much earlier than storage,
             * but good enough for demo purposes. If we remove storage before reporter, then we can
             * get the correct stats
             */
            endTime = System.currentTimeMillis();
            float runTime = (float) (endTime - startTime) / 1000; // # in secs
            if (runTime > 0) {
                float recordVolume = (float) recordCount / runTime; // # edges/sec

                Logger.getLogger(Kafka.class.getName()).log(Level.INFO, "Reporter runtime: {0} secs", runTime);
                Logger.getLogger(Kafka.class.getName()).log(Level.INFO, "Record volume: {0} edges/sec", recordVolume);
            }
            return true;
        } catch (Exception exception) {
            Logger.getLogger(Kafka.class.getName()).log(Level.SEVERE, null, exception);
            return false;
        }
    }

    private List<TCCDMDatum> mapProcess(AbstractVertex vertex) {
        List<TCCDMDatum> tccdmDatums = new LinkedList<>();

        /* Generate the Subject record */
        Subject.Builder subjectBuilder = Subject.newBuilder();
        subjectBuilder.setUid(vertex.hashCode());
        subjectBuilder.setType(SubjectType.SUBJECT_PROCESS);
        String activitySource = vertex.getAnnotation("source");
        if (activitySource.equals("/dev /audit")) {
            subjectBuilder.setSource(InstrumentationSource.SOURCE_LINUX_AUDIT_TRACE);
        } else if (activitySource.equals("/proc")) {
            subjectBuilder.setSource(InstrumentationSource.SOURCE_LINUX_PROC_TRACE);
        } else {
            Logger.getLogger(Kafka.class.getName()).log(Level.WARNING,
                    "Unexpected Activity source: {0}", activitySource);
            return tccdmDatums;
        }
        subjectBuilder.setStartTimestampMicros(0); // XXX not provided, but CDM requires this field
        subjectBuilder.setPid(Integer.parseInt(vertex.getAnnotation("pid")));
        subjectBuilder.setPpid(Integer.parseInt(vertex.getAnnotation("ppid")));
        String unit = vertex.getAnnotation("unit");
        if (unit != null) {
            subjectBuilder.setUnitId(Integer.parseInt(unit));
        }
        subjectBuilder.setCmdLine(vertex.getAnnotation("commandline"));           // optional, so null is ok
        Map<String, String> properties = new HashMap<>();
        properties.put("programName", vertex.getAnnotation("name"));
        properties.put("uid", vertex.getAnnotation("uid")); // user ID, not unique ID
        properties.put("group", vertex.getAnnotation("gid"));
        String cwd = vertex.getAnnotation("cwd");
        if (cwd != null) {
            properties.put("cwd", cwd);
        }
        subjectBuilder.setProperties(properties);
        Subject subject = subjectBuilder.build();
        tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(subject).build());

        /* XXX Need to create a principal to put uid and group, then check if it's new, and if so publish to Kafka.
         * Also, need to create edge to connect Subject and Principal
         */

        return tccdmDatums;
    }

    private List<TCCDMDatum> mapArtifact(AbstractVertex vertex) {
        List<TCCDMDatum> tccdmDatums = new LinkedList<>();

        InstrumentationSource source = InstrumentationSource.SOURCE_LINUX_AUDIT_TRACE; // XXX Is this right?
        AbstractObject baseObject = AbstractObject.newBuilder().setSource(source).build();
        String entityType = vertex.getAnnotation("subtype");
        if (entityType.equals("file")) {
            FileObject.Builder fileBuilder = FileObject.newBuilder();
            fileBuilder.setUid(vertex.hashCode());
            fileBuilder.setBaseObject(baseObject);
            fileBuilder.setUrl("file://" + vertex.getAnnotation("path"));
            fileBuilder.setVersion(Integer.parseInt(vertex.getAnnotation("version")));
            FileObject fileObject = fileBuilder.build();
            tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(fileObject).build());
            return tccdmDatums;
        } else if (entityType.equals("network")) {
            NetFlowObject.Builder netBuilder = NetFlowObject.newBuilder();
            netBuilder.setUid(vertex.hashCode());
            netBuilder.setBaseObject(baseObject);
            String srcAddress = vertex.getAnnotation("source host");
            if (srcAddress == null) {                                       // XXX required by CDM
                netBuilder.setSrcAddress("");
                netBuilder.setSrcPort(0);
            } else {
                netBuilder.setSrcAddress(srcAddress);
                netBuilder.setSrcPort(Integer.parseInt(vertex.getAnnotation("source port")));
            }
            String destAddress = vertex.getAnnotation("destination host");
            if (destAddress == null) {                                      // XXX required by CDM
                netBuilder.setDestAddress("");
                netBuilder.setDestPort(0);
            } else {
                netBuilder.setDestAddress(destAddress);
                netBuilder.setDestPort(Integer.parseInt(vertex.getAnnotation("destination port")));
            }
            NetFlowObject netFlowObject = netBuilder.build();
            tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(netFlowObject).build());
            return tccdmDatums;
        } else if (entityType.equals("memory")) {
            MemoryObject.Builder memoryBuilder = MemoryObject.newBuilder();
            memoryBuilder.setUid(vertex.hashCode());
            memoryBuilder.setBaseObject(baseObject);
            memoryBuilder.setPageNumber(0);                          // XXX not provided, but CDM requires it
            memoryBuilder.setMemoryAddress(Long.parseLong(vertex.getAnnotation("memory address")));
            MemoryObject memoryObject = memoryBuilder.build();
            tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(memoryObject).build());
            return tccdmDatums;
        } else if (entityType.equals("pipe")) {                             // XXX CDM doesn't support this
            Logger.getLogger(Kafka.class.getName()).log(Level.WARNING,
                    "TC CDM does not support Artifact/Entity type: {0}", entityType);
            return tccdmDatums;
        } else {
            Logger.getLogger(Kafka.class.getName()).log(Level.WARNING,
                    "Unexpected Artifact/Entity type: {0}", entityType);
            return tccdmDatums;
        }
    }

    private void publishRecords(List<TCCDMDatum> tccdmDatums) {
        /**
         * Publish the records in Kafka. Note how the serialization framework doesn't care about
         * the record type (any type from the union schema may be sent)
         */
        for (TCCDMDatum tccdmDatum : tccdmDatums) {
            String key = Long.toString(System.currentTimeMillis());
            ProducerRecord<String, GenericContainer> record
                    = new ProducerRecord<>(kafkaTopic, key, (GenericContainer) tccdmDatum);
            Logger.getLogger(Kafka.class.getName()).log(Level.INFO,
                    "Attempting to publish record {0}", tccdmDatum.toString());
            try {
                producer.send(record).get(); // synchronous send
                recordCount += 1;
                Logger.getLogger(Kafka.class.getName()).log(Level.INFO, "Sent record: ({0})", recordCount);
            } catch (InterruptedException exception) {
                Logger.getLogger(Kafka.class.getName()).log(Level.WARNING, "{0}", exception);
            } catch (ExecutionException exception) {
                Logger.getLogger(Kafka.class.getName()).log(Level.WARNING, "{0}", exception);
            }
        }
    }

    // Group 1: key
    // Group 2: value
    private static final Pattern pattern_key_value = Pattern.compile("(\\w+)=\"*((?<=\")[^\"]+(?=\")|([^\\s]+))\"*");

    /*
     * Takes a string with keyvalue pairs and returns a Map Input e.g.
     * "key1=val1 key2=val2" etc. Input string validation is callee's
     * responsiblity
     */
    private static Map<String, String> parseKeyValPairs(String messageData) {
        Matcher key_value_matcher = pattern_key_value.matcher(messageData);
        Map<String, String> keyValPairs = new HashMap<>();
        while (key_value_matcher.find()) {
            keyValPairs.put(key_value_matcher.group(1), key_value_matcher.group(2));
        }
        return keyValPairs;
    }
}
