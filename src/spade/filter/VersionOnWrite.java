/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2022 SRI International

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

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Settings;
import spade.utility.ArgumentFunctions;
import spade.utility.HelperFunctions;

public class VersionOnWrite extends AbstractFilter{

	private static final Logger logger = Logger.getLogger(VersionOnWrite.class.getName());

	private static final String keyVersionAnnotationName = "versionAnnotationName",
			keyInitialVersion = "initialVersion", keyEdgeAnnoKey = "edgeAnnoKey", keyEdgeAnnoValue = "edgeAnnoValue";

	private String versionAnnotationName;
	private long initialVersion;
	private String edgeAnnoKey, edgeAnnoValue;

	private Map<String, VertexState> verticesState = new HashMap<String, VertexState>();

	@Override
	public boolean initialize(final String arguments){
		try{
			final Map<String, String> map = HelperFunctions.parseKeyValuePairsFrom(arguments,
					new String[]{Settings.getDefaultConfigFilePath(this.getClass())});
			final String versionAnnotationName = ArgumentFunctions.mustParseNonEmptyString(keyVersionAnnotationName,
					map);
			final long initialVersion = ArgumentFunctions.mustParseLong(keyInitialVersion, map);
			final String edgeAnnoKey = ArgumentFunctions.mustParseNonEmptyString(keyEdgeAnnoKey, map);
			final String edgeAnnoValue = ArgumentFunctions.mustParseNonEmptyString(keyEdgeAnnoValue, map);
			return initialize(versionAnnotationName, initialVersion, edgeAnnoKey, edgeAnnoValue);
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to initialize filter", e);
			return false;
		}
	}

	public boolean initialize(final String versionAnnotationName, final long initialVersion, final String edgeAnnoKey,
			final String edgeAnnoValue){
		this.versionAnnotationName = versionAnnotationName;
		this.initialVersion = initialVersion;
		this.edgeAnnoKey = edgeAnnoKey;
		this.edgeAnnoValue = edgeAnnoValue;
		logger.log(Level.INFO,
				"Arguments {" + keyVersionAnnotationName + "=" + this.versionAnnotationName + ", " + keyInitialVersion
						+ "=" + this.initialVersion + ", " + keyEdgeAnnoKey + "=" + this.edgeAnnoKey + ", "
						+ keyEdgeAnnoValue + "=" + this.edgeAnnoValue 
				+ "}");
		return true;
	}

	@Override
	public void putVertex(final AbstractVertex vertex){
		if(vertex == null){
			return;
		}
		final String hash = vertex.bigHashCode();
		if(verticesState.get(hash) == null){
			verticesState.put(hash, new VertexState());
		}
		verticesState.get(hash).putInNextFilterIfHasNotBeenPut(vertex);
	}

	private void addVowInfoToEdge(final AbstractEdge edge){
		edge.addAnnotation(edgeAnnoKey, edgeAnnoValue);
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
		// Init just in case the reporter doesn't report vertices separately
		putVertex(childVertex);
		putVertex(parentVertex);

		if(childVertex.equals(parentVertex)){
			final VertexState childState = verticesState.get(childVertex.bigHashCode());
			final AbstractVertex childVertexCurrentState = childState.putInNextFilterIfHasNotBeenPut(childVertex);
			childState.incrementVOWVersion();
			final AbstractVertex childVertexNewState = childState.putInNextFilterIfHasNotBeenPut(childVertex);
			final AbstractEdge childStateChangeAndCopyEdge = new Edge(childVertexNewState, childVertexCurrentState);
			childStateChangeAndCopyEdge.addAnnotations(edge.getCopyOfAnnotations());
			addVowInfoToEdge(childStateChangeAndCopyEdge);
			putInNextFilter(childStateChangeAndCopyEdge);
		}else{
			final VertexState childState = verticesState.get(childVertex.bigHashCode());
			final AbstractVertex childVertexCurrentState = childState.putInNextFilterIfHasNotBeenPut(childVertex);
			childState.incrementVOWVersion();
			final AbstractVertex childVertexNewState = childState.putInNextFilterIfHasNotBeenPut(childVertex);

			final VertexState parentState = verticesState.get(parentVertex.bigHashCode());
			final AbstractVertex parentVertexCurrentState = parentState.putInNextFilterIfHasNotBeenPut(parentVertex);
			final AbstractEdge edgeCopy = new Edge(childVertexNewState, parentVertexCurrentState);
			edgeCopy.addAnnotations(edge.getCopyOfAnnotations());
			putInNextFilter(edgeCopy);


			final AbstractEdge childStateChangeEdge = new Edge(childVertexNewState, childVertexCurrentState);
			addVowInfoToEdge(childStateChangeEdge);
			putInNextFilter(childStateChangeEdge);
		}
	}

	private class VertexState{
		private long vowVersion = initialVersion;
		private boolean hasBeenPut = false;

		private long getVOWVersion(){
			return vowVersion;
		}

		private void incrementVOWVersion(){
			if(!hasBeenPut){
				// don't double-increment
			}else{
				vowVersion++;
				hasBeenPut = false;
			}
		}

		private AbstractVertex createVertexWithCurrentState(final AbstractVertex vertex){
			final AbstractVertex vertexCopy = new spade.core.Vertex();
			vertexCopy.addAnnotations(vertex.getCopyOfAnnotations());
			vertexCopy.addAnnotation(versionAnnotationName, String.valueOf(getVOWVersion()));
			return vertexCopy;
		}

		private AbstractVertex putInNextFilterIfHasNotBeenPut(final AbstractVertex vertex){
			final AbstractVertex vertexCopy = createVertexWithCurrentState(vertex);
			if(!this.hasBeenPut){
				putInNextFilter(vertexCopy);
				this.hasBeenPut = true;
			}
			return vertexCopy;
		}
	}
}
