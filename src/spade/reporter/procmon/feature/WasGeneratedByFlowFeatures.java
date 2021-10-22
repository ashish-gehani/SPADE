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

import static spade.utility.feature.Count.ZERO;

import spade.utility.feature.ConditionalCount.Condition;
import spade.utility.feature.Count;
import spade.utility.feature.UniqueItems;

public class WasGeneratedByFlowFeatures extends FlowFeatures{

	private final UniqueItems executableFilePathSet;
	private final Count registrySetInfoKey, registrySetValue;
	
	public WasGeneratedByFlowFeatures(Condition condition){
		super(condition);
		executableFilePathSet = new UniqueItems();
		registrySetInfoKey = new Count(ZERO);
		registrySetValue = new Count(ZERO);
	}

	public void updateExecutableFilePathSet(final String executableFilePath){
		executableFilePathSet.update(executableFilePath);
	}

	public boolean containsExecutableFilePath(final String executableFilePath){
		return executableFilePathSet.contains(executableFilePath);
	}

	public void updateRegistrySetInfoKey(){
		registrySetInfoKey.update();
	}
	
	public void updateRegistrySetValue(){
		registrySetValue.update();
	}

	public Count getRegistrySetInfoKey(){
		return registrySetInfoKey;
	}

	public Count getRegistrySetValue(){
		return registrySetValue;
	}
}
