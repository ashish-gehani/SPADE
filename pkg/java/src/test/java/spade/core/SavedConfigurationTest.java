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
package spade.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class SavedConfigurationTest{

	@Test
	void resetIsNotSaved(){
		assertNull(SavedConfiguration.storageArgumentsToSave("reset=true"));
		assertEquals("batchTimeIntervalInSeconds=30",
				SavedConfiguration.storageArgumentsToSave("reset=true batchTimeIntervalInSeconds=30"));
		assertEquals("dbName=spade_pg host=localhost",
				SavedConfiguration.storageArgumentsToSave("dbName=spade_pg reset=TRUE  host=localhost"));
		assertEquals("a=1", SavedConfiguration.storageArgumentsToSave("a=1 Reset = false"));
	}

	@Test
	void otherArgumentsAreKept(){
		assertNull(SavedConfiguration.storageArgumentsToSave(null));
		assertNull(SavedConfiguration.storageArgumentsToSave("  "));
		assertEquals("database=spade.graph", SavedConfiguration.storageArgumentsToSave("database=spade.graph"));
		assertEquals("noreset=true", SavedConfiguration.storageArgumentsToSave("noreset=true"));
	}
}
