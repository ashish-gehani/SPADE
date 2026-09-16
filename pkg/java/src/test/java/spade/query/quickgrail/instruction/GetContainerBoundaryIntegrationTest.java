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
 * {@link GetContainerBoundary} on traces shaped like the Audit reporter's output
 * (see {@link ContainerTrace}). Every scenario runs under both adjacency
 * semantics of the storage backends.
 */
public class GetContainerBoundaryIntegrationTest{

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

		static final String CONTAINER = "4026532270", OTHER_CONTAINER = "4026532280", NESTED = "4026532290",
				DEEPER = "4026532291", UNUSED = "4026532999";

		private final AdjacencySemantics semantics;
		InMemoryQueryHarness harness;
		ContainerTrace trace;
		Process shim;

		Scenarios(final AdjacencySemantics semantics){
			this.semantics = semantics;
		}

		@BeforeEach
		void setUp(){
			harness = new InMemoryQueryHarness(semantics);
			trace = new ContainerTrace(harness);
			shim = trace.preexisting("containerd-shim", "1200");
		}

		// ---------------------------------------------------------------------
		// Running the method

		static final class Result{
			final Set<String> vertices = new TreeSet<String>();
			final Set<String> edges = new TreeSet<String>();
		}

		Result getContainerBoundary(){
			return run(new GetContainerBoundary(newGraph(), harness.baseGraph, HOST));
		}

		Result getContainerBoundary(final String pidNamespace){
			return run(new GetContainerBoundary(newGraph(), harness.baseGraph, HOST, pidNamespace, null));
		}

		Result getContainerBoundary(final String pidNamespace, final int number){
			return run(new GetContainerBoundary(newGraph(), harness.baseGraph, HOST, pidNamespace, number));
		}

		Result getContainerBoundary(final Process... seeds){
			final Graph seedGraph = newGraph();
			final ArrayList<String> hashes = new ArrayList<String>();
			for(final Process seed : seeds){
				hashes.add(seed.hash);
			}
			harness.executor.insertLiteralVertex(seedGraph, hashes);
			return run(new GetContainerBoundary(newGraph(), harness.baseGraph, HOST, seedGraph));
		}

		private Result run(final GetContainerBoundary instruction){
			instruction.exec(new Context(harness.executor));
			final Result result = new Result();
			result.vertices.addAll(harness.executor.exportVertices(instruction.targetGraph).keySet());
			for(final QueriedEdge edge : harness.executor.exportEdges(instruction.targetGraph)){
				result.edges.add(edge.edgeHash);
			}
			return result;
		}

		private Graph newGraph(){
			return harness.executor.createNewGraph();
		}

		/** The boundary of the given container processes: them, their neighbors, and all edges among those. */
		Result boundaryOf(final Process... members){
			final Set<String> memberHashes = new TreeSet<String>();
			for(final Process member : members){
				memberHashes.add(member.hash);
			}
			final Result expected = new Result();
			expected.vertices.addAll(memberHashes);
			for(final QueriedEdge edge : harness.executor.edgesByHash.values()){
				if(memberHashes.contains(edge.childHash)){
					expected.vertices.add(edge.parentHash);
				}
				if(memberHashes.contains(edge.parentHash)){
					expected.vertices.add(edge.childHash);
				}
			}
			for(final QueriedEdge edge : harness.executor.edgesByHash.values()){
				if(expected.vertices.contains(edge.childHash) && expected.vertices.contains(edge.parentHash)){
					expected.edges.add(edge.edgeHash);
				}
			}
			return expected;
		}

		static void assertResult(final Result expected, final Result actual){
			assertEquals(expected.vertices, actual.vertices, "vertices");
			assertEquals(expected.edges, actual.edges, "edges");
		}

		static Process[] processes(final Process[]... groups){
			final ArrayList<Process> all = new ArrayList<Process>();
			for(final Process[] group : groups){
				all.addAll(Arrays.asList(group));
			}
			return all.toArray(new Process[0]);
		}

		static Process[] inside(final DockerRun run){
			return new Process[]{run.init, run.initCgroupUnshared, run.thread, run.application};
		}

		static Process[] inside(final DockerExec exec){
			return new Process[]{exec.process, exec.command};
		}

		// ---------------------------------------------------------------------
		// Every container, and one container by ID

