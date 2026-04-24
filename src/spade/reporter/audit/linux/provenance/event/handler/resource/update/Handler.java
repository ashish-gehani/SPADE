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
import spade.reporter.audit.linux.provenance.SourceEvent;
import spade.reporter.audit.linux.provenance.PlatformProcess;
import spade.reporter.audit.linux.provenance.PlatformResource;
import spade.reporter.audit.linux.provenance.event.handler.Context;
import spade.reporter.audit.linux.provenance.event.resource.update.Event;

public class Handler implements spade.reporter.audit.core.provenance.event.handler.Handler<Event, Context>{

	@Override
	public List<ProvenanceElement> handle(final Event event, final Context provContext){
		final PlatformProcess provUpdater = event.getUpdater();
		final PlatformResource provOldVersion = event.getOldVersion();
		final PlatformResource provNewVersion = event.getNewVersion();
		final SourceEvent sourceEvent = event.getSourceEvent();

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
		updaterToNew.addAnnotations(sourceEvent.getKeyAnnotations(provContext));
		updaterToNew.addAnnotations(sourceEvent.getExtraAnnotations(provContext));

		final AbstractEdge newToOld = provContext.getEdgeGenerator().generate(newVertex, oldVertex);
		newToOld.addAnnotations(sourceEvent.getKeyAnnotations(provContext));
		newToOld.addAnnotations(sourceEvent.getExtraAnnotations(provContext));

		final List<ProvenanceElement> elements = new ArrayList<>();
		elements.add(ProvenanceElement.of(updaterVertex));
		elements.add(ProvenanceElement.of(oldVertex));
		elements.add(ProvenanceElement.of(newVertex));
		elements.add(ProvenanceElement.of(updaterToNew));
		elements.add(ProvenanceElement.of(newToOld));
		return elements;
	}

}
