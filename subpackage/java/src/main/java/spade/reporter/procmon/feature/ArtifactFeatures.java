/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2021 SRI International

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
package spade.reporter.procmon.feature;

import java.util.HashSet;
import java.util.Set;

public class ArtifactFeatures{

	private final Set<ProcessIdentifier> taintedProcesses = new HashSet<>();
	private final Set<String> wasGeneratedByOperations = new HashSet<>();

	public void addTaintedProcess(final ProcessIdentifier processIdentifier){
		taintedProcesses.add(processIdentifier);
	}

	public void addWasGeneratedByOperation(final String wasGeneratedByOperation){
		wasGeneratedByOperations.add(wasGeneratedByOperation);
	}

	public Set<ProcessIdentifier> getTaintedProcesses(){
		return taintedProcesses;
	}

	public Set<String> getWasGeneratedByOperations(){
		return wasGeneratedByOperations;
	}

}
