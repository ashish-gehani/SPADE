/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2016 SRI International

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

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Settings;
import spade.utility.HelperFunctions;

/**
 * CamFlow reporter for SPADE
 * 
 * Assumes that the duplicate flag is true in camflow for now.
 *
 * @author Aurelien Chaline from the original file JSON.java by Hasanat Kazmi
 */
public class CamFlow extends JSON{

	private final Logger logger = Logger.getLogger(this.getClass().getName());
	@Override
	protected Logger getLogger(){
		return logger;
	}
	
	//
	
	private static final String keyInputLog = "inputLog", keyReportingIntervalSeconds = "reportingIntervalSeconds";	

	private Map<String, VertexReference> vertexReferences = new HashMap<String, VertexReference>();
	
	private class VertexReference{
		/**
		 * The vertex (can be null if edge seen before this vertex)
		 */
		private AbstractVertex vertex = null;
		/**
		 * Incremented when referred in edge. Decremented when vertex seen.
		 * When set (back) to 0 then it means that all references to vertices used.
		 * Data in format where a vertex is repeated as many times as the number of times it
		 * is referred in edges.
		 */
		private int used = 0;
		/**
		 * Edges where this vertex is the missing 'from' vertex
		 */
		private final Set<AbstractEdge> incompleteFromEdges = new HashSet<AbstractEdge>();
		/**
		 * Edges where this vertex is the missing 'to' vertex
		 */
		private final Set<AbstractEdge> incompleteToEdges = new HashSet<AbstractEdge>();
	}
	
	private int getCurrentTotalIncompleteEdges(){
		int incompleteEdges = 0;
		for(Map.Entry<String, VertexReference> entry : vertexReferences.entrySet()){
			incompleteEdges += entry.getValue().incompleteFromEdges.size() + entry.getValue().incompleteToEdges.size();
		}
		return incompleteEdges;
	}
	
	@Override
	protected boolean printStats(boolean force){
		final boolean printed = super.printStats(force);
		if(printed){
			log(Level.INFO, "TotalCurrentVertexReferences=" + vertexReferences.size()
				+ ", TotalCurrentIncompleteEdges="+getCurrentTotalIncompleteEdges() + "");
		}
		return printed;
	}

	@Override
	public synchronized boolean launch(String arguments){
		final Map<String, String> map = new HashMap<String, String>();
		try{
			final String configFilePath = Settings.getDefaultConfigFilePath(this.getClass());
			map.putAll(HelperFunctions.parseKeyValuePairsFrom(arguments, configFilePath, null));
		}catch(Exception e){
			log(Level.SEVERE, "Failed to parse arguments and/or storage config file", e);
			return false;
		}

		final String inputFilePathString = map.remove(keyInputLog);
		final String reportingIntervalSecondsString = map.remove(keyReportingIntervalSeconds);
		
		try{
			final boolean blocking = false;
			final boolean closeReaderOnShutdown = true;
			final boolean logAll = true;
			launch(inputFilePathString, reportingIntervalSecondsString, blocking, closeReaderOnShutdown, logAll);
			log(Level.INFO, "Arguments ["+keyInputLog+"="+inputFilePathString+", "+keyReportingIntervalSeconds+"="+reportingIntervalSecondsString+"]");

			if(!map.isEmpty()){
				log(Level.INFO, "Unused key-value pairs in the arguments and/or config file: " + map);
			}

			return true;
		}catch(Exception e){
			log(Level.SEVERE, "Failed to launch reporter", e);
			return false;
		}
	}
	
	/**
	 * Removes if the used count is 0
	 * @param id hash of the vertex as sent by camflow 
	 * @param vref the corresponding VertexReference object
	 */
	private void removeVertexReferenceIfAllUsed(String id, VertexReference vref){
		if(vref.used == 0){
			vertexReferences.remove(id);
		}
	}
	
