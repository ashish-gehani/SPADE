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
import java.util.List;

import spade.reporter.audit.OPMConstants;

/**
 * Writes process provenance into an {@link InMemoryQueryHarness} the way the
 * Audit reporter records it with namespaces=true, agents=false and units=false.
 *
 * What the reporter does, and these fixtures follow:
 *   - A process created in the trace carries `start time` (its clone or execve
 *     time). A process first seen in some other syscall carries `seen time`, and
 *     its namespaces are "-1" until it calls execve, unshare or setns.
 *   - A child is labeled with the namespaces the kernel module reports for it.
 *     clone with SIGCHLD is recorded as `fork` (with CLONE_VM|CLONE_VFORK too, as
 *     `vfork` when simplify=false), and the clone flags go in the edge's `flags`.
 *   - unshare and setns create a new version that keeps the process's name and
 *     `start time` and changes only namespace labels. Vertex hashes come from
 *     annotations, so a process returning to labels it had before gets the
 *     earlier vertex back.
 *   - WasTriggeredBy edges point from the new process or version to the old one
 *     and carry `time` ("seconds.milliseconds") and `event id`.
 */
final class ContainerTrace{

	/** PROC_PID_INIT_INO: the host's PID namespace. */
	static final String HOST = "4026531836";
	/** The host's mount and cgroup namespaces. */
	static final String HOST_MOUNT = "4026531841", HOST_CGROUP = "4026531835";
	static final String UNOBSERVED = "-1";

	/** A process vertex written to the harness. */
	static final class Process{
		final String hash, name, pid, pidNamespace, childrenPidNamespace, mountNamespace, cgroupNamespace;
		/** `start time` or `seen time`, whichever the vertex has. */
		final String timeKey, time;

		private Process(final String name, final String pid, final String pidNamespace,
				final String childrenPidNamespace, final String mountNamespace, final String cgroupNamespace,
				final String timeKey, final String time){
			this.name = name;
			this.pid = pid;
			this.pidNamespace = pidNamespace;
			this.childrenPidNamespace = childrenPidNamespace;
			this.mountNamespace = mountNamespace;
			this.cgroupNamespace = cgroupNamespace;
			this.timeKey = timeKey;
			this.time = time;
			this.hash = name + "/" + pid + "/pid:" + pidNamespace + ">" + childrenPidNamespace
					+ "/mnt:" + mountNamespace + "/cgroup:" + cgroupNamespace + "/" + time;
		}

		/** The same process with other namespace labels, as unshare and setns record it. */
		private Process withNamespaces(final String pidNamespace, final String childrenPidNamespace,
				final String mountNamespace, final String cgroupNamespace){
			return new Process(name, pid, pidNamespace, childrenPidNamespace, mountNamespace, cgroupNamespace,
					timeKey, time);
		}

		boolean isObserved(){
			return !UNOBSERVED.equals(pidNamespace);
		}

		@Override
		public String toString(){
			return hash;
		}
	}

	private final InMemoryQueryHarness harness;
	private final boolean simplify;
	private long clockMillis = 1_700_000_000_000L;
	private long eventId = 7000;
	private long namespaceCounter = 4026532500L;
	private boolean holdClock;

	ContainerTrace(final InMemoryQueryHarness harness){
		this(harness, true);
	}

	ContainerTrace(final InMemoryQueryHarness harness, final boolean simplify){
		this.harness = harness;
		this.simplify = simplify;
	}

	/** A fresh namespace ID. */
	String newNamespace(){
		return String.valueOf(namespaceCounter++);
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
		return putProcess(new Process(name, pid, UNOBSERVED, UNOBSERVED, UNOBSERVED, UNOBSERVED,
				OPMConstants.PROCESS_SEEN_TIME, event[0]));
	}

	/**
	 * clone/fork by `parent`. The child lands in `pidNamespace`; CLONE_NEWNS and
	 * CLONE_NEWCGROUP in the flags give it new mount and cgroup namespaces. The
	 * reporter names the child after the caller, so `name` is usually the parent's.
	 */
	Process spawn(final Process parent, final String name, final String pid, final String flags,
			final String pidNamespace){
		final String[] event = nextEvent();
		final String mountNamespace = flags.contains("CLONE_NEWNS") ? newNamespace()
				: (parent.isObserved() ? parent.mountNamespace : HOST_MOUNT);
		final String cgroupNamespace = flags.contains("CLONE_NEWCGROUP") ? newNamespace()
				: (parent.isObserved() ? parent.cgroupNamespace : HOST_CGROUP);
		final Process child = putProcess(new Process(name, pid, pidNamespace, pidNamespace, mountNamespace,
				cgroupNamespace, OPMConstants.PROCESS_START_TIME, event[0]));
		putEdge(child, parent, creationOperation(flags), event, OPMConstants.EDGE_FLAGS, flags);
		return child;
	}

	/** execve by an observed process: a new version running `name`. */
	Process execve(final Process process, final String name){
		requireObserved(process);
		return execve(process, name, process.pidNamespace, process.childrenPidNamespace);
	}

	/**
	 * execve that also reveals the PID namespaces of a process not observed before
	 * (its other namespaces are taken to be the host's).
	 */
	Process execve(final Process process, final String name, final String pidNamespace,
			final String childrenPidNamespace){
		final String[] event = nextEvent();
		final Process version = putProcess(new Process(name, process.pid, pidNamespace, childrenPidNamespace,
				process.isObserved() ? process.mountNamespace : HOST_MOUNT,
				process.isObserved() ? process.cgroupNamespace : HOST_CGROUP,
				OPMConstants.PROCESS_START_TIME, event[0]));
		putEdge(version, process, OPMConstants.OPERATION_EXECVE, event);
		return version;
	}

