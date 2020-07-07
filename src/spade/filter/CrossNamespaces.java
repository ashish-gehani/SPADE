/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2020 SRI International

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
package spade.filter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractVertex;
import spade.core.Settings;
import spade.utility.FileUtility;
import spade.utility.HelperFunctions;
import spade.utility.Result;
import spade.utility.map.external.ExternalMap;
import spade.utility.map.external.ExternalMapArgument;
import spade.utility.map.external.ExternalMapManager;

public class CrossNamespaces extends AbstractFilter{

	private static final Logger logger = Logger.getLogger(CrossNamespaces.class.getName());
	
	private static final String
		keyDebug = "debug",
		keyInMemory = "inMemory",
		keyArtifactToProcessMapId = "artifactToProcessMapId",
		keyArtifactAnnotationsToMatch = "artifactAnnotationsToMatch",
		keyProcessAnnotationsToMatch = "processAnnotationsToMatch",
		keyTypesOfArtifactToProcessEdge = "typesOfArtifactToProcessEdge",
		keyTypesOfProcessToArtifactEdge = "typesOfProcessToArtifactEdge";
	
	private final Set<String> typesOfArtifactToProcessEdge = new HashSet<String>();
	private final Set<String> typesOfProcessToArtifactEdge = new HashSet<String>();
	
	private final Set<String> artifactAnnotationsToMatch = new HashSet<String>();
	private final Set<String> processAnnotationsToMatch = new HashSet<String>();
	
	private boolean inMemoryMap;
	private boolean debug;
	
	// External map
	private String artifactToProcessMapId;
	private ExternalMap<TreeMap<String, String>, ArrayList<TreeMap<String, String>>> artifactToProcessMap;
	
	// In memory map
	private Map<TreeMap<String, String>, ArrayList<TreeMap<String, String>>> artifactToProcessMapInMemory;
	
	private long msgCounter = 0;
	
