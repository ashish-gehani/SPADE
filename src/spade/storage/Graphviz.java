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
package spade.storage;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractEdge;
import spade.core.AbstractStorage;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Graph;
import spade.core.Settings;
import spade.core.Vertex;
import spade.utility.DotConfiguration;
import spade.utility.DotConfiguration.ShapeColor;
import spade.utility.FileUtility;
import spade.utility.HelperFunctions;
import spade.utility.Result;

/**
 * A storage implementation that writes data to a DOT file.
 *
 * @author Dawood Tariq
 */
public final class Graphviz extends AbstractStorage{

	private static final Logger logger = Logger.getLogger(Graphviz.class.getName());
	
	private static final String 
		keyFlushAfterBytesCount = "flushAfterBytes",
		keyOutput = "output";
	
	private String outputFilePath;
	private int flushAfterBytes;
	
	private volatile boolean isInitialized = false;

	private volatile boolean shutdown = false;
	private BufferedWriter outputFileWriter;
	private DotConfiguration dotConfiguration;
	private boolean writeHeader, writeFooter;
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

			/*
			if(!map.isEmpty()){
				logger.log(Level.INFO, "Unused key-value pairs in the arguments and/or config file: " + map);
			}
			*/

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

		initializeUnsafe(outputFilePathString, flushAfterBytesCountResult.result.intValue(), DotConfiguration.getDefaultConfigFilePath(),
				true, true, System.lineSeparator(), true);
	}
	
	public final synchronized void initializeUnsafe(final String validatedOutputFilePath, final int validatedFlushAfterBytesCount,
			final String dotConfigurationFilePath, 
			final boolean writeHeader, final boolean writeFooter, final String newLine,
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
			initializeUnsafe(writer, dotConfigurationFilePath, writeHeader, writeFooter, newLine, closeWriterOnShutdown);
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
			final String dotConfigurationFilePath, 
			final boolean writeHeader, final boolean writeFooter, final String newLine,
			final boolean closeWriterOnShutdown) throws Exception{
		if(isInitialized){
			throw new Exception("Storage already initialized");
		}
		
		if(newLine == null){
			throw new Exception("New line separator cannot be null");
		}
		
		final Result<DotConfiguration> dotConfigurationResult = DotConfiguration.loadFromFile(dotConfigurationFilePath);
		if(dotConfigurationResult.error){
			throw new Exception("Failed to initialize dot configuration: " + dotConfigurationResult.errorMessage, dotConfigurationResult.exception);
		}
		this.dotConfiguration = dotConfigurationResult.result;
		
		this.writeHeader = writeHeader;
		this.writeFooter = writeFooter;
		this.newLine = newLine;
		this.outputFileWriter = bufferedWriter;
		this.closeWriterOnShutdown = closeWriterOnShutdown;
		
		try{
			if(this.writeHeader){
				final String startOfFile = 
						"digraph spade2dot {" + newLine
						+ "graph [rankdir = \"RL\"];" + newLine
						+ "node [fontname=\"Helvetica\" fontsize=\"8\" style=\"filled\" margin=\"0.0,0.0\"];" + newLine
						+ "edge [fontname=\"Helvetica\" fontsize=\"8\"];" + newLine;
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
					write("}" + newLine, true);
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

	private final String convertAnnotationsToDotLabel(final Map<String, String> annotations){
		final StringBuilder annotationString = new StringBuilder();
		for(final Map.Entry<String, String> currentEntry : annotations.entrySet()){
			String key = currentEntry.getKey();
			String value = currentEntry.getValue();
			if(key == null || value == null){
				logger.log(Level.WARNING, "NULL key and/or value. Entry ignored. Map: " + annotations);
				continue;
			}
			annotationString.append(key);
			annotationString.append(":");
			annotationString.append(value);
			annotationString.append("\\n");
		}
		final String result = escapeDoubleQuotes(annotationString.toString());
		if(result.length() >= 2){ // for the trailing "\\n"
			return result.substring(0, result.length() - 2);
		}else{
			return result;
		}
	}
	
	private final String escapeDoubleQuotes(final String str){
		return str.replaceAll("\"", "\\\\\"");
	}
	
	@Override
	public final synchronized boolean storeVertex(final AbstractVertex vertex){
		if(vertex == null){
			logger.log(Level.WARNING, "NULL vertex to put. Vertex ignored.");
		}else{
			try{
				final Map<String, String> annotationsCopy = vertex.getCopyOfAnnotations();
	
				final String vertexLabel = convertAnnotationsToDotLabel(annotationsCopy);
				final ShapeColor shapeColor = dotConfiguration.getVertexShapeColor(vertex);
	
				final String key = escapeDoubleQuotes(vertex.getIdentifierForExport());
				final String shape = escapeDoubleQuotes(shapeColor.shape);
				final String color = escapeDoubleQuotes(shapeColor.color);
				
				final String vertexString = 
						"\"" + key + "\" [label=\"" + vertexLabel + "\" shape=\"" 
						+ shape + "\" fillcolor=\"" + color + "\"];" + newLine;
				write(vertexString, false);
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
		}else{
			try{
				final Map<String, String> annotationsCopy = edge.getCopyOfAnnotations();

				final String edgeLabel = convertAnnotationsToDotLabel(annotationsCopy);
				final String style = escapeDoubleQuotes(dotConfiguration.getEdgeStyle(edge));
				final String color = escapeDoubleQuotes(dotConfiguration.getEdgeColor(edge));
				
				final AbstractVertex childVertex = edge.getChildVertex();
				final AbstractVertex parentVertex = edge.getParentVertex();
				
				final String childKey = escapeDoubleQuotes(childVertex.getIdentifierForExport());
				final String parentKey = escapeDoubleQuotes(parentVertex.getIdentifierForExport());
				
				final String edgeString = 
						"\"" + childKey + "\" -> \"" + parentKey + "\" [label=\"" + edgeLabel
						+ "\" color=\"" + color + "\" style=\"" + style + "\"];" + newLine;
				write(edgeString, false);
			}catch(Exception e){
				logger.log(Level.WARNING, "Unexpected error. Edge discarded: " + edge, e);
			}
		}
		return true;
	}

	@Override
	public final synchronized Object executeQuery(String query){
		throw new RuntimeException("Graphviz storage does NOT support querying");
	}
	
	public static void main(final String[] args) throws Exception{
		final String dotPath = "/tmp/test.dot";
		final String svgPath = "/tmp/test.svg";
		
		Graph g = new Graph();
		AbstractVertex v0 = new Vertex("www");
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
		
		Graphviz s = new Graphviz();
		if(s.initialize(keyOutput+"="+dotPath + " " + keyFlushAfterBytesCount + "=0")){
	//		s.initializeUnsafe(dotPath, 100, DotConfiguration.getDefaultConfigFilePath(), false, false, System.lineSeparator());
			for(AbstractVertex v : g.vertexSet()){
				s.putVertex(v);
			}
			for(AbstractEdge e : g.edgeSet()){
				s.putEdge(e);
			}
			s.shutdown();
			
			Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", "/usr/local/bin/dot -Tsvg " + dotPath + " > " + svgPath});
			Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", "/usr/bin/open " + svgPath});
		}
	}
}
