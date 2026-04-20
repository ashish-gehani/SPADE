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

import java.util.ArrayList;
import java.util.List;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.reporter.audit.core.provenance.ManagerContext;
import spade.reporter.audit.core.provenance.ProvenanceElement;
import spade.reporter.audit.core.provenance.event.Event;
import spade.reporter.audit.core.provenance.event.ID;
import spade.reporter.audit.core.provenance.event.ResourceType;
import spade.reporter.audit.core.provenance.type.AbstractContext;
import spade.reporter.audit.core.provenance.type.AbstractProcess;
import spade.reporter.audit.core.provenance.type.AbstractResource;

public abstract class Create extends Event{

	private final AbstractProcess creator;
	private final AbstractResource resource;

	public Create(final ID id, final AbstractProcess creator, final AbstractResource resource){
		super(ResourceType.CREATE, id);
		if(creator == null){
			throw new IllegalArgumentException("creator cannot be NULL");
		}
		if(resource == null){
			throw new IllegalArgumentException("resource cannot be NULL");
		}
		this.creator = creator;
		this.resource = resource;
	}

	public AbstractProcess getCreator(){
		return creator;
	}

	public AbstractResource getResource(){
		return resource;
	}

	@Override
	public List<ProvenanceElement> handle(final AbstractContext context, final ManagerContext managerContext){
		final AbstractVertex creatorVertex = managerContext.getVertexGenerator().generate();
		creatorVertex.addAnnotations(creator.getKeyAnnotations());
		creatorVertex.addAnnotations(creator.getExtraAnnotations());

		final AbstractVertex resourceVertex = managerContext.getVertexGenerator().generate();
		resourceVertex.addAnnotations(resource.getKeyAnnotations());
		resourceVertex.addAnnotations(resource.getExtraAnnotations());

		final AbstractEdge edge = managerContext.getEdgeGenerator().generate(resourceVertex, creatorVertex);
		edge.addAnnotations(getKeyAnnotations());
		edge.addAnnotations(getExtraAnnotations());

		final List<ProvenanceElement> elements = new ArrayList<>();
		elements.add(ProvenanceElement.of(creatorVertex));
		elements.add(ProvenanceElement.of(resourceVertex));
		elements.add(ProvenanceElement.of(edge));
		return elements;
	}

}
