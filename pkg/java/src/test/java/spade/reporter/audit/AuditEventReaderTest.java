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
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class AuditEventReaderTest{

	@Test
	void moduleCommPaddedWithNulsIsCutAtTheFirstNul(){
		// "gdbus" as the audit kernel module writes it: all 16 bytes of the comm buffer
		assertEquals("gdbus", AuditEventReader.decodeHexValue("67646275730000000000000000000000"));
	}

	@Test
	void fullLengthCommIsUnchanged(){
		// "runc:[2:INIT]" plus the terminating NUL fills 14 of 16 bytes
		assertEquals("runc:[2:INIT]", AuditEventReader.decodeHexValue("72756E633A5B323A494E49545D000000"));
		// 15 characters, the longest comm, then one NUL
		assertEquals("systemd-journal", AuditEventReader.decodeHexValue("73797374656D642D6A6F75726E616C00"));
	}

	@Test
	void kernelHexWithoutNulIsUnchanged(){
		// the kernel hex-encodes a path with a space, without any NUL
		assertEquals("/tmp/a b", AuditEventReader.decodeHexValue("2F746D702F612062"));
	}

	@Test
	void allNulCommIsEmpty(){
		assertEquals("", AuditEventReader.decodeHexValue("00000000000000000000000000000000"));
	}

	@Test
	void invalidHexIsNull(){
		assertNull(AuditEventReader.decodeHexValue("6G"));
		assertNull(AuditEventReader.decodeHexValue("676"));
	}
}
