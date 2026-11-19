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
package spade.reporter.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The order of trace 2's B13a, from auditd's log: dash (1000, created in the trace) vforks
 * 1001 to run sleep, and the vfork, with the child's pid, is logged after the child's execve.
 */
public class VforkOrderingTest{

	/** What the reporter knows: pids it has a process for, and those it saw start. */
	private final Set<String> known = new HashSet<String>(), started = new HashSet<String>();
	private final List<String> handled = new ArrayList<String>();
	private VforkOrdering ordering;

	/** Stands in for the reporter: learns the processes the events create or show. */
	private final Consumer<Map<String, String>> sink = e -> {
		handled.add(e.get("name"));
		final String child = created(e);
		if(child != null){
			known.add(child);
			started.add(child);
		}else if(e.get(AuditEventReader.PID) != null){
			known.add(e.get(AuditEventReader.PID));
			if("59".equals(e.get(AuditEventReader.SYSCALL))){
				started.add(e.get(AuditEventReader.PID));
			}
		}
	};

	private static String created(final Map<String, String> e){
		return "58".equals(e.get(AuditEventReader.SYSCALL)) ? e.get(AuditEventReader.EXIT) : null;
	}

	private static Map<String, String> event(final String name, final String time, final String pid,
			final String ppid, final String syscall, final String exit){
		final Map<String, String> e = new HashMap<String, String>();
		e.put("name", name);
		e.put(AuditEventReader.TIME, time);
		e.put(AuditEventReader.PID, pid);
		e.put(AuditEventReader.PPID, ppid);
		e.put(AuditEventReader.SYSCALL, syscall);
		e.put(AuditEventReader.EXIT, exit);
		return e;
	}

	@BeforeEach
	public void setUp(){
		known.add("1000");
		started.add("1000");
		ordering = new VforkOrdering(known::contains, started::contains, VforkOrderingTest::created, 1000, 1000);
	}

	@Test
	public void aVforkChildsEventsFollowItsCreation(){
		ordering.accept(event("child execve", "100.053", "1001", "1000", "59", "0"), sink);
		ordering.accept(event("child mmap", "100.053", "1001", "1000", "9", "0"), sink);
		ordering.accept(event("other", "100.053", "2000", "1", "0", "0"), sink);
		ordering.accept(event("parent vfork", "100.052", "1000", "999", "58", "1001"), sink);
		ordering.accept(event("child exit", "100.060", "1001", "1000", "231", "0"), sink);
		assertEquals(List.of("other", "parent vfork", "child execve", "child mmap", "child exit"), handled);
		assertEquals(1, ordering.getReleasedByCreation());
	}

	/** No creation within the window: the events follow in their order, and are not held again. */
	@Test
	public void eventsWithoutACreationFollowAfterTheWindow(){
		ordering.accept(event("child execve", "100.000", "1001", "1000", "59", "0"), sink);
		ordering.accept(event("other", "100.500", "2000", "1", "0", "0"), sink);
		assertEquals(List.of("other"), handled);
		ordering.accept(event("late", "101.200", "2000", "1", "0", "0"), sink);
		ordering.accept(event("child read", "101.300", "1001", "1000", "0", "0"), sink);
		assertEquals(List.of("other", "child execve", "late", "child read"), handled);
		assertEquals(1, ordering.getReleasedByWindow());
	}

	/** A process whose parent is unknown, or only first seen, is handled at once, as before. */
	@Test
	public void aProcessIsNotHeldUnlessItsParentStartedInTheTrace(){
		ordering.accept(event("orphan", "100.000", "3001", "3000", "0", "0"), sink);
		known.add("4000"); // first seen, not started
		ordering.accept(event("child of first seen", "100.001", "4001", "4000", "0", "0"), sink);
		ordering.accept(event("parent", "100.002", "1000", "999", "0", "0"), sink);
		assertEquals(List.of("orphan", "child of first seen", "parent"), handled);
	}

	/** A child of a held process is held too, and a held child's own vfork releases it. */
	@Test
	public void aGrandchildFollowsItsParentsCreation(){
		ordering.accept(event("child execve", "100.005", "1001", "1000", "59", "0"), sink);
		ordering.accept(event("grandchild execve", "100.010", "1002", "1001", "59", "0"), sink);
		ordering.accept(event("child vfork", "100.006", "1001", "1000", "58", "1002"), sink);
		ordering.accept(event("parent vfork", "100.004", "1000", "999", "58", "1001"), sink);
		assertEquals(List.of("parent vfork", "child execve", "child vfork", "grandchild execve"), handled);
	}

	@Test
	public void flushPassesWhatIsLeft(){
		ordering.accept(event("child execve", "100.000", "1001", "1000", "59", "0"), sink);
		ordering.flush(sink);
		assertEquals(List.of("child execve"), handled);
	}
}
