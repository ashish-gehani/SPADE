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
package spade.storage;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONObject;

import spade.core.AbstractEdge;
import spade.core.AbstractStorage;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Graph;
import spade.core.Settings;
import spade.core.Vertex;
import spade.utility.FileUtility;
import spade.utility.HelperFunctions;
import spade.utility.Result;

public final class JSON extends AbstractStorage{

	private static final Logger logger = Logger.getLogger(JSON.class.getName());
	
	public static final String keyVertexType = AbstractVertex.typeKey;
	public static final String keyVertexAnnotations = AbstractVertex.annotationsKey;
	public static final String keyVertexId = AbstractVertex.idKey;
	public static final String keyEdgeType = AbstractEdge.typeKey;
	public static final String keyEdgeAnnotations = AbstractEdge.annotationsKey;
	public static final String keyEdgeFromId = AbstractEdge.fromIdKey;
	public static final String keyEdgeToId = AbstractEdge.toIdKey;
	
	private static final String 
		keyFlushAfterBytesCount = "flushAfterBytes",
		keyOutput = "output";
	
	private String outputFilePath;
	private int flushAfterBytes;
	
	private volatile boolean isInitialized = false;

	private volatile boolean isFirstElement = true;
	private volatile boolean shutdown = false;
	private BufferedWriter outputFileWriter;
	private boolean writeHeader, writeFooter, writeRecordSeparator;
	private String newLine;
	private boolean closeWriterOnShutdown = true;
	
	@Override
	public final synchronized boolean initialize(String arguments){
		final Map<String, String> map = new HashMap<String, String>();
		try{
			final String configFilePath = Settings.getDefaultConfigFilePath(this.getClass());
			map.putAll(HelperFunctions.parseKeyValuePairsFrom(arguments, configFilePath, null));
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to parse arguments and/or storage config file", e);
			return false;
		}
		
		final String outputFilePathString = map.remove(keyOutput);
		final String flushAfterBytesString = map.remove(keyFlushAfterBytesCount);
		
		try{
			initialize(outputFilePathString, flushAfterBytesString);
			logger.log(Level.INFO, "Arguments ["+keyOutput+"="+outputFilePath+", "+keyFlushAfterBytesCount+"="+flushAfterBytes+"]");

			if(!map.isEmpty()){
				logger.log(Level.INFO, "Unused key-value pairs in the arguments and/or config file: " + map);
			}

			return true;
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to initialize storage", e);
			return false;
		}
	}
	
	public final synchronized void initialize(final String outputFilePathString, final String flushAfterBytesString) throws Exception{
		final Result<Long> flushAfterBytesCountResult = HelperFunctions.parseLong(flushAfterBytesString, 10, 0, Integer.MAX_VALUE);
		if(flushAfterBytesCountResult.error){
			throw new Exception("Invalid value for number to bytes to flush after: '"+flushAfterBytesString+"'. " + flushAfterBytesCountResult.errorMessage);
		}

		try{
			FileUtility.pathMustBeAWritableFile(outputFilePathString);
		}catch(Exception e){
			throw new Exception("Invalid output file path to write to: '"+outputFilePathString+"'", e);
		}

		initializeUnsafe(outputFilePathString, flushAfterBytesCountResult.result.intValue(),
				true, true, true, System.lineSeparator(), true);
	}
	
