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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import spade.query.quickgrail.core.Instruction;
import spade.query.quickgrail.core.Program;
import spade.query.quickgrail.core.QuickGrailQueryResolver;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.parser.DSLParserWrapper;
import spade.query.quickgrail.utility.TreeStringSerializable;

/**
 * {@link GetContainerBoundary}'s contract with the resolver and the plan printer.
 * Behavior on traces is in {@link GetContainerBoundaryIntegrationTest}.
 */
public class GetContainerBoundaryTest{

	private static final String HOST = "4026531836", CONTAINER = "4026532270";

	@Test
	public void constructors_storeTheirForm(){
		final Graph target = new Graph("t"), subject = new Graph("s"), seeds = new Graph("p");

		final GetContainerBoundary all = new GetContainerBoundary(target, subject, HOST);
		assertSame(target, all.targetGraph);
		assertSame(subject, all.subjectGraph);
		assertEquals(HOST, all.hostPidNamespace);
		assertNull(all.pidNamespaceId);
		assertNull(all.containerNumber);
		assertNull(all.seedGraph);

		final GetContainerBoundary numbered = new GetContainerBoundary(target, subject, HOST, CONTAINER, 2);
		assertEquals(CONTAINER, numbered.pidNamespaceId);
		assertEquals(Integer.valueOf(2), numbered.containerNumber);
		assertNull(numbered.seedGraph);

		final GetContainerBoundary bySeeds = new GetContainerBoundary(target, subject, HOST, seeds);
		assertSame(seeds, bySeeds.seedGraph);
		assertNull(bySeeds.pidNamespaceId);
	}

	@Test
	public void constructor_rejectsIdsThatAreNotContainers(){
		final Graph target = new Graph("t"), subject = new Graph("s");

		final IllegalArgumentException host = assertThrows(IllegalArgumentException.class,
				() -> new GetContainerBoundary(target, subject, HOST, HOST, null));
		assertTrue(host.getMessage().contains("is the host's"), host.getMessage());
		final IllegalArgumentException unobserved = assertThrows(IllegalArgumentException.class,
				() -> new GetContainerBoundary(target, subject, HOST, "-1", null));
		assertTrue(unobserved.getMessage().contains("not observed"), unobserved.getMessage());
		assertThrows(IllegalArgumentException.class, () -> new GetContainerBoundary(target, subject, HOST, "", null));
		assertThrows(IllegalArgumentException.class,
				() -> new GetContainerBoundary(target, subject, HOST, CONTAINER, 0));
		assertThrows(IllegalArgumentException.class,
				() -> new GetContainerBoundary(target, subject, HOST, (Graph)null));
	}

	@Test
	public void getLabel_returnsClassName(){
		assertEquals("GetContainerBoundary", new GetContainerBoundary(new Graph("t"), new Graph("s"), HOST).getLabel());
	}

	@Test
	public void getFieldStringItems_listsTheFieldsOfEachForm(){
		assertFields(new GetContainerBoundary(new Graph("tg"), new Graph("sg"), HOST),
				List.of("targetGraph", "subjectGraph", "hostPidNamespace", "pidNamespaceId"),
				List.of("tg", "sg", HOST, "<all>"));
		assertFields(new GetContainerBoundary(new Graph("tg"), new Graph("sg"), HOST, CONTAINER, null),
				List.of("targetGraph", "subjectGraph", "hostPidNamespace", "pidNamespaceId"),
				List.of("tg", "sg", HOST, CONTAINER));
		assertFields(new GetContainerBoundary(new Graph("tg"), new Graph("sg"), HOST, CONTAINER, 3),
				List.of("targetGraph", "subjectGraph", "hostPidNamespace", "pidNamespaceId", "containerNumber"),
				List.of("tg", "sg", HOST, CONTAINER, "3"));
		assertFields(new GetContainerBoundary(new Graph("tg"), new Graph("sg"), HOST, new Graph("pg")),
				List.of("targetGraph", "subjectGraph", "hostPidNamespace", "seedGraph"),
				List.of("tg", "sg", HOST, "pg"));
	}

