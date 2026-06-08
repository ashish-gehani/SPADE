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
import static org.junit.Assert.assertTrue;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

import spade.query.execution.Context;
import spade.query.quickgrail.core.QueriedEdge;
import spade.query.quickgrail.entities.Graph;

/**
 * Integration tests for {@link GetContainerBoundary}.
 *
 * Each test synthesizes a small but realistic mixed host + multi-container
 * provenance graph via {@link InMemoryQueryHarness}, runs the instruction,
 * and checks the target graph's vertex/edge contents against expectations.
 *
 * Fixture layout (built in {@link #seedTwoContainersWithHost()}):
 *
 *   Host (no `pid namespace`):
 *     v_h1    — containerd daemon
 *     v_hfile — /etc/host_only_config (used only by v_h1)
 *
 *   Container A (`pid namespace` = "ns_A"):
 *     v_a1   — `ns pid` = "1" (in-container init)
 *     v_a2   — `ns pid` = "2"
 *     v_fa   — /etc/passwd inside container A (used by v_a1)
 *
 *   Container B (`pid namespace` = "ns_B"):
 *     v_b1   — `ns pid` = "1"
 *     v_fb   — /etc/passwd inside container B (used by v_b1)
 *
 *   Edges (child → parent in OPM `WasTriggeredBy` direction):
 *     e_clone_a1  : v_a1 → v_h1   ("clone")  — host daemon spawns A1
 *     e_clone_a2  : v_a2 → v_a1   ("clone")  — A1 spawns A2 inside container
 *     e_used_a    : v_a1 → v_fa   ("read")   — A1 reads /etc/passwd
 *     e_clone_b1  : v_b1 → v_h1   ("clone")  — host daemon spawns B1
 *     e_used_b    : v_b1 → v_fb   ("read")   — B1 reads /etc/passwd
 *     e_used_h    : v_h1 → v_hfile("read")   — host reads its config
 */
public class GetContainerBoundaryIntegrationTest{

	private InMemoryQueryHarness harness;
	private Context ctx;

	@Before
	public void setUp(){
		harness = new InMemoryQueryHarness();
		ctx = new Context(harness.executor);
		seedTwoContainersWithHost();
	}

	private void seedTwoContainersWithHost(){
		// Host
		harness.putVertex("v_h1", "type", "Process", "pid", "100", "name", "containerd");
		harness.putVertex("v_hfile", "type", "Artifact", "path", "/etc/host_only_config");

		// Container A
		harness.putVertex("v_a1", "type", "Process", "pid", "1001", "ns pid", "1", "pid namespace", "ns_A");
		harness.putVertex("v_a2", "type", "Process", "pid", "1002", "ns pid", "2", "pid namespace", "ns_A");
		harness.putVertex("v_fa", "type", "Artifact", "path", "/etc/passwd");

		// Container B
		harness.putVertex("v_b1", "type", "Process", "pid", "2001", "ns pid", "1", "pid namespace", "ns_B");
		harness.putVertex("v_fb", "type", "Artifact", "path", "/etc/passwd");

		// Edges (child → parent)
		harness.putEdge("e_clone_a1", "v_a1", "v_h1", "clone");
		harness.putEdge("e_clone_a2", "v_a2", "v_a1", "clone");
		harness.putEdge("e_used_a",   "v_a1", "v_fa", "read");
		harness.putEdge("e_clone_b1", "v_b1", "v_h1", "clone");
		harness.putEdge("e_used_b",   "v_b1", "v_fb", "read");
		harness.putEdge("e_used_h",   "v_h1", "v_hfile", "read");
	}

	private Set<String> vertexHashesOf(final Graph g){
		return harness.executor.exportVertices(g).keySet();
	}

	private Set<String> edgeHashesOf(final Graph g){
		return harness.executor.exportEdges(g).stream()
				.map(e -> e.edgeHash).collect(Collectors.toSet());
	}

	// =========================================================================
	// Single-container form
	// =========================================================================

	@Test
	public void singleContainer_keepsOnlyChosenContainersProcessesAndAdjacentArtifacts(){
		final Graph target = harness.env.allocateGraph();
		harness.executor.createEmptyGraph(target);

		new GetContainerBoundary(target, harness.baseGraph, "ns_A").exec(ctx);

		final Set<String> vertices = vertexHashesOf(target);
		final Set<String> edges = edgeHashesOf(target);

		// In-container processes and the artifact they accessed must be present.
		assertTrue("container A's PID-1 process must be in result", vertices.contains("v_a1"));
		assertTrue("container A's PID-2 process must be in result", vertices.contains("v_a2"));
		assertTrue("container A's accessed artifact must be in result", vertices.contains("v_fa"));
		// Host parent that cloned A1 is adjacent → included by getAdjacentVertex(kBoth).
		assertTrue("host caller (containerd) is adjacent and must be in result",
				vertices.contains("v_h1"));

		// Container B and the host-only artifact must NOT leak in.
		assertFalse("container B process must not be in result", vertices.contains("v_b1"));
		assertFalse("container B artifact must not be in result", vertices.contains("v_fb"));
		assertFalse("host-only artifact (not adjacent to any container proc) must not be in result",
				vertices.contains("v_hfile"));

		// Edges spanning the boundary set must be present.
		assertTrue("clone edge A1→containerd must be in result", edges.contains("e_clone_a1"));
		assertTrue("internal clone edge A2→A1 must be in result", edges.contains("e_clone_a2"));
		assertTrue("read edge A1→/etc/passwd must be in result",  edges.contains("e_used_a"));

		// Edges to vertices that did not land in the boundary must be dropped.
		assertFalse("clone edge B1→containerd must not appear",   edges.contains("e_clone_b1"));
		assertFalse("read edge B1→passwd must not appear",        edges.contains("e_used_b"));
		assertFalse("host's own read of /etc/host_only_config must not appear",
				edges.contains("e_used_h"));
	}

