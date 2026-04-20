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

public abstract class Update extends Event{

	private final AbstractProcess oldVersion;
	private final AbstractProcess newVersion;

	public Update(final ID id, final AbstractProcess oldVersion, final AbstractProcess newVersion){
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

	public AbstractProcess getOldVersion(){
		return oldVersion;
	}

	public AbstractProcess getNewVersion(){
		return newVersion;
	}

	@Override
	public List<ProvenanceElement> handle(final AbstractContext context, final ManagerContext managerContext){
		final AbstractVertex oldVertex = managerContext.getVertexGenerator().generate();
		oldVertex.addAnnotations(oldVersion.getKeyAnnotations(context));
		oldVertex.addAnnotations(oldVersion.getExtraAnnotations(context));

		final AbstractVertex newVertex = managerContext.getVertexGenerator().generate();
		newVertex.addAnnotations(newVersion.getKeyAnnotations(context));
		newVertex.addAnnotations(newVersion.getExtraAnnotations(context));

		final AbstractEdge edge = managerContext.getEdgeGenerator().generate(newVertex, oldVertex);
		edge.addAnnotations(getKeyAnnotations(context));
		edge.addAnnotations(getExtraAnnotations(context));

		final List<ProvenanceElement> elements = new ArrayList<>();
		elements.add(ProvenanceElement.of(oldVertex));
		elements.add(ProvenanceElement.of(newVertex));
		elements.add(ProvenanceElement.of(edge));
		return elements;
	}

}
