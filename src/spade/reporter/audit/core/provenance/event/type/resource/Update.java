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
package spade.reporter.audit.core.provenance.event.type.resource;

import spade.reporter.audit.core.provenance.Process;
import spade.reporter.audit.core.provenance.Resource;
import spade.reporter.audit.core.provenance.event.Event;
import spade.reporter.audit.core.provenance.event.ID;
import spade.reporter.audit.core.provenance.event.Type;

public abstract class Update extends Event{

	private final Process updater;
	private final Resource oldVersion;
	private final Resource newVersion;

	public Update(
		final ID id,
		final Process updater,
		final Resource oldVersion,
		final Resource newVersion
	){
		super(Type.RESOURCE_UPDATE, id);
		if(updater == null){
			throw new IllegalArgumentException("updater cannot be NULL");
		}
		if(oldVersion == null){
			throw new IllegalArgumentException("oldVersion cannot be NULL");
		}
		if(newVersion == null){
			throw new IllegalArgumentException("newVersion cannot be NULL");
		}
		this.updater = updater;
		this.oldVersion = oldVersion;
		this.newVersion = newVersion;
	}

	public Process getUpdater(){
		return updater;
	}

	public Resource getOldVersion(){
		return oldVersion;
	}

	public Resource getNewVersion(){
		return newVersion;
	}

}
