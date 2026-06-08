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

import spade.query.execution.Context;
import spade.query.quickgrail.core.Instruction;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.query.quickgrail.core.QuickGrailQueryResolver.PredicateOperator;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.instruction.GetLineage.Direction;
import spade.query.quickgrail.utility.TreeStringSerializable;
import spade.reporter.audit.OPMConstants;

/**
 * Extract the subgraph(s) corresponding to one or all container boundaries.
 *
 * A container at runtime is defined by its PID namespace (CLARION,
 * USENIX Security 2021 §4.2.1). Its boundary in the provenance graph
 * is the set of process vertices sharing a PID namespace identifier
 * plus the artifacts those processes accessed and the connecting edges.
 *
 * Two forms:
 *   $r = $base.getContainerBoundary()
 *       → union of every labeled container's boundary
 *   $r = $base.getContainerBoundary('<pid_namespace_id>')
 *       → boundary of one container identified by its PID namespace id
 */
public class GetContainerBoundary extends Instruction<String>{

	public final Graph targetGraph;
	public final Graph subjectGraph;
	// null = all labeled containers
	public final String pidNamespaceId;

	public GetContainerBoundary(final Graph targetGraph, final Graph subjectGraph,
			final String pidNamespaceId){
		this.targetGraph = targetGraph;
		this.subjectGraph = subjectGraph;
		this.pidNamespaceId = pidNamespaceId;
	}

	@Override
	public String getLabel(){
		return "GetContainerBoundary";
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
		inline_field_names.add("pidNamespaceId");
		inline_field_values.add(pidNamespaceId == null ? "<all>" : pidNamespaceId);
	}

	@Override
	public final String exec(final Context ctx){
		final QueryInstructionExecutor executor = ctx.getExecutor();

		final Graph procs = executor.createNewGraph();
		if(pidNamespaceId == null){
			final ArrayList<String> keys = new ArrayList<String>();
			keys.add(OPMConstants.PROCESS_PID_NAMESPACE);
			executor.getWhereAnnotationsExist(procs, subjectGraph, keys);
		}else{
			executor.getVertex(procs, subjectGraph,
					OPMConstants.PROCESS_PID_NAMESPACE,
					PredicateOperator.EQUAL,
					pidNamespaceId,
					true);
		}

		final Graph adjacent = executor.createNewGraph();
		executor.getAdjacentVertex(adjacent, subjectGraph, procs, Direction.kBoth);

		final Graph skeleton = executor.createNewGraph();
		executor.unionGraph(skeleton, procs);
		executor.unionGraph(skeleton, adjacent);

		executor.getSubgraph(targetGraph, subjectGraph, skeleton);

		return null;
	}
}
