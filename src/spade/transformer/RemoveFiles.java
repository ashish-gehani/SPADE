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
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;

import spade.core.AbstractEdge;
import spade.core.AbstractTransformer;
import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.core.Graph.QueryParams;
import spade.core.Settings;

public class RemoveFiles extends AbstractTransformer{
	
	private Pattern filesToRemovePattern = null;
	
	public boolean initialize(String arguments){
		
		try{
			filesToRemovePattern = Pattern.compile(FileUtils.readLines(new File(Settings.getProperty("removefiles_transformer_config_filepath"))).get(0));
			return true;
		}catch(Exception e){
			Logger.getLogger(getClass().getName()).log(Level.WARNING, null, e);
			return false;
		}
		
	}
	
	public Graph putGraph(Graph graph){
		Graph resultGraph = new Graph();
		
		Set<AbstractVertex> queryVertices = null;
		
		try{
			queryVertices = (Set<AbstractVertex>)graph.getQueryParam(QueryParams.VERTEX_SET);
		}catch(Exception e){
			Logger.getLogger(getClass().getName()).log(Level.WARNING, "Expected value to be of type Set<AbstractVertex>", e);
		}
		
		for(AbstractEdge edge : graph.edgeSet()){
			String srcFilepath = getAnnotationSafe(edge.getSourceVertex(), "path");
			String dstFilepath = getAnnotationSafe(edge.getDestinationVertex(), "path");
			if(!(fileExistsInSet(srcFilepath, queryVertices) || fileExistsInSet(dstFilepath, queryVertices))){
				if(isFileToBeRemoved(srcFilepath) 
					|| isFileToBeRemoved(dstFilepath)){
					continue;
				}
			}
			AbstractEdge newEdge = createNewWithoutAnnotations(edge);
			if(newEdge != null && newEdge.getSourceVertex() != null && newEdge.getDestinationVertex() != null){
				resultGraph.putVertex(newEdge.getSourceVertex());
				resultGraph.putVertex(newEdge.getDestinationVertex());
				resultGraph.putEdge(newEdge);
			}
		}
		return resultGraph;
	}
	
	private boolean isFileToBeRemoved(String path){
		if(filesToRemovePattern != null && path != null){
			return filesToRemovePattern.matcher(path).find();
		}
		return false;
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
	
}
