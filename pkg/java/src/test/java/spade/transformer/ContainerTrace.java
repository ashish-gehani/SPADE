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

import java.util.HashMap;
import java.util.Map;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Graph;
import spade.core.Vertex;
import spade.reporter.audit.OPMConstants;

/**
 * Writes process provenance into a {@link Graph} the way the Audit reporter
 * records it with namespaces=true, agents=false and units=false.
 *
 * What the reporter does, and these fixtures follow:
 *   - A process created in the trace carries `start time` (its clone or execve
 *     time). A process first seen in some other syscall carries `seen time`, and
 *     its namespaces are "-1" until it calls unshare or setns.
 *   - A child is labeled with the namespaces the kernel module reports for it.
 *     clone with SIGCHLD is recorded as `fork` (with CLONE_VM|CLONE_VFORK too, as
 *     `vfork` when simplify=false), and the clone flags go in the edge's `flags`.
 *   - unshare and setns create a new version that keeps the process's name and
 *     `start time` and changes only namespace labels. Vertex hashes come from
 *     annotations, so a process returning to labels it had before gets the
 *     earlier vertex back.
 *   - execve creates a version with the process's current namespaces (BUG_FIXES.md
 *     F17). Reporters without F17 labeled it with the namespaces the process was
 *     created with ("-1" if it was not created in the trace), even after an
 *     unshare or setns; {@link #withLabelsRevertedAtExecve()} records that.
 *   - WasTriggeredBy edges point from the new process or version to the old one
 *     and carry `time` ("seconds.milliseconds") and `event id`.
 *   - A network socket carries the `net namespace` of the process whose syscall
 *     named it, and a System V message queue or shared memory the `ipc namespace`.
 */
final class ContainerTrace{

	/** PROC_PID_INIT_INO: the host's PID namespace. */
	static final String HOST = "4026531836";
	/** The host's other namespaces. */
	static final String HOST_MOUNT = "4026531841", HOST_CGROUP = "4026531835", HOST_NET = "4026531840",
			HOST_IPC = "4026531839";
	static final String UNOBSERVED = "-1";
	/** What kernel modules without the fix F7 in BUG_FIXES.md recorded for a namespace they could not read. */
	static final String UNREADABLE = "4294967295";

	/** A process vertex written to the graph. */
	static final class Process{
		final String name, pid, pidNamespace, childrenPidNamespace, mountNamespace, cgroupNamespace, netNamespace,
				ipcNamespace;
		/** `start time` or `seen time`, whichever the vertex has. */
		final String timeKey, time;
		final AbstractVertex vertex;
		final String hash;

		private Process(final String name, final String pid, final String pidNamespace,
				final String childrenPidNamespace, final String mountNamespace, final String cgroupNamespace,
				final String netNamespace, final String ipcNamespace, final String timeKey, final String time){
			this.name = name;
			this.pid = pid;
			this.pidNamespace = pidNamespace;
			this.childrenPidNamespace = childrenPidNamespace;
			this.mountNamespace = mountNamespace;
			this.cgroupNamespace = cgroupNamespace;
			this.netNamespace = netNamespace;
			this.ipcNamespace = ipcNamespace;
			this.timeKey = timeKey;
			this.time = time;
			this.vertex = new Vertex();
			vertex.addAnnotation(OPMConstants.TYPE, OPMConstants.PROCESS);
			vertex.addAnnotation(OPMConstants.SOURCE, OPMConstants.SOURCE_AUDIT_SYSCALL);
			vertex.addAnnotation(OPMConstants.PROCESS_NAME, name);
			vertex.addAnnotation(OPMConstants.PROCESS_PID, pid);
			vertex.addAnnotation(OPMConstants.PROCESS_PID_NAMESPACE, pidNamespace);
			vertex.addAnnotation(OPMConstants.PROCESS_PID_CHILDREN_NAMESPACE, childrenPidNamespace);
			vertex.addAnnotation(OPMConstants.PROCESS_MOUNT_NAMESPACE, mountNamespace);
			vertex.addAnnotation(OPMConstants.PROCESS_CGROUP_NAMESPACE, cgroupNamespace);
			vertex.addAnnotation(OPMConstants.PROCESS_NET_NAMESPACE, netNamespace);
			vertex.addAnnotation(OPMConstants.PROCESS_IPC_NAMESPACE, ipcNamespace);
			vertex.addAnnotation(timeKey, time);
			this.hash = vertex.bigHashCode();
		}

