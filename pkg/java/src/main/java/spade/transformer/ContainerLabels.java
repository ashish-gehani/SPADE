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
package spade.transformer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.core.AbstractEdge;
import spade.core.AbstractTransformer;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Graph;
import spade.core.Settings;
import spade.core.Vertex;
import spade.reporter.Audit;
import spade.reporter.audit.LinuxConstants;
import spade.reporter.audit.OPMConstants;
import spade.utility.FileUtility;
import spade.utility.HelperFunctions;

/**
 * Labels the processes of each container, and the network sockets and System V
 * IPC objects that only they used, with a `container_id` annotation.
 *
 * A container at runtime is the set of processes in one PID namespace (CLARION,
 * USENIX Security 2021, §4.2). The Audit reporter, with namespaces=true, labels
 * every process vertex with its `pid namespace` and `children pid namespace`, and
 * links processes with WasTriggeredBy edges. A container's start is the recorded
 * creation of its first process (its init) in a new PID namespace (§4.2.2). The
 * kernel reuses a namespace ID once the namespace is gone, so a container is a
 * namespace ID from one recorded start to the next start of that ID.
 *
 * A process version is labeled only when it is positively identified: it is in
 * the container's namespace, its `start time` (or `seen time`) falls in the
 * container's lifetime, and it descends, through creation and version edges
 * inside that namespace, from the container's init or from a process created
 * into the namespace during that lifetime (e.g. by docker exec). Everything else
 * is left as it is: processes of containers whose start is not in the input
 * graph, processes not linked to a start, and host processes.
 *
 * A `network socket` artifact with a `net namespace`, or a `system v message
 * queue` or `system v shared memory` artifact with an `ipc namespace`, gets its
 * processes' label when every process connected to it is labeled with that one
 * container and has the artifact's namespace. A matching namespace alone is not
 * enough, and a shared or ambiguous artifact is left as it is.
 *
 * The result is a copy of the whole input graph: the same vertices and edges,
 * with `container_id` added to those identified. See CLARITY_METHODS.md in
 * spade/query/quickgrail for the design, the kernel rules it relies on, and its
 * limits.
 *
 * Usage:
 *   $labeled = $base.transform(ContainerLabels, '')
 *   $c = $labeled.getVertex(container_id = '4026532270:1700000000.123:7012')
 *
 * Optional argument: hostPidNamespace=&lt;id&gt; gives the host's PID namespace ID;
 * by default it is PROC_PID_INIT_INO from the Audit reporter's constants file.
 */
public class ContainerLabels extends AbstractTransformer{

	private static final Logger logger = Logger.getLogger(ContainerLabels.class.getName());

	/** The annotation this transformer adds. */
	public static final String CONTAINER_ID = "container_id";

	/** Initialization argument: the host's PID namespace ID. */
	public static final String ARGUMENT_HOST_PID_NAMESPACE = "hostPidNamespace";

	/** `pid namespace` value of processes whose namespaces the reporter never observed. */
	static final String UNOBSERVED_NAMESPACE = "-1";

	/**
	 * The kernel module's "could not read" printed unsigned, as modules before the
	 * fix F7 in BUG_FIXES.md wrote it. Not a namespace ID. Such a module labels every
	 * unshare(CLONE_NEWPID) version's `children pid namespace` with it, because the
	 * new namespace has no process yet.
	 */
	static final String UNREADABLE_NAMESPACE = "4294967295";

	private static final String NEW_PID_NAMESPACE_FLAG = "CLONE_NEWPID";

	/** WasTriggeredBy operations that create a process (raw syscall names too, for simplify=false). */
	private static final Set<String> CREATION_OPERATIONS = Collections.unmodifiableSet(new HashSet<String>(
			Arrays.asList(OPMConstants.OPERATION_CLONE, OPMConstants.OPERATION_FORK, "vfork")));

