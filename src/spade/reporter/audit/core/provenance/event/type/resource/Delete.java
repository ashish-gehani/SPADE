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
import spade.reporter.audit.core.provenance.Process;
import spade.reporter.audit.core.provenance.Context;
import spade.reporter.audit.core.provenance.ProvenanceElement;
import spade.reporter.audit.core.provenance.Resource;
import spade.reporter.audit.core.provenance.event.Event;
import spade.reporter.audit.core.provenance.event.ID;
import spade.reporter.audit.core.provenance.event.ResourceType;


public abstract class Delete<C extends Context> extends Event<C>{

	private final Process<C> deleter;
	private final Resource<C> resource;

	public Delete(final ID id, final Process<C> deleter, final Resource<C> resource){
		super(ResourceType.DELETE, id);
		if(deleter == null){
			throw new IllegalArgumentException("deleter cannot be NULL");
		}
		if(resource == null){
			throw new IllegalArgumentException("resource cannot be NULL");
		}
		this.deleter = deleter;
		this.resource = resource;
	}

	public Process<C> getDeleter(){
		return deleter;
	}

	public Resource<C> getResource(){
		return resource;
	}

	@Override
	public List<ProvenanceElement> handle(final C provContext){
		final AbstractVertex deleterVertex = provContext.getVertexGenerator().generate();
		deleterVertex.addAnnotations(deleter.getKeyAnnotations(provContext));
		deleterVertex.addAnnotations(deleter.getExtraAnnotations(provContext));

		final AbstractVertex resourceVertex = provContext.getVertexGenerator().generate();
		resourceVertex.addAnnotations(resource.getKeyAnnotations(provContext));
		resourceVertex.addAnnotations(resource.getExtraAnnotations(provContext));

		final AbstractEdge edge = provContext.getEdgeGenerator().generate(deleterVertex, resourceVertex);
		edge.addAnnotations(getKeyAnnotations(provContext));
		edge.addAnnotations(getExtraAnnotations(provContext));

		final List<ProvenanceElement> elements = new ArrayList<>();
		elements.add(ProvenanceElement.of(deleterVertex));
		elements.add(ProvenanceElement.of(resourceVertex));
		elements.add(ProvenanceElement.of(edge));
		return elements;
	}

}
