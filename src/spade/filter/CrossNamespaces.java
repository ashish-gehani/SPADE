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

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractVertex;
import spade.core.Settings;
import spade.utility.ArgumentFunctions;
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
		keyArtifactAnnotationsToReport = "artifactAnnotationsToReport",
		keyProcessAnnotationsToReport = "processAnnotationsToReport",
		keyTypesOfArtifactToProcessEdge = "typesOfArtifactToProcessEdge",
		keyTypesOfProcessToArtifactEdge = "typesOfProcessToArtifactEdge",
		keyOutput = "output",
		keyPretty = "pretty";
	
	private final Set<String> typesOfArtifactToProcessEdge = new HashSet<String>();
	private final Set<String> typesOfProcessToArtifactEdge = new HashSet<String>();
	
	private final Set<String> artifactAnnotationsToMatch = new HashSet<String>();
	private final Set<String> processAnnotationsToMatch = new HashSet<String>();

	private final Set<String> artifactAnnotationsToReport = new HashSet<String>();
	private final Set<String> processAnnotationsToReport = new HashSet<String>();

	private boolean inMemoryMap;
	private boolean debug;
	private boolean pretty;
	private String outputPath;
	private BufferedWriter outputWriter;

	private ExternalMap<Matched, ArrayList<Matched>> artifactToProcessMap;
	private Map<Matched, ArrayList<Matched>> artifactToProcessMapInMemory;

	private Map<Matched, HashSet<Matched>> matchedToReportMapInMemory;

	private long msgCounter = 0;

	private final List<String> allValuesMustBeNonEmpty(final List<String> values, final String key) throws Exception{
		final List<String> result = new ArrayList<String>();
		for(final String value : values){
			if(HelperFunctions.isNullOrEmpty(value)){
				throw new Exception("NULL/Empty value in '"+key+"'");
			}
			result.add(value.trim());
		}
		return result;
	}

	@Override
	public boolean initialize(final String arguments){
		final String configFilePath = Settings.getDefaultConfigFilePath(this.getClass());
		try{
			final Map<String, String> configMap = HelperFunctions.parseKeyValuePairsFrom(arguments, new String[]{configFilePath});

			final List<String> artifactAnnotationsToMatchArguments = 
					ArgumentFunctions.mustParseCommaSeparatedValues(keyArtifactAnnotationsToMatch, configMap);
			this.artifactAnnotationsToMatch.addAll(
					allValuesMustBeNonEmpty(artifactAnnotationsToMatchArguments, keyArtifactAnnotationsToMatch)
					);

			final List<String> processAnnotationsToMatchArguments = 
					ArgumentFunctions.mustParseCommaSeparatedValues(keyProcessAnnotationsToMatch, configMap);
			this.processAnnotationsToMatch.addAll(
					allValuesMustBeNonEmpty(processAnnotationsToMatchArguments, keyProcessAnnotationsToMatch)
					);

			if(!HelperFunctions.isNullOrEmpty(configMap.get(keyArtifactAnnotationsToReport))){
				final List<String> artifactAnnotationsToReportArguments = 
						ArgumentFunctions.mustParseCommaSeparatedValues(keyArtifactAnnotationsToReport, configMap);
				this.artifactAnnotationsToReport.addAll(
						allValuesMustBeNonEmpty(artifactAnnotationsToReportArguments, keyArtifactAnnotationsToReport)
						);
			}

			if(!HelperFunctions.isNullOrEmpty(configMap.get(keyProcessAnnotationsToReport))){
				final List<String> processAnnotationsToReportArguments = 
						ArgumentFunctions.mustParseCommaSeparatedValues(keyProcessAnnotationsToReport, configMap);
				this.processAnnotationsToReport.addAll(
						allValuesMustBeNonEmpty(processAnnotationsToReportArguments, keyProcessAnnotationsToReport)
						);
			}

			final List<String> typesOfArtifactToProcessEdgeArguments = 
					ArgumentFunctions.mustParseCommaSeparatedValues(keyTypesOfArtifactToProcessEdge, configMap);
			this.typesOfArtifactToProcessEdge.addAll(
					allValuesMustBeNonEmpty(typesOfArtifactToProcessEdgeArguments, keyTypesOfArtifactToProcessEdge)
					);

			final List<String> typesOfProcessToArtifactEdgeArguments = 
					ArgumentFunctions.mustParseCommaSeparatedValues(keyTypesOfProcessToArtifactEdge, configMap);
			this.typesOfProcessToArtifactEdge.addAll(
					allValuesMustBeNonEmpty(typesOfProcessToArtifactEdgeArguments, keyTypesOfProcessToArtifactEdge)
					);

			final Set<String> commonInTypesOfArtifactToProcessAndTypesOfProcessToArtifact = new HashSet<String>();
			commonInTypesOfArtifactToProcessAndTypesOfProcessToArtifact.addAll(typesOfArtifactToProcessEdge);
			commonInTypesOfArtifactToProcessAndTypesOfProcessToArtifact.retainAll(typesOfProcessToArtifactEdge);
			if(commonInTypesOfArtifactToProcessAndTypesOfProcessToArtifact.size() > 0){
				throw new Exception("'"+keyTypesOfArtifactToProcessEdge+"' and '"+keyTypesOfProcessToArtifactEdge+"'"
						+ " cannot have common values. Common: " + commonInTypesOfArtifactToProcessAndTypesOfProcessToArtifact);
			}

			inMemoryMap = ArgumentFunctions.mustParseBoolean(keyInMemory, configMap);
			debug = ArgumentFunctions.mustParseBoolean(keyDebug, configMap);
			outputPath = ArgumentFunctions.mustParseWritableFilePath(keyOutput, configMap);
			outputWriter = new BufferedWriter(new FileWriter(outputPath));
			pretty = ArgumentFunctions.mustParseBoolean(keyPretty, configMap);
			matchedToReportMapInMemory = new HashMap<Matched, HashSet<Matched>>();

			if(inMemoryMap){
				artifactToProcessMapInMemory = new HashMap<Matched, ArrayList<Matched>>();
			}else{
				final ExternalMapArgument artifactMapArgument = 
						ArgumentFunctions.mustParseExternalMapArgument(keyArtifactToProcessMapId, configMap);

				final Result<ExternalMap<Matched, ArrayList<Matched>>> artifactToProcessMapResult = 
						ExternalMapManager.create(artifactMapArgument);
				if(artifactToProcessMapResult.error){
					throw new Exception("Failed to create external map with id: " + artifactMapArgument.mapId + ". " 
							+ artifactToProcessMapResult.toErrorString());
				}

				this.artifactToProcessMap = artifactToProcessMapResult.result;
				logger.log(Level.INFO, "Map arguments: " + artifactMapArgument);
			}

			logger.log(Level.INFO, "Arguments: "
					+ "{0}={1}, {2}=[{3}], {4}=[{5}], {6}=[{7}], {8}=[{9}], {10}={11}, {12}={13}, {14}={15}, {16}=[{17}], {18}=[{19}]",
					new Object[]{
							keyInMemory, inMemoryMap
							, keyArtifactAnnotationsToMatch, artifactAnnotationsToMatch
							, keyProcessAnnotationsToMatch, processAnnotationsToMatch
							, keyTypesOfArtifactToProcessEdge, typesOfArtifactToProcessEdge
							, keyTypesOfProcessToArtifactEdge, typesOfProcessToArtifactEdge
							, keyDebug, debug
							, keyOutput, outputPath
							, keyPretty, pretty
							, keyArtifactAnnotationsToReport, artifactAnnotationsToReport
							, keyProcessAnnotationsToReport, processAnnotationsToReport
					});
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to read config file: " + configFilePath, e);
			return false;
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
		if(outputWriter != null){
			try{
				outputWriter.close();
			}catch(Exception e){
				logger.log(Level.WARNING, "Failed to close file. Buffered data at the tail might be lost", e);
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
			final String annotationValue;
			if(annotationToGet.equals(AbstractVertex.hashKey)){
				annotationValue = vertex.bigHashCode();
			}else{
				annotationValue = vertex.getAnnotation(annotationToGet);
			}
			if(annotationValue != null){
				finalMap.put(annotationToGet, annotationValue);
			}
		}
		return finalMap;
	}
	
	private final ArrayList<Matched> mapGetter(final Matched key){
		if(inMemoryMap){
			return artifactToProcessMapInMemory.get(key);
		}else{
			return artifactToProcessMap.get(key);
		}
	}
	
	private final void mapPutter(final Matched key, final ArrayList<Matched> value){
		if(inMemoryMap){
			artifactToProcessMapInMemory.put(key, value);
		}else{
			artifactToProcessMap.put(key, value);
		}
	}

	private final JSONObject createJSONObjectFromMap(final Map<String, String> map) throws Exception{
		final JSONObject object = new JSONObject();
		for(final String key : map.keySet()){
			final String value = map.get(key);
			if(value != null){
				object.put(key, value);
			}
		}
		return object;
	}

	private final JSONObject createJSONEvent(final long eventId, final Matched artifactAnnotations,
			final ArrayList<Matched> writerProcessesAnnotations,
			final Map<String, String> readerProcessAnnotations,
			final Map<String, String> readEdgeAnnotations) throws Exception{
		final JSONObject eventObject = new JSONObject();
		eventObject.put("cross-namespace-event-id", String.valueOf(eventId));
		eventObject.put("read-edge", createJSONObjectFromMap(readEdgeAnnotations));
		eventObject.put("artifact", createJSONObjectFromMap(artifactAnnotations.annotations));

		final JSONArray artifactsArray = new JSONArray();
		final HashSet<Matched> artifactAnnotationsToReportSet = matchedToReportMapInMemory.get(artifactAnnotations);
		if(artifactAnnotationsToReportSet != null){
			for(final Matched artifactAnnotationsToReport : artifactAnnotationsToReportSet){
				final TreeMap<String, String> finalMap = new TreeMap<>(artifactAnnotations.annotations);
				finalMap.putAll(artifactAnnotationsToReport.annotations);
				artifactsArray.put(createJSONObjectFromMap(finalMap));
			}
		}
		eventObject.put("artifacts", artifactsArray);

		eventObject.put("reader", createJSONObjectFromMap(readerProcessAnnotations));
		final JSONArray writerArray = new JSONArray();
		for(final Matched writerProcessAnnotations : writerProcessesAnnotations){
			final HashSet<Matched> writerProcessAnnotationsToReportSet = matchedToReportMapInMemory.get(writerProcessAnnotations);
			if(writerProcessAnnotationsToReportSet == null){
				writerArray.put(createJSONObjectFromMap(writerProcessAnnotations.annotations));
			}else{
				for(final Matched writerProcessAnnotationsToReport : writerProcessAnnotationsToReportSet){
					final TreeMap<String, String> finalMap = new TreeMap<>(writerProcessAnnotations.annotations);
					finalMap.putAll(writerProcessAnnotationsToReport.annotations);
					writerArray.put(createJSONObjectFromMap(finalMap));
				}
			}
		}
		eventObject.put("writers", writerArray);
		return eventObject;
	}

	private final void writeJSONEvent(final JSONObject eventObject) throws Exception{
		final String eventString;
		if(pretty){
			eventString = eventObject.toString(2);
		}else{
			eventString = eventObject.toString();
		}
		this.outputWriter.write(eventString + "\n");
	}

	private final void outputEvent(final long eventId, final Matched artifactAnnotations,
			final ArrayList<Matched> writerProcessesAnnotations,
			final Map<String, String> readerProcessAnnotations, final Map<String, String> readEdgeAnnotations){
		final JSONObject eventObject;
		try{
			eventObject = createJSONEvent(eventId, 
					artifactAnnotations, writerProcessesAnnotations, readerProcessAnnotations, readEdgeAnnotations);
		}catch(Exception e){
			logger.log(Level.WARNING, "Failed to create event as JSON object", e);
			return;
		}
		try{
			writeJSONEvent(eventObject);
		}catch(Exception e){
			logger.log(Level.WARNING, "Failed to write event", e);
			return;
		}
	}

	private final void checkUpdateOrLogCrossFlow(final AbstractEdge edge, 
			final AbstractVertex process, final AbstractVertex artifact, boolean isRead){
		final Matched artifactAnnotations = new Matched(getAnnotationsFromVertex(artifact, artifactAnnotationsToMatch));
		if(artifactAnnotations.isEmpty()){
			if(debug){
				logger.log(Level.WARNING, "Ignoring vertex (artifact) containing none of the specified artifact annotations to match in edge: " + edge);
			}
			return;
		}
		final Matched processAnnotations = new Matched(getAnnotationsFromVertex(process, processAnnotationsToMatch));
		if(processAnnotations.isEmpty()){
			if(debug){
				logger.log(Level.WARNING, "Ignoring vertex (process) containing none of the specified process annotations to match in edge: " + edge);
			}
			return;
		}

		final Matched artifactAnnotationsToReportValue = new Matched(getAnnotationsFromVertex(artifact, this.artifactAnnotationsToReport));
		if(!artifactAnnotationsToReportValue.isEmpty()){
			HashSet<Matched> existingSet = matchedToReportMapInMemory.get(artifactAnnotations);
			if(existingSet == null){
				existingSet = new HashSet<>();
				matchedToReportMapInMemory.put(artifactAnnotations, existingSet);
			}
			existingSet.add(artifactAnnotationsToReportValue);
		}

		if(isRead){
			// Check if there was a write
			final ArrayList<Matched> setOfProcessThatAlreadyWrote = mapGetter(artifactAnnotations);
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
					outputEvent(msgCounter++, artifactAnnotations, 
							setOfProcessThatAlreadyWrote, process.getCopyOfAnnotations(), edge.getCopyOfAnnotations());
				}
			}
		}else{
			final Matched processAnnotationsToReportValue = new Matched(getAnnotationsFromVertex(process, this.processAnnotationsToReport));
			if(!processAnnotationsToReportValue.isEmpty()){
				HashSet<Matched> existingSet = matchedToReportMapInMemory.get(processAnnotations);
				if(existingSet == null){
					existingSet = new HashSet<>();
					matchedToReportMapInMemory.put(processAnnotations, existingSet);
				}
				existingSet.add(processAnnotationsToReportValue);
			}

			ArrayList<Matched> setOfProcessThatAlreadyWrote = mapGetter(artifactAnnotations);
			if(setOfProcessThatAlreadyWrote == null){
				setOfProcessThatAlreadyWrote = new ArrayList<Matched>();
				mapPutter(artifactAnnotations, setOfProcessThatAlreadyWrote);
			}
			setOfProcessThatAlreadyWrote.remove(processAnnotations); // Remove if already present because we don't want duplicates
			setOfProcessThatAlreadyWrote.add(processAnnotations); // Add it as the last to maintain order for future
		}
	}

	private static class Matched implements Serializable{
		private static final long serialVersionUID = 6218289689183807202L;
		private final TreeMap<String, String> annotations = new TreeMap<>();
		private Matched(final TreeMap<String, String> annotations){
			this.annotations.putAll(annotations);
		}
		private boolean isEmpty(){
			return this.annotations.isEmpty();
		}
		@Override
		public int hashCode(){
			final int prime = 31;
			int result = 1;
			result = prime * result + ((annotations == null) ? 0 : annotations.hashCode());
			return result;
		}
		@Override
		public boolean equals(Object obj){
			if(this == obj)
				return true;
			if(obj == null)
				return false;
			if(getClass() != obj.getClass())
				return false;
			Matched other = (Matched)obj;
			if(annotations == null){
				if(other.annotations != null)
					return false;
			}else if(!annotations.equals(other.annotations))
				return false;
			return true;
		}
		@Override
		public String toString(){
			return "Matched [annotations=" + annotations + "]";
		}
	}
}