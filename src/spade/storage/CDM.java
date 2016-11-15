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
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.security.auth.PrincipalBuilder;

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
	
	private Map<String, Long> stats = new HashMap<String, Long>();
	
	private static final Logger logger = Logger.getLogger(CDM.class.getName());
    
    private Map<String, ProcessInformation> pidMappings = new HashMap<>();
    
    // A set to keep track of principals that have been published to avoid duplication
    private Set<UUID> principalUUIDs = new HashSet<UUID>(); 
    
    //Key is <time + ":" + event id>
    private Map<String, Set<UUID>> timeEventIdToPendingLoadedFilesUUIDs = new HashMap<String, Set<UUID>>();
    
    private boolean hexUUIDs = false;
    
    private boolean agents = false;
    
    @Override
    public boolean initialize(String arguments) {
    	boolean initResult = super.initialize(arguments);
    	
    	try{
	    	Map<String, String> argumentsMap = CommonFunctions.parseKeyValPairs(arguments);
	    	if("true".equals(argumentsMap.get("hexUUIDs"))){
	    		hexUUIDs = true;
	    	}
	    	if("true".equals(argumentsMap.get("agents"))){
	    		agents = true;
	    	}
    	}catch(Exception e){
    		logger.log(Level.WARNING, "Failed to parse arguments into key-value pairs. Using default values.", e);
    	}
    	
    	publishStreamMarkerObject(true);
    	    	
    	return initResult;
    }
    
    /**
     * Creates a SrcSinkObject with special annotations to be used as start of stream and end of stream markers
     * AND publishes the object too
     * 
     * Annotations: 'start time' if start of stream, 'end time' if end of stream
     * 
     * @param isStart true if start of stream, false if end of stream
     * @return SrcSinkObject instance
     */
    private void publishStreamMarkerObject(boolean isStart){
    	String annotationName = isStart ? "start time" : "end time";
    	
    	Builder baseObjectBuilder = AbstractObject.newBuilder();
    	baseObjectBuilder.setSource(InstrumentationSource.SOURCE_LINUX_AUDIT_TRACE);
        AbstractObject baseObject = baseObjectBuilder.build();
    	
    	SrcSinkObject.Builder streamMarkerObjectBuilder = SrcSinkObject.newBuilder();
    	Map<CharSequence, CharSequence> properties = new HashMap<>();
    	if(!isStart){
    		for(Map.Entry<String, Long> entry : stats.entrySet()){
    			properties.put(entry.getKey(), String.valueOf(entry.getValue()));
    		}
    	}
    	properties.put(annotationName, String.valueOf(System.currentTimeMillis()*1000)); //millis to micros
    	baseObject.setProperties(properties);
    	streamMarkerObjectBuilder.setBaseObject(baseObject);
    	streamMarkerObjectBuilder.setUuid(getUuid(properties)); //uuid is based on the annotations and values in the properties map
    	streamMarkerObjectBuilder.setType(SrcSinkType.SOURCE_SYSTEM_PROPERTY);
        SrcSinkObject streamMarkerObject = streamMarkerObjectBuilder.build();  
        
        List<GenericContainer> dataToPublish = new ArrayList<GenericContainer>();
    	dataToPublish.add(TCCDMDatum.newBuilder().setDatum(streamMarkerObject).build());
    	publishRecords(dataToPublish);
    }
    
    public void incrementStatsCount(String key){
    	if(stats.get(key) == null){
    		stats.put(key, 0L);
    	}
    	stats.put(key, stats.get(key) + 1);
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
            } else if(vertexType.equals("Agent")){
            	tccdmDatums = mapAgent(vertex);
            } else {
                logger.log(Level.WARNING, "Unexpected vertex type: {0}", vertexType);
                return false;
            }

            // Now we publish the records in Kafka.
            publishRecords(tccdmDatums);
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
    
    protected int publishRecords(List<GenericContainer> genericContainers){
    	for(GenericContainer genericContainer : genericContainers){
    		try{
	    		if(genericContainer instanceof TCCDMDatum){
	    			Object cdmObject = ((TCCDMDatum) genericContainer).getDatum();
	    			if(cdmObject != null){
	    				if(cdmObject.getClass().equals(Subject.class)){
	    					Integer unitId = ((Subject)cdmObject).getUnitId();
	    					if(unitId == null){
	    						incrementStatsCount("Subject");
	    					}else{
	    						if(unitId == 0){
	    							incrementStatsCount("Subject");
	    						}else{
	    							incrementStatsCount("Unit");
	    						}
	    					}
	    				}else if(cdmObject.getClass().equals(Principal.class)){
	    					incrementStatsCount("Principal");
	    				}else if(cdmObject.getClass().equals(SimpleEdge.class)){
	    					EdgeType edgeType = ((SimpleEdge)cdmObject).getType();
	    					if(edgeType != null){
	    						incrementStatsCount(edgeType.name());
	    					}
	    				}else if(cdmObject.getClass().equals(Event.class)){
	    					EventType eventType = ((Event)cdmObject).getType();
	    					if(eventType != null){
	    						incrementStatsCount(eventType.name());
	    					}
	    				}else if(cdmObject.getClass().equals(SrcSinkObject.class)){
	    					if(((SrcSinkObject)cdmObject).getType() == SrcSinkType.SOURCE_UNKNOWN){
	    						incrementStatsCount("SrcSinkObject");
	    					}
	    				}else if(cdmObject.getClass().equals(MemoryObject.class)){
	    					incrementStatsCount("MemoryObject");
	    				}else if(cdmObject.getClass().equals(NetFlowObject.class)){
	    					incrementStatsCount("NetFlowObject");
	    				}else if(cdmObject.getClass().equals(FileObject.class)){
	    					if(((FileObject)cdmObject).getIsPipe()){
	    						incrementStatsCount("PipeObject");
	    					}else{
		    					AbstractObject baseObject = ((FileObject)cdmObject).getBaseObject();
		    					if(baseObject != null){
		    						Map<CharSequence, CharSequence> properties = baseObject.getProperties();
		    						if(properties != null){
		    							if("true".equals(properties.get("isUnixSocket"))){
		    								incrementStatsCount("UnixSocketObject");
		    							}else{
		    								incrementStatsCount("FileObject");
		    							}
		    						}
		    					}
	    					}
	    				}
	    			}
	    		}
    		}catch (Exception e) {
				logger.log(Level.WARNING, "Failed to collect stats", e);
			}
    	}
    	return super.publishRecords(genericContainers);
    }
    
    /**
     * Creates a simple edge
     * 
     * @param fromUuid the uuid of the source vertex
     * @param toUuid the uuid of the destination vertex
     * @param edgeType the type of the edge
     * @param time the time of the edge
     * @param properties map of additional properties to add to the edge
     * @return a simple edge instance
     */
    private SimpleEdge createSimpleEdge(UUID fromUuid, UUID toUuid, EdgeType edgeType, Long time, Map<CharSequence, CharSequence> properties){
    	SimpleEdge.Builder simpleEdgeBuilder = SimpleEdge.newBuilder();
        simpleEdgeBuilder.setFromUuid(fromUuid);  // Event record's UID
        simpleEdgeBuilder.setToUuid(toUuid);  //source vertex is the child
        simpleEdgeBuilder.setType(edgeType);
        simpleEdgeBuilder.setTimestamp(time);
        
        if(properties != null && properties.size() > 0){
        	simpleEdgeBuilder.setProperties(properties);
        }        
        
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
            if(opmOperation == null && !opmEdgeType.equals("WasControlledBy")){
            	logger.log(Level.WARNING,
                        "NULL {0} operation! event id = {1}", new Object[]{opmEdgeType, eventId});
                return false;
            }
            
            if(opmEdgeType.equals("WasControlledBy")){
            	if(opmOperation == null){
	            	SimpleEdge simpleEdge = createSimpleEdge(getUuid(edge.getSourceVertex()), 
	            			getUuid(edge.getDestinationVertex()), 
	    	        		EdgeType.EDGE_SUBJECT_HASLOCALPRINCIPAL, 
	    	        		convertTimeToMicroseconds(eventId, edge.getAnnotation("time"), 0L), null);
	    	        tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(simpleEdge).build());
	    	        publishRecords(tccdmDatums);
	    	        return true;
            	}else if(opmOperation.equals("setuid")){
            		actingProcessPidString = edge.getSourceVertex().getAnnotation("pid");
                	affectsEdgeType = EdgeType.EDGE_EVENT_AFFECTS_SUBJECT;
            		eventType = EventType.EVENT_CHANGE_PRINCIPAL;
            	}else{
            		logger.log(Level.WARNING,
                            "Unexpected WasControlledBy operation: {0}. event id = {1}", new Object[]{opmOperation, eventId});
                    return false;
            	}
            }else if (opmEdgeType.equals("WasTriggeredBy")) {
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
            		
            		// Add any pending load edges
            		
            		Set<UUID> pendingLoadedFilesUUIDs = timeEventIdToPendingLoadedFilesUUIDs.get(time+":"+eventId);
            		
            		if(pendingLoadedFilesUUIDs != null){
            			Map<CharSequence, CharSequence> properties = new HashMap<CharSequence, CharSequence>();
            			properties.put("event id", edge.getAnnotation("event id"));
            			properties.put("time", edge.getAnnotation("time"));
            			properties.put("source", edge.getAnnotation("time"));
            			for(UUID pendingLoadedFileUUID : pendingLoadedFilesUUIDs){
            				SimpleEdge loadEdge = createSimpleEdge(pendingLoadedFileUUID, getUuid(edge),
                    				EdgeType.EDGE_FILE_AFFECTS_EVENT, time, properties);
    	                    tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(loadEdge).build());
            			}
            			timeEventIdToPendingLoadedFilesUUIDs.remove(time+":"+eventId); //remove since all have been added
            		}
            		
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
                		Map<CharSequence, CharSequence> properties = new HashMap<CharSequence, CharSequence>();
            			properties.put("event id", edge.getAnnotation("event id"));
            			properties.put("time", edge.getAnnotation("time"));
            			properties.put("source", edge.getAnnotation("time"));
                		SimpleEdge loadEdge = createSimpleEdge(getUuid(edge.getDestinationVertex()), getExecEventUUID(actingProcessPidString),
                				EdgeType.EDGE_FILE_AFFECTS_EVENT, time, properties);
	                    tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(loadEdge).build());
	                    publishRecords(tccdmDatums);
	                    return true; //no need to create an event for this so returning from here after adding the edge
                	}else{
                		if(timeEventIdToPendingLoadedFilesUUIDs.get(time+":"+eventId) == null){
                			timeEventIdToPendingLoadedFilesUUIDs.put(time+":"+eventId, new HashSet<UUID>());
                		}
                		timeEventIdToPendingLoadedFilesUUIDs.get(time+":"+eventId).add(getUuid(edge.getDestinationVertex()));
                		return true;
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
                	Map<CharSequence, CharSequence> properties = new HashMap<CharSequence, CharSequence>();
        			properties.put("event id", edge.getAnnotation("event id"));
        			properties.put("time", edge.getAnnotation("time"));
        			properties.put("source", edge.getAnnotation("time"));
                	SimpleEdge updateEdge = createSimpleEdge(getUuid(edge.getSourceVertex()), getUuid(edge.getDestinationVertex()), 
                			EdgeType.EDGE_OBJECT_PREV_VERSION, time, properties);
                	
                    tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(updateEdge).build());
                    publishRecords(tccdmDatums);
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
            if(opmEdgeType.equals("Used")){ // event affected
            	affectsEdge = createSimpleEdge(getUuid(edge.getDestinationVertex()), getUuid(edge), 
            			affectsEdgeType, time, null);
            	uuidOfProcessVertex = getUuid(edge.getSourceVertex()); //getting the source because that is the process
            }else if(opmEdgeType.equals("WasControlledBy")){ 
            	affectsEdge = createSimpleEdge(getUuid(edge), getUuid(edge.getDestinationVertex()), 
            			affectsEdgeType, time, null);
            	uuidOfProcessVertex = getUuid(edge.getSourceVertex()); //getting the source because that is the process
            }else{// event affects
            	affectsEdge = createSimpleEdge(getUuid(edge), getUuid(edge.getSourceVertex()), 
            			affectsEdgeType, time, null);
            	uuidOfProcessVertex = getUuid(edge.getDestinationVertex()); //getting the destination because that is the process
            }
            tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(affectsEdge).build());
            
            if (opmEdgeType.equals("WasDerivedFrom")) {
                /* Generate another _*_AFFECTS_* edge in the reverse direction */
            	SimpleEdge affectsEventEdge = createSimpleEdge(getUuid(edge.getDestinationVertex()), getUuid(edge), 
            			getArtifactAffectsEventEdgeType(edge.getDestinationVertex()), time, null);
                tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(affectsEventEdge).build());
                
                uuidOfProcessVertex = getProcessSubjectUUID(String.valueOf(actingProcessPid));
            }
            
            if(uuidOfProcessVertex != null){
	            /* Generate the EVENT_ISGENERATEDBY_SUBJECT edge record */
            	SimpleEdge eventToProcessEdge = createSimpleEdge(getUuid(edge), uuidOfProcessVertex, 
            			EdgeType.EDGE_EVENT_ISGENERATEDBY_SUBJECT, time, null);
	            tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(eventToProcessEdge).build());
            }else{
            	logger.log(Level.WARNING, "Failed to find process uuid in process cache map for pid {0}. event id = {1}", new Object[]{edge.getAnnotation("pid"), eventId});
            }

            // Now we publish the records in Kafka.
            publishRecords(tccdmDatums);
            return true;
        } catch (Exception exception) {
            logger.log(Level.SEVERE, null, exception);
            return false;
        }
    }

    @Override
    public boolean shutdown() {
        try {
            
            for(Map.Entry<String, Set<UUID>> entry : timeEventIdToPendingLoadedFilesUUIDs.entrySet()){
            	String timeEventId = entry.getKey();
            	if(entry.getValue() != null && entry.getValue().size() > 0){
            		logger.log(Level.WARNING, "Missing execve event with id with stamp '"+timeEventId+"'. Failed to add " + entry.getValue().size() + " load edges");
            	}
            }            
            
            publishStreamMarkerObject(false);
            
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

    private void addIfNotNull(String key, Map<String, String> from, Map<CharSequence, CharSequence> to){
    	if(from.get(key) != null){
    		to.put(key, from.get(key));
    	}
    }
    
    private List<GenericContainer> mapAgent(AbstractVertex vertex){
    	List<GenericContainer> tccdmDatums = new LinkedList<GenericContainer>();
    	
    	Principal principal = createPrincipal(vertex);
    	if(principal != null){
    		tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(principal).build());
    	}
    	
    	return tccdmDatums;
    }
    
    private List<GenericContainer> mapProcess(AbstractVertex vertex) {
        List<GenericContainer> tccdmDatums = new LinkedList<GenericContainer>();

        /* Generate the Subject record */
        Subject.Builder subjectBuilder = Subject.newBuilder();
        
        InstrumentationSource activitySource = getInstrumentationSource(vertex.getAnnotation("source"));
        if (activitySource != null) {
        	subjectBuilder.setSource(activitySource); 
        } else {
            logger.log(Level.WARNING,
                    "Unexpected Activity source: {0}", vertex.getAnnotation("source"));
            return tccdmDatums;
        }
        
        putProcessSubjectUUID(vertex.getAnnotation("pid"), getUuid(vertex));
        
        subjectBuilder.setUuid(getUuid(vertex));
        subjectBuilder.setType(SubjectType.SUBJECT_PROCESS);
        subjectBuilder.setStartTimestampMicros(convertTimeToMicroseconds(null, vertex.getAnnotation("start time"), null));
        subjectBuilder.setPid(CommonFunctions.parseInt(vertex.getAnnotation("pid"), 0)); //Default not null because int primitive argument
        subjectBuilder.setPpid(CommonFunctions.parseInt(vertex.getAnnotation("ppid"), 0)); //Default not null because int primitive argument
        subjectBuilder.setUnitId(CommonFunctions.parseInt(vertex.getAnnotation("unit"), null)); //Can be null
        subjectBuilder.setCmdLine(vertex.getAnnotation("commandline")); // optional, so null is ok

        Map<CharSequence, CharSequence> properties = new HashMap<>();
        addIfNotNull("iteration", vertex.getAnnotations(), properties);
        addIfNotNull("count", vertex.getAnnotations(), properties);
        addIfNotNull("name", vertex.getAnnotations(), properties);
        addIfNotNull("uid", vertex.getAnnotations(), properties);
        addIfNotNull("gid", vertex.getAnnotations(), properties);
        addIfNotNull("cwd", vertex.getAnnotations(), properties);
        subjectBuilder.setProperties(properties);

        Subject subject = subjectBuilder.build();
        tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(subject).build()); //added subject
        
        if(!agents){
        
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
		        		convertTimeToMicroseconds(null, vertex.getAnnotation("start time"), 0L), null);
		        tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(simpleEdge).build());
	        }
        
        }
        return tccdmDatums;
    }
    
    private AbstractVertex createPrincipalVertex(AbstractVertex processVertex){
    	AbstractVertex vertex = new Vertex();
    	vertex.addAnnotation("uid", processVertex.getAnnotation("uid"));
    	vertex.addAnnotation("euid", processVertex.getAnnotation("euid"));
    	vertex.addAnnotation("gid", processVertex.getAnnotation("gid"));
    	vertex.addAnnotation("egid", processVertex.getAnnotation("egid"));
    	if(processVertex.getAnnotation("suid") != null){
    		vertex.addAnnotation("suid", processVertex.getAnnotation("suid"));
    	}
    	if(processVertex.getAnnotation("fsuid") != null){
    		vertex.addAnnotation("fsuid", processVertex.getAnnotation("fsuid"));
    	}
    	if(processVertex.getAnnotation("sgid") != null){
    		vertex.addAnnotation("sgid", processVertex.getAnnotation("sgid"));
    	}
    	if(processVertex.getAnnotation("fsgid") != null){
    		vertex.addAnnotation("fsgid", processVertex.getAnnotation("fsgid"));
    	}
    	vertex.addAnnotation("source", processVertex.getAnnotation("source"));
    	return vertex;
    }
    
    private Principal createPrincipal(AbstractVertex principalVertex){
        try{
        	InstrumentationSource source = getInstrumentationSource(principalVertex.getAnnotation("source"));
            if(source == null){
            	logger.log(Level.WARNING, "Missing source annotation for principal: " + principalVertex);
            	return null;
            }
            
            String userId = principalVertex.getAnnotation("uid");
            if(userId == null){
            	logger.log(Level.WARNING, "Missing user id for principal: " + principalVertex);
            	return null;
            }
            
        	Principal.Builder principalBuilder = Principal.newBuilder();
        	principalBuilder.setUuid(getUuid(principalVertex));
        	principalBuilder.setType(PrincipalType.PRINCIPAL_LOCAL);
        	principalBuilder.setSource(source);
        	principalBuilder.setUserId(userId);
                        
            Map<CharSequence, CharSequence> properties = new HashMap<CharSequence, CharSequence>();
            addIfNotNull("euid", principalVertex.getAnnotations(), properties);
            addIfNotNull("egid", principalVertex.getAnnotations(), properties);
            addIfNotNull("suid", principalVertex.getAnnotations(), properties);
            addIfNotNull("fsuid", principalVertex.getAnnotations(), properties);
            principalBuilder.setProperties(properties);
            
            List<CharSequence> groupIds = new ArrayList<CharSequence>();
            if(principalVertex.getAnnotation("gid") != null){
            	groupIds.add(principalVertex.getAnnotation("gid"));
            }
            if(principalVertex.getAnnotation("egid") != null){
            	groupIds.add(principalVertex.getAnnotation("egid"));
            }
            if(principalVertex.getAnnotation("sgid") != null){
            	groupIds.add(principalVertex.getAnnotation("sgid"));
            }
            if(principalVertex.getAnnotation("fsgid") != null){
            	groupIds.add(principalVertex.getAnnotation("fsgid"));
            }
            principalBuilder.setGroupIds(groupIds);
            
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
        	logger.log(Level.WARNING, "Unexpected Artifact source: {0}", activitySource);
        	return tccdmDatums;
        }else{
        	baseObjectBuilder.setSource(activitySource);
        }
        AbstractObject baseObject = baseObjectBuilder.build();
        String artifactType = vertex.getAnnotation("subtype");
        if (artifactType.equals(ArtifactIdentifier.SUBTYPE_FILE)) {
            FileObject.Builder fileBuilder = FileObject.newBuilder();
            fileBuilder.setUuid(getUuid(vertex));
            fileBuilder.setBaseObject(baseObject);
            fileBuilder.setUrl("file://" + vertex.getAnnotation("path"));
            fileBuilder.setVersion(CommonFunctions.parseInt(vertex.getAnnotation("version"), 0)); // Zero default value
            fileBuilder.setIsPipe(false);
            
            Map<CharSequence, CharSequence> properties = new HashMap<>();
            addIfNotNull("epoch", vertex.getAnnotations(), properties);
            if(properties.size() > 0){
            	baseObject.setProperties(properties);
            }
            
            FileObject fileObject = fileBuilder.build();
            tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(fileObject).build());
            return tccdmDatums;
        } else if (artifactType.equals(ArtifactIdentifier.SUBTYPE_SOCKET)) { //not handling unix sockets yet. TODO
            if(vertex.getAnnotation("path") != null){

            	//TODO should do?
            	FileObject.Builder unixSocketBuilder = FileObject.newBuilder();
                unixSocketBuilder.setUuid(getUuid(vertex));
                unixSocketBuilder.setBaseObject(baseObject);
                unixSocketBuilder.setUrl("file://" + vertex.getAnnotation("path"));
                unixSocketBuilder.setVersion(CommonFunctions.parseInt(vertex.getAnnotation("version"), 0));
                unixSocketBuilder.setIsPipe(false);
                Map<CharSequence, CharSequence> properties = new HashMap<>();
                addIfNotNull("epoch", vertex.getAnnotations(), properties);
                properties.put("isUnixSocket", "true");
                if(properties.size() > 0){
                	baseObject.setProperties(properties);
                }                
                FileObject uniSocketObject = unixSocketBuilder.build();
                tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(uniSocketObject).build());
            	//return always from here
            	return tccdmDatums;
            }
            
            NetFlowObject.Builder netBuilder = NetFlowObject.newBuilder();
            netBuilder.setUuid(getUuid(vertex));
            Map<CharSequence, CharSequence> properties = new HashMap<CharSequence, CharSequence>();
            addIfNotNull("epoch", vertex.getAnnotations(), properties);
            addIfNotNull("version", vertex.getAnnotations(), properties);
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
        } else if (artifactType.equals(ArtifactIdentifier.SUBTYPE_MEMORY)) { //no epoch for memory
        	
        	long memoryAddres = 0L;
            try{
            	memoryAddres = Long.parseLong(vertex.getAnnotation("memory address"), 16);
            }catch(Exception e){
            	logger.log(Level.WARNING, "Failed to parse memory address: " + vertex.getAnnotation("memory address"), e);
            	return tccdmDatums;
            }
                    	
        	Map<CharSequence, CharSequence> properties = new HashMap<>();
        	addIfNotNull("size", vertex.getAnnotations(), properties);
        	addIfNotNull("version", vertex.getAnnotations(), properties);
        	addIfNotNull("pid", vertex.getAnnotations(), properties);
        	if(properties.size() > 0){
        		baseObject.setProperties(properties);
        	}
            MemoryObject.Builder memoryBuilder = MemoryObject.newBuilder();
            memoryBuilder.setUuid(getUuid(vertex));
            memoryBuilder.setBaseObject(baseObject);
            memoryBuilder.setMemoryAddress(memoryAddres);   
            MemoryObject memoryObject = memoryBuilder.build();
            tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(memoryObject).build());
            return tccdmDatums;
        } else if (artifactType.equals(ArtifactIdentifier.SUBTYPE_PIPE)) {                            
        	FileObject.Builder pipeBuilder = FileObject.newBuilder();
        	pipeBuilder.setUuid(getUuid(vertex));
        	pipeBuilder.setBaseObject(baseObject);
            pipeBuilder.setUrl("file://" + vertex.getAnnotation("path")); 
            pipeBuilder.setVersion(CommonFunctions.parseInt(vertex.getAnnotation("version"), 0));
            pipeBuilder.setIsPipe(true);
            Map<CharSequence, CharSequence> properties = new HashMap<>();
            addIfNotNull("epoch", vertex.getAnnotations(), properties);
            addIfNotNull("pid", vertex.getAnnotations(), properties);
            if(properties.size() > 0){
            	baseObject.setProperties(properties);
            }
            FileObject pipeObject = pipeBuilder.build();
            tccdmDatums.add(TCCDMDatum.newBuilder().setDatum(pipeObject).build());
            return tccdmDatums;
        } else if (artifactType.equals(ArtifactIdentifier.SUBTYPE_UNKNOWN)) { //can only be file or pipe subtypes behind the scenes. include all. TODO.
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
        		logger.log(Level.WARNING, "Missing or malformed path annotation in unknown artifact type.");
        		return tccdmDatums;
        	}
        	addIfNotNull("version", vertex.getAnnotations(), properties);
        	addIfNotNull("epoch", vertex.getAnnotations(), properties);
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
                    "Unexpected Artifact type: {0}", artifactType);
            return tccdmDatums;
        }
    }
    
    private UUID getUuid(AbstractVertex vertex){
    	byte[] vertexHash = vertex.bigHashCode();
    	if(hexUUIDs){
    		vertexHash = String.valueOf(Hex.encodeHex(vertexHash, true)).getBytes();
    	}
        return new UUID(vertexHash);
    }
    
    private UUID getUuid(AbstractEdge edge){
    	byte[] edgeHash = edge.bigHashCode();
    	if(hexUUIDs){
    		edgeHash = String.valueOf(Hex.encodeHex(edgeHash, true)).getBytes();
    	}
        return new UUID(edgeHash);
    }
    
    private UUID getUuid(Map<CharSequence, CharSequence> map){
    	byte[] mapHash = DigestUtils.md5(map.toString());
    	if(hexUUIDs){
    		mapHash = String.valueOf(Hex.encodeHex(mapHash, true)).getBytes();
    	}
    	return new UUID(mapHash);
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
