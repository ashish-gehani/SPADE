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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static spade.transformer.ContainerTrace.HOST;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import spade.core.AbstractEdge;
import spade.core.AbstractTransformer;
import spade.core.AbstractVertex;
import spade.core.Graph;
import spade.core.Vertex;
import spade.query.quickgrail.core.AbstractQueryEnvironment;
import spade.query.quickgrail.core.Instruction;
import spade.query.quickgrail.core.Program;
import spade.query.quickgrail.core.QuickGrailQueryResolver;
import spade.query.quickgrail.instruction.TransformGraph;
import spade.query.quickgrail.parser.DSLParserWrapper;
import spade.reporter.audit.OPMConstants;
import spade.transformer.ContainerTrace.DockerExec;
import spade.transformer.ContainerTrace.DockerRun;
import spade.transformer.ContainerTrace.Process;

/**
 * {@link ContainerLabels} on synthetic traces modeled on the Audit reporter's
 * source code (see {@link ContainerTrace}). Every scenario runs on traces
 * recorded four ways: with today's kernel module and reporter, with a module
 * without F7 (the new PID namespace unnamed), with a reporter without F17
 * (namespace labels reverted at execve), and with neither.
 */
public class ContainerLabelsTest{

	@Nested
	class CurrentModuleAndReporter extends Scenarios{
		CurrentModuleAndReporter(){
			super(false, false);
		}
	}

	@Nested
	class ModuleWithoutF7 extends Scenarios{
		ModuleWithoutF7(){
			super(true, false);
		}
	}

	@Nested
	class ReporterWithoutF17 extends Scenarios{
		ReporterWithoutF17(){
			super(false, true);
		}
	}

	@Nested
	class ModuleWithoutF7AndReporterWithoutF17 extends Scenarios{
		ModuleWithoutF7AndReporterWithoutF17(){
			super(true, true);
		}
	}

	////////////////////////////////////////////////////////////////////////////
	// The transformer's contract

	@Test
	public void create_findsTheTransformerByName(){
		assertFalse(AbstractTransformer.create("ContainerLabels").error);
		assertTrue(AbstractTransformer.create("ContainerLabels").result instanceof ContainerLabels);
	}

	@Test
	public void initialize_readsTheHostPidNamespaceFromTheAuditConstantsFile(){
		final ContainerTrace trace = new ContainerTrace();
		final DockerRun run = trace.dockerRun(trace.preexisting("containerd-shim", "1200"), "4026532270", 4100, "sh");
		final ContainerLabels transformer = new ContainerLabels();

		assertTrue(transformer.initialize(""));

		assertNotNull(labelsOf(transformer.transform(trace.graph)).get(run.application.hash));
	}

	@Test
	public void transform_beforeInitialize_fails(){
		assertThrows(IllegalStateException.class, () -> new ContainerLabels().transform(new Graph()));
	}

	@Test
	public void quickGrail_transformResolvesToATransformGraphInstruction(){
		final Program program = new QuickGrailQueryResolver().resolveProgram(
				new DSLParserWrapper().fromText("$labeled = $base.transform(ContainerLabels, '')"), environment());
		int found = 0;
		for(int i = 0; i < program.getInstructionsSize(); i++){
			final Instruction<? extends Serializable> instruction = program.getInstruction(i);
			if(instruction instanceof TransformGraph){
				found++;
				assertTrue(instruction.toString().contains("ContainerLabels"), instruction.toString());
			}
		}
		assertEquals(1, found, program.toString());
	}

	@Test
	public void unsimplifiedTrace_vforkCreatesContainerProcessesToo(){
		final ContainerTrace trace = new ContainerTrace(false);
		final DockerRun run = trace.dockerRun(trace.preexisting("containerd-shim", "1200"), "4026532270", 4100, "sh");
		final Process child = trace.spawn(run.application, "sh", "4110", "CLONE_VM|CLONE_VFORK|SIGCHLD", "4026532270");
		assertEquals("vfork", trace.edge(child, run.application).getAnnotation(OPMConstants.EDGE_OPERATION));

		final Map<String, String> labels = labelsOf(transform(trace.graph));

		assertEquals(labels.get(run.application.hash), labels.get(child.hash));
		assertNotNull(labels.get(child.hash));
	}

	////////////////////////////////////////////////////////////////////////////

	abstract static class Scenarios{

		static final String CONTAINER = "4026532270", OTHER_CONTAINER = "4026532280", NESTED = "4026532290",
				DEEPER = "4026532300";

		private final boolean unnamedNewPidNamespaces, labelsRevertedAtExecve;
		ContainerTrace trace;
		Process shim;

		Scenarios(final boolean unnamedNewPidNamespaces, final boolean labelsRevertedAtExecve){
			this.unnamedNewPidNamespaces = unnamedNewPidNamespaces;
			this.labelsRevertedAtExecve = labelsRevertedAtExecve;
		}

