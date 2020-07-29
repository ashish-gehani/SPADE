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

import java.io.File;
import java.io.FileReader;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import spade.core.AbstractEdge;
import spade.core.AbstractReporter;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Graph;
import spade.core.Settings;
import spade.core.Vertex;
import spade.utility.FileUtility;
import spade.utility.HelperFunctions;
import spade.utility.Result;

/**
 * JSON reporter for SPADE
 *
 * @author Hasanat Kazmi
 */
public class JSON extends AbstractReporter{

	private final Logger logger = Logger.getLogger(JSON.class.getName());
	
	private final String keyInput = "input";
	private final String keyDebug = "debug";
	
	private final Object shutdownLock = new Object();
	private volatile boolean shutdown = false;
	
	private final Object isMainThreadRunningLock = new Object();
	private volatile boolean isMainThreadRunning = false;
	
	private boolean debug;
	private File inputFile;
	
	private final boolean initialize(final String arguments){
		final Map<String, String> argsMap = HelperFunctions.parseKeyValPairs(arguments);
		
		String inputString = argsMap.get(keyInput);
		String debugString = argsMap.get(keyDebug);
		
		if(inputString == null || debugString == null){
			final String configFile = Settings.getDefaultConfigFilePath(this.getClass());
			final Map<String, String> configMap;
			try{
				configMap = FileUtility.readConfigFileAsKeyValueMap(configFile, "=");
			}catch(Exception e){
				logger.log(Level.SEVERE, "Failed to read config file '"+configFile+"'", e);
				return false;
			}
			
			if(inputString == null){
				inputString = configMap.get(keyInput);
			}
			if(debugString == null){
				debugString = configMap.get(keyDebug);
			}
		}
		
		if(inputString == null){
			logger.log(Level.SEVERE, "Must specify input file path to read json from using key '"+keyInput+"'");
			return false;
		}
		
		if(debugString == null){
			logger.log(Level.SEVERE, "Must specify whether to debug or not using key '"+keyDebug+"' as a boolean");
			return false;
		}
		
		try{
			this.inputFile = new File(inputString);
			if(!this.inputFile.exists()){
				throw new Exception("Does not exist");
			}else{
				if(this.inputFile.isDirectory()){
					throw new Exception("Cannot be a directory. Must be a file");
				}
				if(!this.inputFile.canRead()){
					throw new Exception("No read permissions");
				}
			}
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to validate input file '"+keyInput+"': '" + inputString + "'", e);
			return false;
		}
		
		final Result<Boolean> debugResult = HelperFunctions.parseBoolean(debugString);
		if(debugResult.error){
			logger.log(Level.SEVERE, "Invalid value for '"+keyDebug+"': '"+debugString+"'");
			logger.log(Level.SEVERE, debugResult.toErrorString());
			return false;
		}
		this.debug = debugResult.result;
		
		logger.log(Level.INFO, "Arguments ["+keyInput+" = "+inputFile.getAbsolutePath()+", "+keyDebug+" = "+debug+"]");
		return true;
	}
	
	@Override
	public boolean launch(final String arguments){
		if(!initialize(arguments)){
			return false;
		}
		
		final JSONArray jsonArray;
		try{
			jsonArray = new JSONArray(new JSONTokener(new FileReader(JSON.this.inputFile)));
		}catch(Exception e){
			log(Level.SEVERE, "Failed to create JSON Array object", e);
			return false;
		}
		
		final Runnable eventThread = new Runnable(){
			public void run(){
				try{
					setIsMainThreadRunning(true);
					
					
	
					debugLog("Size of JSON Array: " + jsonArray.length());
					for(int i = 0; i < jsonArray.length() && !isShutdown(); i++){
						final JSONObject jsonObject;
						try{
							jsonObject = jsonArray.getJSONObject(i);
						}catch(JSONException e){
							log(Level.SEVERE, "Can not read object in JSON Array", e);
							continue;
						}

						try{
							processJSONObject(jsonObject);
						}catch(Exception e){
							log(Level.SEVERE, "Failed to process json object: " + jsonObject, e);
							continue;
						}
					}
					
					debugLog("All provenance reported through JSON file has been retrived. Wait for buffers to clear....");
	
					while(JSON.this.getBuffer().size() > 0){
						if(isShutdown()){
							break;
						}
						HelperFunctions.sleepSafe(5 * 1000); // 5 seconds
					}
	
					debugLog("All buffers cleared. You may remove JSON reporter");
				}finally{
					setIsMainThreadRunning(false);
				}
			}
		};
		new Thread(eventThread, "JsonReporter-Thread").start();
		return true;
	}

	@Override
	public final boolean shutdown(){
		synchronized(shutdownLock){
			shutdown = true;
		}
		debugLog("Waiting on main thread to exit before continuing with shutdown");
		while(isMainThreadRunning()){
			HelperFunctions.sleepSafe(5 * 1000); // 5 second
		}
		debugLog("Shutdown complete");
		return true;
	}
	
