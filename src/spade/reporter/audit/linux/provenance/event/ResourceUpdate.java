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
import spade.reporter.audit.linux.provenance.ModelEvent;
import spade.reporter.audit.linux.provenance.ModelProcess;
import spade.reporter.audit.linux.provenance.ModelResource;


public class ResourceUpdate extends spade.reporter.audit.linux.provenance.event.Event{

	private final ModelProcess updater;
	private final ModelResource oldVersion;
	private final ModelResource newVersion;

	public ResourceUpdate(
		final ID id,
		final ModelEvent modelEvent,
		final ModelProcess updater,
		final ModelResource oldVersion,
		final ModelResource newVersion
	){
		super(Type.RESOURCE_UPDATE, id, modelEvent);
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

	public ModelProcess getUpdater(){
		return updater;
	}

	public ModelResource getOldVersion(){
		return oldVersion;
	}

	public ModelResource getNewVersion(){
		return newVersion;
	}
}
