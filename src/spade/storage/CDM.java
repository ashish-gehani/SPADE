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

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Vertex;

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

    // default parameter values
    private String kafkaServer = "localhost:9092";
    private String kafkaTopic = "trace-topic";
    private String kafkaProducerID = "trace-producer";
    private String schemaFilename = SPADE_ROOT + "cfg/TCCDMDatum.avsc";
    
    private Map<String, Integer> pidToHashCode = new HashMap<String, Integer>();
    
    private static final Logger logger = Logger.getLogger(CDM.class.getName());

    @Override
    public boolean initialize(String arguments) {
    	
    	if(arguments == null || arguments.trim().isEmpty()){
    		arguments = "kafkaServer=" + kafkaServer + 
    					" KafkaTopic=" + kafkaTopic +
    					" KafkaProducerID=" + kafkaProducerID +
    					" SchemaFilename=" + schemaFilename;
    	}
    	
    	if(super.initialize(arguments)){
    		try {
                /* Note: This is not an accurate start time because we really want the first reported event,
                 * but fine for now
                 */
                startTime = System.currentTimeMillis();
                endTime = 0;
                recordCount = 0;

                return true;
            } catch (Exception exception) {
                logger.log(Level.SEVERE, null, exception);
                return false;
            }
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
            EdgeType affectsEdgeType;

            /* Generate the Event record */
            Event.Builder eventBuilder = Event.newBuilder();
            eventBuilder.setUuid(edge.hashCode());
            String time = edge.getAnnotation("time");
            Long timeLong = parseTimeToLong(time);
            eventBuilder.setTimestampMicros(timeLong);
            Long eventId = parseLong(edge.getAnnotation("event id"));
            if(eventId == null){ 
            	eventBuilder.setSequence(0); // XXX not provided, but CDM requires it
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
            
            Map<String, String> properties = new HashMap<>();
            properties.put("eventId", edge.getAnnotation("event id"));
            String edgeType = edge.type();
            String operation = edge.getAnnotation("operation");
            if (edgeType.equals("WasTriggeredBy")) {
                if (operation == null) {
                    logger.log(Level.WARNING,
                            "NULL WasTriggeredBy/WasInformedBy operation!");
                    return false;
                } else if (operation.equals("fork")) {
                    eventBuilder.setType(EventType.EVENT_FORK);
                    affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_SUBJECT;
                } else if (operation.equals("clone")) {                             // XXX CDM doesn't support this
                	eventBuilder.setType(EventType.EVENT_CLONE); 
                	affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_SUBJECT;
//                    logger.log(Level.WARNING,
//                            "TC CDM does not support WasTriggeredBy/WasInformed operation: {0}", operation);
//                    return false;
                } else if (operation.equals("execve")) {
                    eventBuilder.setType(EventType.EVENT_EXECUTE);
                    affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_SUBJECT;
                } else if (operation.equals("setuid")) {
                    eventBuilder.setType(EventType.EVENT_CHANGE_PRINCIPAL);
                    affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_SUBJECT;
                    /* XXX How do we capture the UID the Subject was set to?
                     * Perhaps a new HasLocalPrincipal edge? But that doesn't seem right.
                     */
                } else if (operation.equals("unit")) {   
                	eventBuilder.setType(EventType.EVENT_UNIT);
                	affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_SUBJECT;// XXX CDM doesn't support this
//                    logger.log(Level.WARNING,
//                            "TC CDM does not support WasTriggeredBy/WasInformed operation: {0}", operation);
//                    return false;
                } else {
                    logger.log(Level.WARNING,
                            "Unexpected WasTriggeredBy/WasInformedBy operation: {0}", operation);
                    return false;
                }
            } else if (edgeType.equals("WasGeneratedBy")) {
                if (operation == null) {
                    logger.log(Level.WARNING,
                            "NULL WasGeneratedBy operation!");
                    return false;
                } else if (operation.equals("write")) {
                    eventBuilder.setType(EventType.EVENT_WRITE);
                    String size = edge.getAnnotation("size");
                    if (size != null) {
                        eventBuilder.setSize(parseLong(size));
                    }
                    affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_FILE;
                } else if (operation.equals("send") || operation.equals("sendto")) {
                    // XXX CDM currently doesn't support send/sendto even type, so mapping to write.
                    eventBuilder.setType(EventType.EVENT_WRITE);
                    String size = edge.getAnnotation("size");
                    if (size != null) {
                    	eventBuilder.setSize(parseLong(size));
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
                } else if (operation.equals("rename_write")) {                      // XXX CDM doesn't support this
                    logger.log(Level.WARNING,
                            "TC CDM does not support WasGeneratedBy operation: {0}", operation);
                    return false;
                } else if (operation.equals("link_write")) {                        // XXX CDM doesn't support this
                    logger.log(Level.WARNING,
                            "TC CDM does not support WasGeneratedBy operation: {0}", operation);
                    return false;
                } else {
                    logger.log(Level.WARNING,
                            "Unexpected WasGeneratedBy operation: {0}", operation);
                    return false;
                }
            } else if (edgeType.equals("Used")) {
                if (operation == null) {
                    logger.log(Level.WARNING,
                            "NULL Used operation!");
                    return false;
                } else if (operation.equals("read")) {
                    eventBuilder.setType(EventType.EVENT_READ);
                    String size = edge.getAnnotation("size");
                    if (size != null) {
                    	eventBuilder.setSize(parseLong(size));
                    }
                    affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_FILE; // XXX should be EDGE_FILE_AFFECTS_EVENT but not in CDM
                } else if (operation.equals("recv") || operation.equals("recvfrom")) { // XXX CDM doesn't support this
                    eventBuilder.setType(EventType.EVENT_READ);
                    String size = edge.getAnnotation("size");
                    if (size != null) {
                    	eventBuilder.setSize(parseLong(size));
                    }
                    affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_NETFLOW; // XXX should be EDGE_NETFLOW_AFFECTS_EVENT but not in CDM
                } else if (operation.equals("accept")) {
                    eventBuilder.setType(EventType.EVENT_ACCEPT);
                    affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_NETFLOW; // XXX should be EDGE_NETFLOW_AFFECTS_EVENT but not in CDM
                } else if (operation.equals("rename_read")) {                       // XXX CDM doesn't support this
                    logger.log(Level.WARNING,
                            "TC CDM does not support Used operation: {0}", operation);
                    return false;
                } else if (operation.equals("link_read")) {                         // XXX CDM doesn't support this
                    logger.log(Level.WARNING,
                            "TC CDM does not support Used operation: {0}", operation);
                    return false;
                } else {
                    logger.log(Level.WARNING,
                            "Unexpected Used operation: {0}", operation);
                    return false;
                }
            } else if (edgeType.equals("WasDerivedFrom")) {
                // XXX No Subject provided for EVENT_ISGENERATEDBY_SUBJECT edge
                if (operation == null) {
                    logger.log(Level.WARNING,
                            "NULL WasDerivedBy operation!");
                    return false;
                } else if (operation.equals("update")) {   
                	eventBuilder.setType(EventType.EVENT_UPDATE);
                    affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_FILE;// XXX CDM doesn't support this
//                    logger.log(Level.WARNING,
//                            "TC CDM does not support WasDerivedFrom operation: {0}", operation);
//                    return false;
                } else if (operation.equals("rename")) {                            // XXX CDM doesn't support this
                    eventBuilder.setType(EventType.EVENT_RENAME);
                    affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_FILE;
//                	logger.log(Level.WARNING,
//                            "TC CDM does not support WasDerivedFrom operation: {0}", operation);
//                    return false;
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
            eventBuilder.setProperties(properties);
            Event event = eventBuilder.build();
            tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(event).build());

            /* Generate the _*_AFFECTS_* edge record */
            SimpleEdge.Builder affectsEdgeBuilder = SimpleEdge.newBuilder();
            affectsEdgeBuilder.setFromUuid(edge.hashCode());  // Event record's UID
            affectsEdgeBuilder.setToUuid(edge.getSourceVertex().hashCode()); // UID of Subject/Object being affected
            affectsEdgeBuilder.setType(affectsEdgeType);
            affectsEdgeBuilder.setTimestamp(timeLong);
            SimpleEdge affectsEdge = affectsEdgeBuilder.build();
            tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(affectsEdge).build());

            Integer hashOfDestinationProcessVertex = edge.getDestinationVertex().hashCode();
            
            if (edgeType.equals("WasDerivedFrom")) {
                /* Generate another _*_AFFECTS_* edge in the reverse direction */
                affectsEdgeBuilder.setFromUuid(edge.getDestinationVertex().hashCode()); // UID of Object being affecting
                affectsEdgeBuilder.setToUuid(edge.hashCode()); // Event record's UID
                affectsEdgeBuilder.setType(EdgeType.EDGE_EVENT_AFFECTS_FILE); // XXX should be EDGE_FILE_AFFECTS_EVENT but not in CDM
                affectsEdgeBuilder.setTimestamp(timeLong);//TODO this wasn't here before. added. right?
                affectsEdge = affectsEdgeBuilder.build();
                tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(affectsEdge).build());
                
                hashOfDestinationProcessVertex = pidToHashCode.get(edge.getAnnotation("pid"));
            }
            
            if(hashOfDestinationProcessVertex != null){
	            /* Generate the EVENT_ISGENERATEDBY_SUBJECT edge record */
	            SimpleEdge.Builder generatedByEdgeBuilder = SimpleEdge.newBuilder();
	            generatedByEdgeBuilder.setFromUuid(edge.hashCode()); // Event record's UID
	            generatedByEdgeBuilder.setToUuid(hashOfDestinationProcessVertex); //UID of Subject generating event
	            generatedByEdgeBuilder.setType(EdgeType.EDGE_EVENT_ISGENERATEDBY_SUBJECT);
	            generatedByEdgeBuilder.setTimestamp(timeLong);
	            SimpleEdge generatedByEdge = generatedByEdgeBuilder.build();
	            tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(generatedByEdge).build());
            }else{
            	logger.log(Level.WARNING, "Failed to find process hash in process cache map for pid {0}", edge.getAnnotation("pid"));
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
    
    private Long parseLong(String str){
    	try{
    		return Long.parseLong(str);
    	}catch(Exception e){
    		logger.log(Level.WARNING,
                    "Value passed isn't LONG: {0}", str);
    		return null;
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
        subjectBuilder.setUuid(vertex.hashCode());
        subjectBuilder.setType(SubjectType.SUBJECT_PROCESS);
        InstrumentationSource activitySource = getInstrumentationSource(vertex.getAnnotation("source"));
        if (activitySource != null) {
        	subjectBuilder.setSource(activitySource); 
        } else {
            logger.log(Level.WARNING,
                    "Unexpected Activity source: {0}", activitySource);
            return tccdmDatums;
        }
        
        pidToHashCode.put(vertex.getAnnotation("pid"), vertex.hashCode());
        
        Long time = parseTimeToLong(vertex.getAnnotation("start time"));
        subjectBuilder.setStartTimestampMicros(time); // XXX not provided, but CDM requires this field
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
        
        AbstractVertex principalVertex = createPrincipalVertex(vertex);
        Principal principal = createPrincipal(principalVertex);
        
        if(principal != null){
        	tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(principal).build());
        	
        	SimpleEdge.Builder simpleEdgeBuilder = SimpleEdge.newBuilder();
        	simpleEdgeBuilder.setFromUuid(vertex.hashCode());
        	simpleEdgeBuilder.setToUuid(principalVertex.hashCode());
        	simpleEdgeBuilder.setType(EdgeType.EDGE_SUBJECT_HASLOCALPRINCIPAL);
        	Long startTime = parseTimeToLong(vertex.getAnnotation("start time"));
        	simpleEdgeBuilder.setTimestamp(startTime);
        	SimpleEdge simpleEdge = simpleEdgeBuilder.build();
            tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(simpleEdge).build());
        }

        /* XXX Need to create a principal to put uid and group, then check if it's new, and if so publish to Kafka.
         * Also, need to create edge to connect Subject and Principal
         */

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
        	principalBuilder.setUuid(principalVertex.hashCode());
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
            fileBuilder.setUuid(vertex.hashCode());
            fileBuilder.setBaseObject(baseObject);
            fileBuilder.setUrl("file://" + vertex.getAnnotation("path"));
            fileBuilder.setVersion(Integer.parseInt(vertex.getAnnotation("version")));
            fileBuilder.setIsPipe(false);
            FileObject fileObject = fileBuilder.build();
            tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(fileObject).build());
            return tccdmDatums;
        } else if (entityType.equals("network")) {
            NetFlowObject.Builder netBuilder = NetFlowObject.newBuilder();
            netBuilder.setUuid(vertex.hashCode());
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
            memoryBuilder.setUuid(vertex.hashCode());
            memoryBuilder.setBaseObject(baseObject);
            memoryBuilder.setPageNumber(0);                          // XXX not provided, but CDM requires it
            memoryBuilder.setMemoryAddress(Long.parseLong(vertex.getAnnotation("memory address")));
            MemoryObject memoryObject = memoryBuilder.build();
            tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(memoryObject).build());
            return tccdmDatums;
        } else if (entityType.equals("pipe")) {                            
        	FileObject.Builder pipeBuilder = FileObject.newBuilder();
        	pipeBuilder.setUuid(vertex.hashCode());
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
            unknownBuilder.setUuid(vertex.hashCode());
            SrcSinkObject unknownObject = unknownBuilder.build();
            tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(unknownObject).build());
            return tccdmDatums;	
        } else {
            logger.log(Level.WARNING,
                    "Unexpected Artifact/Entity type: {0}", entityType);
            return tccdmDatums;
        }
    }

}
