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

public abstract class Update<C extends AbstractContext> extends Event<C>{

	private final AbstractProcess<C> oldVersion;
	private final AbstractProcess<C> newVersion;

	public Update(final ID id, final AbstractProcess<C> oldVersion, final AbstractProcess<C> newVersion){
		super(ProcessType.UPDATE, id);
		if(oldVersion == null){
			throw new IllegalArgumentException("oldVersion cannot be NULL");
		}
		if(newVersion == null){
			throw new IllegalArgumentException("newVersion cannot be NULL");
		}
		this.oldVersion = oldVersion;
		this.newVersion = newVersion;
	}

	public AbstractProcess<C> getOldVersion(){
		return oldVersion;
	}

	public AbstractProcess<C> getNewVersion(){
		return newVersion;
	}

	@Override
	public List<ProvenanceElement> handle(final C provContext, final Context managerContext){
		final AbstractVertex oldVertex = managerContext.getVertexGenerator().generate();
		oldVertex.addAnnotations(oldVersion.getKeyAnnotations(provContext));
		oldVertex.addAnnotations(oldVersion.getExtraAnnotations(provContext));

		final AbstractVertex newVertex = managerContext.getVertexGenerator().generate();
		newVertex.addAnnotations(newVersion.getKeyAnnotations(provContext));
		newVertex.addAnnotations(newVersion.getExtraAnnotations(provContext));

		final AbstractEdge edge = managerContext.getEdgeGenerator().generate(newVertex, oldVertex);
		edge.addAnnotations(getKeyAnnotations(provContext));
		edge.addAnnotations(getExtraAnnotations(provContext));

		final List<ProvenanceElement> elements = new ArrayList<>();
		elements.add(ProvenanceElement.of(oldVertex));
		elements.add(ProvenanceElement.of(newVertex));
		elements.add(ProvenanceElement.of(edge));
		return elements;
	}

}
