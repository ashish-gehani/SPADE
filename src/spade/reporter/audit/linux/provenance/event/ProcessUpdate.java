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
package spade.reporter.audit.linux.provenance.event;

import spade.reporter.audit.core.provenance.event.ID;
import spade.reporter.audit.linux.provenance.ModelProcess;
import spade.reporter.audit.linux.provenance.ModelEvent;

public class ProcessUpdate extends Event{

	private final ModelProcess oldVersion;
	private final ModelProcess newVersion;

	public ProcessUpdate(final ID id, final ModelEvent modelEvent, final ModelProcess oldVersion, final ModelProcess newVersion){
		super(Type.PROCESS_UPDATE, id, modelEvent);
		if(oldVersion == null){
			throw new IllegalArgumentException("oldVersion cannot be NULL");
		}
		if(newVersion == null){
			throw new IllegalArgumentException("newVersion cannot be NULL");
		}
		this.oldVersion = oldVersion;
		this.newVersion = newVersion;
	}

	public ModelProcess getOldVersion(){
		return oldVersion;
	}

	public ModelProcess getNewVersion(){
		return newVersion;
	}
}
