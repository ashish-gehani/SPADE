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

import java.nio.ByteBuffer;
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
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SslConfigs;

import com.bbn.tc.schema.avro.cdm18.AbstractObject;
import com.bbn.tc.schema.avro.cdm18.EndMarker;
import com.bbn.tc.schema.avro.cdm18.Event;
import com.bbn.tc.schema.avro.cdm18.EventType;
import com.bbn.tc.schema.avro.cdm18.FileObject;
import com.bbn.tc.schema.avro.cdm18.FileObjectType;
import com.bbn.tc.schema.avro.cdm18.Host;
import com.bbn.tc.schema.avro.cdm18.HostIdentifier;
import com.bbn.tc.schema.avro.cdm18.HostType;
import com.bbn.tc.schema.avro.cdm18.InstrumentationSource;
import com.bbn.tc.schema.avro.cdm18.Interface;
import com.bbn.tc.schema.avro.cdm18.MemoryObject;
import com.bbn.tc.schema.avro.cdm18.NetFlowObject;
import com.bbn.tc.schema.avro.cdm18.Principal;
import com.bbn.tc.schema.avro.cdm18.PrincipalType;
import com.bbn.tc.schema.avro.cdm18.SHORT;
import com.bbn.tc.schema.avro.cdm18.SrcSinkObject;
import com.bbn.tc.schema.avro.cdm18.SrcSinkType;
import com.bbn.tc.schema.avro.cdm18.StartMarker;
import com.bbn.tc.schema.avro.cdm18.Subject;
import com.bbn.tc.schema.avro.cdm18.SubjectType;
import com.bbn.tc.schema.avro.cdm18.TCCDMDatum;
import com.bbn.tc.schema.avro.cdm18.TimeMarker;
import com.bbn.tc.schema.avro.cdm18.UUID;
import com.bbn.tc.schema.avro.cdm18.UnitDependency;
import com.bbn.tc.schema.avro.cdm18.UnnamedPipeObject;
import com.bbn.tc.schema.serialization.AvroConfig;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Settings;
import spade.reporter.Audit;
import spade.reporter.audit.OPMConstants;
import spade.utility.CommonFunctions;
import spade.utility.FileUtility;
import spade.utility.HostInfo;
import spade.vertex.opm.Artifact;
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
 * How are events handled:
 * 
 * It is assumed that all edges for the same event are received back to back. All edges for the same event id and time
 * are buffered until all the edges for the current event are received. Once received those edges are processed together
 * to create the CDM event. In some special cases like setuid, update, and etc the group of edges received for a single 
 * event need to be processed individually and that is treated like a special case. All the special cases should be in
 * the function: {@link #processEdgesWrapper(List) processEdgesWrapper}
 *
 * @author Armando Caro
 * @author Hassaan Irshad
 * @author Ashish Gehani
 */
public class CDM extends Kafka {

	private final Logger logger = Logger.getLogger(CDM.class.getName());

	/**
	 * Tracking counts of different kinds of objects being published/written
	 */
	private Map<String, Long> stats = new HashMap<String, Long>();
	/**
	 * Array of integers for an Agent as in Audit OPM model
	 */
	private final String[] agentAnnotations = {OPMConstants.AGENT_UID, OPMConstants.AGENT_EUID, 
			OPMConstants.AGENT_SUID, OPMConstants.AGENT_FSUID, OPMConstants.AGENT_GID, 
			OPMConstants.AGENT_EGID, OPMConstants.AGENT_SGID, OPMConstants.AGENT_FSGID};
	/**
	 * Flag whose value is set from arguments to decide whether to output hashcode and hex or raw bytes
	 */
	private boolean hexUUIDs = false;
	
	/**
	 * Flag to tell whether to get host info from OS or not.
	 * If true then host info gotten from OS, host info saved to output file for class HostInfo and this host info
	 * published.
	 * If false then host info read from output file for class HostInfo and published.
	 */
	private boolean createHostConfig = true;
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
	private List<Object> currentVerticesAndEdges = new ArrayList<Object>();
	/**
	 * A map from the Set of edges needed for each event to complete.
	 */
	private Map<Set<TypeOperation>, EventType> rulesToEventType = 
			new HashMap<Set<TypeOperation>, EventType>();
	
	private UUID hostUUID = null;
	
	private int sessionNumber = 0; // default zero
	
	private boolean useSsl = true;
	private String securityProtocol, trustStoreLocation, trustStorePassword, keyStoreLocation, keyStorePassword, keyPassword;
	
	/**
	 * Rules from OPM edges types and operations to event types in CDM
	 */
	private void populateEventRules(){
		rulesToEventType.put(
				getSet(new TypeOperation(OPMConstants.WAS_TRIGGERED_BY, OPMConstants.OPERATION_EXIT)), 
						EventType.EVENT_EXIT);
		rulesToEventType.put(
				getSet(new TypeOperation(OPMConstants.WAS_TRIGGERED_BY, OPMConstants.OPERATION_FORK)), 
						EventType.EVENT_FORK);
		rulesToEventType.put(
				getSet(new TypeOperation(OPMConstants.WAS_TRIGGERED_BY, OPMConstants.OPERATION_CLONE)), 
						EventType.EVENT_CLONE);
		rulesToEventType.put(
				getSet(new TypeOperation(OPMConstants.WAS_TRIGGERED_BY, OPMConstants.OPERATION_EXECVE)), 
						EventType.EVENT_EXECUTE);
		rulesToEventType.put(
				getSet(new TypeOperation(OPMConstants.WAS_TRIGGERED_BY, OPMConstants.OPERATION_SETUID)), 
						EventType.EVENT_CHANGE_PRINCIPAL);
		rulesToEventType.put(
				getSet(new TypeOperation(OPMConstants.WAS_TRIGGERED_BY, OPMConstants.OPERATION_UPDATE)), 
						EventType.EVENT_CHANGE_PRINCIPAL);
		rulesToEventType.put(
				getSet(new TypeOperation(OPMConstants.WAS_TRIGGERED_BY, OPMConstants.OPERATION_SETGID)), 
						EventType.EVENT_CHANGE_PRINCIPAL);
		rulesToEventType.put(
				getSet(new TypeOperation(OPMConstants.WAS_TRIGGERED_BY, OPMConstants.OPERATION_UNIT)), 
						EventType.EVENT_UNIT);
		rulesToEventType.put(
				getSet(new TypeOperation(OPMConstants.WAS_GENERATED_BY, OPMConstants.OPERATION_CLOSE)), 
						EventType.EVENT_CLOSE);
		rulesToEventType.put(
				getSet(new TypeOperation(OPMConstants.WAS_GENERATED_BY, OPMConstants.OPERATION_UNLINK)), 
						EventType.EVENT_UNLINK);
		rulesToEventType.put(
				getSet(new TypeOperation(OPMConstants.WAS_GENERATED_BY, OPMConstants.OPERATION_OPEN)), 
						EventType.EVENT_OPEN);
		rulesToEventType.put(
				getSet(new TypeOperation(OPMConstants.WAS_GENERATED_BY, OPMConstants.OPERATION_CREATE)), 
						EventType.EVENT_CREATE_OBJECT);
		rulesToEventType.put(
				getSet(new TypeOperation(OPMConstants.WAS_GENERATED_BY, OPMConstants.OPERATION_WRITE)), 
						EventType.EVENT_WRITE);
		rulesToEventType.put(
				getSet(new TypeOperation(OPMConstants.WAS_GENERATED_BY, OPMConstants.OPERATION_SEND)), 
						EventType.EVENT_SENDMSG);
		rulesToEventType.put(
				getSet(new TypeOperation(OPMConstants.WAS_GENERATED_BY, OPMConstants.OPERATION_MPROTECT)), 
						EventType.EVENT_MPROTECT);
		rulesToEventType.put(
				getSet(new TypeOperation(OPMConstants.WAS_GENERATED_BY, OPMConstants.OPERATION_CONNECT)), 
						EventType.EVENT_CONNECT);
		rulesToEventType.put(
				getSet(new TypeOperation(OPMConstants.WAS_GENERATED_BY, OPMConstants.OPERATION_TRUNCATE)), 
						EventType.EVENT_TRUNCATE);
		rulesToEventType.put(
				getSet(new TypeOperation(OPMConstants.WAS_GENERATED_BY, OPMConstants.OPERATION_CHMOD)), 
						EventType.EVENT_MODIFY_FILE_ATTRIBUTES);
		rulesToEventType.put(
				getSet(new TypeOperation(OPMConstants.USED, OPMConstants.OPERATION_CREATE)), 
						EventType.EVENT_CREATE_OBJECT);
		rulesToEventType.put(
				getSet(new TypeOperation(OPMConstants.USED, OPMConstants.OPERATION_CLOSE)), 
						EventType.EVENT_CLOSE);
		rulesToEventType.put(
				getSet(new TypeOperation(OPMConstants.USED, OPMConstants.OPERATION_LOAD)), 
						EventType.EVENT_LOADLIBRARY);
		rulesToEventType.put(
				getSet(new TypeOperation(OPMConstants.USED, OPMConstants.OPERATION_OPEN)), 
						EventType.EVENT_OPEN);
		rulesToEventType.put(
				getSet(new TypeOperation(OPMConstants.USED, OPMConstants.OPERATION_READ)), 
						EventType.EVENT_READ);
		rulesToEventType.put(
				getSet(new TypeOperation(OPMConstants.USED, OPMConstants.OPERATION_RECV)), 
						EventType.EVENT_RECVMSG);
		rulesToEventType.put(
				getSet(new TypeOperation(OPMConstants.USED, OPMConstants.OPERATION_ACCEPT)), 
						EventType.EVENT_ACCEPT);
		rulesToEventType.put(
				getSet(new TypeOperation(OPMConstants.WAS_GENERATED_BY, 
						OPMConstants.buildOperation(OPMConstants.OPERATION_MMAP, OPMConstants.OPERATION_WRITE))), 
						EventType.EVENT_MMAP);
		rulesToEventType.put(
				getSet(new TypeOperation(OPMConstants.WAS_DERIVED_FROM, OPMConstants.OPERATION_TEE),
						new TypeOperation(OPMConstants.WAS_GENERATED_BY, 
								OPMConstants.buildOperation(OPMConstants.OPERATION_TEE, OPMConstants.OPERATION_WRITE)),
						new TypeOperation(OPMConstants.USED, 
								OPMConstants.buildOperation(OPMConstants.OPERATION_TEE, OPMConstants.OPERATION_READ))),
						EventType.EVENT_OTHER); // tee
		rulesToEventType.put(
				getSet(new TypeOperation(OPMConstants.WAS_DERIVED_FROM, OPMConstants.OPERATION_SPLICE),
						new TypeOperation(OPMConstants.WAS_GENERATED_BY, 
								OPMConstants.buildOperation(OPMConstants.OPERATION_SPLICE, OPMConstants.OPERATION_WRITE)),
						new TypeOperation(OPMConstants.USED, 
								OPMConstants.buildOperation(OPMConstants.OPERATION_SPLICE, OPMConstants.OPERATION_READ))),
						EventType.EVENT_OTHER); // splice
		rulesToEventType.put(getSet(new TypeOperation(OPMConstants.WAS_GENERATED_BY, OPMConstants.OPERATION_VMSPLICE)),
						EventType.EVENT_OTHER); // vmsplice
		rulesToEventType.put(getSet(new TypeOperation(OPMConstants.USED, OPMConstants.OPERATION_INIT_MODULE)), 
						EventType.EVENT_OTHER); // init_module
		rulesToEventType.put(getSet(new TypeOperation(OPMConstants.USED, OPMConstants.OPERATION_FINIT_MODULE)), 
						EventType.EVENT_OTHER); // finit_module
		rulesToEventType.put(
				getSet(new TypeOperation(OPMConstants.WAS_DERIVED_FROM, OPMConstants.OPERATION_MMAP),
						new TypeOperation(OPMConstants.WAS_GENERATED_BY, 
								OPMConstants.buildOperation(OPMConstants.OPERATION_MMAP, OPMConstants.OPERATION_WRITE)),
						new TypeOperation(OPMConstants.USED, 
								OPMConstants.buildOperation(OPMConstants.OPERATION_MMAP, OPMConstants.OPERATION_READ))), 
						EventType.EVENT_MMAP);
		rulesToEventType.put(
				getSet(new TypeOperation(OPMConstants.WAS_DERIVED_FROM, OPMConstants.OPERATION_RENAME),
						new TypeOperation(OPMConstants.WAS_GENERATED_BY, 
								OPMConstants.buildOperation(OPMConstants.OPERATION_RENAME, OPMConstants.OPERATION_WRITE)),
						new TypeOperation(OPMConstants.USED, 
								OPMConstants.buildOperation(OPMConstants.OPERATION_RENAME, OPMConstants.OPERATION_READ))), 
						EventType.EVENT_RENAME);
		rulesToEventType.put(
				getSet(new TypeOperation(OPMConstants.WAS_DERIVED_FROM, OPMConstants.OPERATION_LINK),
						new TypeOperation(OPMConstants.WAS_GENERATED_BY, 
								OPMConstants.buildOperation(OPMConstants.OPERATION_LINK, OPMConstants.OPERATION_WRITE)),
						new TypeOperation(OPMConstants.USED, 
								OPMConstants.buildOperation(OPMConstants.OPERATION_LINK, OPMConstants.OPERATION_READ))), 
						EventType.EVENT_LINK);
	}
	
	private Set<TypeOperation> getSet(TypeOperation... typeOperations){
		Set<TypeOperation> set = new HashSet<TypeOperation>();
		for(TypeOperation typeOperation : typeOperations){
			set.add(typeOperation);
		}
		return set;
	}

	private void publishEvent(EventType eventType, AbstractEdge edge, AbstractVertex actingProcess, 
			AbstractVertex actedUpon1, AbstractVertex actedUpon2){

		if(eventType == null || edge == null || actingProcess == null || actedUpon1 == null){
			logger.log(Level.WARNING, "Missing arguments: eventType={0}, "
					+ "edge={1}, actingProcess={2}, actedUpon1={3}", new Object[]{
							eventType, edge, actingProcess, actedUpon1
			});
		}else{
			InstrumentationSource source = getInstrumentationSource(edge.getAnnotation(OPMConstants.SOURCE));

			UUID uuid = getUuid(edge);
			Long sequence = CommonFunctions.parseLong(edge.getAnnotation(OPMConstants.EDGE_EVENT_ID), 0L);
			Integer threadId = CommonFunctions.parseInt(actingProcess.getAnnotation(OPMConstants.PROCESS_PID), null);
			UUID subjectUUID = getUuid(actingProcess);
			UUID predicateObjectUUID = getUuid(actedUpon1);
			String predicateObjectPath = actedUpon1 != null ? actedUpon1.getAnnotation(OPMConstants.ARTIFACT_PATH) : null;
			UUID predicateObject2UUID = getUuid(actedUpon2);
			String predicateObject2Path = actedUpon2 != null ? actedUpon2.getAnnotation(OPMConstants.ARTIFACT_PATH) : null;
			Long timestampNanos = convertTimeToNanoseconds(sequence, edge.getAnnotation(OPMConstants.EDGE_TIME), 0L);
			Long size = CommonFunctions.parseLong(edge.getAnnotation(OPMConstants.EDGE_SIZE), null);
			Long location = CommonFunctions.parseLong(edge.getAnnotation(OPMConstants.EDGE_OFFSET), null);

			// validation of mandatory values
			if(uuid == null || threadId == null || subjectUUID == null 
					|| predicateObjectUUID == null || source == null){
				logger.log(Level.WARNING, "NULL arguments for event: "
						+ "EdgeUUID={0}, threadId={1}, subjectUUID={2}, predicateObjectUUID={3}, "
						+ "source={4}", 
						new Object[]{uuid, threadId, subjectUUID, predicateObjectUUID, source});
			}else{

				// + Adding all annotations to the properties map that have not been added already 
				// directly to the event
				Map<CharSequence, CharSequence> properties = new HashMap<CharSequence, CharSequence>();
				for(String key : edge.getAnnotations().keySet()){
					if(!key.equals(OPMConstants.EDGE_EVENT_ID) && !key.equals(OPMConstants.EDGE_TIME) 
							&& !key.equals(OPMConstants.SOURCE) && !key.equals(OPMConstants.EDGE_PID) && 
							!key.equals(OPMConstants.EDGE_OPERATION) && !key.equals(OPMConstants.EDGE_SIZE)
							&& !key.equals(OPMConstants.TYPE) && !key.equals(OPMConstants.EDGE_OFFSET)){
						properties.put(key, edge.getAnnotation(key));
					}
				}

				if(eventType.equals(EventType.EVENT_CLOSE) ||
						eventType.equals(EventType.EVENT_OPEN)){
					// Used or WasGeneratedBy
					properties.put(OPMConstants.OPM, edge.type());
				}else if(eventType.equals(EventType.EVENT_CHANGE_PRINCIPAL)){
					properties.put(OPMConstants.EDGE_OPERATION, edge.getAnnotation(OPMConstants.EDGE_OPERATION));
				}
				
				String eventOperation = edge.getAnnotation(OPMConstants.EDGE_OPERATION);
				if(OPMConstants.OPERATION_INIT_MODULE.equals(eventOperation) // init_module
						|| OPMConstants.OPERATION_FINIT_MODULE.equals(eventOperation) // finit_module
						|| OPMConstants.OPERATION_VMSPLICE.equals(eventOperation) // vmsplice only
						|| OPMConstants.OPERATION_TEE.equals(eventOperation) // tee
						|| OPMConstants.OPERATION_SPLICE.equals(eventOperation)){ // splice
					properties.put(OPMConstants.EDGE_OPERATION, eventOperation);
				}

				Event event = new Event(uuid, 
						sequence, eventType, threadId, hostUUID, subjectUUID, predicateObjectUUID, 
						predicateObjectPath, predicateObject2UUID, predicateObject2Path, timestampNanos, 
						null, null, location, size, null, properties);

				publishRecords(Arrays.asList(buildTcCDMDatum(event, source)));

			}
		}
	}

	private boolean publishSubjectAndPrincipal(AbstractVertex process){

		if(isProcessVertex(process)){

			String pid = process.getAnnotation(OPMConstants.PROCESS_PID);

			InstrumentationSource subjectSource = getInstrumentationSource(process.getAnnotation(OPMConstants.SOURCE));

			if(subjectSource != null){
				
				// Agents cannot come from BEEP source. Added just in case.
				InstrumentationSource principalSource = 
						subjectSource.equals(InstrumentationSource.SOURCE_LINUX_BEEP_TRACE)
						? InstrumentationSource.SOURCE_LINUX_SYSCALL_TRACE : subjectSource;

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
			if(process.getAnnotation(OPMConstants.PROCESS_ITERATION) != null){
				subjectType = SubjectType.SUBJECT_UNIT; // if a unit
			}

			String pidString = process.getAnnotation(OPMConstants.PROCESS_PID);
			Integer pid = CommonFunctions.parseInt(pidString, null);
			if(pid == null){
				logger.log(Level.WARNING, "Invalid pid {0} for Process {1}", new Object[]{
						pidString, process
				});
			}else{

				// Mandatory but if missing then default value is 0
				Long startTime = convertTimeToNanoseconds(null, process.getAnnotation(OPMConstants.PROCESS_START_TIME), 0L);

				String unitIdAnnotation = process.getAnnotation(OPMConstants.PROCESS_UNIT);
				Integer unitId = CommonFunctions.parseInt(unitIdAnnotation, null);

				// meaning that the unit annotation was non-numeric.
				// Can't simply check for null because null is valid in case units=false in Audit
				if(unitId == null && unitIdAnnotation != null){
					logger.log(Level.WARNING, "Unexpected 'unit' value {0} for process {1}", 
							new Object[]{unitIdAnnotation, process});
				}else{

					String iterationAnnotation = process.getAnnotation(OPMConstants.PROCESS_ITERATION);
					Integer iteration = CommonFunctions.parseInt(iterationAnnotation, null);

					if(iteration == null && iterationAnnotation != null){
						logger.log(Level.WARNING, "Unexpected 'iteration' value {0} for process {1}", 
								new Object[]{iterationAnnotation, process});
					}else{

						String countAnnotation = process.getAnnotation(OPMConstants.PROCESS_COUNT);
						Integer count = CommonFunctions.parseInt(countAnnotation, null);

						if(count == null && countAnnotation != null){
							logger.log(Level.WARNING, "Unexpected 'count' value {0} for process {1}", 
									new Object[]{countAnnotation, process});
						}else{

							String cmdLine = process.getAnnotation(OPMConstants.PROCESS_COMMAND_LINE);
							String ppid = process.getAnnotation(OPMConstants.PROCESS_PPID);
							
							Map<CharSequence, CharSequence> properties = new HashMap<>();
							addIfNotNull(OPMConstants.PROCESS_NAME, process.getAnnotations(), properties);
							addIfNotNull(OPMConstants.PROCESS_CWD, process.getAnnotations(), properties);
							addIfNotNull(OPMConstants.PROCESS_PPID, process.getAnnotations(), properties);
							addIfNotNull(OPMConstants.PROCESS_SEEN_TIME, process.getAnnotations(), properties);

							Subject subject = new Subject(subjectUUID, subjectType, pid, 
									pidSubjectUUID.get(ppid), hostUUID, principalUUID, startTime, 
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
			String userId = agentVertex.getAnnotation(OPMConstants.AGENT_UID);
			if(userId != null){
				Map<CharSequence, CharSequence> properties = new HashMap<CharSequence, CharSequence>();
				addIfNotNull(OPMConstants.AGENT_EUID, agentVertex.getAnnotations(), properties);
				addIfNotNull(OPMConstants.AGENT_SUID, agentVertex.getAnnotations(), properties);
				addIfNotNull(OPMConstants.AGENT_FSUID, agentVertex.getAnnotations(), properties);

				List<CharSequence> groupIds = new ArrayList<CharSequence>();
				if(agentVertex.getAnnotation(OPMConstants.AGENT_GID) != null){
					groupIds.add(agentVertex.getAnnotation(OPMConstants.AGENT_GID));
				}
				if(agentVertex.getAnnotation(OPMConstants.AGENT_EGID) != null){
					groupIds.add(agentVertex.getAnnotation(OPMConstants.AGENT_EGID));
				}
				if(agentVertex.getAnnotation(OPMConstants.AGENT_SGID) != null){
					groupIds.add(agentVertex.getAnnotation(OPMConstants.AGENT_SGID));
				}
				if(agentVertex.getAnnotation(OPMConstants.AGENT_FSGID) != null){
					groupIds.add(agentVertex.getAnnotation(OPMConstants.AGENT_FSGID));
				}
				Principal principal = new Principal(uuid, PrincipalType.PRINCIPAL_LOCAL, 
						hostUUID, userId, null, groupIds, properties);
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
		agentVertex.addAnnotation(OPMConstants.SOURCE, OPMConstants.SOURCE_AUDIT_SYSCALL);
		for(String agentAnnotation : agentAnnotations){
			String agentAnnotationValue = process.getAnnotation(agentAnnotation);
			if(agentAnnotation != null){ // some are optional so check for null
				agentVertex.addAnnotation(agentAnnotation, agentAnnotationValue);
			}
		}
		return agentVertex;
	}
	
	private boolean publishHost(AbstractVertex vertex){
		if(vertex != null){
			UUID uuid = getUuid(vertex);
			String hostName = vertex.getAnnotation(OPMConstants.ARTIFACT_HOST_NETWORK_NAME);
			String serialNumber = vertex.getAnnotation(OPMConstants.ARTIFACT_HOST_SERIAL_NUMBER);
			HostIdentifier hostIdentifier = new HostIdentifier(OPMConstants.ARTIFACT_HOST_SERIAL_NUMBER, serialNumber);
			List<HostIdentifier> hostIdentifiers = Arrays.asList(hostIdentifier);
			String operatingSystem = vertex.getAnnotation(OPMConstants.ARTIFACT_HOST_OPERATING_SYSTEM);
			HostType hostType = HostType.HOST_DESKTOP; // TODO always DESKTOP in AUDIT for now.
			List<Interface> interfaces = new ArrayList<Interface>();
			String interfacesCountString = vertex.getAnnotation(OPMConstants.ARTIFACT_HOST_INTERFACES_COUNT);
			Integer interfacesCount = CommonFunctions.parseInt(interfacesCountString, null);
			if(interfacesCount != null){
				for(int a = 0; a < interfacesCount; a++){
					String interfaceName = vertex.getAnnotation(OPMConstants.buildHostNetworkInterfaceNameKey(a));
					String interfaceMacAddress = vertex.getAnnotation(OPMConstants.buildHostNetworkInterfaceMacAddressKey(a));
					String interfaceIpAddresses = vertex.getAnnotation(OPMConstants.buildHostNetworkInterfaceIpAddressesKey(a));
					interfaceIpAddresses = interfaceIpAddresses == null ? "" : interfaceIpAddresses; 
					List<CharSequence> ipAddressesList = 
							OPMConstants.parseHostNetworkInterfaceIpAddressesValue(interfaceIpAddresses);
					Interface interfaze = new Interface(interfaceName, interfaceMacAddress, ipAddressesList);
					interfaces.add(interfaze);
				}
			}
			Host host = new Host(uuid, hostName, hostIdentifiers, operatingSystem, hostType, interfaces);
			if(publishRecords(Arrays.asList(buildTcCDMDatum(host, InstrumentationSource.SOURCE_LINUX_SYSCALL_TRACE))) > 0){
				return true;
			}else{
				return false;
			}
		}else{
			return false;
		}
	}

	private boolean publishArtifact(AbstractVertex vertex) {
		InstrumentationSource source = getInstrumentationSource(vertex.getAnnotation(OPMConstants.SOURCE));
		if(source == null){
			return false;
		}else{
			// Make sure all artifacts without epoch are being treated fine. 
			String epochAnnotation = vertex.getAnnotation(OPMConstants.ARTIFACT_EPOCH);
			Integer epoch = CommonFunctions.parseInt(epochAnnotation, null);
			// Non-numeric value for epoch
			if(epoch == null && epochAnnotation != null){
				logger.log(Level.WARNING, "Epoch annotation {0} must be of type LONG", new Object[]{epochAnnotation});
				return false;
			}else{

				Object tccdmObject = null;

				String artifactType = vertex.getAnnotation(OPMConstants.ARTIFACT_SUBTYPE);
				if(OPMConstants.SUBTYPE_NETWORK_SOCKET.equals(artifactType)){

					String srcAddress = vertex.getAnnotation(OPMConstants.ARTIFACT_LOCAL_ADDRESS);
					String srcPort = vertex.getAnnotation(OPMConstants.ARTIFACT_LOCAL_PORT);
					String destAddress = vertex.getAnnotation(OPMConstants.ARTIFACT_REMOTE_ADDRESS);
					String destPort = vertex.getAnnotation(OPMConstants.ARTIFACT_REMOTE_PORT);
					String protocolName = vertex.getAnnotation(OPMConstants.ARTIFACT_PROTOCOL); //can be null
					Integer protocol = Audit.getProtocolNumber(protocolName);

					srcAddress = srcAddress == null ? "" : srcAddress;
					destAddress = destAddress == null ? "" : destAddress;
					srcPort = srcPort == null ? "0" : srcPort;
					destPort = srcPort == null ? "0" : destPort;

					Map<CharSequence, CharSequence> properties = new HashMap<CharSequence, CharSequence>();
					addIfNotNull(OPMConstants.ARTIFACT_VERSION, vertex.getAnnotations(), properties);

					AbstractObject baseObject = new AbstractObject(hostUUID, null, epoch, properties);

					tccdmObject = new NetFlowObject(getUuid(vertex), baseObject, 
							srcAddress, CommonFunctions.parseInt(srcPort, 0), 
							destAddress, CommonFunctions.parseInt(destPort, 0), protocol, null);

				}else if(OPMConstants.SUBTYPE_MEMORY_ADDRESS.equals(artifactType)){

					try{
						Long memoryAddress = Long.parseLong(vertex.getAnnotation(OPMConstants.ARTIFACT_MEMORY_ADDRESS), 16);
						Long size = null;
						if(vertex.getAnnotation(OPMConstants.ARTIFACT_SIZE) != null && 
								!vertex.getAnnotation(OPMConstants.ARTIFACT_SIZE).trim().isEmpty()){
							size = Long.parseLong(vertex.getAnnotation(OPMConstants.ARTIFACT_SIZE), 16);
						}

						Map<CharSequence, CharSequence> properties = new HashMap<CharSequence, CharSequence>();
						addIfNotNull(OPMConstants.ARTIFACT_VERSION, vertex.getAnnotations(), properties);
						addIfNotNull(OPMConstants.ARTIFACT_TGID, vertex.getAnnotations(), properties);

						AbstractObject baseObject = new AbstractObject(hostUUID, null, epoch, properties);

						tccdmObject = new MemoryObject(getUuid(vertex), baseObject, memoryAddress, null, null, size);

					}catch(NumberFormatException nfe){
						logger.log(Level.WARNING, "Failed to parse memory address or size: "
								+ "" + vertex.getAnnotation(OPMConstants.ARTIFACT_MEMORY_ADDRESS) + ", " + 
								vertex.getAnnotation(OPMConstants.ARTIFACT_SIZE), nfe);
						return false;
					}catch(Exception e){
						logger.log(Level.SEVERE, null, e);
						return false;
					}

				}else if(OPMConstants.SUBTYPE_UNNAMED_PIPE.equals(artifactType)){

					Integer sourceFileDescriptor = CommonFunctions.parseInt(vertex.getAnnotation(OPMConstants.ARTIFACT_READ_FD), null);
					Integer sinkFileDescriptor = CommonFunctions.parseInt(vertex.getAnnotation(OPMConstants.ARTIFACT_WRITE_FD), null);

					if(sourceFileDescriptor == null || sinkFileDescriptor == null){
						logger.log(Level.WARNING, "Error parsing src/sink fds in artifact {0}", new Object[]{vertex});
						return false;
					}else{
						Map<CharSequence, CharSequence> properties = new HashMap<>();
						addIfNotNull(OPMConstants.ARTIFACT_PID, vertex.getAnnotations(), properties);
						addIfNotNull(OPMConstants.ARTIFACT_VERSION, vertex.getAnnotations(), properties);

						AbstractObject baseObject = new AbstractObject(hostUUID, null, epoch, properties);

						tccdmObject = new UnnamedPipeObject(getUuid(vertex), baseObject, 
								sourceFileDescriptor, sinkFileDescriptor, null, null); // TODO UUID for src and sink???
					}
					
				}else if(OPMConstants.SUBTYPE_UNKNOWN.equals(artifactType)){

					Integer pid = CommonFunctions.parseInt(vertex.getAnnotation(OPMConstants.ARTIFACT_PID), null);
					Integer fd = CommonFunctions.parseInt(vertex.getAnnotation(OPMConstants.ARTIFACT_FD), null);
					if(fd == null || pid == null){
						logger.log(Level.WARNING, "Error parsing pid/fd in artifact {0}", new Object[]{vertex});
						return false;
					}else{

						Map<CharSequence, CharSequence> properties = new HashMap<CharSequence, CharSequence>();
						properties.put(OPMConstants.ARTIFACT_PID, String.valueOf(pid));
						addIfNotNull(OPMConstants.ARTIFACT_VERSION, vertex.getAnnotations(), properties);	                    		

						AbstractObject baseObject = new AbstractObject(hostUUID, null, epoch, properties);

						tccdmObject = new SrcSinkObject(getUuid(vertex), baseObject, 
								SrcSinkType.SRCSINK_UNKNOWN, fd);
					}

				}else if(artifactType != null){
					FileObjectType fileObjectType = null;
					switch (artifactType) {
						case OPMConstants.SUBTYPE_FILE: fileObjectType = FileObjectType.FILE_OBJECT_FILE; break;
						case OPMConstants.SUBTYPE_DIRECTORY: fileObjectType = FileObjectType.FILE_OBJECT_DIR; break;
						case OPMConstants.SUBTYPE_BLOCK_DEVICE: fileObjectType = FileObjectType.FILE_OBJECT_BLOCK; break;
						case OPMConstants.SUBTYPE_CHARACTER_DEVICE: fileObjectType = FileObjectType.FILE_OBJECT_CHAR; break;
						case OPMConstants.SUBTYPE_LINK: fileObjectType = FileObjectType.FILE_OBJECT_LINK; break;
						case OPMConstants.SUBTYPE_UNIX_SOCKET: fileObjectType = FileObjectType.FILE_OBJECT_UNIX_SOCKET; break;
						case OPMConstants.SUBTYPE_NAMED_PIPE: fileObjectType = FileObjectType.FILE_OBJECT_NAMED_PIPE; break;
						default: break;
					}
					if(fileObjectType != null){
						tccdmObject = createFileObject(getUuid(vertex), 
								vertex.getAnnotation(OPMConstants.ARTIFACT_PATH), 
								vertex.getAnnotation(OPMConstants.ARTIFACT_VERSION), 
								epoch, 
								vertex.getAnnotation(OPMConstants.ARTIFACT_PERMISSIONS), 
								fileObjectType);
					}else{
						logger.log(Level.WARNING, "Unexpected artifact subtype {0}", new Object[]{vertex});
						return false;
					}
				}else{
					logger.log(Level.WARNING, "NULL artifact subtype {0}", new Object[]{vertex});
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
	 * @param permissionsAnnotation permissions in octal string format (can be null)
	 * @param type FileObjectType
	 * @return FileObject instance
	 */
	private FileObject createFileObject(UUID uuid, String path, String version,
			Integer epoch, String permissionsAnnotation, FileObjectType type){

		Map<CharSequence, CharSequence> properties = new HashMap<CharSequence, CharSequence>();
		if(path != null){
			properties.put(OPMConstants.ARTIFACT_PATH, path);
		}
		if(version != null){
			properties.put(OPMConstants.ARTIFACT_VERSION, version);
		}
		
		SHORT permissions = getPermissionsAsCDMSHORT(permissionsAnnotation);

		AbstractObject baseObject = new AbstractObject(hostUUID, permissions, epoch, properties);
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
		long currentTimeNanos = System.currentTimeMillis() * 1000 * 1000; // millis to nanos
		TimeMarker timeMarker = new TimeMarker(currentTimeNanos);
		Object sessionMarker = null;
		if(isStart){
			sessionMarker = new StartMarker(this.sessionNumber);
		}else{
			// manually add the time marker for end and the session end marker count
			incrementStatsCount(timeMarker.getClass().getSimpleName());
			incrementStatsCount(EndMarker.class.getSimpleName());
			Map<CharSequence, CharSequence> recordCounts = new HashMap<>();
			for(Map.Entry<String, Long> entry : this.stats.entrySet()){
				recordCounts.put(entry.getKey(), String.valueOf(entry.getValue()));
			}
			sessionMarker = new EndMarker(this.sessionNumber, recordCounts);
		}
		// First publish time marker and then publish session start/end marker
		publishRecords(Arrays.asList(buildTcCDMDatum(timeMarker, InstrumentationSource.SOURCE_LINUX_SYSCALL_TRACE)));
		publishRecords(Arrays.asList(buildTcCDMDatum(sessionMarker, InstrumentationSource.SOURCE_LINUX_SYSCALL_TRACE)));
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
									String keyName = eventType.name();
									if(eventType.equals(EventType.EVENT_OTHER)){
										CharSequence keyNameCharSeq = ((Event)cdmObject).getProperties().get(OPMConstants.EDGE_OPERATION);
										if(keyNameCharSeq != null){
											keyName = String.valueOf(keyNameCharSeq);
										}
									}
									incrementStatsCount(keyName);
								}
							}else if(cdmObject.getClass().equals(SrcSinkObject.class)){
								if(((SrcSinkObject)cdmObject).getType().equals(SrcSinkType.SRCSINK_UNKNOWN)){
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
		if(useSsl){
			properties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, securityProtocol);
			properties.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, trustStoreLocation);
			properties.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, trustStorePassword);
			properties.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, keyStoreLocation);
			properties.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, keyStorePassword);
			properties.put(SslConfigs.SSL_KEY_PASSWORD_CONFIG, keyPassword);
		}
		return properties;
	}

	@Override
	public boolean initialize(String arguments){
		Map<String, String> argumentsMap = CommonFunctions.parseKeyValPairs(arguments);
		String hexUUIDsArgValue = argumentsMap.get("hexUUIDs");
		if(hexUUIDsArgValue != null){
			hexUUIDsArgValue = hexUUIDsArgValue.trim();
			if("false".equals(hexUUIDsArgValue)){
				hexUUIDs = false;
			}else if("true".equals(hexUUIDsArgValue)){
				hexUUIDs = true;
			}else{
				logger.log(Level.SEVERE, "Invalid 'hexUUIDs' value: " + hexUUIDsArgValue + ". Only 'true' or 'false'");
				return false;
			}
		}
		
		String sessionNumberString = argumentsMap.get("session");
		if(sessionNumberString != null){
			Integer sessionNumber = CommonFunctions.parseInt(sessionNumberString, null);
			if(sessionNumber == null){
				logger.log(Level.SEVERE, "'session' must be an 'int': " + sessionNumberString);
				return false;
			}else{
				this.sessionNumber = sessionNumber;
			}
		}
		
		String createHostConfigArgValue = argumentsMap.get("createHostConfig");
		if(createHostConfigArgValue != null){
			createHostConfigArgValue = createHostConfigArgValue.trim();
			if("false".equals(createHostConfigArgValue)){
				createHostConfig = false;
			}else if("true".equals(createHostConfigArgValue)){
				createHostConfig = true;
			}else{
				logger.log(Level.SEVERE, "Invalid 'createHostConfig' value: " + createHostConfigArgValue + ". Only 'true' or 'false'");
				return false;
			}
		}
		
		// Populate the ssl configs before calling parent's initialize because of ssl properties.
		String sslArgValue = argumentsMap.get("ssl");
		if(sslArgValue != null){
			if("false".equals(sslArgValue)){
				useSsl = false;
			}else if("true".equals(sslArgValue)){
				useSsl = true;
			}else{
				logger.log(Level.SEVERE, "Invalid 'ssl' value: " + useSsl + ". Only 'true' or 'false'");
				return false;
			}
		}
		
		// Only do the following checks in case of server
		if(useSsl && writeDataToServer(argumentsMap)){
			String configFile = Settings.getDefaultConfigFilePath(this.getClass());
			try{
				Map<String, String> configMap = FileUtility.readConfigFileAsKeyValueMap(configFile, "=");
				securityProtocol = configMap.get("SecurityProtocol");
				trustStoreLocation = configMap.get("TrustStoreLocation");
				trustStorePassword = configMap.get("TrustStorePassword");
				keyStoreLocation = configMap.get("KeyStoreLocation");
				keyStorePassword = configMap.get("KeyStorePassword");
				keyPassword = configMap.get("KeyPassword");
				if(securityProtocol == null || trustStoreLocation == null || trustStorePassword == null || keyStoreLocation == null
						|| keyStorePassword == null || keyPassword == null){
					logger.log(Level.SEVERE, "In config file the following keys must be defined: 'SecurityProtocol', 'TrustStoreLocation', "
							+ "'TrustStorePassword', 'KeyStoreLocation', 'KeyStorePassword', 'KeyPassword'");
					return false;
				}
				if(!FileUtility.fileExists(trustStoreLocation)){
					logger.log(Level.SEVERE, "Invalid location for trust store 'TrustStoreLocation': " + trustStoreLocation);
					return false;
				}
				if(!FileUtility.fileExists(keyStoreLocation)){
					logger.log(Level.SEVERE, "Invalid location for key store 'KeyStoreLocation': " + keyStoreLocation);
					return false;
				}
			}catch(Exception e){
				logger.log(Level.SEVERE, "Failed to read config file: " + configFile);
				return false;
			}
		}
		
		boolean initResult = super.initialize(arguments);
		if(!initResult){
			return false;
		}else{
			AbstractVertex hostVertex = createHostVertex();
			if(hostVertex == null){
				return false;
			}else{
				populateEventRules();
				publishStreamMarkerObject(true);
				publishHost(hostVertex);
				this.hostUUID = getUuid(hostVertex);
			}
			return true;
		}
	}
	
	private AbstractVertex createHostVertex(){
		String configFilePath = Settings.getDefaultConfigFilePath(this.getClass());
		Map<String, String> configMap = null;
		try{
			configMap = FileUtility.readConfigFileAsKeyValueMap(configFilePath, "=");
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to read config file: "+ configFilePath, e);
			return null;
		}
		
		String hostFileKey = "hostFile";
		String hostFilePath = configMap.get(hostFileKey);
		
		if(hostFilePath == null || (hostFilePath = hostFilePath.trim()).isEmpty()){
			logger.log(Level.SEVERE, "Missing/Empty '"+hostFileKey+"' value in config");
			return null;
		}else{
			if(createHostConfig){
				if(!HostInfo.generateCurrentHostFile(hostFilePath)){
					// Failed to write the host file.
					return null;
				}
			}
			if(FileUtility.fileExists(hostFilePath)){
				HostInfo.Host hostInfo = HostInfo.ReadFromFile.readSafe(hostFilePath);
				AbstractVertex hostVertex = new Artifact();
				hostVertex.addAnnotations(hostInfo.getAnnotationsMap());
				return hostVertex;
			}else{
				logger.log(Level.SEVERE, "Missing host file at path: " + hostFilePath);
				return null;
			}
		}
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
		currentVerticesAndEdges.add(incomingVertex);
		return true;
	}
	
	private boolean publishVertex(AbstractVertex incomingVertex) {
		try{
			if(incomingVertex != null){
				String type = incomingVertex.type();
	
				if(isProcessVertex(incomingVertex)){
					return publishSubjectAndPrincipal(incomingVertex);
				}else if(OPMConstants.ARTIFACT.equals(type)){
					if(OPMConstants.SOURCE_AUDIT_NETFILTER.equals(incomingVertex.getAnnotation(OPMConstants.SOURCE))){
						// Ignore until CDM updated with refine edge. TODO
					}else{
						return publishArtifact(incomingVertex);
					}
				}else{
					logger.log(Level.WARNING, "Unexpected vertex type {0}", new Object[]{type});
				}
	
			}
		}catch(Exception e){
			logger.log(Level.WARNING, null, e);
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

		try{
			if(edge != null){
				SimpleEntry<String, String> newEdgeTimeEventId = getTimeEventId(edge);
	
				if(newEdgeTimeEventId != null){
	
					if(lastTimeEventId == null){
						lastTimeEventId = newEdgeTimeEventId;
					}
	
					// handles the first edge case also
					if(lastTimeEventId.equals(newEdgeTimeEventId)){
						currentVerticesAndEdges.add(edge);
					}else{
						// new time,eventid so flush the current edges and move to the next
						publishVerticesAndEdges(currentVerticesAndEdges);
						lastTimeEventId = newEdgeTimeEventId;
						currentVerticesAndEdges.clear();
						currentVerticesAndEdges.add(edge);
					}
	
					return true;
				}else{
					return false;
				}
	
			}else{
				return false;
			}
		}catch(Exception e){
			logger.log(Level.WARNING, null, e);
			return false;
		}
	}
	
	private void publishVerticesAndEdges(List<Object> objects){
		/*
		 * First process the vertices before the edges for this event
		 * Then process the edges
		 * Then process the vertices after the edges
		 * 
		 * Doing this to make sure that state set by a process vertex after an edge
		 * isn't overwritten by an edge. Specifically the case of setuid/setgid over-
		 * writing the subject uuid for a pid set by a process vertex later on.
		 * 
		 */
		// Create a copy to be safe
		List<Object> objectsCopy = new ArrayList<Object>(objects);
		
		/*
		 * Handling the case where each event can now contain process agent update events
		 * with the same time and event id. 
		 * Iterating through the list and processing them first one by one (i.e. as each 
		 * edge for process update is encountered). 
		 * Then the rest of the list is processed (excluding the already processed one)
		 * 
		 * Note: Relies on NO mixing of vertices. Meaning that all vertices for an edge are seen
		 * before than edge and there is no edge between a vertex and the corresponding edge.
		 * 
		 * Example: P1, P2, P2->P1, P4, P4->P2 (is correct), and P1, P2, P4, P2->P1, P4->P2 (is wrong)
		 */
		int lastProcessUpdateEdgeIndex = 0;
		List<AbstractVertex> currentProcessUpdateVertices = new ArrayList<AbstractVertex>();
		for(int a = 0; a < objectsCopy.size(); a++){
			Object object = objectsCopy.get(a);
			if(object instanceof AbstractVertex){
				currentProcessUpdateVertices.add((AbstractVertex)object);
			}else if(object instanceof AbstractEdge){
				AbstractEdge edge = (AbstractEdge)object;
				if(edge.type().equals(OPMConstants.WAS_TRIGGERED_BY)
						&& OPMConstants.OPERATION_UPDATE.equals(edge.getAnnotation(OPMConstants.EDGE_OPERATION))){
					for(AbstractVertex vertex : currentProcessUpdateVertices){
						publishVertex(vertex);
					}
					processEdgesWrapper(Arrays.asList(edge));
					currentProcessUpdateVertices.clear();
					lastProcessUpdateEdgeIndex = a+1;
				}
			}else{
				logger.log(Level.WARNING, "Unexpected object type in: " + objects);
			}
		}
		
		// process the rest if there are any remaining vertices and edges
		if(objectsCopy.size() > lastProcessUpdateEdgeIndex){
			objectsCopy = objectsCopy.subList(lastProcessUpdateEdgeIndex, objectsCopy.size());
			
			List<AbstractEdge> edges = new ArrayList<AbstractEdge>();
			List<AbstractVertex> verticesBeforeAndBetweenEdges = new ArrayList<AbstractVertex>();
			List<AbstractVertex> verticesAfterTheLastEdge = new ArrayList<AbstractVertex>();
			
			List<AbstractVertex> vertexListRef = verticesAfterTheLastEdge;
			
			for(int a = objectsCopy.size() - 1 ; a>=0; a--){
				Object object = objectsCopy.get(a);
				if(object instanceof AbstractVertex){
					vertexListRef.add((AbstractVertex)object);
				}else if(object instanceof AbstractEdge){
					vertexListRef = verticesBeforeAndBetweenEdges;
					edges.add((AbstractEdge)object);
				}else{
					logger.log(Level.WARNING, "Unexpected object type in: " + objects);
				}
			}
			
			for(AbstractVertex vertex : verticesBeforeAndBetweenEdges){
				publishVertex(vertex);
			}
			processEdgesWrapper(edges);
			for(AbstractVertex vertex : verticesAfterTheLastEdge){
				publishVertex(vertex);
			}
		}
	}
	
	// Handles special cases before calling processEdges
	private void processEdgesWrapper(List<AbstractEdge> edges){
		// special cases
		boolean processIndividually = false;
		// If execve or setuid then multiple edges in the same time,eventid 
		// but need to process them separately
		if((edgesContainTypeOperation(edges, 
				new TypeOperation(OPMConstants.USED, OPMConstants.OPERATION_LOAD))
				|| edgesContainTypeOperation(edges, 
						new TypeOperation(OPMConstants.WAS_TRIGGERED_BY, OPMConstants.OPERATION_EXECVE)))
				|| (edgesContainTypeOperation(edges, 
						new TypeOperation(OPMConstants.WAS_TRIGGERED_BY, OPMConstants.OPERATION_SETUID)))
				|| (edgesContainTypeOperation(edges, 
						new TypeOperation(OPMConstants.WAS_TRIGGERED_BY, OPMConstants.OPERATION_SETGID)))
				|| (edgesContainTypeOperation(edges, 
						new TypeOperation(OPMConstants.WAS_TRIGGERED_BY, OPMConstants.OPERATION_UPDATE)))){
			processIndividually = true;
		}else if(edgesContainTypeOperation(edges,
				new TypeOperation(OPMConstants.WAS_DERIVED_FROM, OPMConstants.OPERATION_UPDATE))){
			
			// Process the update edge here first
			// Remove the update edge
			// Then send on the remaining edges to processEdges function
			AbstractEdge updateEdge = null;
			AbstractVertex actingVertex = null;
			for(AbstractEdge edge : edges){
				if(OPMConstants.OPERATION_UPDATE.equals(edge.getAnnotation(OPMConstants.EDGE_OPERATION))){
					if(OPMConstants.SOURCE_AUDIT_NETFILTER.equals(edge.getAnnotation(OPMConstants.SOURCE))){
						// Means that this is the new edge: netfilter network -> WDF -> syscall network
						// TODO update this when this event option added in CDM
						// Ignoring the edge for now
						// This edge comes from a netfilter event and has a unique time:eventid combo
						// Only this (one) edge for this event
						return;
					}
					updateEdge = edge;
				}
				if(OPMConstants.USED.equals(edge.getAnnotation(OPMConstants.TYPE))){
					actingVertex = edge.getChildVertex();
				}else if(OPMConstants.WAS_GENERATED_BY.equals(edge.getAnnotation(OPMConstants.TYPE))){
					actingVertex = edge.getParentVertex();
				}
			}
			
			if(updateEdge == null || actingVertex == null){
				logger.log(Level.WARNING, "Missing acting vertex or update edge in edges: " + edges);
				return;
			}else{
				publishEvent(EventType.EVENT_UPDATE, updateEdge, actingVertex, 
						updateEdge.getParentVertex(), updateEdge.getChildVertex());
				
				// Remove the update edge and process the rest of the edges
				List<AbstractEdge> edgesCopy = new ArrayList<AbstractEdge>(edges);
				edgesCopy.remove(updateEdge);
				
				processEdges(edgesCopy);
				return;
			}
			
		}else if(edgesContainTypeOperation(edges, 
				new TypeOperation(OPMConstants.WAS_TRIGGERED_BY, OPMConstants.OPERATION_UNIT_DEPENDENCY))){
			// very special case. ah!
			// We can get operation=unit edges along with operation=unit dependency edges
			// Processing unit dependency edges individually here because they have no event type
			// Then sending all operation=unit edges (if any) ahead to be processed individually
			List<AbstractEdge> edgesCopy = new ArrayList<AbstractEdge>(edges);
			for(int a = edgesCopy.size() - 1; a> -1; a--){
				AbstractEdge edge = edgesCopy.get(a);
				if(edge.getAnnotation(OPMConstants.EDGE_OPERATION).equals(OPMConstants.OPERATION_UNIT_DEPENDENCY)
						&& edge.getAnnotation(OPMConstants.TYPE).equals(OPMConstants.WAS_TRIGGERED_BY)){
					AbstractVertex acting = edge.getParentVertex();
					AbstractVertex dependent = edge.getChildVertex();
					UnitDependency unitDependency = new UnitDependency(getUuid(acting), getUuid(dependent));
					publishRecords(Arrays.asList(buildTcCDMDatum(unitDependency, InstrumentationSource.SOURCE_LINUX_BEEP_TRACE)));
					edgesCopy.remove(a);
				}
			}
			if(edgesCopy.isEmpty()){
				return;
			}else{
				processIndividually = true;
				edges = edgesCopy;
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
			publishVerticesAndEdges(currentVerticesAndEdges);
			currentVerticesAndEdges.clear();
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
				if(typeOperation.getType().equals(edge.getAnnotation(OPMConstants.TYPE))
						&& typeOperation.getOperation().equals(edge.getAnnotation(OPMConstants.EDGE_OPERATION))){
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
					String edgeOperation = edgeForEvent.getAnnotation(OPMConstants.EDGE_OPERATION);

					if(OPMConstants.WAS_TRIGGERED_BY.equals(edgeType)){

						actingVertex = edgeForEvent.getParentVertex();
						actedUpon1 = edgeForEvent.getChildVertex();

						// Handling the case where a process A setuids and becomes A'
						// and then A' setuid's to become A. If this is not done then 
						// if process A creates a process C, then in putVertex the process
						// C would get the pid for A' instead of A as it's parentProcessUUID
						// Not doing this for UNIT vertices
						if((OPMConstants.OPERATION_SETUID.equals(edgeOperation) 
								|| OPMConstants.OPERATION_SETGID.equals(edgeOperation)
								|| OPMConstants.OPERATION_UPDATE.equals(edgeOperation))
								&& actedUpon1.getAnnotation(OPMConstants.PROCESS_ITERATION) == null){
							// The acted upon vertex is the new containing process for the pid. 
							// Excluding units from coming in here
							pidSubjectUUID.put(actedUpon1.getAnnotation(OPMConstants.PROCESS_PID), getUuid(actedUpon1));
						}
						
					}else if(OPMConstants.WAS_GENERATED_BY.equals(edgeType)){// 'mmap (write)' here too in case of MAP_ANONYMOUS

						actingVertex = edgeForEvent.getParentVertex();
						actedUpon1 = edgeForEvent.getChildVertex();

					}else if(OPMConstants.USED.equals(edgeType)){

						actingVertex = edgeForEvent.getChildVertex();
						actedUpon1 = edgeForEvent.getParentVertex();

					}else{
						logger.log(Level.WARNING, "Unexpected edge type {0}", new Object[]{edgeType});
					}

				}else{
					logger.log(Level.WARNING, "NULL edge for event {0}", new Object[]{eventType});
				}
			}else{

				AbstractEdge twoArtifactsEdge = getFirstMatchedEdge(edges, OPMConstants.TYPE, OPMConstants.WAS_DERIVED_FROM);
				AbstractEdge edgeWithProcess = getFirstMatchedEdge(edges, OPMConstants.TYPE, OPMConstants.WAS_GENERATED_BY);

				if(edges.size() == 2 || edges.size() == 3){
					// mmap(2 or 3 edges), rename(3), link(3), tee(3), splice(3)
					if(twoArtifactsEdge != null && edgeWithProcess != null){

						// Add the protection to the WDF edge from the WGB edge (only for mmap)
						if(twoArtifactsEdge.getAnnotation(OPMConstants.EDGE_OPERATION).
								equals(OPMConstants.OPERATION_MMAP)){
							twoArtifactsEdge.addAnnotation(OPMConstants.EDGE_PROTECTION, 
									edgeWithProcess.getAnnotation(OPMConstants.EDGE_PROTECTION));
						}

						edgeForEvent = twoArtifactsEdge;
						actedUpon1 = twoArtifactsEdge.getParentVertex();
						actedUpon2 = twoArtifactsEdge.getChildVertex();
						actingVertex = edgeWithProcess.getParentVertex();
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
					pidSubjectUUID.remove(actingVertex.getAnnotation(OPMConstants.PROCESS_PID));
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
	 * @param source allowed values listed in OPMConstants class
	 * @return InstrumentationSource instance or null
	 */
	private InstrumentationSource getInstrumentationSource(String source){
		if(OPMConstants.SOURCE_AUDIT_SYSCALL.equals(source)){
			return InstrumentationSource.SOURCE_LINUX_SYSCALL_TRACE;
		}else if(OPMConstants.SOURCE_AUDIT_NETFILTER.equals(source)){
			return InstrumentationSource.SOURCE_LINUX_NETFILTER_TRACE;
		}else if(OPMConstants.SOURCE_PROCFS.equals(source)){	
			return InstrumentationSource.SOURCE_LINUX_PROC_TRACE;
		}else if(OPMConstants.SOURCE_BEEP.equals(source)){
			return InstrumentationSource.SOURCE_LINUX_BEEP_TRACE;
		}else{
			logger.log(Level.WARNING,
					"Unexpected source: {0}", new Object[]{source});
		}
		return null;
	}

	/**
	 * Converts the given time (in seconds) to nanoseconds
	 * 
	 * If failed to convert the given time for any reason then the default value is
	 * returned.
	 * 
	 * @param eventId id of the event
	 * @param time timestamp in seconds
	 * @param defaultValue default value
	 * @return the time in nanoseconds
	 */
	private Long convertTimeToNanoseconds(Long eventId, String time, Long defaultValue){
		try{
			if(time == null){
				return defaultValue;
			}
			Double timeNanosecondsDouble = Double.parseDouble(time) * 1000; // Going to convert to long so multiply before
			Long timeNanosecondsLong = timeNanosecondsDouble.longValue();
			timeNanosecondsLong = timeNanosecondsLong * 1000 * 1000; //converting milliseconds to nanoseconds
			return timeNanosecondsLong;
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
		return process != null && process.type().equals(OPMConstants.PROCESS);
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
			return new SimpleEntry<String, String>(edge.getAnnotation(OPMConstants.EDGE_TIME), 
					edge.getAnnotation(OPMConstants.EDGE_EVENT_ID));
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
				typesAndOperations.add(new TypeOperation(edge.getAnnotation(OPMConstants.TYPE), 
						edge.getAnnotation(OPMConstants.EDGE_OPERATION)));
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
			byte[] vertexHash = new byte[0];
			vertexHash = vertex.bigHashCodeBytes();
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
			byte[] edgeHash = new byte[0];
			edgeHash = edge.bigHashCodeBytes();
			if(hexUUIDs){
				edgeHash = String.valueOf(Hex.encodeHex(edgeHash, true)).getBytes();
			}
			return new UUID(edgeHash);
		}
		return null;
	}
	
	/**
	 * Converts the permissions string to short first (using base 8) and then
	 * adds writes the short to a bytebuffer and then gets the byte array from the
	 * buffer which is then added to the CDM SHORT type
	 * 
	 * @param permissions octal permissions string
	 * @return CDM SHORT type or null
	 */
	private SHORT getPermissionsAsCDMSHORT(String permissions){
		// IMPORTANT: If this function is changed then change the function in CDM reporter which reverses this
		if(permissions == null || permissions.isEmpty()){
			return null;
		}else{
			Short permissionsShort = Short.parseShort(permissions, 8);
			ByteBuffer bb = ByteBuffer.allocate(2);
			bb.putShort(permissionsShort);
			SHORT cdmPermissions = new SHORT(bb.array());
			return cdmPermissions;
		}
	}
}

/**
 * A class to keep track of type and operation annotations on an OPM edge
 * 
 * hashCode and equals methods modified to add a special case when operation is '*'
 *
 */
class TypeOperation{
	public static final String ANY_OPERATION = "*";
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
		// Not using operation because it can have the special value -> '*'
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
		if(ANY_OPERATION.equals(operation) || ANY_OPERATION.equals(other.operation)){
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