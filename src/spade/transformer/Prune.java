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

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractTransformer;
import spade.core.Graph;

public class Prune extends AbstractTransformer{

	private static final Logger logger = Logger.getLogger(Prune.class.getName());

	@Override
	public LinkedHashSet<ArgumentName> getArgumentNames(){
		return new LinkedHashSet<ArgumentName>(
				Arrays.asList(
						ArgumentName.SOURCE_GRAPH
						, ArgumentName.MAX_DEPTH
						, ArgumentName.DIRECTION
						)
				);
	}

	@Override
	public Graph transform(Graph graph, ExecutionContext context){
		try{
			if(context.getMaxDepth() == null){
				throw new IllegalArgumentException("Depth cannot be null");
			}
			if(context.getDirection() == null){
				throw new IllegalArgumentException("Direction cannot be null");
			}
			if(context.getSourceGraph() == null){
				throw new IllegalArgumentException("Source graph cannot be null");
			}
		}catch(Exception e){
			logger.log(Level.WARNING, "Missing arguments for the current query", e);

			return graph;
		}

		Graph resultGraph = new Graph();

		Graph toRemoveGraph = graph.getLineage(
				context.getSourceGraph().vertexSet(),
				context.getDirection(),
				context.getMaxDepth());

		if(toRemoveGraph != null){
			removeEdges(resultGraph, graph, toRemoveGraph);

			return resultGraph;
		}else{
			return graph;
		}
	}
}
