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

import org.apache.commons.io.FileUtils;
import spade.client.QueryMetaData;
import spade.core.AbstractEdge;
import spade.core.AbstractTransformer;
import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.core.Settings;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Aggregation extends AbstractTransformer
{
	private final static Logger logger = Logger.getLogger(Aggregation.class.getName());
	
	private Map<String, String> annotationAggregationFunction = new HashMap<>();
	private Map<String, String> annotationNewAnnotation = new HashMap<>();
		
	//read file with every line containing <originalAnnotationName newAnnotationName aggregationFunction>
	public boolean initialize(String arguments)
    {
		String filepath = Settings.getDefaultConfigFilePath(this.getClass());
		try
        {
			List<String> lines = FileUtils.readLines(new File(filepath));
			int lineNumber = 0;
			for(String line : lines)
			{
				lineNumber++;
				line = line.trim();
				if(line.startsWith("#") || line.isEmpty())
				{
					continue;
				}
				String tokens[] = line.split(",");
				if(tokens.length == 3)
				{
					String annotationKey = tokens[0].trim();
					String newAnnotationKey = tokens[1].trim();
					String aggregationFunction = tokens[2].trim();
					annotationAggregationFunction.put(annotationKey, aggregationFunction);
					annotationNewAnnotation.put(annotationKey, newAnnotationKey);
				}
				else
				    {
					logger.log(Level.SEVERE, "No. " + lineNumber + " malformed in file '"+filepath+"'");
				}
			}

			return true;
		}
		catch (Exception exception)
        {
			logger.log(Level.SEVERE, "Unable to read file '"+filepath+"'", exception);

			return false;
		}
	}
	
	@Override
	public Graph putGraph(Graph graph, QueryMetaData queryMetaData)
	{
		String[] annotationsToRemove = annotationAggregationFunction.keySet().toArray(new String[]{});
		Map<AbstractEdge, Map<String, List<String>>> edgeAnnotationSet = new HashMap<>();
		Map<AbstractVertex, Map<String, List<String>>> vertexAnnotationSet = new HashMap<>();
		Map<AbstractVertex, AbstractVertex> oldNewVertices = new HashMap<>(); //to avoid double work when same vertex again
		
		//build map which contains the vertex -> annotation -> list of values (for all vertices and their annotations)
		for(AbstractVertex originalVertex : graph.vertexSet())
		{
			AbstractVertex newVertex = createNewWithoutAnnotations(originalVertex, annotationsToRemove);
			if(vertexAnnotationSet.get(newVertex) == null)
			{
				vertexAnnotationSet.put(newVertex, new HashMap<>());
			}
			for(String annotationToRemove : annotationsToRemove)
			{
                //annotations actually exists for the vertex. only then.
				if(originalVertex.getAnnotation(annotationToRemove) != null)
				{
					if(vertexAnnotationSet.get(newVertex).get(annotationToRemove) == null)
					{
						vertexAnnotationSet.get(newVertex).put(annotationToRemove, new ArrayList<>());
					}
					vertexAnnotationSet.get(newVertex).get(annotationToRemove).add(getAnnotationSafe(originalVertex, annotationToRemove));
				}
			}
		}
		
		//build map which contains the edge -> annotation -> list of values (for all edges and their annotations)
		for(AbstractEdge originalEdge : graph.edgeSet())
		{
			AbstractEdge newEdge = createNewWithoutAnnotations(originalEdge, annotationsToRemove);
			if(edgeAnnotationSet.get(newEdge) == null)
			{
				edgeAnnotationSet.put(newEdge, new HashMap<>());
			}
			for(String annotationToRemove : annotationsToRemove)
			{
                //annotations actually exists for the vertex. only then.
				if(originalEdge.getAnnotation(annotationToRemove) != null)
				{
					if(edgeAnnotationSet.get(newEdge).get(annotationToRemove) == null)
					{
						edgeAnnotationSet.get(newEdge).put(annotationToRemove, new ArrayList<>());
					}
					edgeAnnotationSet.get(newEdge).get(annotationToRemove).add(getAnnotationSafe(originalEdge, annotationToRemove));
				}
			}
		}
		
		Graph resultGraph = new Graph();
		//create a new graph with updated (aggregated) annotations
		for(AbstractEdge edge : edgeAnnotationSet.keySet())
		{
			AbstractEdge newEdge = createNewWithoutAnnotations(edge, annotationsToRemove);
			for(String annotation : edgeAnnotationSet.get(edge).keySet())
			{
				String aggregationFunction = annotationAggregationFunction.get(annotation);
				List<String> list = edgeAnnotationSet.get(edge).get(annotation);
				if(list != null && list.size() != 0)
				{
					String newValue = applyFunctionOnList(list, aggregationFunction);
					newEdge.addAnnotation(annotationNewAnnotation.get(annotation), newValue);
				}
			}
			
			AbstractVertex childVertex = newEdge.getChildVertex();
			AbstractVertex parentVertex = newEdge.getParentVertex();
			
			if(oldNewVertices.get(childVertex) == null)
			{
				AbstractVertex newChildVertex = getVertexWithUpdatedAnnotations(childVertex, annotationsToRemove, annotationAggregationFunction, vertexAnnotationSet.get(childVertex));
				oldNewVertices.put(childVertex, newChildVertex);
			}
			newEdge.setChildVertex(oldNewVertices.get(childVertex));
						
			if(oldNewVertices.get(parentVertex) == null)
			{
				AbstractVertex newParentVertex = getVertexWithUpdatedAnnotations(parentVertex, annotationsToRemove, annotationAggregationFunction, vertexAnnotationSet.get(parentVertex));
				oldNewVertices.put(parentVertex, newParentVertex);
			}
			newEdge.setParentVertex(oldNewVertices.get(parentVertex));
			
			
			resultGraph.putVertex(newEdge.getChildVertex());
			resultGraph.putVertex(newEdge.getParentVertex());
			resultGraph.putEdge(newEdge);
		}
		
		return resultGraph;
	}
	
	private AbstractVertex getVertexWithUpdatedAnnotations
            (
			AbstractVertex vertex, 
			String[] annotationsToRemove, 
			Map<String, String> aggregationFunctionMap, 
			Map<String, List<String>> annotationValues
            )
    {
		AbstractVertex newVertex = createNewWithoutAnnotations(vertex, annotationsToRemove);
		for(String annotation : annotationValues.keySet())
		{
			List<String> list = annotationValues.get(annotation);
			if(list != null && list.size() != 0)
			{
				String aggregationFunction = aggregationFunctionMap.get(annotation);
				String newValue = applyFunctionOnList(list, aggregationFunction);
				newVertex.addAnnotation(annotationNewAnnotation.get(annotation), newValue);
			}
		}

		return newVertex;
	}
	
	private static void sortList_StringAsDouble(List<String> list)
    {
		Collections.sort(list, new Comparator<String>()
        {
			public int compare(String a, String b)
            {
				Double d = parseDouble(a) - parseDouble(b);
				if(d > 0)
				{
					return 1;
				}
				else if(d < 0)
				{
					return -1;
				}
				else
                {
					return 0;
				}
			}
		});
	}
	
	//valid function names = unique(prints unique elements from the list), list (lists all elements even if duplicate), 
	//minmax (prints the first and and last element of the sorted list), sum (prints the sum of the list), count (prints the size of the list)
	//all functions are only applicable on numeric lists so far.
	private static String applyFunctionOnList(List<String> list, String function)
    {
		if(function.equals("unique"))
		{
			Set<String> set = new HashSet<>(list);
			list = new ArrayList<>(set);
			sortList_StringAsDouble(list);

			return list.toString();
		}
		else
        {
			sortList_StringAsDouble(list);
			if(function.equals("list"))
			{
				return list.toString();
			}
			else if(function.equals("minmax"))
			{
				if(list.size() > 0)
				{
					return "["+list.get(0)+", "+list.get(list.size() - 1)+"]";
				}
				else
                {
					return "[]";
				}
			}
			else if(function.equals("sum"))
			{
				Double sum = 0.0;
				for(String string : list)
				{
					sum += parseDouble(string);
				}
				String sumString = sum.toString();
				if(sum == sum.intValue())
				{
					sumString = String.valueOf(sum.intValue());  
				}
				return "["+sumString+"]";
			}
			else if(function.equals("count"))
			{
				return "["+list.size()+"]";
			}
		}

		return "[unknown_aggregation_function:"+function+"]";
	}	
	
	private static Double parseDouble(String str)
    {
		try
        {
			return Double.parseDouble(str);
		}
		catch(Exception e)
        {
			return 0.0;
		}
	}
}
