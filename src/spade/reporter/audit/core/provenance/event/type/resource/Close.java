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

public abstract class Close extends Event{

	private final AbstractProcess closer;
	private final AbstractResource resource;

	public Close(final ID id, final AbstractProcess closer, final AbstractResource resource){
		super(ResourceType.CLOSE, id);
		if(closer == null){
			throw new IllegalArgumentException("closer cannot be NULL");
		}
		if(resource == null){
			throw new IllegalArgumentException("resource cannot be NULL");
		}
		this.closer = closer;
		this.resource = resource;
	}

	public AbstractProcess getCloser(){
		return closer;
	}

	public AbstractResource getResource(){
		return resource;
	}

	@Override
	public List<ProvenanceElement> handle(final AbstractContext context, final ManagerContext managerContext){
		final AbstractVertex closerVertex = managerContext.getVertexGenerator().generate();
		closerVertex.addAnnotations(closer.getKeyAnnotations());
		closerVertex.addAnnotations(closer.getExtraAnnotations());

		final AbstractVertex resourceVertex = managerContext.getVertexGenerator().generate();
		resourceVertex.addAnnotations(resource.getKeyAnnotations());
		resourceVertex.addAnnotations(resource.getExtraAnnotations());

		final AbstractEdge edge = managerContext.getEdgeGenerator().generate(closerVertex, resourceVertex);
		edge.addAnnotations(getKeyAnnotations());
		edge.addAnnotations(getExtraAnnotations());

		final List<ProvenanceElement> elements = new ArrayList<>();
		elements.add(ProvenanceElement.of(closerVertex));
		elements.add(ProvenanceElement.of(resourceVertex));
		elements.add(ProvenanceElement.of(edge));
		return elements;
	}

}
