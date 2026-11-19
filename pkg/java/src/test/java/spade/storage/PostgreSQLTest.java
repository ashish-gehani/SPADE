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
package spade.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PostgreSQLTest{

	@Test
	void nullIsAnEmptyUnquotedField(){
		assertEquals("", PostgreSQL.formatToCSV(null));
	}

	@Test
	void emptyStringIsQuoted(){
		// in CSV format, an unquoted empty field is NULL and "" is the empty string
		assertEquals("\"\"", PostgreSQL.formatToCSV(""));
	}

	@Test
	void plainValueIsQuoted(){
		assertEquals("\"/bin/sh\"", PostgreSQL.formatToCSV("/bin/sh"));
	}

	@Test
	void quoteIsDoubled(){
		assertEquals("\"sh -c \"\"true\"\"\"", PostgreSQL.formatToCSV("sh -c \"true\""));
	}

	@Test
	void delimiterAndNewlineStayInsideTheQuotes(){
		assertEquals("\"a,b\nc\"", PostgreSQL.formatToCSV("a,b\nc"));
	}

	@Test
	void nulIsDropped(){
		assertEquals("\"gdbus\"", PostgreSQL.formatToCSV("gdbus\0\0\0"));
		assertEquals("\"ab\"", PostgreSQL.formatToCSV("a\0b"));
	}
}
