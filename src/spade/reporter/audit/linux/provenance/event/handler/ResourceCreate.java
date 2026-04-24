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
package spade.reporter.audit.linux.provenance.event.handler;

import java.util.ArrayList;
import java.util.List;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.reporter.audit.core.provenance.ProvenanceElement;
import spade.reporter.audit.linux.provenance.ModelProcess;
import spade.reporter.audit.linux.provenance.ModelResource;
import spade.reporter.audit.linux.provenance.ModelEvent;

public class ResourceCreate implements spade.reporter.audit.core.provenance.event.handler.Handler<spade.reporter.audit.linux.provenance.event.ResourceCreate, Context>{

	@Override
	public List<ProvenanceElement> handle(final spade.reporter.audit.linux.provenance.event.ResourceCreate event, final Context provContext){
		final ModelProcess provCreator = event.getCreator();
		final ModelResource provResource = event.getResource();
		final ModelEvent modelEvent = event.getModelEvent();

		final AbstractVertex creatorVertex = provContext.getVertexGenerator().generate();
		creatorVertex.addAnnotations(provCreator.getKeyAnnotations(provContext));
		creatorVertex.addAnnotations(provCreator.getExtraAnnotations(provContext));

		final AbstractVertex resourceVertex = provContext.getVertexGenerator().generate();
		resourceVertex.addAnnotations(provResource.getKeyAnnotations(provContext));
		resourceVertex.addAnnotations(provResource.getExtraAnnotations(provContext));

		final AbstractEdge edge = provContext.getEdgeGenerator().generate(resourceVertex, creatorVertex);
		edge.addAnnotations(modelEvent.getKeyAnnotations(provContext));
		edge.addAnnotations(modelEvent.getExtraAnnotations(provContext));

		final List<ProvenanceElement> elements = new ArrayList<>();
		elements.add(ProvenanceElement.of(creatorVertex));
		elements.add(ProvenanceElement.of(resourceVertex));
		elements.add(ProvenanceElement.of(edge));
		return elements;
	}

}
