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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;

import org.junit.Test;

import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.utility.TreeStringSerializable;

/**
 * Unit tests for {@link GetContainerBoundary} — the QuickGrail graph
 * method that returns the subgraph spanning one container's boundary
 * (process vertices in a PID namespace plus their adjacent artifacts)
 * or, with no argument, the union across every labeled container.
 *
 * The tests cover the instruction's contract with the resolver and the
 * tree-serialization machinery (used for debug printing of execution
 * plans). The composite {@code exec()} body is exercised end-to-end
 * via integration tests against a real storage; those are not in scope
 * here.
 */
public class GetContainerBoundaryTest{

	@Test
	public void constructor_storesAllFieldsForSingleContainerForm(){
		final Graph target = new Graph("target_g");
		final Graph subject = new Graph("subject_g");
		final String nsId = "4026532270";

		final GetContainerBoundary instruction = new GetContainerBoundary(target, subject, nsId);

		assertSame("target graph must be the one passed in", target, instruction.targetGraph);
		assertSame("subject graph must be the one passed in", subject, instruction.subjectGraph);
		assertEquals("PID namespace id must round-trip unchanged", nsId, instruction.pidNamespaceId);
	}

	@Test
	public void constructor_allowsNullPidNamespaceForAllContainersForm(){
		final GetContainerBoundary instruction = new GetContainerBoundary(
				new Graph("t"), new Graph("s"), null);

		assertNull("null PID namespace id selects the all-containers form",
				instruction.pidNamespaceId);
	}

	@Test
	public void getLabel_returnsClassName(){
		final GetContainerBoundary instruction = new GetContainerBoundary(
				new Graph("t"), new Graph("s"), "42");

		assertEquals("GetContainerBoundary", instruction.getLabel());
	}

	@Test
	public void getFieldStringItems_listsBothGraphsAndExplicitNamespace(){
		final ArrayList<String> names = new ArrayList<String>();
		final ArrayList<String> values = new ArrayList<String>();
		final ArrayList<String> noncontainerNames = new ArrayList<String>();
		final ArrayList<TreeStringSerializable> noncontainerChildren = new ArrayList<TreeStringSerializable>();
		final ArrayList<String> containerNames = new ArrayList<String>();
		final ArrayList<ArrayList<? extends TreeStringSerializable>> containerChildren =
				new ArrayList<ArrayList<? extends TreeStringSerializable>>();

		final GetContainerBoundary instruction = new GetContainerBoundary(
				new Graph("tg"), new Graph("sg"), "4026532270");

		instruction.getFieldStringItems(names, values,
				noncontainerNames, noncontainerChildren,
				containerNames, containerChildren);

		assertEquals("inline field names and values must be 1:1", names.size(), values.size());
		assertTrue("must list targetGraph", names.contains("targetGraph"));
		assertEquals("tg", values.get(names.indexOf("targetGraph")));
		assertTrue("must list subjectGraph", names.contains("subjectGraph"));
		assertEquals("sg", values.get(names.indexOf("subjectGraph")));
		assertTrue("must list pidNamespaceId", names.contains("pidNamespaceId"));
		assertEquals("explicit id must serialize verbatim",
				"4026532270", values.get(names.indexOf("pidNamespaceId")));
	}

	@Test
	public void getFieldStringItems_serializesAllContainersFormWithSentinel(){
		final ArrayList<String> names = new ArrayList<String>();
		final ArrayList<String> values = new ArrayList<String>();

		final GetContainerBoundary instruction = new GetContainerBoundary(
				new Graph("tg"), new Graph("sg"), null);

		instruction.getFieldStringItems(names, values,
				new ArrayList<String>(), new ArrayList<TreeStringSerializable>(),
				new ArrayList<String>(), new ArrayList<ArrayList<? extends TreeStringSerializable>>());

		assertEquals("null id must serialize as the <all> sentinel for readability",
				"<all>", values.get(names.indexOf("pidNamespaceId")));
	}
}