	@Override
	protected void putVertexToBuffer(final AbstractVertex vertex){
		if(vertex == null){
			throw new RuntimeException("NULL vertex");
		}
		
		final String id = vertex.id();
		if(id == null){
			throw new RuntimeException("NULL id for vertex: " + vertex);
		}
		
		super.putVertexToBuffer(vertex);
		
		VertexReference vref = vertexReferences.get(id);
		if(vref == null){ // Vertex not seen before or was seen but was removed when used in all edges
			vref = new VertexReference();
			vref.vertex = vertex;
			vertexReferences.put(id, vref);
			
			// Decrement the count, and wait till the corresponding edge seen (which will increment this)
			vref.used--;
			// No need to check for incomplete since vertex never seen before
		}else{
			vref.vertex = vertex;
			
			// Decrement here too since vertex is seen again but not used yet (maybe)
			vref.used--;
			
			// Since the vertex cannot be null now check if there are any incomplete edges where this vertex was involved.
			Set<AbstractEdge> completeEdges = new HashSet<AbstractEdge>();
			for(AbstractEdge fromEdge : vref.incompleteFromEdges){
				fromEdge.setChildVertex(vertex);
				if(fromEdge.getChildVertex() != null && fromEdge.getParentVertex() != null){
					// Making sure that both the endpoints are non-null now
					completeEdges.add(fromEdge);
				}
			}
			for(AbstractEdge toEdge : vref.incompleteToEdges){
				toEdge.setParentVertex(vertex);
				if(toEdge.getChildVertex() != null && toEdge.getParentVertex() != null){
					completeEdges.add(toEdge);
				}
			}
			
			// Remove all completed edges from incomplete sets
			vref.incompleteFromEdges.removeAll(completeEdges);
			vref.incompleteToEdges.removeAll(completeEdges);
			
			for(AbstractEdge completeEdge : completeEdges){
				super.putEdgeToBuffer(completeEdge);
			}
			
			// Remove if the used count is 0 now i.e. the number of times this vertex was seen is equal to the number
			// of times this vertex was seen in edges.
			removeVertexReferenceIfAllUsed(id, vref);
		}
	}

	@Override
	protected void putEdgeToBuffer(final AbstractEdge edge){
		if(edge == null){
			throw new RuntimeException("NULL edge");
		}
		
		final AbstractVertex childVertex = edge.getChildVertex();
		if(childVertex == null){
			throw new RuntimeException("NULL child vertex in edge: " + edge);
		}
		
		final String childVertexId = childVertex.id();
		if(childVertexId == null){
			throw new RuntimeException("NULL child vertex id in edge: " + edge);
		}
		
		final AbstractVertex parentVertex = edge.getParentVertex();
		if(parentVertex == null){
			throw new RuntimeException("NULL parent vertex in edge: " + edge);
		}
		
		final String parentVertexId = parentVertex.id();
		if(parentVertexId == null){
			throw new RuntimeException("NULL parent vertex id in edge: " + edge);
		}
		
		// Note: 'fromVRef', and 'toVRef' can be same.
		// So, process one of them and then (after) get the other one otherwise
		// the one gotten later would overwrite the previous one.
		VertexReference fromVRef = vertexReferences.get(childVertexId);
		if(fromVRef == null){
			fromVRef = new VertexReference();
			fromVRef.incompleteFromEdges.add(edge); // Add to incomplete
			vertexReferences.put(childVertexId, fromVRef);
		}else{
			if(fromVRef.vertex != null){
				edge.setChildVertex(fromVRef.vertex); // If the vertex is not null then fill the edge
			}else{
				fromVRef.incompleteFromEdges.add(edge); // Add to incomplete since vertex still null. Wait for it.
			}
		}
		fromVRef.used++; // Since vertex referred.
		
		// Get this after the above code (in case the vertices are the same)
		VertexReference toVRef = vertexReferences.get(parentVertexId); 
		if(toVRef == null){
			toVRef = new VertexReference();
			toVRef.incompleteToEdges.add(edge);
			vertexReferences.put(parentVertexId, toVRef);
		}else{
			if(toVRef.vertex != null){
				edge.setParentVertex(toVRef.vertex);
			}else{
				toVRef.incompleteToEdges.add(edge);
			}
		}
		toVRef.used++;

		// If both null
		if(edge.getParentVertex() != null && edge.getChildVertex() != null){
			super.putEdgeToBuffer(edge);
		}
		
		// Remove the references if all used up.
		removeVertexReferenceIfAllUsed(childVertexId, fromVRef);
		removeVertexReferenceIfAllUsed(parentVertexId, toVRef);
	}
}
