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

import org.junit.jupiter.api.Test;

public class OPMConstantsTest{

	/** openat2's edges are named as openat's, also when an open truncates (open plus write). */
	@Test
	public void openat2IsAnOpen(){
		assertEquals(OPMConstants.getOperation(SYSCALL.OPENAT, null, true),
				OPMConstants.getOperation(SYSCALL.OPENAT2, null, true));
		assertEquals(OPMConstants.OPERATION_OPEN, OPMConstants.getOperation(SYSCALL.OPENAT2, null, true));
		assertEquals(OPMConstants.getOperation(SYSCALL.OPENAT, SYSCALL.WRITE, true),
				OPMConstants.getOperation(SYSCALL.OPENAT2, SYSCALL.WRITE, true));
	}
}
