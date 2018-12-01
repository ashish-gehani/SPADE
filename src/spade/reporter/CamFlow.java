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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

import spade.core.AbstractEdge;
import spade.core.AbstractReporter;
import spade.core.AbstractVertex;
import spade.edge.prov.ActedOnBehalfOf;
import spade.edge.prov.Used;
import spade.edge.prov.WasAssociatedWith;
import spade.edge.prov.WasAttributedTo;
import spade.edge.prov.WasDerivedFrom;
import spade.edge.prov.WasGeneratedBy;
import spade.edge.prov.WasInformedBy;
import spade.utility.CommonFunctions;
import spade.utility.FileUtility;
import spade.utility.LoadableField;
import spade.utility.LoadableFieldHelper;
import spade.vertex.prov.Activity;
import spade.vertex.prov.Agent;
import spade.vertex.prov.Entity;

/**
 * CamFlow reporter for SPADE
 * 
 * Assumes that the duplicate flag is true in camflow for now.
 *
 * @author Aurélien Chaline from the original file JSON.java by Hasanat Kazmi
 */
public class CamFlow extends AbstractReporter {

	private final Logger logger = Logger.getLogger(this.getClass().getName());
	private final static String 
			OBJECT_NAME_ACTIVITY = "Activity",
			OBJECT_NAME_AGENT = "Agent",
			OBJECT_NAME_ENTITY = "Entity",
			OBJECT_NAME_WASGENERATEDBY = "WasGeneratedBy",
			OBJECT_NAME_WASINFORMEDBY = "WasInformedBy",
			OBJECT_NAME_WASDERIVEDFROM = "WasDerivedFrom",
			OBJECT_NAME_WASATTRIBUTEDTO = "WasAttributedTo",
			OBJECT_NAME_WASASSOCIATEDWITH = "WasAssociatedWith",
			OBJECT_NAME_USED = "Used",
			OBJECT_NAME_ACTEDONBEHALFOF = "ActedOnBehalfOf";
	private static final long SLEEP_WAIT_MS = 250;
	private static final String 
			ARGUMENT_NAME_INPUTLOG = "inputLog",
			ARGUMENT_NAME_WAITFORLOG = "waitForLog",
			ARGUMENT_NAME_FAILFAST = "failFast",
			ARGUMENT_NAME_STATS_INTERVAL_MILLIS = "statsIntervalMillis",
			ARGUMENT_NAME_CAMFLOW_DEDUPLICATE = "camflowDuplicate";
	private static final String
			JSON_KEY_ID = "id",
			JSON_KEY_FROM = "from",
			JSON_KEY_TO = "to",
			JSON_KEY_ANNOTATIONS = "annotations",
			JSON_KEY_TYPE = "type";
	
	@LoadableField(name=ARGUMENT_NAME_INPUTLOG, optional=false, mustBeDirectory=false)
	private File inputLog;
	@LoadableField(name=ARGUMENT_NAME_WAITFORLOG, optional=false)
	private Boolean waitForLog;
	@LoadableField(name=ARGUMENT_NAME_FAILFAST, optional=false)
	private Boolean failFast;
	@LoadableField(name=ARGUMENT_NAME_CAMFLOW_DEDUPLICATE, optional=false)
	private Boolean camflowDuplicate;
	@LoadableField(name=ARGUMENT_NAME_STATS_INTERVAL_MILLIS, optional=true, radix=10, min=0)
	private Long statsIntervalMillis;
	
	private boolean reportingEnabled = false;
	private long lastReportedStatsMillis = 0;
	
	private volatile boolean shutdown = false;
	private JsonObjectReaderSingleLine reader;
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
	
	private void printStats(boolean force){
		long currentMillis = System.currentTimeMillis();
		long elapsedMillis = currentMillis - lastReportedStatsMillis;
		if(force || (elapsedMillis >= statsIntervalMillis && statsIntervalMillis > 0)){
			logger.log(Level.INFO, "TotalCurrentVertexReferences={0}, TotalCurrentIncompleteEdges={1}", 
					new Object[]{vertexReferences.size(), getCurrentTotalIncompleteEdges()});
			lastReportedStatsMillis = currentMillis;
		}
	}
	
