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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static spade.query.quickgrail.instruction.ContainerTrace.HOST;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import spade.query.execution.Context;
import spade.query.quickgrail.core.QueriedEdge;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.instruction.ContainerTrace.DockerExec;
import spade.query.quickgrail.instruction.ContainerTrace.DockerRun;
import spade.query.quickgrail.instruction.ContainerTrace.Process;
import spade.query.quickgrail.instruction.InMemoryQueryHarness.AdjacencySemantics;

/**
 * {@link GetContainerInit} on traces shaped like the Audit reporter's output
 * (see {@link ContainerTrace}). Every scenario runs under both adjacency
 * semantics of the storage backends.
 */
public class GetContainerInitIntegrationTest{

	@Nested
	class PostgreSQLAndQuickstepAdjacency extends Scenarios{
		PostgreSQLAndQuickstepAdjacency(){
			super(AdjacencySemantics.SOURCES_ALWAYS);
		}
	}

	@Nested
	class Neo4jAdjacency extends Scenarios{
		Neo4jAdjacency(){
			super(AdjacencySemantics.SOURCES_VIA_EDGES);
		}
	}

	abstract static class Scenarios{

		static final String CONTAINER = "4026532270", OTHER_CONTAINER = "4026532280", NESTED = "4026532290";

		private final AdjacencySemantics semantics;
		InMemoryQueryHarness harness;
		ContainerTrace trace;

		Scenarios(final AdjacencySemantics semantics){
			this.semantics = semantics;
		}

		@BeforeEach
		void setUp(){
			harness = new InMemoryQueryHarness(semantics);
			trace = new ContainerTrace(harness);
		}

		// ---------------------------------------------------------------------
		// Scenario building blocks

		Process shim(){
			return trace.preexisting("containerd-shim", "1200");
		}

		/** getContainerInit's result for a docker run: unshare step, init creation, init versions up to the application. */
		Set<String> initVertices(final DockerRun run){
			return set(run.stage1.hash, run.stage1Unshared.hash, run.init.hash, run.initCgroupUnshared.hash,
					run.application.hash);
		}

		Set<String> initEdges(final DockerRun run){
			return set(trace.edge(run.stage1Unshared, run.stage1), trace.edge(run.init, run.stage1Unshared),
					trace.edge(run.initCgroupUnshared, run.init), trace.edge(run.application, run.initCgroupUnshared));
		}

		// ---------------------------------------------------------------------
		// Running the method

		final class Result{
			final Set<String> vertices = new TreeSet<String>();
			final Set<String> edges = new TreeSet<String>();

			void assertEmpty(){
				assertEquals(set(), vertices, "vertices");
				assertEquals(set(), edges, "edges");
			}
		}

		Result getContainerInit(){
			final Graph target = harness.executor.createNewGraph();
			new GetContainerInit(target, harness.baseGraph, HOST).exec(new Context(harness.executor));
			final Result result = new Result();
			result.vertices.addAll(harness.executor.exportVertices(target).keySet());
			for(final QueriedEdge edge : harness.executor.exportEdges(target)){
				result.edges.add(edge.edgeHash);
			}
			return result;
		}

		ContainerAnalysis.Crossings crossings(){
			return new ContainerAnalysis(harness.executor, harness.baseGraph, HOST).crossings();
		}

		static List<String> children(final List<ContainerAnalysis.Crossing> crossings){
			final List<String> hashes = new ArrayList<String>();
			for(final ContainerAnalysis.Crossing crossing : crossings){
				hashes.add(crossing.childHash);
			}
			return hashes;
		}

		static Set<String> set(final String... values){
			return new TreeSet<String>(Arrays.asList(values));
		}

		@SafeVarargs
		static Set<String> union(final Set<String>... sets){
			final Set<String> result = new TreeSet<String>();
			for(final Set<String> set : sets){
				result.addAll(set);
			}
			return result;
		}

		// ---------------------------------------------------------------------
		// Container starts

