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
package spade.reporter.audit.process;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * An exit (60) record names the thread group (audit's pid= is the thread group id), so it
 * cannot say which thread of a multi-threaded process exited.
 */
class ThreadExitTest{

	private static final ProcessKey LEADER = new ProcessKey("312246", "1793587906.752");
	private static final ProcessKey THREAD = new ProcessKey("342867", "1793588023.242");

	private static Set<ProcessKey> threads(final ProcessKey... keys){
		final Set<ProcessKey> set = new HashSet<ProcessKey>();
		for(final ProcessKey key : keys){
			set.add(key);
		}
		return set;
	}

	@Test
	void aProcessWithoutThreadsExits(){
		assertTrue(ProcessManager.exitEndsState(LEADER, null));
		assertTrue(ProcessManager.exitEndsState(LEADER, threads()));
	}

	@Test
	void aRecordNamingTheGroupKeepsItWhileOtherThreadsRemain(){
		// a thread of movie-id-service exited; the record names the service's own pid
		assertFalse(ProcessManager.exitEndsState(LEADER, threads(THREAD)));
	}

	@Test
	void aRecordNamingAKnownThreadEndsThatThread(){
		assertTrue(ProcessManager.exitEndsState(THREAD, threads(THREAD)));
		assertTrue(ProcessManager.exitEndsState(new ProcessKey("342867", "1793588023.242"), threads(THREAD)));
	}

	@Test
	void aProcessWithoutStateAndOtherThreadsKeepsThem(){
		assertFalse(ProcessManager.exitEndsState(null, threads(THREAD)));
	}
}