		/** The same process with other namespace labels, as unshare and setns record it. */
		private Process withNamespaces(final String pidNamespace, final String childrenPidNamespace,
				final String mountNamespace, final String cgroupNamespace, final String netNamespace,
				final String ipcNamespace){
			return new Process(name, pid, pidNamespace, childrenPidNamespace, mountNamespace, cgroupNamespace,
					netNamespace, ipcNamespace, timeKey, time);
		}

		boolean isObserved(){
			return !UNOBSERVED.equals(pidNamespace);
		}

		@Override
		public String toString(){
			return name + "/" + pid + "/pid:" + pidNamespace + ">" + childrenPidNamespace + "/" + time;
		}
	}

	final Graph graph = new Graph();
	private final boolean simplify;
	private long clockMillis = 1_700_000_000_000L;
	private long eventId = 7000;
	private long namespaceCounter = 4026532500L;
	private boolean holdClock;
	/** Label unshare(CLONE_NEWPID) versions as kernel modules without F7 did. */
	private boolean unnamedNewPidNamespaces;
	/** Label execve versions as reporters without F17 did. */
	private boolean labelsRevertedAtExecve;
	/** Per pid, the namespaces the process was created with. */
	private final Map<String, Process> creationStateByPid = new HashMap<String, Process>();

	ContainerTrace(){
		this(true);
	}

	ContainerTrace(final boolean simplify){
		this.simplify = simplify;
	}

	/** A fresh namespace ID. */
	String newNamespace(){
		return String.valueOf(namespaceCounter++);
	}

	/**
	 * Record as kernel modules without the fix F7 in BUG_FIXES.md did: the version
	 * after unshare(CLONE_NEWPID) has `children pid namespace` {@link #UNREADABLE},
	 * because the new namespace has no process yet. Its children still go there.
	 */
	ContainerTrace withUnnamedNewPidNamespaces(){
		unnamedNewPidNamespaces = true;
		return this;
	}

	/** Record execve versions as reporters without F17 did: with the namespaces the process was created with. */
	ContainerTrace withLabelsRevertedAtExecve(){
		labelsRevertedAtExecve = true;
		return this;
	}

	/** The next event happens in the same millisecond as the previous one. */
	ContainerTrace sameMillisecond(){
		holdClock = true;
		return this;
	}

	////////////////////////////////////////////////////////////////////////////
	// Processes

	/** A process that was running before tracing started, first seen in an unrelated syscall. */
	Process preexisting(final String name, final String pid){
		final String[] event = nextEvent();
		return created(putProcess(new Process(name, pid, UNOBSERVED, UNOBSERVED, UNOBSERVED, UNOBSERVED,
				UNOBSERVED, UNOBSERVED, OPMConstants.PROCESS_SEEN_TIME, event[0])));
	}

	/**
	 * A process first seen in some syscall, with the namespaces the kernel module
	 * reported for that syscall, and no creation edge.
	 */
	Process firstSeen(final String name, final String pid, final String pidNamespace){
		final String[] event = nextEvent();
		return created(putProcess(new Process(name, pid, pidNamespace, pidNamespace, HOST_MOUNT, HOST_CGROUP,
				HOST_NET, HOST_IPC, OPMConstants.PROCESS_SEEN_TIME, event[0])));
	}