		@BeforeEach
		void setUp(){
			trace = new ContainerTrace();
			if(unnamedNewPidNamespaces){
				trace.withUnnamedNewPidNamespaces();
			}
			if(labelsRevertedAtExecve){
				trace.withLabelsRevertedAtExecve();
			}
			shim = trace.preexisting("containerd-shim", "1200");
		}

		// ---------------------------------------------------------------------
		// Helpers

		/** The labels the transformer gives the trace: original vertex hash to container_id. */
		Map<String, String> labels(){
			return labelsOf(transform(trace.graph));
		}

		/** The container_id of the container whose init `creator` created. */
		String idOf(final Process init, final Process creator){
			final AbstractEdge creation = trace.edge(init, creator);
			return init.pidNamespace + ":" + creation.getAnnotation(OPMConstants.EDGE_TIME) + ":"
					+ creation.getAnnotation(OPMConstants.EDGE_EVENT_ID);
		}

		String idOf(final DockerRun run){
			return idOf(run.init, run.stage1Unshared);
		}

		/** Expected labels: each process given is labeled with the id. */
		static final class Expected{
			final Map<String, String> labels = new TreeMap<String, String>();

			Expected with(final String id, final Object... vertices){
				for(final Object vertex : vertices){
					labels.put(hash(vertex), id);
				}
				return this;
			}
		}

		static Expected expect(){
			return new Expected();
		}

		static void assertLabels(final Expected expected, final Map<String, String> actual){
			assertEquals(expected.labels, new TreeMap<String, String>(actual));
		}

		static Object[] inside(final DockerRun run){
			return run.application == null ? new Object[]{run.init, run.initCgroupUnshared, run.thread}
					: new Object[]{run.init, run.initCgroupUnshared, run.thread, run.application};
		}

		static Object[] inside(final DockerExec exec){
			return new Object[]{exec.process, exec.command};
		}

		static Object[] all(final Object[]... groups){
			int size = 0;
			for(final Object[] group : groups){
				size += group.length;
			}
			final Object[] result = new Object[size];
			int i = 0;
			for(final Object[] group : groups){
				for(final Object member : group){
					result[i++] = member;
				}
			}
			return result;
		}

		// ---------------------------------------------------------------------
		// Container starts

		@Test
		void dockerRun_labelsTheInitAndEverythingItStarts(){
			final DockerRun run = trace.dockerRun(shim, CONTAINER, 4100, "nginx");
			final Process worker = trace.spawn(run.application, "nginx", "4110", "SIGCHLD", CONTAINER);
			final Process reloaded = trace.execve(run.application, "nginx-reloaded");
			trace.writes(worker, "/var/log/nginx/access.log");
			trace.exit(worker);

			// runc's processes on the host, the shim and the file are not labeled
			assertLabels(expect().with(idOf(run), all(inside(run), new Object[]{worker, reloaded})), labels());
		}

		@Test
		void containerId_isTheNamespaceAndTheInitsCreationEvent(){
			final DockerRun run = trace.dockerRun(shim, CONTAINER, 4100, "nginx");
			final AbstractEdge creation = trace.edge(run.init, run.stage1Unshared);

			assertEquals(CONTAINER + ":" + creation.getAnnotation(OPMConstants.EDGE_TIME) + ":"
					+ creation.getAnnotation(OPMConstants.EDGE_EVENT_ID), labels().get(run.init.hash));
		}

		@Test
		void dockerExec_labelsTheProcessesItStartsWithTheContainersId(){
			final DockerRun run = trace.dockerRun(shim, CONTAINER, 4100, "nginx");
			final DockerExec exec = trace.dockerExec(shim, run.application, 4200, "sh");
			final Process child = trace.spawn(exec.command, "sh", "4210", "SIGCHLD", CONTAINER);

			// The runc process that entered stays on the host and is not labeled
			assertLabels(expect().with(idOf(run), all(inside(run), inside(exec), new Object[]{child})), labels());
		}

		@Test
		void cloneWithNewPidNamespace_startsAContainer(){
			final Process lxc = trace.hostProgram("lxc-start", 5000);
			final Process child = trace.spawn(lxc, "lxc-start", "5001",
					"CLONE_NEWNS|CLONE_NEWPID|CLONE_NEWUTS|CLONE_NEWIPC|CLONE_NEWNET|SIGCHLD", CONTAINER);
			final Process init = trace.execve(child, "init");
			final Process gettyProcess = trace.spawn(init, "init", "5002", "SIGCHLD", CONTAINER);
			final Process getty = trace.execve(gettyProcess, "getty");

			// lxc-start stays on the host
			assertLabels(expect().with(idOf(child, lxc), child, init, gettyProcess, getty), labels());
		}

		@Test
		void cloneWithNewPidNamespaceButNoSigchld_isRecordedAsCloneAndStillFound(){
			final Process sandbox = trace.hostProgram("sandbox", 5100);
			final Process child = trace.spawn(sandbox, "sandbox", "5101", "CLONE_NEWPID|CLONE_NEWNS", CONTAINER);
			final Process app = trace.execve(child, "app");

			assertLabels(expect().with(idOf(child, sandbox), child, app), labels());
		}

