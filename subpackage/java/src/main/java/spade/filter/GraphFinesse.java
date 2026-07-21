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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Settings;
import spade.core.Vertex;
import spade.utility.ArgumentFunctions;
import spade.utility.HelperFunctions;
import spade.utility.SkeletonGraph;

public class GraphFinesse extends AbstractFilter {

	private static final Logger logger = Logger.getLogger(GraphFinesse.class.getName());

	private static final String configKeyVersion = "annotation";

	private String versionAnnotationName;

	private final Map<String, VertexState> verticesState = new HashMap<>();

	private final SkeletonGraph skeletonGraph = new SkeletonGraph();

	@Override
	public boolean initialize(final String arguments){
		try{
			final Map<String, String> map = HelperFunctions.parseKeyValuePairsFrom(arguments,
					new String[]{Settings.getDefaultConfigFilePath(this.getClass())});
			this.versionAnnotationName = ArgumentFunctions.mustParseNonEmptyString(configKeyVersion, map);
			logger.log(Level.INFO, "Arguments [{0}={1}]", new Object[]{configKeyVersion, versionAnnotationName});
			return true;
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to initialize filter", e);
			return false;
		}
	}

    @Override
	public void putVertex(final AbstractVertex vertex){
		if(vertex == null){
			return;
		}
		final String vertexHash = vertex.bigHashCode();
		if(verticesState.get(vertexHash) == null){
			verticesState.put(vertexHash, new VertexState(vertex));
		}
	}

	@Override
	public void putEdge(final AbstractEdge edge){
		if(edge == null){
			return;
		}
		final AbstractVertex child = edge.getChildVertex();
		final AbstractVertex parent = edge.getParentVertex();
		if(child == null || parent == null){
			return;
		}

		// init
		putVertex(child);
		putVertex(parent);

		final String childHash = child.bigHashCode();
		final String parentHash = parent.bigHashCode();
		if(!skeletonGraph.willCreateCycle(childHash, parentHash)){
			final VertexState childState = verticesState.get(childHash);
			final VertexState parentState = verticesState.get(parentHash);
			putEdgeInNextFilter(edge, childState.putInNextFilter(), parentState.putInNextFilter());
		}else{
			if(Objects.equals(childHash, parentHash)){
				// Self-loop special case since the hash i.e. childHash == parentHash and states
				// are equal
				final VertexState oneState = verticesState.get(childHash);
				final AbstractVertex childCurrentVertex = oneState.putInNextFilter();
				oneState.incrementVersion();
				// Create after incrementing the version
				final AbstractVertex parentCurrentVertex = oneState.putInNextFilter();
				putEdgeInNextFilter(edge, childCurrentVertex, parentCurrentVertex);
			}else{
				final VertexState childState = verticesState.get(childHash);
				final VertexState parentState = verticesState.get(parentHash);
				childState.incrementVersion();
				putEdgeInNextFilter(edge, childState.putInNextFilter(), parentState.putInNextFilter());
			}
		}
		skeletonGraph.putEdge(childHash, parentHash);
	}

	private void putEdgeInNextFilter(final AbstractEdge edge, final AbstractVertex childCurrentVertex,
			final AbstractVertex parentCurrentVertex){
		final AbstractEdge currentEdge = new Edge(childCurrentVertex, parentCurrentVertex);
		currentEdge.addAnnotations(edge.getCopyOfAnnotations());
		putInNextFilter(currentEdge);
	}

	private class VertexState{
		private final TreeMap<String, String> annotations = new TreeMap<>();
		private boolean hasBeenPut;
		private long version;
		private AbstractVertex vertex;

		private VertexState(final AbstractVertex vertex){
			this.annotations.putAll(vertex.getCopyOfAnnotations());
			this.hasBeenPut = false;
			this.version = 0;
			this.vertex = createVertex();
		}

		private AbstractVertex createVertex(){
			final AbstractVertex vertex = new Vertex();
			vertex.addAnnotations(this.annotations);
			vertex.addAnnotation(GraphFinesse.this.versionAnnotationName, String.valueOf(version));
			return vertex;
		}

		private void incrementVersion(){
			this.hasBeenPut = false;
			this.version++;
			this.vertex = createVertex();
		}

		private AbstractVertex putInNextFilter(){
			if(hasBeenPut){
				return this.vertex;
			}
			this.hasBeenPut = true;
			GraphFinesse.this.putInNextFilter(this.vertex);
			return this.vertex;
		}
	}
}
