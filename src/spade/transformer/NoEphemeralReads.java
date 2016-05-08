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
package spade.transformer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import spade.client.QueryParameters;
import spade.core.AbstractEdge;
import spade.core.AbstractTransformer;
import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.core.Settings;
import spade.utility.CommonFunctions;
import spade.utility.FileUtility;

public class NoEphemeralReads extends AbstractTransformer {
	
	private Pattern ignoreFilesPattern = null;
	
	// limited = true means that only matching files in the graph should be checked for ephemeral reads.
	// limited = false means that all files in the graph should be checked for ephemeral reads.
	public boolean initialize(String arguments){
		Map<String, String> argumentsMap = CommonFunctions.parseKeyValPairs(arguments);
		if("false".equals(argumentsMap.get("limited"))){
			return true;
		}else{
			try{
				String filepath = Settings.getDefaultConfigFilePath(this.getClass());
				ignoreFilesPattern = FileUtility.constructRegexFromFile(filepath);
				if(ignoreFilesPattern == null){
					throw new Exception("Regex read from file '"+filepath+"' cannot be null");
				}
				return true;
			}catch(Exception e){
				Logger.getLogger(getClass().getName()).log(Level.WARNING, null, e);
				return false;
			}
		}
	}

	public Graph putGraph(Graph graph, QueryParameters digQueryParams){
		
		AbstractVertex queriedVertex = null;
		
		if(digQueryParams != null){
			queriedVertex = digQueryParams.getVertex();
		}
		
		Map<AbstractVertex, Set<String>> fileWrittenBy = new HashMap<AbstractVertex, Set<String>>();
		
		for(AbstractEdge edge : graph.edgeSet()){
			AbstractEdge newEdge = createNewWithoutAnnotations(edge);
			if(getAnnotationSafe(newEdge.getSourceVertex(), "subtype").equals("file")
					|| getAnnotationSafe(newEdge.getDestinationVertex(), "subtype").equals("file")){
				String operation = getAnnotationSafe(newEdge, "operation");
				if(operation.equals("write") || operation.equals("writev") || operation.equals("pwrite64") || operation.equals("rename_write") || operation.equals("link_write") || operation.equals("symlink_write")){
					if(fileWrittenBy.get(newEdge.getSourceVertex()) == null){
						fileWrittenBy.put(newEdge.getSourceVertex(), new HashSet<String>());
					}
					fileWrittenBy.get(newEdge.getSourceVertex()).add(getAnnotationSafe(newEdge.getDestinationVertex(), "pid"));
				}
			}
		}
		
		Graph resultGraph = new Graph();
		
		for(AbstractEdge edge : graph.edgeSet()){
			AbstractEdge newEdge = createNewWithoutAnnotations(edge);
			if((getAnnotationSafe(newEdge, "operation").equals("read") || getAnnotationSafe(newEdge, "operation").equals("readv") || 
					getAnnotationSafe(newEdge, "operation").equals("pread64"))
					&& getAnnotationSafe(newEdge.getDestinationVertex(), "subtype").equals("file")){
				AbstractVertex vertex = newEdge.getDestinationVertex();
				String path = getAnnotationSafe(vertex, "path");
				if(!pathEqualsVertex(path, queriedVertex)){ //if file passed as an argument then always log it otherwise check further
					if(isPathInIgnoreFilesPattern(path)){ //if file is not in ignore list then always log it otherwise check further
						if((fileWrittenBy.get(vertex) == null) || (fileWrittenBy.get(vertex).size() == 1 
								&& fileWrittenBy.get(vertex).toArray()[0].equals(getAnnotationSafe(newEdge.getSourceVertex(), "pid")))){
							continue;
						}
					}
				}
			}
		
			resultGraph.putVertex(newEdge.getSourceVertex());
			resultGraph.putVertex(newEdge.getDestinationVertex());
			resultGraph.putEdge(newEdge);			
		}
		
		return resultGraph;
		
	}
	
	private boolean pathEqualsVertex(String path, AbstractVertex vertex){
		if(path == null || vertex == null){
			return false;
		}
		if(getAnnotationSafe(vertex, "subtype").equals("file")){
			String vpath = getAnnotationSafe(vertex, "path");
			if(path.equals(vpath)){
				return true;
			}
		}
		return false;
	}
	
	private boolean isPathInIgnoreFilesPattern(String path){
		if(ignoreFilesPattern == null){
			return true;
		}
		if(path != null){
			Matcher filepathMatcher = ignoreFilesPattern.matcher(path);
			return filepathMatcher.find();
		}
		return false;
	}
	
}