		@Test
		void unshareThenExecve_theNewProgramsFirstChildIsTheInit(){
			// unshare --pid bash: without --fork, bash's first child becomes PID 1
			final Process tool = trace.hostProgram("unshare", 6100);
			final Process toolUnshared = trace.unshare(tool, "CLONE_NEWPID", CONTAINER);
			final Process shell = trace.execve(toolUnshared, "bash");
			final Process child = trace.spawn(shell, "bash", "6101", "SIGCHLD", CONTAINER);
			final Process ls = trace.execve(child, "ls");

			assertLabels(expect().with(idOf(child, shell), child, ls), labels());
		}

		@Test
		void creatorJoiningAnotherNamespaceFirst_stillStartsTheContainer(){
			final DockerRun other = trace.dockerRun(shim, OTHER_CONTAINER, 4100, "redis");
			final Process stage1 = trace.hostProgram("runc:[1:CHILD]", 4300);
			final Process joined = trace.setnsMount(stage1, other.init.mountNamespace);
			final Process unshared = trace.unshare(joined, "CLONE_NEWUTS|CLONE_NEWIPC|CLONE_NEWPID", CONTAINER);
			final Process init = trace.spawn(unshared, "runc:[1:CHILD]", "4301", "CLONE_PARENT|SIGCHLD", CONTAINER);
			final Process app = trace.execve(init, "app");

			assertLabels(expect().with(idOf(other), inside(other)).with(idOf(init, unshared), init, app), labels());
		}

		@Test
		void unshareWhoseNamespaceNeverGetsAProcess_startsNothing(){
			final Process sandbox = trace.hostProgram("sandbox", 6400);
			final Process unshared = trace.unshare(sandbox, "CLONE_NEWPID", CONTAINER);
			trace.exit(unshared);

			assertLabels(expect(), labels());
		}

		@Test
		void secondUnshareBeforeAnyChild_onlyTheSecondNamespaceStarts(){
			// The first namespace dies empty; the child goes to the second
			final Process sandbox = trace.hostProgram("sandbox", 6500);
			final Process intoFirst = trace.unshare(sandbox, "CLONE_NEWPID", CONTAINER);
			final Process intoSecond = trace.unshare(intoFirst, "CLONE_NEWPID", OTHER_CONTAINER);
			final Process init = trace.spawn(intoSecond, "sandbox", "6501", "SIGCHLD", OTHER_CONTAINER);
			final Process app = trace.execve(init, "sh");

			assertLabels(expect().with(idOf(init, intoSecond), init, app), labels());
		}

		@Test
		void laterChildrenOfTheSameUnshare_joinTheContainer(){
			final Process sandbox = trace.hostProgram("sandbox", 6000);
			final Process unshared = trace.unshare(sandbox, "CLONE_NEWPID", CONTAINER);
			final Process first = trace.spawn(unshared, "sandbox", "6001", "SIGCHLD", CONTAINER);
			final Process second = trace.sameMillisecond().spawn(unshared, "sandbox", "6002", "SIGCHLD", CONTAINER);
			final Process firstApp = trace.execve(first, "sh");
			final Process helper = trace.execve(second, "helper");

			assertLabels(expect().with(idOf(first, unshared), first, second, firstApp, helper), labels());
		}

		@Test
		void processReturningToEarlierLabels_startsTwoContainersAndThenJoinsTheFirst(){
			final Process sandbox = trace.hostProgram("sandbox", 6200);
			final Process intoFirst = trace.unshare(sandbox, "CLONE_NEWPID", CONTAINER);
			final Process firstInit = trace.spawn(intoFirst, "sandbox", "6201", "SIGCHLD", CONTAINER);
			final Process firstApp = trace.execve(firstInit, "sh");
			final Process restored = trace.setnsPid(intoFirst, HOST);
			final Process intoSecond = trace.unshare(restored, "CLONE_NEWPID", OTHER_CONTAINER);
			final Process secondInit = trace.spawn(intoSecond, "sandbox", "6202", "SIGCHLD", OTHER_CONTAINER);
			final Process secondApp = trace.execve(secondInit, "sh");
			final Process rejoined = trace.setnsPid(trace.setnsPid(intoSecond, HOST), CONTAINER);
			final Process helper = trace.spawn(rejoined, "sandbox", "6203", "SIGCHLD", CONTAINER);
			final Process helperApp = trace.execve(helper, "helper");
			// The reporter reuses vertices for labels a process had before
			assertEquals(sandbox.hash, restored.hash);

			assertLabels(expect().with(idOf(firstInit, intoFirst), firstInit, firstApp, helper, helperApp)
					.with(idOf(secondInit, intoSecond), secondInit, secondApp), labels());
		}

