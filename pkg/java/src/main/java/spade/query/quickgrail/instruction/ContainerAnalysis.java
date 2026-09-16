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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import spade.query.quickgrail.core.QueriedEdge;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.query.quickgrail.core.QuickGrailQueryResolver.PredicateOperator;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.instruction.GetLineage.Direction;
import spade.reporter.audit.OPMConstants;
import spade.utility.HelperFunctions;

/**
 * Container structure recovered from Audit reporter provenance, shared by
 * {@link GetContainerInit} and {@link GetContainerBoundary}.
 *
 * A container at runtime is the set of processes in one PID namespace
 * (CLARION, USENIX Security 2021, §4.2.1). The Audit reporter labels every
 * process vertex with its `pid namespace` and `children pid namespace` and
 * links processes with WasTriggeredBy edges. Everything here is built from
 * those labels and edges with existing executor primitives, so it works on
 * every storage backend. See CLARITY_METHODS.md for the design, the kernel
 * rules it relies on, and its assumptions.
 */
final class ContainerAnalysis{

	/** `pid namespace` value of processes whose namespaces the reporter never observed. */
	static final String UNOBSERVED_NAMESPACE = "-1";

	private static final String NEW_PID_NAMESPACE_FLAG = "CLONE_NEWPID";

	/** WasTriggeredBy operations that create a process (raw syscall names too, for simplify=false). */
	private static final List<String> CREATION_OPERATIONS = Collections.unmodifiableList(Arrays.asList(
			OPMConstants.OPERATION_CLONE, OPMConstants.OPERATION_FORK, "vfork"));

	/**
	 * WasTriggeredBy operations that make a new version of a process still running
	 * the same program. With the reporter's default agents=false and units=false,
	 * only unshare and setns appear.
	 */
	private static final List<String> SAME_PROGRAM_OPERATIONS = Collections.unmodifiableList(Arrays.asList(
			OPMConstants.OPERATION_UNSHARE, OPMConstants.OPERATION_SETNS,
			OPMConstants.OPERATION_SETUID, "setreuid", "setresuid", "setfsuid",
			OPMConstants.OPERATION_SETGID, "setregid", "setresgid", "setfsgid",
			OPMConstants.OPERATION_UPDATE, OPMConstants.OPERATION_UNIT));

	/**
	 * A creation edge whose child lands in a different PID namespace than its parent.
	 */
	static final class Crossing{

		static enum Kind{
			/** The child is the first process of a new PID namespace: a container starts. */
			INIT,
			/** The child joins a PID namespace that already has processes, e.g. docker exec. */
			ENTRY
		}

		final Kind kind;
		final String edgeHash, childHash, parentHash;
		/** Edge `time` in the reporter's format; ordered as a string, like the storage does. */
		final String time;
		final long eventId;
		final Map<String, String> child, parent;

		private Crossing(final Kind kind, final QueriedEdge edge, final Map<String, String> child,
				final Map<String, String> parent){
			this.kind = kind;
			this.edgeHash = edge.edgeHash;
			this.childHash = edge.childHash;
			this.parentHash = edge.parentHash;
			final Map<String, String> annotations = edge.getCopyOfAnnotations();
			this.time = nonNull(annotations.get(OPMConstants.EDGE_TIME));
			this.eventId = parseLong(annotations.get(OPMConstants.EDGE_EVENT_ID));
			this.child = child;
			this.parent = parent;
		}

		private Crossing(final Kind kind, final Crossing other){
			this.kind = kind;
			this.edgeHash = other.edgeHash;
			this.childHash = other.childHash;
			this.parentHash = other.parentHash;
			this.time = other.time;
			this.eventId = other.eventId;
			this.child = other.child;
			this.parent = other.parent;
		}

		private Crossing withKind(final Kind newKind){
			return new Crossing(newKind, this);
		}

		String childNamespace(){
			return child.get(OPMConstants.PROCESS_PID_NAMESPACE);
		}

