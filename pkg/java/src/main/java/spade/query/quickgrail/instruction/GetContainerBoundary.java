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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import spade.query.execution.Context;
import spade.query.quickgrail.core.Instruction;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.instruction.GetLineage.Direction;
import spade.query.quickgrail.utility.TreeStringSerializable;
import spade.reporter.audit.OPMConstants;
import spade.utility.HelperFunctions;

/**
 * Extract the boundary of containers: their processes, the vertices those
 * processes are connected to, and the edges among them.
 *
 * A container at runtime is the set of processes in one PID namespace
 * (CLARION, USENIX Security 2021, §4.2.1). Because the kernel reuses PID
 * namespace IDs, one ID can belong to several containers over a trace; they
 * are told apart by their recorded starts (see {@link GetContainerInit}).
 * Selecting a container also selects the containers started inside it, whose
 * processes the kernel also counts as inside it. Processes on the host or with
 * unobserved namespaces ("-1") are never container processes.
 *
 * Signatures:
 *   $r = $base.getContainerBoundary()
 *       every container
 *   $r = $base.getContainerBoundary('<pid namespace id>')
 *       the container with that ID; fails, listing the candidates, if the ID
 *       belonged to more than one container in the trace
 *   $r = $base.getContainerBoundary('<pid namespace id>', <number>)
 *       the numbered container among those with that ID, oldest first
 *   $r = $base.getContainerBoundary($processes)
 *       the containers that the given processes belong to
 */
public class GetContainerBoundary extends Instruction<String>{

	public final Graph targetGraph;
	public final Graph subjectGraph;
	/** `pid namespace` value of host processes, i.e. the kernel's PROC_PID_INIT_INO. */
	public final String hostPidNamespace;
	/** PID namespace ID of the container to select; null for all containers or a seed graph. */
	public final String pidNamespaceId;
	/** 1-based number among containers with {@link #pidNamespaceId}; null to require exactly one. */
	public final Integer containerNumber;
	/** Processes whose containers to select; null unless selecting by processes. */
	public final Graph seedGraph;

	/** Every container. */
	public GetContainerBoundary(final Graph targetGraph, final Graph subjectGraph, final String hostPidNamespace){
		this(targetGraph, subjectGraph, hostPidNamespace, null, null, null);
	}

	/** The container with the PID namespace ID, or the numbered one among containers with that ID. */
	public GetContainerBoundary(final Graph targetGraph, final Graph subjectGraph, final String hostPidNamespace,
			final String pidNamespaceId, final Integer containerNumber){
		this(targetGraph, subjectGraph, hostPidNamespace, pidNamespaceId, containerNumber, null);
		if(HelperFunctions.isNullOrEmpty(pidNamespaceId)){
			throw new IllegalArgumentException("getContainerBoundary: NULL/empty PID namespace ID");
		}
		if(pidNamespaceId.equals(hostPidNamespace)){
			throw new IllegalArgumentException("getContainerBoundary: PID namespace " + pidNamespaceId
					+ " is the host's (PROC_PID_INIT_INO), not a container's");
		}
		if(pidNamespaceId.equals(ContainerAnalysis.UNOBSERVED_NAMESPACE)){
			throw new IllegalArgumentException("getContainerBoundary: PID namespace " + pidNamespaceId
					+ " marks processes whose namespaces were not observed, not a container");
		}
		if(containerNumber != null && containerNumber < 1){
			throw new IllegalArgumentException("getContainerBoundary: container number must be 1 or more, not "
					+ containerNumber);
		}
	}

	/** The containers that the processes in the seed graph belong to. */
	public GetContainerBoundary(final Graph targetGraph, final Graph subjectGraph, final String hostPidNamespace,
			final Graph seedGraph){
		this(targetGraph, subjectGraph, hostPidNamespace, null, null, seedGraph);
		if(seedGraph == null){
			throw new IllegalArgumentException("NULL seed graph");
		}
	}

