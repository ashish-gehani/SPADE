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
package spade.reporter.audit.linux.provenance.event.handler.resource.delete;

import java.util.ArrayList;
import java.util.List;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.reporter.audit.core.provenance.ProvenanceElement;
import spade.reporter.audit.linux.provenance.SourceEvent;
import spade.reporter.audit.linux.provenance.PlatformProcess;
import spade.reporter.audit.linux.provenance.PlatformResource;
import spade.reporter.audit.linux.provenance.event.handler.Context;
import spade.reporter.audit.linux.provenance.event.resource.delete.Event;

public class Handler implements spade.reporter.audit.core.provenance.event.handler.Handler<Event, Context>{

	@Override
	public List<ProvenanceElement> handle(final Event event, final Context provContext){
		final PlatformProcess provDeleter = event.getDeleter();
		final PlatformResource provResource = event.getResource();
		final SourceEvent sourceEvent = event.getSourceEvent();

		final AbstractVertex deleterVertex = provContext.getVertexGenerator().generate();
		deleterVertex.addAnnotations(provDeleter.getKeyAnnotations(provContext));
		deleterVertex.addAnnotations(provDeleter.getExtraAnnotations(provContext));

		final AbstractVertex resourceVertex = provContext.getVertexGenerator().generate();
		resourceVertex.addAnnotations(provResource.getKeyAnnotations(provContext));
		resourceVertex.addAnnotations(provResource.getExtraAnnotations(provContext));

		final AbstractEdge edge = provContext.getEdgeGenerator().generate(deleterVertex, resourceVertex);
		edge.addAnnotations(sourceEvent.getKeyAnnotations(provContext));
		edge.addAnnotations(sourceEvent.getExtraAnnotations(provContext));

		final List<ProvenanceElement> elements = new ArrayList<>();
		elements.add(ProvenanceElement.of(deleterVertex));
		elements.add(ProvenanceElement.of(resourceVertex));
		elements.add(ProvenanceElement.of(edge));
		return elements;
	}

}