		String parentNamespace(){
			return parent.get(OPMConstants.PROCESS_PID_NAMESPACE);
		}

		/** `start time` of the parent version, or `seen time` if it was not created in the trace. */
		String parentTime(){
			return processTime(parent);
		}

		String describeChild(){
			return describeProcess(child);
		}
	}

	/** Event order: time, then event id to break ties within the same millisecond. */
	static final Comparator<Crossing> EVENT_ORDER = new Comparator<Crossing>(){
		@Override
		public int compare(final Crossing a, final Crossing b){
			return compareEvents(a.time, a.eventId, b.time, b.eventId);
		}
	};

	static int compareEvents(final String timeA, final long eventIdA, final String timeB, final long eventIdB){
		final int byTime = timeA.compareTo(timeB);
		return byTime != 0 ? byTime : Long.compare(eventIdA, eventIdB);
	}

	/** A recorded unshare that created a PID namespace for the caller's children. */
	private static final class NamespaceCreation{
		final String stepEdgeHash;
		final String pidNamespace;
		final String time;
		final long eventId;

		private NamespaceCreation(final QueriedEdge step, final String pidNamespace){
			final Map<String, String> annotations = step.getCopyOfAnnotations();
			this.stepEdgeHash = step.edgeHash;
			this.pidNamespace = pidNamespace;
			this.time = nonNull(annotations.get(OPMConstants.EDGE_TIME));
			this.eventId = parseLong(annotations.get(OPMConstants.EDGE_EVENT_ID));
		}
	}

	/**
	 * All PID namespace crossings in the subject graph.
	 */
	static final class Crossings{
		/** Container starts, in event order. */
		final List<Crossing> inits;
		/** Processes entering an existing PID namespace, in event order. */
		final List<Crossing> entries;
		/** Child vertices of {@link #inits}. */
		final Graph initProcesses;
		/** Parent vertices of {@link #inits}. */
		final Graph creators;
		/** Versions whose children go to a PID namespace other than their own. */
		final Graph changedVersions;
		/** The unshare/setns versions where such a change was recorded. */
		final Graph changers;

		private Crossings(final List<Crossing> inits, final List<Crossing> entries, final Graph initProcesses,
				final Graph creators, final Graph changedVersions, final Graph changers){
			this.inits = Collections.unmodifiableList(inits);
			this.entries = Collections.unmodifiableList(entries);
			this.initProcesses = initProcesses;
			this.creators = creators;
			this.changedVersions = changedVersions;
			this.changers = changers;
		}
	}

	private final QueryInstructionExecutor executor;
	private final Graph subject;
	private final String hostPidNamespace;

	private Graph containerProcesses;
	private Graph wasTriggeredBy;
	private Graph creationEdges, creationGraph;
	private Graph execveGraph, sameProgramVersionGraph, versionGraph;
	private Graph lineageGraph;
	private Graph namespaceStepEdges, namespaceStepGraph;
	private Crossings crossings;

	ContainerAnalysis(final QueryInstructionExecutor executor, final Graph subject, final String hostPidNamespace){
		if(executor == null){
			throw new IllegalArgumentException("NULL query instruction executor");
		}
		if(subject == null){
			throw new IllegalArgumentException("NULL subject graph");
		}
		if(HelperFunctions.isNullOrEmpty(hostPidNamespace)){
			throw new IllegalArgumentException("NULL/empty host PID namespace");
		}
		this.executor = executor;
		this.subject = subject;
		this.hostPidNamespace = hostPidNamespace;
	}

	////////////////////////////////////////////////////////////////////////////
	// Process sets

