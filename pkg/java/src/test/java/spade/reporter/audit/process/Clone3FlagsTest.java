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

import org.junit.jupiter.api.Test;

import spade.reporter.audit.SYSCALL;

class Clone3FlagsTest{

	@Test
	void syscall435IsClone3(){
		assertEquals(SYSCALL.CLONE3, SYSCALL.get64BitSyscall(435));
		assertEquals(SYSCALL.CLONE, SYSCALL.get64BitSyscall(56));
	}

	@Test
	void posixSpawnFlagsKeepWhatCloneCanExpress(){
		// glibc's posix_spawn: CLONE_VM|CLONE_VFORK|CLONE_CLEAR_SIGHAND, exit signal SIGCHLD,
		// as the kernel module reports it (0x100004111). CLONE_CLEAR_SIGHAND is bit 32.
		assertEquals(Integer.toString(0x4111), ProcessManager.clone3FlagsAsCloneFlags("4294983953"));
	}

	@Test
	void flagsInTheLowWordAreUnchanged(){
		// pthread_create's thread flags with no exit signal
		final int threadFlags = 0x3d0f00;
		assertEquals(Integer.toString(threadFlags), ProcessManager.clone3FlagsAsCloneFlags(Integer.toString(threadFlags)));
	}

	@Test
	void bit31BecomesNegativeAsForClone(){
		// clone's a0 with 8 hex digits converts to an int the same way (Audit.convertArgsHexToDec)
		assertEquals(Integer.toString(0x80000011), ProcessManager.clone3FlagsAsCloneFlags("2147483665"));
	}

	@Test
	void missingOrInvalidFlagsAreZero(){
		assertEquals("0", ProcessManager.clone3FlagsAsCloneFlags(null));
		assertEquals("0", ProcessManager.clone3FlagsAsCloneFlags("0x4111"));
		assertEquals("0", ProcessManager.clone3FlagsAsCloneFlags(""));
	}
}
