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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

import spade.query.execution.Context;
import spade.query.quickgrail.entities.Graph;

/**
 * Integration tests for {@link GetContainerInit}. Fixtures are built in
 * each test (rather than a shared {@code @Before}) so that the chain
 * topology is visible right next to the assertion that depends on it.
 *
 * Conventions follow OPM as SPADE materializes it: edges go from child
 * to parent (the child {@code WasTriggeredBy} the parent), so a clone
 * edge {@code C → P} reads as "P called clone() and got C as a child".
 */
public class GetContainerInitIntegrationTest{

	private InMemoryQueryHarness harness;
	private Context ctx;

	@Before
	public void setUp(){
		harness = new InMemoryQueryHarness();
		ctx = new Context(harness.executor);
	}

	private Set<String> vertexHashesOf(final Graph g){
		return harness.executor.exportVertices(g).keySet();
	}

	private Set<String> edgeHashesOf(final Graph g){
		return harness.executor.exportEdges(g).stream()
				.map(e -> e.edgeHash).collect(Collectors.toSet());
	}

	// =========================================================================
	// Happy path: Docker-style clone-into-new-PID-namespace + execve chain
	// =========================================================================

	@Test
	public void dockerLikeInitChain_isExtractedEndToEnd(){
		// Host side (no `pid namespace`):
		//   v_containerd, v_shim, v_runc, v_runc_parent
		// In-container (ns pid == "1", pid namespace == "ns_X"):
		//   v_runc_child, v_runc_init, v_hello  (three execve snapshots of the
		//   same PID-1 process, ending with the user's app)
		harness.putVertex("v_containerd",  "type", "Process", "name", "containerd");
		harness.putVertex("v_shim",        "type", "Process", "name", "containerd-shim");
		harness.putVertex("v_runc",        "type", "Process", "name", "runC");
		harness.putVertex("v_runc_parent", "type", "Process", "name", "runC[Parent]");
		harness.putVertex("v_runc_child",  "type", "Process", "name", "runC[Child]",
				"ns pid", "1", "pid namespace", "ns_X");
		harness.putVertex("v_runc_init",   "type", "Process", "name", "runC[INIT]",
				"ns pid", "1", "pid namespace", "ns_X");
		harness.putVertex("v_hello",       "type", "Process", "name", "hello",
				"ns pid", "1", "pid namespace", "ns_X");

		// Clone chain (child → parent) walking up to the daemon.
		harness.putEdge("e_clone_1", "v_shim",        "v_containerd",  "clone");
		harness.putEdge("e_clone_2", "v_runc",        "v_shim",        "clone");
		harness.putEdge("e_clone_3", "v_runc_parent", "v_runc",        "clone");
		// THE boundary-crossing clone — child becomes PID 1 in the new namespace:
		harness.putEdge("e_clone_4", "v_runc_child",  "v_runc_parent", "clone");
		// Execve chain inside the container:
		harness.putEdge("e_execve_1", "v_runc_init",  "v_runc_child", "execve");
		harness.putEdge("e_execve_2", "v_hello",      "v_runc_init",  "execve");

		final Graph target = harness.env.allocateGraph();
		harness.executor.createEmptyGraph(target);
		new GetContainerInit(target, harness.baseGraph, 10).exec(ctx);

		final Set<String> vertices = vertexHashesOf(target);
		final Set<String> edges = edgeHashesOf(target);

		// Every step from the in-container app back to the boundary-crossing
		// caller must be present.
		assertTrue("in-container app must be in result", vertices.contains("v_hello"));
		assertTrue("intermediate execve target must be in result", vertices.contains("v_runc_init"));
		assertTrue("PID-1 child (clone destination) must be in result", vertices.contains("v_runc_child"));
		assertTrue("clone caller (boundary-crossing destination) must be in result",
				vertices.contains("v_runc_parent"));

		// Per §4.2.2, the pattern STARTS at the boundary-crossing event, so
		// the engine chain above runC[Parent] is intentionally not in the
		// init subgraph (the prose definition in the paper does not extend
		// to it; only the figures show it as adjacent context).
		assertFalse("host-side runC (above the boundary) must not appear", vertices.contains("v_runc"));
		assertFalse("host-side containerd-shim must not appear", vertices.contains("v_shim"));
		assertFalse("host-side containerd daemon must not appear", vertices.contains("v_containerd"));

		// All path edges must be present; the clone edges above the boundary
		// (and any edge missing one in-result endpoint) must not.
		assertTrue(edges.contains("e_execve_2"));
		assertTrue(edges.contains("e_execve_1"));
		assertTrue(edges.contains("e_clone_4"));
		assertFalse("clone above the boundary must not appear", edges.contains("e_clone_3"));
		assertFalse(edges.contains("e_clone_2"));
		assertFalse(edges.contains("e_clone_1"));
	}

	// =========================================================================
	// Unshare variant
	// =========================================================================

