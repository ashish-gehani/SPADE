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

public abstract class Control extends Event{

	private final AbstractProcess controller;
	private final AbstractProcess target;

	public Control(final ID id, final AbstractProcess controller, final AbstractProcess target){
		super(ProcessType.CONTROL, id);
		if(controller == null){
			throw new IllegalArgumentException("controller cannot be NULL");
		}
		if(target == null){
			throw new IllegalArgumentException("target cannot be NULL");
		}
		this.controller = controller;
		this.target = target;
	}

	public AbstractProcess getController(){
		return controller;
	}

	public AbstractProcess getTarget(){
		return target;
	}

	@Override
	public List<ProvenanceElement> handle(final AbstractContext context, final ManagerContext managerContext){
		final AbstractVertex controllerVertex = managerContext.getVertexGenerator().generate();
		controllerVertex.addAnnotations(controller.getKeyAnnotations(context));
		controllerVertex.addAnnotations(controller.getExtraAnnotations(context));

		final AbstractVertex targetVertex = managerContext.getVertexGenerator().generate();
		targetVertex.addAnnotations(target.getKeyAnnotations(context));
		targetVertex.addAnnotations(target.getExtraAnnotations(context));

		final AbstractEdge edge = managerContext.getEdgeGenerator().generate(controllerVertex, targetVertex);
		edge.addAnnotations(getKeyAnnotations(context));
		edge.addAnnotations(getExtraAnnotations(context));

		final List<ProvenanceElement> elements = new ArrayList<>();
		elements.add(ProvenanceElement.of(controllerVertex));
		elements.add(ProvenanceElement.of(targetVertex));
		elements.add(ProvenanceElement.of(edge));
		return elements;
	}

}