	/**
	 * Processes in a container: labeled with a PID namespace that is neither
	 * the host's nor unobserved.
	 */
	Graph containerProcesses(){
		if(containerProcesses == null){
			final ArrayList<String> keys = new ArrayList<String>();
			keys.add(OPMConstants.PROCESS_PID_NAMESPACE);
			final Graph labeled = executor.createNewGraph();
			executor.getWhereAnnotationsExist(labeled, subject, keys);

			final Graph host = verticesWithPidNamespace(labeled, hostPidNamespace);
			final Graph unobserved = verticesWithPidNamespace(labeled, UNOBSERVED_NAMESPACE);

			final Graph notHost = executor.createNewGraph();
			executor.subtractGraph(notHost, labeled, host, Graph.Component.kVertex);
			containerProcesses = executor.createNewGraph();
			executor.subtractGraph(containerProcesses, notHost, unobserved, Graph.Component.kVertex);
		}
		return containerProcesses;
	}

	Graph verticesWithPidNamespace(final Graph from, final String pidNamespace){
		final Graph vertices = executor.createNewGraph();
		executor.getVertex(vertices, from, OPMConstants.PROCESS_PID_NAMESPACE, PredicateOperator.EQUAL,
				pidNamespace, true);
		return vertices;
	}

	boolean isContainerNamespace(final String pidNamespace){
		return isObserved(pidNamespace) && !hostPidNamespace.equals(pidNamespace);
	}

	////////////////////////////////////////////////////////////////////////////
	// Edge sets. The *Graph variants include endpoint vertices, which Neo4j's
	// adjacency requires of a subject graph.

	/** execve edges: the new version runs another program. */
	Graph execveGraph(){
		if(execveGraph == null){
			execveGraph = withEndpoints(edgesWithOperations(Arrays.asList(OPMConstants.OPERATION_EXECVE)));
		}
		return execveGraph;
	}

	/** Version edges that keep the program, e.g. unshare and setns. */
	Graph sameProgramVersionGraph(){
		if(sameProgramVersionGraph == null){
			sameProgramVersionGraph = withEndpoints(edgesWithOperations(SAME_PROGRAM_OPERATIONS));
		}
		return sameProgramVersionGraph;
	}

	/** All version edges: from a new version of a process to the version before it. */
	Graph versionGraph(){
		if(versionGraph == null){
			versionGraph = union(execveGraph(), sameProgramVersionGraph());
		}
		return versionGraph;
	}

	Graph creationEdges(){
		if(creationEdges == null){
			creationEdges = edgesWithOperations(CREATION_OPERATIONS);
		}
		return creationEdges;
	}

	Graph creationGraph(){
		if(creationGraph == null){
			creationGraph = withEndpoints(creationEdges());
		}
		return creationGraph;
	}

	/** Creation and version edges with their endpoints. */
	Graph lineageGraph(){
		if(lineageGraph == null){
			lineageGraph = union(creationGraph(), versionGraph());
		}
		return lineageGraph;
	}

	/** unshare and setns edges with their endpoints. */
	Graph namespaceStepGraph(){
		if(namespaceStepGraph == null){
			namespaceStepEdges = edgesWithOperations(
					Arrays.asList(OPMConstants.OPERATION_UNSHARE, OPMConstants.OPERATION_SETNS));
			namespaceStepGraph = withEndpoints(namespaceStepEdges);
		}
		return namespaceStepGraph;
	}

	private Graph wasTriggeredBy(){
		if(wasTriggeredBy == null){
			wasTriggeredBy = executor.createNewGraph();
			executor.getEdge(wasTriggeredBy, subject, OPMConstants.TYPE, PredicateOperator.EQUAL,
					OPMConstants.WAS_TRIGGERED_BY, true);
		}
		return wasTriggeredBy;
	}

	private Graph edgesWithOperations(final List<String> operations){
		final Graph edges = executor.createNewGraph();
		for(final String operation : operations){
			executor.getEdge(edges, wasTriggeredBy(), OPMConstants.EDGE_OPERATION, PredicateOperator.EQUAL,
					operation, true);
		}
		return edges;
	}