	private GetContainerBoundary(final Graph targetGraph, final Graph subjectGraph, final String hostPidNamespace,
			final String pidNamespaceId, final Integer containerNumber, final Graph seedGraph){
		this.targetGraph = targetGraph;
		this.subjectGraph = subjectGraph;
		this.hostPidNamespace = hostPidNamespace;
		this.pidNamespaceId = pidNamespaceId;
		this.containerNumber = containerNumber;
		this.seedGraph = seedGraph;
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
		inline_field_names.add("hostPidNamespace");
		inline_field_values.add(hostPidNamespace);
		if(seedGraph != null){
			inline_field_names.add("seedGraph");
			inline_field_values.add(seedGraph.name);
		}else{
			inline_field_names.add("pidNamespaceId");
			inline_field_values.add(pidNamespaceId == null ? "<all>" : pidNamespaceId);
			if(containerNumber != null){
				inline_field_names.add("containerNumber");
				inline_field_values.add(String.valueOf(containerNumber));
			}
		}
	}

	@Override
	public final String exec(final Context ctx){
		final QueryInstructionExecutor executor = ctx.getExecutor();
		final ContainerAnalysis analysis = new ContainerAnalysis(executor, subjectGraph, hostPidNamespace);

		final Graph members;
		if(seedGraph != null){
			members = membersOfSeedContainers(executor, analysis);
		}else if(pidNamespaceId != null){
			members = membersOfNumberedContainer(executor, analysis);
		}else{
			members = analysis.containerProcesses();
		}

		final Graph adjacent = executor.createNewGraph();
		executor.getAdjacentVertex(adjacent, subjectGraph, members, Direction.kBoth);
		executor.getSubgraph(targetGraph, subjectGraph, analysis.union(members, adjacent));
		return null;
	}

	private Graph membersOfNumberedContainer(final QueryInstructionExecutor executor,
			final ContainerAnalysis analysis){
		final List<ContainerAnalysis.Instance> instances = analysis.instances(pidNamespaceId);
		if(containerNumber == null){
			if(instances.size() > 1){
				throw new RuntimeException(ambiguousMessage(instances));
			}
			return instances.isEmpty() ? executor.createNewGraph() : analysis.members(instances.get(0));
		}
		if(containerNumber > instances.size()){
			throw new RuntimeException("getContainerBoundary: PID namespace " + pidNamespaceId + " belonged to "
					+ instances.size() + " container(s) in this trace, so there is no container " + containerNumber
					+ ".");
		}
		return analysis.members(instances.get(containerNumber - 1));
	}

	private Graph membersOfSeedContainers(final QueryInstructionExecutor executor,
			final ContainerAnalysis analysis){
		final Graph seeds = analysis.intersection(seedGraph, analysis.containerProcesses());
		final Map<String, ContainerAnalysis.Instance> instances = new LinkedHashMap<String, ContainerAnalysis.Instance>();
		for(final Map<String, String> seed : executor.exportVertices(seeds).values()){
			final ContainerAnalysis.Instance instance = analysis.instanceAt(
					seed.get(OPMConstants.PROCESS_PID_NAMESPACE), ContainerAnalysis.processTime(seed));
			instances.put(instance.key(), instance);
		}
		final Graph members = executor.createNewGraph();
		for(final ContainerAnalysis.Instance instance : instances.values()){
			executor.unionGraph(members, analysis.members(instance));
		}
		return members;
	}

	private String ambiguousMessage(final List<ContainerAnalysis.Instance> instances){
		final StringBuilder message = new StringBuilder("getContainerBoundary: PID namespace " + pidNamespaceId
				+ " belonged to " + instances.size() + " containers in this trace, since the kernel reuses IDs."
				+ " Pick one with getContainerBoundary('" + pidNamespaceId + "', <number>):");
		for(int i = 0; i < instances.size(); i++){
			message.append("\n  ").append(i + 1).append(": ").append(instances.get(i).describe());
		}
		return message.toString();
	}
}