	/**
	 * clone/fork by `parent`. The child lands in `pidNamespace`; CLONE_NEWNS,
	 * CLONE_NEWCGROUP, CLONE_NEWNET and CLONE_NEWIPC in the flags give it new
	 * namespaces of those types. The reporter names the child after the caller,
	 * so `name` is usually the parent's.
	 */
	Process spawn(final Process parent, final String name, final String pid, final String flags,
			final String pidNamespace){
		final String[] event = nextEvent();
		final Process child = created(putProcess(new Process(name, pid, pidNamespace, pidNamespace,
				inherit(flags, "CLONE_NEWNS", parent.mountNamespace, HOST_MOUNT, parent),
				inherit(flags, "CLONE_NEWCGROUP", parent.cgroupNamespace, HOST_CGROUP, parent),
				inherit(flags, "CLONE_NEWNET", parent.netNamespace, HOST_NET, parent),
				inherit(flags, "CLONE_NEWIPC", parent.ipcNamespace, HOST_IPC, parent),
				OPMConstants.PROCESS_START_TIME, event[0])));
		putEdge(child, parent, creationOperation(flags), event, OPMConstants.EDGE_FLAGS, flags);
		return child;
	}

	private String inherit(final String flags, final String flag, final String parentNamespace,
			final String hostNamespace, final Process parent){
		return flags.contains(flag) ? newNamespace() : (parent.isObserved() ? parentNamespace : hostNamespace);
	}

	/** execve: a new version running `name`. */
	Process execve(final Process process, final String name){
		final Process labels = labelsRevertedAtExecve && creationStateByPid.containsKey(process.pid)
				? creationStateByPid.get(process.pid) : process;
		final String[] event = nextEvent();
		final Process version = putProcess(new Process(name, process.pid, labels.pidNamespace,
				labels.childrenPidNamespace, labels.mountNamespace, labels.cgroupNamespace, labels.netNamespace,
				labels.ipcNamespace, OPMConstants.PROCESS_START_TIME, event[0]));
		putEdge(version, process, OPMConstants.OPERATION_EXECVE, event);
		return version;
	}

	/**
	 * unshare by an observed process. CLONE_NEWPID sends later children to
	 * `childrenPidNamespace` (pass null without it); CLONE_NEWNS, CLONE_NEWCGROUP,
	 * CLONE_NEWNET and CLONE_NEWIPC give new namespaces of those types.
	 */
	Process unshare(final Process process, final String flags, final String childrenPidNamespace){
		requireObserved(process);
		final String newChildrenPidNamespace = !flags.contains("CLONE_NEWPID") ? process.childrenPidNamespace
				: unnamedNewPidNamespaces ? UNREADABLE : childrenPidNamespace;
		return step(process, OPMConstants.OPERATION_UNSHARE, process.withNamespaces(process.pidNamespace,
				newChildrenPidNamespace,
				flags.contains("CLONE_NEWNS") ? newNamespace() : process.mountNamespace,
				flags.contains("CLONE_NEWCGROUP") ? newNamespace() : process.cgroupNamespace,
				flags.contains("CLONE_NEWNET") ? newNamespace() : process.netNamespace,
				flags.contains("CLONE_NEWIPC") ? newNamespace() : process.ipcNamespace));
	}

	/** setns into a PID namespace, which later children go to. */
	Process setnsPid(final Process process, final String childrenPidNamespace){
		requireObserved(process);
		return step(process, OPMConstants.OPERATION_SETNS, process.withNamespaces(process.pidNamespace,
				childrenPidNamespace, process.mountNamespace, process.cgroupNamespace, process.netNamespace,
				process.ipcNamespace));
	}

	/** setns into a mount namespace. */
	Process setnsMount(final Process process, final String mountNamespace){
		requireObserved(process);
		return step(process, OPMConstants.OPERATION_SETNS, process.withNamespaces(process.pidNamespace,
				process.childrenPidNamespace, mountNamespace, process.cgroupNamespace, process.netNamespace,
				process.ipcNamespace));
	}

	/** setns into the mount, net and ipc namespaces of `target`, as docker exec does. */
	Process setnsOthers(final Process process, final Process target){
		requireObserved(process);
		return step(process, OPMConstants.OPERATION_SETNS, process.withNamespaces(process.pidNamespace,
				process.childrenPidNamespace, target.mountNamespace, process.cgroupNamespace, target.netNamespace,
				target.ipcNamespace));
	}

