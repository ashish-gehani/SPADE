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
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
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
import spade.reporter.audit.ArtifactIdentifier;
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
    
    // A set to keep track of principals that have been published
    private Set<UUID> principalUUIDs = new HashSet<UUID>(); 
    
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

    /**
     * Based on the subtype of artifact returns the appropriate edge type from event to artifact
     * 
     * @param vertex artifact vertex with subtype annotation
     * @return appropriate EdgeType
     */
    private EdgeType getEventAffectsArtifactEdgeType(AbstractVertex vertex){
    	String subtype = vertex.getAnnotation("subtype");
    	if("memory".equals(subtype)){
    		return EdgeType.EDGE_EVENT_AFFECTS_MEMORY;
    	}else if("file".equals(subtype) || "pipe".equals(subtype)){
    		return EdgeType.EDGE_EVENT_AFFECTS_FILE;
    	}else if("unknown".equals(subtype)){
    		return EdgeType.EDGE_EVENT_AFFECTS_SRCSINK;
    	}else if("network".equals(subtype)){
    		if(vertex.getAnnotation("path") != null){ //is a unix socket
    			return EdgeType.EDGE_EVENT_AFFECTS_FILE;
    		}
    		return EdgeType.EDGE_EVENT_AFFECTS_NETFLOW;
    	}else{
    		return null;
    	}
    }
    
    /**
     * Based on the subtype of artifact returns the appropriate edge type from artifact to event
     * 
     * @param vertex artifact vertex with subtype annotation
     * @return appropriate EdgeType
     */
    private EdgeType getArtifactAffectsEventEdgeType(AbstractVertex vertex){
    	String subtype = vertex.getAnnotation("subtype");
    	if("memory".equals(subtype)){
    		return EdgeType.EDGE_MEMORY_AFFECTS_EVENT;
    	}else if("file".equals(subtype) || "pipe".equals(subtype)){
    		return EdgeType.EDGE_FILE_AFFECTS_EVENT;
    	}else if("unknown".equals(subtype)){
    		return EdgeType.EDGE_SRCSINK_AFFECTS_EVENT;
    	}else if("network".equals(subtype)){
    		if(vertex.getAnnotation("path") != null){ //is a unix socket
    			return EdgeType.EDGE_FILE_AFFECTS_EVENT;
    		}
    		return EdgeType.EDGE_NETFLOW_AFFECTS_EVENT;
    	}else{
    		return null;
    	}
    }
    
    /**
     * Creates a simple edge
     * 
     * @param fromUuid the uuid of the source vertex
     * @param toUuid the uuid of the destination vertex
     * @param edgeType the type of the edge
     * @param time the time of the edge
     * @return a simple edge instance
     */
    private SimpleEdge createSimpleEdge(UUID fromUuid, UUID toUuid, EdgeType edgeType, Long time){
    	SimpleEdge.Builder simpleEdgeBuilder = SimpleEdge.newBuilder();
        simpleEdgeBuilder.setFromUuid(fromUuid);  // Event record's UID
        simpleEdgeBuilder.setToUuid(toUuid);  //source vertex is the child
        simpleEdgeBuilder.setType(edgeType);
        simpleEdgeBuilder.setTimestamp(time);
        SimpleEdge simpleEdge = simpleEdgeBuilder.build();
        return simpleEdge;
    }
    
    @Override
    public boolean putEdge(AbstractEdge edge) {
        try {
            List<GenericContainer> tccdmDatums = new LinkedList<GenericContainer>();
            EdgeType affectsEdgeType = null;
 
            Long eventId = CommonFunctions.parseLong(edge.getAnnotation("event id"), 0L); //the default event id value is decided to be 0
            Long time = convertTimeToMicroseconds(eventId, edge.getAnnotation("time"), 0L);
            InstrumentationSource edgeSource = getInstrumentationSource(edge.getAnnotation("source"));
            String actingProcessPidString = null;
            EventType eventType = null;
            String protectionValue = null;
            Long sizeValue = null;
            String modeValue = null;
            
            String opmEdgeType = edge.type();
            if(opmEdgeType == null){
            	logger.log(Level.WARNING,
                        "NULL OPM edge type! event id = {1}", new Object[]{opmEdgeType, eventId});
                return false;
            }
            String opmOperation = edge.getAnnotation("operation");
            if(opmOperation == null){
            	logger.log(Level.WARNING,
                        "NULL {0} operation! event id = {1}", new Object[]{opmEdgeType, eventId});
                return false;
            }
            if (opmEdgeType.equals("WasTriggeredBy")) {
            	actingProcessPidString = edge.getDestinationVertex().getAnnotation("pid"); //parent process
            	affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_SUBJECT;
            	if(opmOperation.equals("exit")){
            		eventType = EventType.EVENT_EXIT;
            	}else if(opmOperation.equals("fork")){
            		eventType = EventType.EVENT_FORK;
            	}else if(opmOperation.equals("clone")){
            		eventType = EventType.EVENT_CLONE;
            	}else if(opmOperation.equals("execve")){
            		//uuid of edge is the uuid of the exec event vertex. putting that against the pid of the new process vertex
            		putExecEventUUID(edge.getSourceVertex().getAnnotation("pid"), getUuid(edge));
            		eventType = EventType.EVENT_EXECUTE;
            	}else if(opmOperation.equals("unknown")){
            		eventType = EventType.EVENT_KERNEL_UNKNOWN;
            	}else if(opmOperation.equals("setuid")){
            		eventType = EventType.EVENT_CHANGE_PRINCIPAL;
            	}else if(opmOperation.equals("unit")){
            		eventType = EventType.EVENT_UNIT;
            	}else{
            		logger.log(Level.WARNING,
                            "Unexpected WasTriggeredBy/WasInformedBy operation: {0}. event id = {1}", new Object[]{opmOperation, eventId});
                    return false;
            	}
            } else if (opmEdgeType.equals("WasGeneratedBy")) {
            	actingProcessPidString = edge.getDestinationVertex().getAnnotation("pid");
            	affectsEdgeType = getEventAffectsArtifactEdgeType(edge.getSourceVertex());
            	if(opmOperation.equals("close")){
                	eventType = EventType.EVENT_CLOSE;
                }else if(opmOperation.equals("unlink")){
                	eventType = EventType.EVENT_UNLINK;
                }else if (opmOperation.equals("open")){
                	eventType = EventType.EVENT_OPEN;
                } else if(opmOperation.equals("create")){
                	eventType = EventType.EVENT_CREATE_OBJECT;  
                } else if (opmOperation.equals("write")) {
                	eventType = EventType.EVENT_WRITE;
                	sizeValue = CommonFunctions.parseLong(edge.getAnnotation("size"), null);
                } else if (opmOperation.equals("send") || opmOperation.equals("sendto") || opmOperation.equals("sendmsg")) {
                	if(opmOperation.equals("sendmsg")){
                		eventType = EventType.EVENT_SENDMSG;
                	}else if(opmOperation.equals("sendto")){
                		eventType = EventType.EVENT_SENDTO;
                	}else{
                		eventType = EventType.EVENT_SENDMSG;
                	}
                	sizeValue = CommonFunctions.parseLong(edge.getAnnotation("size"), null);
                } else if (opmOperation.equals("mprotect")) {
                	eventType = EventType.EVENT_MPROTECT;
                	protectionValue = edge.getAnnotation("protection");
                } else if (opmOperation.equals("connect")) {
                	eventType = EventType.EVENT_CONNECT;
                } else if (opmOperation.equals("truncate") || opmOperation.equals("ftruncate")) {
                	eventType = EventType.EVENT_TRUNCATE;
                } else if (opmOperation.equals("chmod") || opmOperation.equals("fchmod")) {
                	eventType = EventType.EVENT_MODIFY_FILE_ATTRIBUTES;
                	modeValue = edge.getAnnotation("mode");
                } else if (opmOperation.equals("rename_write")) {
                	//handled automatically in case of WasDerivedFrom 'rename' operation
                    return false;
                } else if (opmOperation.equals("link_write")) {
                	//handled automatically in case of WasDerivedFrom 'link' operation
                    return false;
                } else if (opmOperation.equals("mmap_write")) {
                	//handled automatically in case of WasDerivedFrom 'mmap' operation
                    return false;
                }else {
                    logger.log(Level.WARNING,
                            "Unexpected WasGeneratedBy operation: {0}. event id = {1}", new Object[]{opmOperation, eventId});
                    return false;
                }
            } else if (opmEdgeType.equals("Used")) {
            	actingProcessPidString = edge.getSourceVertex().getAnnotation("pid");
            	affectsEdgeType = getArtifactAffectsEventEdgeType(edge.getDestinationVertex());
            	if(opmOperation.equals("create")){
                	eventType = EventType.EVENT_CREATE_OBJECT;  
                } else if(opmOperation.equals("close")){
                	eventType = EventType.EVENT_CLOSE;
                }else if(opmOperation.equals("load")){
                	if(getExecEventUUID(actingProcessPidString) != null){
                		SimpleEdge loadEdge = createSimpleEdge(getUuid(edge.getDestinationVertex()), getExecEventUUID(actingProcessPidString),
                				EdgeType.EDGE_FILE_AFFECTS_EVENT, time);
	                    tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(loadEdge).build());
	                    recordCount += publishRecords(tccdmDatums);
	                    return true; //no need to create an event for this so returning from here after adding the edge
                	}else{
                		logger.log(Level.WARNING, "Unable to create load edge for pid " + actingProcessPidString + ". event id = " + eventId);
                		return false;
                	}
                } else if (opmOperation.equals("open")){
                	eventType = EventType.EVENT_OPEN; 
                } else if (opmOperation.equals("read")) {
                	eventType = EventType.EVENT_READ;
                	sizeValue = CommonFunctions.parseLong(edge.getAnnotation("size"), null);
                } else if (opmOperation.equals("recv") || opmOperation.equals("recvfrom") || opmOperation.equals("recvmsg")) {
                	if(opmOperation.equals("recvmsg")){
                		eventType = EventType.EVENT_RECVMSG;
                	}else if(opmOperation.equals("recvfrom")){
                		eventType = EventType.EVENT_RECVFROM;
                	}else{
                		eventType = EventType.EVENT_RECVMSG;
                	}                    
                	sizeValue = CommonFunctions.parseLong(edge.getAnnotation("size"), null);
                } else if (opmOperation.equals("accept") || opmOperation.equals("accept4")) {
                	eventType = EventType.EVENT_ACCEPT;
                } else if (opmOperation.equals("rename_read")) {
                	//handled automatically in case of WasDerivedFrom 'rename' operation
                    return false;
                } else if (opmOperation.equals("link_read")) {
                	//handled automatically in case of WasDerivedFrom 'link' operation
                    return false;
                } else if(opmOperation.equals("mmap_read")){
                	//handled automatically in case of WasDerivedFrom 'mmap' operation
                	return false;
                } else {
                    logger.log(Level.WARNING,
                            "Unexpected Used operation: {0}. event id = {1}", new Object[]{opmOperation, eventId});
                    return false;
                }
            } else if (opmEdgeType.equals("WasDerivedFrom")) {
            	actingProcessPidString = edge.getAnnotation("pid");
            	affectsEdgeType = getEventAffectsArtifactEdgeType(edge.getSourceVertex());
                if(opmOperation.equals("mmap") || opmOperation.equals("mmap2")){
                	eventType = EventType.EVENT_MMAP;
                	protectionValue = edge.getAnnotation("protection");
                } else if (opmOperation.equals("update")) {   
                	SimpleEdge updateEdge = createSimpleEdge(getUuid(edge.getSourceVertex()), getUuid(edge.getDestinationVertex()), 
                			EdgeType.EDGE_OBJECT_PREV_VERSION, time);
                    tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(updateEdge).build());
                    recordCount += publishRecords(tccdmDatums);
                    return true; //no need to create an event for this so returning from here after adding the edge
                } else if (opmOperation.equals("rename")) {
                	eventType = EventType.EVENT_RENAME;
                } else if (opmOperation.equals("link")) {
                	eventType = EventType.EVENT_LINK;
                } else {
                    logger.log(Level.WARNING,
                            "Unexpected WasDerivedFrom operation: {0}. event id = {1}", new Object[]{opmOperation, eventId});
                    return false;
                }
            } else {
                logger.log(Level.WARNING, "Unexpected edge type: {0}. event id = {1}", new Object[]{opmEdgeType, eventId});
                return false;
            }
            
            Integer actingProcessPid = CommonFunctions.parseInt(actingProcessPidString, null);
            if(actingProcessPid == null){
            	logger.log(Level.WARNING, "Unknown thread ID for event ID: {0}", eventId);
            	return false;
            }
            Map<CharSequence, CharSequence> eventProperties = new HashMap<>();
            if(eventId != null){
            	eventProperties.put("event id", String.valueOf(eventId));
            }
            if(protectionValue != null){
            	eventProperties.put("protection", protectionValue);
            }
            if(modeValue != null){
            	eventProperties.put("mode", modeValue);
            }
            
            /* Generate the Event record */
            Event.Builder eventBuilder = Event.newBuilder();
            if(sizeValue != null){
            	eventBuilder.setSize(sizeValue);
            }
            eventBuilder.setUuid(getUuid(edge)); //set uuid
            eventBuilder.setType(eventType);
            eventBuilder.setTimestampMicros(time);
            eventBuilder.setSequence(eventId);
            eventBuilder.setSource(edgeSource);
            eventBuilder.setProperties(eventProperties);
            eventBuilder.setThreadId(actingProcessPid);
            Event event = eventBuilder.build();
            tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(event).build());

            /* Generate the _*_AFFECTS_* edge record */
            UUID uuidOfProcessVertex = null;
            SimpleEdge affectsEdge = null; 
            if(opmEdgeType.equals("Used")){ //artifact affects event
            	affectsEdge = createSimpleEdge(getUuid(edge.getDestinationVertex()), getUuid(edge), 
            			affectsEdgeType, time);
            	uuidOfProcessVertex = getUuid(edge.getSourceVertex()); //getting the source because that is the process
            }else{ //event affects artifact
            	affectsEdge = createSimpleEdge(getUuid(edge), getUuid(edge.getSourceVertex()), 
            			affectsEdgeType, time);
            	uuidOfProcessVertex = getUuid(edge.getDestinationVertex()); //getting the destination because that is the process
            }
            tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(affectsEdge).build());
            
            if (opmEdgeType.equals("WasDerivedFrom")) {
                /* Generate another _*_AFFECTS_* edge in the reverse direction */
            	SimpleEdge affectsEventEdge = createSimpleEdge(getUuid(edge.getDestinationVertex()), getUuid(edge), 
            			getArtifactAffectsEventEdgeType(edge.getDestinationVertex()), time);
                tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(affectsEventEdge).build());
                
                uuidOfProcessVertex = getProcessSubjectUUID(String.valueOf(actingProcessPid));
            }
            
            if(uuidOfProcessVertex != null){
	            /* Generate the EVENT_ISGENERATEDBY_SUBJECT edge record */
            	SimpleEdge eventToProcessEdge = createSimpleEdge(getUuid(edge), uuidOfProcessVertex, 
            			EdgeType.EDGE_EVENT_ISGENERATEDBY_SUBJECT, time);
	            tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(eventToProcessEdge).build());
            }else{
            	logger.log(Level.WARNING, "Failed to find process uuid in process cache map for pid {0}. event id = {1}", new Object[]{edge.getAnnotation("pid"), eventId});
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
            if(time == null){
                return defaultValue;
            }
    		Double timeMicroseconds = Double.parseDouble(time);
    		timeMicroseconds = timeMicroseconds * 1000 * 1000; //converting seconds to microseconds
    		return timeMicroseconds.longValue();
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
                    "Unexpected Activity source: {0}", vertex.getAnnotation("source"));
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
        subjectBuilder.setCmdLine(vertex.getAnnotation("commandline")); // optional, so null is ok
        Map<CharSequence, CharSequence> properties = new HashMap<>();
        String iteration = vertex.getAnnotation("iteration");
        if(iteration != null){
        	properties.put("iteration", iteration);
        }
        String count = vertex.getAnnotation("count");
        if(count != null){
        	properties.put("count", count);
        }
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
        UUID principalVertexUUID = getUuid(principalVertex);
        
        // Add principal vertex only if it hasn't been seen before. 
        // Only added when seen for the first time
        if(!principalUUIDs.contains(principalVertexUUID)){
        	Principal principal = createPrincipal(principalVertex);
        	if(principal != null){
        		tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(principal).build());
        		principalUUIDs.add(principalVertexUUID);
        	}
        }
        
        // Making sure that the principal vertex was published successfully
        if(principalUUIDs.contains(principalVertexUUID)){
	        SimpleEdge simpleEdge = createSimpleEdge(getUuid(vertex), principalVertexUUID, 
	        		EdgeType.EDGE_SUBJECT_HASLOCALPRINCIPAL, 
	        		convertTimeToMicroseconds(null, vertex.getAnnotation("start time"), 0L));
	        tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(simpleEdge).build());
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
    	}else{
    		logger.log(Level.WARNING,
                    "Unexpected source: {0}", new Object[]{source});
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
        if (entityType.equals(ArtifactIdentifier.SUBTYPE_FILE)) {
            FileObject.Builder fileBuilder = FileObject.newBuilder();
            fileBuilder.setUuid(getUuid(vertex));
            fileBuilder.setBaseObject(baseObject);
            fileBuilder.setUrl("file://" + vertex.getAnnotation("path"));
            fileBuilder.setVersion(Integer.parseInt(vertex.getAnnotation("version")));
            Map<CharSequence, CharSequence> properties = new HashMap<>();
            if(vertex.getAnnotation("epoch") != null){
            	properties.put("epoch", vertex.getAnnotation("epoch"));
            }
            if(properties.size() > 0){
            	baseObject.setProperties(properties);
            }
            fileBuilder.setIsPipe(false);
            FileObject fileObject = fileBuilder.build();
            tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(fileObject).build());
            return tccdmDatums;
        } else if (entityType.equals(ArtifactIdentifier.SUBTYPE_SOCKET)) { //not handling unix sockets yet. TODO
            if(vertex.getAnnotation("path") != null){

            	//TODO should do?
            	FileObject.Builder unixSocketBuilder = FileObject.newBuilder();
                unixSocketBuilder.setUuid(getUuid(vertex));
                unixSocketBuilder.setBaseObject(baseObject);
                unixSocketBuilder.setUrl("file://" + vertex.getAnnotation("path"));
                unixSocketBuilder.setVersion(Integer.parseInt(vertex.getAnnotation("version")));
                Map<CharSequence, CharSequence> properties = new HashMap<>();
                if(vertex.getAnnotation("epoch") != null){
                	properties.put("epoch", vertex.getAnnotation("epoch"));
                }
                properties.put("isUnixSocket", "true");
                if(properties.size() > 0){
                	baseObject.setProperties(properties);
                }
                unixSocketBuilder.setIsPipe(false);
                FileObject uniSocketObject = unixSocketBuilder.build();
                tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(uniSocketObject).build());
            	                
            	//return always from here
            	return tccdmDatums;
            }
            
            NetFlowObject.Builder netBuilder = NetFlowObject.newBuilder();
            netBuilder.setUuid(getUuid(vertex));
            Map<CharSequence, CharSequence> properties = new HashMap<CharSequence, CharSequence>();
            if(vertex.getAnnotation("epoch") != null){
            	properties.put("epoch", vertex.getAnnotation("epoch"));
            }
            if(vertex.getAnnotation("version") != null){
            	properties.put("version", vertex.getAnnotation("version"));
            }
            if(properties.size() > 0){
            	baseObject.setProperties(properties);
            }
            netBuilder.setBaseObject(baseObject);
            String srcAddress = vertex.getAnnotation("source address");
            String srcPort = vertex.getAnnotation("source port");
            String destAddress = vertex.getAnnotation("destination address");
            String destPort = vertex.getAnnotation("destination port");
            
            srcAddress = srcAddress == null ? "" : srcAddress;
            destAddress = destAddress == null ? "" : destAddress;
            srcPort = srcPort == null ? "0" : srcPort;
            destPort = srcPort == null ? "0" : destPort;
            
            netBuilder.setSrcAddress(srcAddress);
            netBuilder.setSrcPort(CommonFunctions.parseInt(srcPort, 0));
            
            netBuilder.setDestAddress(destAddress);
            netBuilder.setDestPort(CommonFunctions.parseInt(destPort, 0));
            
            NetFlowObject netFlowObject = netBuilder.build();
            tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(netFlowObject).build());
            return tccdmDatums;
        } else if (entityType.equals(ArtifactIdentifier.SUBTYPE_MEMORY)) { //no epoch for memory
        	Map<CharSequence, CharSequence> properties = new HashMap<>();
        	if(vertex.getAnnotation("size") != null){
        		properties.put("size", vertex.getAnnotation("size"));
        	}
        	if(vertex.getAnnotation("version") != null){
        		properties.put("version", vertex.getAnnotation("version"));
        	}
        	if(vertex.getAnnotation("pid") != null){
        		properties.put("pid", vertex.getAnnotation("pid"));
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
        } else if (entityType.equals(ArtifactIdentifier.SUBTYPE_PIPE)) {                            
        	FileObject.Builder pipeBuilder = FileObject.newBuilder();
        	pipeBuilder.setUuid(getUuid(vertex));
        	pipeBuilder.setBaseObject(baseObject);
            pipeBuilder.setUrl("file://" + vertex.getAnnotation("path")); 
            pipeBuilder.setVersion(CommonFunctions.parseInt(vertex.getAnnotation("version"), null));
            pipeBuilder.setIsPipe(true);
            Map<CharSequence, CharSequence> properties = new HashMap<>();
            if(vertex.getAnnotation("epoch") != null){
            	properties.put("epoch", vertex.getAnnotation("epoch"));
            }
            if(vertex.getAnnotation("pid") != null){
            	properties.put("pid", vertex.getAnnotation("pid"));
            }
            if(properties.size() > 0){
            	baseObject.setProperties(properties);
            }
            FileObject pipeObject = pipeBuilder.build();
            tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(pipeObject).build());
            return tccdmDatums;
        } else if (entityType.equals(ArtifactIdentifier.SUBTYPE_UNKNOWN)) { //can only be file or pipe subtypes behind the scenes. include all. TODO.
        	SrcSinkObject.Builder unknownBuilder = SrcSinkObject.newBuilder();
        	Map<CharSequence, CharSequence> properties = new HashMap<>();
        	String path = vertex.getAnnotation("path");
        	boolean added = false;
        	if(path != null){
	        	String pathTokens[] = path.split("/");
	        	if(pathTokens.length >= 5){
		        	String pid = pathTokens[2];
		        	String fd = pathTokens[4];
		        	properties.put("pid", pid);
		        	properties.put("fd", fd);
		        	added = true;
	        	}
        	}
        	if(!added){
        		logger.log(Level.INFO, "Missing or malformed path annotation in unknown artifact type.");
        		return tccdmDatums;
        	}
        	if(vertex.getAnnotation("version") != null){
            	properties.put("version", vertex.getAnnotation("version"));
            }
            if(vertex.getAnnotation("epoch") != null){
            	properties.put("epoch", vertex.getAnnotation("epoch"));
            }
            if(properties.size() > 0){
            	baseObject.setProperties(properties);
            }
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