	private final boolean isShutdown(){
		synchronized(shutdownLock){
			return shutdown;
		}
	}
	
	private final boolean isMainThreadRunning(){
		synchronized(isMainThreadRunningLock){
			return isMainThreadRunning;
		}
	}
	
	private final void setIsMainThreadRunning(final boolean isMainThreadRunning){
		synchronized(isMainThreadRunningLock){
			this.isMainThreadRunning = isMainThreadRunning;
		}
	}

	private final void processJSONObject(final JSONObject jsonObject){
		final JSONObject annotationsJSONObject;
		try{
			annotationsJSONObject = jsonObject.getJSONObject(AbstractVertex.annotationsKey);
		}catch(JSONException e){
			log(Level.SEVERE, "Missing '" + AbstractVertex.annotationsKey + "' in object. Ignoring object: " + jsonObject, null);
			return;
		}

		final String objectType;
		try{
			objectType = annotationsJSONObject.getString(AbstractVertex.typeKey);
		}catch(JSONException e){
			log(Level.SEVERE, "Missing '" + AbstractVertex.typeKey + "' in nested json object with key '" 
					+ AbstractVertex.annotationsKey + "'. Ignoring object: " + jsonObject, null);
			return;
		}

		if(Graph.isVertexType(objectType)){
			processVertex(jsonObject, annotationsJSONObject);
		}else if(Graph.isEdgeType(objectType)){
			processEdge(jsonObject, annotationsJSONObject);
		}else{
			log(Level.SEVERE, "Unknown object type: '" + objectType + "'. Ignoring object: " + jsonObject, null);
		}
	}

	private void processVertex(final JSONObject vertexObject, final JSONObject annotationsObject){
		String hashString = null;

		try{
			Object hashObject = vertexObject.get(AbstractVertex.hashKey);
			if(hashObject == null){
				throw new JSONException("");
			}
			hashString = String.valueOf(hashObject);
		}catch(JSONException e){
			log(Level.SEVERE, "Missing '" + AbstractVertex.hashKey + "' in vertex. Ignoring vertex: " + vertexObject.toString(), null);
			return;
		}

		AbstractVertex vertex = new Vertex(hashString);

		try{
			if(annotationsObject.length() != 0){
				for(Iterator<?> iterator = annotationsObject.keys(); iterator.hasNext();){
					String key = String.valueOf(iterator.next());
					String value = annotationsObject.getString(key);
					vertex.addAnnotation(key, value);
				}
			}
		}catch(JSONException e){
			// no annotations
		}

		putVertex(vertex);
	}

	private void processEdge(final JSONObject edgeObject, final JSONObject annotationsObject){
		String hashString = null;

		try{
			Object hashObject = edgeObject.get(AbstractEdge.hashKey);
			if(hashObject == null){
				throw new JSONException("");
			}
			hashString = String.valueOf(hashObject);
		}catch(JSONException e){
			log(Level.SEVERE, "Missing '" + AbstractEdge.hashKey + "' in edge. Ignoring edge: " + edgeObject.toString(), null);
			return;
		}

		String childVertexHashString;
		try{
			Object hashObject = edgeObject.get(AbstractEdge.childVertexHashKey);
			if(hashObject == null){
				throw new JSONException("");
			}
			childVertexHashString = String.valueOf(hashObject);
		}catch(JSONException e){
			log(Level.SEVERE, "Missing '" + AbstractEdge.childVertexHashKey + "' in edge. Ignoring edge: " + edgeObject.toString(), null);
			return;
		}

		String parentVertexHashString;
		try{
			Object hashObject = edgeObject.get(AbstractEdge.parentVertexHashKey);
			if(hashObject == null){
				throw new JSONException("");
			}
			parentVertexHashString = String.valueOf(hashObject);
		}catch(JSONException e){
			log(Level.SEVERE, "Missing '" + AbstractEdge.parentVertexHashKey + "' in edge. Ignoring edge: " + edgeObject.toString(), null);
			return;
		}

		AbstractVertex childVertex = new Vertex(childVertexHashString);
		AbstractVertex parentVertex = new Vertex(parentVertexHashString);

		AbstractEdge edge = new Edge(hashString, childVertex, parentVertex);

		try{
			if(annotationsObject.length() != 0){
				for(Iterator<?> iterator = annotationsObject.keys(); iterator.hasNext();){
					String key = String.valueOf(iterator.next());
					String value = annotationsObject.getString(key);
					edge.addAnnotation(key, value);
				}
			}

		}catch(JSONException e){
			// no annotations
		}

		putEdge(edge);
	}

	private final void debugLog(String msg){
		if(debug){
			log(Level.INFO, msg, null);
		}
	}

	private final void log(Level level, String msg, Throwable thrown){
		logger.log(level, msg, thrown);
	}
}
