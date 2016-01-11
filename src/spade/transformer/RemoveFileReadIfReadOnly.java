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
package spade.transformer;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;

import spade.core.AbstractEdge;
import spade.core.AbstractTransformer;
import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.core.Graph.QueryParams;
import spade.core.Settings;

public class RemoveFileReadIfReadOnly extends AbstractTransformer {
	
	private Pattern ignoreFilesPattern = null;
	
	// argument = true means that every file in the graph should be checked against the pattern in the regex file. otherwise don't check against it
	public boolean initialize(String arguments){
		
		if(arguments != null && arguments.trim().equals("true")){		
			try{
				ignoreFilesPattern = Pattern.compile(FileUtils.readLines(new File(Settings.getProperty("removegarbagefiles_transformer_config_filepath"))).get(0));
				return true;
			}catch(Exception e){
				Logger.getLogger(getClass().getName()).log(Level.WARNING, null, e);
				return false;
			}
		}
		
		return true;
	}

	public Graph putGraph(Graph graph){
		
		Set<AbstractVertex> vertices = null;
		try{
			vertices = (Set<AbstractVertex>)graph.getQueryParam(QueryParams.VERTEX_SET);
		}catch(Exception e){
			Logger.getLogger(getClass().getName()).log(Level.WARNING, "Expected value to be of type Set<AbstractVertex>", e);
		}
		
		Map<AbstractVertex, Set<String>> fileWrittenBy = new HashMap<AbstractVertex, Set<String>>();
		
		for(AbstractEdge edge : graph.edgeSet()){
			AbstractEdge newEdge = createNewWithoutAnnotations(edge);
			if(getAnnotationSafe(newEdge.getSourceVertex(), "subtype").equals("file")
					|| getAnnotationSafe(newEdge.getDestinationVertex(), "subtype").equals("file")){
				String operation = getAnnotationSafe(newEdge, "operation");
				if(operation.equals("write")){
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
			if(getAnnotationSafe(newEdge, "operation").equals("read") 
					&& getAnnotationSafe(newEdge.getDestinationVertex(), "subtype").equals("file")){
				AbstractVertex vertex = newEdge.getDestinationVertex();
				String path = getAnnotationSafe(vertex, "path");
				if(!fileExistsInSet(path, vertices)){ //if file passed as an argument then always log it otherwise check further
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
	
	private boolean fileExistsInSet(String path, Set<AbstractVertex> vertices){
		if(path == null || vertices == null || vertices.size() == 0){
			return false;
		}
		for(AbstractVertex vertex : vertices){
			if(getAnnotationSafe(vertex, "subtype").equals("file")){
				String vpath = getAnnotationSafe(vertex, "path");
				if(path.equals(vpath)){
					return true;
				}
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
