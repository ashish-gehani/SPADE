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
package spade.reporter;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.avro.Schema;
import org.apache.avro.Schema.Parser;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.commons.codec.binary.Hex;

import com.bbn.tc.schema.avro.cdm19.AbstractObject;
import com.bbn.tc.schema.avro.cdm19.Event;
import com.bbn.tc.schema.avro.cdm19.EventType;
import com.bbn.tc.schema.avro.cdm19.FileObject;
import com.bbn.tc.schema.avro.cdm19.Host;
import com.bbn.tc.schema.avro.cdm19.HostIdentifier;
import com.bbn.tc.schema.avro.cdm19.InstrumentationSource;
import com.bbn.tc.schema.avro.cdm19.Interface;
import com.bbn.tc.schema.avro.cdm19.IpcObject;
import com.bbn.tc.schema.avro.cdm19.IpcObjectType;
import com.bbn.tc.schema.avro.cdm19.MemoryObject;
import com.bbn.tc.schema.avro.cdm19.NetFlowObject;
import com.bbn.tc.schema.avro.cdm19.Principal;
import com.bbn.tc.schema.avro.cdm19.RecordType;
import com.bbn.tc.schema.avro.cdm19.SHORT;
import com.bbn.tc.schema.avro.cdm19.SrcSinkObject;
import com.bbn.tc.schema.avro.cdm19.Subject;
import com.bbn.tc.schema.avro.cdm19.TCCDMDatum;
import com.bbn.tc.schema.avro.cdm19.UUID;
import com.bbn.tc.schema.avro.cdm19.UnitDependency;

import spade.core.AbstractEdge;
import spade.core.AbstractReporter;
import spade.core.AbstractVertex;
import spade.core.Settings;
import spade.edge.cdm.SimpleEdge;
import spade.reporter.audit.OPMConstants;
import spade.utility.CommonFunctions;
import spade.utility.ExternalMemoryMap;
import spade.utility.FileUtility;
import spade.utility.Hasher;

/**
 * CDM reporter that reads output of CDM json storage.
 *	
 * Assumes that all vertices are seen before the edges they are a part of.
 * If a vertex is not found then edge is not put.
 *
 */
public class CDM extends AbstractReporter{
	
	private final Logger logger = Logger.getLogger(this.getClass().getName());
	
	// Keys used in config
	private static final String CONFIG_KEY_CACHE_DATABASE_PARENT_PATH = "cacheDatabasePath",
								CONFIG_KEY_CACHE_DATABASE_NAME = "verticesDatabaseName",
								CONFIG_KEY_CACHE_SIZE = "verticesCacheSize",
								CONFIG_KEY_BLOOMFILTER_FALSE_PROBABILITY = "verticesBloomfilterFalsePositiveProbability",
								CONFIG_KEY_BLOOMFILTER_EXPECTED_ELEMENTS = "verticesBloomFilterExpectedNumberOfElements",
								CONFIG_KEY_SCHEMA = "Schema";
	
	public final static String KEY_CDM_TYPE = "cdm.type";
	
	//Reporting variables
	private boolean reportingEnabled = false;
	private long reportEveryMs;
	private long lastReportedTime;
	private long linesRead = 0;

	private volatile boolean shutdown = false;
	
	// Using an external map because can grow arbitrarily
	private ExternalMemoryMap<String, AbstractVertex> uuidToVertexMap;
	private final String uuidMapId = "CDM[UUID2VertexMap]";
	
	private LinkedList<DataReader> dataReaders = new LinkedList<DataReader>();
	private boolean waitForLog = true;
		
	// The main thread that processes the file
	private Thread datumProcessorThread = new Thread(new Runnable(){
		@Override
		public void run(){
			boolean shutdownCalledAndSucceeded = false;
			try{
				while(!dataReaders.isEmpty()){
					DataReader dataReader = dataReaders.removeFirst();
					String currentFilePath = dataReader.getDataFilePath();
					logger.log(Level.INFO, "Started reading file: " + currentFilePath);
					TCCDMDatum tccdmDatum = null;
					while((tccdmDatum = (TCCDMDatum)dataReader.read()) != null){
						if(shutdown && !waitForLog){
							shutdownCalledAndSucceeded = true;
							logger.log(Level.INFO, "Shutting down the data reader thread");
							break;
						}
						processDatum(tccdmDatum);
					}
					try{
						dataReader.close();
					}catch(Exception e){
						logger.log(Level.WARNING, "Continuing but FAILED to close data reader for file: " + 
								currentFilePath, e);
					}
					if(shutdownCalledAndSucceeded){ // break out of the outer loop too
						break;
					}
					logger.log(Level.INFO, "Finished reading file: " + currentFilePath);
				}
				if(!shutdownCalledAndSucceeded){ // If shutdown not called
					logger.log(Level.INFO, "Finished reading all file(s)");
				}
			}catch(Exception e){
				logger.log(Level.SEVERE, "Stopping because of reading/processing error", e);
			}
			// Here either because of exception, shutdown, or all files read.
			doCleanup();
			logger.log(Level.INFO, "Exiting data reader thread");
		}
	}, "CDM-Reporter");
	
