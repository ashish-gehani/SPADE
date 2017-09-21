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
package spade.core;

import spade.client.QueryMetaData;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static spade.core.AbstractStorage.CHILD_VERTEX_KEY;
import static spade.core.AbstractStorage.PARENT_VERTEX_KEY;
import static spade.core.AbstractStorage.PRIMARY_KEY;


public abstract class AbstractTransformer
{
		
	public String arguments;
	
	public boolean initialize(String arguments){
		return true;
	}
	
	public boolean shutdown() {
       	return true;
    }
	
	public abstract Graph putGraph(Graph graph, QueryMetaData digQueryParams);
	
	public static String getAnnotationSafe(AbstractVertex vertex, String annotation)
	{
		if(vertex != null)
		{
			return getAnnotationSafe(vertex.getAnnotations(), annotation);
		}

			return "";
	}
	
	public static String getAnnotationSafe(AbstractEdge edge, String annotation)
	{
		if(edge != null)
		{
			return getAnnotationSafe(edge.getAnnotations(), annotation);
		}

		return "";
	}
	
	public static String getAnnotationSafe(Map<String, String> annotations, String annotation)
	{
		if(annotations != null)
		{
			String value;
			if((value = annotations.get(annotation)) != null)
			{
				return value;
			}
		}

		return "";
	}
	
	public static AbstractVertex createNewWithoutAnnotations(AbstractVertex vertex, String... annotations)
	{
		AbstractVertex newVertex = new Vertex();
		newVertex.addAnnotations(vertex.getAnnotations());
		if(annotations != null)
		{
			for(String annotation : annotations)
			{
				newVertex.removeAnnotation(annotation);
			}
		}
		newVertex.removeAnnotation(PARENT_VERTEX_KEY);
		newVertex.removeAnnotation(CHILD_VERTEX_KEY);
		newVertex.removeAnnotation(PRIMARY_KEY);
		return newVertex;
	}
	
	public static AbstractEdge createNewWithoutAnnotations(AbstractEdge edge, String... annotations)
	{
		AbstractVertex newChild = createNewWithoutAnnotations(edge.getChildVertex(), annotations);
		AbstractVertex newParent = createNewWithoutAnnotations(edge.getParentVertex(), annotations);
		AbstractEdge newEdge = new Edge(newChild, newParent);
		newEdge.addAnnotations(edge.getAnnotations());
		if(annotations != null)
		{
			for(String annotation : annotations)
			{
				newEdge.removeAnnotation(annotation);
			}
		}
		newEdge.removeAnnotation(PARENT_VERTEX_KEY);
		newEdge.removeAnnotation(CHILD_VERTEX_KEY);
		newEdge.removeAnnotation(PRIMARY_KEY);
		return newEdge;
	}
	
	public static void removeEdges(Graph result, Graph removeFrom, Graph toRemove)
	{
		Set<AbstractEdge> toRemoveEdges = new HashSet<>();
		for(AbstractEdge edge : toRemove.edgeSet())
		{
			toRemoveEdges.add(createNewWithoutAnnotations(edge));
		}
		
		for(AbstractEdge edge : removeFrom.edgeSet())
		{
			AbstractEdge strippedEdge = createNewWithoutAnnotations(edge);
			if(toRemoveEdges.contains(strippedEdge))
			{
				continue;
			}
			result.putVertex(strippedEdge.getChildVertex());
			result.putVertex(strippedEdge.getParentVertex());
			result.putEdge(strippedEdge);
		}
	}
	
}
