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
package spade.filter;

import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Settings;
import spade.utility.ArgumentFunctions;
import spade.utility.HelperFunctions;

// Source: https://www.usenix.org/legacy/event/fast09/tech/slides/muniswamy.pdf
public class CycleAvoidance extends AbstractFilter{

	private static final Logger logger = Logger.getLogger(CycleAvoidance.class.getName());

	private static final String keyVersionAnnotationName = "versionAnnotationName",
			keyInitialVersion = "initialVersion", keyKeepAllVertices = "keepAllVertices";

	private String versionAnnotationName;
	private long initialVersion;
	private boolean keepAllVertices;

	private final GlobalState globalState = new GlobalState();
	private final LocalState localState = new LocalState();

	@Override
	public boolean initialize(final String arguments){
		try{
			final Map<String, String> map = HelperFunctions.parseKeyValuePairsFrom(arguments, new String[]{
					Settings.getDefaultConfigFilePath(this.getClass())
			});
			final String versionAnnotationName = ArgumentFunctions.mustParseNonEmptyString(keyVersionAnnotationName,
					map);
			final long initialVersion = ArgumentFunctions.mustParseLong(keyInitialVersion, map);
			final boolean keepAllVertices = ArgumentFunctions.mustParseBoolean(keyKeepAllVertices, map);
			return initialize(versionAnnotationName, initialVersion, keepAllVertices);
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to initialize filter", e);
			return false;
		}
	}

	public boolean initialize(final String versionAnnotationName, final long initialVersion,
			final boolean keepAllVertices){
		this.versionAnnotationName = versionAnnotationName;
		this.initialVersion = initialVersion;
		this.keepAllVertices = keepAllVertices;
		logger.log(Level.INFO,
				"Arguments {" + keyVersionAnnotationName + "=" + this.versionAnnotationName + ", " + keyInitialVersion
						+ "=" + this.initialVersion + ", " + keyKeepAllVertices + "=" + this.keepAllVertices
				+ "}");
		return true;
	}

	@Override
	public void putVertex(final AbstractVertex vertex){
		if(vertex == null){
			return;
		}
		final VertexState vertexState;
		if(!globalState.contains(vertex)){
			vertexState = globalState.add(vertex);
		}else{
			vertexState = globalState.getState(vertex);
		}
		if(keepAllVertices){
			vertexState.putInNextFilterIfHasNotBeenPut();
		}
	}

	@Override
	public void putEdge(final AbstractEdge edge){
		if(edge == null){
			return;
		}
		final AbstractVertex childVertex = edge.getChildVertex();
		final AbstractVertex parentVertex = edge.getParentVertex();
		if(childVertex == null || parentVertex == null){
			return;
		}
		if(childVertex.equals(parentVertex)){
			return;
		}
		// Init just in case the reporter doesn't report vertices separately
		putVertex(childVertex);
		putVertex(parentVertex);

		final VertexParentState childParentState;
		if(!localState.contains(childVertex)){
			childParentState = localState.add(childVertex);
		}else{
			childParentState = localState.getState(childVertex);
		}

		final VertexState childGlobalState = globalState.getState(childVertex);
		final VertexState parentGlobalState =  globalState.getState(parentVertex);
		final Long parentGlobalVersion = parentGlobalState.getCAVersion();

		final boolean put;

		if(!childParentState.hasParent(parentVertex)){
			childParentState.addParent(parentVertex, parentGlobalVersion);
			childGlobalState.incrementCAVersion();
			put = true;
		}else{
			final Long parentLocalVersion = childParentState.getParentVersion(parentVertex);
			if(parentLocalVersion == parentGlobalVersion){
				// discard because duplicate
				put = false;
			}else if(parentLocalVersion > parentGlobalVersion){
				// discard because might cause cycles
				put = false;
			}else{
				childGlobalState.incrementCAVersion();
				put = true;
			}
		}

		if(put){
			final AbstractVertex caChildVertex = childGlobalState.putInNextFilterIfHasNotBeenPut();
			final AbstractVertex caParentVertex = parentGlobalState.putInNextFilterIfHasNotBeenPut();
			final AbstractEdge caEdge = new Edge(caChildVertex, caParentVertex);
			caEdge.addAnnotations(edge.getCopyOfAnnotations());
			putInNextFilter(caEdge);
		}
	}

	private class GlobalState{
		private final TreeMap<String, VertexState> vertices = new TreeMap<>();

		private VertexState add(final AbstractVertex vertex){
			final String hash = vertex.bigHashCode();
			final Map<String, String> annotations = vertex.getCopyOfAnnotations();
			final VertexState vertexState = new VertexState(annotations);
			vertices.put(hash, vertexState);
			return vertexState;
		}

		private VertexState getState(final AbstractVertex vertex){
			return vertices.get(vertex.bigHashCode());
		}

		private boolean contains(final AbstractVertex vertex){
			return getState(vertex) != null;
		}
	}

	private class VertexState{
		private final TreeMap<String, String> annotations = new TreeMap<>();
		private long caVersion = initialVersion;
		private boolean hasBeenPut = false;

		private VertexState(final Map<String, String> annotations){
			this.annotations.putAll(annotations);
		}

		private long getCAVersion(){
			return caVersion;
		}

		private void incrementCAVersion(){
			if(!hasBeenPut){
				// don't double-increment
			}else{
				caVersion++;
				hasBeenPut = false;
			}
		}

		private AbstractVertex createVertexWithCurrentState(){
			final AbstractVertex vertex = new spade.core.Vertex();
			vertex.addAnnotations(this.annotations);
			vertex.addAnnotation(versionAnnotationName, String.valueOf(caVersion));
			return vertex;
		}

		private AbstractVertex putInNextFilterIfHasNotBeenPut(){
			final AbstractVertex vertex = this.createVertexWithCurrentState();
			if(!this.hasBeenPut){
				putInNextFilter(vertex);
				this.hasBeenPut = true;
			}
			return vertex;
		}
	}

	private class VertexParentState{
		private final TreeMap<String, Long> parentVersions = new TreeMap<>();

		private boolean hasParent(final AbstractVertex parent){
			return getParentVersion(parent) != null;
		}

		private Long getParentVersion(final AbstractVertex parent){
			return parentVersions.get(parent.bigHashCode());
		}

		private void addParent(final AbstractVertex parent, final Long version){
			final String parentHash = parent.bigHashCode();
			parentVersions.put(parentHash, version);
		}
	}

	private class LocalState{
		private final TreeMap<String, VertexParentState> vertices = new TreeMap<>();

		private VertexParentState getState(final AbstractVertex vertex){
			return vertices.get(vertex.bigHashCode());
		}

		private boolean contains(final AbstractVertex vertex){
			return getState(vertex) != null;
		}

		private VertexParentState add(final AbstractVertex vertex){
			final String hash = vertex.bigHashCode();
			final VertexParentState vertexParentState = new VertexParentState();
			vertices.put(hash, vertexParentState);
			return vertexParentState;
		}
	}

}
