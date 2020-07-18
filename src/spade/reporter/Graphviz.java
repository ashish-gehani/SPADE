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
package spade.reporter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;

import spade.core.AbstractEdge;
import spade.core.AbstractReporter;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Graph;
import spade.core.Settings;
import spade.core.Vertex;
import spade.utility.HelperFunctions;
import spade.utility.LoadableField;
import spade.utility.LoadableFieldHelper;

/**
 *
 * @author Dawood Tariq
 */
public class Graphviz extends AbstractReporter{

	private static final Logger logger = Logger.getLogger(Graphviz.class.getName());

	private static final String keyInput = "input", keyReportingIntervalMillis = "reportingIntervalMillis";

	@LoadableField(name = keyInput, optional = false)
	private final String filePath = null;

	@LoadableField(name = keyReportingIntervalMillis, optional = false, min = Long.MIN_VALUE, max = Long.MAX_VALUE)
	private final Long reportingIntervalMillis = null;

	private boolean reportingEnabled = false;

	///////////////////////////////////////////////////////

	private long lastReportedAtMillis = 0;

	private final Object shutdownLock = new Object();
	private volatile boolean shutdown = false;
	private volatile BufferedReader reader = null;
	private volatile boolean mainRunning = false;

	private long linesCountInterval = 0;
	private long linesCountOverall = 0;

	private long vertexCountInterval = 0;
	private long vertexCountOverall = 0;

	private long edgeCountInterval = 0;
	private long edgeCountOverall = 0;

	private final void lineCountIncrement(){
		linesCountInterval++;
		linesCountOverall++;
	}

	private final void vertexCountIncrement(){
		vertexCountInterval++;
		vertexCountOverall++;
	}

	private final void edgeCountIncrement(){
		edgeCountInterval++;
		edgeCountOverall++;
	}

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

	private final void printStats(boolean force){
		long currentMillis = System.currentTimeMillis();
		if(force || (reportingEnabled && currentMillis - lastReportedAtMillis >= reportingIntervalMillis)){
			logger.log(Level.INFO, "Lines [Overall=" + linesCountOverall + ", Interval=" + linesCountInterval + "]");
			logger.log(Level.INFO,
					"Vertices [Overall=" + vertexCountOverall + ", Interval=" + vertexCountInterval + "]");
			logger.log(Level.INFO, "Edges [Overall=" + edgeCountOverall + ", Interval=" + edgeCountInterval + "]");
			logger.log(Level.INFO, "Current Buffer Size=" + getBuffer().size());

			linesCountInterval = vertexCountInterval = edgeCountInterval = 0;
			lastReportedAtMillis = System.currentTimeMillis();
		}
	}

	private final Runnable main = new Runnable(){

		@Override
		public void run(){

			mainRunning = true;

			lastReportedAtMillis = System.currentTimeMillis();

			String line = null;
			while(!isShutdown()){

				printStats(false);

				try{
					line = reader.readLine();
					lineCountIncrement();
				}catch(Exception e){
					logger.log(Level.SEVERE, "Failed to read line. Shutting down.", e);
					break;
				}
				if(line == null){ // EOF
					logger.log(Level.INFO, "Finished reading input file: " + filePath);
					break;
				}
				try{
					processImportLine(line);
				}catch(Exception e){
					logger.log(Level.SEVERE, "Failed to process line: " + line, e);
					logger.log(Level.SEVERE, "Shutting down");
					break;
				}
			}

			mainRunning = false;
			logger.log(Level.INFO, "Exited main thread");
		}

	};

	@Override
	public boolean launch(String arguments){
		try{
			final Map<String, String> map = HelperFunctions.parseKeyValuePairsFrom(arguments,
					Settings.getDefaultConfigFilePath(this.getClass()), null);

			LoadableFieldHelper.loadAllLoadableFieldsFromMap(this, map);

			logger.log(Level.INFO, "Arguments: " + LoadableFieldHelper.allLoadableFieldsToString(this));

			if(this.reportingIntervalMillis > 0){
				this.reportingEnabled = true;
			}

		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to initialize from arguments and config file", e);
			return false;
		}

		if(HelperFunctions.isNullOrEmpty(filePath)){
			logger.log(Level.SEVERE,
					"Invalid value for '" + keyInput + "'. Null/Empty: '" + filePath + "'. Must be a readable file.");
			return false;
		}

		try{
			final File file = new File(filePath);
			if(!file.exists()){
				logger.log(Level.SEVERE,
						"Invalid value for '" + keyInput + "'. No file at path: '" + filePath + "'. Must exist.");
				return false;
			}

			if(file.isDirectory()){
				logger.log(Level.SEVERE, "Invalid value for '" + keyInput + "'. Not a file at path: '" + filePath
						+ "'. Must be a file.");
				return false;
			}

			if(!file.canRead()){
				logger.log(Level.SEVERE, "Invalid value for '" + keyInput + "'. Path not readable: '" + filePath
						+ "'. Must be a readable file.");
				return false;
			}
		}catch(Exception e){
			logger.log(Level.SEVERE,
					"Failed to validate value for '" + keyInput + "' as a readable existing file: " + filePath, e);
			return false;
		}

		try{
			this.reader = new BufferedReader(new FileReader(new File(filePath)));
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to create file reader: " + filePath, e);
			return false;
		}

		try{
			final Thread thread = new Thread(main, "Graphviz-reporter-thread");
			thread.start();
			return true;
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to start main thread", e);
			shutdown();
			return false;
		}
	}

	private void processImportLine(String line) throws Exception{
		Matcher nodeMatcher = Graph.nodePattern.matcher(line);
		Matcher edgeMatcher = Graph.edgePattern.matcher(line);
		if(nodeMatcher.find()){
			String key = nodeMatcher.group(1);
			String label = nodeMatcher.group(2);
			AbstractVertex vertex = new Vertex(key);

			vertex.addAnnotation("type", "Vertex");

			String[] pairs = label.split("\\\\n");
			for(String pair : pairs){
				String key_value[] = pair.split(":", 2);
				if(key_value.length == 2){
					vertex.addAnnotation(key_value[0], key_value[1]);
				}
			}

			vertexCountIncrement();
			putVertex(vertex);
		}else if(edgeMatcher.find()){
			String childkey = edgeMatcher.group(1);
			String dstkey = edgeMatcher.group(2);
			String label = edgeMatcher.group(3);

			AbstractEdge edge = new Edge(new Vertex(childkey), new Vertex(dstkey));
			edge.addAnnotation("type", "Edge");

			if((label != null) && (label.length() > 2)){
				String[] pairs = label.split("\\\\n");
				for(String pair : pairs){
					String key_value[] = pair.split(":", 2);
					if(key_value.length == 2){
						edge.addAnnotation(key_value[0], key_value[1]);
					}
				}
			}

			edgeCountIncrement();
			putEdge(edge);
		}
	}

	@Override
	public boolean shutdown(){
		if(!isShutdown()){
			setShutdown(true);

			logger.log(Level.INFO, "Waiting for main thread to exit... ");
			while(this.mainRunning){
				HelperFunctions.sleepSafe(100);
			}

			if(this.reader != null){
				try{
					this.reader.close();
				}catch(Exception e){
					logger.log(Level.WARNING, "Failed to close file reader", e);
				}
				this.reader = null;
			}

			printStats(true);
		}
		return true;
	}
}