	private final Thread eventProcessor = new Thread(new Runnable(){
		public void run(){
			while(!shutdown){
				try{
					JSONObject object = reader.readObject();
					if(object != null){
						try{
							processJsonObject(object);
						}catch(MalformedCamFlowObjectException mje){
							String msg = "Failed to process json object: " + object;
							if(failFast){
								logger.log(Level.SEVERE, "Stopping. " + msg, mje);
								break;
							}else{
								logger.log(Level.SEVERE, msg, mje);
							}
						}catch(Throwable t){
							// Letting it continue if failFast 'false', and a new unexpected exception happens.
							String msg = "Failed to process json object because of unexpected exception: " + object;
							if(failFast){
								logger.log(Level.SEVERE, "Stopping. " + msg, t);
								break;
							}else{
								logger.log(Level.SEVERE, msg, t);
							}
						}
					}else{
						logger.log(Level.INFO, "Finished reading json file:" + CamFlow.this.inputLog);
						break;
					}
				}catch(IOException ioe){
					logger.log(Level.SEVERE, "Stopping. Failed to read json object." , ioe);
					break;
				}catch(Throwable t){
					// Letting it continue if failFast 'false', and a new unexpected exception happens.
					// Can be here because of malformed JSON
					String msg = "Failed to read json object because of unexpected exception.";
					if(failFast){
						logger.log(Level.SEVERE, "Stopping. " + msg, t);
						break;
					}else{
						logger.log(Level.SEVERE, msg, t);
					}
				}
			}
			
			closeReader();
			
			printStats(true);

			shutdown = true;
		}
	}, "CamFlow-EventProcessor");
	
	private boolean printGlobals(){
		try{
			String globalsString = LoadableFieldHelper.allLoadableFieldsToString(this);
			logger.log(Level.INFO, "Arguments: " + globalsString);
			return true;
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to log globals", e);
			return false;
		}
	}
	
	private boolean initGlobals(String arguments){
		Map<String, String> globalsMap = null;
		try{
			globalsMap = CommonFunctions.getGlobalsMapFromConfigAndArguments(this.getClass(), arguments);
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to build globals map", e);
			return false;
		}
		
		try{
			LoadableFieldHelper.loadAllLoadableFieldsFromMap(this, globalsMap);
		}catch(Exception e){
			logger.log(Level.SEVERE, "Exception in initializing globals", e);
			return false;
		}
		
		if(!camflowDuplicate){
			logger.log(Level.SEVERE, "Only 'true' supported for argument '"+ARGUMENT_NAME_CAMFLOW_DEDUPLICATE+"' as of now");
			return false;
		}
		
		if(statsIntervalMillis != null && statsIntervalMillis > 0){
			reportingEnabled = true;
		}
		
		if(printGlobals()){
			return true;
		}else{
			return false;
		}
	}

	@Override
	public boolean launch(final String arguments){
		if(!initGlobals(arguments)){
			return false;
		}else{
			String inputLogPath = null;
			try{
				inputLogPath = inputLog.getAbsolutePath();
				if(FileUtility.isFileReadable(inputLogPath)){
					try{
						this.reader = new JsonObjectReaderSingleLine(inputLogPath);
						try{
							this.eventProcessor.start();
							return true;
						}catch(Throwable t){
							logger.log(Level.SEVERE, "Failed to start event processor thread", t);
							closeReader();
							return false;
						}
					}catch(Throwable t){
						logger.log(Level.SEVERE, "Failed to create json reader: " + inputLogPath, t);
						return false;
					}
				}else{
					logger.log(Level.SEVERE, "File not readable: " + inputLogPath);
					return false;
				}
			}catch(Throwable t){
				logger.log(Level.SEVERE, "File error: " + inputLogPath, t);
				return false;
			}
		}
	}