	public final synchronized void initializeUnsafe(final String validatedOutputFilePath, final int validatedFlushAfterBytesCount,
			final boolean writeHeader, final boolean writeFooter, final boolean writeRecordSeparator, final String newLine,
			final boolean closeWriterOnShutdown) throws Exception{
		if(isInitialized){
			throw new Exception("Storage already initialized");
		}
		
		BufferedWriter writer = null;
		try{
			this.outputFilePath = validatedOutputFilePath;
			this.flushAfterBytes = validatedFlushAfterBytesCount;
			
			if(this.flushAfterBytes <= 0){
				writer = new BufferedWriter(new FileWriter(new File(this.outputFilePath)));
			}else{
				writer = new BufferedWriter(new FileWriter(new File(this.outputFilePath)), this.flushAfterBytes);
			}
		}catch(Exception e){
			throw new Exception("Failed to create output file writer for file: '" + validatedOutputFilePath + "'", e);
		}
		
		try{
			initializeUnsafe(writer, writeHeader, writeFooter, writeRecordSeparator, newLine, closeWriterOnShutdown);
		}catch(Exception e){
			if(writer != null){
				try{
					writer.close();
				}catch(Exception closeException){
					// ignore
				}
			}
			throw e;
		}
	}
	
	public final synchronized void initializeUnsafe(final BufferedWriter bufferedWriter,
			final boolean writeHeader, final boolean writeFooter, final boolean writeRecordSeparator, final String newLine,
			final boolean closeWriterOnShutdown) throws Exception{
		if(isInitialized){
			throw new Exception("Storage already initialized");
		}
		
		if(newLine == null){
			throw new Exception("New line separator cannot be null");
		}

		this.writeHeader = writeHeader;
		this.writeFooter = writeFooter;
		this.writeRecordSeparator = writeRecordSeparator;
		this.newLine = newLine;
		this.outputFileWriter = bufferedWriter;
		this.closeWriterOnShutdown = closeWriterOnShutdown;

		try{
			if(this.writeHeader){
				final String startOfFile = "[" + newLine;
				write(startOfFile, true);
			}
			
			this.isInitialized = true;
		}catch(Exception e){
			closeFileWriter();
			throw new Exception("Failed to write header", e);
		}
	}

	public final synchronized boolean isShutdown(){
		return shutdown;
	}
	
	@Override
	public final synchronized boolean shutdown(){
		if(isInitialized){
			if(shutdown){
				logger.log(Level.SEVERE, "Storage is already shutdown");
			}else{
				shutdown = true;
				if(writeFooter){
					write("]" + newLine, true);
				}
				if(this.outputFileWriter != null){
					closeFileWriter();
				}
			}
		}
		return true;
	}

	private final synchronized void write(final String data, final boolean forceFlush){
		if(data != null){
			if(this.outputFileWriter == null){
				logger.log(Level.SEVERE, "File writer is already closed. Data discarded: " + data);
			}else{
				try{
					this.outputFileWriter.write(data);
					if(forceFlush){
						this.outputFileWriter.flush();
					}
				}catch(Exception writeException){
					logger.log(Level.SEVERE, "Failed to write/flush data. Tail end of data discarded: " + data, writeException);
					closeFileWriter();
				}
			}
		}else{
			logger.log(Level.WARNING, "Discarded <NULL> data to write");
		}
	}
	
	private final synchronized void writeObject(String object, final boolean forceFlush){
		if(isFirstElement){
			isFirstElement = false;
		}else{
			if(writeRecordSeparator){
				object = "," + object;
			}
		}
		write(object, forceFlush);
	}
	
	private final synchronized void closeFileWriter(){
		if(this.outputFileWriter != null){
			try{
				this.outputFileWriter.flush(); // always flush
				if(this.closeWriterOnShutdown){
					this.outputFileWriter.close();
				}
			}catch(Exception e){
				logger.log(Level.SEVERE, "Failed to close file writer. Some data might be lost.", e);
			}finally{
				this.outputFileWriter = null;
			}
		}else{
			logger.log(Level.WARNING, "File writer is already closed.");
		}
	}
	