		@Test
		void unshareBeforeTracing_labelsNothing(){
			// The namespace change happened before tracing, so the first child is not known as PID 1
			final Process tool = trace.preexisting("unshare", "3100");
			final Process child = trace.spawn(tool, "unshare", "3101", "SIGCHLD", CONTAINER);
			trace.execve(child, "sh");

			assertLabels(expect(), labels());
		}

		@Test
		void unshareByProcessWithUnobservedNamespaces_isTakenAsJoiningAndLabelsNothing(){
			// It may have been an unshare of another namespace type after an unshare(CLONE_NEWPID) before tracing
			final Process sandbox = trace.preexisting("sandbox", "3200");
			final Process unshared = trace.unobservedStep(sandbox, "unshare", HOST, CONTAINER);
			final Process child = trace.spawn(unshared, "sandbox", "3201", "SIGCHLD", CONTAINER);
			trace.execve(child, "sh");

			assertLabels(expect(), labels());
		}

		@Test
		void mountNamespaceOnHost_isNotAContainer(){
			final Process session = trace.spawn(trace.preexisting("sshd", "700"), "sshd", "7000", "SIGCHLD", HOST);
			final Process privateMounts = trace.unshare(session, "CLONE_NEWNS", null);
			trace.execve(trace.spawn(privateMounts, "sshd", "7001", "SIGCHLD", HOST), "bash");

			assertLabels(expect(), labels());
		}

		@Test
		void emptyGraph_staysEmpty(){
			final Graph output = transform(new Graph());

			assertTrue(output.vertexSet().isEmpty());
			assertTrue(output.edgeSet().isEmpty());
		}

		// ---------------------------------------------------------------------
		// Inits that never call execve: still containers

		@Test
		void initThatNeverCallsExecve_isLabeledAllTheSame(){
			final DockerRun run = trace.dockerRun(shim, CONTAINER, 4100, null);

			assertLabels(expect().with(idOf(run), inside(run)), labels());
		}

		@Test
		void bubblewrapSandbox_itsReaperInitAndTheAppAreLabeled(){
			// flatpak run → bwrap; bwrap's first process in the sandbox forks the app's process and reaps
			final Process bwrap = trace.execve(trace.hostProgram("flatpak", 5300), "bwrap");
			final Process reaper = trace.spawn(bwrap, "bwrap", "5301",
					"CLONE_NEWNS|CLONE_NEWUSER|CLONE_NEWPID|SIGCHLD", CONTAINER);
			final Process appProcess = trace.spawn(reaper, "bwrap", "5302", "SIGCHLD", CONTAINER);
			final Process app = trace.execve(appProcess, "gedit");

			assertLabels(expect().with(idOf(reaper, bwrap), reaper, appProcess, app), labels());
		}

		@Test
		void nspawnAsPid2_itsStubInitAndTheCommandAreLabeled(){
			// systemd-nspawn --as-pid2: outer child → inner child (PID 1, stub init) → PID 2 runs the command
			final Process nspawn = trace.hostProgram("systemd-nspawn", 5400);
			final Process outer = trace.spawn(nspawn, "systemd-nspawn", "5401", "CLONE_NEWNS|SIGCHLD", HOST);
			final Process stubInit = trace.spawn(outer, "systemd-nspawn", "5402",
					"CLONE_NEWNS|CLONE_NEWIPC|CLONE_NEWPID|CLONE_NEWUTS|SIGCHLD", CONTAINER);
			final Process command = trace.spawn(stubInit, "systemd-nspawn", "5403", "SIGCHLD", CONTAINER);
			final Process bash = trace.execve(command, "bash");

			assertLabels(expect().with(idOf(stubInit, outer), stubInit, command, bash), labels());
		}

		// ---------------------------------------------------------------------
		// What cannot be positively identified stays as it is

		@Test
		void containerStartedBeforeTracing_isNotLabeled_norWhatIsExecdIntoIt(){
			final Process master = trace.preexisting("nginx", "3000");
			final Process masterJoinedMounts = trace.unobservedStep(master, "setns", CONTAINER, CONTAINER);
			final Process worker = trace.spawn(masterJoinedMounts, "nginx", "3001", "SIGCHLD", CONTAINER);
			trace.execve(worker, "logrotate");
			trace.dockerExec(shim, worker, 4200, "sh");

			assertLabels(expect(), labels());
		}

		@Test
		void processFirstSeenInsideAContainer_isNotLinkedToItsStart_soNotLabeled(){
			final DockerRun run = trace.dockerRun(shim, CONTAINER, 4100, "nginx");
			trace.firstSeen("nginx", "4150", CONTAINER);

			assertLabels(expect().with(idOf(run), inside(run)), labels());
		}

		@Test
		void subgraphWithoutAProcesssCreation_leavesThatProcessAndItsDescendantsUnlabeled(){
			final DockerRun run = trace.dockerRun(shim, CONTAINER, 4100, "nginx");
			final Process worker = trace.spawn(run.application, "nginx", "4110", "SIGCHLD", CONTAINER);
			final Process grandchild = trace.spawn(worker, "nginx", "4111", "SIGCHLD", CONTAINER);
			final Graph subgraph = without(trace.graph, trace.edge(worker, run.application));

			final Map<String, String> labels = labelsOf(transform(subgraph));

			assertLabels(expect().with(idOf(run), inside(run)), labels);
			assertNull(labels.get(grandchild.hash));
		}

