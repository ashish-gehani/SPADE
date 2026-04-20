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
package spade.reporter.audit.core.provenance.event.type.process;

import java.util.ArrayList;
import java.util.List;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.reporter.audit.core.provenance.ManagerContext;
import spade.reporter.audit.core.provenance.ProvenanceElement;
import spade.reporter.audit.core.provenance.event.Event;
import spade.reporter.audit.core.provenance.event.ID;
import spade.reporter.audit.core.provenance.event.ProcessType;
import spade.reporter.audit.core.provenance.type.AbstractContext;
import spade.reporter.audit.core.provenance.type.AbstractProcess;

public abstract class Signal extends Event{

	private final AbstractProcess sender;
	private final AbstractProcess receiver;

	public Signal(final ID id, final AbstractProcess sender, final AbstractProcess receiver){
		super(ProcessType.SIGNAL, id);
		if(sender == null){
			throw new IllegalArgumentException("sender cannot be NULL");
		}
		if(receiver == null){
			throw new IllegalArgumentException("receiver cannot be NULL");
		}
		this.sender = sender;
		this.receiver = receiver;
	}

	public AbstractProcess getSender(){
		return sender;
	}

	public AbstractProcess getReceiver(){
		return receiver;
	}

	@Override
	public List<ProvenanceElement> handle(final AbstractContext context, final ManagerContext managerContext){
		final AbstractVertex senderVertex = managerContext.getVertexGenerator().generate();
		senderVertex.addAnnotations(sender.getKeyAnnotations(context));
		senderVertex.addAnnotations(sender.getExtraAnnotations(context));

		final AbstractVertex receiverVertex = managerContext.getVertexGenerator().generate();
		receiverVertex.addAnnotations(receiver.getKeyAnnotations(context));
		receiverVertex.addAnnotations(receiver.getExtraAnnotations(context));

		final AbstractEdge edge = managerContext.getEdgeGenerator().generate(senderVertex, receiverVertex);
		edge.addAnnotations(getKeyAnnotations(context));
		edge.addAnnotations(getExtraAnnotations(context));

		final List<ProvenanceElement> elements = new ArrayList<>();
		elements.add(ProvenanceElement.of(senderVertex));
		elements.add(ProvenanceElement.of(receiverVertex));
		elements.add(ProvenanceElement.of(edge));
		return elements;
	}

}