	@Override
	public final synchronized boolean storeVertex(final AbstractVertex vertex){
		if(vertex == null){
			logger.log(Level.WARNING, "NULL vertex to put. Vertex ignored.");
		}else if(vertex.type() == null){
			logger.log(Level.WARNING, "Vertex with no type. Vertex ignored: " + vertex);
		}else{
			try{
				final String vertexType = vertex.type();
				
				final Map<String, String> annotationsCopy = vertex.getCopyOfAnnotations();
				annotationsCopy.remove(AbstractVertex.typeKey);
				annotationsCopy.remove(AbstractVertex.idKey);
				
				final JSONObject jsonObject = new JSONObject();
				jsonObject.put(keyVertexId, vertex.getIdentifierForExport());
				jsonObject.put(keyVertexType, vertexType);
				jsonObject.put(keyVertexAnnotations, annotationsCopy);
				
				final String jsonObjectString = jsonObject.toString() + newLine;
				
				writeObject(jsonObjectString, false);
			}catch(Exception e){
				logger.log(Level.WARNING, "Unexpected error. Vertex discarded: " + vertex, e);
			}
		}
		return true;
	}

	@Override
	public final synchronized boolean storeEdge(final AbstractEdge edge){
		if(edge == null){
			logger.log(Level.WARNING, "NULL edge to put. Edge ignored.");
		}else if(edge.getChildVertex() == null){
			logger.log(Level.WARNING, "NULL child vertex in edge to put. Edge ignored: " + edge);
		}else if(edge.getParentVertex() == null){
			logger.log(Level.WARNING, "NULL parent vertex in edge to put. Edge ignored: " + edge);
		}else if(edge.type() == null){
			logger.log(Level.WARNING, "Edge with no type. Edge ignored: " + edge);
		}else{
			try{
				final String edgeType = edge.type();
				
				final Map<String, String> annotationsCopy = edge.getCopyOfAnnotations();
				annotationsCopy.remove(AbstractEdge.typeKey);
				annotationsCopy.remove(AbstractEdge.idKey);
				
				final JSONObject jsonObject = new JSONObject();
				jsonObject.put(keyEdgeFromId, edge.getChildVertex().getIdentifierForExport());
				jsonObject.put(keyEdgeToId, edge.getParentVertex().getIdentifierForExport());
				jsonObject.put(keyEdgeType, edgeType);
				jsonObject.put(keyEdgeAnnotations, annotationsCopy);

				final String jsonObjectString = jsonObject.toString() + newLine;
				
				writeObject(jsonObjectString, false);
			}catch(Exception e){
				logger.log(Level.WARNING, "Unexpected error. Edge discarded: " + edge, e);
			}
		}
		return true;
	}

	@Override
	public final synchronized Object executeQuery(String query){
		throw new RuntimeException("JSON storage does NOT support querying");
	}
	
	public static void main(final String[] args) throws Exception{
		final String jsonPath = "/tmp/test.txt";
		
		Graph g = new Graph();
		AbstractVertex v0 = new Vertex();
		v0.addAnnotation("a", "\"b");
		v0.addAnnotation("type", "Artifact");
		v0.addAnnotation("id", "0\"01");
		g.putVertex(v0);
		
		AbstractVertex v1 = new Vertex();
		v1.addAnnotation("type", "Artifact");
		v1.addAnnotation("subtype", "network socket");
//		v1.addAnnotation("id", "002");
		g.putVertex(v1);
		
		AbstractEdge e0 = new Edge(v0, v1);
		e0.addAnnotation("type", "SimpleEdge");
		e0.addAnnotation("cdm.type", "EVENT_WRITE");
		g.putEdge(e0);
		
		JSON s = new JSON();
		if(true){//s.initialize(keyOutput+"="+jsonPath + " " + keyFlushAfterBytesCount + "=0")){
			s.initializeUnsafe(new BufferedWriter(new OutputStreamWriter(System.out)), true, true, true, System.lineSeparator(), false);
			for(AbstractVertex v : g.vertexSet()){
				s.putVertex(v);
			}
			for(AbstractEdge e : g.edgeSet()){
				s.putEdge(e);
			}
			s.shutdown();
			
			//Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", "/usr/bin/open " + jsonPath});
		}
		System.out.println("I did not get closed");
	}
}