	private Graph withEndpoints(final Graph edges){
		final Graph graph = executor.createNewGraph();
		executor.unionGraph(graph, edges);
		executor.getEdgeEndpoint(graph, edges, GetEdgeEndpoint.Component.kBoth);
		return graph;
	}

	////////////////////////////////////////////////////////////////////////////
	// Graph helpers

	/**
	 * Vertices reachable from the seeds by repeatedly following edges of the given
	 * graph in one direction, until nothing new is added. Vertices in `stop` are
	 * neither included nor followed. Returns vertices only. Cycles are fine: a
	 * process that returns to labels it had before reuses the earlier vertex.
	 */
	Graph closure(final Graph edgeGraph, final Graph seeds, final Direction direction, final Graph stop){
		final Graph result = executor.createNewGraph();
		executor.subtractGraph(result, seeds, stop == null ? executor.createNewGraph() : stop,
				Graph.Component.kVertex);
		Graph frontier = result;
		while(true){
			final Graph step = executor.createNewGraph();
			executor.getAdjacentVertex(step, edgeGraph, frontier, direction);
			Graph fresh = executor.createNewGraph();
			executor.subtractGraph(fresh, step, result, Graph.Component.kVertex);
			if(stop != null){
				final Graph notStopped = executor.createNewGraph();
				executor.subtractGraph(notStopped, fresh, stop, Graph.Component.kVertex);
				fresh = notStopped;
			}
			if(executor.getGraphCount(fresh).getVertices() == 0){
				return result;
			}
			executor.unionGraph(result, fresh);
			frontier = fresh;
		}
	}

	Graph union(final Graph... graphs){
		final Graph result = executor.createNewGraph();
		for(final Graph graph : graphs){
			executor.unionGraph(result, graph);
		}
		return result;
	}

	Graph intersection(final Graph lhs, final Graph rhs){
		final Graph result = executor.createNewGraph();
		executor.intersectGraph(result, lhs, rhs);
		return result;
	}

	Graph vertices(final Set<String> hashes){
		final Graph graph = executor.createNewGraph();
		if(!hashes.isEmpty()){
			executor.insertLiteralVertex(graph, new ArrayList<String>(hashes));
		}
		return graph;
	}

	/** Creation edges whose parent is one of the given vertices. */
	private Graph creationEdgesFrom(final Graph parents){
		final Graph adjacent = executor.createNewGraph();
		executor.getAdjacentVertex(adjacent, creationGraph(), parents, Direction.kDescendant);
		final Graph edges = executor.createNewGraph();
		executor.getEdge(edges, adjacent, null, null, null, false);
		return edges;
	}

	////////////////////////////////////////////////////////////////////////////
	// PID namespace crossings