	/**
	 * WasTriggeredBy operations from a new version of a process to the version
	 * before it. With the reporter's default agents=false and units=false, only
	 * execve, unshare and setns appear, and setuid/setgid without a detected agent
	 * change.
	 */
	private static final Set<String> VERSION_OPERATIONS = Collections.unmodifiableSet(new HashSet<String>(
			Arrays.asList(OPMConstants.OPERATION_EXECVE,
					OPMConstants.OPERATION_UNSHARE, OPMConstants.OPERATION_SETNS,
					OPMConstants.OPERATION_SETUID, "setreuid", "setresuid", "setfsuid",
					OPMConstants.OPERATION_SETGID, "setregid", "setresgid", "setfsgid",
					OPMConstants.OPERATION_UPDATE, OPMConstants.OPERATION_UNIT)));

	/** Artifact subtypes that may be labeled, with the namespace annotation that ties them to processes. */
	private static final Map<String, String> NAMESPACED_ARTIFACTS;
	static{
		final Map<String, String> map = new HashMap<String, String>();
		map.put(OPMConstants.SUBTYPE_NETWORK_SOCKET, OPMConstants.PROCESS_NET_NAMESPACE);
		map.put(OPMConstants.SUBTYPE_SYSV_MSG_Q, OPMConstants.PROCESS_IPC_NAMESPACE);
		map.put(OPMConstants.SUBTYPE_SYSV_SHARED_MEMORY, OPMConstants.PROCESS_IPC_NAMESPACE);
		NAMESPACED_ARTIFACTS = Collections.unmodifiableMap(map);
	}

	private String hostPidNamespace;

	public ContainerLabels(){
		setParametersInContext();
	}

	@Override
	public boolean initialize(final String arguments){
		super.initialize(arguments);
		try{
			final String given = HelperFunctions.parseKeyValPairs(arguments).get(ARGUMENT_HOST_PID_NAMESPACE);
			hostPidNamespace = HelperFunctions.isNullOrEmpty(given) ? readHostPidNamespace() : given.trim();
			return true;
		}catch(Exception e){
			logger.log(Level.SEVERE, "Failed to initialize ContainerLabels", e);
			return false;
		}
	}

	/**
	 * The `pid namespace` value of host processes: the kernel's PROC_PID_INIT_INO, read from
	 * the Linux constants file that the Audit reporter's 'constantsSource' setting points to.
	 */
	private static String readHostPidNamespace() throws Exception{
		final String auditConfigPath = Settings.getDefaultConfigFilePath(Audit.class);
		try{
			final Map<String, String> auditConfig = FileUtility.readConfigFileAsKeyValueMap(auditConfigPath, "=");
			final LinuxConstants constants = LinuxConstants.instance(auditConfig, "constantsSource");
			return String.valueOf(constants.getProcPidInitIno());
		}catch(Exception e){
			throw new Exception("Failed to read PROC_PID_INIT_INO through 'constantsSource' in '"
					+ auditConfigPath + "'", e);
		}
	}

	@Override
	public Graph transform(final Graph graph){
		if(hostPidNamespace == null){
			throw new IllegalStateException("ContainerLabels used before initialize()");
		}
		final Labeling labeling = new Labeling(graph, hostPidNamespace);
		labeling.run();
		logger.log(Level.INFO, labeling.summary());
		return labeling.copyWithLabels();
	}

	////////////////////////////////////////////////////////////////////////////

	/** A creation edge whose child lands in a different PID namespace than its parent. */
	static final class Crossing{
		final AbstractEdge edge;
		final AbstractVertex child, parent;
		/** Edge `time` in the reporter's format; ordered as a string. */
		final String time;
		final String eventId;
		final long eventNumber;

		private Crossing(final AbstractEdge edge, final AbstractVertex child, final AbstractVertex parent){
			this.edge = edge;
			this.child = child;
			this.parent = parent;
			this.time = nonNull(edge.getAnnotation(OPMConstants.EDGE_TIME));
			this.eventId = nonNull(edge.getAnnotation(OPMConstants.EDGE_EVENT_ID)).trim();
			this.eventNumber = parseLong(this.eventId);
		}

		String childNamespace(){
			return child.getAnnotation(OPMConstants.PROCESS_PID_NAMESPACE);
		}

		String childHash(){
			return child.bigHashCode();
		}
	}

