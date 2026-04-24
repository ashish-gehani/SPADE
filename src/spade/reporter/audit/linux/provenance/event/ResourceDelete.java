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


public class ResourceDelete extends spade.reporter.audit.linux.provenance.event.Event{

	private final ModelProcess deleter;
	private final ModelResource resource;

	public ResourceDelete(final ID id, final ModelEvent modelEvent, final ModelProcess deleter, final ModelResource resource){
		super(Type.RESOURCE_DELETE, id, modelEvent);
		if(deleter == null){
			throw new IllegalArgumentException("deleter cannot be NULL");
		}
		if(resource == null){
			throw new IllegalArgumentException("resource cannot be NULL");
		}
		this.deleter = deleter;
		this.resource = resource;
	}

	public ModelProcess getDeleter(){
		return deleter;
	}

	public ModelResource getResource(){
		return resource;
	}
}