	@Override
	public boolean initialize(final String arguments){
		final Map<String, String> map = new HashMap<String, String>();
		final String configFilePath = Settings.getDefaultConfigFilePath(this.getClass());
		try{
			final Map<String, String> configMap = FileUtility.readConfigFileAsKeyValueMap(configFilePath, "=");
			map.putAll(configMap);
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to read config file: " + configFilePath, e);
			return false;
		}
		map.putAll(HelperFunctions.parseKeyValPairs(arguments));
		//
		final String csvValueArtifactAnnotationsToMatch = map.get(keyArtifactAnnotationsToMatch);
		if(HelperFunctions.isNullOrEmpty(csvValueArtifactAnnotationsToMatch)){
			logger.log(Level.SEVERE, "NULL/Empty value for '"+keyArtifactAnnotationsToMatch+"'");
			return false;
		}
		final String[] artifactAnnotationsToMatchTokens = csvValueArtifactAnnotationsToMatch.trim().split(",");
		for(final String artifactAnnotationsToMatchToken : artifactAnnotationsToMatchTokens){
			if(HelperFunctions.isNullOrEmpty(artifactAnnotationsToMatchToken)){
				logger.log(Level.SEVERE, "NULL/Empty value in '"+keyArtifactAnnotationsToMatch+"'");
				return false;
			}
			artifactAnnotationsToMatch.add(artifactAnnotationsToMatchToken.trim());
		}
		//
		final String csvValueProcessAnnotationsToMatch = map.get(keyProcessAnnotationsToMatch);
		if(HelperFunctions.isNullOrEmpty(csvValueProcessAnnotationsToMatch)){
			logger.log(Level.SEVERE, "NULL/Empty value for '"+keyProcessAnnotationsToMatch+"'");
			return false;
		}
		final String[] processAnnotationsToMatchTokens = csvValueProcessAnnotationsToMatch.trim().split(",");
		for(final String processAnnotationsToMatchToken : processAnnotationsToMatchTokens){
			if(HelperFunctions.isNullOrEmpty(processAnnotationsToMatchToken)){
				logger.log(Level.SEVERE, "NULL/Empty value in '"+keyProcessAnnotationsToMatch+"'");
				return false;
			}
			processAnnotationsToMatch.add(processAnnotationsToMatchToken.trim());
		}
		//
		final String csvValueTypesOfArtifactToProcessEdge = map.get(keyTypesOfArtifactToProcessEdge);
		if(HelperFunctions.isNullOrEmpty(csvValueTypesOfArtifactToProcessEdge)){
			logger.log(Level.SEVERE, "NULL/Empty value for '"+keyTypesOfArtifactToProcessEdge+"'");
			return false;
		}
		final String[] typesOfArtifactToProcessEdgeTokens = csvValueTypesOfArtifactToProcessEdge.trim().split(",");
		for(final String typesOfArtifactToProcessEdgeToken : typesOfArtifactToProcessEdgeTokens){
			if(HelperFunctions.isNullOrEmpty(typesOfArtifactToProcessEdgeToken)){
				logger.log(Level.SEVERE, "NULL/Empty value in '"+keyTypesOfArtifactToProcessEdge+"'");
				return false;
			}
			typesOfArtifactToProcessEdge.add(typesOfArtifactToProcessEdgeToken.trim());
		}
		//
		final String csvValueTypesOfProcessToArtifactEdge = map.get(keyTypesOfProcessToArtifactEdge);
		if(HelperFunctions.isNullOrEmpty(csvValueTypesOfProcessToArtifactEdge)){
			logger.log(Level.SEVERE, "NULL/Empty value for '"+keyTypesOfProcessToArtifactEdge+"'");
			return false;
		}
		final String[] typesOfProcessToArtifactEdgeTokens = csvValueTypesOfProcessToArtifactEdge.trim().split(",");
		for(final String typesOfProcessToArtifactEdgeToken : typesOfProcessToArtifactEdgeTokens){
			if(HelperFunctions.isNullOrEmpty(typesOfProcessToArtifactEdgeToken)){
				logger.log(Level.SEVERE, "NULL/Empty value in '"+keyTypesOfProcessToArtifactEdge+"'");
				return false;
			}
			typesOfProcessToArtifactEdge.add(typesOfProcessToArtifactEdgeToken.trim());
		}
		//
		final Set<String> commonInTypesOfArtifactToProcessAndTypesOfProcessToArtifact = new HashSet<String>();
		commonInTypesOfArtifactToProcessAndTypesOfProcessToArtifact.addAll(typesOfArtifactToProcessEdge);
		commonInTypesOfArtifactToProcessAndTypesOfProcessToArtifact.retainAll(typesOfProcessToArtifactEdge);
		if(commonInTypesOfArtifactToProcessAndTypesOfProcessToArtifact.size() > 0){
			logger.log(Level.SEVERE, "'"+keyTypesOfArtifactToProcessEdge+"' and '"+keyTypesOfProcessToArtifactEdge+"'"
					+ " cannot have common values. Common: " + commonInTypesOfArtifactToProcessAndTypesOfProcessToArtifact);
			return false;
		}
		//
		final Result<Boolean> inMemoryResult = HelperFunctions.parseBoolean(map.get(keyInMemory));
		if(inMemoryResult.error){
			logger.log(Level.SEVERE, "Failed to parse '"+keyInMemory+"'. " + inMemoryResult.toErrorString());
			return false;
		}
		inMemoryMap = inMemoryResult.result;
		//
		final Result<Boolean> debugResult = HelperFunctions.parseBoolean(map.get(keyDebug));
		if(debugResult.error){
			logger.log(Level.SEVERE, "Failed to parse '"+keyDebug+"'. " + debugResult.toErrorString());
			return false;
		}
		debug = debugResult.result;
		//
		ExternalMapArgument externalMapArgument = null;
		if(!inMemoryMap){
			artifactToProcessMapId = map.get(keyArtifactToProcessMapId);
			if(HelperFunctions.isNullOrEmpty(artifactToProcessMapId)){
				logger.log(Level.SEVERE, "NULL/Empty value for '"+keyArtifactToProcessMapId+"'");
				return false;
			}
			artifactToProcessMapId = artifactToProcessMapId.trim();
			final Result<ExternalMapArgument> artifactToProcessMapArgumentResult = 
					ExternalMapManager.parseArgumentFromMap(artifactToProcessMapId, map);
			if(artifactToProcessMapArgumentResult.error){
				logger.log(Level.SEVERE, "Invalid arguments for external map with id: " + artifactToProcessMapId + ". " 
						+ artifactToProcessMapArgumentResult.toErrorString());
				return false;
			}
			externalMapArgument = artifactToProcessMapArgumentResult.result;
			final Result<ExternalMap<TreeMap<String, String>, ArrayList<TreeMap<String, String>>>> artifactToProcessMapResult = 
					ExternalMapManager.create(artifactToProcessMapArgumentResult.result);
			if(artifactToProcessMapResult.error){
				logger.log(Level.SEVERE, "Failed to create external map with id: " + artifactToProcessMapId + ". " 
						+ artifactToProcessMapResult.toErrorString());
				return false;
			}
			artifactToProcessMap = artifactToProcessMapResult.result;
		}else{
			artifactToProcessMapInMemory = new HashMap<TreeMap<String, String>, ArrayList<TreeMap<String, String>>>();
		}
		//
		logger.log(Level.INFO, "Arguments. {0}={1}, {2}=[{3}], {4}=[{5}], {6}=[{7}], {8}=[{9}], {10}={11}",
				new Object[]{
						keyInMemory, inMemoryMap
						, keyArtifactAnnotationsToMatch, artifactAnnotationsToMatch
						, keyProcessAnnotationsToMatch, processAnnotationsToMatch
						, keyTypesOfArtifactToProcessEdge, typesOfArtifactToProcessEdge
						, keyTypesOfProcessToArtifactEdge, typesOfProcessToArtifactEdge
						, keyDebug, debug
				});
		if(!inMemoryMap){
			logger.log(Level.INFO, "Map: " + keyArtifactToProcessMapId + "=" + artifactToProcessMapId);
			logger.log(Level.INFO, "Map arguments: " + externalMapArgument);
		}
		return true;
	}
	
