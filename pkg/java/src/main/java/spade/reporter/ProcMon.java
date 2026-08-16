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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractReporter;
import spade.core.Settings;
import spade.edge.opm.Used;
import spade.edge.opm.WasControlledBy;
import spade.edge.opm.WasGeneratedBy;
import spade.edge.opm.WasTriggeredBy;
import spade.reporter.procmon.Event;
import spade.reporter.procmon.EventReader;
import spade.reporter.procmon.ProvenanceModel;
import spade.reporter.procmon.SystemConstant;
import spade.utility.ArgumentFunctions;
import spade.utility.HelperFunctions;
import spade.vertex.opm.Agent;
import spade.vertex.opm.Artifact;
import spade.vertex.opm.Process;

/**
 * @author dawood
 */
public class ProcMon extends AbstractReporter{

	private static final Logger logger = Logger.getLogger(ProcMon.class.getName());
	private static final String
		keyInput = "input",
		keyVersions = "versions",
		keyWaitForLog = "waitForLog";

	private boolean versions;
	private boolean waitForLog;
	private String inputPath;
    private EventReader eventReader;

    private Map<String, Process> processMap = new HashMap<String, Process>();
    private Map<String, Set<String>> threadMap = new HashMap<String,Set<String>>();
    private Map<String, Integer> artifactVersions = new HashMap<String, Integer>();
    private Set<String> loadedImages = new HashSet<String>();
    private Set<String> networkConnections = new HashSet<String>();
    
    private volatile boolean shutdown = false;

    @Override
	public boolean launch(String arguments){
		try{
			final Map<String, String> configMap = HelperFunctions.parseKeyValuePairsFrom(arguments,
					new String[]{Settings.getDefaultConfigFilePath(this.getClass())});
			final String inputPath = ArgumentFunctions.mustParseReadableFilePath(keyInput, configMap);
			final boolean versions = ArgumentFunctions.mustParseBoolean(keyVersions, configMap);
			final boolean waitForLog = ArgumentFunctions.mustParseBoolean(keyWaitForLog, configMap);

			return launch(inputPath, versions, waitForLog);
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to read/parse arguments and configurations", e);
			return false;
		}

    }

	public boolean launch(final String inputPath, final boolean versions, final boolean waitForLog) throws Exception{
		this.inputPath = inputPath;
		this.versions = versions;
		this.waitForLog = waitForLog;

		try{
			eventReader = EventReader.createReader(this.inputPath);
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to create ProcMon CSV log reader", e);
			return false;
		}

		final Runnable eventProcessor = new Runnable(){
			public void run(){
				try{
					Event event;
					while(!shutdown){
						event = eventReader.read();
						if(event == null){
							break;
						}
						handleEvent(event);
					}
				}catch(Exception e){
					logger.log(Level.SEVERE, "Failed to read/process event", e);
				}finally{
					closeEventReader();
				}
				logger.log(Level.INFO, "Finished processing file: '" + inputPath + "'");
				shutdown = true;
			}
		};
		try{
			new Thread(eventProcessor, "ProcMon-Thread").start();
			return true;
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to create ProcMon CSV log reader", e);
			closeEventReader();
			return false;
		}
    }

    private void closeEventReader(){
    	if(eventReader != null){
			try{
				eventReader.close();
			}catch(Exception sube){
				
			}
		}
    }

	@Override
	public boolean shutdown(){
		if(waitForLog){
			while(!shutdown){
				HelperFunctions.sleepSafe(1000);
			}
		}
		shutdown = true;
		return true;
	}

	private void handleEvent(final Event event){
		if(!event.isSuccessful()){
			return;
		}

		handleProcess(event);

		final String eventClass = event.getEventClass();
		final String category = event.getCategory();
		final String operation = event.getOperation();

		if(eventClass == null){
			logger.log(Level.WARNING, "No event class in event: " + String.valueOf(event));
			return;
		}

		if(category == null){
			logger.log(Level.WARNING, "No category in event: " + String.valueOf(event));
			return;
		}

		if(operation == null){
			logger.log(Level.WARNING, "No operation in event: " + String.valueOf(event));
			return;
		}

		switch(category){
			case SystemConstant.CATEGORY_READ:
			case SystemConstant.CATEGORY_READ_METADATA:{
				switch(eventClass){
					case SystemConstant.EVENT_CLASS_REGISTRY:
					case SystemConstant.EVENT_CLASS_FILE_SYSTEM:{
						readArtifact(event);
						return;
					}
					default: break;
				}
			}
			break;
			case SystemConstant.CATEGORY_WRITE:
			case SystemConstant.CATEGORY_WRITE_METADATA:{
				switch(eventClass){
					case SystemConstant.EVENT_CLASS_REGISTRY:
					case SystemConstant.EVENT_CLASS_FILE_SYSTEM:{
						writeArtifact(event);
						return;
					}
					default: break;
			}
			}
			break;
			default: break;
		}

		switch(operation){
			case SystemConstant.OPERATION_LOAD_IMAGE:{
				loadImage(event);
				return;
			}
			case SystemConstant.OPERATION_TCP_SEND:
			case SystemConstant.OPERATION_UDP_SEND:
			case SystemConstant.OPERATION_TCP_CONNECT:
			case SystemConstant.OPERATION_TCP_RECONNECT:{
				networkSend(event);
				return;
			}
			case SystemConstant.OPERATION_TCP_RECEIVE:
			case SystemConstant.OPERATION_UDP_RECEIVE:{
				networkReceive(event);
				return;
			}
			default: break;
		}

	}

