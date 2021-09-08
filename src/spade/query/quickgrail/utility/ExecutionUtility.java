/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2021 SRI International

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
package spade.query.quickgrail.utility;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import spade.core.AbstractEdge;
import spade.core.AbstractStorage;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.query.quickgrail.QuickGrailExecutor;
import spade.query.quickgrail.RemoteResolver;
import spade.query.quickgrail.core.GraphStatistic;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.query.quickgrail.core.QuickGrailQueryResolver.PredicateOperator;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.instruction.CreateEmptyGraph;
import spade.query.quickgrail.instruction.DistinctifyGraph;
import spade.query.quickgrail.instruction.ExportGraph;
import spade.query.quickgrail.instruction.ExportGraph.Format;
import spade.query.quickgrail.instruction.GetGraphStatistic;
import spade.query.quickgrail.instruction.GetVertex;
import spade.query.quickgrail.instruction.InsertLiteralEdge;
import spade.query.quickgrail.instruction.InsertLiteralVertex;
import spade.query.quickgrail.instruction.SubtractGraph;
import spade.query.quickgrail.instruction.UnionGraph;
import spade.reporter.audit.OPMConstants;
import spade.utility.HelperFunctions;

public class ExecutionUtility{

	public static spade.core.Graph patchRemoteLineageGraph(final AbstractVertex localVertex,
			final spade.core.Graph remoteVerticesGraph, final spade.core.Graph remoteLineageGraph){
		final Set<AbstractVertex> commonRemoteVertices = new HashSet<AbstractVertex>();

		commonRemoteVertices.addAll(remoteVerticesGraph.vertexSet());
		commonRemoteVertices.retainAll(remoteLineageGraph.vertexSet());

		final Set<AbstractEdge> newEdges = new HashSet<AbstractEdge>();
		for(AbstractVertex remoteVertex : commonRemoteVertices){
			AbstractEdge e0 = new Edge(localVertex, remoteVertex);
			AbstractEdge e1 = new Edge(remoteVertex, localVertex);
			e0.addAnnotation(OPMConstants.TYPE, OPMConstants.WAS_DERIVED_FROM);
			e1.addAnnotation(OPMConstants.TYPE, OPMConstants.WAS_DERIVED_FROM);
			newEdges.add(e0);
			newEdges.add(e1);
		}

		final spade.core.Graph patchedGraph = new spade.core.Graph();
		patchedGraph.vertexSet().addAll(remoteVerticesGraph.vertexSet());
		patchedGraph.vertexSet().addAll(remoteLineageGraph.vertexSet());
		patchedGraph.edgeSet().addAll(remoteLineageGraph.edgeSet());
		patchedGraph.edgeSet().addAll(newEdges);
		return patchedGraph;
	}

	public static void insertVertexHashesInBatches(final QueryInstructionExecutor instructionExecutor,
			final long putGraphBatchSize, final Graph targetGraph, final spade.core.Graph srcGraph){
		final Set<String> vertexHashSet = new HashSet<String>();
		for(AbstractVertex v : srcGraph.vertexSet()){
			vertexHashSet.add(v.bigHashCode());
			if(vertexHashSet.size() >= putGraphBatchSize){
				try{
					instructionExecutor.insertLiteralVertex(
							new InsertLiteralVertex(targetGraph, new ArrayList<String>(vertexHashSet)));
				}finally{
					vertexHashSet.clear();
				}
			}
		}

		if(vertexHashSet.size() > 0){
			try{
				instructionExecutor.insertLiteralVertex(
						new InsertLiteralVertex(targetGraph, new ArrayList<String>(vertexHashSet)));
			}finally{
				vertexHashSet.clear();
			}
		}
	}

	public static void insertEdgeHashesInBatches(final QueryInstructionExecutor instructionExecutor,
			final long putGraphBatchSize, final Graph targetGraph, final spade.core.Graph srcGraph){
		final Set<String> edgeHashSet = new HashSet<String>();
		for(AbstractEdge e : srcGraph.edgeSet()){
			edgeHashSet.add(e.bigHashCode());
			if(edgeHashSet.size() >= putGraphBatchSize){
				try{
					instructionExecutor
							.insertLiteralEdge(new InsertLiteralEdge(targetGraph, new ArrayList<String>(edgeHashSet)));
				}finally{
					edgeHashSet.clear();
				}
			}
		}

		if(edgeHashSet.size() > 0){
			try{
				instructionExecutor
						.insertLiteralEdge(new InsertLiteralEdge(targetGraph, new ArrayList<String>(edgeHashSet)));
			}finally{
				edgeHashSet.clear();
			}
		}
	}