		@Test
		void everyContainer_isItsProcessesWhatTheyTouchAndTheEdgesBetween(){
			final DockerRun web = trace.dockerRun(shim, CONTAINER, 4100, "nginx");
			final Process worker = trace.spawn(web.application, "nginx", "4110", "SIGCHLD", CONTAINER);
			final String log = trace.writes(worker, "/var/log/nginx/access.log");
			final Process logrotate = trace.hostProgram("logrotate", 5000);
			trace.writes(logrotate, "/var/log/nginx/access.log");
			final DockerExec exec = trace.dockerExec(shim, web.application, 4200, "sh");
			final DockerRun cache = trace.dockerRun(shim, OTHER_CONTAINER, 4300, "redis");

			final Result result = getContainerBoundary();

			assertResult(boundaryOf(processes(inside(web), new Process[]{worker}, inside(exec), inside(cache))),
					result);
			// The runc processes that created or entered a container, and the file, are at the boundary
			assertTrue(result.vertices.containsAll(Arrays.asList(web.stage1Unshared.hash, exec.joined.hash, log)));
			assertTrue(result.edges.contains(trace.edge(web.init, web.stage1Unshared)));
			// Host processes one step further out and processes with unobserved namespaces are not
			assertFalse(result.vertices.contains(web.stage1.hash));
			assertFalse(result.vertices.contains(exec.joiningPid.hash));
			assertFalse(result.vertices.contains(logrotate.hash));
			assertFalse(result.vertices.contains(shim.hash));
		}

		@Test
		void pidNamespaceId_selectsThatContainerOnly(){
			final DockerRun web = trace.dockerRun(shim, CONTAINER, 4100, "nginx");
			final Process worker = trace.spawn(web.application, "nginx", "4110", "SIGCHLD", CONTAINER);
			final DockerExec exec = trace.dockerExec(shim, web.application, 4200, "sh");
			trace.dockerRun(shim, OTHER_CONTAINER, 4300, "redis");

			assertResult(boundaryOf(processes(inside(web), new Process[]{worker}, inside(exec))),
					getContainerBoundary(CONTAINER));
		}

		@Test
		void containerRunningBeforeTracing_isOneContainer(){
			final Process master = trace.preexisting("nginx", "3000");
			final Process masterJoinedMounts = trace.unobservedStep(master, "setns", CONTAINER, CONTAINER);
			final Process worker = trace.spawn(masterJoinedMounts, "nginx", "3001", "SIGCHLD", CONTAINER);

			final Result expected = boundaryOf(masterJoinedMounts, worker);
			assertResult(expected, getContainerBoundary(CONTAINER));
			assertResult(expected, getContainerBoundary(CONTAINER, 1));
			assertResult(expected, getContainerBoundary());
		}

		@Test
		void unknownPidNamespaceId_returnsNothing_butANumberedContainerMustExist(){
			trace.dockerRun(shim, CONTAINER, 4100, "nginx");

			assertResult(new Result(), getContainerBoundary(UNUSED));
			final RuntimeException error = assertThrows(RuntimeException.class,
					() -> getContainerBoundary(UNUSED, 1));
			assertEquals("getContainerBoundary: PID namespace " + UNUSED + " belonged to 0 container(s) in this trace,"
					+ " so there is no container 1.", error.getMessage());
		}

		@Test
		void emptyGraph_returnsNothing(){
			assertResult(new Result(), getContainerBoundary());
		}

		// ---------------------------------------------------------------------
		// One ID, several containers

		final class ReusedId{
			final Process masterJoinedMounts, oldWorker;
			final DockerRun first, second;
			final DockerExec exec;
			final Process secondWorker;

			ReusedId(){
				final Process master = trace.preexisting("nginx", "3000");
				masterJoinedMounts = trace.unobservedStep(master, "setns", CONTAINER, CONTAINER);
				oldWorker = trace.spawn(masterJoinedMounts, "nginx", "3001", "SIGCHLD", CONTAINER);
				trace.exit(oldWorker);
				first = trace.dockerRun(shim, CONTAINER, 4100, "nginx");
				exec = trace.dockerExec(shim, first.application, 4200, "sh");
				trace.exit(first.application);
				// Same ID and the same host pids again
				second = trace.dockerRun(shim, CONTAINER, 4100, "redis");
				secondWorker = trace.spawn(second.application, "redis", "4110", "SIGCHLD", CONTAINER);
			}
		}

		@Test
		void reusedPidNamespaceId_failsListingTheContainers(){
			final ReusedId reused = new ReusedId();

			final RuntimeException error = assertThrows(RuntimeException.class,
					() -> getContainerBoundary(CONTAINER));

			assertEquals("getContainerBoundary: PID namespace " + CONTAINER + " belonged to 3 containers in this"
					+ " trace, since the kernel reuses IDs. Pick one with getContainerBoundary('" + CONTAINER
					+ "', <number>):"
					+ "\n  1: running when tracing started"
					+ "\n  2: started " + reused.first.init.time + ", first process runc:[1:CHILD] (host pid 4103)"
					+ "\n  3: started " + reused.second.init.time + ", first process runc:[1:CHILD] (host pid 4103)",
					error.getMessage());
		}