	@Override
	public boolean shutdown(){
		if(!inMemoryMap){
			if(artifactToProcessMap != null){
				artifactToProcessMap.close();
				artifactToProcessMap = null;
			}
		}
		return true;
	}
	
	@Override
	public void putVertex(final AbstractVertex vertex){
		putInNextFilter(vertex);
	}

	@Override
	public void putEdge(final AbstractEdge edge){
		if(edge != null && edge.getChildVertex() != null && edge.getParentVertex() != null){
			final String type = edge.type();
			if(type != null){
				boolean handle = false;
				AbstractVertex process = null, artifact = null;
				Boolean isRead = null;
				if(typesOfArtifactToProcessEdge.contains(type)){
					handle = true;
					isRead = false;
					process = edge.getParentVertex();
					artifact = edge.getChildVertex();
				}
				if(typesOfProcessToArtifactEdge.contains(type)){
					handle = true;
					isRead = true;
					process = edge.getChildVertex();
					artifact = edge.getParentVertex();
				}
				if(handle){
					checkUpdateOrLogCrossFlow(edge, process, artifact, isRead);
				}
			}
		}
		putInNextFilter(edge);
	}
	
	private final TreeMap<String, String> getAnnotationsFromVertex(final AbstractVertex vertex, 
			final Set<String> annotationsToGetSet){
		final TreeMap<String, String> finalMap = new TreeMap<String, String>();
		for(final String annotationToGet : annotationsToGetSet){
			final String annotationVaue = vertex.getAnnotation(annotationToGet);
			if(annotationVaue != null){
				finalMap.put(annotationToGet, annotationVaue);
			}
		}
		return finalMap;
	}
	
	private final ArrayList<TreeMap<String, String>> mapGetter(final TreeMap<String, String> key){
		if(inMemoryMap){
			return artifactToProcessMapInMemory.get(key);
		}else{
			return artifactToProcessMap.get(key);
		}
	}
	
	private final void mapPutter(final TreeMap<String, String> key, final ArrayList<TreeMap<String, String>> value){
		if(inMemoryMap){
			artifactToProcessMapInMemory.put(key, value);
		}else{
			artifactToProcessMap.put(key, value);
		}
	}
	
	private final void checkUpdateOrLogCrossFlow(final AbstractEdge edge, 
			final AbstractVertex process, final AbstractVertex artifact, boolean isRead){
		final TreeMap<String, String> artifactAnnotations = getAnnotationsFromVertex(artifact, artifactAnnotationsToMatch);
		if(artifactAnnotations.isEmpty()){
			if(debug){
				logger.log(Level.WARNING, "Ignoring vertex (artifact) containing none of the specified artifact annotations to match in edge: " + edge);
			}
		}else{
			final TreeMap<String, String> processAnnotations = getAnnotationsFromVertex(process, processAnnotationsToMatch);
			if(processAnnotations.isEmpty()){
				if(debug){
					logger.log(Level.WARNING, "Ignoring vertex (process) containing none of the specified process annotations to match in edge: " + edge);
				}
			}else{
				if(isRead){
					// Check if there was a write
					final ArrayList<TreeMap<String, String>> setOfProcessThatAlreadyWrote = mapGetter(artifactAnnotations);
					if(setOfProcessThatAlreadyWrote != null){
						boolean hadOtherWriters = false;
						final int indexOfReaderInWriter = setOfProcessThatAlreadyWrote.indexOf(processAnnotations);
						if(indexOfReaderInWriter == -1){ // not found
							if(setOfProcessThatAlreadyWrote.size() > 0){ // There were other writers
								hadOtherWriters = true;
							}
						}else{ // found
							if(setOfProcessThatAlreadyWrote.size() > 1){ // There were other writers (excluding this one since it was found)
								hadOtherWriters = true;
							}
						}
						if(hadOtherWriters){
							final String msgPrefix = "[" + this.getClass().getSimpleName() + " event# " + msgCounter++ + "]";
							logger.log(Level.INFO, msgPrefix + " Artifact: " + artifactAnnotations);
							logger.log(Level.INFO, msgPrefix + " Writers : " + setOfProcessThatAlreadyWrote);
							logger.log(Level.INFO, msgPrefix + " Reader  : " + processAnnotations);
						}
					}
				}else{
					ArrayList<TreeMap<String, String>> setOfProcessThatAlreadyWrote = mapGetter(artifactAnnotations);
					if(setOfProcessThatAlreadyWrote == null){
						setOfProcessThatAlreadyWrote = new ArrayList<TreeMap<String, String>>();
						mapPutter(artifactAnnotations, setOfProcessThatAlreadyWrote);
					}
					setOfProcessThatAlreadyWrote.remove(processAnnotations); // Remove if already present because we don't want duplicates
					setOfProcessThatAlreadyWrote.add(processAnnotations); // Add it as the last to maintain order for future
				}
			}
		}
	}
}