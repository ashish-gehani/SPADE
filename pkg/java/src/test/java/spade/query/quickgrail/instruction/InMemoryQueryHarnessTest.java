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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import spade.query.quickgrail.core.QuickGrailQueryResolver.PredicateOperator;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.instruction.InMemoryQueryHarness.AdjacencySemantics;

/**
 * Pins down the backend behaviors the harness mirrors, so the container
 * method tests built on it exercise what PostgreSQL, Neo4j and Quickstep do.
 */
public class InMemoryQueryHarnessTest{

	/** p1 -> p2 -> p3 (child to parent), plus an isolated vertex p4. */
	private static InMemoryQueryHarness chain(final AdjacencySemantics semantics){
		final InMemoryQueryHarness h = new InMemoryQueryHarness(semantics);
		h.putVertex("p1", "name", "one");
		h.putVertex("p2", "name", "two");
		h.putVertex("p3", "name", "three");
		h.putVertex("p4", "name", "four");
		h.putEdge("e12", "p1", "p2", "fork");
		h.putEdge("e23", "p2", "p3", "fork");
		return h;
	}

	private static Graph graphOf(final InMemoryQueryHarness h, final String... vertexHashes){
		final Graph g = h.executor.createNewGraph();
		h.executor.insertLiteralVertex(g, new ArrayList<String>(Arrays.asList(vertexHashes)));
		return g;
	}

	private static Set<String> vertices(final InMemoryQueryHarness h, final Graph g){
		return new TreeSet<String>(h.executor.exportVertices(g).keySet());
	}

	private static Set<String> edges(final InMemoryQueryHarness h, final Graph g){
		return h.executor.exportEdges(g).stream().map(e -> e.edgeHash).collect(Collectors.toCollection(TreeSet::new));
	}

	@Test
	public void adjacency_postgresSemantics_addsEverySourceVertex(){
		final InMemoryQueryHarness h = chain(AdjacencySemantics.SOURCES_ALWAYS);
		final Graph target = h.executor.createNewGraph();

		h.executor.getAdjacentVertex(target, h.baseGraph, graphOf(h, "p2", "p4"), GetLineage.Direction.kAncestor);

		assertEquals(new TreeSet<String>(Arrays.asList("p2", "p3", "p4")), vertices(h, target),
				"sources (including isolated p4), neighbors");
		assertEquals(new TreeSet<String>(Arrays.asList("e23")), edges(h, target));
	}

	@Test
	public void adjacency_neo4jSemantics_addsSourcesOnlyThroughMatchingEdges(){
		final InMemoryQueryHarness h = chain(AdjacencySemantics.SOURCES_VIA_EDGES);
		final Graph target = h.executor.createNewGraph();

		h.executor.getAdjacentVertex(target, h.baseGraph, graphOf(h, "p2", "p4"), GetLineage.Direction.kAncestor);

		assertEquals(new TreeSet<String>(Arrays.asList("p2", "p3")), vertices(h, target),
				"isolated source p4 has no matching edge");
	}

	@Test
	public void adjacency_neo4jSemantics_requiresBothEndpointsInSubject(){
		final InMemoryQueryHarness h = chain(AdjacencySemantics.SOURCES_VIA_EDGES);
		// Subject holds both edges but not vertex p3
		final Graph subject = graphOf(h, "p1", "p2");
		h.executor.insertLiteralEdge(subject, new ArrayList<String>(Arrays.asList("e12", "e23")));
		final Graph target = h.executor.createNewGraph();

		h.executor.getAdjacentVertex(target, subject, graphOf(h, "p2"), GetLineage.Direction.kBoth);

		assertEquals(new TreeSet<String>(Arrays.asList("e12")), edges(h, target),
				"e23 is skipped because its parent p3 is not a vertex of the subject");
	}

	@Test
	public void subtract_removesPerComponent_andRejectsBaseSubtrahend(){
		final InMemoryQueryHarness h = chain(AdjacencySemantics.SOURCES_VIA_EDGES);
		final Graph out = h.executor.createNewGraph();

		h.executor.subtractGraph(out, graphOf(h, "p1", "p2", "p3"), graphOf(h, "p2"), Graph.Component.kVertex);

		assertEquals(new TreeSet<String>(Arrays.asList("p1", "p3")), vertices(h, out));
		assertThrows(UnsupportedOperationException.class,
				() -> h.executor.subtractGraph(h.executor.createNewGraph(), graphOf(h, "p1"), h.baseGraph, null));
	}

	@Test
	public void comparisons_areStringOrdered_andLikeUsesSqlWildcards(){
		final InMemoryQueryHarness h = new InMemoryQueryHarness();
		h.putVertex("a", "time", "1700000000.100", "flags", "CLONE_NEWNS|CLONE_NEWPID|SIGCHLD");
		h.putVertex("b", "time", "1700000000.900", "flags", "SIGCHLD");
		h.putVertex("c", "pid", "9");
		h.putVertex("d", "pid", "10");

		final Graph late = h.executor.createNewGraph();
		h.executor.getVertex(late, h.baseGraph, "time", PredicateOperator.GREATER_EQUAL, "1700000000.500", true);
		assertEquals(new TreeSet<String>(Arrays.asList("b")), vertices(h, late));

		final Graph newPid = h.executor.createNewGraph();
		h.executor.getVertex(newPid, h.baseGraph, "flags", PredicateOperator.LIKE, "%CLONE_NEWPID%", true);
		assertEquals(new TreeSet<String>(Arrays.asList("a")), vertices(h, newPid));

		// String order, as in varchar columns: "9" > "10"
		final Graph above = h.executor.createNewGraph();
		h.executor.getVertex(above, h.baseGraph, "pid", PredicateOperator.GREATER, "10", true);
		assertEquals(new TreeSet<String>(Arrays.asList("c")), vertices(h, above));
	}
}
