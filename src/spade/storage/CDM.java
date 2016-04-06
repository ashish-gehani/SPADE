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
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.avro.generic.GenericContainer;

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
import com.bbn.tc.schema.avro.Subject;
import com.bbn.tc.schema.avro.SubjectType;
import com.bbn.tc.schema.avro.TCCDMDatum;
import com.bbn.tc.schema.avro.UUID;
import com.bbn.tc.schema.utils.SchemaUtils;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Settings;
import spade.core.Vertex;
import spade.utility.CommonFunctions;
import spade.utility.FileUtility;

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
public class CDM extends Kafka {

    // for volume stats
    private long startTime, endTime;
    private long recordCount;

    private String kafkaTopic = null;
    
    private Map<String, UUID> pidToUuid = new HashMap<>();
    
    private static final Logger logger = Logger.getLogger(CDM.class.getName());
    
    private String defaultConfigFilePath = Settings.getDefaultConfigFilePath(CDM.class);

    @Override
    public boolean initialize(String arguments) {
    	
    	if(arguments == null || arguments.trim().isEmpty()){
    		try{
    			Map<String, String> defaultConfig = FileUtility.readConfigFileAsKeyValueMap(defaultConfigFilePath, "=");
    			kafkaTopic = defaultConfig.get("KafkaTopic");
    			arguments = "KafkaServer=" + defaultConfig.get("KafkaServer") + 
    					" KafkaTopic=" + kafkaTopic +
    					" KafkaProducerID=" + defaultConfig.get("KafkaProducerID") +
    					" SchemaFilename=" + defaultConfig.get("SchemaFilename");
    		}catch(Exception e){
    			logger.log(Level.SEVERE, "Failed to read default config file '"+defaultConfigFilePath+"'", e);
    			return false;
    		}
    	}
    	
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
            recordCount += publishRecords(kafkaTopic, tccdmDatums);
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
            EdgeType affectsEdgeType;

            /* Generate the Event record */
            Event.Builder eventBuilder = Event.newBuilder();
            eventBuilder.setUuid(getUuid(edge));
            String time = edge.getAnnotation("time");
            Long timeLong = parseTimeToLong(time);
            eventBuilder.setTimestampMicros(timeLong);
            Long eventId = CommonFunctions.parseLong(edge.getAnnotation("event id"), null);
            if(eventId == null){ 
            	eventBuilder.setSequence(0); // Value to be confirmed here TODO
            }else{
            	eventBuilder.setSequence(eventId);
            }
            
            InstrumentationSource edgeSource = getInstrumentationSource(edge.getAnnotation("source"));
            if(edgeSource == null){
            	logger.log(Level.WARNING,
                        "Unexpected Edge source: {0}", edgeSource);
            }else{
            	eventBuilder.setSource(edgeSource);
            }
            
            String pid = null;
            
            Map<String, String> properties = new HashMap<>();
            properties.put("eventId", edge.getAnnotation("event id"));
            String edgeType = edge.type();
            String operation = edge.getAnnotation("operation");
            if (edgeType.equals("WasTriggeredBy")) {
            	pid = edge.getDestinationVertex().getAnnotation("pid");
                if (operation == null) {
                    logger.log(Level.WARNING,
                            "NULL WasTriggeredBy/WasInformedBy operation!");
                    return false;
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
                            "Unexpected WasTriggeredBy/WasInformedBy operation: {0}", operation);
                    return false;
                }
            } else if (edgeType.equals("WasGeneratedBy")) {
            	pid = edge.getDestinationVertex().getAnnotation("pid");
                if (operation == null) {
                    logger.log(Level.WARNING,
                            "NULL WasGeneratedBy operation!");
                    return false;
                } else if (operation.equals("write")) {
                    eventBuilder.setType(EventType.EVENT_WRITE);
                    String size = edge.getAnnotation("size");
                    if (size != null) {
                        eventBuilder.setSize(CommonFunctions.parseLong(size, 0L));
                    }
                    affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_FILE;
                } else if (operation.equals("send") || operation.equals("sendto")) {
                    // XXX CDM currently doesn't support send/sendto even type, so mapping to write.
                    eventBuilder.setType(EventType.EVENT_WRITE);
                    String size = edge.getAnnotation("size");
                    if (size != null) {
                    	eventBuilder.setSize(CommonFunctions.parseLong(size, 0L));
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
                    properties.put("permissions", edge.getAnnotation("mode"));
                    affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_FILE;
                } else if (operation.equals("rename_write")) {
                	//handled automatically in case of WasDerivedFrom 'rename' operation
                    return false;
                } else if (operation.equals("link_write")) {
                	//handled automatically in case of WasDerivedFrom 'link' operation
                    return false;
                } else {
                    logger.log(Level.WARNING,
                            "Unexpected WasGeneratedBy operation: {0}", operation);
                    return false;
                }
            } else if (edgeType.equals("Used")) {
            	pid = edge.getSourceVertex().getAnnotation("pid");
                if (operation == null) {
                    logger.log(Level.WARNING,
                            "NULL Used operation!");
                    return false;
                } else if (operation.equals("read")) {
                    eventBuilder.setType(EventType.EVENT_READ);
                    String size = edge.getAnnotation("size");
                    if (size != null) {
                    	eventBuilder.setSize(CommonFunctions.parseLong(size, 0L));
                    }
                    affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_FILE; // XXX should be EDGE_FILE_AFFECTS_EVENT but not in CDM
                } else if (operation.equals("recv") || operation.equals("recvfrom")) { // XXX CDM doesn't support this
                    eventBuilder.setType(EventType.EVENT_READ);
                    String size = edge.getAnnotation("size");
                    if (size != null) {
                    	eventBuilder.setSize(CommonFunctions.parseLong(size, 0L));
                    }
                    affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_NETFLOW; // XXX should be EDGE_NETFLOW_AFFECTS_EVENT but not in CDM
                } else if (operation.equals("accept")) {
                    eventBuilder.setType(EventType.EVENT_ACCEPT);
                    affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_NETFLOW; // XXX should be EDGE_NETFLOW_AFFECTS_EVENT but not in CDM
                } else if (operation.equals("rename_read")) {
                	//handled automatically in case of WasDerivedFrom 'rename' operation
                    return false;
                } else if (operation.equals("link_read")) {
                	//handled automatically in case of WasDerivedFrom 'rename' operation
                    return false;
                } else {
                    logger.log(Level.WARNING,
                            "Unexpected Used operation: {0}", operation);
                    return false;
                }
            } else if (edgeType.equals("WasDerivedFrom")) {
            	pid = edge.getAnnotation("pid");
                if (operation == null) {
                    logger.log(Level.WARNING,
                            "NULL WasDerivedFrom operation!");
                    return false;
                } else if (operation.equals("update")) {   
                	eventBuilder.setType(EventType.EVENT_UPDATE);
                    affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_FILE;
                } else if (operation.equals("rename")) {
                    eventBuilder.setType(EventType.EVENT_RENAME);
                    affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_FILE;
                } else if (operation.equals("link")) {
                    eventBuilder.setType(EventType.EVENT_LINK);
                    affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_FILE;
                } else {
                    logger.log(Level.WARNING,
                            "Unexpected WasDerivedFrom operation: {0}", operation);
                    return false;
                }
            } else {
                logger.log(Level.WARNING, "Unexpected edge type: {0}", edgeType);
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
            affectsEdgeBuilder.setToUuid(getUuid(edge.getSourceVertex())); // UID of Subject/Object being affected
            affectsEdgeBuilder.setType(affectsEdgeType);
            affectsEdgeBuilder.setTimestamp(timeLong);
            SimpleEdge affectsEdge = affectsEdgeBuilder.build();
            tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(affectsEdge).build());

            UUID uuidOfDestinationProcessVertex = getUuid(edge.getDestinationVertex());
            
            if (edgeType.equals("WasDerivedFrom")) {
                /* Generate another _*_AFFECTS_* edge in the reverse direction */
                affectsEdgeBuilder.setFromUuid(getUuid(edge.getDestinationVertex())); // UID of Object being affecting
                affectsEdgeBuilder.setToUuid(getUuid(edge)); // Event record's UID
                affectsEdgeBuilder.setType(EdgeType.EDGE_EVENT_AFFECTS_FILE); // XXX should be EDGE_FILE_AFFECTS_EVENT but not in CDM
                affectsEdgeBuilder.setTimestamp(timeLong);
                affectsEdge = affectsEdgeBuilder.build();
                tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(affectsEdge).build());
                
                uuidOfDestinationProcessVertex = pidToUuid.get(edge.getAnnotation("pid"));
            }
            