	public static void putGraph(final QuickGrailExecutor quickGrailExecutor,
			final QueryInstructionExecutor instructionExecutor, final long putGraphBatchSize, final Graph targetGraph,
			final spade.core.Graph graph){
		final AbstractStorage storage = instructionExecutor.getStorage();

		final Graph verticesGraph = createNewGraph(instructionExecutor);
		final Graph edgesGraph = createNewGraph(instructionExecutor);

		// The following two calls insert vertices/edges in 'graph' that exist in $base,
		// into the variable 'targetGraph'
		insertVertexHashesInBatches(instructionExecutor, putGraphBatchSize, verticesGraph, graph);
		insertEdgeHashesInBatches(instructionExecutor, putGraphBatchSize, edgesGraph, graph);

		// Getting the vertices/edges that were assigned to variable 'targetGraph'
		final spade.core.Graph vertexGraph = exportGraph(quickGrailExecutor, verticesGraph);
		final spade.core.Graph edgeGraph = exportGraph(quickGrailExecutor, edgesGraph);

		// Subtracting vertices in 'targetGraph' from 'graph' to get the vertices which
		// are not in $base
		final spade.core.Graph subgraphNotPresent = new spade.core.Graph();
		subgraphNotPresent.vertexSet().addAll(graph.vertexSet());
		subgraphNotPresent.edgeSet().addAll(graph.edgeSet());
		subgraphNotPresent.vertexSet().removeAll(vertexGraph.vertexSet());
		subgraphNotPresent.edgeSet().removeAll(edgeGraph.edgeSet());

		// Putting the new vertices in $base
		for(AbstractVertex vertex : subgraphNotPresent.vertexSet()){
			storage.putVertex(vertex);
		}

		// Putting the new edges in $base
		for(AbstractEdge edge : subgraphNotPresent.edgeSet()){
			storage.putEdge(edge);
		}

		storage.flushTransactions(true);

		HelperFunctions.sleepSafe(50);

		// The vertices/edges that were not in $base (but were in 'graph') are now in
		// $base
		// Insert the just-added vertices and edges to 'targetGraph'
		insertVertexHashesInBatches(instructionExecutor, putGraphBatchSize, verticesGraph, graph);
		insertEdgeHashesInBatches(instructionExecutor, putGraphBatchSize, edgesGraph, graph);

		// Union into one variable 'targetGraph'
		unionGraph(instructionExecutor, targetGraph, verticesGraph);
		unionGraph(instructionExecutor, targetGraph, edgesGraph);
	}

	public static String buildRemoteGetVertexPredicate(final AbstractVertex localNetworkVertex){
		String predicate = "";
		predicate += formatQueryName(RemoteResolver.getAnnotationLocalAddress()) + "="
				+ formatQueryValue(RemoteResolver.getRemoteAddress(localNetworkVertex));
		predicate += " and ";
		predicate += formatQueryName(RemoteResolver.getAnnotationLocalPort()) + "="
				+ formatQueryValue(RemoteResolver.getRemotePort(localNetworkVertex));
		predicate += " and ";
		predicate += formatQueryName(RemoteResolver.getAnnotationRemoteAddress()) + "="
				+ formatQueryValue(RemoteResolver.getLocalAddress(localNetworkVertex));
		predicate += " and ";
		predicate += formatQueryName(RemoteResolver.getAnnotationRemotePort()) + "="
				+ formatQueryValue(RemoteResolver.getLocalPort(localNetworkVertex));
		return predicate;
	}

	public static String formatQueryName(String name){
		return '"' + name + '"';
	}

	public static String formatQueryValue(String name){
		return "'" + name + "'";
	}

	public static GraphStatistic.Count getGraphCount(final QueryInstructionExecutor instructionExecutor, 
			final Graph graph){
		return instructionExecutor.getGraphCount(new GetGraphStatistic.Count(graph));
	}

	public static spade.core.Graph exportGraph(final QuickGrailExecutor quickGrailExecutor, final Graph graph){
		return quickGrailExecutor.exportGraph(new ExportGraph(graph, Format.kDot, true, null));
	}

	public static Graph createNewGraph(final QueryInstructionExecutor instructionExecutor){
		Graph newGraph = instructionExecutor.getQueryEnvironment().allocateGraph();
		instructionExecutor.createEmptyGraph(new CreateEmptyGraph(newGraph));
		return newGraph;
	}

	public static void clearGraph(final QueryInstructionExecutor instructionExecutor, final Graph graph){
		instructionExecutor.createEmptyGraph(new CreateEmptyGraph(graph));
	}

	public static void unionGraph(final QueryInstructionExecutor instructionExecutor, final Graph target,
			final Graph source){
		instructionExecutor.unionGraph(new UnionGraph(target, source));
	}

	public static void subtractGraph(final QueryInstructionExecutor instructionExecutor, final Graph target,
			final Graph source){
		instructionExecutor.subtractGraph(new SubtractGraph(target, target, source, null));
	}

	public static void distinctifyGraph(final QueryInstructionExecutor instructionExecutor, final Graph target,
			final Graph source){
		instructionExecutor.distinctifyGraph(new DistinctifyGraph(target, source));
	}

	public static Set<AbstractVertex> getNetworkVertices(final QuickGrailExecutor quickGrailExecutor,
			final QueryInstructionExecutor instructionExecutor, final Graph graph){
		final Graph tempGraph = createNewGraph(instructionExecutor);
		instructionExecutor.getVertex(new GetVertex(tempGraph, graph, OPMConstants.ARTIFACT_SUBTYPE,
				PredicateOperator.EQUAL, OPMConstants.SUBTYPE_NETWORK_SOCKET));
		final spade.core.Graph networkVerticesGraph = exportGraph(quickGrailExecutor, tempGraph);
		final Set<AbstractVertex> networkVertices = new HashSet<AbstractVertex>();
		for(final AbstractVertex networkVertex : networkVerticesGraph.vertexSet()){
			networkVertices.add(networkVertex);
		}
		return networkVertices;
	}

}
