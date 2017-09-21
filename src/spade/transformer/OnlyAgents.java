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

import org.jgrapht.EdgeFactory;
import org.jgrapht.alg.TransitiveClosure;
import org.jgrapht.graph.SimpleDirectedGraph;
import spade.client.QueryMetaData;
import spade.core.AbstractEdge;
import spade.core.AbstractTransformer;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Graph;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class OnlyAgents extends AbstractTransformer
{
	@Override
	public Graph putGraph(Graph graph, QueryMetaData queryMetaData)
	{
		/*
		 * Code description: 
		 * 
		 * Add every agent vertex into the final graph while building the following data structures
		 * Build the graph needed by the TransitiveClosure class
		 * Build a map where every key is a vertex that was connected to an agent. Map(Vertex -> list of Agent edges)
		 * Run transitive closure on the graph
		 * For every edge in the transitive closure graph, check if both endpoints of the edge have agents connected to them (using the map built before)
		 * 		If yes then draw edges between all the agents of both the endpoints with appropriate directions
		 * TODO: Do we need to do draw edges between agents connected to the same vertex? eg. A process connected to 2 or more agents. 
		 */
		
		Graph resultGraph = new Graph();
		
		SimpleDirectedGraph<AbstractVertex, AbstractEdge> spadeGraph =
				new SimpleDirectedGraph<>(new EdgeFactory<AbstractVertex, AbstractEdge>()
		{
			@Override
			public AbstractEdge createEdge(AbstractVertex childVertex, AbstractVertex parentVertex)
			{
				return new Edge(childVertex, parentVertex);
			}
		});

		Map<AbstractVertex, Set<AbstractEdge>> vertexToAgent = new HashMap<>();
		for(AbstractEdge edge : graph.edgeSet())
		{
			AbstractVertex agentVertex = null, otherVertex = null;
			if(getAnnotationSafe(edge.getChildVertex(), "type").equals("Agent"))
			{
				otherVertex = edge.getParentVertex();
				agentVertex = edge.getChildVertex();
			}
			else if(getAnnotationSafe(edge.getParentVertex(), "type").equals("Agent"))
			{
				otherVertex = edge.getChildVertex();
				agentVertex = edge.getParentVertex();
			}
			if(agentVertex != null && otherVertex != null)
			{
				if(vertexToAgent.get(otherVertex) == null)
				{
					vertexToAgent.put(otherVertex, new HashSet<>());
				}
				vertexToAgent.get(otherVertex).add(edge);
				resultGraph.putVertex(agentVertex);
			}
			spadeGraph.addVertex(edge.getChildVertex());
			spadeGraph.addVertex(edge.getParentVertex());
			spadeGraph.addEdge(edge.getChildVertex(), edge.getParentVertex(), edge);
		}
		
		TransitiveClosure.INSTANCE.closeSimpleDirectedGraph(spadeGraph);
				
		for(AbstractEdge edge : spadeGraph.edgeSet())
		{
			Set<AbstractEdge> sourceEdges = null, destinationEdges = null;
			if((sourceEdges = vertexToAgent.get(edge.getChildVertex())) != null
					&& (destinationEdges = vertexToAgent.get(edge.getParentVertex())) != null &&
					sourceEdges.size() > 0 && destinationEdges.size() > 0)
			{
				for(AbstractEdge sourceEdge : sourceEdges)
				{
					AbstractVertex sourceAgent = getAnnotationSafe(sourceEdge.getChildVertex(), "type").equals("Agent") ?
							sourceEdge.getChildVertex() : sourceEdge.getParentVertex();
					for(AbstractEdge destinationEdge : destinationEdges)
					{
						AbstractVertex destinationAgent = getAnnotationSafe(destinationEdge.getChildVertex(), "type").equals("Agent") ?
								destinationEdge.getChildVertex() : destinationEdge.getParentVertex();
						AbstractEdge newEdge = new Edge(sourceAgent, destinationAgent);
						newEdge.addAnnotation("type", "ActedOnBehalfOf");
						//have added all agents previously. so, not adding them here
						resultGraph.putEdge(newEdge);
					}
				}				
			}
		}
		return resultGraph;
	}
}
