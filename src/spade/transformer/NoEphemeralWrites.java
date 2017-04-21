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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import spade.client.QueryParameters;
import spade.core.AbstractEdge;
import spade.core.AbstractTransformer;
import spade.core.AbstractVertex;
import spade.core.Graph;

public class NoEphemeralWrites extends AbstractTransformer {

	public Graph putGraph(Graph graph, QueryParameters digQueryParams){
		
		Map<AbstractVertex, Set<String>> fileReadBy = new HashMap<AbstractVertex, Set<String>>();
		
		for(AbstractEdge edge : graph.edgeSet()){
			
			AbstractEdge newEdge = createNewWithoutAnnotations(edge);
		
			if(getAnnotationSafe(newEdge.getChildVertex(), "subtype").equals("file")
					|| getAnnotationSafe(newEdge.getParentVertex(), "subtype").equals("file")){
				String operation = getAnnotationSafe(newEdge, "operation");
				if(operation.equals("read") || operation.equals("readv") || operation.equals("pread64")){
					if(fileReadBy.get(newEdge.getParentVertex()) == null){
						fileReadBy.put(newEdge.getParentVertex(), new HashSet<String>());
					}
					fileReadBy.get(newEdge.getParentVertex()).add(getAnnotationSafe(newEdge.getChildVertex(), "pid"));
				}
			}			
		}
	
		Graph resultGraph = new Graph();
		
		for(AbstractEdge edge : graph.edgeSet()){
			AbstractEdge newEdge = createNewWithoutAnnotations(edge);
			if((getAnnotationSafe(newEdge, "operation").equals("writev") || getAnnotationSafe(newEdge, "operation").equals("write") || 
					getAnnotationSafe(newEdge, "operation").equals("pwrite64") || getAnnotationSafe(newEdge, "operation").equals("rename_write") || getAnnotationSafe(newEdge, "operation").equals("link_write")
					|| getAnnotationSafe(newEdge, "operation").equals("symlink_write"))
					&& getAnnotationSafe(newEdge.getChildVertex(), "subtype").equals("file")){
				AbstractVertex vertex = newEdge.getChildVertex();
				if((fileReadBy.get(vertex) == null) || (fileReadBy.get(vertex).size() == 1 
						&& fileReadBy.get(vertex).toArray()[0].equals(getAnnotationSafe(newEdge.getParentVertex(), "pid")))){
					continue; 
				}
			}		
			resultGraph.putVertex(newEdge.getChildVertex());
			resultGraph.putVertex(newEdge.getParentVertex());
			resultGraph.putEdge(newEdge);
		}
		
		return resultGraph;
	}
	
}
