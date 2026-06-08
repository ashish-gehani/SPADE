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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;

import org.junit.Test;

import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.utility.TreeStringSerializable;

/**
 * Unit tests for {@link GetContainerInit} — the QuickGrail graph method
 * that returns the subgraph spanning container-initialization activity:
 * paths from in-container PID-1 vertices back to the host-side caller
 * of an unshare or PID-namespace-crossing clone, bounded by maxDepth.
 *
 * The tests cover the instruction's contract with the resolver and the
 * tree-serialization machinery (used for debug printing of execution
 * plans). The composite {@code exec()} body — which performs the path
 * search and the throw-on-incomplete completeness check — is exercised
 * end-to-end via integration tests against a real storage; those are
 * not in scope here.
 */
public class GetContainerInitTest{

	@Test
	public void constructor_storesAllFields(){
		final Graph target = new Graph("target_g");
		final Graph subject = new Graph("subject_g");
		final int maxDepth = 10;

		final GetContainerInit instruction = new GetContainerInit(target, subject, maxDepth);

		assertSame("target graph must be the one passed in", target, instruction.targetGraph);
		assertSame("subject graph must be the one passed in", subject, instruction.subjectGraph);
		assertEquals("maxDepth must round-trip unchanged", maxDepth, instruction.maxDepth);
	}

	@Test
	public void constructor_acceptsZeroDepthEvenThoughResolverRejectsIt(){
		// Resolver guards against missing maxDepth env var, but the Instruction
		// is not the right place to enforce policy — record the input verbatim.
		final GetContainerInit instruction = new GetContainerInit(
				new Graph("t"), new Graph("s"), 0);

		assertEquals(0, instruction.maxDepth);
	}

	@Test
	public void getLabel_returnsClassName(){
		final GetContainerInit instruction = new GetContainerInit(
				new Graph("t"), new Graph("s"), 5);

		assertEquals("GetContainerInit", instruction.getLabel());
	}

	@Test
	public void getFieldStringItems_listsBothGraphsAndMaxDepth(){
		final ArrayList<String> names = new ArrayList<String>();
		final ArrayList<String> values = new ArrayList<String>();
		final ArrayList<String> noncontainerNames = new ArrayList<String>();
		final ArrayList<TreeStringSerializable> noncontainerChildren = new ArrayList<TreeStringSerializable>();
		final ArrayList<String> containerNames = new ArrayList<String>();
		final ArrayList<ArrayList<? extends TreeStringSerializable>> containerChildren =
				new ArrayList<ArrayList<? extends TreeStringSerializable>>();

		final GetContainerInit instruction = new GetContainerInit(
				new Graph("tg"), new Graph("sg"), 12);

		instruction.getFieldStringItems(names, values,
				noncontainerNames, noncontainerChildren,
				containerNames, containerChildren);

		assertEquals("inline field names and values must be 1:1", names.size(), values.size());
		assertTrue("must list targetGraph", names.contains("targetGraph"));
		assertEquals("tg", values.get(names.indexOf("targetGraph")));
		assertTrue("must list subjectGraph", names.contains("subjectGraph"));
		assertEquals("sg", values.get(names.indexOf("subjectGraph")));
		assertTrue("must list maxDepth", names.contains("maxDepth"));
		assertEquals("maxDepth must serialize as its decimal string form",
				"12", values.get(names.indexOf("maxDepth")));
	}

	@Test
	public void getFieldStringItems_serializesDepthAsDecimalNotHexOrOctal(){
		// A guard against a regression where String.format or Integer.toHexString
		// might creep in. The plan printer is consumed by humans reading logs.
		final ArrayList<String> names = new ArrayList<String>();
		final ArrayList<String> values = new ArrayList<String>();

		final GetContainerInit instruction = new GetContainerInit(
				new Graph("tg"), new Graph("sg"), 255);

		instruction.getFieldStringItems(names, values,
				new ArrayList<String>(), new ArrayList<TreeStringSerializable>(),
				new ArrayList<String>(), new ArrayList<ArrayList<? extends TreeStringSerializable>>());

		assertEquals("255", values.get(names.indexOf("maxDepth")));
	}
}
