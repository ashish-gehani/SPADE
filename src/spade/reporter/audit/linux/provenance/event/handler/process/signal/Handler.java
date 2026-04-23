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
package spade.reporter.audit.linux.provenance.event.handler.process.signal;

import java.util.ArrayList;
import java.util.List;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.reporter.audit.core.provenance.ProvenanceElement;
import spade.reporter.audit.linux.provenance.ProvEvent;
import spade.reporter.audit.linux.provenance.ProvProcess;
import spade.reporter.audit.linux.provenance.event.handler.Context;
import spade.reporter.audit.linux.provenance.event.type.process.signal.Event;

public class Handler implements spade.reporter.audit.core.provenance.event.handler.Handler<Event, Context>{

	@Override
	public List<ProvenanceElement> handle(final Event event, final Context provContext){
		final ProvProcess provSender = event.getSender();
		final ProvProcess provReceiver = event.getReceiver();
		final ProvEvent provEvent = event.getProvEvent();

		final AbstractVertex senderVertex = provContext.getVertexGenerator().generate();
		senderVertex.addAnnotations(provSender.getKeyAnnotations(provContext));
		senderVertex.addAnnotations(provSender.getExtraAnnotations(provContext));

		final AbstractVertex receiverVertex = provContext.getVertexGenerator().generate();
		receiverVertex.addAnnotations(provReceiver.getKeyAnnotations(provContext));
		receiverVertex.addAnnotations(provReceiver.getExtraAnnotations(provContext));

		final AbstractEdge edge = provContext.getEdgeGenerator().generate(senderVertex, receiverVertex);
		edge.addAnnotations(provEvent.getKeyAnnotations(provContext));
		edge.addAnnotations(provEvent.getExtraAnnotations(provContext));

		final List<ProvenanceElement> elements = new ArrayList<>();
		elements.add(ProvenanceElement.of(senderVertex));
		elements.add(ProvenanceElement.of(receiverVertex));
		elements.add(ProvenanceElement.of(edge));
		return elements;
	}

}