	private static void assertFields(final GetContainerBoundary instruction, final List<String> expectedNames,
			final List<String> expectedValues){
		final ArrayList<String> names = new ArrayList<String>();
		final ArrayList<String> values = new ArrayList<String>();
		instruction.getFieldStringItems(names, values,
				new ArrayList<String>(), new ArrayList<TreeStringSerializable>(),
				new ArrayList<String>(), new ArrayList<ArrayList<? extends TreeStringSerializable>>());
		assertEquals(expectedNames, names);
		assertEquals(expectedValues, values);
	}

	// -------------------------------------------------------------------------
	// Resolver

	@Test
	public void resolver_noArguments_selectsEveryContainer(){
		final GetContainerBoundary instruction = resolveOnly("$r = $base.getContainerBoundary()");

		assertEquals(HOST, instruction.hostPidNamespace);
		assertNull(instruction.pidNamespaceId);
		assertNull(instruction.seedGraph);
	}

	@Test
	public void resolver_stringArgument_selectsById(){
		final GetContainerBoundary instruction = resolveOnly("$r = $base.getContainerBoundary('" + CONTAINER + "')");

		assertEquals(CONTAINER, instruction.pidNamespaceId);
		assertNull(instruction.containerNumber);
	}

	@Test
	public void resolver_stringAndInteger_selectsANumberedContainer(){
		final GetContainerBoundary instruction = resolveOnly(
				"$r = $base.getContainerBoundary('" + CONTAINER + "', 2)");

		assertEquals(CONTAINER, instruction.pidNamespaceId);
		assertEquals(Integer.valueOf(2), instruction.containerNumber);
	}

	@Test
	public void resolver_graphArgument_selectsBySeedProcesses(){
		final GetContainerBoundary instruction = resolveOnly(
				"$p = $base.getVertex(* LIKE '%nginx%'); $r = $base.getContainerBoundary($p)");

		assertNotNull(instruction.seedGraph);
		assertNull(instruction.pidNamespaceId);
	}

	@Test
	public void resolver_rejectsInvalidArguments(){
		assertResolveFails("$r = $base.getContainerBoundary('" + CONTAINER + "', 1, 2)", "expected 0, 1 or 2");
		assertResolveFails("$r = $base.getContainerBoundary('" + CONTAINER + "', 0)", "expected 1 or more");
		assertResolveFails("$r = $base.getContainerBoundary(5)", "expected string");
		assertResolveFails("$r = $base.getContainerBoundary('" + CONTAINER + "', 'two')", "expected integer");
		assertResolveFails("$r = $base.getContainerBoundary('" + HOST + "')", "is the host's");
		assertResolveFails("$r = $base.getContainerBoundary('-1')", "not observed");
	}

	private static void assertResolveFails(final String query, final String expectedMessagePart){
		final RuntimeException error = assertThrows(RuntimeException.class, () -> resolveOnly(query));
		assertTrue(error.getMessage().contains(expectedMessagePart), query + " -> " + error.getMessage());
	}

	private static GetContainerBoundary resolveOnly(final String query){
		final InMemoryQueryHarness harness = new InMemoryQueryHarness();
		final Program program = new QuickGrailQueryResolver().resolveProgram(new DSLParserWrapper().fromText(query),
				harness.env);
		GetContainerBoundary found = null;
		for(int i = 0; i < program.getInstructionsSize(); i++){
			final Instruction<? extends Serializable> instruction = program.getInstruction(i);
			if(instruction instanceof GetContainerBoundary){
				assertNull(found, "one GetContainerBoundary instruction");
				found = (GetContainerBoundary)instruction;
			}
		}
		assertNotNull(found, "GetContainerBoundary instruction in " + program);
		return found;
	}
}
