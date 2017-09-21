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

import spade.client.QueryMetaData;
import spade.core.AbstractEdge;
import spade.core.AbstractTransformer;
import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.reporter.audit.OPMConstants;
import spade.utility.CommonFunctions;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static spade.core.AbstractStorage.DIRECTION_ANCESTORS;
import static spade.core.AbstractStorage.DIRECTION_DESCENDANTS;

public class TemporalTraversal extends AbstractTransformer
{
	
	private static final Logger logger = Logger.getLogger(TemporalTraversal.class.getName());

    private String annotationName;
    
    //must specify the name of an annotation
    public boolean initialize(String arguments)
	{
    	Map<String, String> argumentsMap = CommonFunctions.parseKeyValPairs(arguments);
    	if("timestamp".equals(argumentsMap.get("order")))
    	{
    		annotationName = OPMConstants.EDGE_TIME;
    	}
    	else
		{
    		annotationName = OPMConstants.EDGE_EVENT_ID;
    	}

    	return true;
    }

	public Graph putGraph(Graph graph, QueryMetaData queryMetaData)
	{
		String direction = queryMetaData.getDirection();
		if(direction == null)
		{
			logger.log(Level.SEVERE, "Direction cannot be null");

			return graph;
		}
		else if((!DIRECTION_ANCESTORS.startsWith(direction) && !DIRECTION_DESCENDANTS.startsWith(direction)))
		{
			logger.log(Level.SEVERE, "Direction cannot be '"+direction+"' for this transformer");

			return graph;
		}
		
		Graph resultGraph = new Graph();
		AbstractVertex queriedVertex = createNewWithoutAnnotations(queryMetaData.getRootVertex());
				
		if(DIRECTION_ANCESTORS.startsWith(direction))
		{
			Double maxValue = Double.MIN_VALUE;
			for(AbstractEdge edge : graph.edgeSet())
			{
				AbstractEdge newEdge = createNewWithoutAnnotations(edge);
				if(newEdge.getChildVertex().equals(queriedVertex) || newEdge.getParentVertex().equals(queriedVertex))
				{
					try
					{
						Double value = Double.parseDouble(getAnnotationSafe(newEdge, annotationName));
						if(value > maxValue)
						{
							maxValue = value;
						}
					}
					catch(Exception e)
					{
						logger.log(Level.SEVERE, "Failed to parse where "+annotationName+"='"+getAnnotationSafe(newEdge, annotationName)+"'");
					}
				}
			} 
			
			for(AbstractEdge edge : graph.edgeSet())
			{
				AbstractEdge newEdge = createNewWithoutAnnotations(edge);
				boolean add = true;
				try
				{
					Double value = Double.parseDouble(getAnnotationSafe(newEdge, annotationName));
					if(value > maxValue)
					{
						add = false;
					}
				}
				catch(Exception e)
				{
					logger.log(Level.SEVERE, "Failed to parse where "+annotationName+"='"+getAnnotationSafe(newEdge, annotationName)+"'");
				}
				if(add)
				{
					resultGraph.putVertex(newEdge.getChildVertex());
					resultGraph.putVertex(newEdge.getParentVertex());
					resultGraph.putEdge(newEdge);
				}
			}
		}
		else if(DIRECTION_DESCENDANTS.startsWith(direction))
		{
			Double minValue = Double.MAX_VALUE;
			for(AbstractEdge edge : graph.edgeSet())
			{
				AbstractEdge newEdge = createNewWithoutAnnotations(edge);
				if(newEdge.getChildVertex().equals(queriedVertex) || newEdge.getParentVertex().equals(queriedVertex))
				{
					try
					{
						Double value = Double.parseDouble(getAnnotationSafe(newEdge, annotationName));
						if(value < minValue)
						{
							minValue = value;
						}
					}
					catch(Exception e)
					{
						logger.log(Level.SEVERE, "Failed to parse where "+annotationName+"='"+getAnnotationSafe(newEdge, annotationName)+"'");
					}
				}
			} 
			
			for(AbstractEdge edge : graph.edgeSet())
			{
				AbstractEdge newEdge = createNewWithoutAnnotations(edge);
				boolean add = true;
				try
				{
					Double value = Double.parseDouble(getAnnotationSafe(newEdge, annotationName));
					if(value < minValue)
					{
						add = false;
					}
				}
				catch(Exception e)
				{
					logger.log(Level.SEVERE, "Failed to parse where "+annotationName+"='"+getAnnotationSafe(newEdge, annotationName)+"'");
				}
				if(add)
				{
					resultGraph.putVertex(newEdge.getChildVertex());
					resultGraph.putVertex(newEdge.getParentVertex());
					resultGraph.putEdge(newEdge);
				}
			}
		}
		return resultGraph;
	}
}
