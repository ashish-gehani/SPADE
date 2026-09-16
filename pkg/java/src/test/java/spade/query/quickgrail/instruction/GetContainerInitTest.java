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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import spade.query.execution.Context;
import spade.query.quickgrail.core.Instruction;
import spade.query.quickgrail.core.Program;
import spade.query.quickgrail.core.QuickGrailQueryResolver;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.parser.DSLParserWrapper;
import spade.query.quickgrail.utility.TreeStringSerializable;

/**
 * {@link GetContainerInit}'s contract with the resolver and the plan printer.
 * Behavior on traces is in {@link GetContainerInitIntegrationTest}.
 */
public class GetContainerInitTest{

	@Test
	public void constructor_storesAllFields(){
		final Graph target = new Graph("target_g");
		final Graph subject = new Graph("subject_g");

		final GetContainerInit instruction = new GetContainerInit(target, subject, "4026531836");

		assertSame(target, instruction.targetGraph);
		assertSame(subject, instruction.subjectGraph);
		assertEquals("4026531836", instruction.hostPidNamespace);
	}

	@Test
	public void getLabel_returnsClassName(){
		assertEquals("GetContainerInit", new GetContainerInit(new Graph("t"), new Graph("s"), "1").getLabel());
	}

	@Test
	public void getFieldStringItems_listsGraphsAndHostPidNamespace(){
		final ArrayList<String> names = new ArrayList<String>();
		final ArrayList<String> values = new ArrayList<String>();

		new GetContainerInit(new Graph("tg"), new Graph("sg"), "4026531836").getFieldStringItems(names, values,
				new ArrayList<String>(), new ArrayList<TreeStringSerializable>(),
				new ArrayList<String>(), new ArrayList<ArrayList<? extends TreeStringSerializable>>());

		assertEquals(List.of("targetGraph", "subjectGraph", "hostPidNamespace"), names);
		assertEquals(List.of("tg", "sg", "4026531836"), values);
	}

	@Test
	public void exec_rejectsMissingHostPidNamespace(){
		final InMemoryQueryHarness harness = new InMemoryQueryHarness();
		final GetContainerInit instruction = new GetContainerInit(harness.executor.createNewGraph(),
				harness.baseGraph, "");

		assertThrows(IllegalArgumentException.class, () -> instruction.exec(new Context(harness.executor)));
	}

	@Test
	public void resolver_readsHostPidNamespaceFromTheAuditConstantsFile(){
		final GetContainerInit instruction = resolveOnly("$r = $base.getContainerInit()");

		assertEquals("4026531836", instruction.hostPidNamespace);
	}

	@Test
	public void resolver_rejectsArguments(){
		final RuntimeException error = assertThrows(RuntimeException.class,
				() -> resolveOnly("$r = $base.getContainerInit(10)"));

		assertTrue(error.getMessage().contains("expected 0"), error.getMessage());
	}

	private static GetContainerInit resolveOnly(final String query){
		final InMemoryQueryHarness harness = new InMemoryQueryHarness();
		final Program program = new QuickGrailQueryResolver().resolveProgram(new DSLParserWrapper().fromText(query),
				harness.env);
		GetContainerInit found = null;
		for(int i = 0; i < program.getInstructionsSize(); i++){
			final Instruction<? extends Serializable> instruction = program.getInstruction(i);
			if(instruction instanceof GetContainerInit){
				assertEquals(null, found, "one GetContainerInit instruction");
				found = (GetContainerInit)instruction;
			}
		}
		assertTrue(found != null, "GetContainerInit instruction in " + program);
		return found;
	}
}
