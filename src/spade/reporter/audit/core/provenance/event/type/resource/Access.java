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
import spade.reporter.audit.core.provenance.Context;
import spade.reporter.audit.core.provenance.ProvenanceElement;
import spade.reporter.audit.core.provenance.event.Event;
import spade.reporter.audit.core.provenance.event.ID;
import spade.reporter.audit.core.provenance.event.ResourceType;
import spade.reporter.audit.core.provenance.type.AbstractContext;
import spade.reporter.audit.core.provenance.type.AbstractProcess;
import spade.reporter.audit.core.provenance.type.AbstractResource;

public abstract class Access<C extends AbstractContext> extends Event<C>{

	private final AbstractProcess<C> accessor;
	private final AbstractResource<C> resource;

	public Access(final ID id, final AbstractProcess<C> accessor, final AbstractResource<C> resource){
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

	public AbstractProcess<C> getAccessor(){
		return accessor;
	}

	public AbstractResource<C> getResource(){
		return resource;
	}

	@Override
	public List<ProvenanceElement> handle(final C provContext, final Context managerContext){
		final AbstractVertex accessorVertex = managerContext.getVertexGenerator().generate();
		accessorVertex.addAnnotations(accessor.getKeyAnnotations(provContext));
		accessorVertex.addAnnotations(accessor.getExtraAnnotations(provContext));

		final AbstractVertex resourceVertex = managerContext.getVertexGenerator().generate();
		resourceVertex.addAnnotations(resource.getKeyAnnotations(provContext));
		resourceVertex.addAnnotations(resource.getExtraAnnotations(provContext));

		final AbstractEdge edge = managerContext.getEdgeGenerator().generate(accessorVertex, resourceVertex);
		edge.addAnnotations(getKeyAnnotations(provContext));
		edge.addAnnotations(getExtraAnnotations(provContext));

		final List<ProvenanceElement> elements = new ArrayList<>();
		elements.add(ProvenanceElement.of(accessorVertex));
		elements.add(ProvenanceElement.of(resourceVertex));
		elements.add(ProvenanceElement.of(edge));
		return elements;
	}

}
