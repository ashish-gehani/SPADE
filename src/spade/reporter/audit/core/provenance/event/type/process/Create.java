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
import spade.reporter.audit.core.provenance.Context;
import spade.reporter.audit.core.provenance.ProvenanceElement;
import spade.reporter.audit.core.provenance.event.Event;
import spade.reporter.audit.core.provenance.event.ID;
import spade.reporter.audit.core.provenance.event.ProcessType;
import spade.reporter.audit.core.provenance.type.AbstractContext;
import spade.reporter.audit.core.provenance.type.AbstractProcess;

public abstract class Create<C extends AbstractContext> extends Event<C>{

	private final AbstractProcess<C> parent;
	private final AbstractProcess<C> child;

	public Create(final ID id, final AbstractProcess<C> parent, final AbstractProcess<C> child){
		super(ProcessType.CREATE, id);
		if(parent == null){
			throw new IllegalArgumentException("parent cannot be NULL");
		}
		if(child == null){
			throw new IllegalArgumentException("child cannot be NULL");
		}
		this.parent = parent;
		this.child = child;
	}

	public AbstractProcess<C> getParent(){
		return parent;
	}

	public AbstractProcess<C> getChild(){
		return child;
	}

	@Override
	public List<ProvenanceElement> handle(final C provContext, final Context managerContext){
		final AbstractVertex parentVertex = managerContext.getVertexGenerator().generate();
		parentVertex.addAnnotations(parent.getKeyAnnotations(provContext));
		parentVertex.addAnnotations(parent.getExtraAnnotations(provContext));

		final AbstractVertex childVertex = managerContext.getVertexGenerator().generate();
		childVertex.addAnnotations(child.getKeyAnnotations(provContext));
		childVertex.addAnnotations(child.getExtraAnnotations(provContext));

		final AbstractEdge edge = managerContext.getEdgeGenerator().generate(childVertex, parentVertex);
		edge.addAnnotations(getKeyAnnotations(provContext));
		edge.addAnnotations(getExtraAnnotations(provContext));

		final List<ProvenanceElement> elements = new ArrayList<>();
		elements.add(ProvenanceElement.of(parentVertex));
		elements.add(ProvenanceElement.of(childVertex));
		elements.add(ProvenanceElement.of(edge));
		return elements;
	}

}