	private Map<String, String> readDefaultConfigFile(){
		try{
			return FileUtility.readConfigFileAsKeyValueMap(
					Settings.getDefaultConfigFilePath(this.getClass()),
					"="
					);
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to load config file", e);
			return null;
		}
	}
	
	private ExternalMemoryMap<String, AbstractVertex> initCacheMap(String tempDirPath, String verticesDatabaseName, String verticesCacheSize,
			String verticesBloomfilterFalsePositiveProbability, String verticesBloomfilterExpectedNumberOfElements){
		try{
			return CommonFunctions.createExternalMemoryMapInstance(uuidMapId, verticesCacheSize, 
					verticesBloomfilterFalsePositiveProbability, verticesBloomfilterExpectedNumberOfElements, tempDirPath, 
					verticesDatabaseName, null, new Hasher<String>(){
						@Override
						public String getHash(String t) {
							return t;
						}
					});
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to create external map", e);
			return null;
		}
	}
	
	private void initReporting(String reportingIntervalSecondsConfig){
		if(reportingIntervalSecondsConfig != null){
			Integer reportingIntervalSeconds = CommonFunctions.parseInt(reportingIntervalSecondsConfig.trim(), null);
			if(reportingIntervalSeconds != null){
				reportingEnabled = true;
				reportEveryMs = reportingIntervalSeconds * 1000;
				lastReportedTime = System.currentTimeMillis();
			}else{
				logger.log(Level.WARNING, "Invalid reporting interval. Reporting disabled.");
			}
		}
	}
	