	/** Event order: time, then event id to break ties within the same millisecond. */
	private static final Comparator<Crossing> EVENT_ORDER = new Comparator<Crossing>(){
		@Override
		public int compare(final Crossing a, final Crossing b){
			return compareEvents(a.time, a.eventNumber, b.time, b.eventNumber);
		}
	};

	private static int compareEvents(final String timeA, final long eventA, final String timeB, final long eventB){
		final int byTime = timeA.compareTo(timeB);
		return byTime != 0 ? byTime : Long.compare(eventA, eventB);
	}

	/** A recorded unshare that created a PID namespace for the caller's children. */
	private static final class NamespaceCreation{
		final String stepEdgeHash;
		/** The new namespace's ID; null until known when the module could not name it. */
		String pidNamespace;
		/** Host pid of the caller. */
		final String pid;
		final String time;
		final long eventNumber;

		private NamespaceCreation(final AbstractEdge step, final String pidNamespace, final String pid){
			this.stepEdgeHash = step.bigHashCode();
			this.pidNamespace = pidNamespace;
			this.pid = pid;
			this.time = nonNull(step.getAnnotation(OPMConstants.EDGE_TIME));
			this.eventNumber = parseLong(step.getAnnotation(OPMConstants.EDGE_EVENT_ID));
		}
	}

	/** When a process changed where its children go: any counted unshare or setns step. */
	private static final class NamespaceStep{
		final String pid;
		final String time;
		final long eventNumber;

		private NamespaceStep(final AbstractEdge step, final String pid){
			this.pid = pid;
			this.time = nonNull(step.getAnnotation(OPMConstants.EDGE_TIME));
			this.eventNumber = parseLong(step.getAnnotation(OPMConstants.EDGE_EVENT_ID));
		}
	}

	/** One container: a PID namespace ID from its recorded start until the next start of that ID. */
	static final class Container{
		final String pidNamespace;
		final Crossing init;
		/** Time of the next start with the same ID, which ends this one; null if none. */
		final String end;
		final String id;

		private Container(final Crossing init, final String end){
			this.pidNamespace = init.childNamespace();
			this.init = init;
			this.end = end;
			this.id = pidNamespace + ":" + init.time + ":" + init.eventId;
		}

		boolean contains(final String time){
			return time.compareTo(init.time) >= 0 && (end == null || time.compareTo(end) < 0);
		}
	}

	/** The identification over one input graph. */
	static final class Labeling{
		private final Graph input;
		private final String hostPidNamespace;

		/** Every vertex by hash, including edge endpoints missing from the vertex set. */
		private final Map<String, AbstractVertex> vertices = new LinkedHashMap<String, AbstractVertex>();
		private final Map<String, List<AbstractEdge>> creationsByParent = new HashMap<String, List<AbstractEdge>>();
		private final Map<String, List<AbstractEdge>> versionsByOld = new HashMap<String, List<AbstractEdge>>();
		private final List<AbstractEdge> creationEdges = new ArrayList<AbstractEdge>();
		private final List<AbstractEdge> namespaceSteps = new ArrayList<AbstractEdge>();

		final List<Crossing> inits = new ArrayList<Crossing>();
		final List<Crossing> entries = new ArrayList<Crossing>();
		final List<Container> containers = new ArrayList<Container>();
		/** Vertex hash to container id. */
		final Map<String, String> labels = new HashMap<String, String>();

		private int conflictingProcesses, unlabeledContainerProcesses;
		private int labeledArtifacts, ambiguousArtifacts;

		Labeling(final Graph input, final String hostPidNamespace){
			if(input == null){
				throw new IllegalArgumentException("NULL graph");
			}
			if(HelperFunctions.isNullOrEmpty(hostPidNamespace)){
				throw new IllegalArgumentException("NULL/empty host PID namespace");
			}
			this.input = input;
			this.hostPidNamespace = hostPidNamespace;
		}

		void run(){
			index();
			findCrossings();
			findContainers();
			labelProcesses();
			labelArtifacts();
		}

		////////////////////////////////////////////////////////////////////////
		// Index

