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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class LinuxConstantsTest{

	// Resolved against the Surefire working directory (pkg/java), where cfg links to the root cfg
	private static final String SHIPPED_CONSTANTS = "cfg/spade.reporter.audit.LinuxConstants.config";

	@Test
	public void procPidInitIno_isReadFromShippedConstantsFile() throws Exception{
		final LinuxConstants constants = new LinuxConstants();
		constants.initialize(SHIPPED_CONSTANTS);

		// include/linux/proc_ns.h: PROC_PID_INIT_INO = 0xEFFFFFFCU
		assertEquals(4026531836L, constants.getProcPidInitIno());
	}

	@Test
	public void procPidInitIno_isOptionalWhenLoadingButRequiredWhenRequested(@TempDir final Path dir) throws Exception{
		final List<String> linesWithoutKey = Files.readAllLines(Path.of(SHIPPED_CONSTANTS), StandardCharsets.UTF_8)
				.stream()
				.filter(line -> !line.trim().startsWith("PROC_PID_INIT_INO"))
				.collect(Collectors.toList());
		final Path constantsWithoutKey = dir.resolve("constants-without-proc-pid-init-ino.config");
		Files.write(constantsWithoutKey, linesWithoutKey, StandardCharsets.UTF_8);

		final LinuxConstants constants = new LinuxConstants();
		// Must still load: the Audit reporter does not use PROC_PID_INIT_INO
		constants.initialize(constantsWithoutKey.toString());

		final Exception e = assertThrows(Exception.class, constants::getProcPidInitIno);
		assertTrue(e.getMessage().contains("PROC_PID_INIT_INO"),
				"error must name the missing key; got: " + e.getMessage());
	}
}
