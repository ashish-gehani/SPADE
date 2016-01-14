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

import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractTransformer;
import spade.core.DigQueryParams;
import spade.core.Graph;

public class Prune extends AbstractTransformer{
	
	private String vertexExpression;
	
	public boolean initialize(String arguments){
		if(arguments == null || arguments.trim().isEmpty()){
			return false;
		}
		vertexExpression = arguments;
		return true;
	}

	@Override
	public Graph putGraph(Graph graph, DigQueryParams digQueryParams){
		
		try{
			
			if(vertexExpression == null){
				throw new IllegalArgumentException("VertexExpression cannot be null");
			}
			
			if(digQueryParams.getDepth() == null){
				throw new IllegalArgumentException("Depth cannot be null");
			}
			
			if(digQueryParams.getDirection() == null){
				throw new IllegalArgumentException("Direction cannot be null");
			}
			
		}catch(Exception e){
			Logger.getLogger(getClass().getName()).log(Level.WARNING, "Missing arguments in graph object", e);
			return graph;
		}
		
		Graph resultGraph = new Graph();
		
		Graph toRemoveGraph = graph.getLineage(this.vertexExpression, digQueryParams.getDepth(), digQueryParams.getDirection(), digQueryParams.getTerminatingExpression());
		
		if(toRemoveGraph != null){
			removeEdges(resultGraph, graph, toRemoveGraph);
			return resultGraph;
		}else{
			return graph;
		}
		
		
	}
	
	
}