		private void index(){
			for(final AbstractVertex vertex : input.vertexSet()){
				vertices.put(vertex.bigHashCode(), vertex);
			}
			for(final AbstractEdge edge : input.edgeSet()){
				final AbstractVertex child = vertexOf(edge.getChildVertex());
				final AbstractVertex parent = vertexOf(edge.getParentVertex());
				if(child == null || parent == null || !OPMConstants.WAS_TRIGGERED_BY.equals(edge.type())
						|| !isProcess(child) || !isProcess(parent)){
					continue;
				}
				final String operation = edge.getAnnotation(OPMConstants.EDGE_OPERATION);
				if(CREATION_OPERATIONS.contains(operation)){
					creationEdges.add(edge);
					add(creationsByParent, parent.bigHashCode(), edge);
				}else if(VERSION_OPERATIONS.contains(operation)){
					add(versionsByOld, parent.bigHashCode(), edge);
					if(OPMConstants.OPERATION_UNSHARE.equals(operation) || OPMConstants.OPERATION_SETNS.equals(operation)){
						namespaceSteps.add(edge);
					}
				}
			}
		}

		/** The vertex with the endpoint's hash, preferring the one in the vertex set. */
		private AbstractVertex vertexOf(final AbstractVertex endpoint){
			if(endpoint == null){
				return null;
			}
			final String hash = endpoint.bigHashCode();
			AbstractVertex vertex = vertices.get(hash);
			if(vertex == null){
				vertex = endpoint;
				vertices.put(hash, vertex);
			}
			return vertex;
		}