            if(uuidOfDestinationProcessVertex != null){
	            /* Generate the EVENT_ISGENERATEDBY_SUBJECT edge record */
	            SimpleEdge.Builder generatedByEdgeBuilder = SimpleEdge.newBuilder();
	            generatedByEdgeBuilder.setFromUuid(getUuid(edge)); // Event record's UID
	            generatedByEdgeBuilder.setToUuid(uuidOfDestinationProcessVertex); //UID of Subject generating event
	            generatedByEdgeBuilder.setType(EdgeType.EDGE_EVENT_ISGENERATEDBY_SUBJECT);
	            generatedByEdgeBuilder.setTimestamp(timeLong);
	            SimpleEdge generatedByEdge = generatedByEdgeBuilder.build();
	            tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(generatedByEdge).build());
            }else{
            	logger.log(Level.WARNING, "Failed to find process hash in process cache map for pid {0}", edge.getAnnotation("pid"));
            }

            // Now we publish the records in Kafka.
            recordCount += publishRecords(kafkaTopic, tccdmDatums);
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
    
    private long parseTimeToLong(String time){
    	try{
    		Float f = Float.parseFloat(time);
    		f = f * 1000;
    		return f.longValue();
    	}catch(Exception e){
    		logger.log(Level.WARNING,
                    "Time type is not FLOAT: {0}", time);
    		return 0;
    	}
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
        
        pidToUuid.put(vertex.getAnnotation("pid"), getUuid(vertex));
        
        Long time = parseTimeToLong(vertex.getAnnotation("start time"));
        subjectBuilder.setStartTimestampMicros(time); 
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
        	Long startTime = parseTimeToLong(vertex.getAnnotation("start time"));
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
            principalBuilder.setUserId(Integer.parseInt(principalVertex.getAnnotation("uid")));
            Map<String, String> properties = new HashMap<String, String>();
            properties.put("egid", principalVertex.getAnnotation("egid"));
            properties.put("euid", principalVertex.getAnnotation("euid"));
            List<Integer> groupIds = new ArrayList<Integer>();
            groupIds.add(Integer.parseInt(principalVertex.getAnnotation("gid")));
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
        } else if (entityType.equals("network")) {
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
            MemoryObject.Builder memoryBuilder = MemoryObject.newBuilder();
            memoryBuilder.setUuid(getUuid(vertex));
            memoryBuilder.setBaseObject(baseObject);
            // memoryBuilder.setPageNumber(0);                          // TODO remove when marked optional
            memoryBuilder.setMemoryAddress(Long.parseLong(vertex.getAnnotation("memory address")));
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
        } else if (entityType.equals("unknown")) {
        	SrcSinkObject.Builder unknownBuilder = SrcSinkObject.newBuilder();
        	Map<String, String> properties = new HashMap<String, String>();
        	String pathTokens[] = vertex.getAnnotation("path").split("/");
        	String pid = pathTokens[1];
        	String fd = pathTokens[3];
        	properties.put("pid", pid);
        	properties.put("fd", fd);
        	properties.put("version", vertex.getAnnotation("version"));
        	baseObject.setProperties(properties);
        	unknownBuilder.setBaseObject(baseObject);
            unknownBuilder.setUuid(getUuid(vertex));
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
        return SchemaUtils.toUUID(vertex.hashCode());
    }
    
    private UUID getUuid(AbstractEdge edge){
        return SchemaUtils.toUUID(edge.hashCode());
    }
}