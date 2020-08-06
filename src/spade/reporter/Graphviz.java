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
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 *
 * @author Dawood Tariq
 */
public class Graphviz extends AbstractReporter{

	private static final Logger logger = Logger.getLogger(Graphviz.class.getName());
	private static final Pattern nodePattern = Pattern.compile("\"(.*)\" \\[label=\"(.*)\" shape=\"(\\w*)\" fillcolor=\"(\\w*)\"", Pattern.DOTALL);
	private static final Pattern edgePattern = Pattern.compile("\"(.*)\" -> \"(.*)\" \\[label=\"(.*)\" color=\"(\\w*)\"", Pattern.DOTALL);

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
	private volatile BufferedReader reader = null; 
	private volatile boolean mainRunning = false;
	
	private volatile boolean mainStopped = false;

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

	private final void printStats(boolean force){
		long currentMillis = System.currentTimeMillis();
		if(force || (reportingEnabled && currentMillis - lastReportedAtMillis >= reportingIntervalMillis)){
			log(Level.INFO, "Lines [Overall=" + linesCountOverall + ", Interval=" + linesCountInterval + "]");
			log(Level.INFO, "Vertices [Overall=" + vertexCountOverall + ", Interval=" + vertexCountInterval + "]");
			log(Level.INFO, "Edges [Overall=" + edgeCountOverall + ", Interval=" + edgeCountInterval + "]");
			log(Level.INFO, "Current Buffer Size=" + getBuffer().size());

			linesCountInterval = vertexCountInterval = edgeCountInterval = 0;
			lastReportedAtMillis = System.currentTimeMillis();
		}
	}

	private final Runnable main = new Runnable(){
		@Override
		public void run(){

			try{
				mainRunning = true;
	
				lastReportedAtMillis = System.currentTimeMillis();
	
				String line = null;
				while(!isShutdown()){
					printStats(false);
	
					try{
						line = reader.readLine();
						lineCountIncrement();
					}catch(Exception e){
						log(Level.SEVERE, "Failed to read line", e);
						break;
					}
					if(line == null || line.trim().equals("}")){ // EOF or end of dot object
						log(Level.INFO, "Finished reading input file");
						break;
					}
					try{
						processImportLine(line);
					}catch(Exception e){
						log(Level.SEVERE, "Failed to process line: " + line, e);
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
	public final synchronized boolean launch(String arguments){
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
	
	public final synchronized void launchUnsafe(final BufferedReader reader, final boolean blocking, 
			final boolean closeReaderOnShutdown, final boolean logAll) throws Exception{
		if(isLaunched){
			throw new Exception("Reporter already launched");
		}
		
		this.reader = reader;
		this.closeReaderOnShutdown = closeReaderOnShutdown;
		this.logAll = logAll;
		
		try{
			final Thread thread = new Thread(main, "Graphviz-reporter-thread");
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

	private void processImportLine(String line) throws Exception{
		Matcher nodeMatcher = nodePattern.matcher(line);
		Matcher edgeMatcher = edgePattern.matcher(line);
		if(nodeMatcher.find()){
			final String key = nodeMatcher.group(1);
			final String label = nodeMatcher.group(2);
			
			final Map<String, String> annotations = new HashMap<String, String>();
			
			String[] pairs = label.split("\\\\n");
			for(String pair : pairs){
				String key_value[] = pair.split(":", 2);
				if(key_value.length == 2){
					annotations.put(key_value[0], key_value[1]);
				}
			}
			
			AbstractVertex vertex = new Vertex(key);
			vertex.addAnnotations(annotations);

			vertexCountIncrement();
			putVertex(vertex);
		}else if(edgeMatcher.find()){
			final String childKey = edgeMatcher.group(1);
			final String parentKey = edgeMatcher.group(2);
			final String label = edgeMatcher.group(3);

			final Map<String, String> annotations = new HashMap<String, String>();

			if((label != null) && (label.length() > 2)){
				String[] pairs = label.split("\\\\n");
				for(String pair : pairs){
					String key_value[] = pair.split(":", 2);
					if(key_value.length == 2){
						annotations.put(key_value[0], key_value[1]);
					}
				}
			}

			AbstractEdge edge = new Edge(new Vertex(childKey), new Vertex(parentKey));
			edge.addAnnotations(annotations);
			
			edgeCountIncrement();
			putEdge(edge);
		}
	}

	@Override
	public boolean shutdown(){
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
	
	private final void log(final Level level, final String msg){
		log(level, msg, null);
	}
	
	private final void log(final Level level, final String msg, final Exception exception){
		if(!logAll){
			if(Level.INFO.equals(level) || Level.WARNING.equals(level)){
				return;
			}
		}
		if(exception == null){
			logger.log(level, msg);
		}else{
			logger.log(level, msg, exception);
		}
	}
}