	@Test
	public void unshareCase_yieldsThePostUnshareToCallerEdge(){
		// Models a process that called unshare(CLONE_NEWPID|CLONE_NEWNS|…):
		//   pre-unshare snapshot (host-side) → post-unshare snapshot (PID 1 in new ns)
		harness.putVertex("v_caller",       "type", "Process", "name", "engine_pre_unshare");
		harness.putVertex("v_post_unshare", "type", "Process", "name", "engine_post_unshare",
				"ns pid", "1", "pid namespace", "ns_U");
		// op == "unshare" boundary edge.
		harness.putEdge("e_unshare", "v_post_unshare", "v_caller", "unshare");

		final Graph target = harness.env.allocateGraph();
		harness.executor.createEmptyGraph(target);
		new GetContainerInit(target, harness.baseGraph, 10).exec(ctx);

		final Set<String> vertices = vertexHashesOf(target);
		final Set<String> edges = edgeHashesOf(target);

		assertTrue("post-unshare PID-1 vertex must be in result", vertices.contains("v_post_unshare"));
		assertTrue("unshare caller must be in result", vertices.contains("v_caller"));
		assertTrue("the unshare edge itself must be in result", edges.contains("e_unshare"));
	}

	// =========================================================================
	// No-containers edge case (no PID-1 vertices anywhere)
	// =========================================================================

	@Test
	public void noPid1Vertices_returnsEmptyGraphWithoutError(){
		// Only host-side processes, no container labeling at all.
		harness.putVertex("v_host_a", "type", "Process", "name", "a");
		harness.putVertex("v_host_b", "type", "Process", "name", "b");
		harness.putEdge("e_clone", "v_host_b", "v_host_a", "clone");

		final Graph target = harness.env.allocateGraph();
		harness.executor.createEmptyGraph(target);
		new GetContainerInit(target, harness.baseGraph, 10).exec(ctx);

		assertEquals("an input with no containers must produce an empty result, not an error",
				0L, harness.executor.getGraphCount(target).getVertices());
		assertEquals(0L, harness.executor.getGraphCount(target).getEdges());
	}

	// =========================================================================
	// Two independent containers
	// =========================================================================

	@Test
	public void twoIndependentContainers_bothInitChainsAreExtracted(){
		// Container X: a clone-NEWPID-style init.
		harness.putVertex("v_x_parent", "type", "Process", "name", "x_caller");
		harness.putVertex("v_x_child",  "type", "Process", "name", "x_init",
				"ns pid", "1", "pid namespace", "ns_X");
		harness.putEdge("e_x_clone", "v_x_child", "v_x_parent", "clone");

		// Container Y: an unshare-style init.
		harness.putVertex("v_y_caller", "type", "Process", "name", "y_caller");
		harness.putVertex("v_y_post",   "type", "Process", "name", "y_init",
				"ns pid", "1", "pid namespace", "ns_Y");
		harness.putEdge("e_y_unshare", "v_y_post", "v_y_caller", "unshare");

		final Graph target = harness.env.allocateGraph();
		harness.executor.createEmptyGraph(target);
		new GetContainerInit(target, harness.baseGraph, 10).exec(ctx);

		final Set<String> vertices = vertexHashesOf(target);
		final Set<String> edges = edgeHashesOf(target);

		// Both containers' boundary-crossing vertices must appear.
		assertTrue(vertices.contains("v_x_parent"));
		assertTrue(vertices.contains("v_x_child"));
		assertTrue(vertices.contains("v_y_caller"));
		assertTrue(vertices.contains("v_y_post"));

		// Both boundary edges must appear.
		assertTrue(edges.contains("e_x_clone"));
		assertTrue(edges.contains("e_y_unshare"));
	}

	// =========================================================================
	// Defensive failure modes
	// =========================================================================

	@Test
	public void pid1ExistsButNoBoundaryEdges_throwsTruncationException(){
		// PID-1 vertex but no unshare/clone edges at all — the trace was
		// snipped before the boundary-crossing event. The method must not
		// silently return half a chain; it must say so out loud.
		harness.putVertex("v_inside", "type", "Process", "name", "stranded_init",
				"ns pid", "1", "pid namespace", "ns_T");
		harness.putVertex("v_file",   "type", "Artifact", "path", "/etc/passwd");
		harness.putEdge("e_used", "v_inside", "v_file", "read");

		final Graph target = harness.env.allocateGraph();
		harness.executor.createEmptyGraph(target);

		try{
			new GetContainerInit(target, harness.baseGraph, 10).exec(ctx);
			fail("Expected a RuntimeException because no unshare/clone-NEWPID edges exist");
		}catch(RuntimeException e){
			final String message = e.getMessage();
			assertNotNull(message);
			assertTrue("error must mention the missing boundary edges; got: " + message,
					message.contains("no 'unshare' or PID-namespace-crossing 'clone'"));
		}
	}

	@Test
	public void depthZero_throwsCompletenessException(){
		// Depth 0 means we never traverse any edge, so even the immediate
		// clone caller is unreachable. This stresses the throw-on-incomplete
		// guard described in CLARITY_METHODS.md.
		harness.putVertex("v_caller", "type", "Process", "name", "engine");
		harness.putVertex("v_init",   "type", "Process", "name", "init",
				"ns pid", "1", "pid namespace", "ns_Z");
		harness.putEdge("e_clone", "v_init", "v_caller", "clone");

		final Graph target = harness.env.allocateGraph();
		harness.executor.createEmptyGraph(target);

		try{
			new GetContainerInit(target, harness.baseGraph, 0).exec(ctx);
			fail("Expected a RuntimeException because no path could be traversed at depth 0");
		}catch(RuntimeException e){
			final String message = e.getMessage();
			assertNotNull(message);
			assertTrue("error must mention maxDepth so the user knows the remediation; got: " + message,
					message.contains("maxDepth"));
		}
	}
}
