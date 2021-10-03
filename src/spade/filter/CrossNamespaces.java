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
package spade.filter;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

import spade.core.AbstractEdge;
import spade.core.AbstractFilter;
import spade.core.AbstractVertex;
import spade.core.Settings;
import spade.filter.crossnamespaces.CrossMatcher;
import spade.utility.ArgumentFunctions;
import spade.utility.HelperFunctions;

public class CrossNamespaces extends AbstractFilter{

	private static final Logger logger = Logger.getLogger(CrossNamespaces.class.getName());
	
	private static final String
		keyOutput = "output",
		keyPretty = "pretty";

	private boolean pretty;
	private String outputPath;
	private BufferedWriter outputWriter;

	private final CrossMatcher matcher = new CrossMatcher();

	@Override
	public boolean initialize(final String arguments){
		final String configFilePath = Settings.getDefaultConfigFilePath(this.getClass());
		try{
			final Map<String, String> configMap = HelperFunctions.parseKeyValuePairsFrom(arguments, new String[]{configFilePath});

			outputPath = ArgumentFunctions.mustParseWritableFilePath(keyOutput, configMap);
			outputWriter = new BufferedWriter(new FileWriter(outputPath));
			pretty = ArgumentFunctions.mustParseBoolean(keyPretty, configMap);

			matcher.initialize(this, configMap);

			logger.log(Level.INFO, "Arguments: "
					+ "{0}={1}, {2}={3}",
					new Object[]{
							keyOutput, outputPath
							, keyPretty, pretty
					});
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to read config file: " + configFilePath, e);
			return false;
		}
		return true;
	}
	
	@Override
	public boolean shutdown(){
		matcher.shutdown();
		if(outputWriter != null){
			try{
				outputWriter.close();
			}catch(Exception e){
				logger.log(Level.WARNING, "Failed to close file. Buffered data at the tail might be lost", e);
			}
		}
		return true;
	}
	
	@Override
	public void putVertex(final AbstractVertex vertex){
		putInNextFilter(vertex);
	}

	@Override
	public void putEdge(final AbstractEdge edge){
		putInNextFilter(edge);
		matcher.check(edge);
	}

	private final JSONObject createJSONObjectFromMap(final Map<String, String> map) throws Exception{
		final JSONObject object = new JSONObject();
		for(final String key : map.keySet()){
			final String value = map.get(key);
			if(value != null){
				object.put(key, value);
			}
		}
		return object;
	}

	private final JSONObject createJSONEvent(
			final long eventId, 
			final TreeMap<String, String> matchedArtifactAnnotations,
			final HashSet<TreeMap<String, String>> completeArtifactAnnotationsSet,
			final HashSet<TreeMap<String, String>> completeOtherWriters,
			final AbstractVertex readerProcessVertex,
			final AbstractEdge readEdge) throws Exception{
		final JSONObject eventObject = new JSONObject();
		eventObject.put("cross-namespace-event-id", String.valueOf(eventId));
		eventObject.put("read-edge", createJSONObjectFromMap(readEdge.getCopyOfAnnotations()));
		eventObject.put("artifact", createJSONObjectFromMap(matchedArtifactAnnotations));

		final JSONArray artifactsArray = new JSONArray();
		for(final TreeMap<String, String> tmp : completeArtifactAnnotationsSet){
			artifactsArray.put(createJSONObjectFromMap(tmp));
		}
		eventObject.put("artifacts", artifactsArray);

		eventObject.put("reader", createJSONObjectFromMap(readerProcessVertex.getCopyOfAnnotations()));
		
		final JSONArray writerArray = new JSONArray();
		for(final TreeMap<String, String> tmp : completeOtherWriters){
			writerArray.put(createJSONObjectFromMap(tmp));
		}
		eventObject.put("writers", writerArray);
		return eventObject;
	}

	private final void writeJSONEvent(final JSONObject eventObject) throws Exception{
		final String eventString;
		if(pretty){
			eventString = eventObject.toString(2);
		}else{
			eventString = eventObject.toString();
		}
		this.outputWriter.write(eventString + "\n");
	}

	public final void outputEvent(final long eventId, 
			final TreeMap<String, String> matchedArtifactAnnotations,
			final HashSet<TreeMap<String, String>> completeArtifactAnnotationsSet,
			final HashSet<TreeMap<String, String>> completeOtherWriters,
			final AbstractVertex readerProcessVertex,
			final AbstractEdge readEdge){
		final JSONObject eventObject;
		try{
			eventObject = createJSONEvent(eventId, matchedArtifactAnnotations, 
					completeArtifactAnnotationsSet, completeOtherWriters, readerProcessVertex, readEdge);
		}catch(Exception e){
			logger.log(Level.WARNING, "Failed to create event as JSON object", e);
			return;
		}
		try{
			writeJSONEvent(eventObject);
		}catch(Exception e){
			logger.log(Level.WARNING, "Failed to write event", e);
			return;
		}
	}

}