	/**
	 * Every creation edge whose child lands in a PID namespace other than its parent's.
	 *
	 * Kernel rules: a child is placed in its parent's `children pid namespace`,
	 * except that clone(CLONE_NEWPID) places it in a brand-new namespace. A
	 * parent's `children pid namespace` only differs from its own after
	 * unshare(CLONE_NEWPID) or setns(CLONE_NEWPID), and only the first process
	 * created after unshare(CLONE_NEWPID) becomes the new namespace's init. So
	 * crossings are the children of versions after a recorded change of
	 * `children pid namespace`, plus clones flagged CLONE_NEWPID. Processes are
	 * linked through edges, never through reused `pid` or namespace values, and
	 * nested containers need nothing special.
	 */
	Crossings crossings(){
		if(crossings != null){
			return crossings;
		}

		// 1. unshare/setns steps that changed where the caller's children go (few; compared in Java)
		final Map<String, Map<String, String>> stepVertices = executor.exportVertices(namespaceStepGraph());
		final Set<String> unsharers = new HashSet<String>();
		final Set<String> joiners = new HashSet<String>();
		final Set<String> restorers = new HashSet<String>();
		final List<NamespaceCreation> creations = new ArrayList<NamespaceCreation>();
		for(final QueriedEdge step : executor.exportEdges(namespaceStepEdges)){
			final Map<String, String> newVersion = stepVertices.get(step.childHash);
			final Map<String, String> oldVersion = stepVertices.get(step.parentHash);
			if(newVersion == null || oldVersion == null){
				continue;
			}
			final String pidNamespace = newVersion.get(OPMConstants.PROCESS_PID_NAMESPACE);
			final String childrenPidNamespace = newVersion.get(OPMConstants.PROCESS_PID_CHILDREN_NAMESPACE);
			final String oldChildrenPidNamespace = oldVersion.get(OPMConstants.PROCESS_PID_CHILDREN_NAMESPACE);
			if(!isObserved(pidNamespace) || !isObserved(childrenPidNamespace)
					|| childrenPidNamespace.equals(oldChildrenPidNamespace)){
				continue; // e.g. unshare(CLONE_NEWNS), or setns into a non-PID namespace
			}
			final String operation = step.getCopyOfAnnotations().get(OPMConstants.EDGE_OPERATION);
			if(pidNamespace.equals(childrenPidNamespace)){
				restorers.add(step.childHash); // children placed back in its own namespace
			}else if(OPMConstants.OPERATION_UNSHARE.equals(operation) && isObserved(oldChildrenPidNamespace)){
				unsharers.add(step.childHash);
				creations.add(new NamespaceCreation(step, childrenPidNamespace));
			}else{
				// setns; or an unshare by a process whose namespaces were never observed, which may
				// have been for another namespace type after an unshare(CLONE_NEWPID) before tracing
				joiners.add(step.childHash);
			}
		}

		// 2. Versions after a change, up to the next change. A process returning to labels it
		// had before reuses a vertex, so a vertex is never stopped by its own kind of change.
		final Graph unsharerVertices = vertices(unsharers);
		final Graph joinerVertices = vertices(joiners);
		final Graph afterUnshare = closure(versionGraph(), unsharerVertices, Direction.kDescendant,
				vertices(plus(minus(joiners, unsharers), restorers)));
		final Graph afterJoin = closure(versionGraph(), joinerVertices, Direction.kDescendant,
				vertices(plus(minus(unsharers, joiners), restorers)));

		// 3. Crossing edges: children of those versions, and clones asking for a new PID namespace
		final Graph fromUnshare = creationEdgesFrom(afterUnshare);
		final Graph fromJoin = creationEdgesFrom(afterJoin);
		final Graph newPidClones = executor.createNewGraph();
		executor.getEdge(newPidClones, creationEdges(), OPMConstants.EDGE_FLAGS, PredicateOperator.LIKE,
				"%" + NEW_PID_NAMESPACE_FLAG + "%", true);

		// 4. Export the (few) crossing edges and classify them
		final Graph crossingEdges = union(fromUnshare, fromJoin, newPidClones);
		final Map<String, Map<String, String>> endpoints = executor.exportVertices(withEndpoints(crossingEdges));
		final Set<String> newPidCloneHashes = edgeHashes(executor.exportEdges(newPidClones));
		final Set<String> fromUnshareHashes = edgeHashes(executor.exportEdges(fromUnshare));

		final List<Crossing> inits = new ArrayList<Crossing>();
		final List<Crossing> entries = new ArrayList<Crossing>();
		final Map<String, List<Crossing>> childrenByCreation = new LinkedHashMap<String, List<Crossing>>();
		for(final QueriedEdge edge : executor.exportEdges(crossingEdges)){
			final Map<String, String> child = endpoints.get(edge.childHash);
			final Map<String, String> parent = endpoints.get(edge.parentHash);
			if(child == null || parent == null
					|| !isContainerNamespace(child.get(OPMConstants.PROCESS_PID_NAMESPACE))){
				continue;
			}
			final Crossing crossing = new Crossing(Crossing.Kind.ENTRY, edge, child, parent);
			if(newPidCloneHashes.contains(edge.edgeHash)){
				inits.add(crossing.withKind(Crossing.Kind.INIT));
			}else if(fromUnshareHashes.contains(edge.edgeHash)){
				final String creation = creationBehind(crossing, creations);
				if(!childrenByCreation.containsKey(creation)){
					childrenByCreation.put(creation, new ArrayList<Crossing>());
				}
				childrenByCreation.get(creation).add(crossing);
			}else{
				entries.add(crossing);
			}
		}
		// Only the first process created after an unshare(CLONE_NEWPID) becomes the namespace's init
		for(final List<Crossing> children : childrenByCreation.values()){
			Collections.sort(children, EVENT_ORDER);
			inits.add(children.get(0).withKind(Crossing.Kind.INIT));
			entries.addAll(children.subList(1, children.size()));
		}
		Collections.sort(inits, EVENT_ORDER);
		Collections.sort(entries, EVENT_ORDER);

		final Set<String> initChildren = new HashSet<String>();
		final Set<String> initParents = new HashSet<String>();
		for(final Crossing init : inits){
			initChildren.add(init.childHash);
			initParents.add(init.parentHash);
		}
		crossings = new Crossings(inits, entries, vertices(initChildren), vertices(initParents),
				union(afterUnshare, afterJoin), union(unsharerVertices, joinerVertices));
		return crossings;
	}

