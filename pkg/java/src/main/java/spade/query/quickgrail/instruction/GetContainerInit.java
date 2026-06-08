/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2026 SRI International

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
package spade.query.quickgrail.instruction;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import spade.query.execution.Context;
import spade.query.quickgrail.core.GraphStatistic;
import spade.query.quickgrail.core.Instruction;
import spade.query.quickgrail.core.QueriedEdge;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.query.quickgrail.core.QuickGrailQueryResolver.PredicateOperator;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.utility.TreeStringSerializable;
import spade.reporter.audit.OPMConstants;

/**
 * Extract the subgraph representing container initialization activity.
 *
 * CLARION (USENIX Security 2021) §4.2.2 defines the init pattern as
 * starting with an `unshare` (or `clone` with a new namespace flag) and
 * ending with an `execve` that launches the in-container application;
 * the resulting in-container process has `ns pid` == '1'.
 *
 * Algorithm:
 *   ends    = vertices with `ns pid` == '1'
 *   starts  = destination endpoints of (`unshare` edges ∪
 *             `clone` edges whose child is in `ends`)
 *   target  = simple paths from `ends` to `starts`, bounded by maxDepth
 *
 * The path search is bounded by the `maxDepth` environment variable.
 * If any detected init start is not reachable from an `ns pid` == '1'
 * endpoint within that depth, the query fails with a clear message so
 * the user can raise `maxDepth` and retry.
 *
 * Signature:
 *   $r = $base.getContainerInit()
 */
public class GetContainerInit extends Instruction<String>{

	public final Graph targetGraph;
	public final Graph subjectGraph;
	public final int maxDepth;

	public GetContainerInit(final Graph targetGraph, final Graph subjectGraph, final int maxDepth){
		this.targetGraph = targetGraph;
		this.subjectGraph = subjectGraph;
		this.maxDepth = maxDepth;
	}

	@Override
	public String getLabel(){
		return "GetContainerInit";
	}

	@Override
	protected void getFieldStringItems(ArrayList<String> inline_field_names,
			ArrayList<String> inline_field_values,
			ArrayList<String> non_container_child_field_names,
			ArrayList<TreeStringSerializable> non_container_child_fields,
			ArrayList<String> container_child_field_names,
			ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
		inline_field_names.add("targetGraph");
		inline_field_values.add(targetGraph.name);
		inline_field_names.add("subjectGraph");
		inline_field_values.add(subjectGraph.name);
		inline_field_names.add("maxDepth");
		inline_field_values.add(String.valueOf(maxDepth));
	}

	@Override
	public final String exec(final Context ctx){
		final QueryInstructionExecutor executor = ctx.getExecutor();

		final Graph pid1Vertices = executor.createNewGraph();
		executor.getVertex(pid1Vertices, subjectGraph,
				OPMConstants.PROCESS_NS_PID,
				PredicateOperator.EQUAL,
				"1",
				true);

		if(executor.getGraphCount(pid1Vertices).getVertices() == 0){
			// No PID-1 vertices means there is nothing labeled as an in-container
			// init process in the input graph. Return an empty target.
			return null;
		}

		final Graph unshareEdges = executor.createNewGraph();
		executor.getEdge(unshareEdges, subjectGraph,
				OPMConstants.EDGE_OPERATION,
				PredicateOperator.EQUAL,
				OPMConstants.OPERATION_UNSHARE,
				true);

		final Graph allCloneEdges = executor.createNewGraph();
		executor.getEdge(allCloneEdges, subjectGraph,
				OPMConstants.EDGE_OPERATION,
				PredicateOperator.EQUAL,
				OPMConstants.OPERATION_CLONE,
				true);

		final Map<String, Map<String, String>> pid1VerticesData = executor.exportVertices(pid1Vertices);
		final Set<String> pid1Hashes = pid1VerticesData.keySet();

		final Set<QueriedEdge> allCloneEdgeSet = executor.exportEdges(allCloneEdges);
		final ArrayList<String> cloneCrossingNamespaceHashes = new ArrayList<String>();
		for(final QueriedEdge edge : allCloneEdgeSet){
			if(pid1Hashes.contains(edge.childHash)){
				cloneCrossingNamespaceHashes.add(edge.edgeHash);
			}
		}

		final Graph cloneCrossingNamespaceEdges = executor.createNewGraph();
		if(!cloneCrossingNamespaceHashes.isEmpty()){
			executor.insertLiteralEdge(cloneCrossingNamespaceEdges, cloneCrossingNamespaceHashes);
		}

		final Graph boundaryEdges = executor.createNewGraph();
		executor.unionGraph(boundaryEdges, unshareEdges);
		executor.unionGraph(boundaryEdges, cloneCrossingNamespaceEdges);

		final long boundaryEdgeCount = executor.getGraphCount(boundaryEdges).getEdges();
		if(boundaryEdgeCount == 0){
			throw new RuntimeException(
					"getContainerInit: found " + pid1Hashes.size() + " 'ns pid' == '1' vertex/vertices "
					+ "but no 'unshare' or PID-namespace-crossing 'clone' edges in the input graph. "
					+ "The input may be truncated.");
		}

		final Graph startVertices = executor.createNewGraph();
		executor.getEdgeEndpoint(startVertices, boundaryEdges, GetEdgeEndpoint.Component.kDestination);

		executor.getSimplePath(targetGraph, subjectGraph, pid1Vertices, startVertices, maxDepth);

		final Graph startsInResult = executor.createNewGraph();
		executor.intersectGraph(startsInResult, startVertices, targetGraph);

		final GraphStatistic.Count expectedStarts = executor.getGraphCount(startVertices);
		final GraphStatistic.Count reachedStarts = executor.getGraphCount(startsInResult);

		if(reachedStarts.getVertices() < expectedStarts.getVertices()){
			throw new RuntimeException(
					"getContainerInit: " + expectedStarts.getVertices() + " init starts detected ("
					+ "'unshare' callers or 'clone' callers crossing into a new PID namespace), but only "
					+ reachedStarts.getVertices() + " reached an 'ns pid' == '1' endpoint within maxDepth="
					+ maxDepth + ". Increase via `env set maxDepth <N>` and retry.");
		}

		return null;
	}
}