	/**
	 * unshare by an observed process. CLONE_NEWPID sends later children to
	 * `childrenPidNamespace` (pass null without it); CLONE_NEWNS and CLONE_NEWCGROUP
	 * give new mount and cgroup namespaces.
	 */
	Process unshare(final Process process, final String flags, final String childrenPidNamespace){
		requireObserved(process);
		return step(process, OPMConstants.OPERATION_UNSHARE, process.withNamespaces(process.pidNamespace,
				flags.contains("CLONE_NEWPID") ? childrenPidNamespace : process.childrenPidNamespace,
				flags.contains("CLONE_NEWNS") ? newNamespace() : process.mountNamespace,
				flags.contains("CLONE_NEWCGROUP") ? newNamespace() : process.cgroupNamespace));
	}

	/** setns into a PID namespace, which later children go to. */
	Process setnsPid(final Process process, final String childrenPidNamespace){
		requireObserved(process);
		return step(process, OPMConstants.OPERATION_SETNS, process.withNamespaces(process.pidNamespace,
				childrenPidNamespace, process.mountNamespace, process.cgroupNamespace));
	}

	/** setns into a mount namespace. */
	Process setnsMount(final Process process, final String mountNamespace){
		requireObserved(process);
		return step(process, OPMConstants.OPERATION_SETNS, process.withNamespaces(process.pidNamespace,
				process.childrenPidNamespace, mountNamespace, process.cgroupNamespace));
	}

	/**
	 * unshare or setns by a process whose namespaces were not observed before: the
	 * new version is labeled with what the kernel module reports.
	 */
	Process unobservedStep(final Process process, final String operation, final String pidNamespace,
			final String childrenPidNamespace){
		return step(process, operation, process.withNamespaces(pidNamespace, childrenPidNamespace, HOST_MOUNT,
				HOST_CGROUP));
	}

	/** The exit edge the reporter draws from a process to itself. */
	void exit(final Process process){
		putEdge(process, process, "exit", nextEvent());
	}

	/** A file written by the process, with the edges the reporter draws for it. */
	String writes(final Process process, final String path){
		final String[] event = nextEvent();
		final String file = "file:" + path;
		harness.putVertex(file, OPMConstants.TYPE, OPMConstants.ARTIFACT, "subtype", "file", "path", path);
		harness.putEdge("WasGeneratedBy#" + event[1], file, process.hash, "write",
				OPMConstants.TYPE, OPMConstants.WAS_GENERATED_BY,
				OPMConstants.EDGE_TIME, event[0], OPMConstants.EDGE_EVENT_ID, event[1]);
		harness.putEdge("Used#" + event[1], process.hash, file, "open",
				OPMConstants.TYPE, OPMConstants.USED,
				OPMConstants.EDGE_TIME, event[0], OPMConstants.EDGE_EVENT_ID, event[1]);
		return file;
	}

	////////////////////////////////////////////////////////////////////////////
	// Lookup

	/** Hash of the one WasTriggeredBy edge from `child` to `parent`. */
	String edge(final Process child, final Process parent){
		final List<String> found = new ArrayList<String>();
		final String prefix = child.hash + " -> " + parent.hash + " #";
		for(final String hash : harness.executor.edgesByHash.keySet()){
			if(hash.startsWith(prefix)){
				found.add(hash);
			}
		}
		if(found.size() != 1){
			throw new IllegalStateException(found.size() + " edges from " + child.hash + " to " + parent.hash);
		}
		return found.get(0);
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

	private Process putProcess(final Process process){
		harness.putVertex(process.hash,
				OPMConstants.TYPE, OPMConstants.PROCESS,
				OPMConstants.SOURCE, OPMConstants.SOURCE_AUDIT_SYSCALL,
				OPMConstants.PROCESS_NAME, process.name,
				OPMConstants.PROCESS_PID, process.pid,
				OPMConstants.PROCESS_PID_NAMESPACE, process.pidNamespace,
				OPMConstants.PROCESS_PID_CHILDREN_NAMESPACE, process.childrenPidNamespace,
				OPMConstants.PROCESS_MOUNT_NAMESPACE, process.mountNamespace,
				OPMConstants.PROCESS_CGROUP_NAMESPACE, process.cgroupNamespace,
				process.timeKey, process.time);
		return process;
	}

	private void putEdge(final Process child, final Process parent, final String operation, final String[] event,
			final String... extra){
		final String[] annotations = new String[8 + extra.length];
		annotations[0] = OPMConstants.TYPE;
		annotations[1] = OPMConstants.WAS_TRIGGERED_BY;
		annotations[2] = OPMConstants.EDGE_TIME;
		annotations[3] = event[0];
		annotations[4] = OPMConstants.EDGE_EVENT_ID;
		annotations[5] = event[1];
		annotations[6] = OPMConstants.SOURCE;
		annotations[7] = OPMConstants.SOURCE_AUDIT_SYSCALL;
		System.arraycopy(extra, 0, annotations, 8, extra.length);
		harness.putEdge(child.hash + " -> " + parent.hash + " #" + event[1], child.hash, parent.hash, operation,
				annotations);
	}

	private static void requireObserved(final Process process){
		if(!process.isObserved()){
			throw new IllegalArgumentException(process.hash + " has unobserved namespaces; give them explicitly");
		}
	}
}
