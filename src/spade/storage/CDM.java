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

import java.io.File;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.avro.generic.GenericContainer;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.kafka.clients.producer.ProducerConfig;

import com.bbn.tc.schema.avro.AbstractObject;
import com.bbn.tc.schema.avro.Event;
import com.bbn.tc.schema.avro.EventType;
import com.bbn.tc.schema.avro.FileObject;
import com.bbn.tc.schema.avro.FileObjectType;
import com.bbn.tc.schema.avro.InstrumentationSource;
import com.bbn.tc.schema.avro.MemoryObject;
import com.bbn.tc.schema.avro.NetFlowObject;
import com.bbn.tc.schema.avro.Principal;
import com.bbn.tc.schema.avro.PrincipalType;
import com.bbn.tc.schema.avro.SrcSinkObject;
import com.bbn.tc.schema.avro.SrcSinkType;
import com.bbn.tc.schema.avro.Subject;
import com.bbn.tc.schema.avro.SubjectType;
import com.bbn.tc.schema.avro.TCCDMDatum;
import com.bbn.tc.schema.avro.UUID;
import com.bbn.tc.schema.avro.UnnamedPipeObject;
import com.bbn.tc.schema.serialization.AvroConfig;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Settings;
import spade.reporter.audit.ArtifactIdentifier;
import spade.utility.CommonFunctions;
import spade.vertex.prov.Agent;

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

	private final String ARTIFACT = "Artifact",
			BEEP = "beep",
			COMMANDLINE = "commandline",
			COUNT = "count",
			CWD = "cwd",
			DESTINATION_ADDRESS = "destination address",
			DESTINATION_PORT = "destination port",
			EGID = "egid",
			END_TIME = "end time",
			EPOCH = "epoch",
			EVENT_ID = "event id",
			EUID = "euid",
			EXECVE = "execve",
			FSGID = "fsgid",
			FSUID = "fsuid",
			GID = "gid",
			ITERATION = "iteration",
			MEMORY_ADDRESS = "memory address",
			NAME = "name",
			OPERATION = "operation",
			PATH = "path",
			PID = "pid",
			PPID = "ppid",
			PROC = "/proc",
			PROCESS = "Process",
			SETUID = "setuid",
			SGID = "sgid",
			SIZE = "size",
			SOURCE = "source",
			SOURCE_ADDRESS = "source address",
			SOURCE_DEV_AUDIT = "/dev/audit",
			SOURCE_PORT = "source port",
			START_TIME = "start time",
			SUBTYPE = "subtype",
			SUID = "suid",
			TIME = "time",
			TYPE = "type",
			UID = "uid",
			UNIT = "unit",
			UPDATE = "update",
			USED = "Used",
			VERSION = "version",
			WAS_DERIVED_FROM = "WasDerivedFrom",
			WAS_GENERATED_BY = "WasGeneratedBy",
			WAS_TRIGGERED_BY = "WasTriggeredBy";

	private final Logger logger = Logger.getLogger(CDM.class.getName());

	/**
	 * Tracking counts of different kinds of objects being published/written
	 */
	private Map<String, Long> stats = new HashMap<String, Long>();
	/**
	 * Array of integers for an Agent as in Audit OPM model
	 */
	private final String[] agentAnnotations = {UID, EUID, SUID, FSUID, GID, EGID, SGID, FSGID};
	/**
	 * Flag whose value is set from arguments to decide whether to output hashcode and hex or raw bytes
	 */
	private boolean hexUUIDs = false;
	/**
	 * A map used to keep track of:
	 * 1) To keep track of parent subject UUIDs, equivalent to ppid
	 */
	private final Map<String, UUID> pidSubjectUUID = 
			new HashMap<String, UUID>();
	/**
	 * To keep track of principals published so that we don't duplicate them
	 */
	private final Set<UUID> publishedPrincipals = new HashSet<UUID>();
	/**
	 * To keep track of the last time and event id. All edges with the same time and event id are
	 * collected first and the edges are processes as one event
	 */
	private SimpleEntry<String, String> lastTimeEventId = null;
	/**
	 * List of currently unprocessed edges i.e. all edges for an event haven't been received
	 */
	private List<AbstractEdge> currentEventEdges = new ArrayList<AbstractEdge>();
	/**
	 * A map from the Set of edges needed for each event to complete.
	 */
	private Map<Set<TypeOperation>, EventType> rulesToEventType = null;


	/**
	 * Reads the lines passed as list and parses them to build a map using which 
	 * edge type and edge operation combinations are used to map them to CDM EventType
	 * enum
	 *  
	 * Format of line: <edge type>:<edge operation> & <edge type>:<edge operation>, CDM EventType enum  
	 *  
	 * Returns null if any error in processing the lines
	 *  
	 * @param lines list of lines as in the config file
	 * @return null/Map from type operation rule to event type
	 */
	public Map<Set<TypeOperation>, EventType> getEventRules(List<String> lines){
		if(lines != null){
			Map<Set<TypeOperation>, EventType> rules = new HashMap<Set<TypeOperation>, EventType>();
			for(String line : lines){
				if(line == null){
					logger.log(Level.SEVERE, "Null line");
				}else{
					line = line.trim();
					if(!line.startsWith("#")){
						String [] ruleEventType = line.split(",");
						if(ruleEventType.length == 2){
							String rule = ruleEventType[0];
							String eventTypeString = ruleEventType[1].trim();
							EventType eventType = null;
							try{
								eventType = EventType.valueOf(eventTypeString);
							}catch(Exception e){
								logger.log(Level.SEVERE, line, e);
								return null;
							}
							String ruleTokens[] = rule.split("&");
							Set<TypeOperation> typeOperations = new HashSet<TypeOperation>();
							for(String ruleToken : ruleTokens){
								String[] typeOperation = ruleToken.split(":");
								if(typeOperation.length == 2){
									String type = typeOperation[0].trim();
									String operation = typeOperation[1].trim();
									typeOperations.add(new TypeOperation(type, operation));
								}else{
									logger.log(Level.SEVERE, "Malformed line "+line);
									return null;
								}
							}
							if(!typeOperations.isEmpty()){
								rules.put(typeOperations, eventType);
							}else{
								logger.log(Level.SEVERE, "Empty rule not allowed");
								return null;
							}
						}else{
							logger.log(Level.SEVERE, "Malformed line "+line);
							return null;
						}
					}
				}
			}
			return rules;
		}else{
			logger.log(Level.SEVERE, "NULL list of lines for rules");
		}
		return null;
	}

	private void publishEvent(EventType eventType, AbstractEdge edge, AbstractVertex actingProcess, 
			AbstractVertex actedUpon1, AbstractVertex actedUpon2){

		if(eventType == null || edge == null || actingProcess == null || actedUpon1 == null){
			logger.log(Level.WARNING, "Missing arguments: eventType={0}, "
					+ "edge={1}, actingProcess{2}, actedUpon1={3}", new Object[]{
							eventType, edge, actingProcess, actedUpon1
			});
		}else{
			InstrumentationSource source = getInstrumentationSource(edge.getAnnotation(SOURCE));

			UUID uuid = getUuid(edge);
			Long sequence = CommonFunctions.parseLong(edge.getAnnotation(EVENT_ID), 0L);
			Integer threadId = CommonFunctions.parseInt(actingProcess.getAnnotation(PID), null);
			UUID subjectUUID = getUuid(actingProcess);
			UUID predicateObjectUUID = getUuid(actedUpon1);
			String predicateObjectPath = actedUpon1 != null ? actedUpon1.getAnnotation(PATH) : null;
			UUID predicateObject2UUID = getUuid(actedUpon2);
			String predicateObject2Path = actedUpon2 != null ? actedUpon2.getAnnotation(PATH) : null;
			Long timestampNanos = convertTimeToNanoseconds(sequence, edge.getAnnotation(TIME), 0L);
			Long size = CommonFunctions.parseLong(edge.getAnnotation(SIZE), null);

			// validation of mandatory values
			if(uuid == null || threadId == null || subjectUUID == null 
					|| predicateObjectUUID == null || source == null){
				logger.log(Level.WARNING, "NULL arguments for event: "
						+ "EdgeUUID={0}, threadId={1}, subjectUUID={2}, predicateObjectUUID={3}, "
						+ "source={4}", 
						new Object[]{uuid, threadId, subjectUUID, predicateObjectUUID, source});
			}else{

				Map<CharSequence, CharSequence> properties = new HashMap<CharSequence, CharSequence>();
				for(String key : edge.getAnnotations().keySet()){
					if(!key.equals(EVENT_ID) && !key.equals(TIME) 
							&& !key.equals(SOURCE) && !key.equals(PID) && 
							!key.equals(OPERATION) && !key.equals(SIZE)){
						properties.put(key, edge.getAnnotation(key));
					}
				}

				Event event = new Event(uuid, 
						sequence, eventType, threadId, subjectUUID, predicateObjectUUID, predicateObjectPath, 
						predicateObject2UUID, predicateObject2Path, timestampNanos, null, null, 
						null, size, null, properties);

				publishRecords(Arrays.asList(buildTcCDMDatum(event, source)));

			}
		}
	}

	private boolean publishSubjectAndPrincipal(AbstractVertex process){

		if(isProcessVertex(process)){

			String pid = process.getAnnotation(PID);

			InstrumentationSource subjectSource = getInstrumentationSource(process.getAnnotation(SOURCE));

			if(subjectSource != null){
				
				// Agents cannot come from BEEP source. Added just in case.
				InstrumentationSource principalSource = 
						subjectSource.equals(InstrumentationSource.SOURCE_LINUX_BEEP_TRACE)
						? InstrumentationSource.SOURCE_LINUX_AUDIT_TRACE : subjectSource;

				List<GenericContainer> objectsToPublish = new ArrayList<GenericContainer>();

				Principal principal = getPrincipalFromProcess(process);
				if(principal != null){
					UUID principalUUID = principal.getUuid();

					if(!publishedPrincipals.contains(principalUUID)){
						objectsToPublish.add(buildTcCDMDatum(principal, principalSource));
						publishedPrincipals.add(principalUUID);
					}

					Subject subject = getSubjectFromProcess(process, principalUUID);
					if(subject != null){
						objectsToPublish.add(buildTcCDMDatum(subject, subjectSource));
						if(subject.getType().equals(SubjectType.SUBJECT_PROCESS)){ // not in case of unit
							// The map is used only for getting the parent subject UUID i.e. equivalent
							// of ppid in OPM and that can only be the containing process as in Audit
							pidSubjectUUID.put(pid, subject.getUuid());
						}
						return publishRecords(objectsToPublish) > 0;
					}else {
						return false;
					}
				}else{
					return false;
				}
			}else{
				logger.log(Level.WARNING, "Failed to publish '{0}' vertex because of missing/invalid source",
						new Object[]{process});
			}
		}
		return false;
	}

	private Subject getSubjectFromProcess(AbstractVertex process, UUID principalUUID){
		if(isProcessVertex(process) && principalUUID != null){

			// Based on complete process annotations along with agent annotations
			UUID subjectUUID = getUuid(process);

			SubjectType subjectType = SubjectType.SUBJECT_PROCESS; // default
			if(process.getAnnotation(ITERATION) != null){
				subjectType = SubjectType.SUBJECT_UNIT; // if a unit
			}

			String pidString = process.getAnnotation(PID);
			Integer pid = CommonFunctions.parseInt(pidString, null);
			if(pid == null){
				logger.log(Level.WARNING, "Invalid pid {0} for Process {1}", new Object[]{
						pidString, process
				});
			}else{

				// Mandatory but if missing then default value is 0
				Long startTime = convertTimeToNanoseconds(null, process.getAnnotation(START_TIME), 0L);

				String unitIdAnnotation = process.getAnnotation(UNIT);
				Integer unitId = CommonFunctions.parseInt(unitIdAnnotation, null);

				// meaning that the unit annotation was non-numeric.
				// Can't simply check for null because null is valid in case units=false in Audit
				if(unitId == null && unitIdAnnotation != null){
					logger.log(Level.WARNING, "Unexpected 'unit' value {0} for process {1}", 
							new Object[]{unitIdAnnotation, process});
				}else{

					String iterationAnnotation = process.getAnnotation(ITERATION);
					Integer iteration = CommonFunctions.parseInt(iterationAnnotation, null);

					if(iteration == null && iterationAnnotation != null){
						logger.log(Level.WARNING, "Unexpected 'iteration' value {0} for process {1}", 
								new Object[]{iterationAnnotation, process});
					}else{

						String countAnnotation = process.getAnnotation(COUNT);
						Integer count = CommonFunctions.parseInt(countAnnotation, null);

						if(count == null && countAnnotation != null){
							logger.log(Level.WARNING, "Unexpected 'count' value {0} for process {1}", 
									new Object[]{countAnnotation, process});
						}else{

							String cmdLine = process.getAnnotation(COMMANDLINE);
							String ppid = process.getAnnotation(PPID);
							
							Map<CharSequence, CharSequence> properties = new HashMap<>();
							addIfNotNull(NAME, process.getAnnotations(), properties);
							addIfNotNull(CWD, process.getAnnotations(), properties);
							properties.put(PPID, ppid);

							Subject subject = new Subject(subjectUUID, subjectType, pid, 
									pidSubjectUUID.get(ppid), principalUUID, startTime, 
									unitId, iteration, count, cmdLine, null, null, null,
									properties);

							return subject;

						}

					}

				}

			}

		}else{
			logger.log(Level.WARNING, "Missing Process vertex {0} or Principal UUID {1}", 
					new Object[]{process, principalUUID});
		}
		return null;
	}

	// can return null
	private Principal getPrincipalFromProcess(AbstractVertex process){
		if(isProcessVertex(process)){
			AbstractVertex agentVertex = getAgentFromProcess(process);

			UUID uuid = getUuid(agentVertex);
			String userId = agentVertex.getAnnotation(UID);
			if(userId != null){
				Map<CharSequence, CharSequence> properties = new HashMap<CharSequence, CharSequence>();
				addIfNotNull(EUID, agentVertex.getAnnotations(), properties);
				addIfNotNull(SUID, agentVertex.getAnnotations(), properties);
				addIfNotNull(FSUID, agentVertex.getAnnotations(), properties);

				List<CharSequence> groupIds = new ArrayList<CharSequence>();
				if(agentVertex.getAnnotation(GID) != null){
					groupIds.add(agentVertex.getAnnotation(GID));
				}
				if(agentVertex.getAnnotation(EGID) != null){
					groupIds.add(agentVertex.getAnnotation(EGID));
				}
				if(agentVertex.getAnnotation(SGID) != null){
					groupIds.add(agentVertex.getAnnotation(SGID));
				}
				if(agentVertex.getAnnotation(FSGID) != null){
					groupIds.add(agentVertex.getAnnotation(FSGID));
				}
				Principal principal = new Principal(uuid, PrincipalType.PRINCIPAL_LOCAL, 
						userId, null, groupIds, properties);
				return principal;
			}else{
				logger.log(Level.WARNING, "Missing 'uid' in agent vertex");
				return null;
			}
		}else{
			logger.log(Level.WARNING, "Vertex type MUST be Process but found {0}", new Object[]{process});
			return null;
		}
	}

	// Argument must be of type process and must be ensured by the caller
	private AbstractVertex getAgentFromProcess(AbstractVertex process){
		AbstractVertex agentVertex = new Agent();
		agentVertex.addAnnotation(SOURCE, SOURCE_DEV_AUDIT); //Always /dev/audit unless changed in Audit.java
		for(String agentAnnotation : agentAnnotations){
			String agentAnnotationValue = process.getAnnotation(agentAnnotation);
			if(agentAnnotation != null){ // some are optional so check for null
				agentVertex.addAnnotation(agentAnnotation, agentAnnotationValue);
			}
		}
		return agentVertex;
	}

	private boolean publishArtifact(AbstractVertex vertex) {
		InstrumentationSource source = getInstrumentationSource(vertex.getAnnotation(SOURCE));
		if(source == null){
			return false;
		}else{
			// Make sure all artifacts without epoch are being treated fine. 
			String epochAnnotation = vertex.getAnnotation(EPOCH);
			Integer epoch = CommonFunctions.parseInt(epochAnnotation, null);
			// Non-numeric value for epoch
			if(epoch == null && epochAnnotation != null){
				logger.log(Level.WARNING, "Epoch annotation {0} must be of type LONG", new Object[]{epochAnnotation});
				return false;
			}else{

				Object tccdmObject = null;

				String artifactType = vertex.getAnnotation(SUBTYPE);
				if(ArtifactIdentifier.SUBTYPE_FILE.equals(artifactType)){

					tccdmObject = createFileObject(getUuid(vertex), 
							vertex.getAnnotation(PATH), vertex.getAnnotation(VERSION), 
							epoch, FileObjectType.FILE_OBJECT_FILE);

				}else if(ArtifactIdentifier.SUBTYPE_SOCKET.equals(artifactType)){

					String pathAnnotation = vertex.getAnnotation(PATH);

					if(pathAnnotation == null){ // is network socket

						String srcAddress = vertex.getAnnotation(SOURCE_ADDRESS);
						String srcPort = vertex.getAnnotation(SOURCE_PORT);
						String destAddress = vertex.getAnnotation(DESTINATION_ADDRESS);
						String destPort = vertex.getAnnotation(DESTINATION_PORT);

						srcAddress = srcAddress == null ? "" : srcAddress;
						destAddress = destAddress == null ? "" : destAddress;
						srcPort = srcPort == null ? "0" : srcPort;
						destPort = srcPort == null ? "0" : destPort;

						Map<CharSequence, CharSequence> properties = new HashMap<CharSequence, CharSequence>();
						addIfNotNull(VERSION, vertex.getAnnotations(), properties);

						AbstractObject baseObject = new AbstractObject(null, epoch, properties);

						tccdmObject = new NetFlowObject(getUuid(vertex), baseObject, 
								srcAddress, CommonFunctions.parseInt(srcPort, 0), 
								destAddress, CommonFunctions.parseInt(destPort, 0), null, null);


					}else{ // is unix socket

						tccdmObject = createFileObject(getUuid(vertex), 
								vertex.getAnnotation(PATH), vertex.getAnnotation(VERSION), 
								epoch, FileObjectType.FILE_OBJECT_UNIX_SOCKET);

					}

				}else if(ArtifactIdentifier.SUBTYPE_MEMORY.equals(artifactType)){

					try{
						Long memoryAddress = Long.parseLong(vertex.getAnnotation(MEMORY_ADDRESS), 16);
						Long size = null;
						if(vertex.getAnnotation(SIZE) != null && !vertex.getAnnotation(SIZE).trim().isEmpty()){
							size = Long.parseLong(vertex.getAnnotation(SIZE), 16);
						}

						Map<CharSequence, CharSequence> properties = new HashMap<CharSequence, CharSequence>();
						addIfNotNull(VERSION, vertex.getAnnotations(), properties);
						addIfNotNull(PID, vertex.getAnnotations(), properties);

						AbstractObject baseObject = new AbstractObject(null, epoch, properties);

						tccdmObject = new MemoryObject(getUuid(vertex), baseObject, memoryAddress, null, null, size);

					}catch(NumberFormatException nfe){
						logger.log(Level.WARNING, "Failed to parse memory address or size: "
								+ "" + vertex.getAnnotation(MEMORY_ADDRESS) + ", " + vertex.getAnnotation(SIZE), nfe);
						return false;
					}catch(Exception e){
						logger.log(Level.SEVERE, null, e);
						return false;
					}

				}else if(ArtifactIdentifier.SUBTYPE_PIPE.equals(artifactType)){

					String pid = vertex.getAnnotation(PID);

					if(pid == null){ // named pipe

						tccdmObject = createFileObject(getUuid(vertex), vertex.getAnnotation(PATH), 
								vertex.getAnnotation(VERSION), epoch, FileObjectType.FILE_OBJECT_NAMED_PIPE);

					}else{ // unnamed pipe

						try{

							String path = vertex.getAnnotation(PATH); // pattern -> pipe[<read_fd>-<write_fd>]

							Integer sourceFileDescriptor = CommonFunctions.parseInt(path.substring(path.indexOf('[') + 1, path.indexOf('-')), null);
							Integer sinkFileDescriptor = CommonFunctions.parseInt(path.substring(path.indexOf('-') + 1, path.indexOf(']')), null);

							if(sourceFileDescriptor == null || sinkFileDescriptor == null){
								throw new Exception("Missing read FD or write FD in an unnamed pipe artifact");
							}else{
								Map<CharSequence, CharSequence> properties = new HashMap<>();
								addIfNotNull(PID, vertex.getAnnotations(), properties);
								addIfNotNull(VERSION, vertex.getAnnotations(), properties);

								AbstractObject baseObject = new AbstractObject(null, epoch, properties);

								tccdmObject = new UnnamedPipeObject(getUuid(vertex), baseObject, sourceFileDescriptor, sinkFileDescriptor);
							}

						}catch(Exception e){
							logger.log(Level.WARNING, "Error parsing path for '"+vertex+"' for an unnamed pipe artifact", e);
							return false;
						}

					}

				}else if(ArtifactIdentifier.SUBTYPE_UNKNOWN.equals(artifactType)){

					String path = vertex.getAnnotation(PATH);
					if(path == null){
						logger.log(Level.WARNING, "Missing 'path' annotation in {0}", new Object[]{vertex});
						return false;
					}else{
						String tokens[] = path.split("/");
						if(tokens.length < 5){
							logger.log(Level.WARNING, "Malformed 'path' annotation for {0}", new Object[]{vertex});
							return false;
						}else{
							String pid = tokens[2];
							String fdString = tokens[4];
							Integer fd = CommonFunctions.parseInt(fdString, null);

							if(fd == null && fdString != null){
								logger.log(Level.WARNING, "FD {0} must be of type INT", new Object[]{fd});
								return false;
							}else{

								Map<CharSequence, CharSequence> properties = new HashMap<CharSequence, CharSequence>();
								properties.put(PID, pid);
								addIfNotNull(VERSION, vertex.getAnnotations(), properties);	                    		

								AbstractObject baseObject = new AbstractObject(null, epoch, properties);

								tccdmObject = new SrcSinkObject(getUuid(vertex), baseObject, 
										SrcSinkType.SOURCE_SINK_UNKNOWN, fd);
							}

						}
					}

				}else{
					logger.log(Level.WARNING, "Unexpected artifact subtype {0}", new Object[]{vertex});
					return false;
				}

				if(tccdmObject != null){
					return publishRecords(Arrays.asList(buildTcCDMDatum(tccdmObject, source))) > 0;
				}else{
					logger.log(Level.WARNING, "Failed to create Object from Artifact");
					return false;
				}
			}
		}
	}

	/**
	 * Creates a CDM FileObject from the given arguments
	 * 
	 * @param uuid UUID of the whole artifact vertex
	 * @param path path of the file
	 * @param version version annotation
	 * @param epoch epoch annotation
	 * @param type FileObjectType
	 * @return FileObject instance
	 */
	private FileObject createFileObject(UUID uuid, String path, String version, 
			Integer epoch, FileObjectType type){

		Map<CharSequence, CharSequence> properties = new HashMap<CharSequence, CharSequence>();
		if(path != null){
			properties.put(PATH, path);
		}
		if(version != null){
			properties.put(VERSION, version);
		}

		AbstractObject baseObject = new AbstractObject(null, epoch, properties);
		FileObject fileObject = new FileObject(uuid, baseObject, type, null, null, null, null, null);

		return fileObject;
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
		String annotationName = isStart ? START_TIME : END_TIME;

		Map<CharSequence, CharSequence> properties = new HashMap<>();
		if(!isStart){
			for(Map.Entry<String, Long> entry : stats.entrySet()){
				properties.put(entry.getKey(), String.valueOf(entry.getValue()));
			}
		}
		properties.put(annotationName, 
				String.valueOf(convertTimeToNanoseconds(null, String.valueOf(System.currentTimeMillis()), 0L)));
		AbstractObject baseObject = new AbstractObject(null, null, properties);  
		SrcSinkObject streamMarker = new SrcSinkObject(getUuid(properties), baseObject, 
				SrcSinkType.SOURCE_SYSTEM_PROPERTY, null);
		
		publishRecords(Arrays.asList(buildTcCDMDatum(streamMarker, InstrumentationSource.SOURCE_LINUX_AUDIT_TRACE)));
	}
	
	@Override
	/**
	 * Calls the superclass's publishRecords method after updating the object
	 * type count.
	 */
	protected int publishRecords(List<GenericContainer> genericContainers){
		if(genericContainers != null){
			for(GenericContainer genericContainer : genericContainers){
				try{
					if(genericContainer instanceof TCCDMDatum){
						Object cdmObject = ((TCCDMDatum) genericContainer).getDatum();
						if(cdmObject != null){
							if(cdmObject.getClass().equals(Subject.class)){
								SubjectType subjectType = ((Subject)cdmObject).getType();
								if(subjectType != null){
									incrementStatsCount(subjectType.name());
								}
							}else if(cdmObject.getClass().equals(Event.class)){
								EventType eventType = ((Event)cdmObject).getType();
								if(eventType != null){
									incrementStatsCount(eventType.name());
								}
							}else if(cdmObject.getClass().equals(SrcSinkObject.class)){
								if(((SrcSinkObject)cdmObject).getType().equals(SrcSinkType.SOURCE_SINK_UNKNOWN)){
									incrementStatsCount(SrcSinkObject.class.getSimpleName());
								}
							}else if(cdmObject.getClass().equals(FileObject.class)){
								FileObjectType fileObjectType = ((FileObject)cdmObject).getType();
								if(fileObjectType != null){
									incrementStatsCount(fileObjectType.name());
								}
							}else{
								incrementStatsCount(cdmObject.getClass().getSimpleName());
							}
						}
					}
				}catch (Exception e) {
					logger.log(Level.WARNING, "Failed to collect stats", e);
				}
			}
			return super.publishRecords(genericContainers);
		}else{
			return 0;
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
	public boolean initialize(String arguments) {
		boolean initResult = super.initialize(arguments);
		if(!initResult){
			return false;
		}// else continue

		Map<String, String> argumentsMap = CommonFunctions.parseKeyValPairs(arguments);
		if("true".equals(argumentsMap.get("hexUUIDs"))){
			hexUUIDs = true;
		}

		String configFilePath = Settings.getDefaultConfigFilePath(this.getClass());
		try{
			File configFile = new File(configFilePath);
			List<String> ruleLines = new ArrayList<String>();
			List<String> configLines = FileUtils.readLines(configFile);
			boolean rulesSectionStarted = false;
			for(String configLine : configLines){
				if(configLine.contains("### EVENT rules")){
					rulesSectionStarted = true;
				}
				if(rulesSectionStarted && !configLine.startsWith("#")){
					ruleLines.add(configLine);
				}
			}

			rulesToEventType = getEventRules(ruleLines);
			if(rulesToEventType == null || rulesToEventType.size() == 0){
				return false;
			}    		
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to read config file", e);
			return false;
		}

		publishStreamMarkerObject(true);

		return true;
	}

	/**
	 * Relies on the deduplication functionality in the Audit reporter.
	 * Doesn't do any deduplication itself.
	 * 
	 * @param incomingVertex AbstractVertex
	 * @return true if successfully published. false if failed to publish or didn't publish
	 */
	@Override
	public boolean putVertex(AbstractVertex incomingVertex) {
		if(incomingVertex != null){
			String type = incomingVertex.type();

			if(isProcessVertex(incomingVertex)){
				return publishSubjectAndPrincipal(incomingVertex);
			}else if(ARTIFACT.equals(type)){
				return publishArtifact(incomingVertex);
			}else{
				logger.log(Level.WARNING, "Unexpected vertex type {0}", new Object[]{type});
			}

		}
		return false;
	}

	/**
	 * Gets the edge passed and based on it's timestamp and event id decides
	 * whether to put it in the list of currentEventEdges or first process the 
	 * edges already in the list and then put it in the currentEventEdges list.
	 * 
	 * The currentEventEdges list is processed and emptied whenever the timestamp
	 * and event id changes to a new one from the last seen one
	 * 
	 * @param edge AbstractEdge
	 * @return false if edge is null or is missing the time and event id annotations
	 */
	@Override
	public boolean putEdge(AbstractEdge edge){

		// ASSUMPTION that all edges for the same event are contiguously sent (Audit follows this)

		if(edge != null){
			SimpleEntry<String, String> newEdgeTimeEventId = getTimeEventId(edge);

			if(newEdgeTimeEventId != null){

				if(lastTimeEventId == null){
					lastTimeEventId = newEdgeTimeEventId;
				}

				// handles the first edge case also
				if(lastTimeEventId.equals(newEdgeTimeEventId)){
					currentEventEdges.add(edge);
				}else{
					// new time,eventid so flush the current edges and move to the next
					processEdgesWrapper(currentEventEdges);
					lastTimeEventId = newEdgeTimeEventId;
					currentEventEdges.clear();
					currentEventEdges.add(edge);
				}

				return true;
			}else{
				return false;
			}

		}else{
			return false;
		}
	}
	
	// Handles special cases before calling processEdges
	private void processEdgesWrapper(List<AbstractEdge> edges){
		// special cases
		boolean processIndividually = false;
		// If execve or setuid then multiple edges in the same time,eventid 
		// but need to process them separately
		if((edgesContainTypeOperation(edges, 
				new TypeOperation(WAS_TRIGGERED_BY, EXECVE)))
				|| (edgesContainTypeOperation(edges, 
						new TypeOperation(WAS_TRIGGERED_BY, SETUID)))){
			processIndividually = true;
		}else if(edgesContainTypeOperation(edges,
				new TypeOperation(WAS_DERIVED_FROM, UPDATE))){
			// need to process the WasGeneratedBy edge first
			// and then process WasGeneratedBy and WasDerivedFrom together
			AbstractEdge generatedByEdge = null;
			for(AbstractEdge edge : edges){
				if(WAS_GENERATED_BY.equals(edge.getAnnotation(TYPE))){
					generatedByEdge = edge;
				}
			}
			if(generatedByEdge != null){
				processEdges(Arrays.asList(generatedByEdge));
			}else{
				logger.log(Level.WARNING, "Failed to find WasGeneratedBy edge for UPDATE: {0}", 
						new Object[]{
								currentEventEdges
						});
			}
		}
		
		if(processIndividually){
			for(AbstractEdge currentEventEdge : edges){
				processEdges(Arrays.asList(currentEventEdge));
			}
		}else{
			processEdges(edges);
		}
	}

	public boolean shutdown(){
		try {

			// Flush buffer
			processEdgesWrapper(currentEventEdges);
			currentEventEdges.clear();
			pidSubjectUUID.clear();
			publishedPrincipals.clear();

			publishStreamMarkerObject(false);

			return super.shutdown();
		} catch (Exception exception) {
			logger.log(Level.SEVERE, null, exception);
			return false;
		}
	}

	/**
	 * Returns true if an edge has the same type and operation as the one given
	 * 
	 * @param edges list of edges to compare against
	 * @param typeOperation TypeOperation object
	 * @return true if matched else false
	 */
	private boolean edgesContainTypeOperation(List<AbstractEdge> edges, TypeOperation typeOperation){
		if(edges != null){
			for(AbstractEdge edge : edges){
				if(typeOperation.getType().equals(edge.getAnnotation(TYPE))
						&& typeOperation.getOperation().equals(edge.getAnnotation(OPERATION))){
					return true;
				}
			}
		}
		return false;
	}
	
	/**
	 * Figures out the arguments needed for constructing an event
	 * Modifies the global state to correctly keep track of pid to subject UUID
	 * And after the event does post publishing steps
	 * @param edges list of edges to process
	 */
	private void processEdges(List<AbstractEdge> edges){
		if(edges != null && edges.size() > 0){

			AbstractVertex actedUpon1 = null, actedUpon2 = null;
			AbstractVertex actingVertex = null;
			AbstractEdge edgeForEvent = null;
			EventType eventType = getEventType(edges);

			if(edges.size() == 1){
				edgeForEvent = edges.get(0);
				if(edgeForEvent != null){
					String edgeType = edgeForEvent.type();
					String edgeOperation = edgeForEvent.getAnnotation(OPERATION);

					if(WAS_TRIGGERED_BY.equals(edgeType)){

						actingVertex = edgeForEvent.getDestinationVertex();
						actedUpon1 = edgeForEvent.getSourceVertex();

						// Handling the case where a process A setuid's and becomes A'
						// and then A' setuid's to become A. If this is not done then 
						// if process A creates a process C, then in putVertex the process
						// C would get the pid for A' instead of A as it's parentProcessUUID
						// Not doing this for UNIT vertices
						if(SETUID.equals(edgeOperation) && actedUpon1.getAnnotation(ITERATION) == null){
							// The acted upon vertex is the new containing process for the pid. 
							// Excluding units from coming in here
							pidSubjectUUID.put(actedUpon1.getAnnotation(PID), getUuid(actedUpon1));
						}
						
					}else if(WAS_GENERATED_BY.equals(edgeType)){// mmap_write here too in case of MAP_ANONYMOUS

						actingVertex = edgeForEvent.getDestinationVertex();
						actedUpon1 = edgeForEvent.getSourceVertex();

					}else if(USED.equals(edgeType)){

						actingVertex = edgeForEvent.getSourceVertex();
						actedUpon1 = edgeForEvent.getDestinationVertex();

					}else{
						logger.log(Level.WARNING, "Unexpected edge type {0}", new Object[]{edgeType});
					}

				}else{
					logger.log(Level.WARNING, "NULL edge for event {0}", new Object[]{eventType});
				}
			}else{

				AbstractEdge twoArtifactsEdge = getFirstMatchedEdge(edges, TYPE, WAS_DERIVED_FROM);
				AbstractEdge edgeWithProcess = getFirstMatchedEdge(edges, TYPE, WAS_GENERATED_BY);

				if(edges.size() == 2 || edges.size() == 3){
					// update (2), mmap(3), rename(3), link(3)
					if(twoArtifactsEdge != null && edgeWithProcess != null){
						edgeForEvent = twoArtifactsEdge;
						actedUpon1 = twoArtifactsEdge.getDestinationVertex();
						actedUpon2 = twoArtifactsEdge.getSourceVertex();
						actingVertex = edgeWithProcess.getDestinationVertex();
					}else{
						logger.log(Level.WARNING, "Failed to process event with edges {0}", new Object[]{edges});
					}
				}else{
					logger.log(Level.WARNING, "Event with invalid number of edges {0}", new Object[]{edges});
				}
			}

			if(actingVertex != null){
				
				publishEvent(eventType, edgeForEvent, actingVertex, actedUpon1, actedUpon2);

				// POST publishing things
				if(eventType.equals(EventType.EVENT_EXIT)){
					pidSubjectUUID.remove(actingVertex.getAnnotation(PID));
				}

			}else{
				logger.log(Level.WARNING, "Null Process vertex for event with edges {0}", new Object[]{edges});
			}
		}
	}

	/**
	 * Increment stats counts for the given key in the global map
	 * 
	 * @param key string
	 */
	public void incrementStatsCount(String key){
		if(stats.get(key) == null){
			stats.put(key, 0L);
		}
		stats.put(key, stats.get(key) + 1);
	}

	/**
	 * Creates a TCCDMDatum object with the given source and the value
	 * @param value the CDM object instance
	 * @param source the source value for that value
	 * @return GenericContainer instance
	 */
	private GenericContainer buildTcCDMDatum(Object value, InstrumentationSource source){
		TCCDMDatum.Builder tccdmDatumBuilder = TCCDMDatum.newBuilder();
		tccdmDatumBuilder.setDatum(value);
		tccdmDatumBuilder.setSource(source);
		return tccdmDatumBuilder.build();
	}

	/**
	 * Returns the CDM object for the source annotation
	 * Null if none matched
	 * 
	 * @param source allowed values: '/dev/audit', '/proc', 'beep'
	 * @return InstrumentationSource instance or null
	 */
	private InstrumentationSource getInstrumentationSource(String source){
		if(SOURCE_DEV_AUDIT.equals(source)){
			return InstrumentationSource.SOURCE_LINUX_AUDIT_TRACE;
		}else if(PROC.equals(source)){	
			return InstrumentationSource.SOURCE_LINUX_PROC_TRACE;
		}else if(BEEP.equals(source)){
			return InstrumentationSource.SOURCE_LINUX_BEEP_TRACE;
		}else{
			logger.log(Level.WARNING,
					"Unexpected source: {0}", new Object[]{source});
		}
		return null;
	}

	/**
	 * Converts the given time (in milliseconds) to nanoseconds
	 * 
	 * If failed to convert the given time for any reason then the default value is
	 * returned.
	 * 
	 * @param eventId id of the event
	 * @param time timestamp in milliseconds
	 * @param defaultValue default value
	 * @return the time in nanoseconds
	 */
	private Long convertTimeToNanoseconds(Long eventId, String time, Long defaultValue){
		try{
			if(time == null){
				return defaultValue;
			}
			Double timeNanoseconds = Double.parseDouble(time);
			timeNanoseconds = timeNanoseconds * 1000 * 1000 * 1000; //converting seconds to nanoseconds
			return timeNanoseconds.longValue();
		}catch(Exception e){
			logger.log(Level.INFO,
					"Time type is not Double: {0}. event id = {1}", new Object[]{time, eventId});
			return defaultValue;
		}
	}

	/**
	 * Adds the given key from the 'from' map to the 'to' map only if non-null
	 * 
	 * If either of the maps null then nothing happens
	 * 
	 * @param key key to check for and add as in the from and to maps respectively
	 * @param from map to get the value for the key from
	 * @param to map to put the value for the key and the key to
	 */
	private void addIfNotNull(String key, Map<String, String> from, Map<CharSequence, CharSequence> to){
		if(from != null && to != null){
			if(from.get(key) != null){
				to.put(key, from.get(key));
			}
		}
	}

	/**
	 * Tests whether a vertex is a process vertex without throwing an NPE
	 * 
	 * @param process AbstractVertex
	 * @return true if type is Process else false
	 */
	private boolean isProcessVertex(AbstractVertex process){
		return process != null && process.type().equals(PROCESS);
	}

	/**
	 * Creates a SimpleEntry object where the key is the time and the value is the
	 * event id. 
	 * 
	 * Returns null if edge is null.
	 * 
	 * @param edge AbstractEdge
	 * @return null/SimpleEntry object
	 */
	private SimpleEntry<String, String> getTimeEventId(AbstractEdge edge){
		if(edge != null){
			return new SimpleEntry<String, String>(edge.getAnnotation(TIME), edge.getAnnotation(EVENT_ID));
		}
		return null;
	}
	
	/**
	 * Takes a list of edges and matches them against an array of key values.
	 * Key values format. Every positive (and 0) index is the key and the next one
	 * is the value
	 * 
	 * Returns the first one that matches all the annotations expected.
	 * If a key is not found in the annotations of the edge then that edge is matched too
	 * 
	 * Returns null if edges are null or keysAndValues is null
	 * 
	 * @param edges list of AbstractEdges
	 * @param keysAndValues annotation keys and their expected values
	 * @return null / AbstractEdge
	 */
	private AbstractEdge getFirstMatchedEdge(List<AbstractEdge> edges, String... keysAndValues){
		if(edges != null && keysAndValues != null && keysAndValues.length % 2 == 0){
			for(AbstractEdge edge : edges){
				if(edge != null){
					boolean matched = true;
					for(int a = 0; a<keysAndValues.length; a+=2){
						String edgeAnnotationValue = edge.getAnnotation(keysAndValues[a]);
						if(edgeAnnotationValue != null){
							if(edgeAnnotationValue.equals(keysAndValues[a+1])){
								matched = matched && true;
							}else{
								matched = matched && false;
								break;
							}
						}else{
							matched = matched && false;
							break;
						}
					}
					if(matched){
						return edge;
					}
				}
			}
		}
		return null;
	}

	/**
	 * Builds a Set of TypeOperation objects from a list of AbstractEdges by using the
	 * 'type' and 'operation' annotations in the edge
	 * 
	 * @param edges list of edges
	 * @return set of TypeOperation objects
	 */
	private Set<TypeOperation> getTypeOperationSet(List<AbstractEdge> edges){
		Set<TypeOperation> typesAndOperations = new HashSet<TypeOperation>();
		if(edges != null){
			for(AbstractEdge edge : edges){
				typesAndOperations.add(new TypeOperation(edge.getAnnotation(TYPE), edge.getAnnotation(OPERATION)));
			}
		}
		return typesAndOperations;
	}

	/**
	 * Return EventType by first finding out the set of edge types and edge operations
	 * from the list of edges and then looking that set in the rules map
	 *     
	 * @param edges list of edges
	 * @return EventType enum value or null
	 */
	private EventType getEventType(List<AbstractEdge> edges){
		return rulesToEventType.get(getTypeOperationSet(edges));
	}

	/**
	 * Uses the bigHashCode function in AbstractVertex to get the hashcode which 
	 * is then used to build the UUID object.
	 * 
	 * The bigHashCode is converted to hex values if {@link #hexUUIDs hexUUIDs} is true
	 * 
	 * Returns null if the vertex is null
	 * 
	 * @param vertex the vertex to calculate the hash of
	 * @return null/UUID object
	 */
	private UUID getUuid(AbstractVertex vertex){
		if(vertex != null){
			byte[] vertexHash = vertex.bigHashCode();
			if(hexUUIDs){
				vertexHash = String.valueOf(Hex.encodeHex(vertexHash, true)).getBytes();
			}
			return new UUID(vertexHash);
		}
		return null;
	}

	/**
	 * Uses the bigHashCode function in AbstractEdge to get the hashcode which 
	 * is then used to build the UUID object.
	 * 
	 * The bigHashCode is converted to hex values if {@link #hexUUIDs hexUUIDs} is true
	 * 
	 * Returns null if the edge is null
	 * 
	 * @param edge the edge to calculate the hash of
	 * @return null/UUID object
	 */
	private UUID getUuid(AbstractEdge edge){
		if(edge != null){
			byte[] edgeHash = edge.bigHashCode();
			if(hexUUIDs){
				edgeHash = String.valueOf(Hex.encodeHex(edgeHash, true)).getBytes();
			}
			return new UUID(edgeHash);
		}
		return null;
	}

	/**
	 * Returns the MD5 hash of the result of the object's toString function
	 * 
	 * @param object object to hash
	 * @return UUID
	 */
	private UUID getUuid(Object object){
		byte[] hash = DigestUtils.md5(String.valueOf(object));
		if(hexUUIDs){
			hash = String.valueOf(Hex.encodeHex(hash, true)).getBytes();
		}
		return new UUID(hash);
	}

}

/**
 * A class to keep track of type and operation annotations on an OPM edge
 * 
 * hashCode and equals methods modified to add a special case when operation is '*'
 *
 */
class TypeOperation{
	private String type, operation;
	public TypeOperation(String type, String operation){
		this.type = type;
		this.operation = operation;
	}
	public String getType() {
		return type;
	}
	public String getOperation() {
		return operation;
	}
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		//		result = prime * result + ((operation == null) ? 0 : operation.hashCode());
		result = prime * result + ((type == null) ? 0 : type.hashCode());
		return result;
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TypeOperation other = (TypeOperation) obj;
		if (type == null) {
			if (other.type != null)
				return false;
		} else if (!type.equals(other.type))
			return false;
		if("*".equals(operation) || "*".equals(other.operation)){
			return true;
		}
		if (operation == null) {
			if (other.operation != null)
				return false;
		} else if (!operation.equals(other.operation))
			return false;
		return true;
	}
	@Override
	public String toString() {
		return "TypeOperation [type=" + type + ", operation=" + operation + "]";
	}
}