	/**
	 * unshare or setns by a process whose namespaces were not observed before: the
	 * new version is labeled with what the kernel module reports.
	 */
	Process unobservedStep(final Process process, final String operation, final String pidNamespace,
			final String childrenPidNamespace){
		return step(process, operation, process.withNamespaces(pidNamespace, childrenPidNamespace, HOST_MOUNT,
				HOST_CGROUP, HOST_NET, HOST_IPC));
	}

	/** The exit edge the reporter draws from a process to itself. */
	void exit(final Process process){
		putEdge(process, process, OPMConstants.OPERATION_EXIT, nextEvent());
	}

	/** A process killing another: a WasTriggeredBy edge that neither creates nor versions. */
	void kill(final Process killer, final Process killed){
		putEdge(killed, killer, OPMConstants.OPERATION_KILL, nextEvent());
	}

	////////////////////////////////////////////////////////////////////////////
	// Artifacts

	/** A file written by the process, with the edges the reporter draws for it. */
	AbstractVertex writes(final Process process, final String path){
		final AbstractVertex file = artifact(OPMConstants.SUBTYPE_FILE, OPMConstants.ARTIFACT_PATH, path);
		generatedBy(file, process, OPMConstants.OPERATION_WRITE);
		used(process, file, OPMConstants.OPERATION_OPEN);
		return file;
	}

	/**
	 * A network socket that the process connects and then receives from, labeled
	 * with `netNamespace` (pass null for a socket recorded without one).
	 */
	AbstractVertex connects(final Process process, final String remoteAddress, final String netNamespace){
		final AbstractVertex socket = netNamespace == null
				? artifact(OPMConstants.SUBTYPE_NETWORK_SOCKET, OPMConstants.ARTIFACT_REMOTE_ADDRESS, remoteAddress,
						OPMConstants.ARTIFACT_REMOTE_PORT, "80")
				: artifact(OPMConstants.SUBTYPE_NETWORK_SOCKET, OPMConstants.ARTIFACT_REMOTE_ADDRESS, remoteAddress,
						OPMConstants.ARTIFACT_REMOTE_PORT, "80", OPMConstants.PROCESS_NET_NAMESPACE, netNamespace);
		generatedBy(socket, process, OPMConstants.OPERATION_CONNECT);
		used(process, socket, OPMConstants.OPERATION_RECV);
		return socket;
	}

	/** A process receiving from a socket already in the graph. */
	void receives(final Process process, final AbstractVertex socket){
		used(process, socket, OPMConstants.OPERATION_RECV);
	}

	/** A System V message queue or shared memory, of `subtype`, that the process writes. */
	AbstractVertex systemV(final Process process, final String subtype, final String id, final String ipcNamespace){
		final AbstractVertex object = artifact(subtype, OPMConstants.ARTIFACT_SYSV_ID, id,
				OPMConstants.PROCESS_IPC_NAMESPACE, ipcNamespace);
		generatedBy(object, process, OPMConstants.OPERATION_WRITE);
		return object;
	}

	/** A process reading a System V object already in the graph. */
	void reads(final Process process, final AbstractVertex object){
		used(process, object, OPMConstants.OPERATION_READ);
	}

	private AbstractVertex artifact(final String subtype, final String... annotations){
		final AbstractVertex artifact = new Vertex();
		artifact.addAnnotation(OPMConstants.TYPE, OPMConstants.ARTIFACT);
		artifact.addAnnotation(OPMConstants.SOURCE, OPMConstants.SOURCE_AUDIT_SYSCALL);
		artifact.addAnnotation(OPMConstants.ARTIFACT_SUBTYPE, subtype);
		for(int i = 0; i + 1 < annotations.length; i += 2){
			artifact.addAnnotation(annotations[i], annotations[i + 1]);
		}
		graph.putVertex(artifact);
		return artifact;
	}