		@Test
		void subgraphWithoutTheStart_labelsNothing(){
			final DockerRun run = trace.dockerRun(shim, CONTAINER, 4100, "nginx");
			trace.spawn(run.application, "nginx", "4110", "SIGCHLD", CONTAINER);

			assertLabels(expect(), labelsOf(transform(without(trace.graph, trace.edge(run.init, run.stage1Unshared)))));
		}

		@Test
		void subgraphOfProcessesWithoutEdges_labelsNothing(){
			final DockerRun run = trace.dockerRun(shim, CONTAINER, 4100, "nginx");
			final Graph vertices = new Graph();
			for(final Object process : inside(run)){
				vertices.putVertex(((Process)process).vertex);
			}

			assertLabels(expect(), labelsOf(transform(vertices)));
		}

		@Test
		void unreadableNamespaceLabels_areNeverContainerProcesses(){
			// Older kernel modules write 4294967295 for a namespace they could not read
			final Process unreadable = trace.unobservedStep(trace.preexisting("worker", "3200"), "setns",
					ContainerTrace.UNREADABLE, ContainerTrace.UNREADABLE);
			trace.spawn(unreadable, "worker", "3201", "SIGCHLD", ContainerTrace.UNREADABLE);

			assertLabels(expect(), labels());
		}

		@Test
		void killEdges_doNotCarryALabelToTheHost(){
			final DockerRun run = trace.dockerRun(shim, CONTAINER, 4100, "sh");
			final Process host = trace.hostProgram("sleep", 5000);
			trace.kill(run.application, host);
			trace.kill(host, run.application);

			assertLabels(expect().with(idOf(run), inside(run)), labels());
		}

		// ---------------------------------------------------------------------
		// One ID, several containers

		@Test
		void reusedNamespaceIdAndPids_eachContainerGetsItsOwnId(){
			final Process master = trace.preexisting("nginx", "3000");
			final Process masterJoinedMounts = trace.unobservedStep(master, "setns", CONTAINER, CONTAINER);
			final Process oldWorker = trace.spawn(masterJoinedMounts, "nginx", "3001", "SIGCHLD", CONTAINER);
			trace.exit(oldWorker);
			final DockerRun first = trace.dockerRun(shim, CONTAINER, 4100, "nginx");
			final DockerExec exec = trace.dockerExec(shim, first.application, 4200, "sh");
			trace.exit(first.application);
			// Same ID and the same host pids again
			final DockerRun second = trace.dockerRun(shim, CONTAINER, 4100, "redis");
			final Process secondWorker = trace.spawn(second.application, "redis", "4110", "SIGCHLD", CONTAINER);
			final DockerExec secondExec = trace.dockerExec(shim, secondWorker, 4200, "sh");

			assertNotEquals(idOf(first), idOf(second));
			// The container that was running before tracing has no recorded start
			assertLabels(expect().with(idOf(first), all(inside(first), inside(exec)))
					.with(idOf(second), all(inside(second), new Object[]{secondWorker}, inside(secondExec))),
					labels());
		}

		// ---------------------------------------------------------------------
		// Containers started inside containers

		final class Nesting{
			final DockerRun outer, sibling;
			final Process forked, tool, toolUnshared, nestedInit, nestedShell;
			final Process nestedForked, deeperTool, deeperUnshared, deeperInit, deeperShell;

			Nesting(){
				outer = trace.dockerRun(shim, CONTAINER, 4100, "bash");
				forked = trace.spawn(outer.application, "bash", "4120", "SIGCHLD", CONTAINER);
				tool = trace.execve(forked, "unshare");
				toolUnshared = trace.unshare(tool, "CLONE_NEWNS|CLONE_NEWPID", NESTED);
				nestedInit = trace.spawn(toolUnshared, "unshare", "4121", "SIGCHLD", NESTED);
				nestedShell = trace.execve(nestedInit, "bash");
				nestedForked = trace.spawn(nestedShell, "bash", "4122", "SIGCHLD", NESTED);
				deeperTool = trace.execve(nestedForked, "unshare");
				deeperUnshared = trace.unshare(deeperTool, "CLONE_NEWPID", DEEPER);
				deeperInit = trace.spawn(deeperUnshared, "unshare", "4123", "SIGCHLD", DEEPER);
				deeperShell = trace.execve(deeperInit, "sh");
				sibling = trace.dockerRun(shim, OTHER_CONTAINER, 4300, "redis");
			}

			Expected labels(){
				return expect()
						.with(idOf(outer), all(inside(outer), new Object[]{forked, tool, toolUnshared}))
						.with(idOf(nestedInit, toolUnshared),
								nestedInit, nestedShell, nestedForked, deeperTool, deeperUnshared)
						.with(idOf(deeperInit, deeperUnshared), deeperInit, deeperShell)
						.with(idOf(sibling), inside(sibling));
			}
		}

