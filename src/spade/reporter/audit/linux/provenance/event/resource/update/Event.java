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
package spade.reporter.audit.linux.provenance.event.resource.update;

import spade.reporter.audit.core.provenance.event.ID;
import spade.reporter.audit.linux.provenance.ProvEvent;
import spade.reporter.audit.linux.provenance.ProvProcess;
import spade.reporter.audit.linux.provenance.ProvResource;
import spade.reporter.audit.linux.provenance.event.ResourceType;

public class Event extends spade.reporter.audit.linux.provenance.event.Event{

	private final ProvProcess updater;
	private final ProvResource oldVersion;
	private final ProvResource newVersion;

	public Event(
		final ID id,
		final ProvEvent provEvent,
		final ProvProcess updater,
		final ProvResource oldVersion,
		final ProvResource newVersion
	){
		super(ResourceType.UPDATE, id, provEvent);
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

	public ProvProcess getUpdater(){
		return updater;
	}

	public ProvResource getOldVersion(){
		return oldVersion;
	}

	public ProvResource getNewVersion(){
		return newVersion;
	}
}