		////////////////////////////////////////////////////////////////////////
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
		 * linked through edges, never through reused `pid` or namespace values.
		 *
		 * Kernel modules without the fix F7 in BUG_FIXES.md cannot name the namespace
		 * an unshare(CLONE_NEWPID) creates, and label the new version's `children pid
		 * namespace` {@link #UNREADABLE_NAMESPACE}. Such a creation takes its ID from
		 * the first process its caller creates after it, which becomes the namespace's
		 * init (see {@link #nameUnnamedCreations}).
		 */
		private void findCrossings(){
			// 1. unshare/setns steps that changed where the caller's children go
			final Set<String> unsharers = new HashSet<String>();
			final Set<String> joiners = new HashSet<String>();
			final Set<String> restorers = new HashSet<String>();
			final List<NamespaceCreation> creations = new ArrayList<NamespaceCreation>();
			final List<NamespaceStep> steps = new ArrayList<NamespaceStep>();
			for(final AbstractEdge step : namespaceSteps){
				final AbstractVertex newVersion = vertexOf(step.getChildVertex());
				final AbstractVertex oldVersion = vertexOf(step.getParentVertex());
				final String newHash = newVersion.bigHashCode();
				final String pid = newVersion.getAnnotation(OPMConstants.PROCESS_PID);
				final String pidNamespace = newVersion.getAnnotation(OPMConstants.PROCESS_PID_NAMESPACE);
				final String childrenPidNamespace = newVersion.getAnnotation(OPMConstants.PROCESS_PID_CHILDREN_NAMESPACE);
				final String oldChildrenPidNamespace = oldVersion.getAnnotation(OPMConstants.PROCESS_PID_CHILDREN_NAMESPACE);
				final boolean unshare = OPMConstants.OPERATION_UNSHARE.equals(step.getAnnotation(OPMConstants.EDGE_OPERATION));
				if(unshare && isObserved(pidNamespace) && UNREADABLE_NAMESPACE.equals(childrenPidNamespace)){
					// unshare(CLONE_NEWPID) recorded by a module that could not name the new namespace
					if(isObserved(oldChildrenPidNamespace)){
						unsharers.add(newHash);
						creations.add(new NamespaceCreation(step, null, pid));
					}else{
						joiners.add(newHash); // as below, for a process never observed before
					}
					steps.add(new NamespaceStep(step, pid));
					continue;
				}
				if(!isObserved(pidNamespace) || !isObserved(childrenPidNamespace)
						|| childrenPidNamespace.equals(oldChildrenPidNamespace)){
					continue; // e.g. unshare(CLONE_NEWNS), or setns into a non-PID namespace
				}
				steps.add(new NamespaceStep(step, pid));
				if(pidNamespace.equals(childrenPidNamespace)){
					restorers.add(newHash); // children placed back in its own namespace
				}else if(unshare && isObserved(oldChildrenPidNamespace)){
					unsharers.add(newHash);
					creations.add(new NamespaceCreation(step, childrenPidNamespace, pid));
				}else{
					// setns; or an unshare by a process whose namespaces were never observed, which may
					// have been for another namespace type after an unshare(CLONE_NEWPID) before tracing
					joiners.add(newHash);
				}
			}

			// 2. Versions after a change, up to the next change. A process returning to labels it
			// had before reuses a vertex, so a vertex is never stopped by its own kind of change.
			final Set<String> afterUnshare = closure(versionsByOld, unsharers,
					plus(minus(joiners, unsharers), restorers));
			final Set<String> afterJoin = closure(versionsByOld, joiners,
					plus(minus(unsharers, joiners), restorers));

			// 3. Crossing edges: children of those versions, and clones asking for a new PID namespace
			final Set<String> fromUnshare = new HashSet<String>();
			final Set<String> newPidClones = new HashSet<String>();
			final List<Crossing> candidates = new ArrayList<Crossing>();
			for(final AbstractEdge edge : creationEdges){
				final AbstractVertex child = vertexOf(edge.getChildVertex());
				final AbstractVertex parent = vertexOf(edge.getParentVertex());
				final String parentHash = parent.bigHashCode();
				final boolean newPidClone = nonNull(edge.getAnnotation(OPMConstants.EDGE_FLAGS))
						.contains(NEW_PID_NAMESPACE_FLAG);
				final boolean afterUnshareStep = afterUnshare.contains(parentHash);
				if(!newPidClone && !afterUnshareStep && !afterJoin.contains(parentHash)){
					continue;
				}
				if(!isContainerNamespace(child.getAnnotation(OPMConstants.PROCESS_PID_NAMESPACE))){
					continue;
				}
				final String edgeHash = edge.bigHashCode();
				if(newPidClone){
					newPidClones.add(edgeHash);
				}
				if(afterUnshareStep){
					fromUnshare.add(edgeHash);
				}
				candidates.add(new Crossing(edge, child, parent));
			}
			Collections.sort(candidates, EVENT_ORDER);
			nameUnnamedCreations(creations, steps, candidates, fromUnshare);

			// 4. Classify: only the first process created after an unshare(CLONE_NEWPID) is an init
			final Map<String, List<Crossing>> childrenByCreation = new LinkedHashMap<String, List<Crossing>>();
			for(final Crossing crossing : candidates){
				final String edgeHash = crossing.edge.bigHashCode();
				if(newPidClones.contains(edgeHash)){
					inits.add(crossing);
				}else if(fromUnshare.contains(edgeHash)){
					final String creation = creationBehind(crossing, creations);
					if(!childrenByCreation.containsKey(creation)){
						childrenByCreation.put(creation, new ArrayList<Crossing>());
					}
					childrenByCreation.get(creation).add(crossing);
				}else{
					entries.add(crossing);
				}
			}
			for(final List<Crossing> children : childrenByCreation.values()){
				inits.add(children.get(0)); // already in event order
				entries.addAll(children.subList(1, children.size()));
			}
			Collections.sort(inits, EVENT_ORDER);
			Collections.sort(entries, EVENT_ORDER);
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
				if(crossing.childNamespace().equals(creation.pidNamespace)
						&& compareEvents(creation.time, creation.eventNumber, crossing.time, crossing.eventNumber) < 0
						&& (latest == null || compareEvents(creation.time, creation.eventNumber,
								latest.time, latest.eventNumber) > 0)){
					latest = creation;
				}
			}
			return latest == null ? "pid namespace " + crossing.childNamespace() : latest.stepEdgeHash;
		}

		/**
		 * Gives each creation recorded without a namespace ID the ID of the first process
		 * its caller created after it and before the caller's next namespace step. By the
		 * kernel's rules that process is the new namespace's init, and threads cannot be
		 * created into another PID namespace. The caller is matched by host pid within
		 * that window, where it is still the same process: a later process reusing the pid
		 * reaches these candidates only through namespace steps of its own, which end the
		 * window. A creation whose namespace never got a process stays unnamed and groups
		 * no children.
		 */
		private static void nameUnnamedCreations(final List<NamespaceCreation> creations,
				final List<NamespaceStep> steps, final List<Crossing> candidatesInEventOrder,
				final Set<String> fromUnshare){
			for(final NamespaceCreation creation : creations){
				if(creation.pidNamespace != null || creation.pid == null){
					continue;
				}
				NamespaceStep next = null;
				for(final NamespaceStep step : steps){
					if(creation.pid.equals(step.pid)
							&& compareEvents(step.time, step.eventNumber, creation.time, creation.eventNumber) > 0
							&& (next == null
								|| compareEvents(step.time, step.eventNumber, next.time, next.eventNumber) < 0)){
						next = step;
					}
				}
				for(final Crossing crossing : candidatesInEventOrder){
					if(fromUnshare.contains(crossing.edge.bigHashCode())
							&& creation.pid.equals(crossing.parent.getAnnotation(OPMConstants.PROCESS_PID))
							&& compareEvents(crossing.time, crossing.eventNumber, creation.time, creation.eventNumber) > 0
							&& (next == null || compareEvents(crossing.time, crossing.eventNumber,
									next.time, next.eventNumber) < 0)){
						creation.pidNamespace = crossing.childNamespace();
						break;
					}
				}
			}
		}

		////////////////////////////////////////////////////////////////////////
		// Containers and their processes

		/**
		 * One container per recorded start. The kernel reuses an ID only after its
		 * namespace is gone, so containers with the same ID never overlap in time, and
		 * the next start of the ID ends the one before.
		 */
		private void findContainers(){
			final Map<String, List<Crossing>> initsByNamespace = new LinkedHashMap<String, List<Crossing>>();
			for(final Crossing init : inits){
				add(initsByNamespace, init.childNamespace(), init);
			}
			for(final List<Crossing> sameId : initsByNamespace.values()){
				for(int i = 0; i < sameId.size(); i++){
					containers.add(new Container(sameId.get(i), i + 1 < sameId.size() ? sameId.get(i + 1).time : null));
				}
			}
		}

		/**
		 * A container's processes: its init, the processes created into its namespace
		 * during its lifetime by a process outside (entries, e.g. docker exec), and
		 * everything they lead to through creation and version edges, as long as it
		 * stays in the namespace and in the lifetime. A version claimed by two
		 * containers, which the kernel's rules rule out, is left unlabeled.
		 */
		private void labelProcesses(){
			final Map<String, String> claimed = new HashMap<String, String>();
			final Set<String> conflicts = new HashSet<String>();
			for(final Container container : containers){
				final List<String> seeds = new ArrayList<String>();
				seeds.add(container.init.childHash());
				for(final Crossing entry : entries){
					if(container.pidNamespace.equals(entry.childNamespace()) && container.contains(entry.time)){
						seeds.add(entry.childHash());
					}
				}
				for(final String member : members(container, seeds)){
					final String earlier = claimed.put(member, container.id);
					if(earlier != null && !earlier.equals(container.id)){
						conflicts.add(member);
					}
				}
			}
			for(final Map.Entry<String, String> entry : claimed.entrySet()){
				if(!conflicts.contains(entry.getKey())){
					labels.put(entry.getKey(), entry.getValue());
				}
			}
			conflictingProcesses = conflicts.size();
			for(final AbstractVertex vertex : vertices.values()){
				if(isProcess(vertex) && isContainerNamespace(vertex.getAnnotation(OPMConstants.PROCESS_PID_NAMESPACE))
						&& !labels.containsKey(vertex.bigHashCode())){
					unlabeledContainerProcesses++;
				}
			}
		}

		private Set<String> members(final Container container, final List<String> seeds){
			final Set<String> members = new HashSet<String>();
			final Deque<String> frontier = new ArrayDeque<String>();
			for(final String seed : seeds){
				if(isMember(container, seed) && members.add(seed)){
					frontier.add(seed);
				}
			}
			while(!frontier.isEmpty()){
				final String hash = frontier.poll();
				for(final Map<String, List<AbstractEdge>> edges : Arrays.asList(creationsByParent, versionsByOld)){
					final List<AbstractEdge> out = edges.get(hash);
					if(out == null){
						continue;
					}
					for(final AbstractEdge edge : out){
						final String next = edge.getChildVertex().bigHashCode();
						if(!members.contains(next) && isMember(container, next)){
							members.add(next);
							frontier.add(next);
						}
					}
				}
			}
			return members;
		}

		private boolean isMember(final Container container, final String hash){
			final AbstractVertex vertex = vertices.get(hash);
			return vertex != null && isProcess(vertex)
					&& container.pidNamespace.equals(vertex.getAnnotation(OPMConstants.PROCESS_PID_NAMESPACE))
					&& container.contains(processTime(vertex));
		}

		////////////////////////////////////////////////////////////////////////
		// Artifacts

		/**
		 * A network socket with a `net namespace`, or a System V message queue or shared
		 * memory with an `ipc namespace`, gets the label of the processes connected to it
		 * when they are all labeled with the same container and all have the artifact's
		 * namespace.
		 */
		private void labelArtifacts(){
			final Map<String, Set<String>> processesByArtifact = new HashMap<String, Set<String>>();
			for(final AbstractEdge edge : input.edgeSet()){
				final AbstractVertex child = vertexOf(edge.getChildVertex());
				final AbstractVertex parent = vertexOf(edge.getParentVertex());
				if(child == null || parent == null){
					continue;
				}
				if(namespaceKey(child) != null && isProcess(parent)){
					addToSet(processesByArtifact, child.bigHashCode(), parent.bigHashCode());
				}else if(namespaceKey(parent) != null && isProcess(child)){
					addToSet(processesByArtifact, parent.bigHashCode(), child.bigHashCode());
				}
			}
			for(final Map.Entry<String, Set<String>> entry : processesByArtifact.entrySet()){
				final AbstractVertex artifact = vertices.get(entry.getKey());
				final String key = namespaceKey(artifact);
				final String namespace = artifact.getAnnotation(key);
				String containerId = null;
				boolean unambiguous = true;
				for(final String processHash : entry.getValue()){
					final String label = labels.get(processHash);
					if(label == null || (containerId != null && !containerId.equals(label))
							|| !namespace.equals(vertices.get(processHash).getAnnotation(key))){
						unambiguous = false;
						break;
					}
					containerId = label;
				}
				if(unambiguous){
					labels.put(entry.getKey(), containerId);
					labeledArtifacts++;
				}else{
					ambiguousArtifacts++;
				}
			}
		}

		/** The namespace annotation that may tie this artifact to processes, or null. */
		private static String namespaceKey(final AbstractVertex vertex){
			if(!OPMConstants.ARTIFACT.equals(vertex.type())){
				return null;
			}
			final String key = NAMESPACED_ARTIFACTS.get(vertex.getAnnotation(OPMConstants.ARTIFACT_SUBTYPE));
			return key == null || HelperFunctions.isNullOrEmpty(vertex.getAnnotation(key)) ? null : key;
		}

		////////////////////////////////////////////////////////////////////////
		// Output

		/**
		 * A copy of the input graph with `container_id` added. Vertices and edges that
		 * do not change keep their hashes; an edge to a labeled vertex is a new edge
		 * with the same annotations.
		 */
		Graph copyWithLabels(){
			final Map<String, AbstractVertex> copies = new HashMap<String, AbstractVertex>();
			final Graph output = new Graph();
			for(final AbstractVertex vertex : input.vertexSet()){
				output.putVertex(copyOf(vertex, copies));
			}
			for(final AbstractEdge edge : input.edgeSet()){
				final AbstractVertex child = copyOf(edge.getChildVertex(), copies);
				final AbstractVertex parent = copyOf(edge.getParentVertex(), copies);
				final boolean endpointsKept = !labels.containsKey(edge.getChildVertex().bigHashCode())
						&& !labels.containsKey(edge.getParentVertex().bigHashCode());
				final AbstractEdge copy = endpointsKept && edge.isReferenceEdge()
						? new Edge(edge.bigHashCode(), child, parent) : new Edge(child, parent);
				copy.addAnnotations(edge.getCopyOfAnnotations());
				output.putEdge(copy);
			}
			return output;
		}

		private AbstractVertex copyOf(final AbstractVertex vertex, final Map<String, AbstractVertex> copies){
			final String hash = vertex.bigHashCode();
			AbstractVertex copy = copies.get(hash);
			if(copy == null){
				final String label = labels.get(hash);
				if(label == null){
					copy = vertex.copyAsVertex();
				}else{
					copy = new Vertex();
					copy.addAnnotations(vertex.getCopyOfAnnotations());
					copy.addAnnotation(CONTAINER_ID, label);
				}
				copies.put(hash, copy);
			}
			return copy;
		}

		String summary(){
			int labeledProcesses = labels.size() - labeledArtifacts;
			return "ContainerLabels: " + containers.size() + " container(s) started in the graph; "
					+ labeledProcesses + " process vertices labeled, " + unlabeledContainerProcesses
					+ " in a container's PID namespace left unlabeled (no recorded start, or not linked to one)"
					+ (conflictingProcesses > 0 ? ", " + conflictingProcesses + " claimed by two containers" : "")
					+ "; " + labeledArtifacts + " artifacts labeled, " + ambiguousArtifacts
					+ " namespaced artifacts left unlabeled as shared or ambiguous";
		}

		////////////////////////////////////////////////////////////////////////
		// Helpers

		private boolean isContainerNamespace(final String pidNamespace){
			return isObserved(pidNamespace) && !hostPidNamespace.equals(pidNamespace);
		}
	}

	private static boolean isProcess(final AbstractVertex vertex){
		return OPMConstants.PROCESS.equals(vertex.type());
	}

	private static boolean isObserved(final String pidNamespace){
		return !HelperFunctions.isNullOrEmpty(pidNamespace) && !UNOBSERVED_NAMESPACE.equals(pidNamespace)
				&& !UNREADABLE_NAMESPACE.equals(pidNamespace);
	}

	/** `start time` if the process version was created in the trace, else `seen time`. */
	static String processTime(final AbstractVertex process){
		final String startTime = process.getAnnotation(OPMConstants.PROCESS_START_TIME);
		return startTime != null ? startTime : nonNull(process.getAnnotation(OPMConstants.PROCESS_SEEN_TIME));
	}

	/**
	 * Vertices reached from the seeds by following the edges from an old version to
	 * its new versions, until nothing new is added. Vertices in `stop` are neither
	 * included nor followed. Cycles are fine.
	 */
	private static Set<String> closure(final Map<String, List<AbstractEdge>> edgesByOld, final Set<String> seeds,
			final Set<String> stop){
		final Set<String> result = new HashSet<String>(minus(seeds, stop));
		final Deque<String> frontier = new ArrayDeque<String>(result);
		while(!frontier.isEmpty()){
			final List<AbstractEdge> out = edgesByOld.get(frontier.poll());
			if(out == null){
				continue;
			}
			for(final AbstractEdge edge : out){
				final String next = edge.getChildVertex().bigHashCode();
				if(!stop.contains(next) && result.add(next)){
					frontier.add(next);
				}
			}
		}
		return result;
	}

	private static <K, V> void add(final Map<K, List<V>> map, final K key, final V value){
		List<V> list = map.get(key);
		if(list == null){
			list = new ArrayList<V>();
			map.put(key, list);
		}
		list.add(value);
	}

	private static <K, V> void addToSet(final Map<K, Set<V>> map, final K key, final V value){
		Set<V> set = map.get(key);
		if(set == null){
			set = new HashSet<V>();
			map.put(key, set);
		}
		set.add(value);
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
