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

import spade.query.execution.Context;
import spade.query.quickgrail.core.Instruction;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.instruction.GetLineage.Direction;
import spade.query.quickgrail.utility.TreeStringSerializable;

/**
 * Extract the subgraph of container initialization activity.
 *
 * CLARION (USENIX Security 2021) §4.2.2: initialization starts with an unshare
 * or a clone that creates a new PID namespace, and ends with the execve that
 * launches the application inside the container.
 *
 * For every container start found in the subject graph, including containers
 * nested inside other containers, the result holds:
 *   - the unshare/setns steps of the process that created the container,
 *   - the creation edge into the new PID namespace,
 *   - the versions of the container's first process up to and including its
 *     first execve, which runs the application, with the edges between them.
 *
 * Processes are linked through edges only, never through `pid` or `pid namespace`
 * values, because both are reused within a run. A container that started before
 * tracing has no recorded start and is not reported. If a container's first
 * process never reaches execve in the trace, the query fails, since the result
 * would not show a complete initialization.
 *
 * Signature:
 *   $r = $base.getContainerInit()
 */
public class GetContainerInit extends Instruction<String>{

	private static final int MAX_PROCESSES_IN_ERROR = 5;

	public final Graph targetGraph;
	public final Graph subjectGraph;
	/** `pid namespace` value of host processes, i.e. the kernel's PROC_PID_INIT_INO. */
	public final String hostPidNamespace;

	public GetContainerInit(final Graph targetGraph, final Graph subjectGraph, final String hostPidNamespace){
		this.targetGraph = targetGraph;
		this.subjectGraph = subjectGraph;
		this.hostPidNamespace = hostPidNamespace;
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
		inline_field_names.add("hostPidNamespace");
		inline_field_values.add(hostPidNamespace);
	}

	@Override
	public final String exec(final Context ctx){
		final QueryInstructionExecutor executor = ctx.getExecutor();
		final ContainerAnalysis analysis = new ContainerAnalysis(executor, subjectGraph, hostPidNamespace);

		final ContainerAnalysis.Crossings crossings = analysis.crossings();
		if(crossings.inits.isEmpty()){
			return null;
		}

		// Each container's first process until it runs another program: the application
		final Graph beforeApplication = analysis.closure(analysis.sameProgramVersionGraph(),
				crossings.initProcesses, Direction.kDescendant, null);
		final Graph applicationSteps = executor.createNewGraph();
		executor.getAdjacentVertex(applicationSteps, analysis.execveGraph(), beforeApplication,
				Direction.kDescendant);
		final Graph applicationExecves = executor.createNewGraph();
		executor.getEdge(applicationExecves, applicationSteps, null, null, null, false);
		final Graph applications = executor.createNewGraph();
		executor.getEdgeEndpoint(applications, applicationExecves, GetEdgeEndpoint.Component.kSource);

		// Initialization ends with that execve; fail loudly if a container never got there
		final Graph execvedVersions = executor.createNewGraph();
		executor.getEdgeEndpoint(execvedVersions, applicationExecves, GetEdgeEndpoint.Component.kDestination);
		final Graph reachedApplication = analysis.closure(analysis.sameProgramVersionGraph(), execvedVersions,
				Direction.kAncestor, null);
		final Graph stalled = executor.createNewGraph();
		executor.subtractGraph(stalled, crossings.initProcesses, reachedApplication, Graph.Component.kVertex);
		if(executor.getGraphCount(stalled).getVertices() > 0){
			throw new RuntimeException(stalledMessage(executor.exportVertices(stalled)));
		}

		// Start: the creators, their versions since the namespace change, and the unshare/setns steps before it
		final Graph creatorHistory = analysis.closure(analysis.versionGraph(), crossings.creators,
				Direction.kAncestor, null);
		final Graph sinceChange = analysis.intersection(creatorHistory, crossings.changedVersions);
		final Graph changes = analysis.intersection(sinceChange, crossings.changers);
		final Graph namespaceSteps = analysis.closure(analysis.namespaceStepGraph(), changes,
				Direction.kAncestor, null);

		final Graph skeleton = analysis.union(crossings.creators, sinceChange, namespaceSteps,
				beforeApplication, applications);
		executor.getSubgraph(targetGraph, analysis.lineageGraph(), skeleton);
		return null;
	}

	private static String stalledMessage(final Map<String, Map<String, String>> stalledProcesses){
		final StringBuilder processes = new StringBuilder();
		int listed = 0;
		for(final Map<String, String> process : stalledProcesses.values()){
			if(listed == MAX_PROCESSES_IN_ERROR){
				processes.append(", ...");
				break;
			}
			processes.append(listed == 0 ? "" : ", ").append(ContainerAnalysis.describeProcess(process));
			listed++;
		}
		return "getContainerInit: " + stalledProcesses.size() + " container init process(es) never reached "
				+ "execve in this trace: " + processes + ". The trace may end before those containers finished "
				+ "starting.";
	}
}
