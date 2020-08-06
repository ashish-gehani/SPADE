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
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONObject;
import org.json.JSONTokener;

import spade.core.AbstractEdge;
import spade.core.AbstractReporter;
import spade.core.AbstractVertex;
import spade.core.Edge;
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
	protected Logger getLogger(){
		return logger;
	}
	
	protected final void log(final Level level, final String msg){
		log(level, msg, null);
	}
	
	protected final void log(final Level level, final String msg, final Exception exception){
		if(!logAll){
			if(Level.INFO.equals(level) || Level.WARNING.equals(level)){
				return;
			}
		}
		if(exception == null){
			getLogger().log(level, msg);
		}else{
			getLogger().log(level, msg, exception);
		}
	}
	
	//
	
	private static final String keyInput = "input", keyReportingIntervalSeconds = "reportingIntervalSeconds";

	private String inputFilePath = null;
	private Long reportingIntervalMillis = null;

	private boolean reportingEnabled = false;
	
	private boolean isLaunched = false;
	private boolean closeReaderOnShutdown = true;
	private boolean logAll = true;
	
	///////////////////////////////////////////////////////
	
	private long lastReportedAtMillis = 0;

	private final Object shutdownLock = new Object();
	private volatile boolean shutdown = false;
	private volatile Reader reader = null; 
	private volatile boolean mainRunning = false;
	private volatile JSONTokener jsonTokener = null;
	
	private volatile boolean mainStopped = false;

	private long vertexCountInterval = 0;
	private long vertexCountOverall = 0;

	private long edgeCountInterval = 0;
	private long edgeCountOverall = 0;

	private final void vertexCountIncrement(){
		vertexCountInterval++;
		vertexCountOverall++;
	}

	private final void edgeCountIncrement(){
		edgeCountInterval++;
		edgeCountOverall++;
	}
	
	////////////////////////////////////////
	
	private final boolean isShutdown(){
		synchronized(shutdownLock){
			return shutdown;
		}
	}

	private final void setShutdown(final boolean shutdown){
		synchronized(shutdownLock){
			this.shutdown = shutdown;
		}
	}

	protected boolean printStats(boolean force){
		long currentMillis = System.currentTimeMillis();
		if(force || (reportingEnabled && currentMillis - lastReportedAtMillis >= reportingIntervalMillis)){
			log(Level.INFO, "Vertices [Overall=" + vertexCountOverall + ", Interval=" + vertexCountInterval + "]");
			log(Level.INFO, "Edges [Overall=" + edgeCountOverall + ", Interval=" + edgeCountInterval + "]");
			log(Level.INFO, "Current Buffer Size=" + getBuffer().size());

			vertexCountInterval = edgeCountInterval = 0;
			lastReportedAtMillis = System.currentTimeMillis();
			return true;
		}
		return false;
	}
	
	private final Runnable main = new Runnable(){
		@Override
		public void run(){
			try{
				mainRunning = true;
	
				lastReportedAtMillis = System.currentTimeMillis();

				Object object = null;
				while(!isShutdown()){
					printStats(false);
	
					try{
						object = jsonTokener.nextValue();
					}catch(Exception e){
						log(Level.SEVERE, "Failed to read JSON object", e);
						break;
					}
					if(object == null){
						log(Level.SEVERE, "NULL JSON object");
						break;
					}
					if(!object.getClass().equals(JSONObject.class)){
						log(Level.SEVERE, "Unexpected JSON element '"+object+"'. Expected JSON object");
						break;
					}
					try{
						processJSONObject((JSONObject)object);
					}catch(Exception e){
						log(Level.SEVERE, "Failed to process JSON object: " + object, e);
						break;
					}
					try{
						char c = jsonTokener.skipTo('{');
						if(c != '{'){ // end of input
							break;
						}
					}catch(Exception e){
						log(Level.SEVERE, "Failed to find the next JSON object", e);
						break;
					}
				}
			}finally{
				mainRunning = false;
				log(Level.INFO, "Exited main thread");
				mainStopped = true;
			}
		}
	};
	
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

		final String inputFilePathString = map.remove(keyInput);
		final String reportingIntervalSecondsString = map.remove(keyReportingIntervalSeconds);
		
		try{
			final boolean blocking = false;
			final boolean closeReaderOnShutdown = true;
			final boolean logAll = true;
			launch(inputFilePathString, reportingIntervalSecondsString, blocking, closeReaderOnShutdown, logAll);
			log(Level.INFO, "Arguments ["+keyInput+"="+inputFilePathString+", "+keyReportingIntervalSeconds+"="+reportingIntervalSecondsString+"]");

			if(!map.isEmpty()){
				log(Level.INFO, "Unused key-value pairs in the arguments and/or config file: " + map);
			}

			return true;
		}catch(Exception e){
			log(Level.SEVERE, "Failed to launch reporter", e);
			return false;
		}
	}
	
	public final synchronized void launch(final String inputFilePathString, final String reportingIntervalSecondsString,
			final boolean blocking, final boolean closeReaderOnShutdown, final boolean logAll) throws Exception{
		final Result<Long> reportingIntervalSecondsResult = 
				HelperFunctions.parseLong(reportingIntervalSecondsString, 10, Integer.MIN_VALUE, Integer.MAX_VALUE);
		if(reportingIntervalSecondsResult.error){
			throw new Exception("Invalid number of seconds to report stats at: '"+reportingIntervalSecondsString+"'. " 
					+ reportingIntervalSecondsResult.errorMessage);
		}

		try{
			FileUtility.pathMustBeAReadableFile(inputFilePathString);
		}catch(Exception e){
			throw new Exception("Invalid input file path to read from: '"+inputFilePathString+"'", e);
		}

		launchUnsafe(inputFilePathString, reportingIntervalSecondsResult.result.intValue(), blocking, closeReaderOnShutdown, logAll);
	}
	
	public final synchronized void launchUnsafe(final String validateInputFilePath, final int reportingIntervalSeconds,
			final boolean blocking, final boolean closeReaderOnShutdown, final boolean logAll) throws Exception{
		if(isLaunched){
			throw new Exception("Reporter already launched");
		}

		BufferedReader reader = null;
		try{
			this.inputFilePath = validateInputFilePath;
			this.reportingIntervalMillis = reportingIntervalSeconds * 1000L;
			if(this.reportingIntervalMillis > 0){
				this.reportingEnabled = true;
			}
			
			reader = new BufferedReader(new FileReader(new File(this.inputFilePath)));
		}catch(Exception e){
			throw new Exception("Failed to create input file reader for file: '" + validateInputFilePath + "'", e);
		}
		
		try{
			launchUnsafe(reader, blocking, closeReaderOnShutdown, logAll);
		}catch(Exception e){
			if(reader != null){
				try{
					reader.close();
				}catch(Exception closeException){
					// ignore
				}
			}
			throw e;
		}
	}
	
	public final synchronized void launchUnsafe(final Reader reader, final boolean blocking, 
			final boolean closeReaderOnShutdown, final boolean logAll) throws Exception{
		if(isLaunched){
			throw new Exception("Reporter already launched");
		}
		
		this.reader = reader;
		this.closeReaderOnShutdown = closeReaderOnShutdown;
		this.logAll = logAll;
		
		try{
			this.jsonTokener = new JSONTokener(this.reader);
		}catch(Exception e){
			throw new Exception("Failed to create JSON tokener", e);
		}
		
		try{
			char c = jsonTokener.skipTo('{');
			if(c != '{'){
				throw new Exception("No JSON object");
			}
		}catch(Exception e){
			throw new Exception("Failed to find the first JSON object", e);
		}
		
		try{
			final Thread thread = new Thread(main, this.getClass().getSimpleName() + "-reporter-thread");
			thread.start();
			
			this.isLaunched = true;
			
		}catch(Exception e){
			shutdown();
			throw new Exception("Failed to start main thread", e);
		}
		
		if(blocking){
			// Wait for the thread to stop on its own
			while(!mainStopped){
				HelperFunctions.sleepSafe(100);
			}
		}
	}
	
	@Override
	public final boolean shutdown(){
		if(!isShutdown()){
			setShutdown(true);

			log(Level.INFO, "Waiting for main thread to exit... ");
			while(this.mainRunning){
				HelperFunctions.sleepSafe(100);
			}

			if(this.reader != null){
				try{
					if(this.closeReaderOnShutdown){
						this.reader.close();
					}
				}catch(Exception e){
					log(Level.WARNING, "Failed to close file reader", e);
				}
				this.reader = null;
			}

			printStats(true);
		}
		return true;
	}

	private final void processJSONObject(final JSONObject jsonObject){
		final String typeString = jsonObject.optString(AbstractVertex.typeKey, null);
		if(AbstractVertex.isVertexType(typeString)){
			processVertex(jsonObject);
		}else if(AbstractEdge.isEdgeType(typeString)){
			processEdge(jsonObject);
		}else{
			log(Level.WARNING, "Unhandled 'type' in JSON object: " + jsonObject);
			return;
		}
	}

	private final void processVertex(final JSONObject vertexObject){
		final String idString = vertexObject.optString(AbstractVertex.idKey, null);
		
		if(HelperFunctions.isNullOrEmpty(idString)){
			log(Level.WARNING, "NULL/Empty vertex 'id' in JSON object: " + vertexObject);
			return;
		}
		
		final String typeString = vertexObject.optString(AbstractVertex.typeKey, null);
		
		if(HelperFunctions.isNullOrEmpty(typeString)){
			log(Level.WARNING, "NULL/Empty vertex 'type' in JSON object: " + vertexObject);
			return;
		}
		
		final Map<String, String> annotationsMap = new HashMap<String, String>();
		
		try{
			final JSONObject annotationsObject = vertexObject.optJSONObject(AbstractVertex.annotationsKey);
		
			if(annotationsObject == null){
				throw new Exception("NULL 'annotations'");
			}
			
			annotationsMap.putAll(HelperFunctions.convertJSONObjectToMap(annotationsObject));
		}catch(Exception e){
			log(Level.WARNING, "Failed to get/parse vertex 'annotations' map in JSON object: " + vertexObject, e);
			return;
		}
		
		annotationsMap.put(AbstractVertex.typeKey, typeString);
		
		final Vertex vertex = new Vertex(idString);
		vertex.addAnnotations(annotationsMap);

		vertexCountIncrement();
		putVertexToBuffer(vertex);
	}

	private final void processEdge(final JSONObject edgeObject){
		final String fromIdString = edgeObject.optString(AbstractEdge.fromIdKey, null);
		
		if(HelperFunctions.isNullOrEmpty(fromIdString)){
			log(Level.WARNING, "NULL/Empty edge 'from' id in JSON object: " + edgeObject);
			return;
		}
		
		final String toIdString = edgeObject.optString(AbstractEdge.toIdKey, null);
		
		if(HelperFunctions.isNullOrEmpty(toIdString)){
			log(Level.WARNING, "NULL/Empty edge 'to' id in JSON object: " + edgeObject);
			return;
		}
		
		final String typeString = edgeObject.optString(AbstractEdge.typeKey, null);
		
		if(HelperFunctions.isNullOrEmpty(typeString)){
			log(Level.WARNING, "NULL/Empty edge 'type' in JSON object: " + edgeObject);
			return;
		}
		
		final Map<String, String> annotationsMap = new HashMap<String, String>();
		
		try{
			final JSONObject annotationsObject = edgeObject.optJSONObject(AbstractEdge.annotationsKey);
		
			if(annotationsObject == null){
				throw new Exception("NULL 'annotations'");
			}
			
			annotationsMap.putAll(HelperFunctions.convertJSONObjectToMap(annotationsObject));
		}catch(Exception e){
			log(Level.WARNING, "Failed to get/parse edge 'annotations' map in JSON object: " + edgeObject, e);
			return;
		}
		
		annotationsMap.put(AbstractEdge.typeKey, typeString);

		final AbstractVertex childVertex = new Vertex(fromIdString);
		final AbstractVertex parentVertex = new Vertex(toIdString);

		final AbstractEdge edge = new Edge(childVertex, parentVertex);
		edge.addAnnotations(annotationsMap);

		edgeCountIncrement();
		putEdgeToBuffer(edge);
	}
	
	protected void putVertexToBuffer(final AbstractVertex vertex){
		putVertex(vertex);
	}
	
	protected void putEdgeToBuffer(final AbstractEdge edge){
		putEdge(edge);
	}
}