	@Override
	public boolean launch(String arguments){
		Map<String, String> argsMap = CommonFunctions.parseKeyValPairs(arguments);
		
		String inputFileArgument = argsMap.get("inputFile");
		String rotateArgument = argsMap.get("rotate");
		String waitForLogArgument = argsMap.get("waitForLog");
		
		if(CommonFunctions.isNullOrEmpty(inputFileArgument)){
			logger.log(Level.SEVERE, "NULL/Empty 'inputFile' argument: " + inputFileArgument);
			return false;
		}else{
			inputFileArgument = inputFileArgument.trim();
			File inputFile = null;
			try{
				inputFile = new File(inputFileArgument);
				if(!inputFile.exists()){
					logger.log(Level.SEVERE, "No file at path: " + inputFileArgument);
					return false;
				}
				if(!inputFile.isFile()){
					logger.log(Level.SEVERE, "Not a regular file at path: " + inputFileArgument);
					return false;
				}
			}catch(Exception e){
				logger.log(Level.SEVERE, "Failed to check if file exists: " + inputFileArgument, e);
				return false;
			}
			boolean rotate = false;
			if(rotateArgument != null){
				if(rotateArgument.equalsIgnoreCase("true")){
					rotate = true;
				}else if(rotateArgument.equalsIgnoreCase("false")){
					rotate = false;
				}else{
					logger.log(Level.SEVERE, "Invalid 'rotate' (only 'true'/'false') argument: " + rotateArgument);
					return false;
				}
			}
			if(waitForLogArgument != null){
				if(waitForLogArgument.equalsIgnoreCase("true")){
					waitForLog = true;
				}else if(waitForLogArgument.equalsIgnoreCase("false")){
					waitForLog = false;
				}else{
					logger.log(Level.SEVERE, "Invalid 'waitForLog' (only 'true'/'false') argument: " + waitForLogArgument);
					return false;
				}
			}
			LinkedList<String> inputFilePaths = new LinkedList<String>(); // ordered
			inputFilePaths.addLast(inputFile.getAbsolutePath());
			if(rotate){
				try{
					String inputFileParentPath = inputFile.getParentFile().getAbsolutePath();
					String inputFileName = inputFile.getName();
					int totalFilesCount = inputFile.getParentFile().list().length;
					for(int a = 1; a < totalFilesCount; a++){
						File file = new File(inputFileParentPath + File.separatorChar + inputFileName + "." + a);
						if(file.exists()){
							inputFilePaths.addLast(file.getAbsolutePath());
						}
					}
				}catch(Exception e){
					logger.log(Level.SEVERE, "Failed to gather all input files", e);
					return false;
				}
			}
			
			String schemaFilePath = null;
			Map<String, String> configMap = readDefaultConfigFile();
			if(configMap == null || configMap.isEmpty()){
				logger.log(Level.SEVERE, "NULL/Empty config map: " + configMap);
				return false;
			}else{
				schemaFilePath = configMap.get(CONFIG_KEY_SCHEMA);
				if(CommonFunctions.isNullOrEmpty(schemaFilePath)){
					logger.log(Level.SEVERE, "NULL/Empty '"+CONFIG_KEY_SCHEMA+"' in config file: "+schemaFilePath);
					return false;
				}else{
					schemaFilePath = schemaFilePath.trim();
					try{
						File schemaFile = new File(schemaFilePath);
						if(!schemaFile.exists()){
							logger.log(Level.SEVERE, "Schema file doesn't exist: " + schemaFilePath);
							return false;
						}
						if(!schemaFile.isFile()){
							logger.log(Level.SEVERE, "Schema path is not a regular file: " + schemaFilePath);
							return false;
						}
					}catch(Exception e){
						logger.log(Level.SEVERE, "Failed to check if schema file exists: " + schemaFilePath, e);
						return false;
					}
				}
			}
			
			try{
				boolean binaryFormat = false;
				if(inputFileArgument.endsWith(".json")){
					binaryFormat = false;
				}else{
					binaryFormat = true;
				}
				for(String inputFilePath : inputFilePaths){
					DataReader dataReader = null;
					if(binaryFormat){
						dataReader = new BinaryReader(inputFilePath, schemaFilePath);
					}else{
						dataReader = new JsonReader(inputFilePath, schemaFilePath);
					}
					dataReaders.addLast(dataReader);
				}
			}catch(Exception e){
				logger.log(Level.SEVERE, "Failed to build data reader", e);
				return false;
			}
			
			initReporting(configMap.get("reportingIntervalSeconds"));
			
			try{
				uuidToVertexMap = initCacheMap(configMap.get(CONFIG_KEY_CACHE_DATABASE_PARENT_PATH), 
						configMap.get(CONFIG_KEY_CACHE_DATABASE_NAME), configMap.get(CONFIG_KEY_CACHE_SIZE), 
						configMap.get(CONFIG_KEY_BLOOMFILTER_FALSE_PROBABILITY), 
						configMap.get(CONFIG_KEY_BLOOMFILTER_EXPECTED_ELEMENTS));
				if(uuidToVertexMap == null){
					logger.log(Level.SEVERE, "NULL external memory map");
					return false;
				}
			}catch(Exception e){
				logger.log(Level.SEVERE, "Failed to create external memory map", e);
				return false;
			}
			
			try{
				datumProcessorThread.start();
			}catch(Exception e){
				logger.log(Level.SEVERE, "Failed to start data processor thread", e);
				doCleanup();
				return false;
			}
			
			logger.log(Level.INFO, 
					"Arguments: rotate='"+rotate+"', waitForLog='"+waitForLog+"', inputFile='"+inputFileArgument+"'");
			logger.log(Level.INFO, "Input files: " + inputFilePaths);
			
			return true;
		}
	}
	
