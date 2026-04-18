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

public abstract class Create extends Event{

	private final Process creator;
	private final Resource resource;

	public Create(
		final ID id,
		final Process creator,
		final Resource resource
	){
		super(Type.RESOURCE_CREATE, id);
		if(creator == null){
			throw new IllegalArgumentException("creator cannot be NULL");
		}
		if(resource == null){
			throw new IllegalArgumentException("resource cannot be NULL");
		}
		this.creator = creator;
		this.resource = resource;
	}

	public Process getCreator(){
		return creator;
	}

	public Resource getResource(){
		return resource;
	}

}
