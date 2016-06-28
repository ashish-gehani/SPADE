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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.avro.generic.GenericContainer;
import org.apache.kafka.clients.producer.ProducerConfig;

import com.bbn.tc.schema.avro.AbstractObject;
import com.bbn.tc.schema.avro.AbstractObject.Builder;
import com.bbn.tc.schema.avro.EdgeType;
import com.bbn.tc.schema.avro.Event;
import com.bbn.tc.schema.avro.EventType;
import com.bbn.tc.schema.avro.FileObject;
import com.bbn.tc.schema.avro.InstrumentationSource;
import com.bbn.tc.schema.avro.MemoryObject;
import com.bbn.tc.schema.avro.NetFlowObject;
import com.bbn.tc.schema.avro.Principal;
import com.bbn.tc.schema.avro.PrincipalType;
import com.bbn.tc.schema.avro.SimpleEdge;
import com.bbn.tc.schema.avro.SrcSinkObject;
import com.bbn.tc.schema.avro.SrcSinkType;
import com.bbn.tc.schema.avro.Subject;
import com.bbn.tc.schema.avro.SubjectType;
import com.bbn.tc.schema.avro.TCCDMDatum;
import com.bbn.tc.schema.avro.UUID;
import com.bbn.tc.schema.serialization.AvroConfig;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Vertex;
import spade.utility.CommonFunctions;

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
 * @author Hassaan Irshad
 * @author Ashish Gehani
 */
public class CDM extends Kafka {
	
	private static final Logger logger = Logger.getLogger(CDM.class.getName());

    // for volume stats
    private long startTime, endTime;
    private long recordCount;

    private Map<String, ProcessInformation> pidMappings = new HashMap<>();
    
    @Override
    public boolean initialize(String arguments) {
    	
    	if(super.initialize(arguments)){
    		/* Note: This is not an accurate start time because we really want the first reported event,
             * but fine for now
             */
            startTime = System.currentTimeMillis();
            endTime = 0;
            recordCount = 0;

            return true;
    	}else{
    		return false;
    	} 
    }
    
