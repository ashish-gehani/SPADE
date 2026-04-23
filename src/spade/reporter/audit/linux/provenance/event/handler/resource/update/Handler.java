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
package spade.reporter.audit.linux.provenance.event.handler.resource.update;

import java.util.ArrayList;
import java.util.List;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.reporter.audit.core.provenance.ProvenanceElement;
import spade.reporter.audit.linux.provenance.ProvEvent;
import spade.reporter.audit.linux.provenance.ProvProcess;
import spade.reporter.audit.linux.provenance.ProvResource;
import spade.reporter.audit.linux.provenance.event.handler.Context;
import spade.reporter.audit.linux.provenance.event.type.resource.update.Event;

public class Handler implements spade.reporter.audit.core.provenance.event.handler.Handler<Event, Context>{

	@Override
	public List<ProvenanceElement> handle(final Event event, final Context provContext){
		final ProvProcess provUpdater = event.getUpdater();
		final ProvResource provOldVersion = event.getOldVersion();
		final ProvResource provNewVersion = event.getNewVersion();
		final ProvEvent provEvent = event.getProvEvent();

		final AbstractVertex updaterVertex = provContext.getVertexGenerator().generate();
		updaterVertex.addAnnotations(provUpdater.getKeyAnnotations(provContext));
		updaterVertex.addAnnotations(provUpdater.getExtraAnnotations(provContext));

		final AbstractVertex oldVertex = provContext.getVertexGenerator().generate();
		oldVertex.addAnnotations(provOldVersion.getKeyAnnotations(provContext));
		oldVertex.addAnnotations(provOldVersion.getExtraAnnotations(provContext));

		final AbstractVertex newVertex = provContext.getVertexGenerator().generate();
		newVertex.addAnnotations(provNewVersion.getKeyAnnotations(provContext));
		newVertex.addAnnotations(provNewVersion.getExtraAnnotations(provContext));

		final AbstractEdge updaterToNew = provContext.getEdgeGenerator().generate(updaterVertex, newVertex);
		updaterToNew.addAnnotations(provEvent.getKeyAnnotations(provContext));
		updaterToNew.addAnnotations(provEvent.getExtraAnnotations(provContext));

		final AbstractEdge newToOld = provContext.getEdgeGenerator().generate(newVertex, oldVertex);
		newToOld.addAnnotations(provEvent.getKeyAnnotations(provContext));
		newToOld.addAnnotations(provEvent.getExtraAnnotations(provContext));

		final List<ProvenanceElement> elements = new ArrayList<>();
		elements.add(ProvenanceElement.of(updaterVertex));
		elements.add(ProvenanceElement.of(oldVertex));
		elements.add(ProvenanceElement.of(newVertex));
		elements.add(ProvenanceElement.of(updaterToNew));
		elements.add(ProvenanceElement.of(newToOld));
		return elements;
	}

}