		@Test
		void dockerRun_returnsTheUnshareStepAndTheInitUpToTheApplication(){
			final Process shim = shim();
			final DockerRun run = trace.dockerRun(shim, CONTAINER, 4100, "nginx");
			final Process worker = trace.spawn(run.application, "nginx", "4110", "SIGCHLD", CONTAINER);
			trace.execve(run.application, "nginx-reloaded");
			trace.writes(run.application, "/var/log/nginx/access.log");
			trace.exit(worker);

			final Result result = getContainerInit();

			assertEquals(initVertices(run), result.vertices);
			assertEquals(initEdges(run), result.edges);
		}

		@Test
		void dockerExec_entersAContainerWithoutStartingOne(){
			final Process shim = shim();
			final DockerRun run = trace.dockerRun(shim, CONTAINER, 4100, "nginx");
			final DockerExec exec = trace.dockerExec(shim, run.application, 4200, "sh");

			final ContainerAnalysis.Crossings crossings = crossings();
			assertEquals(Arrays.asList(run.init.hash), children(crossings.inits));
			assertEquals(Arrays.asList(exec.process.hash), children(crossings.entries));

			final Result result = getContainerInit();
			assertEquals(initVertices(run), result.vertices);
			assertEquals(initEdges(run), result.edges);
		}

		@Test
		void dockerExecAlone_returnsNothing(){
			final Process container = trace.preexisting("nginx", "3000");
			final Process worker = trace.spawn(container, "nginx", "3001", "SIGCHLD", CONTAINER);
			final Process runc = trace.hostProgram("runc", 4200);
			final Process joined = trace.setnsPid(runc, CONTAINER);
			trace.execve(trace.spawn(joined, "runc", "4201", "CLONE_PARENT|SIGCHLD", CONTAINER), "sh");
			trace.exit(worker);

			getContainerInit().assertEmpty();
		}

		@Test
		void cloneWithNewPidNamespace_startsAContainer(){
			final Process lxc = trace.hostProgram("lxc-start", 5000);
			final Process child = trace.spawn(lxc, "lxc-start", "5001",
					"CLONE_NEWNS|CLONE_NEWPID|CLONE_NEWUTS|CLONE_NEWIPC|CLONE_NEWNET|SIGCHLD", CONTAINER);
			final Process init = trace.execve(child, "init");
			trace.execve(trace.spawn(init, "init", "5002", "SIGCHLD", CONTAINER), "getty");

			final Result result = getContainerInit();

			assertEquals(set(lxc.hash, child.hash, init.hash), result.vertices);
			assertEquals(set(trace.edge(child, lxc), trace.edge(init, child)), result.edges);
		}

		@Test
		void cloneWithNewPidNamespaceButNoSigchld_isRecordedAsCloneAndStillFound(){
			final Process sandbox = trace.hostProgram("sandbox", 5100);
			final Process child = trace.spawn(sandbox, "sandbox", "5101", "CLONE_NEWPID|CLONE_NEWNS", CONTAINER);
			final Process app = trace.execve(child, "app");

			final Result result = getContainerInit();

			assertEquals(set(sandbox.hash, child.hash, app.hash), result.vertices);
			assertEquals(set(trace.edge(child, sandbox), trace.edge(app, child)), result.edges);
		}

		@Test
		void unshareThenExecve_theNewProgramsFirstChildIsTheInit(){
			// unshare --pid bash: without --fork, bash's first child becomes PID 1
			final Process tool = trace.hostProgram("unshare", 6100);
			final Process toolUnshared = trace.unshare(tool, "CLONE_NEWPID", CONTAINER);
			final Process shell = trace.execve(toolUnshared, "bash");
			final Process child = trace.spawn(shell, "bash", "6101", "SIGCHLD", CONTAINER);
			final Process ls = trace.execve(child, "ls");

			final Result result = getContainerInit();

			assertEquals(set(tool.hash, toolUnshared.hash, shell.hash, child.hash, ls.hash), result.vertices);
			assertEquals(set(trace.edge(toolUnshared, tool), trace.edge(shell, toolUnshared),
					trace.edge(child, shell), trace.edge(ls, child)), result.edges);
		}

