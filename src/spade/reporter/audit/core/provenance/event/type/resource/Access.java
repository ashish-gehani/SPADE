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

public abstract class Access extends Event{

	private final AbstractProcess accessor;
	private final AbstractResource resource;

	public Access(final ID id, final AbstractProcess accessor, final AbstractResource resource){
		super(ResourceType.ACCESS, id);
		if(accessor == null){
			throw new IllegalArgumentException("accessor cannot be NULL");
		}
		if(resource == null){
			throw new IllegalArgumentException("resource cannot be NULL");
		}
		this.accessor = accessor;
		this.resource = resource;
	}

	public AbstractProcess getAccessor(){
		return accessor;
	}

	public AbstractResource getResource(){
		return resource;
	}

	@Override
	public List<ProvenanceElement> handle(final AbstractContext context, final ManagerContext managerContext){
		final AbstractVertex accessorVertex = managerContext.getVertexGenerator().generate();
		accessorVertex.addAnnotations(accessor.getKeyAnnotations(context));
		accessorVertex.addAnnotations(accessor.getExtraAnnotations(context));

		final AbstractVertex resourceVertex = managerContext.getVertexGenerator().generate();
		resourceVertex.addAnnotations(resource.getKeyAnnotations(context));
		resourceVertex.addAnnotations(resource.getExtraAnnotations(context));

		final AbstractEdge edge = managerContext.getEdgeGenerator().generate(accessorVertex, resourceVertex);
		edge.addAnnotations(getKeyAnnotations(context));
		edge.addAnnotations(getExtraAnnotations(context));

		final List<ProvenanceElement> elements = new ArrayList<>();
		elements.add(ProvenanceElement.of(accessorVertex));
		elements.add(ProvenanceElement.of(resourceVertex));
		elements.add(ProvenanceElement.of(edge));
		return elements;
	}

}