	@Test
	public void singleContainer_unknownPidNamespaceProducesEmptyGraph(){
		final Graph target = harness.env.allocateGraph();
		harness.executor.createEmptyGraph(target);

		new GetContainerBoundary(target, harness.baseGraph, "ns_DOES_NOT_EXIST").exec(ctx);

		assertEquals("no vertices match → result must be empty", 0L,
				harness.executor.getGraphCount(target).getVertices());
		assertEquals("no vertices selected → no spanning edges either", 0L,
				harness.executor.getGraphCount(target).getEdges());
	}

	@Test
	public void singleContainer_disjointContainersProduceDisjointResults(){
		final Graph forA = harness.env.allocateGraph();
		final Graph forB = harness.env.allocateGraph();
		harness.executor.createEmptyGraph(forA);
		harness.executor.createEmptyGraph(forB);

		new GetContainerBoundary(forA, harness.baseGraph, "ns_A").exec(ctx);
		new GetContainerBoundary(forB, harness.baseGraph, "ns_B").exec(ctx);

		final Set<String> aVerts = vertexHashesOf(forA);
		final Set<String> bVerts = vertexHashesOf(forB);

		// A's in-container vertices must not appear in B's result, and vice versa.
		assertFalse("A's PID-1 process must not appear in B's boundary", bVerts.contains("v_a1"));
		assertFalse("A's artifact must not appear in B's boundary", bVerts.contains("v_fa"));
		assertFalse("B's PID-1 process must not appear in A's boundary", aVerts.contains("v_b1"));
		assertFalse("B's artifact must not appear in A's boundary", aVerts.contains("v_fb"));

		// The shared host daemon legitimately appears in both because it is
		// the clone-parent of each container's init process.
		assertTrue(aVerts.contains("v_h1"));
		assertTrue(bVerts.contains("v_h1"));
	}

	// =========================================================================
	// All-containers (no-arg) form
	// =========================================================================

	@Test
	public void allContainers_unionsEveryLabeledContainersBoundary(){
		final Graph target = harness.env.allocateGraph();
		harness.executor.createEmptyGraph(target);

		new GetContainerBoundary(target, harness.baseGraph, null).exec(ctx);

		final Set<String> vertices = vertexHashesOf(target);
		final Set<String> edges = edgeHashesOf(target);

		// Both containers' processes and artifacts must show up.
		assertTrue(vertices.contains("v_a1"));
		assertTrue(vertices.contains("v_a2"));
		assertTrue(vertices.contains("v_fa"));
		assertTrue(vertices.contains("v_b1"));
		assertTrue(vertices.contains("v_fb"));
		// And the shared host parent.
		assertTrue(vertices.contains("v_h1"));

		// The host's own artifact, never touched by any container process,
		// must NOT appear — it is what distinguishes the union-of-boundaries
		// from "the whole graph".
		assertFalse("host-only artifact must not appear in any container's boundary",
				vertices.contains("v_hfile"));

		// Every clone/read edge connecting in-container vertices to the host
		// parent or to artifacts must be present.
		assertTrue(edges.contains("e_clone_a1"));
		assertTrue(edges.contains("e_clone_a2"));
		assertTrue(edges.contains("e_used_a"));
		assertTrue(edges.contains("e_clone_b1"));
		assertTrue(edges.contains("e_used_b"));
		// The host-only read does NOT belong — both endpoints must be in the
		// boundary set for an edge to land in the result.
		assertFalse(edges.contains("e_used_h"));
	}

	// =========================================================================
	// Exported-graph invariant sanity
	// =========================================================================

	@Test
	public void resultEdges_alwaysHaveBothEndpointsInTheResultVertexSet(){
		// This is the spanning-subgraph invariant from getSubgraph: every
		// edge present in the result must be incident to two vertices that
		// are also present in the result. A regression here would mean the
		// composite became un-self-consistent.
		final Graph target = harness.env.allocateGraph();
		harness.executor.createEmptyGraph(target);

		new GetContainerBoundary(target, harness.baseGraph, null).exec(ctx);

		final Set<String> vertices = vertexHashesOf(target);
		for(final QueriedEdge edge : harness.executor.exportEdges(target)){
			assertTrue("edge " + edge.edgeHash + " child endpoint not in result vertices",
					vertices.contains(edge.childHash));
			assertTrue("edge " + edge.edgeHash + " parent endpoint not in result vertices",
					vertices.contains(edge.parentHash));
		}
	}

	@Test
	public void singleContainer_exportedAnnotationsAreFaithful(){
		// Spot-check that vertices come back with the annotations we seeded —
		// catching any regression where the exporter drops or rewrites keys.
		final Graph target = harness.env.allocateGraph();
		harness.executor.createEmptyGraph(target);

		new GetContainerBoundary(target, harness.baseGraph, "ns_A").exec(ctx);

		final Map<String, Map<String, String>> exported = harness.executor.exportVertices(target);
		final Map<String, String> a1 = exported.get("v_a1");
		assertTrue(a1 != null && "ns_A".equals(a1.get("pid namespace")));
		assertTrue("1".equals(a1.get("ns pid")));
		assertTrue("Process".equals(a1.get("type")));
	}
}
