/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2021 SRI International

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
package spade.filter.crossnamespaces;

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

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.filter.CrossNamespaces;
import spade.utility.ArgumentFunctions;
import spade.utility.HelperFunctions;
import spade.utility.Result;
import spade.utility.map.external.ExternalMap;
import spade.utility.map.external.ExternalMapArgument;
import spade.utility.map.external.ExternalMapManager;

public class CrossMatcher{

	private static final Logger logger = Logger.getLogger(CrossMatcher.class.getName());

	public static final String
		keyDebug = "debug",
		keyInMemory = "inMemory",
		keyArtifactToProcessMapId = "artifactToProcessMapId",
		keyArtifactAnnotationsToMatch = "artifactAnnotationsToMatch",
		keyProcessAnnotationsToMatch = "processAnnotationsToMatch",
		keyArtifactAnnotationsToReport = "artifactAnnotationsToReport",
		keyProcessAnnotationsToReport = "processAnnotationsToReport",
		keyTypesOfArtifactToProcessEdge = "typesOfArtifactToProcessEdge",
		keyTypesOfProcessToArtifactEdge = "typesOfProcessToArtifactEdge";
	
	private final Set<String> artifactAnnotationsToMatch = new HashSet<String>();
	private final Set<String> processAnnotationsToMatch = new HashSet<String>();
	private final Set<String> artifactAnnotationsToReport = new HashSet<String>();
	private final Set<String> processAnnotationsToReport = new HashSet<String>();

	private final Set<String> typesOfArtifactToProcessEdge = new HashSet<String>();
	private final Set<String> typesOfProcessToArtifactEdge = new HashSet<String>();
	
	private CrossNamespaces filter;
	private boolean debug;
	private boolean inMemoryMap;
	private ExternalMap<Matched, MatchedState> artifactToProcessMap;
	private Map<Matched, MatchedState> artifactToProcessMapInMemory;

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

	public final void initialize(final CrossNamespaces filter, final Map<String, String> configMap) throws Exception{
		this.filter = filter;
		debug = ArgumentFunctions.mustParseBoolean(keyDebug, configMap);

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

		inMemoryMap = ArgumentFunctions.mustParseBoolean(keyInMemory, configMap);
		
		if(inMemoryMap){
			artifactToProcessMapInMemory = new HashMap<Matched, MatchedState>();
		}else{
			final ExternalMapArgument artifactMapArgument = 
					ArgumentFunctions.mustParseExternalMapArgument(keyArtifactToProcessMapId, configMap);

			final Result<ExternalMap<Matched, MatchedState>> artifactToProcessMapResult = 
					ExternalMapManager.create(artifactMapArgument);
			if(artifactToProcessMapResult.error){
				throw new Exception("Failed to create external map with id: " + artifactMapArgument.mapId + ". " 
						+ artifactToProcessMapResult.toErrorString());
			}

			this.artifactToProcessMap = artifactToProcessMapResult.result;
			logger.log(Level.INFO, "External map argument:" + artifactMapArgument.toString());
		}

		logger.log(Level.INFO, "Arguments: "
				+ "{0}={1}, {2}=[{3}], {4}=[{5}], {6}=[{7}], {8}=[{9}], {10}=[{11}], {12}=[{13}], {14}={15}",
				new Object[]{
						keyInMemory, inMemoryMap
						, keyArtifactAnnotationsToMatch, artifactAnnotationsToMatch
						, keyProcessAnnotationsToMatch, processAnnotationsToMatch
						, keyTypesOfArtifactToProcessEdge, typesOfArtifactToProcessEdge
						, keyTypesOfProcessToArtifactEdge, typesOfProcessToArtifactEdge
						, keyArtifactAnnotationsToReport, artifactAnnotationsToReport
						, keyProcessAnnotationsToReport, processAnnotationsToReport
						, keyDebug, debug
				});
	}

	public final void shutdown(){
		if(artifactToProcessMap != null){
			artifactToProcessMap.close();
			artifactToProcessMap = null;
		}
	}

	private final MatchedState mapGetter(final Matched key){
		if(inMemoryMap){
			return artifactToProcessMapInMemory.get(key);
		}else{
			return artifactToProcessMap.get(key);
		}
	}
	
	private final void mapPutter(final Matched key, final MatchedState value){
		if(inMemoryMap){
			artifactToProcessMapInMemory.put(key, value);
		}else{
			artifactToProcessMap.put(key, value);
		}
	}

	private final Matched getMatchedFor(final AbstractVertex vertex, final Set<String> annotationsToGetSet){
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
		return new Matched(finalMap);
	}

	public final void check(final AbstractEdge edge){
		if(edge != null && edge.getChildVertex() != null && edge.getParentVertex() != null){
			final String type = edge.type();
			if(type != null){
				if(typesOfArtifactToProcessEdge.contains(type)){
					final AbstractVertex process = edge.getParentVertex();
					final AbstractVertex artifact = edge.getChildVertex();
					checkWrite(edge, process, artifact);
				}
				if(typesOfProcessToArtifactEdge.contains(type)){
					final AbstractVertex process = edge.getChildVertex();
					final AbstractVertex artifact = edge.getParentVertex();
					checkRead(edge, process, artifact);
				}
			}
		}
	}