		@Test
		void reusedPidNamespaceId_numberSelectsOneContainer(){
			final ReusedId reused = new ReusedId();

			assertResult(boundaryOf(reused.masterJoinedMounts, reused.oldWorker), getContainerBoundary(CONTAINER, 1));
			assertResult(boundaryOf(processes(inside(reused.first), inside(reused.exec))),
					getContainerBoundary(CONTAINER, 2));
			assertResult(boundaryOf(processes(inside(reused.second), new Process[]{reused.secondWorker})),
					getContainerBoundary(CONTAINER, 3));
			final RuntimeException error = assertThrows(RuntimeException.class,
					() -> getContainerBoundary(CONTAINER, 4));
			assertTrue(error.getMessage().endsWith("belonged to 3 container(s) in this trace, so there is no container 4."),
					error.getMessage());
		}

		@Test
		void reusedPidNamespaceId_allContainersStillReturnsEveryProcess(){
			final ReusedId reused = new ReusedId();

			assertResult(boundaryOf(processes(new Process[]{reused.masterJoinedMounts, reused.oldWorker},
					inside(reused.first), inside(reused.exec), inside(reused.second), new Process[]{reused.secondWorker})),
					getContainerBoundary());
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

			Process[] deeper(){
				return new Process[]{deeperInit, deeperShell};
			}

			Process[] nested(){
				return processes(new Process[]{nestedInit, nestedShell, nestedForked, deeperTool, deeperUnshared},
						deeper());
			}

			Process[] outer(){
				return processes(inside(outer), new Process[]{forked, tool, toolUnshared}, nested());
			}
		}

		@Test
		void container_includesContainersStartedInsideIt(){
			final Nesting nesting = new Nesting();

			assertResult(boundaryOf(nesting.outer()), getContainerBoundary(CONTAINER));
			assertResult(boundaryOf(nesting.nested()), getContainerBoundary(NESTED));
			assertResult(boundaryOf(nesting.deeper()), getContainerBoundary(DEEPER));
			assertResult(boundaryOf(processes(nesting.outer(), inside(nesting.sibling))), getContainerBoundary());
		}

		@Test
		void nestedContainer_belongsOnlyToTheOuterContainerThatStartedIt(){
			// The outer ID is reused; only the second outer container starts a nested one
			final DockerRun firstOuter = trace.dockerRun(shim, CONTAINER, 4100, "bash");
			trace.exit(firstOuter.application);
			final Nesting nesting = new Nesting();

			assertResult(boundaryOf(inside(firstOuter)), getContainerBoundary(CONTAINER, 1));
			assertResult(boundaryOf(nesting.outer()), getContainerBoundary(CONTAINER, 2));
		}

		// ---------------------------------------------------------------------
		// Containers of given processes

		@Test
		void seedProcesses_selectTheContainersTheyBelongTo(){
			final ReusedId reused = new ReusedId();
			final Process tool = trace.execve(
					trace.spawn(reused.second.application, "redis", "4130", "SIGCHLD", CONTAINER), "unshare");
			final Process toolUnshared = trace.unshare(tool, "CLONE_NEWPID", NESTED);
			final Process nestedInit = trace.spawn(toolUnshared, "unshare", "4131", "SIGCHLD", NESTED);
			final Process nestedShell = trace.execve(nestedInit, "sh");
			final DockerRun cache = trace.dockerRun(shim, OTHER_CONTAINER, 4300, "memcached");
			final Process[] second = processes(inside(reused.second),
					new Process[]{reused.secondWorker, tool, toolUnshared, nestedInit, nestedShell});

			assertResult(boundaryOf(second), getContainerBoundary(reused.second.application));
			assertResult(boundaryOf(processes(new Process[]{reused.masterJoinedMounts, reused.oldWorker},
					inside(cache))), getContainerBoundary(reused.oldWorker, cache.application));
			assertResult(boundaryOf(processes(inside(reused.first), inside(reused.exec))),
					getContainerBoundary(reused.first.application, reused.exec.command));
			assertResult(boundaryOf(nestedInit, nestedShell), getContainerBoundary(nestedShell));
		}

		@Test
		void seedProcessesOutsideContainers_selectNothing(){
			final DockerRun web = trace.dockerRun(shim, CONTAINER, 4100, "nginx");

			assertResult(new Result(), getContainerBoundary(shim, web.stage1Unshared));
		}
	}
}
