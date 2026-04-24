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


public class ResourceCreate extends spade.reporter.audit.linux.provenance.event.Event{

	private final ModelProcess creator;
	private final ModelResource resource;

	public ResourceCreate(final ID id, final ModelEvent modelEvent, final ModelProcess creator, final ModelResource resource){
		super(Type.RESOURCE_CREATE, id, modelEvent);
		if(creator == null){
			throw new IllegalArgumentException("creator cannot be NULL");
		}
		if(resource == null){
			throw new IllegalArgumentException("resource cannot be NULL");
		}
		this.creator = creator;
		this.resource = resource;
	}

	public ModelProcess getCreator(){
		return creator;
	}

	public ModelResource getResource(){
		return resource;
	}
}
