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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import spade.reporter.audit.artifact.UnknownIdentifier;

/** The kernel closes close-on-exec descriptors at execve, and so does the descriptor model. */
class CloseOnExecTest{

	private static final NamespaceIdentifier NO_NAMESPACES = new NamespaceIdentifier(null, null, null, null, null, null, null);

	private final ProcessStateManager states = new ProcessStateManager(){};

	private static FileDescriptor descriptor(final String fd){
		return new FileDescriptor(new UnknownIdentifier("100", fd), true);
	}

	@Test
	void execveClosesOnlyTheCloseOnExecDescriptors(){
		states.setFd("100", "3", descriptor("3"));
		states.setFd("100", "4", descriptor("4"));
		states.setFdCloseOnExec("100", "4", true);
		states.processExecved("100", "/", NO_NAMESPACES);
		assertNotNull(states.getFd("100", "3"));
		assertNull(states.getFd("100", "4"));
		assertFalse(states.isFdCloseOnExec("100", "3"));
	}

	@Test
	void theFlagBelongsToTheNumberNotTheOpenFile(){
		final FileDescriptor shared = descriptor("5");
		states.setFd("100", "5", shared);
		states.setFdCloseOnExec("100", "5", true);
		states.setFd("100", "6", shared); // dup: a new number for the same open file, not close-on-exec
		states.processExecved("100", "/", NO_NAMESPACES);
		assertNull(states.getFd("100", "5"));
		assertNotNull(states.getFd("100", "6"));
	}

	@Test
	void aNewDescriptorWithAReusedNumberIsNotCloseOnExec(){
		states.setFd("100", "7", descriptor("7"));
		states.setFdCloseOnExec("100", "7", true);
		states.removeFd("100", "7");
		states.setFd("100", "7", descriptor("7"));
		assertFalse(states.isFdCloseOnExec("100", "7"));
		states.setFdCloseOnExec("100", "8", true); // a number with no descriptor
		assertFalse(states.isFdCloseOnExec("100", "8"));
	}

	@Test
	void forkCopiesTheFlagsAndCloneWithSharedFilesSharesThem(){
		states.setFd("100", "3", descriptor("3"));
		states.setFdCloseOnExec("100", "3", true);
		states.processForked("100", "200", NO_NAMESPACES);
		states.processCloned("100", "300", true, true, true, NO_NAMESPACES);
		// the forked child's execve closes its copy only
		states.processExecved("200", "/", NO_NAMESPACES);
		assertNull(states.getFd("200", "3"));
		assertNotNull(states.getFd("100", "3"));
		// the thread shares the table and its flags
		states.setFdCloseOnExec("300", "3", false);
		assertFalse(states.isFdCloseOnExec("100", "3"));
		states.setFdCloseOnExec("100", "3", true);
		assertTrue(states.isFdCloseOnExec("300", "3"));
	}

	@Test
	void unsharingGivesTheProcessItsOwnTable(){
		states.setFd("100", "3", descriptor("3"));
		states.processCloned("100", "300", true, true, true, NO_NAMESPACES);
		states.unshareFds("300"); // close_range with CLOSE_RANGE_UNSHARE
		states.removeFd("300", "3");
		assertNotNull(states.getFd("100", "3"));
		final List<String> numbers = states.getFdNumbers("100");
		assertEquals(Collections.singletonList("3"), numbers);
	}
}