	private final void checkRead(final AbstractEdge readEdge, final AbstractVertex readerProcessVertex,
			final AbstractVertex artifactVertex){
		final Matched artifactMatched = getMatchedFor(artifactVertex, artifactAnnotationsToMatch);
		if(artifactMatched.isEmpty()){
			if(debug){
				logger.log(Level.WARNING, 
						"Ignoring vertex (artifact) containing none of the specified artifact annotations to match in edge: " + readEdge);
			}
			return;
		}
		final Matched readerProcessMatched = getMatchedFor(readerProcessVertex, processAnnotationsToMatch);
		if(readerProcessMatched.isEmpty()){
			if(debug){
				logger.log(Level.WARNING, 
						"Ignoring vertex (process) containing none of the specified process annotations to match in edge: " + readEdge);
			}
			return;
		}

		final MatchedState state = mapGetter(artifactMatched);
		if(state == null){
			return;
		}

		final HashMap<Matched, HashSet<Matched>> otherWriters = state.getWritersExceptFor(readerProcessMatched);
		if(otherWriters.isEmpty()){
			return;
		}

		final Matched artifactReport = getMatchedFor(artifactVertex, this.artifactAnnotationsToReport);
		if(!artifactReport.isEmpty()){
			state.artifactAnnotationsToReport.add(artifactReport);
			mapPutter(artifactMatched, state);
		}

		final long eventId = msgCounter++;
		final TreeMap<String, String> matchedArtifactAnnotations = new TreeMap<>(artifactMatched.annotations);
		final HashSet<TreeMap<String, String>> completeArtifactAnnotationsSet = new HashSet<>();
		for(final Matched reportTemp : state.artifactAnnotationsToReport){
			final TreeMap<String, String> matchedAndReportArtifact = new TreeMap<String, String>(reportTemp.annotations);
			matchedAndReportArtifact.putAll(artifactMatched.annotations);
			completeArtifactAnnotationsSet.add(matchedAndReportArtifact);
		}
		final HashSet<TreeMap<String, String>> completeOtherWriters = new HashSet<>();
		for(final Map.Entry<Matched, HashSet<Matched>> entry : otherWriters.entrySet()){
			for(final Matched tmp : entry.getValue()){
				final TreeMap<String, String> matchedAndReportOtherWriter = new TreeMap<String, String>(tmp.annotations);
				matchedAndReportOtherWriter.putAll(entry.getKey().annotations);
				completeOtherWriters.add(matchedAndReportOtherWriter);
			}
		}
		filter.outputEvent(eventId,
				matchedArtifactAnnotations, 
				completeArtifactAnnotationsSet,
				completeOtherWriters,
				readerProcessVertex, 
				readEdge);
	}

	private final void checkWrite(final AbstractEdge writeEdge, final AbstractVertex writerProcessVertex,
			final AbstractVertex artifactVertex){
		final Matched artifactMatched = getMatchedFor(artifactVertex, artifactAnnotationsToMatch);
		if(artifactMatched.isEmpty()){
			if(debug){
				logger.log(Level.WARNING, 
						"Ignoring vertex (artifact) containing none of the specified artifact annotations to match in edge: " + writeEdge);
			}
			return;
		}
		final Matched writerProcessMatched = getMatchedFor(writerProcessVertex, processAnnotationsToMatch);
		if(writerProcessMatched.isEmpty()){
			if(debug){
				logger.log(Level.WARNING, 
						"Ignoring vertex (process) containing none of the specified process annotations to match in edge: " + writeEdge);
			}
			return;
		}

		boolean put = false;
		
		MatchedState state = mapGetter(artifactMatched);
		if(state == null){
			state = new MatchedState();
			put = true;
		}

		final Matched artifactReport = getMatchedFor(artifactVertex, this.artifactAnnotationsToReport);
		if(!artifactReport.isEmpty()){
			state.artifactAnnotationsToReport.add(artifactReport);
			put = true;
		}

		final Matched writerProcessReport = getMatchedFor(writerProcessVertex, this.processAnnotationsToReport);
		if(state.addWriter(writerProcessMatched, writerProcessReport)){
			put = true;
		}
		
		if(put){
			mapPutter(artifactMatched, state);
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

	private static class MatchedState implements Serializable{
		private static final long serialVersionUID = -7996683701728169086L;
		private final HashSet<Matched> artifactAnnotationsToReport = new HashSet<Matched>();
		private final HashMap<Matched, HashSet<Matched>> writerAnnotationsMatchedToReport = new HashMap<>();

		private final HashMap<Matched, HashSet<Matched>> getWritersExceptFor(final Matched reader){
			final HashMap<Matched, HashSet<Matched>> result = new HashMap<>(writerAnnotationsMatchedToReport);
			result.remove(reader);
			return result;
		}

		private final boolean addWriter(final Matched matched, final Matched report){
			boolean updated = false;
			HashSet<Matched> set = writerAnnotationsMatchedToReport.get(matched);
			if(set == null){
				set = new HashSet<Matched>();
				writerAnnotationsMatchedToReport.put(matched, set);
				updated = true;
			}
			if(!report.isEmpty()){
				set.add(report);
				updated = true;
			}
			return updated;
		}
	}
}
