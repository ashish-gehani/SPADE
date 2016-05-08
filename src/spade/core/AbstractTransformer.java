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
package spade.core;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import spade.client.QueryParameters;

public abstract class AbstractTransformer {
	
	private static final String SRC_VERTEX_ID = "SRC_VERTEX_ID";
	private static final String DST_VERTEX_ID = "DST_VERTEX_ID";
	private static final String ID_STRING = Settings.getProperty("storage_identifier");
		
	public String arguments;
	
	public boolean initialize(String arguments){
		return true;
	}
	
	public boolean shutdown() {
       	return true;
    }
	
	public abstract Graph putGraph(Graph graph, QueryParameters digQueryParams);
	
	public static String getAnnotationSafe(AbstractVertex vertex, String annotation){
		if(vertex != null){
			return getAnnotationSafe(vertex.getAnnotations(), annotation);
		}
		return "";
	}
	
	public static String getAnnotationSafe(AbstractEdge edge, String annotation){
		if(edge != null){
			return getAnnotationSafe(edge.getAnnotations(), annotation);
		}
		return "";
	}
	
	public static String getAnnotationSafe(Map<String, String> annotations, String annotation){
		if(annotations != null){
			String value = null;
			if((value = annotations.get(annotation)) != null){
				return value;
			}
		}
		return "";
	}
	
	public static AbstractVertex createNewWithoutAnnotations(AbstractVertex vertex, String... annotations){
		AbstractVertex newVertex = new Vertex();
		newVertex.addAnnotations(vertex.getAnnotations());
		if(annotations != null){
			for(String annotation : annotations){
				newVertex.removeAnnotation(annotation);
			}
		}
		newVertex.removeAnnotation(DST_VERTEX_ID);
		newVertex.removeAnnotation(SRC_VERTEX_ID);
		newVertex.removeAnnotation(ID_STRING);
		return newVertex;
	}
	
	public static AbstractEdge createNewWithoutAnnotations(AbstractEdge edge, String... annotations){
		AbstractVertex newSource = createNewWithoutAnnotations(edge.getSourceVertex(), annotations);
		AbstractVertex newDestination = createNewWithoutAnnotations(edge.getDestinationVertex(), annotations);
		AbstractEdge newEdge = new Edge(newSource, newDestination);
		newEdge.addAnnotations(edge.getAnnotations());
		if(annotations != null){
			for(String annotation : annotations){
				newEdge.removeAnnotation(annotation);
			}
		}
		newEdge.removeAnnotation(DST_VERTEX_ID);
		newEdge.removeAnnotation(SRC_VERTEX_ID);
		newEdge.removeAnnotation(ID_STRING);
		return newEdge;
	}
	
	public static void removeEdges(Graph result, Graph removeFrom, Graph toRemove){
		Set<AbstractEdge> toRemoveEdges = new HashSet<AbstractEdge>();
		for(AbstractEdge edge : toRemove.edgeSet()){
			toRemoveEdges.add(createNewWithoutAnnotations(edge));
		}
		
		for(AbstractEdge edge : removeFrom.edgeSet()){
			AbstractEdge strippedEdge = createNewWithoutAnnotations(edge);
			if(toRemoveEdges.contains(strippedEdge)){
				continue;
			}
			result.putVertex(strippedEdge.getSourceVertex());
			result.putVertex(strippedEdge.getDestinationVertex());
			result.putEdge(strippedEdge);
		}
	}
	
}
