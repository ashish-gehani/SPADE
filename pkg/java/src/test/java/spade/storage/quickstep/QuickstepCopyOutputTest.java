/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2020 SRI International

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
package spade.storage.quickstep;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Reading Quickstep's copy ... to stdout, which writes a backslash as \\, a newline, tab or carriage return as
 * \n, \t, \r, and the delimiter with a backslash before it (seen with Quickstep's local mode).
 */
class QuickstepCopyOutputTest{

	@Test
	void aValueWithoutABackslash_isAsWritten(){
		assertEquals("grep -q Capability", QuickstepInstructionExecutor.unescapeCopyField("grep -q Capability"));
	}

	@Test
	void aBackslash_comesBackAsOne(){ // a command line of trace 3: grep -q 'Capability bounding set:.*\bsetuid\b'
		assertEquals("set:.*\\bsetuid\\b", QuickstepInstructionExecutor.unescapeCopyField("set:.*\\\\bsetuid\\\\b"));
		assertEquals("two\\\\backslashes", QuickstepInstructionExecutor.unescapeCopyField("two\\\\\\\\backslashes"));
	}

	@Test
	void newlineTabAndCarriageReturn_comeBackAsCharacters(){
		assertEquals("new\nline tab\there cr\rx", QuickstepInstructionExecutor.unescapeCopyField("new\\nline tab\\there cr\\rx"));
	}

	@Test
	void anEscapedDelimiter_comesBackAsTheDelimiter(){
		assertEquals("pipe|bar", QuickstepInstructionExecutor.unescapeCopyField("pipe\\|bar"));
	}

	@Test
	void aRow_isSplitAtTheDelimitersNotEscaped(){
		assertArrayEquals(new String[]{"a,b", "c\\", "", "7"},
				QuickstepInstructionExecutor.splitCopyRow("a\\,b,c\\\\,,7", ','));
		assertArrayEquals(new String[]{"value", "3"}, QuickstepInstructionExecutor.splitCopyRow("value,3", ','));
	}
}
