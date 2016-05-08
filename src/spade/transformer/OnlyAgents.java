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
package spade.transformer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jgrapht.EdgeFactory;
import org.jgrapht.alg.TransitiveClosure;
import org.jgrapht.graph.SimpleDirectedGraph;

import spade.client.QueryParameters;
import spade.core.AbstractEdge;
import spade.core.AbstractTransformer;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Graph;

public class OnlyAgents extends AbstractTransformer{
	@Override
	public Graph putGraph(Graph graph, QueryParameters digQueryParams) {
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
		
		SimpleDirectedGraph<AbstractVertex, AbstractEdge> spadeGraph = new SimpleDirectedGraph<AbstractVertex, AbstractEdge>(new EdgeFactory<AbstractVertex, AbstractEdge>() {
			@Override
			public AbstractEdge createEdge(AbstractVertex sourceVertex, AbstractVertex destinationVertex) {
				return new Edge(sourceVertex, destinationVertex);
			}
		});
		Map<AbstractVertex, Set<AbstractEdge>> vertexToAgent = new HashMap<AbstractVertex, Set<AbstractEdge>>();
		for(AbstractEdge edge : graph.edgeSet()){
			AbstractVertex agentVertex = null, otherVertex = null;
			if(getAnnotationSafe(edge.getSourceVertex(), "type").equals("Agent")){
				otherVertex = edge.getDestinationVertex();
				agentVertex = edge.getSourceVertex();
			}else if(getAnnotationSafe(edge.getDestinationVertex(), "type").equals("Agent")){
				otherVertex = edge.getSourceVertex();
				agentVertex = edge.getDestinationVertex();
			}
			if(agentVertex != null && otherVertex != null){ 
				if(vertexToAgent.get(otherVertex) == null){
					vertexToAgent.put(otherVertex, new HashSet<AbstractEdge>());
				}
				vertexToAgent.get(otherVertex).add(edge);
				resultGraph.putVertex(agentVertex);
			}
			spadeGraph.addVertex(edge.getSourceVertex());
			spadeGraph.addVertex(edge.getDestinationVertex());
			spadeGraph.addEdge(edge.getSourceVertex(), edge.getDestinationVertex(), edge);
		}
		
		TransitiveClosure.INSTANCE.closeSimpleDirectedGraph(spadeGraph);
				
		for(AbstractEdge edge : spadeGraph.edgeSet()){
			Set<AbstractEdge> sourceEdges = null, destinationEdges = null;
			if((sourceEdges = vertexToAgent.get(edge.getSourceVertex())) != null && (destinationEdges = vertexToAgent.get(edge.getDestinationVertex())) != null &&
					sourceEdges.size() > 0 && destinationEdges.size() > 0){
				for(AbstractEdge sourceEdge : sourceEdges){
					AbstractVertex sourceAgent = getAnnotationSafe(sourceEdge.getSourceVertex(), "type").equals("Agent") ? sourceEdge.getSourceVertex() : sourceEdge.getDestinationVertex();
					for(AbstractEdge destinationEdge : destinationEdges){
						AbstractVertex destinationAgent = getAnnotationSafe(destinationEdge.getSourceVertex(), "type").equals("Agent") ? destinationEdge.getSourceVertex() : destinationEdge.getDestinationVertex();
						AbstractEdge newEdge = new Edge(sourceAgent, destinationAgent);
						newEdge.addAnnotation("type", "ActedOnBehalfOf");
						resultGraph.putEdge(newEdge); //have added all agents previously. so, not adding them here
					}
				}				
			}
		}
		
		return resultGraph;
	}
}