	private void closeReader(){
		if(this.reader != null){
			try{
				this.reader.close();
			}catch(Throwable t){
				logger.log(Level.WARNING, "Failed to close json reader: " + inputLog, t);
			}
		}
	}
	
	@Override
	public boolean shutdown(){
		if(!shutdown){
			if(waitForLog){
				logger.log(Level.INFO, "Will shutdown when file consumed");
				while(!shutdown){
					// Loop until not finished
					try{ Thread.sleep(SLEEP_WAIT_MS); }catch(Throwable t){}
				}
			}else{
				shutdown = true;
			}
		}
		return true;
	}

	private void processJsonObject(JSONObject object) throws Throwable{
		if(reportingEnabled){
			printStats(false);
		}
		
		String objectType = object.optString(JSON_KEY_TYPE, null);
		if(objectType != null){
			switch(objectType){
				case OBJECT_NAME_ACTIVITY			: processVertex(object, new Activity()); break;
				case OBJECT_NAME_AGENT				: processVertex(object, new Agent()); break;
				case OBJECT_NAME_ENTITY				: processVertex(object, new Entity()); break;
				// Creates edges with null, and the nulls are replaced by the processEdge function appropriately
				case OBJECT_NAME_WASGENERATEDBY		: processEdge(object, new WasGeneratedBy(null, null)); break;
				case OBJECT_NAME_WASINFORMEDBY		: processEdge(object, new WasInformedBy(null, null)); break;
				case OBJECT_NAME_WASDERIVEDFROM		: processEdge(object, new WasDerivedFrom(null, null)); break;
				case OBJECT_NAME_WASATTRIBUTEDTO	: processEdge(object, new WasAttributedTo(null, null)); break;
				case OBJECT_NAME_WASASSOCIATEDWITH	: processEdge(object, new WasAssociatedWith(null, null)); break;
				case OBJECT_NAME_USED				: processEdge(object, new Used(null, null)); break;
				case OBJECT_NAME_ACTEDONBEHALFOF	: processEdge(object, new ActedOnBehalfOf(null, null)); break;
				default: throw new MalformedCamFlowObjectException("Unexpected '"+JSON_KEY_TYPE+"': " + objectType);
			}
		}else{
			throw new MalformedCamFlowObjectException("NULL '"+JSON_KEY_TYPE+"'");
		}
	}