	private void printStats(){
		Runtime runtime = Runtime.getRuntime();
		long usedMemoryMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024*1024);   	
		long internalBufferSize = getBuffer().size();
		logger.log(Level.INFO, "Lines read: {0}, Internal buffer size: {1}, JVM memory in use: {2}MB", new Object[]{linesRead, internalBufferSize, usedMemoryMB});
	}
	
	private void handleUnitDependency(UnitDependency unitDependency, InstrumentationSource source){
		UUID unitUuid = unitDependency.getUnit();
		UUID dependentUnitUuid = unitDependency.getDependentUnit();		
		putEdge(dependentUnitUuid, unitUuid, null, "UnitDependency", source);
	}
	
	private void handleEvent(Event event, InstrumentationSource source){
		EventType type = event.getType();
		if(type != null){
			Map<String, String> edgeMap = new HashMap<String, String>();
			addAnnotationIfNotNull(edgeMap, "sequence", event.getSequence());
			addAnnotationIfNotNull(edgeMap, "threadId", event.getThreadId());
			addAnnotationIfNotNull(edgeMap, "timestampNanos", event.getTimestampNanos());
			addAnnotationIfNotNull(edgeMap, "location", event.getLocation());
			addAnnotationIfNotNull(edgeMap, "size", event.getSize());
			
			addAnnotationsIfNotNull(edgeMap, event.getProperties());
			
			UUID src1 = null, dst1 = null, src2 = null, dst2 = null, src3 = null, dst3 = null;
			
			String opm = null;
		
			switch(type){
				case EVENT_OTHER:
				{
					String operation = edgeMap.get(OPMConstants.EDGE_OPERATION);
					if(operation != null){
						switch(operation){
							case OPMConstants.OPERATION_TEE:
							case OPMConstants.OPERATION_SPLICE:
							{
								src1 = event.getSubject();
								dst1 = event.getPredicateObject();
								
								src2 = event.getPredicateObject2();
								dst2 = event.getSubject();
								
								src3 = event.getPredicateObject2();
								dst3 = event.getPredicateObject();
							}
							break;
							case OPMConstants.OPERATION_VMSPLICE:
							{
								src1 = event.getPredicateObject();
								dst1 = event.getSubject();
							}
							break;
							case OPMConstants.OPERATION_FINIT_MODULE:
							case OPMConstants.OPERATION_INIT_MODULE:
							{
								src1 = event.getSubject();
								dst1 = event.getPredicateObject();
							}
							break;
							default:
								logger.log(Level.WARNING, "Unexpected 'operation' in event: " + event);
								return;
						}
					}else{
						logger.log(Level.WARNING, "NULL 'operation' for event: " + event);
						return;
					}
				}
				break;
				case EVENT_OPEN:
				case EVENT_CLOSE:
					opm = edgeMap.get(OPMConstants.OPM);
					if(opm == null){
						logger.log(Level.WARNING, "Missing 'opm' for event: " + event);
					}else{
						switch(opm){
							case OPMConstants.USED:
							{
								src1 = event.getSubject();
								dst1 = event.getPredicateObject();
							}
							break;
							case OPMConstants.WAS_GENERATED_BY:
							{
								src1 = event.getPredicateObject();
								dst1 = event.getSubject();
							}
							break;
							default:
								logger.log(Level.SEVERE, "Unexpected 'opm' value for event: " + event);
								return;
						}
					}
					break;
				case EVENT_LOADLIBRARY:
				case EVENT_RECVMSG:
				case EVENT_RECVFROM:
				case EVENT_READ:
				case EVENT_ACCEPT:
				{
					src1 = event.getSubject();
					dst1 = event.getPredicateObject();
				}
				break;
				case EVENT_EXIT:
				case EVENT_UNIT:
				case EVENT_FORK:
				case EVENT_EXECUTE:
				case EVENT_CLONE:
				case EVENT_CHANGE_PRINCIPAL:
				{
					src1 = event.getPredicateObject();
					dst1 = event.getSubject();
				}
				break;
				case EVENT_CONNECT:
				case EVENT_CREATE_OBJECT:
				case EVENT_WRITE:
				case EVENT_MPROTECT:
				case EVENT_SENDTO:
				case EVENT_SENDMSG:
				case EVENT_UNLINK:
				case EVENT_MODIFY_FILE_ATTRIBUTES:
				case EVENT_TRUNCATE:
				{
					src1 = event.getPredicateObject();
					dst1 = event.getSubject();
				}
				break;
				case EVENT_LINK:
				case EVENT_RENAME:
				case EVENT_MMAP:
				case EVENT_UPDATE:
				{
					src1 = event.getSubject();
					dst1 = event.getPredicateObject();
					
					src2 = event.getPredicateObject2();
					dst2 = event.getSubject();
					
					src3 = event.getPredicateObject2();
					dst3 = event.getPredicateObject();
				}
				break;
				default: 
					logger.log(Level.WARNING, "Unhandled event type '"+type+"' for event: " + event);
					return;
			}
			
			if(src1 != null && dst1 != null){
				putEdge(src1, dst1, edgeMap, type, source);
			}
			if(src2 != null && dst2 != null){
				putEdge(src2, dst2, edgeMap, type, source);
			}
			if(src3 != null && dst3 != null){
				putEdge(src3, dst3, edgeMap, type, source);
			}
		}else{
			logger.log(Level.WARNING, "NULL event type for event: " + event);
		}
	}
	
	private void handleFileObject(FileObject fileObject, InstrumentationSource source){
		AbstractVertex vertex = new spade.vertex.cdm.Object();
		UUID uuid = fileObject.getUuid();
		AbstractObject baseObject = fileObject.getBaseObject();
		
		addAnnotationsIfNotNull(vertex, baseObject);
		
		putVertex(vertex, uuid, null, fileObject.getType(), source);
	}
	
	private void handleHost(Host host, InstrumentationSource source){
		AbstractVertex vertex = new spade.vertex.cdm.Object();
		UUID uuid = host.getUuid();
		
		addAnnotationIfNotNull(vertex, "hostName", host.getHostName());
		addAnnotationIfNotNull(vertex, "osDetails", host.getOsDetails());
		addAnnotationIfNotNull(vertex, "hostType", host.getHostType());
		
		List<HostIdentifier> hostIdentifiers = host.getHostIdentifiers();
		if(hostIdentifiers != null){
			for(HostIdentifier hostIdentifier : hostIdentifiers){
				if(hostIdentifier != null){
					addAnnotationIfNotNull(vertex, String.valueOf(hostIdentifier.getIdType()), hostIdentifier.getIdValue());
				}
			}
		}
		
		List<Interface> interfaces = host.getInterfaces();
		if(interfaces != null){
			int interfaceIndex = 0;
			for(Interface interfaze : interfaces){
				if(interfaze != null){
					addAnnotationIfNotNull(vertex, "name " + interfaceIndex, interfaze.getName());
					addAnnotationIfNotNull(vertex, "macAddress " + interfaceIndex, interfaze.getMacAddress());
					addAnnotationIfNotNull(vertex, "ipAddresses " + interfaceIndex, interfaze.getIpAddresses());
					interfaceIndex++;
				}
			}
		}
		putVertex(vertex, uuid, null, "Host", source);
	}
	
	private void handleIpcObject(IpcObject ipcObject, InstrumentationSource source){
		AbstractVertex vertex = new spade.vertex.cdm.Object();
		UUID uuid = ipcObject.getUuid();
		AbstractObject baseObject = ipcObject.getBaseObject();
		
		IpcObjectType type = ipcObject.getType();
		
		String fd0Key = null, fd1Key = null;
		
		switch(type){
			case IPC_OBJECT_PIPE_UNNAMED:
				fd0Key = "sourceFileDescriptor";
				fd1Key = "sinkFileDescriptor";
				break;
			case IPC_OBJECT_SOCKET_PAIR:
				fd0Key = "fd0";
				fd1Key = "fd1";
				break;
			default:
				logger.log(Level.WARNING, "Unexpected IpcObject type '"+type+"' for object: " + ipcObject);
				return;
		}
		
		addAnnotationIfNotNull(vertex, fd0Key, ipcObject.getFd1());
		addAnnotationIfNotNull(vertex, fd1Key, ipcObject.getFd2());
		
		addAnnotationsIfNotNull(vertex, baseObject);
		
		putVertex(vertex, uuid, null, type, source);
	}
	
	private void handleMemoryObject(MemoryObject memoryObject, InstrumentationSource source){
		AbstractVertex vertex = new spade.vertex.cdm.Object();
		UUID uuid = memoryObject.getUuid();
		AbstractObject baseObject = memoryObject.getBaseObject();
		
		addAnnotationIfNotNull(vertex, "memoryAddress", memoryObject.getMemoryAddress());
		addAnnotationIfNotNull(vertex, "size", memoryObject.getSize());
		
		addAnnotationsIfNotNull(vertex, baseObject);
		
		putVertex(vertex, uuid, null, "MemoryObject", source);
	}
	
	private void handleNetFlowObject(NetFlowObject netFlowObject, InstrumentationSource source){
		AbstractVertex vertex = new spade.vertex.cdm.Object();
		UUID uuid = netFlowObject.getUuid();
		AbstractObject baseObject = netFlowObject.getBaseObject();
		
		addAnnotationIfNotNull(vertex, "localAddress", netFlowObject.getLocalAddress());
		addAnnotationIfNotNull(vertex, "localPort", netFlowObject.getLocalPort());
		addAnnotationIfNotNull(vertex, "remoteAddress", netFlowObject.getRemoteAddress());
		addAnnotationIfNotNull(vertex, "remotePort", netFlowObject.getRemotePort());
		addAnnotationIfNotNull(vertex, "ipProtocol", netFlowObject.getIpProtocol());
		
		addAnnotationsIfNotNull(vertex, baseObject);
		
		putVertex(vertex, uuid, null, "NetFlowObject", source);
	}
	
	private void handleSrcSinkObject(SrcSinkObject srcSinkObject, InstrumentationSource source){
		AbstractVertex vertex = new spade.vertex.cdm.Object();
		UUID uuid = srcSinkObject.getUuid();
		AbstractObject baseObject = srcSinkObject.getBaseObject();
		
		addAnnotationIfNotNull(vertex, "fileDescriptor", srcSinkObject.getFileDescriptor());
		addAnnotationsIfNotNull(vertex, baseObject);
		
		putVertex(vertex, uuid, null, "SrcSinkObject", source);
	}
	
	private void handlePrincipal(Principal principal, InstrumentationSource source){
		AbstractVertex vertex = new spade.vertex.cdm.Principal();
		UUID uuid = principal.getUuid();
		
		List<CharSequence> gids = principal.getGroupIds();
		
		CharSequence gid = gids.size() > 0 ? gids.get(0) : null;
		CharSequence egid = gids.size() > 1 ? gids.get(1) : null;
		CharSequence sgid = gids.size() > 2 ? gids.get(2) : null;
		CharSequence fsgid = gids.size() > 3 ? gids.get(3) : null;
		
		addAnnotationIfNotNull(vertex, "userId", principal.getUserId());
		addAnnotationIfNotNull(vertex, "gid", gid);
		addAnnotationIfNotNull(vertex, "egid", egid);
		addAnnotationIfNotNull(vertex, "sgid", sgid);
		addAnnotationIfNotNull(vertex, "fsgid", fsgid);
		
		putVertex(vertex, uuid, principal.getProperties(), "Principal", source);
	}
	
	private void handleSubject(Subject subject, InstrumentationSource source){
		AbstractVertex vertex = new spade.vertex.cdm.Subject();
		UUID uuid = subject.getUuid();
		UUID principalUuid = subject.getLocalPrincipal();
		
		addAnnotationIfNotNull(vertex, "pid", subject.getCid());
		addAnnotationIfNotNull(vertex, "parentSubjectUuid", getUUIDAsString(subject.getParentSubject()));
		addAnnotationIfNotNull(vertex, "localPrincipal", getUUIDAsString(subject.getLocalPrincipal()));
		addAnnotationIfNotNull(vertex, "startTimestampNanos", subject.getStartTimestampNanos());
		addAnnotationIfNotNull(vertex, "unitId", subject.getUnitId());
		addAnnotationIfNotNull(vertex, "iteration", subject.getIteration());
		addAnnotationIfNotNull(vertex, "count", subject.getCount());
		addAnnotationIfNotNull(vertex, "cmdLine", subject.getCmdLine());
		
		putVertex(vertex, uuid, subject.getProperties(), subject.getType(), source);
		
		putEdge(uuid, principalUuid, null, null, source);
	}
	
	private void putVertex(AbstractVertex vertex, UUID uuid, 
			Map<CharSequence, CharSequence> properties, Object cdmType,
			InstrumentationSource source){
		String uuidString = getUUIDAsString(uuid);
		addCdmType(vertex, cdmType);
		addSource(vertex, source);
		addAnnotationIfNotNull(vertex, "uuid", uuidString);
		addAnnotationsIfNotNull(vertex, properties);
		uuidToVertexMap.put(uuidString, vertex);
		super.putVertex(vertex);
	}
	
	private void putEdge(UUID sourceUuid, UUID destinationUuid, Map<String, String> annotations,
			Object cdmType, InstrumentationSource source){
		if(sourceUuid != null && destinationUuid != null){
			String sourceUuidString = getUUIDAsString(sourceUuid);
			String destinationUuidString = getUUIDAsString(destinationUuid);
			if(sourceUuidString != null && destinationUuidString != null){
				AbstractVertex sourceVertex = uuidToVertexMap.get(sourceUuidString);
				AbstractVertex destinationVertex = uuidToVertexMap.get(destinationUuidString);
				if(sourceVertex != null && destinationVertex != null){
					SimpleEdge edge = new SimpleEdge(sourceVertex, destinationVertex);
					if(annotations != null){
						edge.addAnnotations(annotations);
					}
					addCdmType(edge, cdmType);
					addSource(edge, source);
					super.putEdge(edge);
				}
			}
		}
		
	}
	
	private void processDatum(TCCDMDatum tccdmdatum){
		if(reportingEnabled){
			linesRead++;
			long currentTime = System.currentTimeMillis();
			if((currentTime - lastReportedTime) >= reportEveryMs){
				printStats();
				lastReportedTime = currentTime;
			}
		}
		
		if(tccdmdatum != null){
			Object datum = tccdmdatum.getDatum();
			if(datum != null){
				RecordType recordType = tccdmdatum.getType();
				if(recordType != null){
					InstrumentationSource source = tccdmdatum.getSource();
					if(source == null){
						logger.log(Level.WARNING, "NULL instrumentation source in datum: " + tccdmdatum);
					}
					try{
						switch(recordType){
							case RECORD_EVENT: handleEvent((Event)datum, source); break;
							case RECORD_FILE_OBJECT: handleFileObject((FileObject)datum, source); break;
							case RECORD_HOST: handleHost((Host)datum, source); break;
							case RECORD_IPC_OBJECT: handleIpcObject((IpcObject)datum, source); break;
							case RECORD_MEMORY_OBJECT: handleMemoryObject((MemoryObject)datum, source); break;
							case RECORD_NET_FLOW_OBJECT: handleNetFlowObject((NetFlowObject)datum, source); break;
							case RECORD_PRINCIPAL: handlePrincipal((Principal)datum, source); break;
							case RECORD_SRC_SINK_OBJECT: handleSrcSinkObject((SrcSinkObject)datum, source); break;
							case RECORD_SUBJECT: handleSubject((Subject)datum, source); break;
							case RECORD_UNIT_DEPENDENCY: handleUnitDependency((UnitDependency)datum, source); break;
							
							// Unconvertables
							case RECORD_END_MARKER:
							case RECORD_TIME_MARKER:
							default: break;
						}
					}catch(ClassCastException cce){
						logger.log(Level.SEVERE, "Mismatched record type in datum: " + tccdmdatum, cce);
					}catch(Throwable t){
						logger.log(Level.WARNING, "Failed to process datum: " + tccdmdatum, t);
					}
				}else{
					logger.log(Level.WARNING, "NULL type in datum: " + tccdmdatum);
				}
			}else{
				logger.log(Level.WARNING, "NULL object in datum: " + tccdmdatum);
			}
		}else{
			logger.log(Level.WARNING, "NULL datum");
		}
	}
	
	private void addSource(AbstractVertex vertex, InstrumentationSource source){
		addSource(vertex.getAnnotations(), source);
	}
	
	private void addSource(AbstractEdge edge, InstrumentationSource source){
		addSource(edge.getAnnotations(), source);
	}
	
	private void addSource(Map<String, String> map, InstrumentationSource source){
		addAnnotationIfNotNull(map, "source", source);
	}
	
	private void addCdmType(AbstractVertex vertex, Object value){
		addCdmType(vertex.getAnnotations(), value);
	}
	
	private void addCdmType(AbstractEdge edge, Object value){
		addCdmType(edge.getAnnotations(), value);
	}
	
	private void addCdmType(Map<String, String> map, Object value){
		addAnnotationIfNotNull(map, KEY_CDM_TYPE, value);
	}
	
	private void addAnnotationIfNotNull(AbstractVertex vertex, String key, Object value){
		addAnnotationIfNotNull(vertex.getAnnotations(), key, value);
	}
	
	private void addAnnotationsIfNotNull(AbstractVertex vertex, AbstractObject object){
		if(object != null){
			addAnnotationIfNotNull(vertex, "epoch", object.getEpoch());
			addAnnotationIfNotNull(vertex, "permission", getPermissionSHORTAsString(object.getPermission()));
			addAnnotationsIfNotNull(vertex, object.getProperties());
		}
	}
	
	private void addAnnotationIfNotNull(Map<String, String> map, String key, Object value){
		if(value != null){
			map.put(key, value.toString());
		}
	}
	
	private void addAnnotationsIfNotNull(Map<String, String> map, Map<CharSequence, CharSequence> properties){
		if(properties != null){
			for(Map.Entry<CharSequence, CharSequence> entry : properties.entrySet()){
				addAnnotationIfNotNull(map, String.valueOf(entry.getKey()), entry.getValue());
			}
		}
	}
	
	
	private void addAnnotationsIfNotNull(AbstractVertex vertex, Map<CharSequence, CharSequence> properties){
		addAnnotationsIfNotNull(vertex.getAnnotations(), properties);
	}
	
	/**
	 * Returns null if null arguments
	 * 
	 * @param uuid
	 * @return null or encoded hex value
	 */
	private String getUUIDAsString(UUID uuid){
		if(uuid != null){
			return Hex.encodeHexString(uuid.bytes());
		}
		return null;
	}
	
	/**
	 * Return null if null arguments
	 * 
	 * @param permission
	 * @return null/Octal representation of permissions
	 */
	private String getPermissionSHORTAsString(SHORT permission){
		if(permission == null){
			return null;
		}else{
			ByteBuffer bb = ByteBuffer.allocate(2);
			bb.put(permission.bytes()[0]);
			bb.put(permission.bytes()[1]);
			int permissionShort = bb.getShort(0);
			return Integer.toOctalString(permissionShort);
		}
	}
	
	@Override
	public boolean shutdown(){
		shutdown = true;
		if(waitForLog){
			logger.log(Level.INFO, "Going to shutdown after all files read.");
		}else{
			logger.log(Level.INFO, "Going to shutdown right now.");
		}
		return true;
	}
	
	private synchronized void doCleanup(){
		if(uuidToVertexMap != null){
			CommonFunctions.closePrintSizeAndDeleteExternalMemoryMap(uuidMapId, uuidToVertexMap);
			uuidToVertexMap = null;
		}

		if(dataReaders != null){
			while(!dataReaders.isEmpty()){
				DataReader dataReader = dataReaders.removeFirst();
				if(dataReader != null){
					try{
						dataReader.close();
					}catch(Exception e){
						logger.log(Level.WARNING, "Failed to close data reader for file: " + 
								dataReader.getDataFilePath(), e);
					}
				}
			}
		}
	}	
}