	private void generatedBy(final AbstractVertex artifact, final Process process, final String operation){
		final String[] event = nextEvent();
		final AbstractEdge edge = new Edge(artifact, process.vertex);
		edge.addAnnotation(OPMConstants.TYPE, OPMConstants.WAS_GENERATED_BY);
		addEventAnnotations(edge, operation, event);
		graph.putEdge(edge);
	}

	private void used(final Process process, final AbstractVertex artifact, final String operation){
		final String[] event = nextEvent();
		final AbstractEdge edge = new Edge(process.vertex, artifact);
		edge.addAnnotation(OPMConstants.TYPE, OPMConstants.USED);
		addEventAnnotations(edge, operation, event);
		graph.putEdge(edge);
	}

	////////////////////////////////////////////////////////////////////////////
	// Container runtimes

	/** What runc records for docker run: containerd-shim → runc → stages 0, 1 and 2 of runc init. */
	static final class DockerRun{
		final Process runc, stage0, stage1, stage1Unshared, init, initCgroupUnshared, thread, application;

		private DockerRun(final Process runc, final Process stage0, final Process stage1,
				final Process stage1Unshared, final Process init, final Process initCgroupUnshared,
				final Process thread, final Process application){
			this.runc = runc;
			this.stage0 = stage0;
			this.stage1 = stage1;
			this.stage1Unshared = stage1Unshared;
			this.init = init;
			this.initCgroupUnshared = initCgroupUnshared;
			this.thread = thread;
			this.application = application;
		}
	}

	/**
	 * docker run, with pids firstPid to firstPid + 4.
	 *
	 * @param applicationName null for a container whose init never calls execve
	 */
	DockerRun dockerRun(final Process shim, final String pidNamespace, final int firstPid,
			final String applicationName){
		final Process runc = execve(spawn(shim, "containerd-shim", pid(firstPid), "SIGCHLD", HOST), "runc");
		final Process stage0 = execve(spawn(runc, "runc", pid(firstPid + 1), "CLONE_VM|CLONE_VFORK|SIGCHLD", HOST),
				"runc:[0:PARENT]");
		final Process stage1 = spawn(stage0, "runc:[0:PARENT]", pid(firstPid + 2), "CLONE_PARENT|SIGCHLD", HOST);
		final Process stage1Unshared = unshare(stage1,
				"CLONE_NEWNS|CLONE_NEWUTS|CLONE_NEWIPC|CLONE_NEWNET|CLONE_NEWPID", pidNamespace);
		final Process init = spawn(stage1Unshared, "runc:[1:CHILD]", pid(firstPid + 3), "CLONE_PARENT|SIGCHLD",
				pidNamespace);
		final Process initCgroupUnshared = unshare(init, "CLONE_NEWCGROUP", null);
		// A Go runtime thread of runc init
		final Process thread = spawn(initCgroupUnshared, "runc:[2:INIT]", pid(firstPid + 4),
				"CLONE_VM|CLONE_FS|CLONE_FILES|CLONE_SIGHAND|CLONE_THREAD|CLONE_SYSVSEM|CLONE_SETTLS", pidNamespace);
		final Process application = applicationName == null ? null : execve(initCgroupUnshared, applicationName);
		return new DockerRun(runc, stage0, stage1, stage1Unshared, init, initCgroupUnshared, thread, application);
	}

	/** What runc records for docker exec into a running container. */
	static final class DockerExec{
		final Process stage1, joiningPid, joined, process, command;

		private DockerExec(final Process stage1, final Process joiningPid, final Process joined,
				final Process process, final Process command){
			this.stage1 = stage1;
			this.joiningPid = joiningPid;
			this.joined = joined;
			this.process = process;
			this.command = command;
		}
	}

