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
package spade.reporter.audit.core.provenance.event.type.process;

import spade.reporter.audit.core.provenance.Process;
import spade.reporter.audit.core.provenance.event.Event;
import spade.reporter.audit.core.provenance.event.ID;
import spade.reporter.audit.core.provenance.event.Type;

public abstract class Update extends Event{

	private final Process oldVersion;
	private final Process newVersion;

	public Update(
		final ID id,
		final Process oldVersion,
		final Process newVersion
	){
		super(Type.PROCESS_CREATE, id);
		if(oldVersion == null){
			throw new IllegalArgumentException("oldVersion cannot be NULL");
		}
		if(newVersion == null){
			throw new IllegalArgumentException("newVersion cannot be NULL");
		}
		this.oldVersion = oldVersion;
		this.newVersion = newVersion;
	}

	public Process getOldVersion(){
		return oldVersion;
	}

	public Process getNewVersion(){
		return newVersion;
	}

}