interface DataReader{

	/**
	 * Must return null to indicate EOF
	 * 
	 * @return TCCDMDatum object
	 * @throws Exception
	 */
	public Object read() throws Exception;
	
	public void close() throws Exception;
	
	/**
	 * @return The data file being read
	 */
	public String getDataFilePath();
	
}

class JsonReader implements DataReader{
	
	private String filepath;
	private DatumReader<Object> datumReader;
	private Decoder decoder;
	
	public JsonReader(String dataFilepath, String schemaFilepath) throws Exception{
		this.filepath = dataFilepath;
		Parser parser = new Schema.Parser();
		Schema schema = parser.parse(new File(schemaFilepath));
		this.datumReader = new SpecificDatumReader<Object>(schema);
		this.decoder = DecoderFactory.get().jsonDecoder(schema, 
				new FileInputStream(new File(dataFilepath)));
	}
	
	public Object read() throws Exception{
		try{
			return datumReader.read(null, decoder);
		}catch(EOFException eof){
			return null;
		}catch(Exception e){
			throw e;
		}
	}
	
	public void close() throws Exception{
		// Nothing
	}
	
	public String getDataFilePath(){
		return filepath;
	}
	
}

class BinaryReader implements DataReader{
	
	private String filepath;
	private DataFileReader<Object> dataFileReader;
	
	public BinaryReader(String dataFilepath, String schemaFilepath) throws Exception{
		this.filepath = dataFilepath;
		Parser parser = new Schema.Parser();
		Schema schema = parser.parse(new File(schemaFilepath));
		DatumReader<Object> datumReader = new SpecificDatumReader<Object>(schema);
		this.dataFileReader = new DataFileReader<>(new File(dataFilepath), datumReader);
	}
	
	public Object read() throws Exception{
		if(dataFileReader.hasNext()){
			return dataFileReader.next();
		}else{
			return null;
		}
	}
	
	public void close() throws Exception{
		dataFileReader.close();
	}
	
	public String getDataFilePath(){
		return filepath;
	}
}
