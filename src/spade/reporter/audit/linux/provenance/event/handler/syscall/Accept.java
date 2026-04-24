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
package spade.reporter.audit.linux.provenance.event.handler.syscall;

import java.util.ArrayList;
import java.util.List;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.reporter.audit.core.provenance.ProvenanceElement;
import spade.reporter.audit.linux.provenance.ModelProcess;
import spade.reporter.audit.linux.provenance.ModelResource;
import spade.reporter.audit.linux.provenance.ModelEvent;
import spade.reporter.audit.linux.provenance.event.handler.Context;

public class Accept implements spade.reporter.audit.core.provenance.event.handler.Handler<spade.reporter.audit.linux.provenance.event.syscall.Accept, Context>{

	@Override
	public List<ProvenanceElement> handle(final spade.reporter.audit.linux.provenance.event.syscall.Accept event, final Context provContext){
		final ModelProcess provAccessor = event.getAccessor();
		final ModelResource provResource = event.getResource();
		final ModelEvent modelEvent = event.getModelEvent();

		final AbstractVertex accessorVertex = provContext.getVertexGenerator().generate();
		accessorVertex.addAnnotations(provAccessor.getKeyAnnotations(provContext));
		accessorVertex.addAnnotations(provAccessor.getExtraAnnotations(provContext));

		final AbstractVertex resourceVertex = provContext.getVertexGenerator().generate();
		resourceVertex.addAnnotations(provResource.getKeyAnnotations(provContext));
		resourceVertex.addAnnotations(provResource.getExtraAnnotations(provContext));

		final AbstractEdge edge = provContext.getEdgeGenerator().generate(accessorVertex, resourceVertex);
		edge.addAnnotations(modelEvent.getKeyAnnotations(provContext));
		edge.addAnnotations(modelEvent.getExtraAnnotations(provContext));

		final List<ProvenanceElement> elements = new ArrayList<>();
		elements.add(ProvenanceElement.of(accessorVertex));
		elements.add(ProvenanceElement.of(resourceVertex));
		elements.add(ProvenanceElement.of(edge));
		return elements;
	}

}