	/**
	 * The json object must contain a key 'annotations' whose value is a json object
	 * 
	 * @param object json object either a vertex or an edge
	 * @return either empty or filled map
	 * @throws Throwable malformed exception
	 */
	private Map<String, String> getAnnotationsFromJSON(JSONObject object) throws Throwable{
		Map<String, String> annotations = new HashMap<String, String>();
		JSONObject annotationsObject = object.optJSONObject(JSON_KEY_ANNOTATIONS);
		if(annotationsObject == null){
			throw new MalformedCamFlowObjectException("NULL '"+JSON_KEY_ANNOTATIONS+"'");
		}
		if(annotationsObject != null && annotationsObject.length() > 0){
			JSONArray namesArray = annotationsObject.names();
			for(int i = 0; i < annotationsObject.length(); i++){
				String key = namesArray.optString(i, null);
				if(key != null){
					String value = annotationsObject.optString(key, null);
					annotations.put(key, value);
				}else{
					throw new MalformedCamFlowObjectException("NULL key at index: " + i);
				}
			}
		}
		return annotations;
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
	
	private void processVertex(JSONObject object, AbstractVertex vertex) throws Throwable{
		String id = object.optString(JSON_KEY_ID, null);
		if(id != null){
			Map<String, String> annotations = getAnnotationsFromJSON(object);
			vertex.addAnnotations(annotations);
			// TODO deduplicate logic goes here in future (if any)
			putVertex(vertex);
			
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
					putEdge(completeEdge);
				}
				
				// Remove if the used count is 0 now i.e. the number of times this vertex was seen is equal to the number
				// of times this vertex was seen in edges.
				removeVertexReferenceIfAllUsed(id, vref);
			}
		}else{
			throw new MalformedCamFlowObjectException("NULL '"+JSON_KEY_ID+"'");
		}
	}

	private void processEdge(JSONObject object, AbstractEdge edge) throws Throwable{
		String fromVertexId = object.optString(JSON_KEY_FROM, null);
		if(fromVertexId != null){
			String toVertexId = object.optString(JSON_KEY_TO, null);
			if(toVertexId != null){				
				Map<String, String> annotations = getAnnotationsFromJSON(object);
				edge.addAnnotations(annotations);
				
				// Note: 'fromVRef', and 'toVRef' can be same.
				// So, process one of them and then (after) get the other one otherwise
				// the one gotten later would overwrite the previous one.
				VertexReference fromVRef = vertexReferences.get(fromVertexId);
				if(fromVRef == null){
					fromVRef = new VertexReference();
					fromVRef.incompleteFromEdges.add(edge); // Add to incomplete
					vertexReferences.put(fromVertexId, fromVRef);
				}else{
					if(fromVRef.vertex != null){
						edge.setChildVertex(fromVRef.vertex); // If the vertex is not null then fill the edge
					}else{
						fromVRef.incompleteFromEdges.add(edge); // Add to incomplete since vertex still null. Wait for it.
					}
				}
				fromVRef.used++; // Since vertex referred.
				
				// Get this after the above code (in case the vertices are the same)
				VertexReference toVRef = vertexReferences.get(toVertexId); 
				if(toVRef == null){
					toVRef = new VertexReference();
					toVRef.incompleteToEdges.add(edge);
					vertexReferences.put(toVertexId, toVRef);
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
					putEdge(edge);
				}
				
				// Remove the references if all used up.
				removeVertexReferenceIfAllUsed(fromVertexId, fromVRef);
				removeVertexReferenceIfAllUsed(toVertexId, toVRef);
			}else{
				throw new MalformedCamFlowObjectException("NULL '"+JSON_KEY_TO+"'");
			}
		}else{
			throw new MalformedCamFlowObjectException("NULL '"+JSON_KEY_FROM+"'");
		}
	}
	
	abstract class JsonObjectReader{
		final String filePath;
		final BufferedReader reader;
		JsonObjectReader(String filePath) throws Throwable{
			this.filePath = filePath;
			this.reader = new BufferedReader(new FileReader(new File(filePath)));
		}
		abstract JSONObject readObject() throws Throwable;
		final void close() throws Throwable{
			reader.close();
		}
	}
		
	class JsonObjectReaderSingleLine extends JsonObjectReader{
		/* FILE FORMAT:
		 * {"type":<value>,"id":<id>,"annotations":{<key>:<value>, ...}}
		 * {"type":<value>,"id":<id>,"annotations":{<key>:<value>, ...}}
		 * ...
		 * {"type":<value>,"id":<id>,"annotations":{<key>:<value>, ...}}
		 */
		JsonObjectReaderSingleLine(String filePath) throws Throwable{
			super(filePath);
		}
		JSONObject readObject() throws Throwable{
			JSONObject jsonObject = null;
			String line = null;
			while(true){
				line = reader.readLine();
				if(line == null){// EOF
					break;
				}else{
					if(line.trim().isEmpty()){
						// Ignore
					}else{
						jsonObject = new JSONObject(line);
						break;
					}
				}
			}
			return jsonObject;
		}
	}
	
	/**
	 * Thrown when camflow data not in the expected format
	 */
	private class MalformedCamFlowObjectException extends Exception{
		private static final long serialVersionUID = -2650019982640687284L;
		private MalformedCamFlowObjectException(String msg, Exception e){
			super(msg, e);
		}
		private MalformedCamFlowObjectException(String msg){
			super(msg);
		}
	}
}