		@Test
		void creatorJoiningAnotherNamespaceFirst_includesThoseSteps(){
			final Process shim = shim();
			final DockerRun other = trace.dockerRun(shim, OTHER_CONTAINER, 4100, "redis");
			final Process stage1 = trace.hostProgram("runc:[1:CHILD]", 4300);
			final Process joined = trace.setnsMount(stage1, other.init.mountNamespace);
			final Process unshared = trace.unshare(joined, "CLONE_NEWUTS|CLONE_NEWIPC|CLONE_NEWPID", CONTAINER);
			final Process init = trace.spawn(unshared, "runc:[1:CHILD]", "4301", "CLONE_PARENT|SIGCHLD", CONTAINER);
			final Process app = trace.execve(init, "app");

			final Result result = getContainerInit();

			assertEquals(union(initVertices(other), set(stage1.hash, joined.hash, unshared.hash, init.hash, app.hash)),
					result.vertices);
			assertEquals(union(initEdges(other), set(trace.edge(joined, stage1), trace.edge(unshared, joined),
					trace.edge(init, unshared), trace.edge(app, init))), result.edges);
		}

		@Test
		void nestedContainer_isReportedWithItsCreatorInsideTheOuterContainer(){
			final Process shim = shim();
			final DockerRun outer = trace.dockerRun(shim, CONTAINER, 4100, "bash");
			final Process tool = trace.execve(
					trace.spawn(outer.application, "bash", "4120", "SIGCHLD", CONTAINER), "unshare");
			final Process toolUnshared = trace.unshare(tool, "CLONE_NEWNS|CLONE_NEWPID", NESTED);
			final Process innerInit = trace.spawn(toolUnshared, "unshare", "4121", "SIGCHLD", NESTED);
			final Process innerShell = trace.execve(innerInit, "sh");

			final Result result = getContainerInit();

			assertEquals(union(initVertices(outer), set(tool.hash, toolUnshared.hash, innerInit.hash, innerShell.hash)),
					result.vertices);
			assertEquals(union(initEdges(outer), set(trace.edge(toolUnshared, tool), trace.edge(innerInit, toolUnshared),
					trace.edge(innerShell, innerInit))), result.edges);
		}

		@Test
		void laterChildrenOfTheSameUnshare_enterTheNamespace(){
			final Process sandbox = trace.hostProgram("sandbox", 6000);
			final Process unshared = trace.unshare(sandbox, "CLONE_NEWPID", CONTAINER);
			final Process first = trace.spawn(unshared, "sandbox", "6001", "SIGCHLD", CONTAINER);
			final Process second = trace.sameMillisecond().spawn(unshared, "sandbox", "6002", "SIGCHLD", CONTAINER);
			final Process firstApp = trace.execve(first, "sh");
			trace.execve(second, "helper");

			final ContainerAnalysis.Crossings crossings = crossings();
			assertEquals(Arrays.asList(first.hash), children(crossings.inits));
			assertEquals(Arrays.asList(second.hash), children(crossings.entries));

			final Result result = getContainerInit();
			assertEquals(set(sandbox.hash, unshared.hash, first.hash, firstApp.hash), result.vertices);
			assertEquals(set(trace.edge(unshared, sandbox), trace.edge(first, unshared),
					trace.edge(firstApp, first)), result.edges);
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
			trace.execve(helper, "helper");
			// The reporter reuses vertices for labels a process had before
			assertEquals(sandbox.hash, restored.hash);
			assertEquals(intoFirst.hash, rejoined.hash);

			final ContainerAnalysis.Crossings crossings = crossings();
			assertEquals(Arrays.asList(firstInit.hash, secondInit.hash), children(crossings.inits));
			assertEquals(Arrays.asList(helper.hash), children(crossings.entries));

			final Result result = getContainerInit();
			assertEquals(set(sandbox.hash, intoFirst.hash, intoSecond.hash, firstInit.hash, secondInit.hash,
					firstApp.hash, secondApp.hash), result.vertices);
			assertFalse(result.vertices.contains(helper.hash));
		}