	/** docker exec, with pids firstPid to firstPid + 3. */
	DockerExec dockerExec(final Process shim, final Process containerProcess, final int firstPid,
			final String commandName){
		final Process runc = execve(spawn(shim, "containerd-shim", pid(firstPid), "SIGCHLD", HOST), "runc");
		final Process stage0 = execve(spawn(runc, "runc", pid(firstPid + 1), "CLONE_VM|CLONE_VFORK|SIGCHLD", HOST),
				"runc:[0:PARENT]");
		final Process stage1 = spawn(stage0, "runc:[0:PARENT]", pid(firstPid + 2), "CLONE_PARENT|SIGCHLD", HOST);
		final Process joiningPid = setnsPid(stage1, containerProcess.pidNamespace);
		final Process joined = setnsOthers(joiningPid, containerProcess);
		final Process process = spawn(joined, "runc:[1:CHILD]", pid(firstPid + 3), "CLONE_PARENT|SIGCHLD",
				containerProcess.pidNamespace);
		return new DockerExec(stage1, joiningPid, joined, process, execve(process, commandName));
	}

	/** A host process started in the trace from a shell that was running before, now running `program`. */
	Process hostProgram(final String program, final int pid){
		final Process shell = preexisting("bash", "800");
		return execve(spawn(shell, "bash", pid(pid), "SIGCHLD", HOST), program);
	}

	private static String pid(final int pid){
		return String.valueOf(pid);
	}

	/** The WasTriggeredBy edge from `child` to `parent`. */
	AbstractEdge edge(final Process child, final Process parent){
		AbstractEdge found = null;
		for(final AbstractEdge edge : graph.edgeSet()){
			if(edge.getChildVertex().bigHashCode().equals(child.hash)
					&& edge.getParentVertex().bigHashCode().equals(parent.hash)){
				if(found != null){
					throw new IllegalStateException("More than one edge from " + child + " to " + parent);
				}
				found = edge;
			}
		}
		if(found == null){
			throw new IllegalStateException("No edge from " + child + " to " + parent);
		}
		return found;
	}

	////////////////////////////////////////////////////////////////////////////
	// Recording

	private Process step(final Process process, final String operation, final Process version){
		final String[] event = nextEvent();
		putProcess(version);
		putEdge(version, process, operation, event);
		return version;
	}

	private String creationOperation(final String flags){
		if(!flags.contains("SIGCHLD")){
			return OPMConstants.OPERATION_CLONE;
		}
		final boolean vfork = flags.contains("CLONE_VM") && flags.contains("CLONE_VFORK");
		return vfork && !simplify ? "vfork" : OPMConstants.OPERATION_FORK;
	}

	/** {time, event id} of the next event. */
	private String[] nextEvent(){
		if(!holdClock){
			clockMillis += 3;
		}
		holdClock = false;
		eventId++;
		return new String[]{
				String.format("%d.%03d", clockMillis / 1000, clockMillis % 1000), String.valueOf(eventId)};
	}

	private Process created(final Process process){
		creationStateByPid.put(process.pid, process);
		return process;
	}

	private Process putProcess(final Process process){
		graph.putVertex(process.vertex);
		return process;
	}

	private void putEdge(final Process child, final Process parent, final String operation, final String[] event,
			final String... extra){
		final AbstractEdge edge = new Edge(child.vertex, parent.vertex);
		edge.addAnnotation(OPMConstants.TYPE, OPMConstants.WAS_TRIGGERED_BY);
		addEventAnnotations(edge, operation, event);
		for(int i = 0; i + 1 < extra.length; i += 2){
			edge.addAnnotation(extra[i], extra[i + 1]);
		}
		graph.putEdge(edge);
	}

	private static void addEventAnnotations(final AbstractEdge edge, final String operation, final String[] event){
		edge.addAnnotation(OPMConstants.EDGE_OPERATION, operation);
		edge.addAnnotation(OPMConstants.EDGE_TIME, event[0]);
		edge.addAnnotation(OPMConstants.EDGE_EVENT_ID, event[1]);
		edge.addAnnotation(OPMConstants.SOURCE, OPMConstants.SOURCE_AUDIT_SYSCALL);
	}

	private static void requireObserved(final Process process){
		if(!process.isObserved()){
			throw new IllegalArgumentException(process + " has unobserved namespaces; give them explicitly");
		}
	}
}