	private void handleProcess(final Event event){
		final String pid = event.getPid();
		if(!processMap.containsKey(pid)){
			createProcess(event);
			if(event.hasTid()){
				final String tid = event.getTid();
				final Set<String> threads = new HashSet<String>();
				threads.add(tid);
				threadMap.put(pid, threads);
			}
		}
		if(event.hasTid()){// to allow to count how many thread it launched, graph no longer acyclic
			final String tid = event.getTid();
			if(processMap.containsKey(pid) && (!threadMap.get(pid).contains(tid))){
				createWasTriggeredBy(event);
				threadMap.get(pid).add(tid);
			}
		}
	}

	private void createProcess(final Event event){
		final String pid = event.getPid();
		final String ppid = event.getParentPid();

		final Process processVertex = ProvenanceModel.createProcessVertex(event);
		putVertex(processVertex);
		processMap.put(pid, processVertex);

		final Agent agentVertex = ProvenanceModel.createAgentVertex(event);
		putVertex(agentVertex);

		final WasControlledBy wasControlledBy = ProvenanceModel.createWasControlledByEdge(event, processVertex, agentVertex);
		putEdge(wasControlledBy);

		if(processMap.containsKey(ppid)){
			final Process parentProcessVertex = processMap.get(ppid);
			final WasTriggeredBy wasTriggeredBy = ProvenanceModel.createWasTriggeredByEdge(event, processVertex, parentProcessVertex);
			putEdge(wasTriggeredBy);
		}
	}

	private void createWasTriggeredBy(final Event event){
    	final String pid = event.getPid();
    	final Process processVertex = processMap.get(pid);
    	final Process parentProcessVertex = processMap.get(pid);
    	final WasTriggeredBy wasTriggeredBy = ProvenanceModel.createWasTriggeredByEdge(event, processVertex, parentProcessVertex);
    	putEdge(wasTriggeredBy);
    }

	private int getPathVersion(final String path, final boolean increment){
		int version;
		if(artifactVersions.get(path) != null){
			version = artifactVersions.get(path);
			if(increment){
				version++;
				artifactVersions.put(path, version);
			}
		}else{
			version = 0;
			artifactVersions.put(path, version);
			// Do no increment so that all start with 0
		}
		return version;
	}

	private boolean putPathVertex(final String path){
		return !artifactVersions.containsKey(path);
	}

	private void readArtifact(final Event event){
		final String pid = event.getPid();
		final String path = event.getPath();

		final boolean put = putPathVertex(path);
		final int version = getPathVersion(path, false);

		final Artifact artifact = ProvenanceModel.createPathArtifact(event);
		if(versions){
			ProvenanceModel.addVersionToArtifact(artifact, version);
		}

		if(put){
			putVertex(artifact);
		}

		final Process processVertex = processMap.get(pid);
		final Used used = ProvenanceModel.createPathReadEdge(event, processVertex, artifact);
		putEdge(used);
	}

	private void writeArtifact(final Event event){
		final String pid = event.getPid();
		final String path = event.getPath();

		final boolean put = putPathVertex(path);
		final int version = getPathVersion(path, true);

		final Artifact artifact = ProvenanceModel.createPathArtifact(event);
		if(versions){
			ProvenanceModel.addVersionToArtifact(artifact, version);
		}

		if(put){
			putVertex(artifact);
		}

		final Process processVertex = processMap.get(pid);
		final WasGeneratedBy wasGeneratedBy = ProvenanceModel.createPathWriteEdge(event, artifact, processVertex);
		putEdge(wasGeneratedBy);
	}

	private void loadImage(final Event event){
		final String pid = event.getPid();
		final String path = event.getPath();

		final Artifact image = ProvenanceModel.createImageArtifact(event);
		if(!loadedImages.contains(path)){
			loadedImages.add(path);
			putVertex(image);
		}

		final Process process = processMap.get(pid);
		final Used used = ProvenanceModel.createImageLoadEdge(event, process, image);
		putEdge(used);
	}

	private void networkSend(final Event event){
		final String pid = event.getPid();
		final String path = event.getPath();
		final Artifact network = ProvenanceModel.createNetworkArtifact(path);

		if(!networkConnections.contains(path)){
			networkConnections.add(path);
			putVertex(network);
		}

		final Process process = processMap.get(pid);
		final WasGeneratedBy wasGeneratedBy = ProvenanceModel.createNetworkWriteEdge(event, network, process);
		putEdge(wasGeneratedBy);
	}

	private void networkReceive(final Event event){
		final String pid = event.getPid();
		final String path = event.getPath();
		final Artifact network = ProvenanceModel.createNetworkArtifact(path);

		if(!networkConnections.contains(path)){
			networkConnections.add(path);
			putVertex(network);
		}

		final Process process = processMap.get(pid);
		final Used used = ProvenanceModel.createNetworkReadEdge(event, process, network);
		putEdge(used);
	}

}