		@Test
		void reusedNamespaceIdAndPids_startupsStaySeparate(){
			final Process shim = shim();
			final DockerRun first = trace.dockerRun(shim, CONTAINER, 4100, "nginx");
			trace.exit(first.application);
			final DockerRun second = trace.dockerRun(shim, CONTAINER, 4100, "redis");

			final ContainerAnalysis.Crossings crossings = crossings();
			assertEquals(Arrays.asList(first.init.hash, second.init.hash), children(crossings.inits));
			assertEquals(set(), new TreeSet<String>(children(crossings.entries)));

			final Result result = getContainerInit();
			assertEquals(union(initVertices(first), initVertices(second)), result.vertices);
			assertEquals(union(initEdges(first), initEdges(second)), result.edges);
		}

		// ---------------------------------------------------------------------
		// Nothing to report

		@Test
		void containerStartedBeforeTracing_isNotReported(){
			final Process master = trace.preexisting("nginx", "3000");
			final Process worker = trace.spawn(master, "nginx", "3050", "SIGCHLD", CONTAINER);
			trace.execve(worker, "logrotate");

			getContainerInit().assertEmpty();
		}

		@Test
		void unshareBeforeTracing_isNotSeen(){
			// Limitation: the namespace change happened before tracing, so the first child is not known as PID 1
			final Process tool = trace.preexisting("unshare", "3100");
			final Process child = trace.spawn(tool, "unshare", "3101", "SIGCHLD", CONTAINER);
			trace.execve(child, "sh");

			getContainerInit().assertEmpty();
		}

		@Test
		void unshareByProcessWithUnobservedNamespaces_isTakenAsJoining(){
			// It may have been an unshare of another namespace type after an unshare(CLONE_NEWPID) before tracing
			final Process sandbox = trace.preexisting("sandbox", "3200");
			final Process unshared = trace.unobservedStep(sandbox, "unshare", HOST, CONTAINER);
			final Process child = trace.spawn(unshared, "sandbox", "3201", "SIGCHLD", CONTAINER);
			trace.execve(child, "sh");

			final ContainerAnalysis.Crossings crossings = crossings();
			assertEquals(Arrays.asList(), children(crossings.inits));
			assertEquals(Arrays.asList(child.hash), children(crossings.entries));
			getContainerInit().assertEmpty();
		}

		@Test
		void mountNamespaceOnHost_isNotAContainer(){
			final Process session = trace.spawn(trace.preexisting("sshd", "700"), "sshd", "7000", "SIGCHLD", HOST);
			final Process privateMounts = trace.unshare(session, "CLONE_NEWNS", null);
			trace.execve(trace.spawn(privateMounts, "sshd", "7001", "SIGCHLD", HOST), "bash");

			getContainerInit().assertEmpty();
		}

		@Test
		void emptyGraph_returnsNothing(){
			getContainerInit().assertEmpty();
		}

		// ---------------------------------------------------------------------
		// Incomplete starts

		@Test
		void initThatNeverCallsExecve_failsLoudly(){
			final DockerRun run = trace.dockerRun(shim(), CONTAINER, 4100, null);

			final RuntimeException error = assertThrows(RuntimeException.class, () -> getContainerInit());

			assertTrue(error.getMessage().startsWith(
					"getContainerInit: 1 container init process(es) never reached execve in this trace: "),
					error.getMessage());
			assertTrue(error.getMessage().contains("(host pid " + run.init.pid + ", pid namespace " + CONTAINER + ")"),
					error.getMessage());
		}

		@Test
		void reusedNamespaceIdAndPids_doNotHideAnInitThatNeverCallsExecve(){
			final Process shim = shim();
			final DockerRun first = trace.dockerRun(shim, CONTAINER, 4100, "nginx");
			trace.exit(first.application);
			final DockerRun second = trace.dockerRun(shim, CONTAINER, 4100, null);
			assertEquals(first.init.pid, second.init.pid);

			final RuntimeException error = assertThrows(RuntimeException.class, () -> getContainerInit());

			assertTrue(error.getMessage().contains(" 1 container init process(es) "), error.getMessage());
		}
	}
}
