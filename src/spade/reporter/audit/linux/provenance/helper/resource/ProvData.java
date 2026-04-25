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
package spade.reporter.audit.linux.provenance.helper.resource;

import spade.reporter.audit.core.platform.util.datastore.Data;
import spade.reporter.audit.core.platform.util.datastore.Key;

public class ProvData{

	private static final Key KEY = new Key(0L, "provData");

	public static boolean containsOnResource(
		final spade.reporter.audit.linux.platform.resource.State resourceState
	){
		if(resourceState == null){
			return false;
		}
		return resourceState.getDataStore().contains(KEY);
	}

	public static spade.reporter.audit.linux.provenance.model.resource.ProvData getFromResource(
		final spade.reporter.audit.linux.platform.resource.State resourceState
	){
		if(resourceState == null){
			return null;
		}
		return (spade.reporter.audit.linux.provenance.model.resource.ProvData) resourceState.getDataStore().get(KEY).getValue();
	}

	public static void putOnResource(
		final spade.reporter.audit.linux.platform.resource.State resourceState,
		final spade.reporter.audit.linux.provenance.model.resource.ProvData provData
	){
		if(resourceState == null){
			return;
		}
		resourceState.getDataStore().put(KEY, new Data(KEY, provData));
	}

	public void todo(){
		/*
			1. Syscall which doesn't update process state
				a. Get the existing process state
					i. Create if it doesn't exist
				b. Use this VersionedID for process to put in prov events
			2. Syscall which does update process state
				a. Get the existing process state
					i. Create if it doesn't exist
					ii. Create new (next) VersionedID
					iii. Create a copy of the process state with the new VersionedID
					iv. Update the new process state
					v. Put the new process state in the process table
				b. Use the old (previous) and the new (next) VersionedID to create prov event
				c. Garbage collect old version(s) after prov event handled
			3. Syscall which doesn't update resource state
				a. Get existing resource state
					i. Create if it doesn't exist
				b. Use the VersionedID for the resource to put in the prov events
			4. Syscall which does update resource state
				a. Get the existing resource state
					i. Create if it doesn't exist
					ii. Create new (next) VersionedID
					iii. Create a copy of the resource state with the new VersionedID
					iv. Update the resource state
					v. Update the prov data in the resource state
					vi. Put the resource state in the resource table
				b. Use the old (previous) and the new (next) VersionedID to create prov event
				c. Garbage collect old version(s) after prov event handled

			--

			A. Add functionality to garbage collect resources which have only the lifetime of
				a process
		*/

	}

}
