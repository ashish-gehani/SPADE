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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;

import spade.core.AbstractEdge;
import spade.core.AbstractTransformer;
import spade.core.AbstractVertex;
import spade.core.DigQueryParams;
import spade.core.Graph;
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
	
	public Graph putGraph(Graph graph, DigQueryParams digQueryParams){
		Graph resultGraph = new Graph();
		
		AbstractVertex queriedVertex = null;
		
		if(digQueryParams != null){
			queriedVertex = digQueryParams.getVertex();
		}
		
		for(AbstractEdge edge : graph.edgeSet()){
			String srcFilepath = getAnnotationSafe(edge.getSourceVertex(), "path");
			String dstFilepath = getAnnotationSafe(edge.getDestinationVertex(), "path");
			if(!(fileEqualsVertex(srcFilepath, queriedVertex) || fileEqualsVertex(dstFilepath, queriedVertex))){
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
	
	private boolean fileEqualsVertex(String path, AbstractVertex vertex){
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
	
}