    @Override
    protected Properties getDefaultKafkaProducerProperties(String kafkaServer, String kafkaTopic, String kafkaProducerID, String schemaFilename){
		Properties properties = new Properties();
		properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaServer);
		properties.put(ProducerConfig.CLIENT_ID_CONFIG, kafkaProducerID);
		properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringSerializer");
		properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                com.bbn.tc.schema.serialization.kafka.KafkaAvroGenericSerializer.class);
		properties.put(AvroConfig.SCHEMA_WRITER_FILE, schemaFilename);
		properties.put(AvroConfig.SCHEMA_SERDE_IS_SPECIFIC, true);
		return properties;
	}

    @Override
    public boolean putVertex(AbstractVertex vertex) {
        try {
            List<GenericContainer> tccdmDatums;

            String vertexType = vertex.type();
            if (vertexType.equals("Process")) {
                tccdmDatums = mapProcess(vertex);
            } else if (vertexType.equals("Artifact")) {
                tccdmDatums = mapArtifact(vertex);
            } else {
                logger.log(Level.WARNING, "Unexpected vertex type: {0}", vertexType);
                return false;
            }

            // Now we publish the records in Kafka.
            recordCount += publishRecords(tccdmDatums);
            return true;
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
            return false;
        }
    }

    @Override
    public boolean putEdge(AbstractEdge edge) {
        try {
            List<GenericContainer> tccdmDatums = new LinkedList<GenericContainer>();
            EdgeType affectsEdgeType = null;

            /* Generate the Event record */
            Long eventId = CommonFunctions.parseLong(edge.getAnnotation("event id"), 0L); //the default event id value is decided to be 0
            Event.Builder eventBuilder = Event.newBuilder();
            eventBuilder.setUuid(getUuid(edge));
            String time = edge.getAnnotation("time");
            Long timeLong = convertTimeToMicroseconds(eventId, time, 0L);
            eventBuilder.setTimestampMicros(timeLong);
            eventBuilder.setSequence(eventId);
            
            InstrumentationSource edgeSource = getInstrumentationSource(edge.getAnnotation("source"));
            if(edgeSource == null){
            	logger.log(Level.WARNING,
                        "Unexpected Edge source: {0}. event id = {1}", new Object[]{edgeSource, eventId});
            }else{
            	eventBuilder.setSource(edgeSource);
            }
            
            String pid = null;
            
            Map<CharSequence, CharSequence> properties = new HashMap<>();
            properties.put("event id", String.valueOf(eventId));
            String edgeType = edge.type();
            String operation = edge.getAnnotation("operation");
            if (edgeType.equals("WasTriggeredBy")) {
            	pid = edge.getDestinationVertex().getAnnotation("pid");
                if (operation == null) {
                    logger.log(Level.WARNING,
                            "NULL WasTriggeredBy/WasInformedBy operation! event id = {0}", eventId);
                    return false;
                } else if(operation.equals("unknown")) {
                	eventBuilder.setType(EventType.EVENT_KERNEL_UNKNOWN);
                	affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_SUBJECT;
                } else if (operation.equals("fork")) {
                    eventBuilder.setType(EventType.EVENT_FORK);
                    affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_SUBJECT;
                } else if (operation.equals("clone")) {
                	eventBuilder.setType(EventType.EVENT_CLONE); 
                	affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_SUBJECT;
                } else if (operation.equals("execve")) {
                    eventBuilder.setType(EventType.EVENT_EXECUTE);
                    affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_SUBJECT;
                } else if (operation.equals("setuid")) {
                    eventBuilder.setType(EventType.EVENT_CHANGE_PRINCIPAL);
                    affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_SUBJECT;
                } else if (operation.equals("unit")) {   
                	eventBuilder.setType(EventType.EVENT_UNIT);
                	affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_SUBJECT;
                } else {
                    logger.log(Level.WARNING,
                            "Unexpected WasTriggeredBy/WasInformedBy operation: {0}. event id = {1}", new Object[]{operation, eventId});
                    return false;
                }
            } else if (edgeType.equals("WasGeneratedBy")) {
            	pid = edge.getDestinationVertex().getAnnotation("pid");
                if (operation == null) {
                    logger.log(Level.WARNING,
                            "NULL WasGeneratedBy operation! event id = {0}", eventId);
                    return false;
                } else if (operation.equals("open")){
                	eventBuilder.setType(EventType.EVENT_OPEN);
                	affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_FILE; //'open' only for files
                } else if(operation.equals("create")){
                	eventBuilder.setType(EventType.EVENT_CREATE_OBJECT); 
                	affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_FILE; //'create' only for files
                } else if (operation.equals("write")) {
                	eventBuilder.setType(EventType.EVENT_WRITE);
                    String size = edge.getAnnotation("size");
                    if (size != null) {
                        eventBuilder.setSize(CommonFunctions.parseLong(size, 0L));
                    }
                    String subtype = edge.getSourceVertex().getAnnotation("subtype");
                	if("memory".equals(subtype)){
                		affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_MEMORY;
                	}else if("file".equals(subtype) || "pipe".equals(subtype)){
                		affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_FILE;
                	}else if("unknown".equals(subtype)){
                		affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_SRCSINK;
                	}else if("network".equals(subtype)){
                		affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_NETFLOW;
                	}else{
                		logger.log(Level.WARNING, "Invalid source vertex subtype {0}. event id = {1}", new Object[]{subtype, eventId});
                		return false;
                	}
                } else if (operation.equals("send") || operation.equals("sendto") || operation.equals("sendmsg")) {
                	EventType eventType = null;
                	if(operation.equals("sendmsg")){
                		eventType = EventType.EVENT_SENDMSG;
                	}else if(operation.equals("sendto")){
                		eventType = EventType.EVENT_SENDTO;
                	}else{
                		eventType = EventType.EVENT_SENDMSG;
                	}
                    eventBuilder.setType(eventType);
                    String size = edge.getAnnotation("size");
                    if (size != null) {
                    	eventBuilder.setSize(CommonFunctions.parseLong(size, 0L));
                    }
                    affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_NETFLOW;
                } else if (operation.equals("mprotect")) {
                    eventBuilder.setType(EventType.EVENT_MPROTECT);
                    affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_MEMORY;
                } else if (operation.equals("connect")) {
                    eventBuilder.setType(EventType.EVENT_CONNECT);
                    affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_NETFLOW;
                } else if (operation.equals("truncate") || operation.equals("ftruncate")) {
                    eventBuilder.setType(EventType.EVENT_TRUNCATE);
                    affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_FILE;
                } else if (operation.equals("chmod")) {
                    eventBuilder.setType(EventType.EVENT_MODIFY_FILE_ATTRIBUTES);
                    properties.put("mode", edge.getAnnotation("mode"));
                    affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_FILE;
                } else if (operation.equals("rename_write")) {
                	//handled automatically in case of WasDerivedFrom 'rename' operation
                    return false;
                } else if (operation.equals("link_write")) {
                	//handled automatically in case of WasDerivedFrom 'link' operation
                    return false;
                } else if (operation.equals("mmap_write")) {
                	//handled automatically in case of WasDerivedFrom 'mmap' operation
                    return false;
                }else {
                    logger.log(Level.WARNING,
                            "Unexpected WasGeneratedBy operation: {0}. event id = {1}", new Object[]{operation, eventId});
                    return false;
                }
            } else if (edgeType.equals("Used")) {
            	pid = edge.getSourceVertex().getAnnotation("pid");
                if (operation == null) {
                    logger.log(Level.WARNING,
                            "NULL Used operation! event id = {0}", eventId);
                    return false;
                } else if(operation.equals("load")){
                	if(getExecEventUUID(pid) != null){
	                	SimpleEdge.Builder affectsEdgeBuilder = SimpleEdge.newBuilder();
	                    affectsEdgeBuilder.setFromUuid(getExecEventUUID(pid));  // Event record's UID
	                    affectsEdgeBuilder.setToUuid(getUuid(edge.getDestinationVertex())); // UID of Object being affected
	                    affectsEdgeBuilder.setType(EdgeType.EDGE_FILE_AFFECTS_EVENT);
	                    affectsEdgeBuilder.setTimestamp(timeLong);
	                    SimpleEdge affectsEdge = affectsEdgeBuilder.build();
	                    tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(affectsEdge).build());
	                    recordCount += publishRecords(tccdmDatums);
	                    return true; //no need to create an event for this so returning from here after adding the edge
                	}else{
                		logger.log(Level.WARNING, "Unable to create load edge for pid " + pid + ". event id = " + eventId);
                		return false;
                	}
                } else if (operation.equals("open")){
                	eventBuilder.setType(EventType.EVENT_OPEN);
                	affectsEdgeType = EdgeType.EDGE_FILE_AFFECTS_EVENT; //'open' only for files
                } else if (operation.equals("read")) {
                	eventBuilder.setType(EventType.EVENT_READ);
                    String size = edge.getAnnotation("size");
                    if (size != null) {
                        eventBuilder.setSize(CommonFunctions.parseLong(size, 0L));
                    }
                    String subtype = edge.getDestinationVertex().getAnnotation("subtype");
                	if("memory".equals(subtype)){
                		affectsEdgeType = EdgeType.EDGE_MEMORY_AFFECTS_EVENT;
                	}else if("file".equals(subtype) || "pipe".equals(subtype)){
                		affectsEdgeType = EdgeType.EDGE_FILE_AFFECTS_EVENT;
                	}else if("unknown".equals(subtype)){
                		affectsEdgeType = EdgeType.EDGE_SRCSINK_AFFECTS_EVENT;
                	}else if("network".equals(subtype)){
                		affectsEdgeType = EdgeType.EDGE_NETFLOW_AFFECTS_EVENT;
                	}else{
                		logger.log(Level.WARNING, "Invalid destination vertex subtype {0}. event id = {1}", new Object[]{subtype, eventId});
                		return false;
                	}
                } else if (operation.equals("recv") || operation.equals("recvfrom") || operation.equals("recvmsg")) {
                	EventType eventType = null;
                	if(operation.equals("recvmsg")){
                		eventType = EventType.EVENT_RECVMSG;
                	}else if(operation.equals("recvfrom")){
                		eventType = EventType.EVENT_RECVFROM;
                	}else{
                		eventType = EventType.EVENT_RECVMSG;
                	}
                    eventBuilder.setType(eventType);
                    String size = edge.getAnnotation("size");
                    if (size != null) {
                    	eventBuilder.setSize(CommonFunctions.parseLong(size, 0L));
                    }
                    affectsEdgeType = EdgeType.EDGE_NETFLOW_AFFECTS_EVENT;
                } else if (operation.equals("accept")) {
                    eventBuilder.setType(EventType.EVENT_ACCEPT);
                    affectsEdgeType = EdgeType.EDGE_NETFLOW_AFFECTS_EVENT;
                } else if (operation.equals("rename_read")) {
                	//handled automatically in case of WasDerivedFrom 'rename' operation
                    return false;
                } else if (operation.equals("link_read")) {
                	//handled automatically in case of WasDerivedFrom 'link' operation
                    return false;
                } else if(operation.equals("mmap_read")){
                	//handled automatically in case of WasDerivedFrom 'mmap' operation
                	return false;
                } else {
                    logger.log(Level.WARNING,
                            "Unexpected Used operation: {0}. event id = {1}", new Object[]{operation, eventId});
                    return false;
                }
            } else if (edgeType.equals("WasDerivedFrom")) {
            	pid = edge.getAnnotation("pid");
                if (operation == null) {
                    logger.log(Level.WARNING,
                            "NULL WasDerivedFrom operation! event id = {0}", eventId);
                    return false;
                } else if(operation.equals("mmap") || operation.equals("mmap2")){
                	eventBuilder.setType(EventType.EVENT_MMAP);
                    affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_MEMORY;
                } else if (operation.equals("update")) {   
                	
                	SimpleEdge.Builder affectsEdgeBuilder = SimpleEdge.newBuilder();
                    affectsEdgeBuilder.setFromUuid(getUuid(edge.getSourceVertex()));  
                    affectsEdgeBuilder.setToUuid(getUuid(edge.getDestinationVertex())); 
                    affectsEdgeBuilder.setType(EdgeType.EDGE_OBJECT_PREV_VERSION);
                    affectsEdgeBuilder.setTimestamp(timeLong);
                    SimpleEdge affectsEdge = affectsEdgeBuilder.build();
                    tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(affectsEdge).build());
                    recordCount += publishRecords(tccdmDatums);
                    return true; //no need to create an event for this so returning from here after adding the edge
                    
                } else if (operation.equals("rename")) {
                    eventBuilder.setType(EventType.EVENT_RENAME);
                    affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_FILE;
                } else if (operation.equals("link")) {
                    eventBuilder.setType(EventType.EVENT_LINK);
                    affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_FILE;
                } else {
                    logger.log(Level.WARNING,
                            "Unexpected WasDerivedFrom operation: {0}. event id = {1}", new Object[]{operation, eventId});
                    return false;
                }
            } else {
                logger.log(Level.WARNING, "Unexpected edge type: {0}. event id = {1}", new Object[]{edgeType, eventId});
                return false;
            }
            Integer pid_int = CommonFunctions.parseInt(pid, null);
            if(pid_int == null){
            	logger.log(Level.WARNING, "Unknown thread ID for event ID: {0}", eventId);
            	return false;
            }
            eventBuilder.setThreadId(pid_int);
            eventBuilder.setProperties(properties);
            Event event = eventBuilder.build();
            tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(event).build());

            /* Generate the _*_AFFECTS_* edge record */
            SimpleEdge.Builder affectsEdgeBuilder = SimpleEdge.newBuilder();
            affectsEdgeBuilder.setFromUuid(getUuid(edge));  // Event record's UID
            if(edgeType.equals("Used")){// UID of Object being affected.
            	affectsEdgeBuilder.setToUuid(getUuid(edge.getDestinationVertex()));
            }else{
            	affectsEdgeBuilder.setToUuid(getUuid(edge.getSourceVertex()));  
            }
            affectsEdgeBuilder.setType(affectsEdgeType);
            affectsEdgeBuilder.setTimestamp(timeLong);
            SimpleEdge affectsEdge = affectsEdgeBuilder.build();
            tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(affectsEdge).build());

            UUID uuidOfDestinationProcessVertex = getUuid(edge.getDestinationVertex());
            
            if (edgeType.equals("WasDerivedFrom")) {
                /* Generate another _*_AFFECTS_* edge in the reverse direction */
                affectsEdgeBuilder.setFromUuid(getUuid(edge.getDestinationVertex())); // UID of Object being affecting
                affectsEdgeBuilder.setToUuid(getUuid(edge)); // Event record's UID
                affectsEdgeBuilder.setType(EdgeType.EDGE_FILE_AFFECTS_EVENT);
                affectsEdgeBuilder.setTimestamp(timeLong);
                affectsEdge = affectsEdgeBuilder.build();
                tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(affectsEdge).build());
                
                uuidOfDestinationProcessVertex = getProcessSubjectUUID(edge.getAnnotation("pid"));
            }
            
            if(uuidOfDestinationProcessVertex != null){
	            /* Generate the EVENT_ISGENERATEDBY_SUBJECT edge record */
	            SimpleEdge.Builder generatedByEdgeBuilder = SimpleEdge.newBuilder();
	            generatedByEdgeBuilder.setFromUuid(getUuid(edge)); // Event record's UID
	            if(edgeType.equals("Used")){ //UID of Subject generating event
	            	generatedByEdgeBuilder.setToUuid(getUuid(edge.getSourceVertex()));
	            }else{
	            	generatedByEdgeBuilder.setToUuid(uuidOfDestinationProcessVertex); 
	            }
	            generatedByEdgeBuilder.setType(EdgeType.EDGE_EVENT_ISGENERATEDBY_SUBJECT);
	            generatedByEdgeBuilder.setTimestamp(timeLong);
	            SimpleEdge generatedByEdge = generatedByEdgeBuilder.build();
	            tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(generatedByEdge).build());
            }else{
            	logger.log(Level.WARNING, "Failed to find process uuid in process cache map for pid {0}. event id = {1}", new Object[]{edge.getAnnotation("pid"), eventId});
            }
            
            if(eventBuilder.getType().equals(EventType.EVENT_EXECUTE)){
            	putExecEventUUID(edge.getSourceVertex().getAnnotation("pid"), getUuid(edge));//uuid of edge is the uuid of the exec event vertex
            }

            // Now we publish the records in Kafka.
            recordCount += publishRecords(tccdmDatums);
            return true;
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
            return false;
        }
    }

    @Override
    public boolean shutdown() {
        try {
            logger.log(Level.INFO, "{0} records", recordCount);
            /* Note: end time is not accurate, because reporter may have ended much earlier than storage,
             * but good enough for demo purposes. If we remove storage before reporter, then we can
             * get the correct stats
             */
            endTime = System.currentTimeMillis();
            float runTime = (float) (endTime - startTime) / 1000; // # in secs
            if (runTime > 0) {
                float recordVolume = (float) recordCount / runTime; // # edges/sec

                logger.log(Level.INFO, "Reporter runtime: {0} secs", runTime);
                logger.log(Level.INFO, "Record volume: {0} edges/sec", recordVolume);
            }
            return super.shutdown();
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
            return false;
        }
    }
    
    private Long convertTimeToMicroseconds(Long eventId, String time, Long defaultValue){
    	//expected input time example: 12345678.678 (seconds.milliseconds)
    	try{
    		Double d = Double.parseDouble(time);
    		d = d * 1000 * 1000; //converting seconds to microseconds
    		return d.longValue();
    	}catch(Exception e){
    		logger.log(Level.INFO,
                    "Time type is not Double: {0}. event id = {1}", new Object[]{time, eventId});
    		return defaultValue;
    	}
    	//output time example: 12345678678000
    }

    private List<GenericContainer> mapProcess(AbstractVertex vertex) {
        List<GenericContainer> tccdmDatums = new LinkedList<GenericContainer>();

        /* Generate the Subject record */
        Subject.Builder subjectBuilder = Subject.newBuilder();
        subjectBuilder.setUuid(getUuid(vertex));
        subjectBuilder.setType(SubjectType.SUBJECT_PROCESS);
        InstrumentationSource activitySource = getInstrumentationSource(vertex.getAnnotation("source"));
        if (activitySource != null) {
        	subjectBuilder.setSource(activitySource); 
        } else {
            logger.log(Level.WARNING,
                    "Unexpected Activity source: {0}", activitySource);
            return tccdmDatums;
        }
        
        putProcessSubjectUUID(vertex.getAnnotation("pid"), getUuid(vertex));
        
        Long time = convertTimeToMicroseconds(null, vertex.getAnnotation("start time"), null);
        if(time != null){
        	subjectBuilder.setStartTimestampMicros(time);
        }
        subjectBuilder.setPid(Integer.parseInt(vertex.getAnnotation("pid")));
        subjectBuilder.setPpid(Integer.parseInt(vertex.getAnnotation("ppid")));
        String unit = vertex.getAnnotation("unit");
        if (unit != null) {
            subjectBuilder.setUnitId(Integer.parseInt(unit));
        }
        subjectBuilder.setCmdLine(vertex.getAnnotation("commandline"));           // optional, so null is ok
        Map<CharSequence, CharSequence> properties = new HashMap<>();
        properties.put("name", vertex.getAnnotation("name"));
        properties.put("uid", vertex.getAnnotation("uid")); // user ID, not unique ID
        properties.put("gid", vertex.getAnnotation("gid"));
        String cwd = vertex.getAnnotation("cwd");
        if (cwd != null) {
            properties.put("cwd", cwd);
        }
        subjectBuilder.setProperties(properties);
        Subject subject = subjectBuilder.build();
        tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(subject).build()); //added subject
        
        AbstractVertex principalVertex = createPrincipalVertex(vertex);
        Principal principal = createPrincipal(principalVertex);
        
        /* XXX Created a principal to put uid, euid, gid and egid in, (check if it's new or not? if new then publish) 
         * Also, creating an edge to connect Subject and Principal
         */
        
        if(principal != null){
        	tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(principal).build()); //added principal
        	
        	SimpleEdge.Builder simpleEdgeBuilder = SimpleEdge.newBuilder();
        	simpleEdgeBuilder.setFromUuid(getUuid(vertex));
        	simpleEdgeBuilder.setToUuid(getUuid(principalVertex));
        	simpleEdgeBuilder.setType(EdgeType.EDGE_SUBJECT_HASLOCALPRINCIPAL);
        	Long startTime = convertTimeToMicroseconds(null, vertex.getAnnotation("start time"), 0L);
        	simpleEdgeBuilder.setTimestamp(startTime);
        	SimpleEdge simpleEdge = simpleEdgeBuilder.build();
            tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(simpleEdge).build()); //added edge
        }
        return tccdmDatums;
    }
    
    private AbstractVertex createPrincipalVertex(AbstractVertex processVertex){
    	AbstractVertex vertex = new Vertex();
    	vertex.addAnnotation("uid", processVertex.getAnnotation("uid"));
    	vertex.addAnnotation("euid", processVertex.getAnnotation("euid"));
    	vertex.addAnnotation("gid", processVertex.getAnnotation("gid"));
    	vertex.addAnnotation("egid", processVertex.getAnnotation("egid"));
    	vertex.addAnnotation("source", processVertex.getAnnotation("source"));
    	return vertex;
    }
    
    private Principal createPrincipal(AbstractVertex principalVertex){
        try{
        	Principal.Builder principalBuilder = Principal.newBuilder();
        	principalBuilder.setUuid(getUuid(principalVertex));
            principalBuilder.setUserId(principalVertex.getAnnotation("uid"));
            Map<CharSequence, CharSequence> properties = new HashMap<CharSequence, CharSequence>();
            properties.put("egid", principalVertex.getAnnotation("egid"));
            properties.put("euid", principalVertex.getAnnotation("euid"));
            List<CharSequence> groupIds = new ArrayList<CharSequence>();
            groupIds.add(principalVertex.getAnnotation("gid"));
            principalBuilder.setGroupIds(groupIds);
            principalBuilder.setProperties(properties);
            principalBuilder.setType(PrincipalType.PRINCIPAL_LOCAL);
            InstrumentationSource source = getInstrumentationSource(principalVertex.getAnnotation("source"));
            if(source == null){
            	return null;
            }
            principalBuilder.setSource(source);
            return principalBuilder.build();
        }catch(Exception e){
        	logger.log(Level.WARNING, "Failed to create Principal from vertex: {0}", principalVertex.toString());
        	return null;
        }
    }
    
    private InstrumentationSource getInstrumentationSource(String source){
    	if("/dev/audit".equals(source)){
    		return InstrumentationSource.SOURCE_LINUX_AUDIT_TRACE;
    	}else if("/proc".equals(source)){
    		return InstrumentationSource.SOURCE_LINUX_PROC_TRACE;
    	}else if("beep".equals(source)){
    		return InstrumentationSource.SOURCE_LINUX_BEEP_TRACE;
    	}
    	return null;
    }

    private List<GenericContainer> mapArtifact(AbstractVertex vertex) {
        List<GenericContainer> tccdmDatums = new LinkedList<GenericContainer>();
        InstrumentationSource activitySource = getInstrumentationSource(vertex.getAnnotation("source"));
        Builder baseObjectBuilder = AbstractObject.newBuilder();
        if(activitySource == null){
        	logger.log(Level.WARNING,
                    "Unexpected Entity source: {0}", activitySource);
        }else{
        	baseObjectBuilder.setSource(activitySource);
        }
        AbstractObject baseObject = baseObjectBuilder.build();
        String entityType = vertex.getAnnotation("subtype");
        if (entityType.equals("file")) {
            FileObject.Builder fileBuilder = FileObject.newBuilder();
            fileBuilder.setUuid(getUuid(vertex));
            fileBuilder.setBaseObject(baseObject);
            fileBuilder.setUrl("file://" + vertex.getAnnotation("path"));
            fileBuilder.setVersion(Integer.parseInt(vertex.getAnnotation("version")));
            fileBuilder.setIsPipe(false);
            FileObject fileObject = fileBuilder.build();
            tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(fileObject).build());
            return tccdmDatums;
        } else if (entityType.equals("network")) { //not handling unix sockets yet. TODO
            NetFlowObject.Builder netBuilder = NetFlowObject.newBuilder();
            netBuilder.setUuid(getUuid(vertex));
            netBuilder.setBaseObject(baseObject);
            String srcAddress = vertex.getAnnotation("source host");
            if (srcAddress == null) {                                       // required by CDM
                netBuilder.setSrcAddress("");
                netBuilder.setSrcPort(0);
            } else {
                netBuilder.setSrcAddress(srcAddress);
                netBuilder.setSrcPort(Integer.parseInt(vertex.getAnnotation("source port")));
            }
            String destAddress = vertex.getAnnotation("destination host");
            if (destAddress == null) {                                      // required by CDM
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
        	Map<CharSequence, CharSequence> properties = new HashMap<>();
        	if(vertex.getAnnotation("size") != null){
        		properties.put("size", vertex.getAnnotation("size"));
        	}
        	if(vertex.getAnnotation("protection") != null){
        		properties.put("protection", vertex.getAnnotation("protection"));
        	}
        	if(properties.size() > 0){
        		baseObject.setProperties(properties);
        	}
            MemoryObject.Builder memoryBuilder = MemoryObject.newBuilder();
            memoryBuilder.setUuid(getUuid(vertex));
            memoryBuilder.setBaseObject(baseObject);
            // memoryBuilder.setPageNumber(0);                          // TODO remove when marked optional
            memoryBuilder.setMemoryAddress(Long.parseLong(vertex.getAnnotation("memory address"), 16));
            MemoryObject memoryObject = memoryBuilder.build();
            tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(memoryObject).build());
            return tccdmDatums;
        } else if (entityType.equals("pipe")) {                            
        	FileObject.Builder pipeBuilder = FileObject.newBuilder();
        	pipeBuilder.setUuid(getUuid(vertex));
        	pipeBuilder.setBaseObject(baseObject);
            pipeBuilder.setUrl("file://" + vertex.getAnnotation("path")); 
            pipeBuilder.setVersion(Integer.parseInt(vertex.getAnnotation("version")));
            pipeBuilder.setIsPipe(true);
            FileObject pipeObject = pipeBuilder.build();
            tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(pipeObject).build());
            return tccdmDatums;
        } else if (entityType.equals("unknown")) { //can only be file or pipe subtypes behind the scenes. include all. TODO.
        	SrcSinkObject.Builder unknownBuilder = SrcSinkObject.newBuilder();
        	Map<CharSequence, CharSequence> properties = new HashMap<>();
        	String pathTokens[] = vertex.getAnnotation("path").split("/");
        	String pid = pathTokens[1];
        	String fd = pathTokens[3];
        	properties.put("pid", pid);
        	properties.put("fd", fd);
        	properties.put("version", vertex.getAnnotation("version"));
        	baseObject.setProperties(properties);
        	unknownBuilder.setBaseObject(baseObject);
            unknownBuilder.setUuid(getUuid(vertex));
            unknownBuilder.setType(SrcSinkType.SOURCE_UNKNOWN);
            SrcSinkObject unknownObject = unknownBuilder.build();
            tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(unknownObject).build());
            return tccdmDatums;	
        } else {
            logger.log(Level.WARNING,
                    "Unexpected Artifact/Entity type: {0}", entityType);
            return tccdmDatums;
        }
    }
    
    private UUID getUuid(AbstractVertex vertex){
        return new UUID(vertex.bigHashCode());
    }
    
    private UUID getUuid(AbstractEdge edge){
        return new UUID(edge.bigHashCode());
    }
    
    private void putProcessSubjectUUID(String pid, UUID processSubjectUUID){
    	if(pidMappings.get(pid) == null){
    		pidMappings.put(pid, new ProcessInformation());
    	}
    	pidMappings.get(pid).setProcessSubjectUUID(processSubjectUUID);
    }
    
    private void putExecEventUUID(String pid, UUID execEventUUID){
    	if(pidMappings.get(pid) == null){
    		pidMappings.put(pid, new ProcessInformation());
    	}
    	pidMappings.get(pid).setExecEventUUID(execEventUUID);
    }
    
    private UUID getProcessSubjectUUID(String pid){
    	if(pidMappings.get(pid) != null){
    		return pidMappings.get(pid).getProcessSubjectUUID();
    	}
    	return null;
    }
    
    private UUID getExecEventUUID(String pid){
    	if(pidMappings.get(pid) != null){
    		return pidMappings.get(pid).getExecEventUUID();
    	}
    	return null;
    }    
}

class ProcessInformation{

	private UUID processSubjectUUID, execEventUUID; 

	public UUID getProcessSubjectUUID(){
		return processSubjectUUID;
	}
	
	public UUID getExecEventUUID(){
		return execEventUUID;
	}
	
	public void setProcessSubjectUUID(UUID processSubjectUUID){
		this.processSubjectUUID = processSubjectUUID;
	}
	
	public void setExecEventUUID(UUID execEventUUID){
		this.execEventUUID = execEventUUID;
	}
	
}
