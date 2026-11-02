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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class KernelModuleManagerTest{

	private static final String MAIN = "/home/spade/SPADE/lib/kernel-modules/spade_audit.ko";

	@Test
	public void theSameBuildGivesNoWarning(){
		assertNull(KernelModuleManager.staleModuleWarning(MAIN, "5C3F0A1B2D", "5C3F0A1B2D"));
	}

	/** Without both srcversions (no /sys/module entry, no modinfo) nothing can be said. */
	@Test
	public void anUnknownSrcversionGivesNoWarning(){
		assertNull(KernelModuleManager.staleModuleWarning(MAIN, null, "5C3F0A1B2D"));
		assertNull(KernelModuleManager.staleModuleWarning(MAIN, "5C3F0A1B2D", null));
		assertNull(KernelModuleManager.staleModuleWarning(MAIN, "", "5C3F0A1B2D"));
	}

	/** Another build is named, with both srcversions, and the remedy is a reboot, not rmmod. */
	@Test
	public void anotherBuildIsReportedWithItsRemedy(){
		final String warning = KernelModuleManager.staleModuleWarning(MAIN, "OLD0000000", "NEW1111111");
		assertNotNull(warning);
		assertTrue(warning.contains("'spade_audit'"), warning);
		assertTrue(warning.contains("OLD0000000") && warning.contains("NEW1111111"), warning);
		assertTrue(warning.contains("Reboot"), warning);
	}
}