		@Test
		void nestedContainers_eachProcessGetsItsInnermostContainer(){
			final Nesting nesting = new Nesting();

			assertLabels(nesting.labels(), labels());
		}

		@Test
		void nestedContainer_inAReusedOuterId_belongsToTheOuterThatStartedIt(){
			// The outer ID is reused; only the second outer container starts a nested one
			final DockerRun firstOuter = trace.dockerRun(shim, CONTAINER, 4100, "bash");
			trace.exit(firstOuter.application);
			final Nesting nesting = new Nesting();

			final Expected expected = nesting.labels().with(idOf(firstOuter), inside(firstOuter));
			assertLabels(expected, labels());
			assertNotEquals(idOf(firstOuter), idOf(nesting.outer));
		}

		// ---------------------------------------------------------------------
		// Artifacts

		@Test
		void socketOfOneContainer_inItsNetNamespace_getsItsLabel(){
			final DockerRun run = trace.dockerRun(shim, CONTAINER, 4100, "wget");
			final AbstractVertex socket = trace.connects(run.application, "93.184.216.34", run.application.netNamespace);

			assertLabels(expect().with(idOf(run), all(inside(run), new Object[]{socket})), labels());
		}

		@Test
		void socketOfOneContainer_usedByTwoOfItsProcesses_getsItsLabel(){
			final DockerRun run = trace.dockerRun(shim, CONTAINER, 4100, "nginx");
			final Process worker = trace.spawn(run.application, "nginx", "4110", "SIGCHLD", CONTAINER);
			final AbstractVertex socket = trace.connects(run.application, "10.0.0.2", run.application.netNamespace);
			trace.receives(worker, socket);

			assertEquals(idOf(run), labels().get(hash(socket)));
		}

		@Test
		void socketWithoutANetNamespace_isLeft(){
			final DockerRun run = trace.dockerRun(shim, CONTAINER, 4100, "wget");
			final AbstractVertex socket = trace.connects(run.application, "93.184.216.34", null);

			assertNull(labels().get(hash(socket)));
		}

		@Test
		void socketInAnotherNetNamespace_isLeft(){
			final DockerRun run = trace.dockerRun(shim, CONTAINER, 4100, "wget");
			final AbstractVertex socket = trace.connects(run.application, "93.184.216.34", ContainerTrace.HOST_NET);

			assertNull(labels().get(hash(socket)));
		}

		@Test
		void socketAlsoUsedByAHostProcessInTheSameNetNamespace_isLeft(){
			// A matching namespace alone is not enough: nsenter --net puts a host process in it
			final DockerRun run = trace.dockerRun(shim, CONTAINER, 4100, "nginx");
			final AbstractVertex socket = trace.connects(run.application, "10.0.0.2", run.application.netNamespace);
			final Process debugger = trace.setnsOthers(trace.hostProgram("tcpdump", 5000), run.application);
			trace.receives(debugger, socket);

			assertNull(labels().get(hash(socket)));
		}

		@Test
		void socketSharedByTwoContainersInOneNetNamespace_isLeft(){
			// Containers of a pod share a net namespace
			final DockerRun pause = trace.dockerRun(shim, CONTAINER, 4100, "pause");
			final DockerRun app = trace.dockerRun(shim, OTHER_CONTAINER, 4200, "app");
			final Process appInPodNet = trace.setnsOthers(app.application, pause.application);
			final AbstractVertex socket = trace.connects(pause.application, "10.0.0.2", pause.application.netNamespace);
			trace.receives(appInPodNet, socket);

			final Map<String, String> labels = labels();
			assertNull(labels.get(hash(socket)));
			assertEquals(idOf(app), labels.get(appInPodNet.hash));
		}

		@Test
		void socketOfAContainerStartedBeforeTracing_isLeft(){
			final Process master = trace.preexisting("nginx", "3000");
			final Process masterJoined = trace.unobservedStep(master, "setns", CONTAINER, CONTAINER);
			final AbstractVertex socket = trace.connects(masterJoined, "10.0.0.2", masterJoined.netNamespace);

			assertNull(labels().get(hash(socket)));
		}

		@Test
		void systemVMessageQueueOfOneContainer_getsItsLabel(){
			final DockerRun run = trace.dockerRun(shim, CONTAINER, 4100, "postgres");
			final Process worker = trace.spawn(run.application, "postgres", "4110", "SIGCHLD", CONTAINER);
			final AbstractVertex queue = trace.systemV(run.application, OPMConstants.SUBTYPE_SYSV_MSG_Q, "5",
					run.application.ipcNamespace);
			trace.reads(worker, queue);

			assertEquals(idOf(run), labels().get(hash(queue)));
		}