	/**
	 * The recorded unshare that created the namespace a child landed in: the latest
	 * one into that namespace ID before the child. An ID is reused only after its
	 * namespace is gone, and the creating process holds the namespace for as long
	 * as its children go there, so no other creation of that ID can come between.
	 */
	private static String creationBehind(final Crossing crossing, final List<NamespaceCreation> creations){
		NamespaceCreation latest = null;
		for(final NamespaceCreation creation : creations){
			if(creation.pidNamespace.equals(crossing.childNamespace())
					&& compareEvents(creation.time, creation.eventId, crossing.time, crossing.eventId) < 0
					&& (latest == null
						|| compareEvents(creation.time, creation.eventId, latest.time, latest.eventId) > 0)){
				latest = creation;
			}
		}
		return latest == null ? "pid namespace " + crossing.childNamespace() : latest.stepEdgeHash;
	}

	////////////////////////////////////////////////////////////////////////////
	// Small helpers

	static boolean isObserved(final String pidNamespace){
		return !HelperFunctions.isNullOrEmpty(pidNamespace) && !UNOBSERVED_NAMESPACE.equals(pidNamespace);
	}

	/** `start time` if the process version was created in the trace, else `seen time`. */
	static String processTime(final Map<String, String> process){
		final String startTime = process.get(OPMConstants.PROCESS_START_TIME);
		return startTime != null ? startTime : nonNull(process.get(OPMConstants.PROCESS_SEEN_TIME));
	}

	static String describeProcess(final Map<String, String> process){
		return process.get(OPMConstants.PROCESS_NAME)
				+ " (host pid " + process.get(OPMConstants.PROCESS_PID)
				+ ", pid namespace " + process.get(OPMConstants.PROCESS_PID_NAMESPACE) + ")";
	}

	private static Set<String> plus(final Set<String> a, final Set<String> b){
		final Set<String> result = new HashSet<String>(a);
		result.addAll(b);
		return result;
	}

	private static Set<String> minus(final Set<String> a, final Set<String> b){
		final Set<String> result = new HashSet<String>(a);
		result.removeAll(b);
		return result;
	}

	private static Set<String> edgeHashes(final Set<QueriedEdge> edges){
		final Set<String> hashes = new HashSet<String>();
		for(final QueriedEdge edge : edges){
			hashes.add(edge.edgeHash);
		}
		return hashes;
	}

	private static String nonNull(final String value){
		return value == null ? "" : value;
	}

	private static long parseLong(final String value){
		try{
			return value == null ? 0L : Long.parseLong(value.trim());
		}catch(NumberFormatException e){
			return 0L;
		}
	}
}
