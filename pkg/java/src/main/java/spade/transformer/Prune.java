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

import java.util.logging.Logger;

import spade.core.AbstractTransformer;
import spade.core.Graph;
import spade.query.quickgrail.instruction.GetLineage;
import spade.transformer.query.parameter.LineageDirection;
import spade.transformer.query.parameter.MaxDepth;
import spade.transformer.query.parameter.SourceGraph;

public class Prune extends AbstractTransformer{

	private static final Logger logger = Logger.getLogger(Prune.class.getName());

	private final SourceGraph sourceGraph = new SourceGraph();
	private final MaxDepth maxDepth = new MaxDepth();
	private final LineageDirection lineageDirectory = new LineageDirection();

	public Prune()
	{
		setParametersInContext(sourceGraph, maxDepth, lineageDirectory);
	}

	@Override
	public Graph transform(final Graph graph) {
		try{
			return _transform(
				graph,
				sourceGraph.getMaterializedValue(),
				maxDepth.getMaterializedValue(),
				lineageDirectory.getMaterializedValue()
			);
		} catch (Exception e) {
			throw new RuntimeException("Failed transformation", e);
		}
	}

	private Graph _transform(
		Graph graph,  final Graph sourceGraph, final int maxDepth, final GetLineage.Direction direction
	){
		Graph resultGraph = new Graph();

		Graph toRemoveGraph = graph.getLineage(
			sourceGraph.vertexSet(),
			direction,
			maxDepth
		);

		if(toRemoveGraph != null){
			removeEdges(resultGraph, graph, toRemoveGraph);

			return resultGraph;
		}else{
			return graph;
		}
	}
}