		@Test
		void systemVSharedMemoryOfTwoContainersInOneIpcNamespace_isLeft(){
			final DockerRun first = trace.dockerRun(shim, CONTAINER, 4100, "postgres");
			final DockerRun second = trace.dockerRun(shim, OTHER_CONTAINER, 4200, "reader");
			final Process secondInIpc = trace.setnsOthers(second.application, first.application);
			final AbstractVertex memory = trace.systemV(first.application, OPMConstants.SUBTYPE_SYSV_SHARED_MEMORY,
					"7", first.application.ipcNamespace);
			trace.reads(secondInIpc, memory);

			assertNull(labels().get(hash(memory)));
		}

		@Test
		void systemVObjectWithAnotherIpcNamespace_isLeft(){
			final DockerRun run = trace.dockerRun(shim, CONTAINER, 4100, "postgres");
			final AbstractVertex memory = trace.systemV(run.application, OPMConstants.SUBTYPE_SYSV_SHARED_MEMORY,
					"7", ContainerTrace.HOST_IPC);

			assertNull(labels().get(hash(memory)));
		}

		@Test
		void filesAreNeverLabeled(){
			final DockerRun run = trace.dockerRun(shim, CONTAINER, 4100, "nginx");
			final AbstractVertex log = trace.writes(run.application, "/var/log/nginx/access.log");

			assertNull(labels().get(hash(log)));
		}

		// ---------------------------------------------------------------------
		// The output

		@Test
		void output_isACopyOfTheWholeInputWithTheLabelAdded(){
			final DockerRun run = trace.dockerRun(shim, CONTAINER, 4100, "wget");
			trace.connects(run.application, "93.184.216.34", run.application.netNamespace);
			trace.writes(run.application, "/tmp/index.html");
			trace.dockerRun(shim, OTHER_CONTAINER, 4200, "redis");
			final Map<String, Map<String, String>> inputVertices = annotationsByHash(trace.graph);
			final int inputEdges = trace.graph.edgeSet().size();

			final Graph output = transform(trace.graph);

			// The input is unchanged
			assertEquals(inputVertices, annotationsByHash(trace.graph));
			assertEquals(inputEdges, trace.graph.edgeSet().size());
			// Every vertex is copied, with container_id added to those labeled
			final Map<String, String> labels = labelsOf(output);
			assertEquals(inputVertices.size(), output.vertexSet().size());
			for(final AbstractVertex vertex : output.vertexSet()){
				final Map<String, String> annotations = vertex.getCopyOfAnnotations();
				final String label = annotations.remove(ContainerLabels.CONTAINER_ID);
				final String original = originalHash(vertex);
				assertEquals(inputVertices.get(original), annotations);
				assertEquals(label == null, !labels.containsKey(original));
				if(label == null){
					assertEquals(original, vertex.bigHashCode(), "an unlabeled vertex keeps its hash");
				}
			}
			// Every edge is copied, with its annotations, between the copies of its endpoints
			assertEquals(inputEdges, output.edgeSet().size());
			final Map<String, Integer> inputEdgeKeys = edgeKeys(trace.graph);
			assertEquals(inputEdgeKeys, edgeKeys(output));
		}

		@Test
		void output_transformedAgain_isTheSame(){
			final DockerRun run = trace.dockerRun(shim, CONTAINER, 4100, "wget");
			trace.connects(run.application, "93.184.216.34", run.application.netNamespace);
			trace.dockerExec(shim, run.application, 4200, "sh");
			final Graph once = transform(trace.graph);

			assertTrue(transform(once).equals(once));
		}

		@Test
		void inputWithItsLabeledCopy_asQuickGrailStoresIt_givesTheSameLabelsAndNothingNew(){
			final DockerRun first = trace.dockerRun(shim, CONTAINER, 4100, "wget");
			trace.connects(first.application, "93.184.216.34", first.application.netNamespace);
			trace.exit(first.application);
			trace.dockerRun(shim, CONTAINER, 4300, "redis");
			// A clone(CLONE_NEWPID) start: its labeled copy is a second start at the same instant
			final Process lxc = trace.hostProgram("lxc-start", 5000);
			final Process init = trace.execve(trace.spawn(lxc, "lxc-start", "5001",
					"CLONE_NEWNS|CLONE_NEWPID|CLONE_NEWNET|SIGCHLD", OTHER_CONTAINER), "init");
			trace.spawn(init, "init", "5002", "SIGCHLD", OTHER_CONTAINER);
			final Graph once = transform(trace.graph);
			final Graph base = new Graph();
			base.union(trace.graph);
			base.union(once);

			final Graph again = transform(base);

			assertEquals(new TreeMap<String, String>(), difference(once, again));
			assertEquals(once.edgeSet(), again.edgeSet());
		}

		@Test
		void output_isTheSameEveryTime(){
			trace.dockerRun(shim, CONTAINER, 4100, "wget");
			trace.dockerRun(shim, CONTAINER, 4200, "wget");

			assertTrue(transform(trace.graph).equals(transform(trace.graph)));
		}
	}

