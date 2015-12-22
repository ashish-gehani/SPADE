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

import java.util.Map;

import spade.core.AbstractEdge;
import spade.core.AbstractTransformer;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Graph;
import spade.core.Settings;
import spade.core.Vertex;

public class BEEP extends AbstractTransformer {
	
	private static final String SRC_VERTEX_ID = "SRC_VERTEX_ID";
    private static final String DST_VERTEX_ID = "DST_VERTEX_ID";
    private static final String ID_STRING = Settings.getProperty("storage_identifier");
    
	@Override
	public Graph putGraph(Graph graph) {
		Graph resultGraph = new Graph();
		for(AbstractEdge edge : graph.edgeSet()){
			if(getAnnotationSafe(edge.getSourceVertex(), "subtype").equals("memory") || getAnnotationSafe(edge.getDestinationVertex(), "subtype").equals("memory")
					|| isFileSTDIO(edge.getSourceVertex()) || isFileSTDIO(edge.getDestinationVertex())
					|| getAnnotationSafe(edge, "operation").equals("rename") || getAnnotationSafe(edge, "operation").equals("rename_oldpath")
					|| getAnnotationSafe(edge, "operation").equals("update")
					|| getAnnotationSafe(edge, "operation").equals("unit")
					|| isFilePathUnkown(edge.getSourceVertex()) || isFilePathUnkown(edge.getDestinationVertex())){
				continue;
			}
			AbstractEdge newEdge = createNewWithoutAnnotations(edge, "unit", SRC_VERTEX_ID, DST_VERTEX_ID, ID_STRING, "version", "time", "size");
			if(newEdge != null && newEdge.getSourceVertex() != null && newEdge.getDestinationVertex() != null){
				if(!graphContainsVertex(resultGraph, newEdge.getSourceVertex())){
					resultGraph.putVertex(newEdge.getSourceVertex());
				}
				if(!graphContainsVertex(resultGraph, newEdge.getDestinationVertex())){
					resultGraph.putVertex(newEdge.getDestinationVertex());
				}
				if(!graphContainsEdge(resultGraph, newEdge)){
					resultGraph.putEdge(newEdge);
				}	
			}
		}
		return resultGraph;
	}
	
	private boolean isFileSTDIO(AbstractVertex vertex){
		if(getAnnotationSafe(vertex, "subtype").equals("file")){
			String path = getAnnotationSafe(vertex, "path");
			if(path.startsWith("/unknown")){
				String[] toks = path.split("_");
				if(toks.length == 2){
					try{
						if(Integer.parseInt(toks[1]) < 3){
							return true;
						}
					}catch(Exception e){
						//no need to catch this exception
					}
				}
			}
		}
		return false;
	}
	
	private boolean isFilePathUnkown(AbstractVertex vertex){
		String path = null;
		if((path = getAnnotationSafe(vertex, "path")) != null){
			return path.startsWith("/unknown/");
		}
		return false;
	}
	
	private String getAnnotationSafe(AbstractVertex vertex, String annotation){
		if(vertex != null){
			return getAnnotationSafe(vertex.getAnnotations(), annotation);
		}
		return "";
	}
	
	private String getAnnotationSafe(AbstractEdge edge, String annotation){
		if(edge != null){
			return getAnnotationSafe(edge.getAnnotations(), annotation);
		}
		return "";
	}
	
	private String getAnnotationSafe(Map<String, String> annotations, String annotation){
		if(annotations != null){
			String value = null;
			if((value = annotations.get(annotation)) != null){
				return value;
			}
		}
		return "";
	}
	
	private AbstractVertex createNewWithoutAnnotations(AbstractVertex vertex, String... annotations){
		AbstractVertex newVertex = new Vertex();
		newVertex.addAnnotations(vertex.getAnnotations());
		if(annotations != null){
			for(String annotation : annotations){
				newVertex.removeAnnotation(annotation);
			}
		}
		return newVertex;
	}
	
	private AbstractEdge createNewWithoutAnnotations(AbstractEdge edge, String... annotations){
		AbstractVertex newSource = createNewWithoutAnnotations(edge.getSourceVertex(), annotations);
		AbstractVertex newDestination = createNewWithoutAnnotations(edge.getDestinationVertex(), annotations);
		AbstractEdge newEdge = new Edge(newSource, newDestination);
		newEdge.addAnnotations(edge.getAnnotations());
		if(annotations != null){
			for(String annotation : annotations){
				newEdge.removeAnnotation(annotation);
			}
		}
		return newEdge;
	}
	
	private boolean graphContainsVertex(Graph graph, AbstractVertex vertex){
		return graph.vertexSet().contains(vertex);
	}
	
	private boolean graphContainsEdge(Graph graph, AbstractEdge edge){
		return graph.edgeSet().contains(edge);
	}
}