	////////////////////////////////////////////////////////////////////////////
	// Shared helpers

	static Graph transform(final Graph input){
		final ContainerLabels transformer = new ContainerLabels();
		assertTrue(transformer.initialize(ContainerLabels.ARGUMENT_HOST_PID_NAMESPACE + "=" + HOST));
		return transformer.transform(input);
	}

	/** The vertices in only one of the graphs: hash to "only in first/second: annotations". */
	static Map<String, String> difference(final Graph first, final Graph second){
		final Map<String, String> result = new TreeMap<String, String>();
		for(final AbstractVertex vertex : first.vertexSet()){
			if(!second.vertexSet().contains(vertex)){
				result.put(vertex.bigHashCode(), "only in first: " + vertex.getCopyOfAnnotations());
			}
		}
		for(final AbstractVertex vertex : second.vertexSet()){
			if(!first.vertexSet().contains(vertex)){
				result.put(vertex.bigHashCode(), "only in second: " + vertex.getCopyOfAnnotations());
			}
		}
		return result;
	}

	/** Original vertex hash (without container_id) to the container_id the output gives it. */
	static Map<String, String> labelsOf(final Graph output){
		final Map<String, String> labels = new HashMap<String, String>();
		for(final AbstractVertex vertex : output.vertexSet()){
			final String label = vertex.getAnnotation(ContainerLabels.CONTAINER_ID);
			if(label != null){
				labels.put(originalHash(vertex), label);
			}
		}
		return labels;
	}

	static String originalHash(final AbstractVertex vertex){
		final Vertex original = new Vertex();
		original.addAnnotations(vertex.getCopyOfAnnotations());
		original.removeAnnotation(ContainerLabels.CONTAINER_ID);
		return original.bigHashCode();
	}

	static String hash(final Object vertex){
		if(vertex instanceof Process){
			return ((Process)vertex).hash;
		}
		return ((AbstractVertex)vertex).bigHashCode();
	}

	static Graph without(final Graph graph, final AbstractEdge removed){
		final Graph result = new Graph();
		result.vertexSet().addAll(graph.vertexSet());
		for(final AbstractEdge edge : graph.edgeSet()){
			if(!edge.equals(removed)){
				result.putEdge(edge);
			}
		}
		return result;
	}

	static Map<String, Map<String, String>> annotationsByHash(final Graph graph){
		final Map<String, Map<String, String>> result = new HashMap<String, Map<String, String>>();
		for(final AbstractVertex vertex : graph.vertexSet()){
			result.put(vertex.bigHashCode(), vertex.getCopyOfAnnotations());
		}
		return result;
	}

	/** Each edge as (original child hash, annotations, original parent hash), counted. */
	static Map<String, Integer> edgeKeys(final Graph graph){
		final Map<String, Integer> keys = new TreeMap<String, Integer>();
		for(final AbstractEdge edge : graph.edgeSet()){
			final String key = originalHash(edge.getChildVertex()) + " " + edge.getCopyOfAnnotations() + " "
					+ originalHash(edge.getParentVertex());
			final Integer count = keys.get(key);
			keys.put(key, count == null ? 1 : count + 1);
		}
		return keys;
	}

	static AbstractQueryEnvironment environment(){
		return new AbstractQueryEnvironment("spade_base_graph"){
			@Override public void doGarbageCollection(){}
			@Override public void createSymbolStorageIfNotPresent(){}
			@Override public void deleteSymbolStorageIfPresent(){}
			@Override public int readIdCount(){ return 0; }
			@Override public Map<String, spade.query.quickgrail.entities.Graph> readGraphSymbols(){
				return new HashMap<String, spade.query.quickgrail.entities.Graph>(); }
			@Override public void readRemoteSymbols(spade.query.quickgrail.entities.Graph graph){}
			@Override public Map<String, String> readMetadataSymbols(){ return new HashMap<String, String>(); }
			@Override public Map<String, String> readPredicateSymbols(){ return new HashMap<String, String>(); }
			@Override public void saveIdCounter(int idCounter){}
			@Override public void saveGraphSymbol(String symbol, String graphName, boolean symbolNameWasPresent){}
			@Override public void saveRemoteSymbol(spade.query.quickgrail.entities.Graph graph,
					spade.query.quickgrail.entities.Graph.Remote remote){}
			@Override public void saveMetadataSymbol(String symbol, String metadataName, boolean symbolNameWasPresent){}
			@Override public void savePredicateSymbol(String symbol, String predicate, boolean symbolNameWasPresent){}
			@Override public void deleteGraphSymbol(String symbol){}
			@Override public void deleteRemoteSymbol(spade.query.quickgrail.entities.Graph graph,
					spade.query.quickgrail.entities.Graph.Remote remote){}
			@Override public void deleteRemoteSymbols(spade.query.quickgrail.entities.Graph graph){}
			@Override public void deleteMetadataSymbol(String symbol){}
			@Override public void deletePredicateSymbol(String symbol){}
		};
	}
}
